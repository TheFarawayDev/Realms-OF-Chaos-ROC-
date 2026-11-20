package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** One of the three keystones a Ruined Gate needs. Found in the shrine its seeker points to. */
public class KeystoneItem extends Item {
    private final Line line;

    public KeystoneItem(Line line, Properties properties) {
        super(properties);
        this.line = line;
    }

    public Line line() {
        return line;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".keystone.tooltip").withStyle(Style.EMPTY.withColor(line.color())));
    }
}
