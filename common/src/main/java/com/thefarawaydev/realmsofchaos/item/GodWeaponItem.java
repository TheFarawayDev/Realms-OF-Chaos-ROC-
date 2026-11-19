package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A god's relic weapon: a sword-class weapon scaled by rank, with a hit power and a right-click Divine Surge. */
public class GodWeaponItem extends Item {
    private static final int SURGE_COOLDOWN_TICKS = 20 * 12;

    private final God god;

    public GodWeaponItem(God god, Properties properties) {
        super(properties);
        this.god = god;
    }

    public God god() {
        return god;
    }

    /** Builds the item properties (attack stats scale with rank: rank 1 hits hardest). */
    public static Properties properties(God god, ResourceKey<Item> key) {
        ToolMaterial material = new ToolMaterial(
                BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
                1000 + (Line.RANKS - god.rank()) * 150,
                8.0F,
                (float) god.weaponDamageBonus(),
                22,
                ItemTags.NETHERITE_TOOL_MATERIALS);
        Rarity rarity = god.rank() <= 3 ? Rarity.EPIC : god.rank() <= 9 ? Rarity.RARE : Rarity.UNCOMMON;
        return new Properties().setId(key).sword(material, 3.0F, -2.4F).rarity(rarity).fireResistant();
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (attacker.level() instanceof ServerLevel level) {
            god.power().hit(level, target, attacker, god);
            god.applyTargetEffect(target);
            god.applySelfEffect(attacker);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            Power.surge(serverLevel, player, god);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        player.getCooldowns().addCooldown(stack, SURGE_COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(god.displayTitle().copy().withStyle(Style.EMPTY.withColor(god.line().color()).withItalic(true)));
        builder.accept(Component.translatable("tooltip." + RealmsOfChaos.MOD_ID + ".line", god.line().displayName())
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        builder.accept(Component.translatable("tooltip." + RealmsOfChaos.MOD_ID + ".rank", god.rank())
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        builder.accept(Component.translatable("tooltip." + RealmsOfChaos.MOD_ID + ".ability", god.power().description())
                .withStyle(Style.EMPTY.withColor(0x55FFFF)));
    }
}
