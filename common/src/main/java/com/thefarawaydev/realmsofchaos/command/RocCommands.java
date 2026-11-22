package com.thefarawaydev.realmsofchaos.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thefarawaydev.realmsofchaos.ui.CodexMenu;
import com.thefarawaydev.realmsofchaos.ui.Screens;
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
                    ServerPlayer home = ctx.getSource().getPlayerOrException();
                    Screens.show(home, Screens.home(home));
                    return 1;
                })
                .then(Commands.literal("menu").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Screens.show(player, Screens.home(player));
                    return 1;
                }))
                .then(Commands.literal("towers").executes(ctx -> {
                    CodexMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                // what the buttons of the custom screens run
                .then(Commands.literal("ui")
                        .then(Commands.literal("home").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.home(p)))))
                        .then(Commands.literal("towers").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), CodexMenu::open)))
                        .then(Commands.literal("handbook").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.handbook(p)))))
                        .then(Commands.literal("chronicle").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.chronicle(p)))))
                        .then(Commands.literal("map").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            com.thefarawaydev.realmsofchaos.world.Passages.Spot spot = com.thefarawaydev.realmsofchaos.world.Passages.locate(p);
                            if (spot != null) com.thefarawaydev.realmsofchaos.ui.PassageMenu.open(p, spot);
                            else Screens.show(p, Screens.home(p));
                        })))
                        .then(Commands.literal("topic").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            var topic = Screens.topicById(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                            Screens.show(p, topic == null ? Screens.handbook(p) : Screens.topic(p, topic));
                        }))))
                        .then(Commands.literal("chapter").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            var chapter = chapter(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                            Screens.show(p, chapter != null && chapter.unlocked(p) ? Screens.chapter(p, chapter) : Screens.chronicle(p));
                        }))))
                        .then(Commands.literal("take").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            var chapter = chapter(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                            if (chapter != null && chapter.unlocked(p)) com.thefarawaydev.realmsofchaos.story.Story.give(p, chapter);
                        }))))
                        .then(Commands.literal("altar")
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                        .then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p ->
                                                                altar(p, new net.minecraft.core.BlockPos(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")),
                                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what")))))))))
                        .then(Commands.literal("sidebar").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            com.thefarawaydev.realmsofchaos.ui.Sidebar.toggle(p);
                            Screens.show(p, Screens.home(p));
                        })))
                        .then(Commands.literal("skills")
                                .executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.skills(p))))
                                .then(Commands.literal("reset").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.world.Skills.reset(p);
                                    Screens.show(p, Screens.skills(p));
                                })))
                                .then(Commands.literal("raise").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx ->
                                        ui(ctx.getSource().getPlayerOrException(), p -> {
                                            var skill = com.thefarawaydev.realmsofchaos.world.Skills.Skill.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                                            if (skill != null) com.thefarawaydev.realmsofchaos.world.Skills.raise(p, skill);
                                            Screens.show(p, Screens.skills(p));
                                        })))))
                        .then(Commands.literal("hubcore")
                                .executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.hubCommand(p))))
                                .then(Commands.literal("deposit").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), RocCommands::deposit))))
                        .then(Commands.literal("bounty")
                                .executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.bounties(p))))
                                .then(Commands.literal("claim").then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 9)).executes(ctx ->
                                        ui(ctx.getSource().getPlayerOrException(), p -> {
                                            com.thefarawaydev.realmsofchaos.world.Bounties.claim(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "index"));
                                            Screens.show(p, Screens.bounties(p));
                                        })))))
                        .then(Commands.literal("waygate")
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                        .then(Commands.argument("to", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx ->
                                                                ui(ctx.getSource().getPlayerOrException(), p -> com.thefarawaydev.realmsofchaos.world.Waygates.begin(p,
                                                                        new net.minecraft.core.BlockPos(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")),
                                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "to")))))))))
                        .then(Commands.literal("party")
                                .executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.party(p))))
                                .then(Commands.literal("invite").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    com.thefarawaydev.realmsofchaos.world.Parties.invite(p, net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"));
                                    Screens.show(p, Screens.party(p));
                                    return 1;
                                })))
                                .then(Commands.literal("accept").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.world.Parties.accept(p);
                                    Screens.show(p, Screens.party(p));
                                })))
                                .then(Commands.literal("decline").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.world.Parties.decline(p);
                                    Screens.show(p, Screens.party(p));
                                })))
                                .then(Commands.literal("leave").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.world.Parties.leave(p);
                                    Screens.show(p, Screens.party(p));
                                })))))
                .then(Commands.literal("hub").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Teleports.recall(player);
                    return 1;
                }))
                .then(Commands.literal("skills").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.skills(p)))))
                .then(Commands.literal("bestiary").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), com.thefarawaydev.realmsofchaos.ui.BestiaryMenu::open)))
                .then(Commands.literal("settings").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), com.thefarawaydev.realmsofchaos.ui.OptionsMenu::open)))
                .then(Commands.literal("insight").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), com.thefarawaydev.realmsofchaos.ui.InsightMenu::open)))
                .then(Commands.literal("casts").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                    boolean on = com.thefarawaydev.realmsofchaos.ui.Options.toggle(p, com.thefarawaydev.realmsofchaos.ui.Options.Option.CASTS);
                    com.thefarawaydev.realmsofchaos.ui.Hud.actionBar(p, net.minecraft.network.chat.Component.translatable("menu." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".options.casts")
                            .append(": ").append(net.minecraft.network.chat.Component.translatable("menu." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + (on ? ".options.on" : ".options.off"))));
                })))
                .then(Commands.literal("chart").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), com.thefarawaydev.realmsofchaos.world.Charts::give)))
                .then(Commands.literal("sidebar").executes(ctx -> {
                    com.thefarawaydev.realmsofchaos.ui.Sidebar.toggle(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("scan").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    com.thefarawaydev.realmsofchaos.item.ChaosCompassItem.scan(player);
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
                .then(Commands.literal("party")
                        .executes(ctx -> {
                            com.thefarawaydev.realmsofchaos.ui.PartyMenu.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.literal("invite").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
                            com.thefarawaydev.realmsofchaos.world.Parties.invite(ctx.getSource().getPlayerOrException(),
                                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })))
                        .then(Commands.literal("accept").executes(ctx -> {
                            com.thefarawaydev.realmsofchaos.world.Parties.accept(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(ctx -> {
                            com.thefarawaydev.realmsofchaos.world.Parties.leave(ctx.getSource().getPlayerOrException());
                            return 1;
                        })))
                .then(Commands.literal("progress").executes(ctx -> {
                    CodexMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    /**
     * The button on the Heart of the Hub's screen. The heart only stands in the hub, so that is where things are
     * handed over.
     */
    private static void deposit(ServerPlayer player) {
        if (com.thefarawaydev.realmsofchaos.world.Realm.of(player.level().dimension()) != com.thefarawaydev.realmsofchaos.world.Realm.HUB) return;
        com.thefarawaydev.realmsofchaos.world.HubTiers.deposit(player);
        Screens.show(player, Screens.hubCommand(player));
    }

    private static int ui(ServerPlayer player, java.util.function.Consumer<ServerPlayer> action) {
        action.accept(player);
        return 1;
    }

    private static com.thefarawaydev.realmsofchaos.story.Story.Chapter chapter(String id) {
        for (com.thefarawaydev.realmsofchaos.story.Story.Chapter c : com.thefarawaydev.realmsofchaos.story.Story.Chapter.values()) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    /**
     * A button on the Trial Altar screen. The altar must really be there and the player really beside it: the
     * screen only names a place, it is checked again here.
     */
    private static void altar(ServerPlayer player, net.minecraft.core.BlockPos pos, String what) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (!(level.getBlockState(pos).getBlock() instanceof com.thefarawaydev.realmsofchaos.block.AltarBlock altar)) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12.0 * 12.0) return;
        switch (what) {
            case "begin" -> com.thefarawaydev.realmsofchaos.block.AltarBlock.begin(level, player, pos, altar.line());
            case "rematch" -> com.thefarawaydev.realmsofchaos.block.AltarBlock.rematch(level, player, pos, altar.line());
            case "ascend" -> {
                if (com.thefarawaydev.realmsofchaos.block.AltarBlock.state(level, player, pos, altar.line()) == com.thefarawaydev.realmsofchaos.block.AltarBlock.State.DEFEATED) Teleports.ascend(player);
            }
            default -> Screens.show(player, Screens.altar(player, level, altar.line(), pos));
        }
    }
}
