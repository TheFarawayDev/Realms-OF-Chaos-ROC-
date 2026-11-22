package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Options;
import com.thefarawaydev.realmsofchaos.ui.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the gods have learnt about each player. Within a trial a god answers how it is being fought (its stance);
 * this is the longer memory. Every trial is watched - how much of a player's harm came from afar, how often they
 * hid behind a shield, how much they mended, how long they kept away - and a habit seen in trial after trial is
 * remembered. The next god a player meets already knows it, and has an answer ready. A habit a player drops is
 * forgotten again, one trial at a time.
 */
public final class Insight {
    /** A habit's standing runs from 0 to this; every two points are a level. */
    public static final int MAX_POINTS = 6, MAX_LEVEL = 3;
    /** A trial shorter than this teaches a god nothing, and makes it forget nothing. */
    public static final int MIN_TICKS = 20 * 20;

    public enum Habit {
        /** Most of the harm done from afar. Answered: shots count for less, and the god comes for the archer. */
        ARCHER,
        /** Nearly all of it hand to hand. Answered: the god's guard lashes back at the blade. */
        DUELLIST,
        /** Blows taken on a shield. Answered: the god's blows knock the shield aside. */
        GUARD,
        /** Wounds mended in the middle of the fight. Answered: a strike falls where the mending is done. */
        MENDER,
        /** Most of the trial spent out of reach. Answered: the god hauls the runner back. */
        RUNNER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("insight." + RealmsOfChaos.MOD_ID + "." + id());
        }

        /** What the gods do about it. */
        public Component answer() {
            return Component.translatable("insight." + RealmsOfChaos.MOD_ID + "." + id() + ".answer");
        }

        /** What a player does to earn it. */
        public Component cause() {
            return Component.translatable("insight." + RealmsOfChaos.MOD_ID + "." + id() + ".cause");
        }
    }

    /** One player's part in one trial, as the god saw it. */
    public static final class Tally {
        public float melee, ranged, healed;
        public int blocks, ticks, farTicks;

        /** Did this trial show the habit? */
        public boolean shows(Habit habit) {
            float dealt = melee + ranged;
            return switch (habit) {
                case ARCHER -> dealt >= 20.0F && ranged / dealt >= 0.6F;
                case DUELLIST -> dealt >= 20.0F && melee / dealt >= 0.75F;
                case GUARD -> blocks >= 3;
                case MENDER -> healed >= 16.0F;
                case RUNNER -> ticks >= MIN_TICKS && farTicks * 2 >= ticks;
            };
        }
    }

    private Insight() {}

    private static String key(UUID player, Habit habit) {
        return "insight/" + player + "/" + habit.id();
    }

    public static int points(MinecraftServer server, UUID player, Habit habit) {
        return Math.max(0, Math.min(MAX_POINTS, ProgressData.get(server).counter(key(player, habit))));
    }

    /** 0 for a habit the gods have not noticed, up to 3 for one they know well. */
    public static int level(MinecraftServer server, UUID player, Habit habit) {
        return (points(server, player, habit) + 1) / 2;
    }

    public static int level(ServerPlayer player, Habit habit) {
        MinecraftServer server = player.level().getServer();
        return server == null ? 0 : level(server, player.getUUID(), habit);
    }

    public static void setPoints(MinecraftServer server, UUID player, Habit habit, int points) {
        ProgressData.get(server).setCounter(key(player, habit), Math.max(0, Math.min(MAX_POINTS, points)));
    }

    /**
     * A trial is over: what it showed is remembered a little better, what it did not show a little less.
     * @return the habits whose level changed
     */
    public static List<Habit> learn(MinecraftServer server, UUID player, Tally tally) {
        List<Habit> changed = new ArrayList<>();
        if (tally.ticks < MIN_TICKS) return changed;
        for (Habit habit : Habit.values()) {
            int before = points(server, player, habit), after = tally.shows(habit) ? before + 2 : before - 1;
            after = Math.max(0, Math.min(MAX_POINTS, after));
            if (after == before) continue;
            setPoints(server, player, habit, after);
            if ((after + 1) / 2 != (before + 1) / 2) changed.add(habit);
        }
        return changed;
    }

    /** Everything the gods know of a player, strongest first. */
    public static List<Habit> known(ServerPlayer player) {
        List<Habit> out = new ArrayList<>();
        for (Habit habit : Habit.values()) if (level(player, habit) > 0) out.add(habit);
        out.sort((a, b) -> level(player, b) - level(player, a));
        return out;
    }

    public static String numeral(int level) {
        return level <= 1 ? "I" : level == 2 ? "II" : "III";
    }

    /** At the start of a trial: a card naming what this god already knows of its challenger. @return true if one was shown */
    public static boolean announce(ServerPlayer player, God god) {
        List<Habit> known = known(player);
        if (known.isEmpty() || !Options.on(player, Options.Option.INSIGHT)) return false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(2, known.size()); i++) {
            Habit habit = known.get(i);
            if (i > 0) text.append(' ');
            text.append(Words.of(habit.displayName())).append(' ').append(numeral(level(player, habit))).append(": ")
                    .append(Words.of(habit.answer())).append('.');
        }
        Cards.insight(player, Words.of("insight." + RealmsOfChaos.MOD_ID + ".remembers", god.displayName()), text.toString());
        com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.INSIGHT);
        return true;
    }

    public static void forget(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).clearCounters("insight/" + player.getUUID() + "/");
    }
}
