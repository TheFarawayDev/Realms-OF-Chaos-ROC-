package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Ruins;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
 * A seeker: a compass found at a Ruined Gate that knows the way to one keystone. Right-click and it tells how far
 * and which way the nearest shrine of its keystone lies. It never runs out.
 */
public class SeekerItem extends Item {
    private final Line line;

    public SeekerItem(Line line, Properties properties) {
        super(properties);
        this.line = line;
    }

    public Line line() {
        return line;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            Ruins.point(sp, Ruins.Kind.of(line));
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".seeker.tooltip", Ruins.Kind.of(line).displayName())
                .withStyle(Style.EMPTY.withColor(line.color())));
    }
}
