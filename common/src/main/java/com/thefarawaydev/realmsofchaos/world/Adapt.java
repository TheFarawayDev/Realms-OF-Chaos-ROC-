package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.god.God;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How the tower adapts to who is climbing it. A trial faced by several players is a harder trial: the god has more
 * health and hits harder for every extra challenger. And a god that keeps beating the same player relents a
 * little each time, so nobody is stuck on one floor for ever.
 */
public final class Adapt {
    public static final int MAX_CHALLENGERS = 4, MAX_MERCY = 3;
    private static final Map<String, Integer> LOSSES = new HashMap<>();

    private Adapt() {}

    /** Health multiplier for a god facing this many players: +60% for each one after the first. */
    public static double healthScale(int challengers) {
        return 1.0 + 0.6 * (Math.max(1, Math.min(MAX_CHALLENGERS, challengers)) - 1);
    }

    /** Damage multiplier for a god's moves and shots: +15% for each challenger after the first. */
    public static float damageScale(int challengers) {
        return 1.0F + 0.15F * (Math.max(1, Math.min(MAX_CHALLENGERS, challengers)) - 1);
    }

    /** Health multiplier after losses: 8% less for each time this god has beaten the player, up to three. */
    public static double mercyScale(int losses) {
        return 1.0 - 0.08 * Math.max(0, Math.min(MAX_MERCY, losses));
    }

    /** Extra guardians a sealed chamber wakes for a group: one for each player after the first, up to two. */
    public static int extraGuardians(int players) {
        return Math.max(0, Math.min(2, players - 1));
    }

    private static String key(UUID player, God god) {
        return player + "/" + god.id();
    }

    public static int losses(UUID player, God god) {
        return LOSSES.getOrDefault(key(player, god), 0);
    }

    public static void recordLoss(UUID player, God god) {
        LOSSES.merge(key(player, god), 1, Integer::sum);
    }

    public static void clearLosses(UUID player, God god) {
        LOSSES.remove(key(player, god));
    }
}
