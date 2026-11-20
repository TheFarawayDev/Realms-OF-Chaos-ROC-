package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.god.Ranged;
import com.thefarawaydev.realmsofchaos.god.WeaponKind;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A god's relic weapon. What it is - blade, dagger, polearm, hammer, scythe, staff, bow, thrown weapon or relic -
 * comes from the god's {@link WeaponKind}: that sets its melee stats and what right-click does. Every relic also
 * carries its god's hit power, levels up with kills and can be forged with stars (see {@link WeaponProgress}).
 */
public class GodWeaponItem extends Item {
    private static final int SURGE_COOLDOWN_TICKS = 20 * 12;
    /** Ranged kinds keep the item cooldown for their shots, so their Divine Surge is timed here instead. */
    private static final Map<String, Long> SURGE_READY = new ConcurrentHashMap<>();

    private final God god;

    public GodWeaponItem(God god, Properties properties) {
        super(properties);
        this.god = god;
    }

    public God god() {
        return god;
    }

    /** Builds the item properties: melee damage, attack speed and reach all follow the weapon kind and the rank. */
    public static Properties properties(God god, ResourceKey<Item> key) {
        WeaponKind kind = god.kind();
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        god.baseDamage() * kind.meleeScale(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        kind.attackSpeed(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (kind.reach() > 0) {
            attributes.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(RealmsOfChaos.id("relic_reach"),
                    kind.reach(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        Rarity rarity = god.rank() <= 3 ? Rarity.EPIC : god.rank() <= 9 ? Rarity.RARE : Rarity.UNCOMMON;
        return new Properties().setId(key).stacksTo(1).attributes(attributes.build()).rarity(rarity).fireResistant();
    }

    /** Damage of one ranged shot from this stack. */
    public float rangedDamage(ItemStack stack) {
        return (float) (god.baseDamage() * god.kind().rangedScale() * WeaponProgress.multiplier(stack));
    }

    // ---- melee -----------------------------------------------------------------------------------------------

    /** Level and stars add damage on top of the base attribute. */
    @Override
    public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            ItemStack held = attacker.getMainHandItem();
            if (held.getItem() == this) return (float) (damage * (WeaponProgress.multiplier(held) - 1.0));
        }
        return 0.0F;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (attacker.level() instanceof ServerLevel level) {
            god.power().hit(level, target, attacker, god);
            god.applyTargetEffect(target);
            god.applySelfEffect(attacker);
            if (god.kind() == WeaponKind.SCYTHE) {
                // a scythe's edge carries through everything beside the target
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                        e -> e != target && Power.canHurt(attacker, e))) {
                    other.hurtServer(level, Power.source(level, attacker), (float) (god.baseDamage() * 0.35 * WeaponProgress.multiplier(stack)));
                }
            }
        }
    }

    // ---- right-click -----------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        WeaponKind kind = god.kind();
        boolean surge = !kind.ranged() || player.isShiftKeyDown();
        if (!(level instanceof ServerLevel serverLevel)) {
            return (!surge && player.getCooldowns().isOnCooldown(stack)) ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (surge) {
            return surge(serverLevel, player, stack, kind) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        if (kind == WeaponKind.RELIC) {
            invoke(serverLevel, player, stack);
        } else {
            Ranged.fire(serverLevel, player, god, stack, rangedDamage(stack), null);
        }
        player.getCooldowns().addCooldown(stack, Math.max(4, (int) (kind.useCooldown() * WeaponProgress.cooldownScale(stack))));
        return InteractionResult.SUCCESS;
    }

    /** A relic has no edge and no bolt: it releases its god's power on everything within five blocks. */
    private void invoke(ServerLevel level, Player player, ItemStack stack) {
        float damage = rangedDamage(stack);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0), x -> Power.canHurt(player, x))) {
            e.hurtServer(level, Power.source(level, player), damage);
            god.power().hit(level, e, player, god);
            god.applyTargetEffect(e);
        }
        god.applySelfEffect(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    private boolean surge(ServerLevel level, Player player, ItemStack stack, WeaponKind kind) {
        int cooldown = (int) (SURGE_COOLDOWN_TICKS * WeaponProgress.cooldownScale(stack));
        if (kind.ranged()) {
            String key = player.getUUID() + "/" + god.id();
            long now = level.getGameTime();
            Long ready = SURGE_READY.get(key);
            if (ready != null && now < ready && ready - now <= SURGE_COOLDOWN_TICKS) return false;
            SURGE_READY.put(key, now + cooldown);
        } else {
            if (player.getCooldowns().isOnCooldown(stack)) return false;
            player.getCooldowns().addCooldown(stack, cooldown);
        }
        Power.surge(level, player, god);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    // ---- levelling -------------------------------------------------------------------------------------------

    /** Called by the loaders whenever a living thing dies: a kill with a relic in hand feeds that relic. */
    public static void onKill(LivingEntity victim, DamageSource source) {
        TarotEffects.onDeath(victim, source);
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof GodWeaponItem weapon)) return;
        int gained = WeaponProgress.addXp(held, WeaponProgress.xpFor(victim));
        if (gained > 0) {
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".levelup",
                    held.getHoverName(), WeaponProgress.level(held)), weapon.god().line().color()));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    // ---- presentation ----------------------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        int stars = WeaponProgress.stars(stack), level = WeaponProgress.level(stack);
        Component base = super.getName(stack);
        if (stars == 0 && level == 1) return base;
        StringBuilder suffix = new StringBuilder();
        if (level > 1) suffix.append(" +").append(level - 1);
        if (stars > 0) suffix.append(' ').append("★".repeat(stars));
        return base.copy().append(Component.literal(suffix.toString()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        String ns = "tooltip." + RealmsOfChaos.MOD_ID + ".";
        int level = WeaponProgress.level(stack), stars = WeaponProgress.stars(stack);
        builder.accept(god.displayTitle().copy().withStyle(Style.EMPTY.withColor(god.line().color()).withItalic(true)));
        builder.accept(Component.empty().append(god.kind().displayName()).append(Component.literal(" - "))
                .append(Component.translatable(ns + "line", god.line().displayName()))
                .append(Component.literal(" - ")).append(Component.translatable(ns + "rank", god.rank()))
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        builder.accept(god.kind().useHint().copy().withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(Component.translatable(ns + "ability", god.power().description()).withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(level >= WeaponProgress.MAX_LEVEL
                ? Component.translatable(ns + "level.max").withStyle(Style.EMPTY.withColor(0x55FF55))
                : Component.translatable(ns + "level", level, WeaponProgress.xp(stack), WeaponProgress.xpNeeded(level)).withStyle(Style.EMPTY.withColor(0x55FF55)));
        builder.accept(Component.translatable(ns + "stars", stars).withStyle(Style.EMPTY.withColor(0xF5B82E)));
        int bonus = (int) Math.round((WeaponProgress.multiplier(stack) - 1.0) * 100);
        if (bonus > 0) builder.accept(Component.translatable(ns + "bonus", bonus).withStyle(Style.EMPTY.withColor(0xF5B82E)));
    }
}
