package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.RuneStoneBlock;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rune chambers: the trap rooms of the passages. A rune stone stands in each corner. Step into the middle of the
 * room and it springs: its doorways close, and water rises one layer at a time. The four runes light up in an
 * order, over and over; press them in that order and the room drains, opens, and gives up a piece of forge
 * material to everyone in it. A wrong rune starts the order again and the water leaps up a layer.
 *
 * <p>It is a trap, not a grave. Once full, the room holds its catch for a quarter of a minute - hurting them as it
 * does - and then lets go: it drains and opens by itself, without a reward, and will spring again the next time.
 * It resets the moment nobody is left inside, never springs for someone who has already solved it, and a room
 * left flooded by a server stop is put right the next time anyone walks into it.
 */
public final class Traps {
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".trap.";
    /** How many layers of water fill the room, and how long it holds once full. */
    public static final int LAYERS = 4, FULL_TICKS = 20 * 16;
    /** The order is shown again this often; each rune is lit this long, with this gap before the next. */
    public static final int SHOW_EVERY = 20 * 8, GLOW = 12, GAP = 6, SHOW_DELAY = 20;
    /** Where the four rune stones stand, from the middle of the room (two blocks up, on a pillar). */
    private static final int[][] CORNERS = {{-3, -3}, {3, -3}, {3, 3}, {-3, 3}};

    /**
     * What a rune chamber does to whoever it has caught, and how its order has to be remembered. A flooding chamber
     * shows its order over and over while the water rises. A furnace shows it once - and again only after a
     * mistake - while the heat builds. A chamber of gloom shows it over and over, but wants it pressed backwards,
     * while the dark closes in.
     */
    public enum Peril {
        FLOOD, EMBERS, GLOOM;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** What its lines of text are filed under: the flood's are the plain ones. */
        String suffix() {
            return this == FLOOD ? "" : "." + id();
        }
    }

    /** Which peril a chamber holds: fixed by where it is. */
    public static Peril peril(Passages.Plan plan, Passages.Cell cell) {
        long h = plan.seed * 43 + cell.x() * 15485863L + cell.z() * 7919L;
        h ^= h >>> 17;
        h *= 0x9E3779B97F4A7C15L;
        return Peril.values()[(int) Math.floorMod(h >>> 21, (long) Peril.values().length)];
    }

    public static final class Trap {
        final ServerLevel level;
        final Passages.Plan plan;
        final Passages.Cell cell;
        final Peril peril;
        /** The order the runes light in, and the order they have to be pressed in: the same, except in the gloom. */
        final int[] shown;
        final int[] order;
        int pressed, ticks, water, fullTicks, lit = -1, flashUntil, perTick;
        boolean full;
        /** The blocks of the layer the water is still creeping across, and how many it covers each tick. */
        final java.util.ArrayDeque<BlockPos> pending = new java.util.ArrayDeque<>();

        /** How many blocks of the newest layer the water has yet to reach. */
        public int pending() {
            return pending.size();
        }

        Trap(ServerLevel level, Passages.Plan plan, Passages.Cell cell, int[] shown) {
            this.level = level;
            this.plan = plan;
            this.cell = cell;
            this.peril = Traps.peril(plan, cell);
            this.shown = shown;
            this.order = shown.clone();
            if (peril == Peril.GLOOM) {
                for (int i = 0; i < order.length; i++) order[i] = shown[shown.length - 1 - i];
            }
        }

        public Peril peril() {
            return peril;
        }

        public int[] shown() {
            return shown.clone();
        }

        public int[] order() {
            return order.clone();
        }

        public int pressed() {
            return pressed;
        }

        public int water() {
            return water;
        }

        public boolean full() {
            return full;
        }
    }

    private static final Map<String, Trap> ACTIVE = new HashMap<>();

    private Traps() {}

    private static String key(Passages.Plan plan, Passages.Cell cell) {
        return plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + cell.x() + "/" + cell.z();
    }

    private static String flag(Passages.Plan plan, Passages.Cell cell) {
        return "trap/" + key(plan, cell);
    }

    private static String solvedKey(Passages.Plan plan, Passages.Cell cell, ServerPlayer player) {
        return "trapdone/" + key(plan, cell) + "/" + player.getUUID();
    }

    /** How many runes have to be pressed: three low in a tower, five at the top. */
    public static int length(Passages.Plan plan) {
        return 2 + Math.max(1, Math.min(3, plan.tier));
    }

    /** Ticks between one layer of water and the next: slower low in a tower. */
    public static int fillEvery(Passages.Plan plan) {
        return 20 * (10 - Math.max(1, Math.min(3, plan.tier)));
    }

    public static BlockPos rune(Passages.Plan plan, Passages.Cell cell, int n) {
        return plan.centre(cell).offset(CORNERS[n][0], 2, CORNERS[n][1]);
    }

    /** The blocks that close the room: its side of every doorway, three wide and three high. */
    public static List<BlockPos> doors(Passages.Plan plan, Passages.Cell cell) {
        BlockPos m = plan.centre(cell);
        List<BlockPos> out = new ArrayList<>();
        for (Passages.Cell n : plan.neighbours(cell)) {
            if (!plan.connected(cell, n)) continue;
            int sx = n.x() - cell.x(), sz = n.z() - cell.z();
            for (int k = -1; k <= 1; k++) {
                for (int y = 1; y <= 3; y++) out.add(m.offset(sx * 5 + (sx == 0 ? k : 0), y, sz * 5 + (sz == 0 ? k : 0)));
            }
        }
        return out;
    }

    /** Stands the rune stones on their pillars. Called when the passage is built. */
    public static void build(ServerLevel level, Passages.Plan plan, Passages.Cell cell, Block pillar) {
        BlockState stone = ModContent.RUNE_STONE.get().defaultBlockState();
        for (int n = 0; n < RuneStoneBlock.RUNES; n++) {
            BlockPos at = rune(plan, cell, n);
            level.setBlock(at.below(), pillar.defaultBlockState(), 2);
            level.setBlock(at, stone.setValue(RuneStoneBlock.RUNE, n), 2);
        }
        hangSprinklers(level, plan, cell);
    }

    public static Trap active(Passages.Plan plan, Passages.Cell cell) {
        return ACTIVE.get(key(plan, cell));
    }

    public static boolean solvedBy(Passages.Plan plan, Passages.Cell cell, ServerPlayer player) {
        return ProgressData.get(player.level().getServer()).isBuilt(solvedKey(plan, cell, player));
    }

    private static List<ServerPlayer> inside(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        return level.getEntitiesOfClass(ServerPlayer.class, plan.cellBox(cell), p -> p.isAlive() && !p.isSpectator());
    }

    /**
     * Once a second for a player standing in a rune chamber: puts right a room a server stop left flooded, and
     * springs the trap when someone who has not solved it reaches the middle.
     */
    public static void service(ServerPlayer player, ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        if (active(plan, cell) != null) return;
        ProgressData data = ProgressData.get(level.getServer());
        if (data.isBuilt(flag(plan, cell))) restore(level, plan, cell);
        if (player.isCreative() || player.isSpectator() || solvedBy(plan, cell, player)) return;
        BlockPos m = plan.centre(cell);
        if (Math.abs(player.getX() - (m.getX() + 0.5)) <= 2.5 && Math.abs(player.getZ() - (m.getZ() + 0.5)) <= 2.5) spring(level, plan, cell);
    }

    /** A new order for the runes: never the same rune twice in a row. */
    public static int[] newOrder(int length, RandomSource random) {
        int[] order = new int[length];
        for (int i = 0; i < length; i++) {
            do {
                order[i] = random.nextInt(RuneStoneBlock.RUNES);
            } while (i > 0 && order[i] == order[i - 1]);
        }
        return order;
    }

    /** Springs the trap: the doorways close and the water starts to rise. */
    public static Trap spring(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        Trap existing = active(plan, cell);
        if (existing != null) return existing;
        Trap trap = new Trap(level, plan, cell, newOrder(length(plan), level.getRandom()));
        ACTIVE.put(key(plan, cell), trap);
        ProgressData.get(level.getServer()).markBuilt(flag(plan, cell));
        BlockPos m = plan.centre(cell);
        BlockState glass = plan.palette().glass().defaultBlockState();
        // anyone caught in a doorway is pulled into the room rather than shut in the wall
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, plan.cellBox(cell).inflate(1.0, 0, 1.0), e -> e.isAlive() && !e.isSpectator())) {
            double dx = p.getX() - (m.getX() + 0.5), dz = p.getZ() - (m.getZ() + 0.5);
            if (Math.abs(dx) > 3.6 || Math.abs(dz) > 3.6) {
                Fx.place(level, p, m.getX() + 0.5 + Math.max(-3.2, Math.min(3.2, dx)), m.getY() + 1, m.getZ() + 0.5 + Math.max(-3.2, Math.min(3.2, dz)));
            }
        }
        for (BlockPos door : doors(plan, cell)) {
            if (level.getBlockState(door).isAir()) level.setBlock(door, glass, 3);
        }
        level.playSound(null, m, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, m, trap.peril == Peril.FLOOD ? SoundEvents.AMBIENT_UNDERWATER_ENTER : trap.peril == Peril.EMBERS ? SoundEvents.BLAZE_SHOOT : SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.BLOCKS, 2.0F, 0.8F);
        for (ServerPlayer p : inside(level, plan, cell)) {
            Hud.title(p, Hud.colored(Component.translatable(HUD + "sprung" + trap.peril.suffix()), trap.peril == Peril.FLOOD ? 0x55FFFF : trap.peril == Peril.EMBERS ? 0xFF7A3C : 0xB45CFF),
                    Hud.colored(Component.translatable(HUD + "sprung.sub" + trap.peril.suffix(), trap.order.length), 0xFFFFFF), 5, 70, 15);
        }
        return trap;
    }

    private static void setLit(Trap trap, int rune) {
        if (trap.lit == rune) return;
        for (int n = 0; n < RuneStoneBlock.RUNES; n++) {
            BlockPos at = rune(trap.plan, trap.cell, n);
            BlockState state = trap.level.getBlockState(at);
            if (state.getBlock() instanceof RuneStoneBlock && state.getValue(RuneStoneBlock.LIT) != (n == rune)) {
                trap.level.setBlock(at, state.setValue(RuneStoneBlock.LIT, n == rune), 3);
            }
        }
        trap.lit = rune;
        if (rune >= 0) {
            trap.level.playSound(null, rune(trap.plan, trap.cell, rune), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.6F, 0.7F + 0.25F * rune);
        }
    }

    /** How many ticks the water takes to creep across one layer. */
    public static final int CREEP_TICKS = 60;

    /** Where a rune chamber's sprinklers hang: under the ring of trim round its ceiling, one to each side. */
    public static List<BlockPos> sprinklers(Passages.Plan plan, Passages.Cell cell) {
        BlockPos m = plan.centre(cell);
        int h = plan.height(cell);
        return List.of(m.offset(3, h, 0), m.offset(-3, h, 0), m.offset(0, h, 3), m.offset(0, h, -3));
    }

    /** Hangs the sprinklers in a rune chamber's ceiling. */
    public static void hangSprinklers(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        for (BlockPos at : sprinklers(plan, cell)) {
            if (level.getBlockState(at).isAir() && !level.getBlockState(at.above()).isAir()) level.setBlock(at, ModContent.SPRINKLER.get().defaultBlockState(), 2);
        }
    }

    /** Lays the water over as much of the creeping layer as it reaches this tick. */
    private static void creep(Trap trap, int blocks) {
        for (int i = 0; i < blocks && !trap.pending.isEmpty(); i++) {
            BlockPos at = trap.pending.poll();
            if (trap.level.getBlockState(at).isAir()) trap.level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
        }
    }

    /** One more layer of water: it creeps in from the walls over a few seconds. @return false if the room was already full. */
    private static boolean raise(Trap trap) {
        if (trap.water >= LAYERS) return false;
        creep(trap, Integer.MAX_VALUE);                     // (whatever of the last layer was still coming, comes now)
        trap.water++;
        BlockPos m = trap.plan.centre(trap.cell);
        if (trap.peril == Peril.FLOOD) {
            // from the walls in, ring by ring, each ring in no particular order
            java.util.Random order = new java.util.Random(trap.plan.seed + trap.water);
            for (int ring = 4; ring >= 0; ring--) {
                List<BlockPos> here = new ArrayList<>();
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) here.add(m.offset(dx, trap.water, dz));
                }
                java.util.Collections.shuffle(here, order);
                trap.pending.addAll(here);
            }
            trap.perTick = Math.max(1, (trap.pending.size() + CREEP_TICKS - 1) / CREEP_TICKS);
            trap.level.playSound(null, m, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.5F, 0.6F);
        } else {
            // (heat and dark are not built of blocks: the room only gets worse to stand in)
            trap.level.playSound(null, m, trap.peril == Peril.EMBERS ? SoundEvents.FIRECHARGE_USE : SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.2F, 0.6F);
        }
        if (trap.water >= LAYERS && !trap.full) {
            trap.full = true;
            for (ServerPlayer p : inside(trap.level, trap.plan, trap.cell)) Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "full" + trap.peril.suffix()), 0xFF5555));
        }
        return true;
    }

    /** Takes the water out, opens the doorways and puts the runes out. Safe on a room that was never flooded. */
    public static void restore(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        BlockPos m = plan.centre(cell);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = 1; y <= Passages.HEADROOM + 1; y++) {
                    BlockPos at = m.offset(dx, y, dz);
                    if (level.getBlockState(at).is(Blocks.WATER)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        Block glass = plan.palette().glass();
        for (BlockPos door : doors(plan, cell)) {
            if (level.getBlockState(door).is(glass)) level.setBlock(door, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int n = 0; n < RuneStoneBlock.RUNES; n++) {
            BlockPos at = rune(plan, cell, n);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof RuneStoneBlock && state.getValue(RuneStoneBlock.LIT)) level.setBlock(at, state.setValue(RuneStoneBlock.LIT, false), 3);
        }
        ProgressData.get(level.getServer()).unmarkBuilt(flag(plan, cell));
    }

    private static void end(Trap trap) {
        ACTIVE.remove(key(trap.plan, trap.cell));
        trap.pending.clear();
        restore(trap.level, trap.plan, trap.cell);
        trap.level.playSound(null, trap.plan.centre(trap.cell), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    /** The order was pressed right: the room drains and opens, and pays everyone in it who had not solved it before. */
    private static void solve(Trap trap) {
        List<ServerPlayer> solvers = inside(trap.level, trap.plan, trap.cell);
        end(trap);
        ProgressData data = ProgressData.get(trap.level.getServer());
        BlockPos m = trap.plan.centre(trap.cell);
        trap.level.playSound(null, m, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.2F);
        trap.level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), m.getX() + 0.5, m.getY() + 2.0, m.getZ() + 0.5, 80, 2.5, 1.0, 2.5, 0.05);
        for (ServerPlayer p : solvers) {
            Hud.title(p, Hud.colored(Component.translatable(HUD + "solved"), 0x55FF55), Hud.colored(Component.translatable(HUD + "solved.sub"), 0xCCCCCC), 5, 50, 15);
            if (data.isBuilt(solvedKey(trap.plan, trap.cell, p))) continue;
            data.markBuilt(solvedKey(trap.plan, trap.cell, p));
            ItemStack prize = prize(trap.plan);
            Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "prize", prize.getCount(), prize.getHoverName()), 0xF5B82E));
            if (!p.getInventory().add(prize) && !prize.isEmpty()) p.drop(prize, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** What a solved chamber gives each player, once: forge material of the passage's tier. */
    public static ItemStack prize(Passages.Plan plan) {
        return new ItemStack(ModContent.materialItem(plan.line, Math.max(1, Math.min(3, plan.tier))), 1 + plan.stage);
    }

    /**
     * A rune stone was pressed. @return true if it was the right one. A wrong one starts the order again and
     * brings the water up a layer.
     */
    public static boolean press(ServerLevel level, BlockPos pos, ServerPlayer player) {
        for (Trap trap : new ArrayList<>(ACTIVE.values())) {
            if (trap.level != level) continue;
            for (int n = 0; n < RuneStoneBlock.RUNES; n++) {
                if (!rune(trap.plan, trap.cell, n).equals(pos)) continue;
                if (trap.order[trap.pressed] == n) {
                    trap.pressed++;
                    trap.flashUntil = trap.ticks + GLOW;
                    setLit(trap, -1);
                    setLit(trap, n);
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
                    if (trap.pressed >= trap.order.length) solve(trap);
                    return true;
                }
                trap.pressed = 0;
                trap.ticks = 0;                        // the order is shown again from the start
                trap.flashUntil = 0;
                setLit(trap, -1);
                raise(trap);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 2.0F, 0.5F);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
                for (ServerPlayer p : inside(level, trap.plan, trap.cell)) Hud.above(p, pos, Component.translatable(HUD + "wrong" + trap.peril.suffix()), 0xFF5555);
                return false;
            }
        }
        Hud.above(player, pos, Component.translatable(HUD + "dormant"), 0xAAAAAA);
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.8F);
        return false;
    }

    /** Which rune the showing of the order has lit at this tick of a trap, or -1. */
    public static int shown(int[] order, int ticks) {
        int at = ticks - SHOW_DELAY;
        if (at < 0) return -1;
        at %= Math.max(SHOW_EVERY, order.length * (GLOW + GAP) + 40);
        int index = at / (GLOW + GAP);
        return index < order.length && at % (GLOW + GAP) < GLOW ? order[index] : -1;
    }

    /** Which rune a furnace's one showing of its order has lit at this tick, or -1: it is not shown twice. */
    public static int shownOnce(int[] order, int ticks) {
        int at = ticks - SHOW_DELAY;
        if (at < 0) return -1;
        int index = at / (GLOW + GAP);
        return index < order.length && at % (GLOW + GAP) < GLOW ? order[index] : -1;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Trap> it = new ArrayList<>(ACTIVE.values()).iterator(); it.hasNext(); ) step(it.next());
    }

    /** One tick of one trap. */
    public static void step(Trap trap) {
        if (active(trap.plan, trap.cell) != trap) return;
        List<ServerPlayer> caught = inside(trap.level, trap.plan, trap.cell);
        if (caught.isEmpty()) {
            end(trap);                                  // nobody left inside: ready for whoever comes next
            return;
        }
        trap.ticks++;
        if (trap.peril == Peril.FLOOD) {
            creep(trap, trap.perTick);
            // the sprinklers pour while the room fills
            if (!trap.full && trap.ticks % 3 == 0) {
                for (BlockPos s : sprinklers(trap.plan, trap.cell)) {
                    if (!trap.level.getBlockState(s).is(ModContent.SPRINKLER.get())) continue;
                    trap.level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.WATER_DROP.type(), s.getX() + 0.5, s.getY() + 0.15, s.getZ() + 0.5, 6, 0.35, 0.02, 0.35, 0.02);
                }
                if (trap.ticks % 30 == 0) trap.level.playSound(null, trap.plan.centre(trap.cell).above(3), SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.6F, 1.3F);
            }
        }
        if (trap.ticks >= trap.flashUntil) setLit(trap, trap.peril == Peril.EMBERS ? shownOnce(trap.shown, trap.ticks) : shown(trap.shown, trap.ticks));
        if (trap.peril != Peril.FLOOD && trap.water > 0) {
            // what a furnace or the gloom does while it builds: seen every half second, felt every two
            BlockPos m = trap.plan.centre(trap.cell);
            if (trap.ticks % 10 == 0) {
                trap.level.sendParticles(trap.peril == Peril.EMBERS ? com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type() : com.thefarawaydev.realmsofchaos.fx.RocParticles.GLOOM.type(), m.getX() + 0.5, m.getY() + (trap.peril == Peril.EMBERS ? 1.1 : 2.0), m.getZ() + 0.5,
                        6 * trap.water, 3.5, trap.peril == Peril.EMBERS ? 0.1 : 1.0, 3.5, 0.01);
            }
            if (trap.ticks % 40 == 0 && !trap.full) {
                for (ServerPlayer p : caught) {
                    if (p.isCreative()) continue;
                    if (trap.peril == Peril.EMBERS) {
                        p.hurtServer(trap.level, trap.level.damageSources().inFire(), 0.5F * trap.water);
                        p.setRemainingFireTicks(30);
                    } else {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 80, 0, true, false, false));
                        if (trap.water >= 3) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 0, true, false, false));
                    }
                }
            }
        }
        if (!trap.full && trap.ticks % fillEvery(trap.plan) == 0) raise(trap);
        if (trap.full) {
            trap.fullTicks++;
            if (trap.fullTicks % 40 == 0) {
                for (ServerPlayer p : caught) {
                    if (p.isCreative()) continue;
                    switch (trap.peril) {
                        case FLOOD -> p.hurtServer(trap.level, trap.level.damageSources().drown(), 2.0F);
                        case EMBERS -> {
                            p.hurtServer(trap.level, trap.level.damageSources().inFire(), 2.0F);
                            p.setRemainingFireTicks(60);
                        }
                        case GLOOM -> p.hurtServer(trap.level, trap.level.damageSources().wither(), 2.0F);
                    }
                }
            }
            if (trap.fullTicks >= FULL_TICKS) {
                end(trap);
                for (ServerPlayer p : caught) Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "failed" + trap.peril.suffix()), 0xFFAA00));
                return;
            }
        }
        if (trap.ticks % 20 == 0 && !trap.full) {
            for (ServerPlayer p : caught) {
                Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "status" + trap.peril.suffix(), trap.water, LAYERS, trap.pressed, trap.order.length), 0x55FFFF));
            }
        }
    }

    /** Test seam: forgets every running trap without touching the world. */
    public static void forget() {
        ACTIVE.clear();
    }
}
