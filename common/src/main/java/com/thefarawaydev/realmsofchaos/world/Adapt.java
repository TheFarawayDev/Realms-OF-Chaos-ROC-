package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.item.GodWeaponItem;
import com.thefarawaydev.realmsofchaos.item.WeaponProgress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * How the tower adapts to who is climbing it. A trial faced by several players is a harder trial: the god has more
 * health and hits harder for every extra challenger. A god that keeps beating the same player relents a little
 * each time, so nobody is stuck on one floor for ever. And a god measures what its challengers carry: the best
 * weapon in their packs, the enchantments on it, the armour they wear - a challenger who comes over-armed for the
 * floor meets a god that has risen to match. The world's difficulty sets the pace on top of all of it.
 */
public final class Adapt {
    public static final int MAX_CHALLENGERS = 4, MAX_MERCY = 3;
    /** A god never grows past this many times its own health because of gear, however well armed its challengers. */
    public static final double MAX_GEAR_HEALTH = 2.2, MAX_GEAR_DAMAGE = 1.6;
    private static final Map<String, Integer> LOSSES = new HashMap<>();

    private Adapt() {}

    /** Health multiplier for a god facing this many players: +60% for each one after the first. */
    public static double healthScale(int challengers) {
        return 1.0 + 0.6 * (Math.max(1, Math.min(MAX_CHALLENGERS, challengers)) - 1);
    }

    /** Damage multiplier for a god's moves and shots: +15% for each challenger after the first. */
    public static float damageScale(int challengers) {
        return 1.0F + 0.15F * (Math.max(1, Math.min(MAX_CHALLENGERS, challengers)) - 1);
    }

    /** Health multiplier after losses: 8% less for each time this god has beaten the player, up to three. */
    public static double mercyScale(int losses) {
        return 1.0 - 0.08 * Math.max(0, Math.min(MAX_MERCY, losses));
    }

    /** Extra guardians a sealed chamber wakes for a group: one for each player after the first, up to two. */
    public static int extraGuardians(int players) {
        return Math.max(0, Math.min(2, players - 1));
    }

    // ---- what the challengers carry ------------------------------------------------------------------------

    /**
     * What one challenger brings. @param offence the hardest-hitting weapon they carry, enchantments counted
     * @param defence their armour, its toughness and its enchantments @param ranged whether that best weapon
     * is one that fights from a distance
     */
    public record Gear(double offence, double defence, boolean ranged) {}

    /** How a trial was measured: the strongest challenger's gear against what the floor expects. */
    public record Measure(double might, double guard, boolean ranged) {
        public static final Measure NONE = new Measure(1.0, 1.0, false);
    }

    /** The sum of every enchantment level on a stack. */
    public static int enchantLevels(ItemStack stack) {
        int levels = 0;
        for (var entry : stack.getEnchantments().entrySet()) levels += entry.getIntValue();
        return levels;
    }

    /** How hard a stack hits as a weapon; 0 for something that is not one. */
    public static double offence(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        double melee = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
        double value = melee > 1.0 ? melee : 0.0;
        if (stack.getItem() instanceof GodWeaponItem relic) {
            // a relic hits as hard as it has been levelled, starred and tempered
            God god = relic.god();
            double m = WeaponProgress.multiplier(stack);
            value = Math.max(value * m, god.kind().ranged() ? god.baseDamage() * god.kind().rangedScale() * m : 0.0);
        } else if (isLauncher(stack)) {
            value = Math.max(value, 8.0);
        }
        return value <= 0.0 ? 0.0 : value + 0.6 * enchantLevels(stack);
    }

    private static boolean isLauncher(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem;
    }

    public static boolean isRanged(ItemStack stack) {
        return isLauncher(stack) || (stack.getItem() instanceof GodWeaponItem relic && relic.god().kind().ranged());
    }

    public static Gear gear(ServerPlayer player) {
        double best = 0.0;
        boolean ranged = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            double o = offence(stack);
            if (o > best) {
                best = o;
                ranged = isRanged(stack);
            }
        }
        double defence = player.getArmorValue() + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * 0.5;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            defence += 0.5 * enchantLevels(player.getItemBySlot(slot));
        }
        return new Gear(best, defence, ranged);
    }

    /** The weapon a challenger is expected to bring to a floor: an iron sword on floor 1, a well-forged relic at the summit. */
    public static double expectedOffence(int floor) {
        return 6.0 + 0.9 * (Tower.clampFloor(floor) - 1);
    }

    /** The armour a floor expects: a mixed set on floor 1, enchanted diamond at the summit. */
    public static double expectedDefence(int floor) {
        return 7.0 + Tower.clampFloor(floor);
    }

    /** Measures a group against a floor. The strongest challenger sets the bar, as the highest floor does for a party. */
    public static Measure measure(Collection<ServerPlayer> challengers, int floor) {
        double might = 0.0, guard = 0.0;
        boolean ranged = false;
        for (ServerPlayer player : challengers) {
            Gear gear = gear(player);
            double g = gear.defence() / expectedDefence(floor);
            double m = 0.65 * gear.offence() / expectedOffence(floor) + 0.35 * g;
            if (m > might) {
                might = m;
                ranged = gear.ranged();
            }
            guard = Math.max(guard, g);
        }
        return new Measure(might, guard, ranged);
    }

    /** Health multiplier from gear: nothing for gear at or under what the floor expects, then rising, capped. */
    public static double gearHealth(double might) {
        return Math.max(1.0, Math.min(MAX_GEAR_HEALTH, Math.pow(Math.max(0.0, might), 0.8)));
    }

    /** Damage multiplier from armour: a god strikes harder at challengers armoured past what the floor expects. */
    public static float gearDamage(double guard) {
        return (float) Math.max(1.0, Math.min(MAX_GEAR_DAMAGE, Math.sqrt(Math.max(0.0, guard))));
    }

    /** One word for how the challengers measured up, as a translation key suffix. */
    public static String mightKey(double might) {
        double scale = gearHealth(might);
        return scale <= 1.0 ? "even" : scale < 1.3 ? "armed" : scale < 1.7 ? "mighty" : "legendary";
    }

    // ---- the world's difficulty ----------------------------------------------------------------------------

    /** Health multiplier for the world's difficulty (the game itself already scales what a blow does to a player). */
    public static double difficultyHealth(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.85;
            case HARD -> 1.25;
            default -> 1.0;
        };
    }

    /** How long a god waits between moves, as a multiplier: longer on Easy, shorter on Hard. */
    public static double difficultyPace(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1.15;
            case HARD -> 0.85;
            default -> 1.0;
        };
    }

    /** How many shield pylons a god raises: two on Easy, three on Normal, four on Hard, one more for a group of three. */
    public static int pylons(Difficulty difficulty, int challengers, int tempo) {
        int base = switch (difficulty) {
            case EASY -> 2;
            case HARD -> 4;
            default -> 3;
        };
        return Math.max(2, Math.min(6, base + (challengers >= 3 ? 1 : 0) + tempo));
    }

    // ---- mercy ---------------------------------------------------------------------------------------------

    private static String key(UUID player, God god) {
        return player + "/" + god.id();
    }

    public static int losses(UUID player, God god) {
        return LOSSES.getOrDefault(key(player, god), 0);
    }

    public static void recordLoss(UUID player, God god) {
        LOSSES.merge(key(player, god), 1, Integer::sum);
    }

    public static void clearLosses(UUID player, God god) {
        LOSSES.remove(key(player, god));
    }
}
