package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.DeckMenu;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Deck of Fate, dropped by Hades: a stack of all 22 cards that never runs out. Right-click draws a card at
 * random; sneak + right-click opens the deck to choose one (a chosen card makes the deck rest longer).
 */
public class TarotDeckItem extends Item {
    public static final int DRAW_COOLDOWN_TICKS = 20 * 20;

    public TarotDeckItem(Properties properties) {
        super(properties);
    }

    /** Plays {@code card} from the deck in {@code stack} and starts the deck's recharge. */
    public static void play(ServerLevel level, ServerPlayer player, ItemStack stack, Tarot card, int cooldownTicks) {
        card.cast(level, player);
        Hud.actionBar(player, Hud.colored(card.displayName(), 0xF5B82E));
        player.getCooldowns().addCooldown(stack, cooldownTicks);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                DeckMenu.open(sp, hand);
            } else {
                Tarot card = Tarot.values()[serverLevel.getRandom().nextInt(Tarot.values().length)];
                play(serverLevel, sp, stack, card, DRAW_COOLDOWN_TICKS);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        String ns = "item." + RealmsOfChaos.MOD_ID + ".tarot_deck.";
        builder.accept(Component.translatable(ns + "lore1").withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(Component.translatable(ns + "lore2").withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(Component.translatable(ns + "lore3").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }
}
