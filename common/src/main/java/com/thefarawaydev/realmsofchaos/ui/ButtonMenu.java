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
    public static final int SIZE = 27;

    protected final SimpleContainer view;
    protected final ServerPlayer viewer;

    protected ButtonMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer view) {
        super(MenuType.GENERIC_9x3, id, inventory, view, 3);
        this.view = view;
        this.viewer = viewer;
    }

    /** A button was pressed. */
    protected abstract void press(int slot);

    /** Fill {@link #view} for the current state. */
    protected abstract void render();

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < SIZE && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            press(slot);
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
        for (int i = 0; i < SIZE; i++) view.setItem(i, filler.copy());
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
