package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Who may travel to the realms. The gates were broken: a player has to find a Ruined Gate in the Overworld, wake it
 * with the three keystones and step through. From then on they are attuned, and the Compass of Chaos, the Codex and
 * the commands will carry them to the hub from anywhere.
 */
public final class Access {
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private Access() {}

    private static String key(UUID player) {
        return "attuned/" + player;
    }

    /**
     * True for a player a gate knows. Players who already woke a god before gates were needed count, and so does
     * anyone in creative mode.
     */
    public static boolean attuned(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ProgressData data = ProgressData.get(server);
        return data.isBuilt(key(player.getUUID())) || data.total(player.getUUID()) > 0 || player.isCreative();
    }

    /** Stepping through a woken gate. @return true the first time. */
    public static boolean attune(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ProgressData data = ProgressData.get(server);
        if (data.isBuilt(key(player.getUUID()))) return false;
        boolean wasKnown = attuned(player);
        data.markBuilt(key(player.getUUID()));
        if (wasKnown) return false;
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".access.";
        Hud.title(player, Hud.colored(Component.translatable(hud + "attuned"), 0xA55AD6), Hud.colored(Component.translatable(hud + "attuned.sub"), 0xCCCCCC));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    /**
     * May this player travel into the realms right now? Anyone already inside may move about freely; from outside
     * it takes attunement. Says why not, at most every two seconds.
     */
    public static boolean check(ServerPlayer player) {
        if (Realm.of(player.level().dimension()) != null || attuned(player)) return true;
        MinecraftServer server = player.level().getServer();
        long now = server == null ? 0 : server.getTickCount();
        Long last = LAST_NOTICE.get(player.getUUID());
        if (last == null || now < last || now - last >= 40) {
            LAST_NOTICE.put(player.getUUID(), now);
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".access.closed"), 0xFF5555));
        }
        return false;
    }
}
