package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A voodoo doll. Right-click a creature to bind the doll to it; after that, right-clicking the doll stabs it and
 * the bound creature feels it from up to 64 blocks away, through walls. Sneak + right-click unbinds. What the stab
 * does depends on the doll's pin. Gods can be bound too, but shrug off half the harm and all of the curses.
 */
public class VoodooDollItem extends Item {
    public enum Pin { PAIN, BINDING, ROT, EMBERS }

    public static final double RANGE = 64.0;
    private static final int STAB_COOLDOWN_TICKS = 60;
    private static final String TARGET = "roc_doll_target", TARGET_NAME = "roc_doll_name";
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".doll.";

    private final Pin pin;

    public VoodooDollItem(Pin pin, Properties properties) {
        super(properties);
        this.pin = pin;
    }

    public Pin pin() {
        return pin;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static UUID boundTo(ItemStack stack) {
        String id = tag(stack).getStringOr(TARGET, "");
        try {
            return id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void bind(ItemStack stack, LivingEntity target) {
        CompoundTag tag = tag(stack);
        tag.putString(TARGET, target.getUUID().toString());
        tag.putString(TARGET_NAME, target.getDisplayName().getString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    public static void unbind(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);        // in creative the passed stack is a copy
        if (boundTo(held) != null || target instanceof Player) return InteractionResult.PASS;
        bind(held, target);
        if (player instanceof ServerPlayer sp) {
            Hud.actionBar(sp, Hud.colored(Component.translatable(HUD + "bound", target.getDisplayName()), 0xC77DFF));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID id = boundTo(stack);
        if (id == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            unbind(stack);
            Hud.actionBar(sp, Hud.colored(Component.translatable(HUD + "unbound"), 0xAAAAAA));
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        Entity found = serverLevel.getEntity(id);
        if (!(found instanceof LivingEntity target) || !target.isAlive()) {
            unbind(stack);
            Hud.actionBar(sp, Hud.colored(Component.translatable(HUD + "lost"), 0xFF5555));
            return InteractionResult.SUCCESS;
        }
        if (target.distanceTo(player) > RANGE) {
            Hud.actionBar(sp, Hud.colored(Component.translatable(HUD + "far", target.getDisplayName()), 0xFF5555));
            return InteractionResult.SUCCESS;
        }
        stab(serverLevel, sp, target);
        player.getCooldowns().addCooldown(stack, STAB_COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    /** The bound creature takes the stab wherever it is. */
    public void stab(ServerLevel level, ServerPlayer player, LivingEntity target) {
        float scale = target instanceof GodBoss ? 0.5F : 1.0F;
        var source = level.damageSources().indirectMagic(player, player);
        switch (pin) {
            case PAIN -> target.hurtServer(level, source, 8.0F * scale);
            case BINDING -> {
                target.hurtServer(level, source, 2.0F * scale);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            }
            case ROT -> {
                target.hurtServer(level, source, 3.0F * scale);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
            }
            case EMBERS -> {
                target.hurtServer(level, source, 3.0F * scale);
                target.igniteForSeconds(6.0F);
            }
        }
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        String ns = "doll." + RealmsOfChaos.MOD_ID + ".";
        builder.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(Style.EMPTY.withColor(0x55FFFF)));
        String name = tag(stack).getStringOr(TARGET_NAME, "");
        if (boundTo(stack) == null) {
            builder.accept(Component.translatable(ns + "unbound").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        } else {
            builder.accept(Component.translatable(ns + "bound", name).withStyle(Style.EMPTY.withColor(0xC77DFF)));
            builder.accept(Component.translatable(ns + "use").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }
    }
}
