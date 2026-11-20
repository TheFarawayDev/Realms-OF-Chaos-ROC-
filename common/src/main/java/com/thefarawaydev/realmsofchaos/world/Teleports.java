package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Moves players between the overworld, the hub and the floors of the three towers. */
public final class Teleports {
    private static final Map<UUID, Long> LAST_TRAVEL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TICKS = 40;
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".";

    private Teleports() {}

    /** Gates fire every tick while a player stands in them; only one trip (or notice) per two seconds. */
    private static boolean throttled(Map<UUID, Long> map, ServerPlayer player, MinecraftServer server) {
        long now = server.getTickCount();
        Long last = map.get(player.getUUID());
        if (last != null && now >= last && now - last < COOLDOWN_TICKS) return true;
        map.put(player.getUUID(), now);
        return false;
    }

    /** Remembers where a player was before stepping into the realms, so "leave" can put them back. */
    public static void rememberReturn(ServerPlayer player, MinecraftServer server) {
        if (Realm.of(player.level().dimension()) != null || Hermitage.isIn(player.level())) return;
        ProgressData.get(server).setReturnPoint(player.getUUID(), new ProgressData.ReturnPoint(
                player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot()));
    }

    private static void move(ServerPlayer player, ServerLevel dest, Vec3 pos, float yaw) {
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(dest, pos.x, pos.y, pos.z, Set.of(), yaw, 0.0F, true);
    }

    /** For the passages: put a player somewhere in a realm, with the same care as every other journey here. */
    static void moveTo(ServerPlayer player, ServerLevel dest, Vec3 pos, float yaw) {
        MinecraftServer server = player.level().getServer();
        if (server != null) rememberReturn(player, server);
        move(player, dest, pos, yaw);
    }

    private static ServerLevel realmLevel(ServerPlayer player, MinecraftServer server, Realm realm) {
        ServerLevel dest = realm.level(server);
        if (dest == null) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "unavailable"), 0xFF5555));
        return dest;
    }

    public static void travel(ServerPlayer player, Realm to) {
        if (to == Realm.HUB) toHub(player);
        else enterTower(player, to.line());
    }

    public static void toHub(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || throttled(LAST_TRAVEL, player, server)) return;
        ServerLevel dest = realmLevel(player, server, Realm.HUB);
        if (dest == null) return;
        Realm from = Realm.of(player.level().dimension());
        rememberReturn(player, server);
        RealmBuilder.ensureHub(dest);
        Vec3 pos = Realm.HUB.spawn();
        float yaw = Realm.HUB.yaw();
        if (from != null && from != Realm.HUB) {
            pos = Realm.hubArrival(from);
            yaw = Realm.hubArrivalYaw(from);
        }
        RealmBuilder.makeSafe(dest, pos.x, pos.y, pos.z);
        move(player, dest, pos, yaw);
        Hud.title(player, Hud.colored(Component.translatable(HUD + "hub"), 0xA55AD6),
                Hud.colored(Component.translatable(HUD + "hub.sub"), 0xCCCCCC));
    }

    /** Hub gate / Codex: go to the floor this player is currently working on. */
    public static void enterTower(ServerPlayer player, Line line) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        toFloor(player, line, Tower.currentFloor(ProgressData.get(server), player.getUUID(), line));
    }

    /** Travel to a floor the player has reached. Floors above their progress are refused. */
    public static void toFloor(ServerPlayer player, Line line, int floor) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ProgressData data = ProgressData.get(server);
        floor = Math.min(Tower.clampFloor(floor), Tower.currentFloor(data, player.getUUID(), line));
        if (throttled(LAST_TRAVEL, player, server)) return;
        ServerLevel dest = realmLevel(player, server, Realm.ofLine(line));
        if (dest == null) return;
        rememberReturn(player, server);
        TowerBuilder.ensureFloor(dest, line, floor);
        move(player, dest, Tower.arrival(floor), Tower.ARRIVAL_YAW);
        Hud.title(player, Hud.colored(Tower.tierName(line, floor), line.color()),
                Hud.colored(Component.translatable(HUD + "floor", Tower.towerName(line), floor), 0xCCCCCC));
    }

    /**
     * The Gate of Ascent. On a tower floor it opens once the floor's god has fallen to this player, and leads into
     * the passage to the next floor - or straight up, for a player who has walked that passage before. Inside a
     * passage it is the way out, and opens when every seal is broken.
     */
    public static void ascend(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Realm realm = Realm.of(player.level().dimension());
        if (server == null || realm == null || realm.line() == null) return;
        Passages.Spot spot = Passages.locate(player);
        if (spot != null) {
            Passages.tryExit(player, spot);
            return;
        }
        int floor = Tower.floorAt(player.getY());
        God god = Tower.god(realm.line(), floor);
        if (!ProgressData.get(server).isDefeated(player.getUUID(), god)) {
            if (!throttled(LAST_NOTICE, player, server)) {
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "ascend.locked", god.displayName()), 0xFF5555));
            }
            return;
        }
        if (floor >= Tower.FLOORS) return;
        if (ProgressData.get(server).hasPassed(player.getUUID(), realm.line(), floor)) {
            toFloor(player, realm.line(), floor + 1);
        } else if (!throttled(LAST_TRAVEL, player, server)) {
            Passages.enter(player, realm.line(), floor);
        }
    }

    /** Where leaving the realms takes a player. */
    public record Exit(ServerLevel level, Vec3 pos, float yaw) {}

    /** The remembered place the player entered from if it still exists, otherwise the world spawn. */
    public static Exit exitFor(MinecraftServer server, ServerPlayer player) {
        ProgressData.ReturnPoint point = ProgressData.get(server).returnPoint(player.getUUID());
        if (point != null) {
            Identifier id = Identifier.tryParse(point.dimension());
            ServerLevel saved = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (saved != null && Realm.of(saved.dimension()) == null && !Hermitage.isIn(saved)) {
                return new Exit(saved, new Vec3(point.x(), point.y(), point.z()), point.yaw());
            }
        }
        var respawn = server.getRespawnData();
        ServerLevel dest = server.getLevel(respawn.globalPos().dimension());
        if (dest == null) dest = server.overworld();
        BlockPos spawn = respawn.globalPos().pos();
        dest.getChunk(spawn);
        int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new Exit(dest, new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5), respawn.yaw());
    }

    /** Leave the realms: back to where the player came from, or the world spawn if that is unknown. */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        if (Realm.of(player.level().dimension()) == null && !Hermitage.isIn(player.level())) {
            if (!throttled(LAST_NOTICE, player, server)) {
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "not_inside"), 0xFF5555));
            }
            return;
        }
        if (throttled(LAST_TRAVEL, player, server)) return;
        Exit exit = exitFor(server, player);
        move(player, exit.level(), exit.pos(), exit.yaw());
        Hud.title(player, Hud.colored(Component.translatable(HUD + "left"), 0x55FF55),
                Hud.colored(Component.translatable(HUD + "left.sub"), 0xCCCCCC));
    }
}
