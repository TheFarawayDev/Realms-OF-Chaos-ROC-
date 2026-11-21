package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.world.Bounties;
import com.thefarawaydev.realmsofchaos.world.HubTiers;
import com.thefarawaydev.realmsofchaos.world.Parties;
import com.thefarawaydev.realmsofchaos.world.Passages;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Scanner;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * The sidebar: a panel down the right of the screen that says where you are and what is going on - the tower and
 * floor, a trial's phase, pylons and the ring that is left, the hub's tier, the compass's signal, today's
 * bounties, your party. It is off until a player turns it on ({@code /roc sidebar}, or the Codex) and each player
 * has their own: it is sent to that player alone and never touches the world's real scoreboard.
 */
public final class Sidebar {
    private static final String OBJECTIVE = "roc_sidebar", NS = "sidebar." + RealmsOfChaos.MOD_ID + ".";
    public static final int MAX_LINES = 14;

    /** What one player is being shown. A new player object (a new login) starts from nothing. */
    private record Shown(ServerPlayer player, List<String> lines) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    private static int ticker;

    private Sidebar() {}

    private static String key(ServerPlayer player) {
        return "sidebar/" + player.getUUID();
    }

    public static boolean enabled(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && ProgressData.get(server).isBuilt(key(player));
    }

    /** Turns the sidebar on or off for a player. @return whether it is now on. */
    public static boolean toggle(ServerPlayer player) {
        ProgressData data = ProgressData.get(player.level().getServer());
        boolean on = !data.isBuilt(key(player));
        if (on) data.markBuilt(key(player));
        else data.unmarkBuilt(key(player));
        update(player);
        Hud.actionBar(player, Hud.colored(Component.translatable(NS + (on ? "on" : "off")), on ? 0x55FF55 : 0xAAAAAA));
        return on;
    }

    private static MutableComponent line(String key, int color, Object... args) {
        return Component.translatable(NS + key, args).withStyle(Style.EMPTY.withColor(color));
    }

    /** Everything the sidebar would say to this player right now, top to bottom. */
    public static List<Component> lines(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        MinecraftServer server = player.level().getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) return out;
        Realm realm = Realm.of(level.dimension());
        Passages.Spot spot = realm == null ? null : Passages.locate(player);
        // ---- where
        if (spot != null) {
            Passages.Plan plan = Passages.plan(level, spot);
            out.add(line("passage", spot.line().color(), spot.index() + 1));
            out.add(line("passage.level", 0xCCCCCC, spot.stage() + 1, plan.stages));
            out.add(line("passage.seals", 0x55FFFF, plan.seals.size() - Passages.sealsLeft(level, plan), plan.seals.size()));
        } else if (realm == null) {
            out.add(line("outside", 0xCCCCCC));
        } else if (realm.line() == null) {
            out.add(line("hub", 0xA55AD6));
        } else {
            int floor = Tower.floorAt(player.getY());
            out.add(Tower.towerName(realm.line()).copy().withStyle(Style.EMPTY.withColor(realm.line().color())));
            out.add(line("floor", 0xCCCCCC, floor, Tower.FLOORS));
        }
        // ---- a trial close by
        for (GodBoss boss : level.getEntitiesOfClass(GodBoss.class, player.getBoundingBox().inflate(48), b -> b.isAlive() && !b.isStatue())) {
            int health = Math.round(boss.getHealth() / boss.getMaxHealth() * 100);
            out.add(boss.god().displayName().copy().withStyle(Style.EMPTY.withColor(boss.god().line().color())));
            out.add(line("trial.phase." + (boss.phase() >= 3 ? "wrath" : boss.aegisUp() ? "aegis" : boss.phase() == 2 ? "rage" : "first"), 0xFFAA00, health));
            if (boss.aegisUp()) out.add(line("trial.pylons", 0x55FFFF, boss.pylons().size()));
            if (boss.safeRadius() > 0) out.add(line("trial.ring", 0xFF5555, (int) boss.safeRadius()));
            if (boss.stance() != GodBoss.Stance.BALANCED) out.add(line("trial.stance." + boss.stance().name().toLowerCase(java.util.Locale.ROOT), 0xCCCCCC));
            break;
        }
        // ---- the hub, the compass, the party
        int tier = HubTiers.tier(server);
        out.add(line("hubtier", 0xA55AD6, tier, HubTiers.tierName(tier)));
        if (realm == null) {
            out.add(line(Scanner.scanning(player) ? "signal.scanning" : "signal", 0x55FFFF, Scanner.bars(Scanner.quality(level, player))));
        }
        int party = Parties.members(player.getUUID()).size();
        if (party > 1) out.add(line("party", 0x55FF55, party));
        // ---- today's bounties
        if (Bounties.open(server)) {
            for (Bounties.Bounty bounty : Bounties.today(server)) {
                int done = Bounties.progress(player, bounty);
                boolean claimed = Bounties.claimed(player, bounty);
                out.add(line(claimed ? "bounty.claimed" : done >= bounty.goal() ? "bounty.done" : "bounty", claimed ? 0x888888 : done >= bounty.goal() ? 0x55FF55 : 0xFFFFFF,
                        bounty.text(), done, bounty.goal()));
            }
        }
        return out.size() > MAX_LINES ? out.subList(0, MAX_LINES) : out;
    }

    private static Objective objective() {
        return new Objective(new Scoreboard(), OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.translatable(NS + "title").withStyle(Style.EMPTY.withColor(0xF5B82E).withBold(true)), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    public static boolean showing(ServerPlayer player) {
        Shown shown = SHOWN.get(player.getUUID());
        return shown != null && shown.player() == player;
    }

    public static int shownLines(ServerPlayer player) {
        return showing(player) ? SHOWN.get(player.getUUID()).lines().size() : 0;
    }

    /** Brings one player's sidebar up to date: shows it, changes the lines that changed, or takes it away. */
    public static void update(ServerPlayer player) {
        Shown shown = SHOWN.get(player.getUUID());
        if (shown != null && shown.player() != player) shown = null;                // a new login: the client has nothing
        if (!enabled(player)) {
            if (SHOWN.remove(player.getUUID()) != null) player.connection.send(new ClientboundSetObjectivePacket(objective(), ClientboundSetObjectivePacket.METHOD_REMOVE));
            return;
        }
        List<Component> lines = lines(player);
        List<String> text = new ArrayList<>();
        for (Component c : lines) text.add(c.toString());      // (the whole component: its key, its arguments and its colour)
        if (shown == null) {
            Objective objective = objective();
            // (removing first makes adding safe whatever the client already has)
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
            shown = new Shown(player, new ArrayList<>());
        }
        List<String> old = shown.lines();
        boolean reorder = old.size() != text.size();        // scores are positions counted from the bottom: a new length moves every line
        for (int i = 0; i < text.size(); i++) {
            if (reorder || !text.get(i).equals(old.get(i))) {
                player.connection.send(new ClientboundSetScorePacket("line" + i, OBJECTIVE, text.size() - i, Optional.of(lines.get(i)), Optional.of(BlankFormat.INSTANCE)));
            }
        }
        for (int i = text.size(); i < old.size(); i++) player.connection.send(new ClientboundResetScorePacket("line" + i, OBJECTIVE));
        SHOWN.put(player.getUUID(), new Shown(player, text));
    }

    /** Called every server tick; refreshes every sidebar once a second. */
    public static void tick(MinecraftServer server) {
        if (++ticker % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (enabled(player) || SHOWN.containsKey(player.getUUID())) update(player);
        }
        for (Iterator<Map.Entry<UUID, Shown>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().player().hasDisconnected()) it.remove();
        }
    }
}
