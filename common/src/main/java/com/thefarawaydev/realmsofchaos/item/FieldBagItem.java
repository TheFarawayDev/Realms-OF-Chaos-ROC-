package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.FieldBlock;
import com.thefarawaydev.realmsofchaos.ui.BagMenu;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Field Tower Bag: a pack for the things that build the field network. Right-click opens it - three rows, six
 * once the hub has reached its third tier. What is in it stays in it: the contents are part of the bag itself, so
 * they survive death, storage and restarts wherever the bag goes. Sneak and right-click a block to set down the
 * first relay, jammer or waygate in the bag without opening it. A bag cannot be put inside a bag.
 */
public class FieldBagItem extends Item {
    public static final int MAX_SLOTS = 54;

    public FieldBagItem(Properties properties) {
        super(properties);
    }

    /** Everything in the bag, always {@link #MAX_SLOTS} long. */
    public static NonNullList<ItemStack> contents(ItemStack bag) {
        NonNullList<ItemStack> items = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
        bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    public static void store(ItemStack bag, List<ItemStack> items) {
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(new ArrayList<>(items)));
    }

    public static int used(ItemStack bag) {
        int n = 0;
        for (ItemStack stack : contents(bag)) if (!stack.isEmpty()) n++;
        return n;
    }

    /** The bag a new player is given: two Relay Tower Cores and a Waygate Obelisk to begin a network with. */
    public static ItemStack starter() {
        ItemStack bag = new ItemStack(ModContent.FIELD_BAG.get());
        store(bag, List.of(new ItemStack(ModContent.BLOCK_ITEMS.get("relay_core").get(), 2), new ItemStack(ModContent.BLOCK_ITEMS.get("waygate_obelisk").get())));
        return bag;
    }

    public static boolean isBag(ItemStack stack) {
        return stack.getItem() instanceof FieldBagItem;
    }

    public static boolean deployable(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof FieldBlock;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) BagMenu.open(sp, hand);
        return InteractionResult.SUCCESS;
    }

    /** Quick-deploy: sneaking, the first network block in the bag is set down where the player points. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack bag = context.getItemInHand();
        NonNullList<ItemStack> items = contents(bag);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!deployable(stack)) continue;
            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
            InteractionResult placed = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(context.getLevel(), player, context.getHand(), stack, hit));
            if (!placed.consumesAction()) continue;
            // (placing shrinks the stack unless the player is in creative mode)
            store(bag, items);
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".bag.nothing"), 0xAAAAAA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".field_bag.tooltip").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".field_bag.deploy").withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".field_bag.used", used(stack)).withStyle(Style.EMPTY.withColor(0xF5B82E)));
    }
}
