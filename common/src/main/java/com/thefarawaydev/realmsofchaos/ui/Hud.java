package com.thefarawaydev.realmsofchaos.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * On-screen feedback instead of chat lines: big titles for moments (entering a floor, a trial starting, a victory)
 * and the action bar above the hotbar for short notices.
 */
public final class Hud {
    private Hud() {}

    public static Component colored(Component text, int color) {
        return text.copy().withStyle(Style.EMPTY.withColor(color));
    }

    public static void title(ServerPlayer player, Component title, Component subtitle) {
        title(player, title, subtitle, 10, 60, 20);
    }

    /** The most letters a title and the line under it may have before they are written on a card instead. */
    public static final int TITLE_LETTERS = 16, SUBTITLE_LETTERS = 34;

    /**
     * A title is drawn four times the size of ordinary writing, and the line under it twice: on a small window a
     * long one runs off both sides of the screen. So only short ones are titles. Anything longer is a card.
     */
    public static boolean fitsTitle(Component title, Component subtitle) {
        return Words.of(title).length() <= TITLE_LETTERS && (subtitle == null || Words.of(subtitle).length() <= SUBTITLE_LETTERS);
    }

    public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        if (!fitsTitle(title, subtitle)) {
            Cards.notice(player, Words.of(title), Words.of(subtitle));
            return;
        }
        if (player.connection == null) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** The same words are not written above a player's hotbar again within this many ticks. */
    public static final int REPEAT_TICKS = 20;
    private static final java.util.Map<java.util.UUID, String> LAST_WORDS = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_AT = new java.util.HashMap<>();
    private static int said, hushed;

    /** How many lines have been written above hotbars, and how many were left out as repeats: for the self-test. */
    public static int[] counts() {
        return new int[]{said, hushed};
    }

    /**
     * Something a block has to say: shown in a panel floating over the block. A player in another dimension (or with no
     * level) gets it in the action bar instead.
     */
    public static void above(ServerPlayer player, net.minecraft.core.BlockPos pos, Component text, int colour) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) BlockLabel.show(level, pos, text, colour);
        else actionBar(player, colored(text, colour));
    }

    public static void actionBar(ServerPlayer player, Component text) {
        // (a room, a trap or a god that says the same thing every second is not saying anything)
        if (player.level().getServer() != null) {
            long now = player.level().getServer().getTickCount();
            String words = text.toString();
            Long at = LAST_AT.get(player.getUUID());
            if (at != null && now >= at && now - at < REPEAT_TICKS && words.equals(LAST_WORDS.get(player.getUUID()))) {
                hushed++;
                return;
            }
            LAST_AT.put(player.getUUID(), now);
            LAST_WORDS.put(player.getUUID(), words);
        }
        said++;
        if (player.connection == null) return;
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
