package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
        Hud.title(player,
                Component.translatable(ns + "title").withStyle(Style.EMPTY.withColor(0xA55AD6).withBold(true)),
                Component.translatable(ns + "subtitle").withStyle(Style.EMPTY.withColor(0xF5B82E)), 20, 140, 30);
        Hud.actionBar(player, Hud.colored(Component.translatable(ns + "hint"), 0xFFFFFF));

        give(player, new ItemStack(ModContent.CHAOS_CODEX.get()));
        give(player, new ItemStack(ModContent.CHAOS_COMPASS.get()));
        give(player, com.thefarawaydev.realmsofchaos.story.Story.Chapter.PROLOGUE.book());
        com.thefarawaydev.realmsofchaos.ui.Tutorial.welcome(player);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
