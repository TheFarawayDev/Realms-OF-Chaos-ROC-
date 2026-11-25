package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * The Compass of Chaos does not carry anyone anywhere. It scans. A scan runs for a minute to a minute and a half,
 * and every two or three seconds the needle rolls: it picks one of the things it is looking for - the nearer and
 * the more important, the likelier - and gives a bearing and a distance that are off by as much as the signal is
 * poor. Now and then a roll is a <b>Clean Lock</b>, exact; now and then a <b>Static Surge</b>, nonsense. Relay
 * towers near the scanner steady it, and so does a higher hub tier; storms, depth and other people's jammers
 * spoil it. The longer a scan runs, the truer it gets.
 */
public final class Scanner {
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".scan.";
    private static final String MODE_TAG = "roc_scan_mode";
    /** How long a scan lasts, shortest and longest, in ticks. */
    public static final int MIN_TICKS = 20 * 60, MAX_TICKS = 20 * 90;
    public static final int COOLDOWN = 20 * 8;

    /** What the compass is set to look for. Sneak and right-click turns it to the next. */
    public enum Mode {
        /** The way to the Hub of Realms: the nearest Ruined Gate, and waygates you have touched. */
        HUB(0xA55AD6),
        /** The shrines that keep the three keystones. */
        KEYSTONES(0xF5B82E),
        /** What players have built: relay towers and waygates. */
        NETWORK(0x55FFFF);

        public final int color;

        Mode(int color) {
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Component displayName() {
            return Component.translatable(HUD + "mode." + id());
        }
    }

    public enum Roll { NORMAL, CLEAN_LOCK, STATIC_SURGE }

    /** Something a scan can find. @param weight how much the needle favours it */
    public record Target(Component name, double x, double z, double weight) {}

    /** One roll of the needle: what it pointed at, and the bearing and distance it showed (not the true ones). */
    public record Reading(Roll roll, Target target, double dx, double dz, double quality) {
        public double distance() {
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /** The tab's heading while a compass speaks: its own name, in the colour of what it is set to. */
    private static Component heading(Mode mode) {
        return Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass").withStyle(style -> style.withColor(mode.color));
    }

    /** Something to say that is not a reading: on the tab, or above the hotbar with the drawn interface off. */
    private static void say(ServerPlayer player, Mode mode, Component message, int color) {
        if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player)) com.thefarawaydev.realmsofchaos.ui.CompassTab.notice(player, heading(mode), message);
        else Hud.actionBar(player, Hud.colored(message, color));
    }

    /** A reading on the tab. {@code done}: the scan's last word. */
    private static void tab(ServerPlayer player, ServerLevel level, Scan scan, Reading reading, long now, boolean done) {
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".tab.";
        int state = reading == null ? com.thefarawaydev.realmsofchaos.ui.CompassTab.NORMAL : switch (reading.roll()) {
            case CLEAN_LOCK -> com.thefarawaydev.realmsofchaos.ui.CompassTab.LOCK;
            case STATIC_SURGE -> com.thefarawaydev.realmsofchaos.ui.CompassTab.SURGE;
            default -> com.thefarawaydev.realmsofchaos.ui.CompassTab.NORMAL;
        };
        double progress = done ? 1.0 : Math.max(0.0, Math.min(1.0, (now - scan.start) / (double) (scan.end - scan.start)));
        int quality = (int) Math.round(quality(level, player) * 5);
        int seconds = done ? 0 : (int) ((scan.end - now + 19) / 20);
        Component status = done ? Component.translatable(hud + "done") : state == com.thefarawaydev.realmsofchaos.ui.CompassTab.LOCK ? Component.translatable(hud + "lock")
                : state == com.thefarawaydev.realmsofchaos.ui.CompassTab.SURGE ? Component.translatable(hud + "surge") : null;
        if (reading == null) {
            com.thefarawaydev.realmsofchaos.ui.CompassTab.show(player, heading(scan.mode), Component.translatable(hud + "listening", scan.mode.displayName()), null, status,
                    com.thefarawaydev.realmsofchaos.ui.CompassTab.pack(-1, quality, com.thefarawaydev.realmsofchaos.ui.CompassTab.NORMAL, false, seconds, progress, 0));
            return;
        }
        com.thefarawaydev.realmsofchaos.ui.CompassTab.show(player, heading(scan.mode), reading.target().name(), com.thefarawaydev.realmsofchaos.ui.CompassTab.distance(reading.distance(), reading.roll() == Roll.NORMAL), status,
                com.thefarawaydev.realmsofchaos.ui.CompassTab.pack(reading.roll() == Roll.STATIC_SURGE ? -1 : com.thefarawaydev.realmsofchaos.ui.CompassTab.bearing(reading.dx(), reading.dz()), quality, state, false, seconds, progress, 0));
    }

    private static final class Scan {
        ServerPlayer player;
        Mode mode;
        long start, end, nextRoll;
        Reading last, best;
        int locks, surges, rolls;
    }

    private static final Map<UUID, Scan> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> READY = new HashMap<>();

    private Scanner() {}

    // ---- the compass's setting -------------------------------------------------------------------------------

    public static Mode mode(ItemStack compass) {
        CustomData data = compass.get(DataComponents.CUSTOM_DATA);
        int index = data == null ? 0 : data.copyTag().getIntOr(MODE_TAG, 0);
        return Mode.values()[Math.floorMod(index, Mode.values().length)];
    }

    public static Mode cycle(ItemStack compass) {
        Mode next = mode(compass).next();
        CustomData data = compass.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putInt(MODE_TAG, next.ordinal());
        compass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return next;
    }

    // ---- signal ----------------------------------------------------------------------------------------------

    /** The open Overworld is the only place a scan works: the ruins are there, and so is the field network. */
    public static boolean canScan(ServerPlayer player) {
        return Realm.of(player.level().dimension()) == null;
    }

    /** True if a jammer that is not this player's (or their party's) reaches them. */
    public static boolean jammed(ServerLevel level, ServerPlayer player) {
        java.util.Set<UUID> friends = Parties.members(player.getUUID());
        for (Network.Node jammer : Network.near(level, Network.Kind.JAMMER, player.getX(), player.getZ(), Network.Kind.JAMMER.range)) {
            if (!jammer.owner().equals(player.getUUID()) && !friends.contains(jammer.owner())) return true;
        }
        return false;
    }

    public static int relays(ServerLevel level, ServerPlayer player) {
        return Math.min(3, Network.near(level, Network.Kind.RELAY, player.getX(), player.getZ(), Network.Kind.RELAY.range).size());
    }

    /**
     * How good the signal is where the player stands, 0.05 to 1: a bare compass reads 0.35; each relay tower in
     * range adds 0.15 (up to three), each hub tier 0.05; rain costs 0.1, a thunderstorm 0.2, being deep
     * underground 0.15, and someone else's jammer 0.5.
     */
    public static double quality(ServerLevel level, ServerPlayer player) {
        double q = 0.35 + 0.15 * relays(level, player) + HubTiers.calibration(level.getServer()) + Skills.signal(player);
        if (level.isThundering()) q -= 0.2;
        else if (level.isRaining()) q -= 0.1;
        if (player.getY() < 30 && !level.canSeeSky(player.blockPosition())) q -= 0.15;
        if (jammed(level, player)) q -= 0.5;
        return Math.max(0.05, Math.min(1.0, q));
    }

    // ---- what there is to find -------------------------------------------------------------------------------

    public static List<Target> targets(ServerLevel level, ServerPlayer player, Mode mode) {
        List<Target> out = new ArrayList<>();
        boolean ruins = level.dimension() == Level.OVERWORLD;
        double x = player.getX(), z = player.getZ();
        switch (mode) {
            case HUB -> {
                if (ruins) {
                    Ruins.Site gate = Ruins.nearest(level.getSeed(), Ruins.Kind.GATE, x, z);
                    out.add(new Target(Ruins.Kind.GATE.displayName(), gate.x() + 0.5, gate.z() + 0.5, 3.0));
                }
                String here = Network.dimension(level);
                for (Network.Node gate : Network.discovered(player)) {
                    if (gate.dimension().equals(here)) out.add(new Target(waygateName(gate), gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5, 2.0));
                }
            }
            case KEYSTONES -> {
                if (!ruins) break;
                for (Ruins.Kind kind : new Ruins.Kind[] {Ruins.Kind.SKY, Ruins.Kind.SEA, Ruins.Kind.SHADE}) {
                    Ruins.Site site = Ruins.nearest(level.getSeed(), kind, x, z);
                    // a keystone already in the pack is hardly worth pointing at
                    boolean held = player.getInventory().contains(stack -> stack.is(ModContent.keystoneItem(kind.line)));
                    out.add(new Target(kind.displayName(), site.x() + 0.5, site.z() + 0.5, held ? 0.3 : 2.0));
                }
            }
            case NETWORK -> {
                for (Network.Node relay : Network.near(level, Network.Kind.RELAY, x, z, 4000)) {
                    out.add(new Target(Component.translatable(HUD + "target.relay"), relay.pos().getX() + 0.5, relay.pos().getZ() + 0.5, 2.0));
                }
                for (Network.Node gate : Network.near(level, Network.Kind.WAYGATE, x, z, 4000)) {
                    out.add(new Target(waygateName(gate), gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5, 1.0));
                }
            }
        }
        return out;
    }

    public static Component waygateName(Network.Node gate) {
        return Component.translatable(HUD + "target.waygate", gate.pos().getX(), gate.pos().getZ());
    }

    private static double distance(Target target, double x, double z) {
        return Math.sqrt((target.x() - x) * (target.x() - x) + (target.z() - z) * (target.z() - z));
    }

    /** The needle's pick: the nearer and the weightier a target, the likelier. */
    public static Target pick(List<Target> targets, double x, double z, RandomSource random) {
        double total = 0.0;
        double[] share = new double[targets.size()];
        for (int i = 0; i < share.length; i++) {
            share[i] = targets.get(i).weight() / (1.0 + distance(targets.get(i), x, z) / 400.0);
            total += share[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < share.length; i++) {
            roll -= share[i];
            if (roll <= 0.0) return targets.get(i);
        }
        return targets.get(targets.size() - 1);
    }

    public static Target nearest(List<Target> targets, double x, double z) {
        Target best = null;
        for (Target target : targets) {
            if (best == null || distance(target, x, z) < distance(best, x, z)) best = target;
        }
        return best;
    }

    /** The chance of a Static Surge and of a Clean Lock on one roll. */
    public static double surgeChance(double quality) {
        return 0.04 + 0.16 * (1.0 - quality);
    }

    public static double lockChance(double quality, double progress) {
        return 0.03 + 0.17 * quality * (0.5 + 0.5 * progress);
    }

    /** How far off a reading may be: this many degrees either way, and this share of the distance. */
    public static double scatterDegrees(double quality, double progress) {
        return 75.0 * (1.0 - quality) * (1.0 - 0.5 * progress);
    }

    public static double scatterShare(double quality, double progress) {
        return 0.45 * (1.0 - quality) * (1.0 - 0.5 * progress);
    }

    /**
     * One roll of the needle. @param progress how far through the scan this is, 0 to 1 @return the reading, or
     * null when there is nothing of that kind to find.
     */
    public static Reading roll(List<Target> targets, double x, double z, double quality, double progress, RandomSource random) {
        if (targets.isEmpty()) return null;
        double chance = random.nextDouble();
        if (chance < surgeChance(quality)) {
            // static: a bearing out of nowhere
            Target target = pick(targets, x, z, random);
            double a = random.nextDouble() * Math.PI * 2, d = 50 + random.nextDouble() * 900;
            return new Reading(Roll.STATIC_SURGE, target, Math.cos(a) * d, Math.sin(a) * d, quality);
        }
        if (chance > 1.0 - lockChance(quality, progress)) {
            Target target = nearest(targets, x, z);
            return new Reading(Roll.CLEAN_LOCK, target, target.x() - x, target.z() - z, quality);
        }
        Target target = pick(targets, x, z, random);
        double dx = target.x() - x, dz = target.z() - z;
        double turn = Math.toRadians((random.nextDouble() * 2 - 1) * scatterDegrees(quality, progress));
        double stretch = 1.0 + (random.nextDouble() * 2 - 1) * scatterShare(quality, progress);
        double rx = (dx * Math.cos(turn) - dz * Math.sin(turn)) * stretch, rz = (dx * Math.sin(turn) + dz * Math.cos(turn)) * stretch;
        return new Reading(Roll.NORMAL, target, rx, rz, quality);
    }

    // ---- scanning --------------------------------------------------------------------------------------------

    public static boolean scanning(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static Reading last(ServerPlayer player) {
        Scan scan = ACTIVE.get(player.getUUID());
        return scan == null ? null : scan.last;
    }

    public static int locks(ServerPlayer player) {
        Scan scan = ACTIVE.get(player.getUUID());
        return scan == null ? 0 : scan.locks;
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    /** Starts a scan. @return false if one cannot be started here or yet. */
    public static boolean start(ServerPlayer player, Mode mode) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) return false;
        if (!canScan(player)) {
            say(player, mode, Component.translatable(HUD + "nowhere"), 0xAAAAAA);
            return false;
        }
        long now = server.getTickCount();
        Long ready = READY.get(player.getUUID());
        if (ready != null && now < ready && now >= ready - COOLDOWN) {
            say(player, mode, Component.translatable(HUD + "settling", (ready - now + 19) / 20), 0xAAAAAA);
            return false;
        }
        if (targets(level, player, mode).isEmpty()) {
            say(player, mode, Component.translatable(HUD + "nothing", mode.displayName()), 0xAAAAAA);
            return false;
        }
        Scan scan = new Scan();
        scan.player = player;
        scan.mode = mode;
        scan.start = now;
        scan.end = now + MIN_TICKS + level.getRandom().nextInt(MAX_TICKS - MIN_TICKS + 1);
        scan.nextRoll = now + 20;
        ACTIVE.put(player.getUUID(), scan);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player)) tab(player, level, scan, null, now, false);
        else Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "start", mode.displayName(), (scan.end - now) / 20), mode.color));
        return true;
    }

    private static boolean holding(ServerPlayer player) {
        return player.getMainHandItem().is(ModContent.CHAOS_COMPASS.get()) || player.getOffhandItem().is(ModContent.CHAOS_COMPASS.get());
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Scan>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Scan> entry = it.next();
            Scan scan = entry.getValue();
            ServerPlayer player = scan.player;
            // (a player who died or left has a new body, or none: either way this scan is over)
            if (player.isRemoved() || player.hasDisconnected() || !player.isAlive() || !(player.level() instanceof ServerLevel level) || !canScan(player)) {
                it.remove();
                continue;
            }
            if (now >= scan.end) {
                finish(player, scan);
                READY.put(player.getUUID(), now + COOLDOWN);
                it.remove();
                continue;
            }
            if (now >= scan.nextRoll) {
                // tier IV tunes the needle: it rolls every two seconds instead of every two to three
                scan.nextRoll = now + (HubTiers.tier(server) >= 4 ? 40 : 40 + level.getRandom().nextInt(21));
                pulse(player, level, scan, now);
            }
            // (with the drawn interface off, the old line above the hotbar, kept up to date)
            if (now % 10 == 0 && holding(player) && !com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player)) show(player, level, scan, now);
        }
    }

    /** One roll during a scan, with its sound and - on a Clean Lock - a trail of light towards the target. */
    public static Reading pulse(ServerPlayer player, ServerLevel level, Mode mode) {
        Scan scan = ACTIVE.get(player.getUUID());
        if (scan == null) return null;
        Reading reading = pulse(player, level, scan, level.getServer().getTickCount());
        // (asked for: the tab slides out with it, whatever it was)
        if (reading != null && holding(player) && com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player)) tab(player, level, scan, reading, level.getServer().getTickCount(), false);
        return reading;
    }

    private static Reading pulse(ServerPlayer player, ServerLevel level, Scan scan, long now) {
        double progress = Math.max(0.0, Math.min(1.0, (now - scan.start) / (double) (scan.end - scan.start)));
        Reading reading = roll(targets(level, player, scan.mode), player.getX(), player.getZ(), quality(level, player), progress, level.getRandom());
        if (reading == null) return null;
        scan.last = reading;
        scan.rolls++;
        if (!holding(player)) return reading;
        // the tab slides out now and then by itself: on a clean lock, a surge, and every fourth roll besides
        if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player) && (reading.roll() != Roll.NORMAL || scan.rolls % 4 == 1)) tab(player, level, scan, reading, now, false);
        switch (reading.roll()) {
            case CLEAN_LOCK -> {
                scan.locks++;
                scan.best = reading;
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
                double d = Math.max(0.01, reading.distance());
                for (int i = 1; i <= 14; i++) {
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), player.getX() + reading.dx() / d * i * 0.6, player.getY() + 1.2,
                            player.getZ() + reading.dz() / d * i * 0.6, 1, 0, 0, 0, 0);
                }
            }
            case STATIC_SURGE -> {
                scan.surges++;
                level.playSound(null, player.blockPosition(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS, 0.8F, 0.6F);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type(), player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            }
            default -> {
                if (scan.best == null || scan.best.roll() != Roll.CLEAN_LOCK) scan.best = reading;
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4F, 1.2F);
            }
        }
        return reading;
    }

    /** The arrow for a bearing as the player is facing: up is straight ahead. */
    public static String arrow(ServerPlayer player, double dx, double dz) {
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));           // the yaw that would face the target
        double turn = bearing - player.getYRot();
        return arrows[Math.floorMod((int) Math.round(turn / 45.0), 8)];
    }

    public static String bars(double quality) {
        int lit = (int) Math.round(quality * 5);
        return "▮".repeat(lit) + "▯".repeat(5 - lit);
    }

    /** The line above the hotbar: mode, arrow, distance, signal, time left. */
    public static Component line(ServerPlayer player, ServerLevel level, Mode mode, Reading reading, long secondsLeft) {
        double q = quality(level, player);
        if (reading == null) return Component.translatable(HUD + "line.waiting", mode.displayName(), bars(q), secondsLeft);
        String key = switch (reading.roll()) {
            case CLEAN_LOCK -> "line.lock";
            case STATIC_SURGE -> "line.surge";
            default -> "line";
        };
        int distance = (int) Math.round(reading.distance());
        // a rough reading is shown rough: to the nearest ten blocks
        if (reading.roll() == Roll.NORMAL) distance = Math.max(10, (int) Math.round(distance / 10.0) * 10);
        return Component.translatable(HUD + key, reading.target().name(), arrow(player, reading.dx(), reading.dz()), distance, bars(q), secondsLeft);
    }

    private static void show(ServerPlayer player, ServerLevel level, Scan scan, long now) {
        int color = scan.last == null ? 0xAAAAAA : switch (scan.last.roll()) {
            case CLEAN_LOCK -> 0x55FF55;
            case STATIC_SURGE -> 0xFF5555;
            default -> scan.mode.color;
        };
        Hud.actionBar(player, Hud.colored(line(player, level, scan.mode, scan.last, (scan.end - now + 19) / 20), color));
    }

    /** The scan is over: its best reading is written to chat, where it stays. */
    private static void finish(ServerPlayer player, Scan scan) {
        Reading best = scan.best;
        if (best == null) {
            player.sendSystemMessage(Hud.colored(Component.translatable(HUD + "done.none", scan.mode.displayName()), 0xAAAAAA));
            return;
        }
        // (the bearing is given by the compass rose here, since the player will have turned by the time they read it)
        Component way = Component.translatable(Ruins.directionKey(best.dx(), best.dz()));
        if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player) && player.level() instanceof ServerLevel level) tab(player, level, scan, best, level.getServer().getTickCount(), true);
        String key = best.roll() == Roll.CLEAN_LOCK ? "done.lock" : "done";
        player.sendSystemMessage(Hud.colored(Component.translatable(HUD + key, best.target().name(), (int) Math.round(best.distance()), way, scan.locks), scan.mode.color));
        player.level().playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** Test seam: makes a running scan end on the next tick. */
    public static void expire(ServerPlayer player) {
        Scan scan = ACTIVE.get(player.getUUID());
        if (scan != null) scan.end = 0;
    }

    public static void reset() {
        ACTIVE.clear();
        READY.clear();
    }

    /** A block position's name on the action bar. */
    public static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
