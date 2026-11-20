package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Called by each loader when a player joins a world. Says thank you the first time, once per player per world. */
public final class Welcome {
    private Welcome() {}

    public static void onJoin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ProgressData data = ProgressData.get(server);
        boolean first = data.markWelcomed(player.getUUID());
        RocAdvancements.sync(player, data);
        if (!first) return;

        String ns = "welcome." + RealmsOfChaos.MOD_ID + ".";
        if (player.connection != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 140, 30));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable(ns + "subtitle").withStyle(Style.EMPTY.withColor(0xF5B82E))));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable(ns + "title").withStyle(Style.EMPTY.withColor(0xA55AD6).withBold(true))));
        }
        player.sendSystemMessage(Component.translatable(ns + "chat").withStyle(Style.EMPTY.withColor(0xF5B82E)));
        player.sendSystemMessage(Component.translatable(ns + "hint").withStyle(Style.EMPTY.withColor(0xAAAAAA)));

        give(player, new ItemStack(ModContent.CHAOS_CODEX.get()));
        give(player, new ItemStack(ModContent.CHAOS_COMPASS.get()));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
