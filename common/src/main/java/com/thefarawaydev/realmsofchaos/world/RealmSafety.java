package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Called once per server tick by each loader; does its work once a second for players inside a realm:
 * builds the floor they stand on if needed, shows the tower progress bar, and rescues anyone who fell into the
 * void or has been stuck inside a block by putting them back on the arrival pad.
 */
public final class RealmSafety {
    private static final Map<UUID, Integer> STUCK_SECONDS = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static int ticker;

    private RealmSafety() {}

    public static void tick(MinecraftServer server) {
        FloorShifts.tick(server);
        Scheduler.tick(server);
        Ruins.tick(server);
        HubLife.tick(server);
        Scanner.tick(server);
        Waygates.tick(server);
        Traps.tick(server);
        Rooms.tick(server);
        Snares.tick(server);
        Combat.tick(server);
        Passages.tick(server);
        Hazards.tick(server);
        Onslaughts.tick(server);
        com.thefarawaydev.realmsofchaos.item.Panoply.tick(server);
        Commissions.tick(server);
        Citadels.tick(server);
        Craters.tick(server);
        com.thefarawaydev.realmsofchaos.ui.Sidebar.tick(server);
        com.thefarawaydev.realmsofchaos.ui.Cards.tick(server);
        com.thefarawaydev.realmsofchaos.item.TarotEffects.tick(server);
        if (++ticker % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Wards.service(player);
            Skills.service(player, ticker);
            com.thefarawaydev.realmsofchaos.ui.HudFeed.options(player);
            // boons belong to the passage they were won in: out of it, they are laid down
            if (Passages.locate(player) == null) {
                if (Boons.clear(player) > 0) Hud.actionBar(player, Hud.colored(Component.translatable("hud." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".boons.gone"), 0x78E68C));
            } else if (ticker % 100 == 0) {
                Boons.apply(player);
            }
            Realm realm = Realm.of(player.level().dimension());
            if (realm == null || !(player.level() instanceof ServerLevel level)) {
                STUCK_SECONDS.remove(player.getUUID());
                hideBar(player.getUUID());
                com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.FLOOR);
                com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL);
                continue;
            }
            Line line = realm.line();
            Passages.Spot spot = Passages.locate(player);
            if (spot != null) {
                // between floors: the passage looks after its own rooms, bar and guardians
                Passages.service(player, level, spot);
                boolean out = Passages.outside(player, spot);
                int held = player.isAlive() && !player.isSpectator() && player.isInWall() ? STUCK_SECONDS.merge(player.getUUID(), 1, Integer::sum) : 0;
                if (held == 0) STUCK_SECONDS.remove(player.getUUID());
                if (player.isAlive() && !player.isSpectator() && (out || held >= 2)) {
                    rescue(player, realm);
                    STUCK_SECONDS.remove(player.getUUID());
                }
                continue;
            }
            int floor = line == null ? 0 : Tower.floorAt(player.getY());
            // someone who logged out inside a realm before an update still gets the current layout
            if (line == null) {
                RealmBuilder.ensureHub(level);
                HubTiers.sanctuary(player, level);
            }
            else TowerBuilder.ensureFloor(level, line, floor);
            updateBar(player, line, floor);

            if (!player.isAlive() || player.isSpectator()) {
                STUCK_SECONDS.remove(player.getUUID());
                continue;
            }
            boolean fell = player.getY() < (line == null ? RealmBuilder.Y - 14 : Tower.BASE_Y - Tower.DEPTH - 6);
            int stuck = player.isInWall() ? STUCK_SECONDS.merge(player.getUUID(), 1, Integer::sum) : 0;
            if (stuck == 0) STUCK_SECONDS.remove(player.getUUID());
            if (fell || stuck >= 2) {
                rescue(player, realm);
                STUCK_SECONDS.remove(player.getUUID());
            }
        }
        // forget bars of players who logged out
        for (Iterator<Map.Entry<UUID, ServerBossEvent>> it = BARS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ServerBossEvent> e = it.next();
            if (server.getPlayerList().getPlayer(e.getKey()) == null) {
                e.getValue().removeAllPlayers();
                it.remove();
            }
        }
    }

    /** The always-visible tower HUD: "Tower of Zeus - Floor 3 of 15 - Foothills of Olympus" with a climb meter. */
    public static void updateBar(ServerPlayer player, Line line, int floor) {
        // (a tower floor has no affliction: the strip under a passage plaque goes)
        com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL);
        if (line == null) {
            hideBar(player.getUUID());
            com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.FLOOR);
            return;
        }
        Component words = Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".bar", Tower.towerName(line), floor, Tower.tierName(line, floor));
        if (com.thefarawaydev.realmsofchaos.ui.Options.on(player, com.thefarawaydev.realmsofchaos.ui.Options.Option.ART)) {
            // the player's own HUD draws the plaque: which tower, how far up, and the words
            hideBar(player.getUUID());
            com.thefarawaydev.realmsofchaos.ui.HudFeed.set(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.FLOOR, line.ordinal() | (floor << 4) | (Tower.FLOORS << 12), words);
            return;
        }
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(
                UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20));
        bar.setName(words);
        bar.setColor(line.barColor());
        bar.setProgress(floor / (float) Tower.FLOORS);
        if (!bar.getPlayers().contains(player)) {
            bar.removeAllPlayers();      // a respawn or dimension change makes a new player object
            bar.addPlayer(player);
        }
    }

    /** The bar inside a passage: where it leads, and how many of its seals are broken. */
    public static void updatePassageBar(ServerPlayer player, Passages.Plan plan, int broken) {
        int total = plan.seals.size();
        Component words = Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".passage.bar", Tower.towerName(plan.line), plan.label(), plan.stage + 1, plan.stages, broken, total);
        Passages.Affliction ill = plan.affliction();
        boolean held = Passages.sheltered(player);
        Component weighs = ill == Passages.Affliction.NONE ? null
                : Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".passage." + (held ? "ill.held" : "ill"), ill.displayName());
        if (com.thefarawaydev.realmsofchaos.ui.Options.on(player, com.thefarawaydev.realmsofchaos.ui.Options.Option.ART)) {
            hideBar(player.getUUID());
            com.thefarawaydev.realmsofchaos.ui.HudFeed.set(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.FLOOR, plan.line.ordinal() | 4 | (broken << 4) | (total << 12), words);
            // (what weighs on the level has a strip of its own under the plaque)
            if (weighs == null) com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL);
            else com.thefarawaydev.realmsofchaos.ui.HudFeed.set(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL, ill.ordinal() | (held ? com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL_HELD : 0), weighs);
            return;
        }
        com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.FLOOR);
        com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.ILL);
        if (weighs != null) words = Component.empty().append(words).append(" - ").append(weighs);
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(
                UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20));
        bar.setName(words);
        bar.setColor(plan.line.barColor());
        bar.setProgress(total == 0 ? 1.0F : broken / (float) total);
        if (!bar.getPlayers().contains(player)) {
            bar.removeAllPlayers();
            bar.addPlayer(player);
        }
    }

    private static void hideBar(UUID id) {
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) bar.removeAllPlayers();
    }

    public static void rescue(ServerPlayer player, Realm realm) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 pos;
        float yaw;
        if (realm.line() == null) {
            pos = realm.spawn();
            yaw = realm.yaw();
            RealmBuilder.makeSafe(level, pos.x, pos.y, pos.z);
        } else if (Passages.locate(player) != null) {
            Passages.Spot spot = Passages.locate(player);
            Passages.ensure(level, spot.line(), spot.index(), spot.stage());
            pos = Passages.plan(level, spot).arrival();
            yaw = 0.0F;
        } else {
            int floor = Tower.floorAt(player.getY());
            TowerBuilder.ensureFloor(level, realm.line(), floor);
            pos = Tower.arrival(floor);
            yaw = Tower.ARRIVAL_YAW;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, 0.0F, true);
        Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".rescued"), 0x55FFFF));
    }
}
