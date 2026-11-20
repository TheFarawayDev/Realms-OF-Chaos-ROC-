package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
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

/** A single tarot card: stackable, and spent when it is played (the Deck of Fate is the unlimited version). */
public class TarotCardItem extends Item {
    private static final int PLAY_COOLDOWN_TICKS = 40;
    private final Tarot card;

    public TarotCardItem(Tarot card, Properties properties) {
        super(properties);
        this.card = card;
    }

    public Tarot card() {
        return card;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            card.cast(serverLevel, sp);
            Hud.actionBar(sp, Hud.colored(card.displayName(), 0xF5B82E));
            player.getCooldowns().addCooldown(stack, PLAY_COOLDOWN_TICKS);
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(card.description().copy().withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(Component.translatable("tarot." + RealmsOfChaos.MOD_ID + ".single").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }
}
