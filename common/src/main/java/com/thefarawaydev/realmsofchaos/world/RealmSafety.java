package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Called once per server tick by each loader. Once a second it rescues players inside a realm who fell into the
 * void or have been stuck inside a block, by putting them back on that realm's arrival pad.
 */
public final class RealmSafety {
    private static final Map<UUID, Integer> STUCK_SECONDS = new HashMap<>();
    private static int ticker;

    private RealmSafety() {}

    public static void tick(MinecraftServer server) {
        if (++ticker % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Realm realm = Realm.of(player.level().dimension());
            if (realm == null || !player.isAlive() || player.isSpectator()) {
                STUCK_SECONDS.remove(player.getUUID());
                continue;
            }
            boolean fell = player.getY() < RealmBuilder.Y - 14;
            int stuck = player.isInWall() ? STUCK_SECONDS.merge(player.getUUID(), 1, Integer::sum) : 0;
            if (stuck == 0) STUCK_SECONDS.remove(player.getUUID());
            if (fell || stuck >= 2) {
                rescue(player, realm);
                STUCK_SECONDS.remove(player.getUUID());
            }
        }
    }

    public static void rescue(ServerPlayer player, Realm realm) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 pos = realm.spawn();
        RealmBuilder.makeSafe(level, pos.x, pos.y, pos.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), realm.yaw(), 0.0F, true);
        player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".rescued"));
    }
}
