package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What the Orb of Plenty holds: the core set in it, the Divine Ichor poured in to pay for what it makes, the pattern it
 * has been shown (a picture of an item, not the item: to be made again and again), and what it has made, waiting to be
 * taken - and how far it has come toward the next. All of it is handled in the orb's own screen, and hoppers can feed
 * it cores and ichor and take what it makes, from the orb itself or through its plinth.
 */
public class OrbBlockEntity extends BlockEntity implements WorldlyContainer {
    /** The most ichor an orb holds. */
    public static final int MAX_ICHOR = 64;
    /** Items the orb will not make: those that cannot be had in survival, the gods' own gifts, and its own makings. */
    public static final TagKey<Item> UNREPLICABLE = TagKey.create(net.minecraft.core.registries.Registries.ITEM, RealmsOfChaos.id("unreplicable"));
    /** Its slots: the core, the ichor, the pattern, and four for what it has made. */
    public static final int CORE = 0, ICHOR = 1, PATTERN = 2, OUT = 3, OUTPUTS = 4, SIZE = OUT + OUTPUTS;
    private static final int[] FEED = {CORE, ICHOR}, TAKE = {OUT, OUT + 1, OUT + 2, OUT + 3}, ALL = {CORE, ICHOR, OUT, OUT + 1, OUT + 2, OUT + 3};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int work;

    public OrbBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ORB_ENTITY.get(), pos, state);
    }

    public Item pattern() {
        return items.get(PATTERN).isEmpty() ? null : items.get(PATTERN).getItem();
    }

    public int ichor() {
        return items.get(ICHOR).is(ModContent.DIVINE_ICHOR.get()) ? items.get(ICHOR).getCount() : 0;
    }

    public int work() {
        return work;
    }

    public CornucopiaBlock.Core core() {
        return CornucopiaBlock.Core.of(items.get(CORE));
    }

    /** Sets what the orb makes (null to make its core's resource again). */
    public void setPattern(Item item) {
        setItem(PATTERN, item == null ? ItemStack.EMPTY : new ItemStack(item));
    }

    /** Pours in ichor from a stack. @return how much went in */
    public int pour(int count) {
        int room = Math.min(count, MAX_ICHOR - ichor());
        if (room <= 0) return 0;
        items.set(ICHOR, new ItemStack(ModContent.DIVINE_ICHOR.get(), ichor() + room));
        setChanged();
        return room;
    }

    public void addWork(int steps) {
        work += steps;
        setChanged();
    }

    /** Spends the ichor for one copy of the pattern and starts the next. @return whether it could */
    public boolean spend(int cost) {
        if (ichor() < cost) return false;
        items.get(ICHOR).shrink(cost);
        work = 0;
        setChanged();
        return true;
    }

    /** Puts something made into the orb's own slots. @return what would not fit */
    public ItemStack store(ItemStack stack) {
        stack = stack.copy();
        for (int i = OUT; i < SIZE && !stack.isEmpty(); i++) {
            ItemStack there = items.get(i);
            if (there.isEmpty()) {
                items.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            } else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(move);
                stack.shrink(move);
            }
        }
        setChanged();
        return stack;
    }

    /** True if an item may be made by the orb. */
    public static boolean replicable(ItemStack stack) {
        if (stack.isEmpty() || stack.is(UNREPLICABLE)) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem) return false;
        return !CornucopiaBlock.Core.of(stack).equals(CornucopiaBlock.Core.NONE) ? false : !stack.is(ModContent.DIVINE_ICHOR.get());
    }

    /**
     * What one copy of an item costs in ichor: one for a common thing, two for uncommon, four for rare, eight for epic;
     * four times that for a block of metal or gems (a beacon's base), twice for an ingot or gem (a beacon's payment).
     */
    public static int cost(ItemStack stack) {
        Rarity r = stack.getRarity();
        int c = r == Rarity.EPIC ? 8 : r == Rarity.RARE ? 4 : r == Rarity.UNCOMMON ? 2 : 1;
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS)) c *= 4;
        else if (stack.is(net.minecraft.tags.ItemTags.BEACON_PAYMENT_ITEMS)) c *= 2;
        return c;
    }

    /** What one copy costs an orb of a rank: its share of the full cost, never less than one. */
    public static int cost(ItemStack stack, int rank) {
        return Math.max(1, (cost(stack) * CornucopiaBlock.ichorShare(rank) + 99) / 100);
    }

    /** How many steps of the shrine one copy takes: five minutes for a common thing, longer the rarer it is. */
    public static int steps(ItemStack stack) {
        Rarity r = stack.getRarity();
        int k = r == Rarity.EPIC ? 4 : r == Rarity.RARE ? 3 : r == Rarity.UNCOMMON ? 2 : 1;
        return CornucopiaBlock.STEPS * k;
    }

    // ---- as a container: what the screen shows, and what hoppers reach -------------------------------------

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack out = ContainerHelper.removeItem(items, slot, count);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == PATTERN && !ItemStack.isSameItem(items.get(PATTERN), stack)) work = 0;
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case CORE -> CornucopiaBlock.Core.of(stack) != CornucopiaBlock.Core.NONE && items.get(CORE).isEmpty();
            case ICHOR -> stack.is(ModContent.DIVINE_ICHOR.get());
            default -> false;
        };
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return CornucopiaBlock.Core.of(stack) != CornucopiaBlock.Core.NONE ? 1 : Math.min(MAX_ICHOR, stack.getMaxStackSize());
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? FEED : side == Direction.DOWN ? TAKE : ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return (slot == CORE || slot == ICHOR) && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= OUT;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // the core shows in the orb: keep the block's look in step with what is set in it
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            BlockState state = getBlockState();
            CornucopiaBlock.Core now = core();
            if (state.getBlock() instanceof CornucopiaBlock && state.getValue(CornucopiaBlock.CORE) != now) {
                server.setBlock(worldPosition, state.setValue(CornucopiaBlock.CORE, now).setValue(CornucopiaBlock.PROGRESS, 0), 3);
                if (now != CornucopiaBlock.Core.NONE) server.playSound(null, worldPosition, ModContent.sound("orb_core"), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // the pattern is only a picture: everything else spills out when the orb is broken
        items.set(PATTERN, ItemStack.EMPTY);
        if (level != null) Containers.dropContents(level, pos, items);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("work", work);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        ContainerHelper.loadAllItems(in, items);
        work = in.getIntOr("work", 0);
        // an orb from before it had slots: its pattern and ichor were kept apart, and its core only in its look
        in.read("pattern", BuiltInRegistries.ITEM.byNameCodec()).ifPresent(item -> {
            if (items.get(PATTERN).isEmpty()) items.set(PATTERN, new ItemStack(item));
        });
        var legacy = in.getInt("ichor");
        if (legacy.isPresent()) {
            if (legacy.get() > 0) items.set(ICHOR, new ItemStack(ModContent.DIVINE_ICHOR.get(), legacy.get()));
            CornucopiaBlock.Core shown = getBlockState().getBlock() instanceof CornucopiaBlock ? getBlockState().getValue(CornucopiaBlock.CORE) : CornucopiaBlock.Core.NONE;
            if (shown != CornucopiaBlock.Core.NONE) items.set(CORE, new ItemStack(shown.core()));
        }
    }
}
