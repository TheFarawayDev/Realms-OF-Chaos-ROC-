package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a compass has to say, put on a tab that slides out from the right-hand edge of the screen for a few seconds
 * and slides away again: not a line above the hotbar. The tab has a dial with a needle that keeps pointing the right
 * way as the player turns, the thing pointed at, how far, the signal, and how long the scan has left.
 *
 * <p>It is told through {@link HudFeed} like the rest of the HUD: a number that packs the needle's bearing and the
 * state of the scan, and a piece of text with four parts - the heading, the target, the distance, the state. With
 * the drawn interface switched off the old line above the hotbar is used instead.
 */
public final class CompassTab {
    /** The state a reading is in. */
    public static final int NORMAL = 0, LOCK = 1, SURGE = 2, NOTICE = 3;
    /** No bearing: the needle rests. */
    public static final int NO_BEARING = 511;

    private static final Map<UUID, Integer> SEQUENCE = new HashMap<>();

    private CompassTab() {}

    /** Packs a reading into the tab's number. {@code progress} 0..1 of the scan; {@code secondsLeft} up to 127. */
    public static int pack(int bearing, int quality, int state, boolean seeker, int secondsLeft, double progress, int sequence) {
        int b = bearing < 0 || bearing >= 360 ? NO_BEARING : bearing;
        return b | (Math.max(0, Math.min(5, quality)) << 9) | ((state & 3) << 12) | ((seeker ? 1 : 0) << 16)
                | (Math.max(0, Math.min(127, secondsLeft)) << 17) | ((int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 15) << 24) | ((sequence & 3) << 28);
    }

    public static int bearing(int value) {
        return value & 511;
    }

    public static int quality(int value) {
        return (value >> 9) & 7;
    }

    public static int state(int value) {
        return (value >> 12) & 3;
    }

    public static boolean seeker(int value) {
        return ((value >> 16) & 1) != 0;
    }

    public static int secondsLeft(int value) {
        return (value >> 17) & 127;
    }

    public static float progress(int value) {
        return ((value >> 24) & 15) / 15.0F;
    }

    /** The yaw that would face something {@code dx}, {@code dz} away, as the game measures yaw: 0 south, 90 west. */
    public static int bearing(double dx, double dz) {
        return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-dx, dz))), 360);
    }

    public static Component distance(double blocks, boolean rough) {
        int d = (int) Math.round(blocks);
        if (rough) d = Math.max(10, (int) Math.round(d / 10.0) * 10);
        return Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".tab.distance." + (rough ? "rough" : "exact"), d);
    }

    /**
     * Slides the tab out with a reading. @param heading what is speaking, in its colour @param target what it points
     * at @param distance how far, or null @param status a word on the state - a clean lock, a surge - or null
     */
    public static void show(ServerPlayer player, Component heading, Component target, Component distance, Component status, int value) {
        MutableComponent text = Component.empty().append(heading).append(target == null ? Component.empty() : target)
                .append(distance == null ? Component.empty() : distance).append(status == null ? Component.empty() : status);
        // (a fresh number every time, so the tab slides out again even for the same words)
        int sequence = SEQUENCE.merge(player.getUUID(), 1, Integer::sum);
        HudFeed.set(player, HudFeed.COMPASS, (value & ~(3 << 28)) | ((sequence & 3) << 28), text);
    }

    /** A plain notice on the tab: the compass has something to say that is not a reading. */
    public static void notice(ServerPlayer player, Component heading, Component message) {
        show(player, heading, message, null, null, pack(-1, 0, NOTICE, false, 0, 0.0, 0));
    }

    /** Whether the tab is to be used for this player, rather than the line above the hotbar. */
    public static boolean drawn(ServerPlayer player) {
        return Options.on(player, Options.Option.ART);
    }

    public static Integer last(ServerPlayer player) {
        return HudFeed.value(player, HudFeed.COMPASS);
    }
}
