package com.thefarawaydev.realmsofchaos.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thefarawaydev.realmsofchaos.ui.CodexMenu;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /roc (or /roc menu, /roc progress) - Codex;  /roc hub - the hub;  /roc forge - Forge of Chaos;  /roc leave - leave the realms. */
public final class RocCommands {
    private RocCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("roc")
                .executes(ctx -> {
                    CodexMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("menu").executes(ctx -> {
                    CodexMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("hub").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Teleports.toHub(player);
                    return 1;
                }))
                .then(Commands.literal("forge").executes(ctx -> {
                    com.thefarawaydev.realmsofchaos.ui.ForgeMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    Teleports.leave(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("progress").executes(ctx -> {
                    CodexMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
