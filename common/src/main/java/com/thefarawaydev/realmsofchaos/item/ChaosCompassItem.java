package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Right-click to travel to the Hub of Realms. Inside a passage it points the way instead (sneak to travel). */
public class ChaosCompassItem extends Item {
    public ChaosCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            com.thefarawaydev.realmsofchaos.world.Passages.Spot spot = com.thefarawaydev.realmsofchaos.world.Passages.locate(sp);
            if (spot != null && !player.isShiftKeyDown() && sp.level() instanceof net.minecraft.server.level.ServerLevel passage) {
                // inside a passage the compass is a guide: it points at the nearest seal, then at the gate
                com.thefarawaydev.realmsofchaos.world.Passages.point(sp, passage, spot);
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
                return InteractionResult.SUCCESS;
            }
            Teleports.toHub(sp);
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 60);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass.tooltip"));
    }
}
