package com.thefarawaydev.realmsofchaos.ui;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;

/**
 * Base for the mod's in-game menus. They use the vanilla 9x3 chest screen with the server owning every slot, so
 * they behave the same on Fabric and Forge and need no custom packets. Icons are buttons; nothing can be taken.
 */
public abstract class ButtonMenu extends ChestMenu {
    protected final SimpleContainer view;
    protected final ServerPlayer viewer;
    protected final int size;
    /** True while {@link #press} runs for a shift-click. */
    protected boolean shiftClick;

    /** @param rows 3 for a single-chest screen, 6 for a double-chest screen */
    protected ButtonMenu(int id, Inventory inventory, ServerPlayer viewer, int rows) {
        this(id, inventory, viewer, rows, new SimpleContainer(rows * 9));
    }

    private ButtonMenu(int id, Inventory inventory, ServerPlayer viewer, int rows, SimpleContainer view) {
        super(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, id, inventory, view, rows);
        this.view = view;
        this.viewer = viewer;
        this.size = rows * 9;
    }

    public static int slot(int row, int column) {
        return row * 9 + column;
    }

    /** A button was pressed. */
    protected abstract void press(int slot);

    /** Fill {@link #view} for the current state. */
    protected abstract void render();

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < size && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            shiftClick = input == ContainerInput.QUICK_MOVE;
            press(slot);
            shiftClick = false;
        }
        if (viewer.containerMenu == this) {
            sendAllDataToRemote();      // undo whatever the client predicted
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    protected void clickSound() {
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    protected void fill(DyeColor color) {
        ItemStack filler = filler(color);
        for (int i = 0; i < size; i++) view.setItem(i, filler.copy());
    }

    /** A coloured border with a darker inside: reads as a framed panel. */
    protected void frame(DyeColor border, DyeColor inside) {
        ItemStack edge = filler(border), back = filler(inside);
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            view.setItem(i, (row == 0 || row == rows - 1 || col == 0 || col == 8 ? edge : back).copy());
        }
    }

    /** A named pane used as a label or meter segment. */
    protected static ItemStack pane(DyeColor color, Component name, List<Component> lore) {
        return icon(Items.STAINED_GLASS_PANE.pick(color), name, false, lore);
    }

    protected static Style plain(int color) {
        return Style.EMPTY.withItalic(false).withColor(color);
    }

    protected static MutableComponent text(String key, int color) {
        return Component.translatable(key).withStyle(plain(color));
    }

    protected static MutableComponent text(String key, int color, Object... args) {
        return Component.translatable(key, args).withStyle(plain(color));
    }

    protected static ItemStack icon(ItemLike item, Component name, boolean glint, List<Component> lore) {
        ItemStack stack = new ItemStack(item == null ? Items.BARRIER : item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        // keep the tooltip to our own text: hide attack stats
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
        return stack;
    }

    protected static ItemStack filler(DyeColor color) {
        ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.pick(color));
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        return stack;
    }
}
