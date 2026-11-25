package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.core.BlockPos;
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
 * What the Orb of Plenty remembers: the pattern it has been shown (any item, to be made again and again), the Divine
 * Ichor it holds to pay for each one, and how far it has come toward the next.
 */
public class OrbBlockEntity extends BlockEntity {
    /** The most ichor an orb holds. */
    public static final int MAX_ICHOR = 64;
    /** Items the orb will not make: those that cannot be had in survival, the gods' own gifts, and its own makings. */
    public static final TagKey<Item> UNREPLICABLE = TagKey.create(net.minecraft.core.registries.Registries.ITEM, RealmsOfChaos.id("unreplicable"));

    private Item pattern;
    private int ichor;
    private int work;

    public OrbBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ORB_ENTITY.get(), pos, state);
    }

    public Item pattern() {
        return pattern;
    }

    public int ichor() {
        return ichor;
    }

    public int work() {
        return work;
    }

    /** Sets what the orb makes (null to make its core's resource again). */
    public void setPattern(Item item) {
        pattern = item;
        work = 0;
        setChanged();
    }

    /** Pours in ichor from a stack. @return how much went in */
    public int pour(int count) {
        int room = Math.min(count, MAX_ICHOR - ichor);
        ichor += room;
        if (room > 0) setChanged();
        return room;
    }

    public void addWork(int steps) {
        work += steps;
        setChanged();
    }

    /** Spends the ichor for one copy of the pattern and starts the next. @return whether it could */
    public boolean spend(int cost) {
        if (ichor < cost) return false;
        ichor -= cost;
        work = 0;
        setChanged();
        return true;
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

    /** How many steps of the shrine one copy takes: five minutes for a common thing, longer the rarer it is. */
    public static int steps(ItemStack stack) {
        Rarity r = stack.getRarity();
        int k = r == Rarity.EPIC ? 4 : r == Rarity.RARE ? 3 : r == Rarity.UNCOMMON ? 2 : 1;
        return CornucopiaBlock.STEPS * k;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (pattern != null) out.store("pattern", BuiltInRegistries.ITEM.byNameCodec(), pattern);
        out.putInt("ichor", ichor);
        out.putInt("work", work);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        pattern = in.read("pattern", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        ichor = in.getIntOr("ichor", 0);
        work = in.getIntOr("work", 0);
    }
}
