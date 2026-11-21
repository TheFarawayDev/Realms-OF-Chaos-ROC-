package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.AltarBlock;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.world.Parties;
import com.thefarawaydev.realmsofchaos.world.Passages;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * The mod's custom screens. These are not chest menus: they are real screens with a title, text, pictures of items
 * and buttons, built on the game's own dialog system - so they need nothing installed on the client beyond the mod
 * and behave the same on Fabric and Forge. The server decides what a screen says and sends it; every button runs a
 * {@code /roc ui ...} command, which does the thing and usually sends the next screen.
 *
 * Screens with moving parts - the forge's strike bar, the passage map, a tower's floor plan - stay chest menus,
 * because a dialog cannot change while it is open.
 */
public final class Screens {
    private static final String NS = "screen." + RealmsOfChaos.MOD_ID + ".";
    private static final int TEXT_WIDTH = 300, BUTTON_WIDTH = 150;
    /** The screen each player was last shown. Lets the self-test look at what was sent. */
    private static final Map<UUID, Dialog> LAST = new HashMap<>();

    private Screens() {}

    // ---- building blocks -------------------------------------------------------------------------------------

    private static MutableComponent tr(String key, int color, Object... args) {
        return Component.translatable(NS + key, args).withStyle(Style.EMPTY.withColor(color));
    }

    private static DialogBody text(Component component) {
        return new PlainMessage(component, TEXT_WIDTH);
    }

    private static DialogBody item(ItemStack stack, Component description) {
        return new ItemBody(ItemStackTemplate.fromNonEmptyStack(stack), Optional.of(new PlainMessage(description, TEXT_WIDTH - 30)), false, true, 16, 16);
    }

    /** A button that runs a command. */
    private static ActionButton button(Component label, Component tooltip, String command) {
        return new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), BUTTON_WIDTH), Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }

    private static ActionButton close() {
        return new ActionButton(new CommonButtonData(tr("close", 0xFFFFFF), BUTTON_WIDTH), Optional.empty());
    }

    private static ActionButton back(String command) {
        return button(tr("back", 0xFFFFFF), null, command);
    }

    private static CommonDialogData common(Component title, List<DialogBody> body) {
        return new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
    }

    private static Dialog menu(Component title, List<DialogBody> body, List<ActionButton> actions, ActionButton exit, int columns) {
        return new MultiActionDialog(common(title, body), actions, Optional.of(exit), columns);
    }

    private static Dialog notice(Component title, List<DialogBody> body, ActionButton action) {
        return new NoticeDialog(common(title, body), action);
    }

    /** Sends a screen to a player. */
    public static void show(ServerPlayer player, Dialog dialog) {
        LAST.put(player.getUUID(), dialog);
        player.openDialog(Holder.direct(dialog));
    }

    public static Dialog last(ServerPlayer player) {
        return LAST.get(player.getUUID());
    }

    /** Every command a screen's buttons can run, in order. */
    public static List<String> commands(Dialog dialog) {
        List<ActionButton> buttons = new ArrayList<>();
        if (dialog instanceof MultiActionDialog multi) {
            buttons.addAll(multi.actions());
            multi.exitAction().ifPresent(buttons::add);
        } else if (dialog instanceof NoticeDialog notice) {
            buttons.add(notice.action());
        }
        List<String> out = new ArrayList<>();
        for (ActionButton b : buttons) {
            if (b.action().isPresent() && b.action().get() instanceof StaticAction action && action.value() instanceof ClickEvent.RunCommand run) out.add(run.command());
        }
        return out;
    }

    public static int bodyLines(Dialog dialog) {
        return dialog.common().body().size();
    }

    // ---- the Codex -------------------------------------------------------------------------------------------

    /** The Codex of Chaos: where you stand, what to do next, and the way to everything else. */
    public static Dialog home(ServerPlayer player) {
        ProgressData data = ProgressData.get(player.level().getServer());
        List<DialogBody> body = new ArrayList<>();
        body.add(text(tr("home.thanks", 0xA55AD6)));
        body.add(text(tr("home.awake", 0xF5B82E, data.total(player.getUUID()))));
        for (Line line : Line.values()) {
            body.add(text(tr("home.tower", line.color(), Tower.towerName(line), Tower.currentFloor(data, player.getUUID(), line))));
        }
        body.add(text(tr("home.next", 0x55FFFF)));
        for (Component line : StoryMenu.guidance(player)) body.add(text(line));
        List<ActionButton> actions = new ArrayList<>();
        if (Passages.locate(player) != null) actions.add(button(tr("home.map", 0xFFFF55), null, "/roc ui map"));
        actions.add(button(tr("home.towers", 0xFFFFFF), tr("home.towers.tip", 0xAAAAAA), "/roc ui towers"));
        actions.add(button(tr("home.chronicle", 0xA55AD6), tr("home.chronicle.tip", 0xAAAAAA), "/roc ui chronicle"));
        actions.add(button(tr("home.handbook", 0x55FFFF), tr("home.handbook.tip", 0xAAAAAA), "/roc ui handbook"));
        actions.add(button(tr("home.party", 0x55FF55), tr("home.party.tip", 0xAAAAAA), "/roc ui party"));
        actions.add(button(tr("home.forge", 0xFFAA00), tr("home.forge.tip", 0xAAAAAA), "/roc forge"));
        actions.add(button(tr("home.skills", 0xF5B82E, com.thefarawaydev.realmsofchaos.world.Skills.available(player)), tr("home.skills.tip", 0xAAAAAA), "/roc ui skills"));
        actions.add(button(tr(Sidebar.enabled(player) ? "home.sidebar.on" : "home.sidebar.off", 0x55FFFF), tr("home.sidebar.tip", 0xAAAAAA), "/roc ui sidebar"));
        actions.add(button(tr("home.hub", 0xFFFFFF), null, "/roc hub"));
        actions.add(button(tr("home.leave", 0xFFFFFF), null, "/roc leave"));
        return menu(tr("home", 0xA55AD6), body, actions, close(), 2);
    }

    // ---- the handbook ----------------------------------------------------------------------------------------

    private static Component topicTitle(HandbookData.Topic topic) {
        return Component.translatable("handbook." + RealmsOfChaos.MOD_ID + "." + topic.id() + ".title");
    }

    /** The Climber's Handbook: its list of topics. */
    public static Dialog handbook(ServerPlayer player) {
        List<ActionButton> actions = new ArrayList<>();
        for (HandbookData.Topic topic : HandbookData.ALL) actions.add(button(topicTitle(topic), null, "/roc ui topic " + topic.id()));
        return menu(tr("handbook", 0x55FFFF), List.of(text(tr("handbook.intro", 0xAAAAAA))), actions, back("/roc ui home"), 2);
    }

    public static HandbookData.Topic topicById(String id) {
        for (HandbookData.Topic topic : HandbookData.ALL) if (topic.id().equals(id)) return topic;
        return null;
    }

    /** One topic of the handbook, with buttons on to the next topic and back to the list. */
    public static Dialog topic(ServerPlayer player, HandbookData.Topic topic) {
        List<DialogBody> body = new ArrayList<>();
        for (int n = 1; n <= topic.lines(); n++) body.add(text(Component.translatable("handbook." + RealmsOfChaos.MOD_ID + "." + topic.id() + ".line" + n)));
        int at = HandbookData.ALL.indexOf(topic);
        List<ActionButton> actions = new ArrayList<>();
        if (at + 1 < HandbookData.ALL.size()) {
            HandbookData.Topic next = HandbookData.ALL.get(at + 1);
            actions.add(button(Component.empty().append(topicTitle(next)).append(Component.literal("  >")), null, "/roc ui topic " + next.id()));
        }
        actions.add(button(tr("handbook", 0x55FFFF), null, "/roc ui handbook"));
        return menu(topicTitle(topic).copy().withStyle(Style.EMPTY.withColor(0x55FFFF)), body, actions, close(), 1);
    }

    // ---- the chronicle ---------------------------------------------------------------------------------------

    /** The Oracle's Chronicle: the chapters written so far to read, the rest named by what will write them. */
    public static Dialog chronicle(ServerPlayer player) {
        List<DialogBody> body = new ArrayList<>();
        body.add(text(tr("chronicle.count", 0xF5B82E, Story.unlockedCount(player), Story.Chapter.values().length)));
        List<ActionButton> actions = new ArrayList<>();
        for (Story.Chapter chapter : Story.Chapter.values()) {
            if (chapter.unlocked(player)) {
                int color = chapter.line == null ? 0xF5B82E : chapter.line.color();
                actions.add(button(chapter.title().copy().withStyle(Style.EMPTY.withColor(color)), chapter.blurb(), "/roc ui chapter " + chapter.id()));
            }
        }
        // only the next thing to earn in each part is named, so the list is a path and not a wall of blanks
        Story.Chapter upcoming = null;
        for (Story.Chapter chapter : Story.Chapter.values()) {
            if (!chapter.unlocked(player) && (upcoming == null || chapter.line != upcoming.line)) {
                upcoming = chapter;
                Component how = chapter == Story.Chapter.EPILOGUE ? Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".story.unlock.all")
                        : chapter.needed == 0 ? Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".story.unlock.attuned")
                        : Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".story.unlock.count", chapter.needed, Tower.towerName(chapter.line));
                body.add(text(tr("chronicle.locked", 0x888888, how)));
            }
        }
        return menu(tr("chronicle", 0xA55AD6), body, actions, back("/roc ui home"), 2);
    }

    /** One chapter, page after page, with a button to take it away as a book. */
    public static Dialog chapter(ServerPlayer player, Story.Chapter chapter) {
        List<DialogBody> body = new ArrayList<>();
        for (int n = 1; n <= chapter.pages(); n++) {
            body.add(text(Component.translatable("story." + RealmsOfChaos.MOD_ID + "." + chapter.id() + ".page" + n)));
        }
        List<ActionButton> actions = new ArrayList<>();
        actions.add(button(tr("chapter.take", 0x55FF55), null, "/roc ui take " + chapter.id()));
        actions.add(button(tr("chronicle", 0xA55AD6), null, "/roc ui chronicle"));
        int color = chapter.line == null ? 0xF5B82E : chapter.line.color();
        return menu(chapter.title().copy().withStyle(Style.EMPTY.withColor(color)), body, actions, close(), 2);
    }

    // ---- the trial altar -------------------------------------------------------------------------------------

    /** The Trial Altar: who waits on this floor, their move, what they give up, and the button that begins. */
    public static Dialog altar(ServerPlayer player, ServerLevel level, Line line, BlockPos altar) {
        AltarBlock.State state = AltarBlock.state(level, player, altar, line);
        God god = AltarBlock.godFor(level, player, altar, line);
        List<DialogBody> body = new ArrayList<>();
        String at = altar.getX() + " " + altar.getY() + " " + altar.getZ();
        if (god != null) {
            int floor = Tower.floorOf(god);
            ItemStack relic = new ItemStack(ModContent.weaponItem(god));
            body.add(item(relic, tr("altar.rank", line.color(), god.displayTitle(), god.rank())));
            body.add(text(tr("altar.floor", 0xAAAAAA, Tower.towerName(line), floor, Tower.tierName(line, floor))));
            body.add(text(tr("altar.move", 0xF5B82E, god.moveName(), god.signature().description())));
            ItemStack material = new ItemStack(ModContent.materialItem(line, Tower.tier(floor)));
            body.add(text(tr("altar.drops", 0x55FFFF, GodBoss.materialDrop(god), material.getHoverName(), relic.getHoverName())));
            body.add(text(tr("altar.scaling", 0x888888)));
            // what the god would make of this player's arms, were the trial begun now
            com.thefarawaydev.realmsofchaos.world.Adapt.Measure measure = AltarBlock.measure(level, player, altar, line, god);
            double scale = com.thefarawaydev.realmsofchaos.world.Adapt.gearHealth(measure.might())
                    * com.thefarawaydev.realmsofchaos.world.Adapt.difficultyHealth(level.getDifficulty());
            body.add(text(tr("altar.strength", scale > 1.05 ? 0xFFAA00 : 0xAAAAAA,
                    Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".might." + com.thefarawaydev.realmsofchaos.world.Adapt.mightKey(measure.might())),
                    String.format(java.util.Locale.ROOT, "%.2f", scale))));
            body.add(text(tr("altar.phases", 0x888888)));
        }
        body.add(text(tr("altar.state." + state.name().toLowerCase(), state == AltarBlock.State.READY ? 0x55FF55 : state == AltarBlock.State.DEFEATED ? 0x55FFFF : 0xFF5555)));
        Component title = god == null ? tr("altar", line.color()) : god.displayName().copy().withStyle(Style.EMPTY.withColor(line.color()));
        List<ActionButton> actions = new ArrayList<>();
        if (state == AltarBlock.State.READY) actions.add(button(tr("altar.begin", 0x55FF55), null, "/roc ui altar " + at + " begin"));
        if (state == AltarBlock.State.DEFEATED) {
            if (god != null && Tower.floorOf(god) < Tower.FLOORS) actions.add(button(tr("altar.ascend", 0x55FFFF), null, "/roc ui altar " + at + " ascend"));
            if (AltarBlock.canRematch(level, player, altar, line)) actions.add(button(tr("altar.rematch", 0xFFAA00), null, "/roc ui altar " + at + " rematch"));
        }
        return actions.isEmpty() ? notice(title, body, close()) : menu(title, body, actions, close(), 2);
    }

    // ---- the party -------------------------------------------------------------------------------------------

    /** The party: who is in it and how far each has climbed, everyone else to invite, and any invitation waiting. */
    public static Dialog party(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ProgressData data = ProgressData.get(server);
        String menuNs = "menu." + RealmsOfChaos.MOD_ID + ".party.";
        List<DialogBody> body = new ArrayList<>();
        boolean inParty = Parties.inParty(player.getUUID());
        body.add(text(inParty ? Component.translatable(menuNs + "size", Parties.members(player.getUUID()).size()).withStyle(Style.EMPTY.withColor(0x55FF55))
                : Component.translatable(menuNs + "solo").withStyle(Style.EMPTY.withColor(0xAAAAAA))));
        for (ServerPlayer member : Parties.online(player)) {
            Component floors = tr("party.floors", 0xAAAAAA, Tower.currentFloor(data, member.getUUID(), Line.ZEUS),
                    Tower.currentFloor(data, member.getUUID(), Line.POSEIDON), Tower.currentFloor(data, member.getUUID(), Line.HADES));
            body.add(text(tr("party.member", 0x55FFFF, member.getDisplayName(), floors)));
        }
        for (int n = 1; n <= 4; n++) body.add(text(Component.translatable(menuNs + "rule" + n).withStyle(Style.EMPTY.withColor(0x888888))));
        List<ActionButton> actions = new ArrayList<>();
        UUID inviter = Parties.invitedBy(player.getUUID());
        if (inviter != null) {
            actions.add(button(Component.translatable(menuNs + "accept").withStyle(Style.EMPTY.withColor(0x55FF55)), null, "/roc ui party accept"));
            actions.add(button(Component.translatable(menuNs + "decline").withStyle(Style.EMPTY.withColor(0xFF5555)), null, "/roc ui party decline"));
        }
        if (server != null) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (Parties.members(player.getUUID()).contains(other.getUUID()) || actions.size() >= 12) continue;
                actions.add(button(tr("party.invite", 0xFFFFFF, other.getDisplayName()), null, "/roc ui party invite " + other.getGameProfile().name()));
            }
        }
        if (inParty) actions.add(button(Component.translatable(menuNs + "leave").withStyle(Style.EMPTY.withColor(0xFF5555)), null, "/roc ui party leave"));
        actions.add(button(tr("party.refresh", 0xFFFFFF), null, "/roc ui party"));
        return menu(tr("party", 0x55FFFF), body, actions, back("/roc ui home"), 2);
    }

    // ---- the field network -----------------------------------------------------------------------------------

    /** A Waygate Obelisk: where it can send you, what it costs, and how the channel works. */
    public static Dialog waygate(ServerPlayer player, ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        List<DialogBody> body = new ArrayList<>();
        int price = com.thefarawaydev.realmsofchaos.world.Waygates.price(server);
        ItemStack cost = new ItemStack(com.thefarawaydev.realmsofchaos.world.Waygates.cost());
        body.add(text(tr("waygate.how", 0xAAAAAA, com.thefarawaydev.realmsofchaos.world.HubTiers.channelTicks(server) / 20)));
        body.add(price > 0 ? item(cost, tr("waygate.cost", 0xF5B82E, price, cost.getHoverName())) : text(tr("waygate.free", 0x55FF55)));
        List<ActionButton> actions = new ArrayList<>();
        if (com.thefarawaydev.realmsofchaos.world.Access.attuned(player)) {
            actions.add(button(tr("waygate.hub", 0xA55AD6), null, "/roc ui waygate " + at + " " + com.thefarawaydev.realmsofchaos.world.Waygates.HUB));
        } else {
            body.add(text(tr("waygate.unattuned", 0xFF5555)));
        }
        List<com.thefarawaydev.realmsofchaos.world.Network.Node> known = com.thefarawaydev.realmsofchaos.world.Waygates.destinations(player, pos);
        for (com.thefarawaydev.realmsofchaos.world.Network.Node node : known) {
            if (actions.size() >= 12) break;
            actions.add(button(tr("waygate.to", 0x55FFFF, node.pos().getX(), node.pos().getY(), node.pos().getZ()),
                    Component.literal(node.dimension()), "/roc ui waygate " + at + " " + node.code()));
        }
        if (known.isEmpty()) body.add(text(tr("waygate.alone", 0x888888)));
        return actions.isEmpty() ? notice(tr("waygate", 0xA55AD6), body, close()) : menu(tr("waygate", 0xA55AD6), body, actions, close(), 2);
    }

    /** Hub Command, at the Heart of the Hub: the hub's tier, what each tier does, and what the next one asks for. */
    public static Dialog hubCommand(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int tier = com.thefarawaydev.realmsofchaos.world.HubTiers.tier(server);
        List<DialogBody> body = new ArrayList<>();
        body.add(text(tr("hub.tier", 0xA55AD6, tier, com.thefarawaydev.realmsofchaos.world.HubTiers.MAX, com.thefarawaydev.realmsofchaos.world.HubTiers.tierName(tier))));
        for (int t = 1; t <= com.thefarawaydev.realmsofchaos.world.HubTiers.MAX; t++) {
            body.add(text(tr(t <= tier ? "hub.perk.have" : "hub.perk.next", t <= tier ? 0x55FF55 : 0x888888,
                    com.thefarawaydev.realmsofchaos.world.HubTiers.tierName(t), com.thefarawaydev.realmsofchaos.world.HubTiers.perk(t))));
        }
        List<ActionButton> actions = new ArrayList<>();
        if (tier < com.thefarawaydev.realmsofchaos.world.HubTiers.MAX) {
            body.add(text(tr("hub.asks", 0xF5B82E, com.thefarawaydev.realmsofchaos.world.HubTiers.tierName(tier + 1))));
            for (com.thefarawaydev.realmsofchaos.world.HubTiers.Cost cost : com.thefarawaydev.realmsofchaos.world.HubTiers.costs(tier)) {
                int have = com.thefarawaydev.realmsofchaos.world.HubTiers.deposited(server, cost);
                body.add(item(cost.icon(), tr("hub.cost", have >= cost.count() ? 0x55FF55 : 0xFFFFFF, cost.name(), have, cost.count())));
            }
            actions.add(button(tr("hub.deposit", 0x55FF55), tr("hub.deposit.tip", 0xAAAAAA), "/roc ui hubcore deposit"));
        } else {
            body.add(text(tr("hub.complete", 0xF5B82E)));
        }
        actions.add(button(tr("hub.bounties", 0xFFAA00), null, "/roc ui bounty"));
        return menu(tr("hub", 0xA55AD6), body, actions, close(), 2);
    }

    /** The Bounty Board: today's three bounties, how far along this player is, and the buttons that pay out. */
    public static Dialog bounties(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> actions = new ArrayList<>();
        if (!com.thefarawaydev.realmsofchaos.world.Bounties.open(server)) {
            body.add(text(tr("bounty.closed", 0xFF5555, com.thefarawaydev.realmsofchaos.world.HubTiers.tierName(com.thefarawaydev.realmsofchaos.world.Bounties.NEEDS_TIER))));
            return notice(tr("bounty", 0xFFAA00), body, close());
        }
        body.add(text(tr("bounty.intro", 0xAAAAAA)));
        for (com.thefarawaydev.realmsofchaos.world.Bounties.Bounty bounty : com.thefarawaydev.realmsofchaos.world.Bounties.today(server)) {
            int done = com.thefarawaydev.realmsofchaos.world.Bounties.progress(player, bounty);
            boolean claimed = com.thefarawaydev.realmsofchaos.world.Bounties.claimed(player, bounty);
            ItemStack pay = bounty.payment();
            body.add(item(pay, tr(claimed ? "bounty.line.claimed" : done >= bounty.goal() ? "bounty.line.done" : "bounty.line",
                    claimed ? 0x888888 : done >= bounty.goal() ? 0x55FF55 : 0xFFFFFF, bounty.text(), done, bounty.goal(), pay.getCount(), pay.getHoverName())));
            if (!claimed && done >= bounty.goal()) {
                actions.add(button(tr("bounty.claim", 0x55FF55, bounty.text()), null, "/roc ui bounty claim " + bounty.index()));
            }
        }
        return actions.isEmpty() ? notice(tr("bounty", 0xFFAA00), body, close()) : menu(tr("bounty", 0xFFAA00), body, actions, close(), 1);
    }

    // ---- mastery ---------------------------------------------------------------------------------------------

    /** The mastery tree: points to spend, the twelve skills in their three branches, and a button to raise each. */
    public static Dialog skills(ServerPlayer player) {
        int free = com.thefarawaydev.realmsofchaos.world.Skills.available(player);
        List<DialogBody> body = new ArrayList<>();
        body.add(text(tr("skills.points", 0xF5B82E, free, com.thefarawaydev.realmsofchaos.world.Skills.earned(player))));
        body.add(text(tr("skills.how", 0x888888)));
        List<ActionButton> actions = new ArrayList<>();
        com.thefarawaydev.realmsofchaos.world.Skills.Branch branch = null;
        for (com.thefarawaydev.realmsofchaos.world.Skills.Skill skill : com.thefarawaydev.realmsofchaos.world.Skills.Skill.values()) {
            if (skill.branch != branch) {
                branch = skill.branch;
                body.add(text(tr("skills.branch." + branch.name().toLowerCase(), 0x55FFFF)));
            }
            int rank = com.thefarawaydev.realmsofchaos.world.Skills.rank(player, skill);
            body.add(text(tr("skills.line", rank > 0 ? 0x55FF55 : 0xFFFFFF, skill.displayName(), rank, com.thefarawaydev.realmsofchaos.world.Skills.MAX_RANK, skill.description())));
            if (free > 0 && rank < com.thefarawaydev.realmsofchaos.world.Skills.MAX_RANK) {
                actions.add(button(tr("skills.raise", 0x55FF55, skill.displayName(), rank + 1), skill.description(), "/roc ui skills raise " + skill.id()));
            }
        }
        if (com.thefarawaydev.realmsofchaos.world.Skills.spent(player) > 0) actions.add(button(tr("skills.reset", 0xFFAA00), tr("skills.reset.tip", 0xAAAAAA), "/roc ui skills reset"));
        return actions.isEmpty() ? notice(tr("skills", 0xF5B82E), body, back("/roc ui home")) : menu(tr("skills", 0xF5B82E), body, actions, back("/roc ui home"), 2);
    }
}
