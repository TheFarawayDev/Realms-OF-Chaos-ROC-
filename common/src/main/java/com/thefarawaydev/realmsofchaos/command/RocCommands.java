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
                .then(Commands.literal("version").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> p.sendSystemMessage(com.thefarawaydev.realmsofchaos.world.Welcome.version()))))
                // for whoever runs the server: straight to a floor, and a look at each piece of the interface
                .then(Commands.literal("dev").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("tower")
                                .then(Commands.argument("line", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.god.Line line = com.thefarawaydev.realmsofchaos.god.Line.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line").toUpperCase(java.util.Locale.ROOT));
                                    com.thefarawaydev.realmsofchaos.world.Access.attune(p);
                                    Teleports.enterTower(p, line);
                                }))))
                        .then(Commands.literal("floor")
                                .then(Commands.argument("line", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("floor", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 15)).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                            // straight to a floor of a tower, built if need be: somewhere to go and look
                                            com.thefarawaydev.realmsofchaos.god.Line line = com.thefarawaydev.realmsofchaos.god.Line.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line").toUpperCase(java.util.Locale.ROOT));
                                            com.thefarawaydev.realmsofchaos.world.Access.attune(p);
                                            int floor = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "floor");
                                            net.minecraft.server.level.ServerLevel dest = p.level().getServer().getLevel(com.thefarawaydev.realmsofchaos.world.Realm.ofLine(line).key());
                                            if (dest == null) return;
                                            com.thefarawaydev.realmsofchaos.world.TowerBuilder.ensureFloor(dest, line, floor);
                                            net.minecraft.world.phys.Vec3 at = com.thefarawaydev.realmsofchaos.world.Tower.arrival(floor);
                                            p.teleportTo(dest, at.x, at.y, at.z, java.util.Set.of(), com.thefarawaydev.realmsofchaos.world.Tower.ARRIVAL_YAW, 0.0F, false);
                                        })))))
                        .then(Commands.literal("room")
                                .then(Commands.argument("line", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                            // into the first room of a kind in a tower's passages, built if need be: somewhere to go and look
                                            com.thefarawaydev.realmsofchaos.god.Line line = com.thefarawaydev.realmsofchaos.god.Line.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line").toUpperCase(java.util.Locale.ROOT));
                                            com.thefarawaydev.realmsofchaos.world.Passages.Kind kind = com.thefarawaydev.realmsofchaos.world.Passages.Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind").toUpperCase(java.util.Locale.ROOT));
                                            com.thefarawaydev.realmsofchaos.world.Access.attune(p);
                                            // (into this tower's own realm first: its passages are built in it, not in another's)
                                            if (com.thefarawaydev.realmsofchaos.world.Realm.of(p.level().dimension()) != com.thefarawaydev.realmsofchaos.world.Realm.ofLine(line)) Teleports.enterTower(p, line);
                                            if (!(p.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
                                            for (int index = 1; index <= com.thefarawaydev.realmsofchaos.world.Passages.COUNT; index++) {
                                                for (int stage = 0; stage < com.thefarawaydev.realmsofchaos.world.Passages.stages(level, line, index); stage++) {
                                                    com.thefarawaydev.realmsofchaos.world.Passages.Plan plan = com.thefarawaydev.realmsofchaos.world.Passages.plan(level, line, index, stage);
                                                    for (com.thefarawaydev.realmsofchaos.world.Passages.Cell c : plan.cells()) {
                                                        if (plan.kind(c) != kind) continue;
                                                        com.thefarawaydev.realmsofchaos.world.Passages.build(level, plan);
                                                        net.minecraft.core.BlockPos m = plan.centre(c);
                                                        p.teleportTo(level, m.getX() + 0.5, m.getY() + 1, m.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, false);
                                                        return;
                                                    }
                                                }
                                            }
                                        })))))
                        .then(Commands.literal("passage")
                                .then(Commands.argument("line", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 14)).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                            com.thefarawaydev.realmsofchaos.god.Line line = com.thefarawaydev.realmsofchaos.god.Line.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line").toUpperCase(java.util.Locale.ROOT));
                                            com.thefarawaydev.realmsofchaos.world.Access.attune(p);
                                            if (com.thefarawaydev.realmsofchaos.world.Realm.of(p.level().dimension()) == null) Teleports.enterTower(p, line);
                                            com.thefarawaydev.realmsofchaos.world.Passages.enter(p, line, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "index"));
                                        })))))
                        .then(Commands.literal("citadel")
                                .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    // to the nearest citadel (or gate) of a kind, raised if need be, outside its gate
                                    if (!(p.level() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
                                    com.thefarawaydev.realmsofchaos.world.Ruins.Kind kind = com.thefarawaydev.realmsofchaos.world.Ruins.Kind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind").toUpperCase(java.util.Locale.ROOT));
                                    com.thefarawaydev.realmsofchaos.world.Ruins.Site site = com.thefarawaydev.realmsofchaos.world.Ruins.nearest(level.getSeed(), kind, p.getX(), p.getZ());
                                    net.minecraft.core.BlockPos c = com.thefarawaydev.realmsofchaos.world.Ruins.ensure(level, site);
                                    p.teleportTo(level, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5 + kind.radius + 12, java.util.Set.of(), 180.0F, 10.0F, false);
                                }))))
                        .then(Commands.literal("runs").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            // where each kind of corridor of this level is: somewhere to go and look
                            com.thefarawaydev.realmsofchaos.world.Passages.Spot spot = com.thefarawaydev.realmsofchaos.world.Passages.locate(p);
                            if (spot == null || !(p.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
                            com.thefarawaydev.realmsofchaos.world.Passages.Plan plan = com.thefarawaydev.realmsofchaos.world.Passages.plan(level, spot);
                            java.util.Set<Integer> told = new java.util.HashSet<>();
                            for (com.thefarawaydev.realmsofchaos.world.Passages.Cell c : plan.cells()) {
                                for (int dir = 0; dir < 2; dir++) {
                                    int run = plan.run(c, dir);
                                    if (run == com.thefarawaydev.realmsofchaos.world.Passages.RUN_STRAIGHT || !told.add(run)) continue;
                                    net.minecraft.core.BlockPos mid = plan.corridorMiddle(c, dir);
                                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal("run " + run + ": /tp " + mid.getX() + " " + (mid.getY() + 1) + " " + mid.getZ()));
                                }
                            }
                        })))
                        .then(Commands.literal("card").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                            com.thefarawaydev.realmsofchaos.ui.Cards.clear(p);
                            com.thefarawaydev.realmsofchaos.ui.Cards.tip(p, "A tip is a small card at the top of the screen, with up to three lines on it.");
                            com.thefarawaydev.realmsofchaos.ui.Cards.notice(p, "A notice", "This one has a heading, and waits its turn behind the tip.");
                        })))
                        .then(Commands.literal("art").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), com.thefarawaydev.realmsofchaos.ui.HudArt::demo))))
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
                        .then(Commands.literal("commission")
                                .executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.commission(p))))
                                .then(Commands.literal("claim").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                                    com.thefarawaydev.realmsofchaos.world.Commissions.claim(p);
                                    Screens.show(p, Screens.commission(p));
                                })))
                                .then(Commands.literal("list").then(Commands.argument("shelf", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx ->
                                        ui(ctx.getSource().getPlayerOrException(), p -> Screens.show(p, Screens.commissionList(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "shelf")))))))
                                .then(Commands.literal("order").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx ->
                                        ui(ctx.getSource().getPlayerOrException(), p -> {
                                            com.thefarawaydev.realmsofchaos.world.Commissions.place(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                                            Screens.show(p, Screens.commission(p));
                                        })))))
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
                .then(Commands.literal("art").executes(ctx -> ui(ctx.getSource().getPlayerOrException(), p -> {
                    boolean on = com.thefarawaydev.realmsofchaos.ui.Options.toggle(p, com.thefarawaydev.realmsofchaos.ui.Options.Option.ART);
                    p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("menu." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".options.art")
                            .append(": ").append(net.minecraft.network.chat.Component.translatable("menu." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + (on ? ".options.on" : ".options.off"))));
                })))
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
