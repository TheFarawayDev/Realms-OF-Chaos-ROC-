package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Moves players between the hub and the three god zones. */
public final class Teleports {
    private static final Map<UUID, Long> LAST_TRAVEL = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TICKS = 40;

    private Teleports() {}

    public static void travel(ServerPlayer player, Realm to) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        long now = server.getTickCount();
        Long last = LAST_TRAVEL.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS && now >= last) return;

        ServerLevel dest = server.getLevel(to.key());
        if (dest == null) {
            player.sendSystemMessage(Component.literal("[" + RealmsOfChaos.MOD_NAME + "] The realm '" + to.id() + "' is not loaded."));
            return;
        }
        LAST_TRAVEL.put(player.getUUID(), now);

        Realm from = Realm.of(player.level().dimension());
        RealmBuilder.ensureBuilt(dest, to);

        Vec3 pos = to.spawn();
        float yaw = to.yaw();
        if (to == Realm.HUB && from != null && from != Realm.HUB) {
            pos = Realm.hubArrival(from);
            yaw = Realm.hubArrivalYaw(from);
        }
        player.teleportTo(dest, pos.x, pos.y, pos.z, Set.of(), yaw, 0.0F, true);

        if (to == Realm.HUB) {
            player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".hub_welcome"));
        } else {
            player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".enter_zone", to.line().zoneName()));
        }
    }
}
