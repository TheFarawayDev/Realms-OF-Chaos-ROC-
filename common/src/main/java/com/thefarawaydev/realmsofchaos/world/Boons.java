package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a broken seal gives. Whoever breaks a seal is offered three boons and takes one: a little more health, a
 * harder blow, quicker feet, a longer moment to parry in. Boons stack - the same one can be taken again - and last
 * for the passage: they are laid down when the player comes out on to the next floor, or leaves the realms.
 */
public final class Boons {
    public static final int CHOICES = 3, MAX_STACK = 4;

    public enum Boon {
        /** Two more hearts a stack. */
        VIGOUR,
        /** A tenth harder blow a stack. */
        EDGE,
        /** A twelfth quicker a stack. */
        SWIFT,
        /** Two points of armour a stack. */
        WARD,
        /** Three more ticks to parry in a stack. */
        KEEN,
        /** A heart back for every sentinel slain, a stack. */
        THIRST,
        /** One more of the tier's forge material from every cache, a stack. */
        FORTUNE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("boon." + RealmsOfChaos.MOD_ID + "." + id());
        }

        public Component description() {
            return Component.translatable("boon." + RealmsOfChaos.MOD_ID + "." + id() + ".lore");
        }
    }

    private Boons() {}

    private static String key(ServerPlayer player, Boon boon) {
        return "boon/" + player.getUUID() + "/" + boon.id();
    }

    public static int count(ServerPlayer player, Boon boon) {
        MinecraftServer server = player.level().getServer();
        return server == null ? 0 : Math.max(0, Math.min(MAX_STACK, ProgressData.get(server).counter(key(player, boon))));
    }

    public static int total(ServerPlayer player) {
        int n = 0;
        for (Boon boon : Boon.values()) n += count(player, boon);
        return n;
    }

    /** The three boons a seal offers this player: always three different ones, and none already at its limit. */
    public static List<Boon> offer(ServerPlayer player, BlockPos seal) {
        List<Boon> pool = new ArrayList<>();
        for (Boon boon : Boon.values()) if (count(player, boon) < MAX_STACK) pool.add(boon);
        Collections.shuffle(pool, new Random(seal.asLong() ^ player.getUUID().getLeastSignificantBits() ^ total(player) * 31L));
        return new ArrayList<>(pool.subList(0, Math.min(CHOICES, pool.size())));
    }

    /** @return false if the boon is already at its limit */
    public static boolean grant(ServerPlayer player, Boon boon) {
        if (count(player, boon) >= MAX_STACK) return false;
        ProgressData.get(player.level().getServer()).addCounter(key(player, boon), 1);
        apply(player);
        if (boon == Boon.VIGOUR) player.heal(4.0F);
        return true;
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        var id = RealmsOfChaos.id("boon_" + name);
        if (amount == 0.0) instance.removeModifier(id);
        else instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** Puts a player's boons on them. Safe to call as often as wanted. */
    public static void apply(ServerPlayer player) {
        if (player.level().getServer() == null) return;
        modifier(player, Attributes.MAX_HEALTH, "vigour", 4.0 * count(player, Boon.VIGOUR), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.ATTACK_DAMAGE, "edge", 0.10 * count(player, Boon.EDGE), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.MOVEMENT_SPEED, "swift", 0.08 * count(player, Boon.SWIFT), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.ARMOR, "ward", 2.0 * count(player, Boon.WARD), AttributeModifier.Operation.ADD_VALUE);
    }

    /** Lays every boon down: the passage is behind them. @return how many there were */
    public static int clear(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return 0;
        int had = total(player);
        if (had == 0) return 0;
        ProgressData.get(server).clearCounters("boon/" + player.getUUID() + "/");
        apply(player);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        return had;
    }

    /** A sentinel has fallen to this player. */
    public static void onKill(ServerPlayer player) {
        int thirst = count(player, Boon.THIRST);
        if (thirst > 0) player.heal(2.0F * thirst);
    }
}
