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

    public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        if (player.connection == null) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void actionBar(ServerPlayer player, Component text) {
        if (player.connection == null) return;
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
}
