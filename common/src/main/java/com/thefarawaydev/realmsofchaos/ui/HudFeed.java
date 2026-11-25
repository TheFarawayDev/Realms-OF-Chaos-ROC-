package com.thefarawaydev.realmsofchaos.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * What the mod's own HUD is told. The HUD is drawn by the player's game (see the client's RocHud): a boss panel,
 * a plaque for the floor or passage, cards, a combat meter. What a god is doing it reads from the god itself; the
 * rest comes through here, as lines of a scoreboard objective that is never put on show - a number and a piece of
 * text for each thing the HUD draws. It uses only the game's own packets, so it is the same on Fabric and Forge.
 */
public final class HudFeed {
    public static final String OBJECTIVE = "roc_hud";
    /** The lines: the floor or passage plaque, the card showing, the combat meter, and what the player has switched on. */
    public static final String FLOOR = "floor", CARD = "card", METER = "meter", OPTIONS = "opts";
    /** What weighs on the level a player is on: which affliction, and 16 more while a shrine holds it off. */
    public static final String ILL = "ill";
    /** What a compass last said: see {@link CompassTab}. */
    public static final String COMPASS = "compass";
    /** A line too long for the game's own action bar: the HUD writes it above the hotbar on two or three lines instead. */
    public static final String SAY = "say";
    /** Which of the mod's tracks the player's game should play: see {@link com.thefarawaydev.realmsofchaos.world.MusicCues}. */
    public static final String MUSIC = "music";
    public static final int ILL_HELD = 16;
    /** Bits of the options line. */
    public static final int OPT_CASTS = 1, OPT_COMBAT = 2, OPT_ART = 4;
    /** The build of the mod the world is run by: a game with a different one says so. */
    public static final String BUILD = "build";

    private record Line(int value, String text) {}

    private static final class Sent {
        Object connection;
        final Map<String, Line> lines = new HashMap<>();
    }

    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private HudFeed() {}

    private static Objective objective() {
        return new Objective(new Scoreboard(), OBJECTIVE, ObjectiveCriteria.DUMMY, Component.empty(), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    /** What has been sent to this player on this connection; a new connection starts from nothing. */
    private static Sent sent(ServerPlayer player) {
        Sent sent = SENT.computeIfAbsent(player.getUUID(), id -> new Sent());
        if (sent.connection != player.connection) {
            sent.connection = player.connection;
            sent.lines.clear();
            if (player.connection != null) player.connection.send(new ClientboundSetObjectivePacket(objective(), ClientboundSetObjectivePacket.METHOD_ADD));
        }
        return sent;
    }

    /** Sets a line - if it is not already what the player's game holds. */
    public static void set(ServerPlayer player, String line, int value, Component text) {
        Sent sent = sent(player);
        Component shown = text == null ? Component.empty() : text;
        Line now = new Line(value, shown.toString());
        if (now.equals(sent.lines.put(line, now)) || player.connection == null) return;
        player.connection.send(new ClientboundSetScorePacket(line, OBJECTIVE, value, Optional.of(shown), Optional.of(BlankFormat.INSTANCE)));
    }

    public static void clear(ServerPlayer player, String line) {
        Sent sent = SENT.get(player.getUUID());
        if (sent == null || sent.connection != player.connection || sent.lines.remove(line) == null || player.connection == null) return;
        player.connection.send(new ClientboundResetScorePacket(line, OBJECTIVE));
    }

    /** What the HUD needs to know of a player's settings. */
    public static void options(ServerPlayer player) {
        set(player, OPTIONS, (Options.on(player, Options.Option.CASTS) ? OPT_CASTS : 0) | (Options.on(player, Options.Option.COMBAT) ? OPT_COMBAT : 0)
                | (Options.on(player, Options.Option.ART) ? OPT_ART : 0), null);
        set(player, BUILD, 0, Component.literal(com.thefarawaydev.realmsofchaos.RealmsOfChaos.version()));
    }

    /** The number last sent on a line, or null: for tests, and for nothing else. */
    public static Integer value(ServerPlayer player, String line) {
        Sent sent = SENT.get(player.getUUID());
        Line held = sent == null ? null : sent.lines.get(line);
        return held == null ? null : held.value();
    }

    public static void forget(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }
}
