package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The tutorial. A new player is shown the Climber's Handbook once, a few seconds after they first join. After
 * that, tips appear on screen the first time each thing happens to them - the first Ruined Gate, the first altar,
 * the first seal - each once, and never again. The handbook itself is always in the Codex.
 */
public final class Tutorial {
    /** How long after the event a tip appears, so it follows the event's own title instead of replacing it. */
    public static final int DELAY = 50;

    public enum Tip {
        RUIN, HUB, FLOOR, TRIAL, VICTORY, PASSAGE, SEAL, FORGE, UPHEAVAL, AEGIS, WRATH, SCAN, WAYGATE;

        public String id() {
            return name().toLowerCase();
        }

        public Component text() {
            return Component.translatable("tip." + RealmsOfChaos.MOD_ID + "." + id());
        }
    }

    private Tutorial() {}

    private static String key(ServerPlayer player, Tip tip) {
        return "tip/" + player.getUUID() + "/" + tip.id();
    }

    public static boolean seen(ServerPlayer player, Tip tip) {
        MinecraftServer server = player.level().getServer();
        return server != null && ProgressData.get(server).isBuilt(key(player, tip));
    }

    /** Shows a tip if this player has not had it yet. @return true if it will be shown. */
    public static boolean tip(ServerPlayer player, Tip tip) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ProgressData data = ProgressData.get(server);
        if (data.isBuilt(key(player, tip))) return false;
        data.markBuilt(key(player, tip));
        Scheduler.after(server, DELAY, () -> {
            if (!player.isAlive() || player.hasDisconnected()) return;
            Hud.title(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".tip"), 0x55FFFF), Hud.colored(tip.text(), 0xFFFFFF), 10, 120, 20);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        });
        return true;
    }

    /** The first-join welcome: the handbook's first topic, opened as a screen a few seconds in. */
    public static void welcome(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Scheduler.after(server, 80, () -> {
            if (player.isAlive() && !player.hasDisconnected()) Screens.show(player, Screens.topic(player, HandbookData.ALL.get(0)));
        });
    }

    /** Test seam and "show me again": forgets which tips a player has had. */
    public static void reset(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).unmarkPrefix("tip/" + player.getUUID() + "/");
    }
}
