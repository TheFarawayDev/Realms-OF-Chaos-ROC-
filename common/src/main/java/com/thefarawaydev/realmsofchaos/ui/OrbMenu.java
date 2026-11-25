package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.CornucopiaBlock;
import com.thefarawaydev.realmsofchaos.block.OrbBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Orb of Plenty's screen: a slot for its core, one for the ichor that pays for what it makes, the pattern in the
 * middle - shown, not given: click it with anything to have the orb make that, with an empty hand to clear it - and
 * four slots for what it has made. Round the pattern a ring fills as the next one comes; under it the orb says how
 * it stands. What it says is worked out on the server and travels as the menu's numbers, like a furnace's flame.
 */
public class OrbMenu extends AbstractContainerMenu {
    /** The numbers the screen is sent. */
    public static final int SHRINE = 0, MODE = 1, PROGRESS = 2, SECONDS = 3, COST = 4, REFUSED = 5, DATA = 6;
    /** {@link #SHRINE}'s bits: whole, on its plinth, at home; and how many of its four columns stand, from bit 3. */
    public static final int WHOLE = 1, PLINTH = 2, HOME = 4, COLUMNS_SHIFT = 3;
    /** What the orb is doing. */
    public static final int IDLE = 0, BRINGING = 1, MAKING = 2, WAITING = 3;
    /** Where the slots sit on the screen. */
    public static final int CORE_X = 20, CORE_Y = 22, ICHOR_X = 20, ICHOR_Y = 48, PATTERN_X = 80, PATTERN_Y = 37, OUT_X = 131, OUT_Y = 28, INVENTORY_Y = 104;
    /** The first of the player's own slots in the menu. */
    private static final int PLAYER = OrbBlockEntity.SIZE;

    private final Container orb;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private int refused;

    /** The player's side: empty, for the server to fill. */
    public OrbMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(OrbBlockEntity.SIZE), new SimpleContainerData(DATA), ContainerLevelAccess.NULL);
    }

    /** The server's side, for an orb in the world. */
    public OrbMenu(int id, Inventory inventory, OrbBlockEntity orb) {
        this(id, inventory, orb, null, ContainerLevelAccess.create(orb.getLevel(), orb.getBlockPos()));
    }

    private OrbMenu(int id, Inventory inventory, Container orb, ContainerData data, ContainerLevelAccess access) {
        super(ModContent.ORB_MENU.get(), id);
        this.orb = orb;
        this.access = access;
        this.data = data != null ? data : new Live();
        addSlot(new Only(orb, OrbBlockEntity.CORE, CORE_X, CORE_Y, "orb_core"));
        addSlot(new Only(orb, OrbBlockEntity.ICHOR, ICHOR_X, ICHOR_Y, "orb_ichor"));
        addSlot(new Pattern(orb, PATTERN_X, PATTERN_Y));
        for (int i = 0; i < OrbBlockEntity.OUTPUTS; i++) addSlot(new Made(orb, OrbBlockEntity.OUT + i, OUT_X + (i % 2) * 18, OUT_Y + (i / 2) * 18));
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(this.data);
    }

    public int data(int index) {
        return data.get(index);
    }

    // ---- the slots -------------------------------------------------------------------------------------------

    /** The core's slot and the ichor's: only what belongs there goes in. */
    private static final class Only extends Slot {
        private final Identifier icon;

        Only(Container container, int slot, int x, int y, String icon) {
            super(container, slot, x, y);
            this.icon = RealmsOfChaos.id("container/slot/" + icon);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() == OrbBlockEntity.CORE ? CornucopiaBlock.Core.of(stack) != CornucopiaBlock.Core.NONE : stack.is(ModContent.DIVINE_ICHOR.get());
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return getContainerSlot() == OrbBlockEntity.CORE ? 1 : OrbBlockEntity.MAX_ICHOR;
        }

        @Override
        public Identifier getNoItemIcon() {
            return icon;
        }
    }

    /** The pattern: a picture, never taken or given - see {@link #clicked}. */
    private static final class Pattern extends Slot {
        Pattern(Container container, int x, int y) {
            super(container, OrbBlockEntity.PATTERN, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public Identifier getNoItemIcon() {
            return RealmsOfChaos.id("container/slot/orb_pattern");
        }
    }

    /** What the orb has made: to be taken, never put in. */
    private static final class Made extends Slot {
        Made(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    // ---- clicks ----------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot == OrbBlockEntity.PATTERN) {
            // shown, not given: whatever is held becomes the pattern and stays in hand; an empty hand clears it
            if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) return;
            ItemStack held = getCarried();
            if (held.isEmpty()) {
                if (orb.getItem(OrbBlockEntity.PATTERN).isEmpty()) return;
                orb.setItem(OrbBlockEntity.PATTERN, ItemStack.EMPTY);
                sound(player, "ui_click", 0.8F);
            } else if (!OrbBlockEntity.replicable(held)) {
                refused = 60;
            } else if (!ItemStack.isSameItem(orb.getItem(OrbBlockEntity.PATTERN), held)) {
                orb.setItem(OrbBlockEntity.PATTERN, held.copyWithCount(1));
                refused = 0;
                sound(player, "orb_pattern", 1.0F);
            }
            return;
        }
        boolean ichorBefore = slot == OrbBlockEntity.ICHOR && !getCarried().isEmpty();
        int before = orb.getItem(OrbBlockEntity.ICHOR).getCount();
        super.clicked(slot, button, input, player);
        if (ichorBefore && orb.getItem(OrbBlockEntity.ICHOR).getCount() > before) sound(player, "orb_ichor", 1.0F);
    }

    private void sound(Player player, String id, float pitch) {
        access.execute((level, pos) -> level.playSound(null, pos, ModContent.sound(id), SoundSource.BLOCKS, 0.8F, pitch));
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != OrbBlockEntity.PATTERN;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= PLAYER;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == OrbBlockEntity.PATTERN) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), was = stack.copy();
        int end = slots.size();
        if (index < PLAYER) {
            if (!moveItemStackTo(stack, PLAYER, end, true)) return ItemStack.EMPTY;
        } else if (CornucopiaBlock.Core.of(stack) != CornucopiaBlock.Core.NONE) {
            if (!moveItemStackTo(stack, OrbBlockEntity.CORE, OrbBlockEntity.CORE + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModContent.DIVINE_ICHOR.get())) {
            if (!moveItemStackTo(stack, OrbBlockEntity.ICHOR, OrbBlockEntity.ICHOR + 1, false)) return ItemStack.EMPTY;
            sound(player, "orb_ichor", 1.0F);
        } else if (index < end - 9) {
            if (!moveItemStackTo(stack, end - 9, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER, end - 9, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == was.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return was;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModContent.CORNUCOPIA.get());
    }

    @Override
    public void broadcastChanges() {
        if (refused > 0) refused--;
        super.broadcastChanges();
    }

    // ---- what the screen is told -----------------------------------------------------------------------------

    /** The numbers for the screen, worked out from the orb in the world each time they are sent. */
    private final class Live implements ContainerData {
        private int[] last = new int[DATA];
        private long when = -1;

        @Override
        public int get(int index) {
            if (index == REFUSED) return refused;
            // (worked out once a tick, not once for each number)
            access.execute((level, pos) -> {
                if (level instanceof ServerLevel server && server.getGameTime() != when) {
                    when = server.getGameTime();
                    last = report(server, pos);
                }
            });
            return last[index];
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA;
        }
    }

    /** How the shrine round an orb stands, as the screen's numbers. */
    public static int[] report(ServerLevel level, BlockPos pos) {
        int[] out = new int[DATA];
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CornucopiaBlock) || !(CornucopiaBlock.orb(level, pos) instanceof OrbBlockEntity orb)) return out;
        int columns = 0;
        for (BlockPos c : CornucopiaBlock.columns(pos)) if (level.getBlockState(c).is(ModContent.PLENTY_COLUMN.get())) columns++;
        boolean plinth = level.getBlockState(pos.below()).is(ModContent.PLENTY_PLINTH.get()), whole = plinth && columns == 8;
        CornucopiaBlock.Core core = orb.core();
        boolean home = core != CornucopiaBlock.Core.NONE && CornucopiaBlock.atHome(level, pos, core);
        out[SHRINE] = (whole ? WHOLE : 0) | (plinth ? PLINTH : 0) | (home ? HOME : 0) | (columns / 2) << COLUMNS_SHIFT;
        if (core == CornucopiaBlock.Core.NONE) return out;
        int speed = home ? 2 : 1;
        // (the shrine steps forward on the clock: the seconds left count from the next step)
        int toStep = (int) (CornucopiaBlock.STEP - level.getGameTime() % CornucopiaBlock.STEP);
        if (orb.pattern() != null) {
            ItemStack pattern = new ItemStack(orb.pattern());
            int steps = OrbBlockEntity.steps(pattern), cost = OrbBlockEntity.cost(pattern);
            out[COST] = cost;
            boolean waiting = orb.work() >= steps && orb.ichor() < cost;
            out[MODE] = waiting ? WAITING : MAKING;
            out[PROGRESS] = Math.min(1000, orb.work() * 1000 / steps);
            int stepsLeft = Math.max(0, (steps - orb.work() + speed - 1) / speed);
            out[SECONDS] = waiting ? 0 : Math.max(0, (stepsLeft - 1) * CornucopiaBlock.STEP + toStep) / 20;
        } else {
            out[MODE] = BRINGING;
            int progress = state.getValue(CornucopiaBlock.PROGRESS);
            out[PROGRESS] = progress * 1000 / CornucopiaBlock.STEPS;
            int stepsLeft = Math.max(0, (CornucopiaBlock.STEPS - progress + speed - 1) / speed);
            out[SECONDS] = Math.max(0, (stepsLeft - 1) * CornucopiaBlock.STEP + toStep) / 20;
        }
        return out;
    }
}
