package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.world.ProgressData;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What each player has chosen to see. Tips and what the gods learn are shown unless turned off; the cast bar - a
 * god's move named and counted down as it winds up - is shown only to those who turn it on.
 */
public final class Options {
    public enum Option {
        /** The cast bar. Off until asked for. */
        CASTS(false),
        /** Tips, the first time each thing happens. */
        TIPS(true),
        /** A card when a god remembers how you fight. */
        INSIGHT(true);

        public final boolean byDefault;

        Option(boolean byDefault) {
            this.byDefault = byDefault;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Options() {}

    private static String key(ServerPlayer player, Option option) {
        return "opt/" + player.getUUID() + "/" + option.id();
    }

    public static boolean on(ServerPlayer player, Option option) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return option.byDefault;
        int stored = ProgressData.get(server).counter(key(player, option));     // 0: never chosen, 1: on, 2: off
        return stored == 0 ? option.byDefault : stored == 1;
    }

    public static void set(ServerPlayer player, Option option, boolean on) {
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).setCounter(key(player, option), on ? 1 : 2);
    }

    /** @return what it is now */
    public static boolean toggle(ServerPlayer player, Option option) {
        boolean next = !on(player, option);
        set(player, option, next);
        return next;
    }

    /** Back to how a new player has them. */
    public static void reset(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).clearCounters("opt/" + player.getUUID() + "/");
    }
}
