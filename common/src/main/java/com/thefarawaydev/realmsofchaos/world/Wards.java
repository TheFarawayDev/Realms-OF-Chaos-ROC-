package com.thefarawaydev.realmsofchaos.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * The realms cannot be dug through or built in. A survival player is put in adventure mode for as long as they are
 * inside the hub, a tower or a passage - they can still fight, open things and use everything, but not break or
 * place blocks - and given survival mode back the moment they are out. Creative and spectator players are left as
 * they are.
 */
public final class Wards {
    private Wards() {}

    private static String key(ServerPlayer player) {
        return "warded/" + player.getUUID();
    }

    /** Puts the player in the right mode for where they are. Cheap; called on every journey and once a second. */
    public static void service(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ProgressData data = ProgressData.get(server);
        boolean inside = Realm.of(player.level().dimension()) != null;
        if (inside) {
            if (player.gameMode() == GameType.SURVIVAL) {
                player.setGameMode(GameType.ADVENTURE);
                data.markBuilt(key(player));
            }
        } else if (data.isBuilt(key(player))) {
            if (player.gameMode() == GameType.ADVENTURE) player.setGameMode(GameType.SURVIVAL);
            data.unmarkBuilt(key(player));
        }
    }

    /** True while the player is in adventure mode because of the realms. */
    public static boolean warded(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && ProgressData.get(server).isBuilt(key(player));
    }
}
