package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A page: a heading, paragraphs of text (some with a picture of an item beside them) and buttons. It is what the
 * Codex, the handbook, the chronicle, the altar and the rest of the mod's written screens are shown on - a screen of
 * the mod's own, drawn by the player's game (see the client's PageScreen), not the game's stock dialog.
 *
 * <p>A page is a menu with no slots to click. What it says travels as its title: a component whose first part is the
 * heading, second the paragraphs, third the buttons. The few items it pictures ride in hidden slots. A button
 * pressed comes back as the menu's button number, the way an enchanting table's choices do, so nothing but the
 * game's own packets is used and it is the same on Fabric and Forge.
 */
public class PageMenu extends AbstractContainerMenu {
    /** How many items a page can picture. */
    public static final int ICONS = 6;
    /** Marks on the parts of a page's title: a paragraph's icon slot, how many columns the buttons stand in, the way-out button. */
    public static final String ICON = "icon", EXIT = "exit";

    private final Container icons;
    /** What each button does when pressed, in order: a command to run as the player, or null to close the page. Server only. */
    private final List<String> commands;

    /** The player's side: an empty page for the server to fill. */
    public PageMenu(int id, Inventory inventory) {
        this(id, new SimpleContainer(ICONS), List.of());
    }

    private PageMenu(int id, Container icons, List<String> commands) {
        super(ModContent.PAGE.get(), id);
        this.icons = icons;
        this.commands = commands;
        // (far off the screen: they are there to carry the pictures, not to be seen or clicked)
        for (int i = 0; i < ICONS; i++) addSlot(new Slot(icons, i, -4000, -4000));
    }

    public ItemStack icon(int slot) {
        return slot >= 0 && slot < ICONS ? icons.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || button < 0 || button >= commands.size()) return false;
        String command = commands.get(button);
        if (command == null) {
            server.closeContainer();
            return true;
        }
        // (most buttons lead to another page, which takes this one's place)
        if (server.level().getServer() != null) {
            server.level().getServer().getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }
        // a button that did something rather than lead somewhere - begin a trial, a rematch, off to the hub - leaves
        // nothing to read: the page gets out of the way by itself
        if (server.containerMenu == this) server.closeContainer();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static String command(ActionButton button) {
        return button.action().isPresent() && button.action().get() instanceof StaticAction action && action.value() instanceof ClickEvent.RunCommand run ? run.command() : null;
    }

    private static Component label(ActionButton button, boolean exit) {
        MutableComponent label = Component.empty().append(button.button().label());
        if (button.button().tooltip().isPresent()) {
            Component tip = button.button().tooltip().get();
            label.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tip)));
        }
        if (exit) label.withStyle(style -> style.withInsertion(EXIT));
        return label;
    }

    /** What a page says, packed into one component: heading, paragraphs, buttons. */
    public static Component pack(Dialog dialog, Container icons) {
        MutableComponent body = Component.empty();
        int used = 0;
        for (DialogBody part : dialog.common().body()) {
            if (part instanceof PlainMessage message) {
                body.append(Component.empty().append(message.contents()));
            } else if (part instanceof ItemBody item) {
                MutableComponent line = Component.empty();
                if (item.description().isPresent()) line.append(item.description().get().contents());
                if (used < ICONS) {
                    int slot = used++;
                    if (icons != null) icons.setItem(slot, item.item().create());
                    line.withStyle(style -> style.withInsertion(ICON + slot));
                }
                body.append(line);
            }
        }
        MutableComponent buttons = Component.empty();
        int columns = 1;
        if (dialog instanceof MultiActionDialog multi) {
            columns = Math.max(1, multi.columns());
            for (ActionButton button : multi.actions()) buttons.append(label(button, false));
            if (multi.exitAction().isPresent()) buttons.append(label(multi.exitAction().get(), true));
        } else if (dialog instanceof NoticeDialog notice) {
            buttons.append(label(notice.action(), true));
        }
        int across = columns;
        buttons.withStyle(style -> style.withInsertion(String.valueOf(across)));
        return Component.empty().append(Component.empty().append(dialog.common().title())).append(body).append(buttons);
    }

    /** What each button of a page does, in the order {@link #pack} lists them. */
    public static List<String> commands(Dialog dialog) {
        List<String> out = new ArrayList<>();
        if (dialog instanceof MultiActionDialog multi) {
            for (ActionButton button : multi.actions()) out.add(command(button));
            if (multi.exitAction().isPresent()) out.add(command(multi.exitAction().get()));
        } else if (dialog instanceof NoticeDialog notice) {
            out.add(command(notice.action()));
        }
        return out;
    }

    /** Shows a player a page. */
    public static void open(ServerPlayer player, Dialog dialog) {
        Container icons = new SimpleContainer(ICONS);
        Component packed = pack(dialog, icons);
        List<String> commands = commands(dialog);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PageMenu(id, icons, commands), packed));
    }
}
