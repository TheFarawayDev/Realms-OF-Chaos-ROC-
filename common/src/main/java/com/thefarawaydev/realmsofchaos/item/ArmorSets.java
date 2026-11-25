package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Four sets of armour, each forged from a metal that takes a long road to win, each better than netherite in its own
 * way, and each giving its wearer a gift when all four pieces are worn together.
 */
public final class ArmorSets {
    public enum Set {
        /** The hardest metal there is: the most protection of all. Worn whole: resistance. */
        ADAMANTINE(45, new int[]{4, 7, 9, 4}, 18, 4.0F, 0.15F),
        /** Celestial bronze, forged with storm glass. Worn whole: swift and quick-handed. */
        CELESTIAL_BRONZE(38, new int[]{3, 7, 8, 3}, 22, 3.0F, 0.1F),
        /** Orichalcum, the metal of the drowned city. Worn whole: breathe and swim like the sea's own. */
        ORICHALCUM(38, new int[]{3, 6, 8, 3}, 20, 3.0F, 0.1F),
        /** Stygian iron, from the soul valleys. Worn whole: fire and withering cannot touch you. */
        STYGIAN_IRON(40, new int[]{3, 7, 8, 3}, 16, 3.5F, 0.15F);

        public final int durability, enchantability;
        /** Protection of boots, leggings, chestplate, helmet. */
        public final int[] defense;
        public final float toughness, knockback;
        public final ArmorMaterial material;

        Set(int durability, int[] defense, int enchantability, float toughness, float knockback) {
            this.durability = durability;
            this.defense = defense;
            this.enchantability = enchantability;
            this.toughness = toughness;
            this.knockback = knockback;
            Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
            map.put(ArmorType.BOOTS, defense[0]);
            map.put(ArmorType.LEGGINGS, defense[1]);
            map.put(ArmorType.CHESTPLATE, defense[2]);
            map.put(ArmorType.HELMET, defense[3]);
            map.put(ArmorType.BODY, defense[2]);
            this.material = new ArmorMaterial(durability, map, enchantability, SoundEvents.ARMOR_EQUIP_NETHERITE, toughness, knockback,
                    TagKey.create(Registries.ITEM, RealmsOfChaos.id("repairs_" + id())), ResourceKey.create(EquipmentAssets.ROOT_ID, RealmsOfChaos.id(id())));
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The four pieces of a set, in the order they are registered. */
    public static final List<ArmorType> PIECES = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    private ArmorSets() {}

    public static String pieceName(ArmorType type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            default -> "body";
        };
    }

    /** One piece, with a line under its name saying what the whole set gives. */
    public static Item create(Set set, ArmorType type, ResourceKey<Item> key) {
        return new Item(new Item.Properties().setId(key).humanoidArmor(set.material, type).rarity(set == Set.ADAMANTINE ? Rarity.EPIC : Rarity.RARE).fireResistant()
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                        Component.translatable("item." + RealmsOfChaos.MOD_ID + ".armor_set." + set.id()).withStyle(net.minecraft.ChatFormatting.GRAY)))));
    }

    /** The set a player wears whole, or null. */
    public static Set wornWhole(ServerPlayer player) {
        Set found = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getItemBySlot(slot).getItem());
            if (!id.getNamespace().equals(RealmsOfChaos.MOD_ID)) return null;
            Set set = null;
            for (Set s : Set.values()) if (id.getPath().startsWith(s.id() + "_")) set = s;
            if (set == null || (found != null && found != set)) return null;
            found = set;
        }
        return found;
    }

    private static int ticker;

    /** Every second, the gift of a whole set to whoever wears one. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (++ticker % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) tend(player);
    }

    /** Gives one player the gift of the set they wear whole, if they do. @return the set, or null */
    public static Set tend(ServerPlayer player) {
        Set set = wornWhole(player);
        if (set == null) return null;
        switch (set) {
            case ADAMANTINE -> give(player, MobEffects.RESISTANCE, 0);
            case CELESTIAL_BRONZE -> {
                give(player, MobEffects.SPEED, 0);
                give(player, MobEffects.HASTE, 0);
            }
            case ORICHALCUM -> {
                if (player.isInWater()) {
                    give(player, MobEffects.WATER_BREATHING, 0);
                    give(player, MobEffects.DOLPHINS_GRACE, 0);
                }
            }
            case STYGIAN_IRON -> {
                give(player, MobEffects.FIRE_RESISTANCE, 0);
                player.removeEffect(MobEffects.WITHER);
            }
        }
        return set;
    }

    private static void give(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int level) {
        player.addEffect(new MobEffectInstance(effect, 20 * 12, level, true, false, true));
    }
}
