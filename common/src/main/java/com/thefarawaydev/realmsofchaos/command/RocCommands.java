package com.thefarawaydev.realmsofchaos.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /roc hub - travel to the hub;  /roc progress - show how far up each god line you have climbed. */
public final class RocCommands {
    private RocCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("roc")
                .then(Commands.literal("hub").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Teleports.travel(player, Realm.HUB);
                    return 1;
                }))
                .then(Commands.literal("progress").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ProgressData data = ProgressData.get(ctx.getSource().getServer());
                    player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".progress.header"));
                    for (Line line : Line.values()) {
                        int count = data.count(player.getUUID(), line);
                        int next = data.nextRank(player.getUUID(), line);
                        Component nextName = next == 0
                                ? Component.translatable("message." + RealmsOfChaos.MOD_ID + ".progress.complete")
                                : Gods.get(line, next).displayName();
                        player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".progress.line",
                                line.displayName(), count, nextName));
                    }
                    return 1;
                })));
    }
}
