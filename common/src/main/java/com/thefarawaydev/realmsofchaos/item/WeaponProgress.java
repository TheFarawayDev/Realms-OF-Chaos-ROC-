package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Levelling and forging state of one relic weapon, stored on the item stack itself.
 *
 * Level 1-10 is earned: every kill made while holding the weapon gives it experience. Stars 0-5 are forged at the
 * Forge of Chaos with essences dropped by the gods. Both raise damage; stars also shorten recharge times.
 */
public final class WeaponProgress {
    public static final int MAX_LEVEL = 10;
    public static final int MAX_STARS = 5;
    private static final String LEVEL = "roc_level", XP = "roc_xp", STARS = "roc_stars";

    private WeaponProgress() {}

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void write(ItemStack stack, int level, int xp, int stars) {
        CompoundTag tag = tag(stack);
        tag.putInt(LEVEL, level);
        tag.putInt(XP, xp);
        tag.putInt(STARS, stars);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int level(ItemStack stack) {
        return Math.max(1, Math.min(MAX_LEVEL, tag(stack).getIntOr(LEVEL, 1)));
    }

    public static int xp(ItemStack stack) {
        return Math.max(0, tag(stack).getIntOr(XP, 0));
    }

    public static int stars(ItemStack stack) {
        return Math.max(0, Math.min(MAX_STARS, tag(stack).getIntOr(STARS, 0)));
    }

    /** Experience needed to go from {@code level} to the next. */
    public static int xpNeeded(int level) {
        return 20 * level + 10 * level * level;
    }

    /** Damage multiplier from level (+8% each) and stars (+10% each). 1.0 for a fresh weapon. */
    public static double multiplier(ItemStack stack) {
        return (1.0 + 0.08 * (level(stack) - 1)) * (1.0 + 0.10 * stars(stack));
    }

    /** Recharge multiplier from stars: 8% faster per star. */
    public static double cooldownScale(ItemStack stack) {
        return 1.0 - 0.08 * stars(stack);
    }

    /** @return the number of levels gained. */
    public static int addXp(ItemStack stack, int amount) {
        int level = level(stack), xp = xp(stack), gained = 0;
        if (level >= MAX_LEVEL || amount <= 0) return 0;
        xp += amount;
        while (level < MAX_LEVEL && xp >= xpNeeded(level)) {
            xp -= xpNeeded(level);
            level++;
            gained++;
        }
        if (level >= MAX_LEVEL) xp = 0;
        write(stack, level, xp, stars(stack));
        return gained;
    }

    public static void setStars(ItemStack stack, int stars) {
        write(stack, level(stack), xp(stack), Math.max(0, Math.min(MAX_STARS, stars)));
    }

    public static void setLevel(ItemStack stack, int level) {
        write(stack, Math.max(1, Math.min(MAX_LEVEL, level)), 0, stars(stack));
    }

    /** Experience a kill is worth: a little for ordinary mobs, a lot for a god. */
    public static int xpFor(LivingEntity victim) {
        if (victim instanceof GodBoss boss) return 40 + (16 - boss.god().rank()) * 12;
        return Math.max(1, Math.round(victim.getMaxHealth() / 4.0F));
    }

    /** Essences needed to forge star number {@code star} (1-5), and the weapon level it requires. */
    public static int starCost(int star) {
        return star * 2;
    }

    public static int starLevel(int star) {
        return star * 2;
    }
}
