package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The panoply: eight pieces of armour out of the old stories, each with a power of its own rather than a set to
 * collect. Two helms, two for the body, two for the legs, two for the feet - so whoever owns them all still has to
 * choose. They are not found in chests: a lesser god may let one fall, or the forge will make one, slowly.
 */
public final class Panoply {
    public enum Piece {
        /** Unseen while you sneak. */
        HELM_OF_HADES(ArmorType.HELMET, EquipmentSlot.HEAD, 3, 2.0F, 0.0F, 2),
        /** The dark is no dark to you, and what means you harm is lit. */
        HELM_OF_ATHENA(ArmorType.HELMET, EquipmentSlot.HEAD, 3, 2.0F, 0.0F, 2),
        /** Wards every blow a little; hurt badly, it gives you a second wind. */
        AEGIS_CUIRASS(ArmorType.CHESTPLATE, EquipmentSlot.CHEST, 8, 3.0F, 0.1F, 3),
        /** The hide no blade went through: hard to wound, hard to move. */
        NEMEAN_PELT(ArmorType.CHESTPLATE, EquipmentSlot.CHEST, 7, 6.0F, 0.6F, 3),
        /** Strength in every blow. */
        GIRDLE_OF_HIPPOLYTA(ArmorType.LEGGINGS, EquipmentSlot.LEGS, 6, 2.0F, 0.0F, 2),
        /** Swift-footed. */
        GREAVES_OF_ACHILLES(ArmorType.LEGGINGS, EquipmentSlot.LEGS, 6, 2.0F, 0.0F, 2),
        /** Leap high, land soft. */
        TALARIA(ArmorType.BOOTS, EquipmentSlot.FEET, 3, 1.0F, 0.0F, 1),
        /** Fire and deep water cannot harm you. */
        STYX_TREADS(ArmorType.BOOTS, EquipmentSlot.FEET, 3, 2.0F, 0.0F, 1);

        public final ArmorType type;
        public final EquipmentSlot slot;
        public final int defense;
        public final float toughness, knockback;
        /** Which tier of forge material it is made of. */
        public final int tier;

        Piece(ArmorType type, EquipmentSlot slot, int defense, float toughness, float knockback, int tier) {
            this.type = type;
            this.slot = slot;
            this.defense = defense;
            this.toughness = toughness;
            this.knockback = knockback;
            this.tier = tier;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Item item() {
            return ModContent.PANOPLY.get(id()).get();
        }

        public ItemStack stack() {
            return new ItemStack(item());
        }

        public Component lore() {
            return Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + id() + ".lore");
        }

        public boolean wornBy(ServerPlayer player) {
            return player.getItemBySlot(slot).is(item());
        }
    }

    /** The Aegis gives its second wind no more often than this. */
    public static final int SECOND_WIND_TICKS = 20 * 60;

    private Panoply() {}

    /** Makes the item for one piece. Each has a material of its own, since each looks like nothing else. */
    public static Item create(Piece piece, ResourceKey<Item> key) {
        ArmorMaterial material = new ArmorMaterial(40, Map.of(piece.type, piece.defense), 18, SoundEvents.ARMOR_EQUIP_GOLD, piece.toughness, piece.knockback,
                TagKey.create(Registries.ITEM, RealmsOfChaos.id("repairs_panoply")), ResourceKey.create(EquipmentAssets.ROOT_ID, RealmsOfChaos.id(piece.id())));
        return new Item(new Item.Properties().setId(key).humanoidArmor(material, piece.type).rarity(Rarity.EPIC).fireResistant()
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        piece.lore().copy().withStyle(style -> style.withColor(0xF5E6C8).withItalic(false))))));
    }

    private static void give(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int level) {
        // (quietly: no swirl of particles round somebody for the clothes they stand up in)
        player.addEffect(new MobEffectInstance(effect, ticks, level, true, false, true));
    }

    private static int ticker;

    /** Called every server tick: once a second each piece does its work; the sandals watch for a fall all the time. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        boolean second = ++ticker % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (second) tend(player);
            else if (!player.onGround() && player.getDeltaMovement().y < -0.5 && Piece.TALARIA.wornBy(player)) give(player, MobEffects.SLOW_FALLING, 40, 0);
        }
    }

    /** Once a second for every player: what they are wearing does its work. @return how many pieces were at work */
    public static int tend(ServerPlayer player) {
        if (player.isSpectator()) return 0;
        int worn = 0;
        if (Piece.HELM_OF_HADES.wornBy(player)) {
            worn++;
            if (player.isShiftKeyDown()) give(player, MobEffects.INVISIBILITY, 50, 0);
        }
        if (Piece.HELM_OF_ATHENA.wornBy(player)) {
            worn++;
            give(player, MobEffects.NIGHT_VISION, 320, 0);
            if (player.level() instanceof ServerLevel level) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0), m -> m.isAlive() && m instanceof Enemy)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
                }
            }
        }
        if (Piece.AEGIS_CUIRASS.wornBy(player)) {
            worn++;
            give(player, MobEffects.RESISTANCE, 50, 0);
            if (player.getHealth() <= player.getMaxHealth() * 0.3F && !player.getCooldowns().isOnCooldown(Piece.AEGIS_CUIRASS.stack())) {
                player.getCooldowns().addCooldown(Piece.AEGIS_CUIRASS.stack(), SECOND_WIND_TICKS);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 12, 1));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 1));
                player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }
        if (Piece.NEMEAN_PELT.wornBy(player)) worn++;                 // (its hide is its power: toughness, and standing its ground)
        if (Piece.GIRDLE_OF_HIPPOLYTA.wornBy(player)) {
            worn++;
            give(player, MobEffects.STRENGTH, 50, 0);
        }
        if (Piece.GREAVES_OF_ACHILLES.wornBy(player)) {
            worn++;
            give(player, MobEffects.SPEED, 50, 0);
        }
        if (Piece.TALARIA.wornBy(player)) {
            worn++;
            give(player, MobEffects.JUMP_BOOST, 50, 1);
            if (!player.onGround() && player.getDeltaMovement().y < -0.5) give(player, MobEffects.SLOW_FALLING, 40, 0);
        }
        if (Piece.STYX_TREADS.wornBy(player)) {
            worn++;
            give(player, MobEffects.FIRE_RESISTANCE, 50, 0);
            give(player, MobEffects.WATER_BREATHING, 50, 0);
        }
        return worn;
    }
}
