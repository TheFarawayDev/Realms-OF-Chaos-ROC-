package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.item.FieldBagItem;
import com.thefarawaydev.realmsofchaos.world.HubTiers;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The inside of a Field Tower Bag. Unlike the mod's button menus this one really holds things: it is an ordinary
 * chest screen whose slots are the bag's own contents. Every change is written straight back into the bag, the bag
 * itself cannot be moved while it is open, and no bag goes inside a bag.
 */
public class BagMenu extends ChestMenu {
    private final ServerPlayer owner;
    private final ItemStack bag;
    /** The slot of the player's inventory the open bag sits in. */
    private final int bagSlot;
    private final Store store;
    private final int rows;

    /** The bag's slots. Rows the bag has not grown into yet are kept, but not shown. */
    private static final class Store extends SimpleContainer {
        private final ItemStack bag;
        private final NonNullList<ItemStack> hidden;

        Store(ItemStack bag, int rows) {
            super(rows * 9);
            this.bag = bag;
            NonNullList<ItemStack> all = FieldBagItem.contents(bag);
            hidden = NonNullList.create();
            for (int i = rows * 9; i < all.size(); i++) hidden.add(all.get(i));
            // (straight into the list: nothing has changed yet, so there is nothing to write back)
            for (int i = 0; i < rows * 9; i++) getItems().set(i, all.get(i));
        }

        void save() {
            java.util.List<ItemStack> all = new java.util.ArrayList<>(getItems());
            all.addAll(hidden);
            FieldBagItem.store(bag, all);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    }

    private BagMenu(int id, Inventory inventory, ServerPlayer owner, ItemStack bag, int bagSlot, Store store, int rows) {
        super(Skin.menuType(rows), id, inventory, store, rows);
        this.owner = owner;
        this.bag = bag;
        this.bagSlot = bagSlot;
        this.store = store;
        this.rows = rows;
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!FieldBagItem.isBag(bag)) return;
        int rows = HubTiers.bagRows(player.level().getServer());
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BagMenu(id, inventory, player, bag, slot, new Store(bag, rows), rows),
                Skin.title(player, rows == 6 ? Skin.Panel.BAG_LARGE : Skin.Panel.BAG, Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".bag", rows * 9))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    public int rows() {
        return rows;
    }

    private boolean isBagSlot(Slot slot) {
        return slot.container == owner.getInventory() && slot.getContainerSlot() == bagSlot;
    }

    /** Would this click move the open bag, or put a bag into the bag? Then it does nothing. */
    public boolean forbidden(int slotIndex, int button, ContainerInput input) {
        if (input == ContainerInput.SWAP) {
            // a number key (or the off-hand key) swaps that hotbar slot with the slot under the cursor
            if (button == bagSlot) return true;
            ItemStack swapped = button >= 0 && button < owner.getInventory().getContainerSize() ? owner.getInventory().getItem(button) : ItemStack.EMPTY;
            if (FieldBagItem.isBag(swapped) && slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).container == store) return true;
        }
        if (slotIndex < 0 || slotIndex >= slots.size()) return false;
        Slot slot = slots.get(slotIndex);
        if (isBagSlot(slot)) return true;
        if (slot.container == store && FieldBagItem.isBag(getCarried())) return true;
        return input == ContainerInput.QUICK_MOVE && slot.container != store && FieldBagItem.isBag(slot.getItem());
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (forbidden(slotIndex, button, input)) {
            sendAllDataToRemote();          // undo whatever the client predicted
            return;
        }
        super.clicked(slotIndex, button, input, player);
        store.save();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner.getInventory().getItem(bagSlot) == bag && !bag.isEmpty();
    }

    @Override
    public void removed(Player player) {
        store.save();
        super.removed(player);
    }
}
