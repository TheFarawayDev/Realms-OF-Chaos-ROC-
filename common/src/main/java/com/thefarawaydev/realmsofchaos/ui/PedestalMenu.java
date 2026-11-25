package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.GatePedestalBlock;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Keystone Pedestal's screen: the gate's arch drawn above three cradles, one for each tower's keystone, in the
 * order they sit in the lintel - Poseidon's on the left, Zeus's in the middle, Hades's on the right. Lay a keystone
 * in its cradle (or shift-click it from the inventory) and it is set in the arch; once all three are, the arch on
 * the screen fills as the gate wakes.
 */
public class PedestalMenu extends AbstractContainerMenu {
    public static final int ARCH = 0, SET = 1, AWAKE = 2, DATA = 3;
    public static final int CRADLE_Y = 42, CRADLE_SPACING = 30, INVENTORY_Y = 104;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** The player's side: empty, for the server to fill. */
    public PedestalMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(3), new SimpleContainerData(DATA), ContainerLevelAccess.NULL);
    }

    /** The server's side, for a pedestal in the world. */
    public PedestalMenu(int id, Inventory inventory, ServerLevel level, BlockPos pos) {
        this(id, inventory, new GatePedestalBlock.Sockets(level, pos), live(level, pos), ContainerLevelAccess.create(level, pos));
    }

    private PedestalMenu(int id, Inventory inventory, Container sockets, ContainerData data, ContainerLevelAccess access) {
        super(ModContent.PEDESTAL_MENU.get(), id);
        this.data = data;
        this.access = access;
        for (Line line : Line.values()) addSlot(new Cradle(sockets, line, cradleX(line), CRADLE_Y));
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    /** Where a keystone's cradle is on the screen: as its socket is in the arch, Poseidon's left of Zeus's, Hades's right. */
    public static int cradleX(Line line) {
        return 80 + Ruins.cradle(line) * CRADLE_SPACING;
    }

    public int data(int index) {
        return data.get(index);
    }

    /** True if this keystone is set, as the screen was last told. */
    public boolean isSet(Line line) {
        return (data.get(SET) & 1 << line.ordinal()) != 0;
    }

    private static ContainerData live(ServerLevel level, BlockPos pos) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ARCH -> Ruins.hasArch(level, pos) ? 1 : 0;
                    case SET -> {
                        int mask = 0;
                        for (Line line : Line.values()) if (Ruins.isSet(level, pos, line)) mask |= 1 << line.ordinal();
                        yield mask;
                    }
                    case AWAKE -> Ruins.awake(level, pos) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA;
            }
        };
    }

    /** One keystone's cradle: takes that keystone and no other, and never gives it back. */
    private static final class Cradle extends Slot {
        private final Line line;

        Cradle(Container container, Line line, int x, int y) {
            super(container, line.ordinal(), x, y);
            this.line = line;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModContent.keystoneItem(line)) && !hasItem() && (container instanceof SimpleContainer || container.canPlaceItem(getContainerSlot(), stack));
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Identifier getNoItemIcon() {
            return RealmsOfChaos.id("container/slot/keystone_" + line.id());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < 3 || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), was = stack.copy();
        if (!moveItemStackTo(stack, 0, 3, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return was;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= 3;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModContent.GATE_PEDESTAL.get());
    }
}
