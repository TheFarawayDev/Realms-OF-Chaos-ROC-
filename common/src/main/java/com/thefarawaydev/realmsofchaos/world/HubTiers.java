package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Hub of Realms grows. It has five tiers, shared by everyone on the world. At the Heart of the Hub players
 * hand over what the next tier asks for - anyone can add to the pile - and when the pile is complete the hub rises
 * a tier for all of them. Each tier does something: a steadier compass, a sanctuary that mends whoever stands in
 * it, a bigger Field Bag, quicker and finally free waygates.
 */
public final class HubTiers {
    public static final int MAX = 5;
    private static final String TIER = "hub/tier", DEPOSIT = "hub/deposit/";

    /** One thing a tier asks for. */
    public record Cost(String id, int count, ItemStack icon, Predicate<ItemStack> matches) {
        public Component name() {
            return Component.translatable("hubtier." + RealmsOfChaos.MOD_ID + ".cost." + id);
        }
    }

    private HubTiers() {}

    /** 1 to 5. */
    public static int tier(MinecraftServer server) {
        return Math.max(1, Math.min(MAX, 1 + ProgressData.get(server).counter(TIER)));
    }

    public static Component tierName(int tier) {
        return Component.translatable("hubtier." + RealmsOfChaos.MOD_ID + "." + tier);
    }

    public static Component perk(int tier) {
        return Component.translatable("hubtier." + RealmsOfChaos.MOD_ID + "." + tier + ".perk");
    }

    private static Cost plain(String id, int count, Item item) {
        return new Cost(id, count, new ItemStack(item), stack -> stack.is(item));
    }

    /** A forge material of this tower tier, from any of the three towers. */
    private static Cost material(int tier, int count) {
        return new Cost("material_" + tier, count, new ItemStack(ModContent.materialItem(Line.ZEUS, tier)), stack -> {
            for (Line line : Line.values()) if (stack.is(ModContent.materialItem(line, tier))) return true;
            return false;
        });
    }

    /** What it takes to rise from {@code tier} to the next; empty at the top. */
    public static List<Cost> costs(int tier) {
        return switch (tier) {
            case 1 -> List.of(plain("iron", 16, Items.IRON_INGOT), material(1, 8));
            case 2 -> List.of(plain("gold", 16, Items.GOLD_INGOT), plain("amethyst", 12, Items.AMETHYST_SHARD), material(2, 8));
            case 3 -> List.of(plain("diamond", 8, Items.DIAMOND), plain("pearl", 6, Items.ENDER_PEARL), material(3, 8));
            case 4 -> List.of(plain("netherite", 2, Items.NETHERITE_INGOT), material(4, 3));
            default -> List.of();
        };
    }

    public static int deposited(MinecraftServer server, Cost cost) {
        return Math.min(cost.count(), ProgressData.get(server).counter(DEPOSIT + cost.id()));
    }

    public static boolean complete(MinecraftServer server) {
        int tier = tier(server);
        if (tier >= MAX) return false;
        for (Cost cost : costs(tier)) if (deposited(server, cost) < cost.count()) return false;
        return true;
    }

    /**
     * Hands over everything in the player's pack that the next tier still needs. If that completes the pile the
     * hub rises. @return how many items were taken.
     */
    public static int deposit(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return 0;
        ProgressData data = ProgressData.get(server);
        int tier = tier(server), taken = 0;
        if (tier >= MAX) return 0;
        Inventory inventory = player.getInventory();
        for (Cost cost : costs(tier)) {
            int need = cost.count() - deposited(server, cost);
            for (int i = 0; i < inventory.getContainerSize() && need > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !cost.matches().test(stack)) continue;
                int n = Math.min(need, stack.getCount());
                stack.shrink(n);
                need -= n;
                taken += n;
                data.addCounter(DEPOSIT + cost.id(), n);
            }
        }
        if (taken > 0) player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (complete(server)) rise(server);
        return taken;
    }

    /** The hub goes up a tier: the pile is spent, and everyone on the world is told. */
    public static int rise(MinecraftServer server) {
        ProgressData data = ProgressData.get(server);
        int tier = tier(server);
        if (tier >= MAX) return tier;
        data.clearCounters(DEPOSIT);
        data.setCounter(TIER, tier);            // the counter holds "tiers above the first"
        int now = tier + 1;
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".";
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Hud.title(player, Hud.colored(Component.translatable(hud + "hubtier", tierName(now)), 0xA55AD6), Hud.colored(perk(now), 0xF5B82E), 10, 100, 20);
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        return now;
    }

    /** Test seam and admin tool. */
    public static void setTier(MinecraftServer server, int tier) {
        ProgressData data = ProgressData.get(server);
        data.clearCounters(DEPOSIT);
        data.setCounter(TIER, Math.max(1, Math.min(MAX, tier)) - 1);
    }

    // ---- what the tiers do -----------------------------------------------------------------------------------

    /** How much steadier every compass is: +0.05 of signal for each tier above the first. */
    public static double calibration(MinecraftServer server) {
        return 0.05 * (tier(server) - 1);
    }

    /** How many rows a Field Bag has: three, or six from tier III. */
    public static int bagRows(MinecraftServer server) {
        return tier(server) >= 3 ? 6 : 3;
    }

    /** How long a waygate has to be channelled: five seconds, or three from tier IV. */
    public static int channelTicks(MinecraftServer server) {
        return tier(server) >= 4 ? 60 : 100;
    }

    /** From tier V the waygates ask for nothing. */
    public static boolean waygatesFree(MinecraftServer server) {
        return tier(server) >= MAX;
    }

    /**
     * Sanctuary, once a second for everyone standing in the hub: from tier II it mends them, from tier III it also
     * lifts whatever harm they carried in.
     */
    public static void sanctuary(ServerPlayer player, ServerLevel hub) {
        int tier = tier(hub.getServer());
        if (tier < 2 || !player.isAlive() || player.isSpectator()) return;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, tier >= 4 ? 1 : 0, true, false, true));
        if (tier >= 3) {
            List<net.minecraft.core.Holder<MobEffect>> harm = new ArrayList<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                if (!effect.getEffect().value().isBeneficial()) harm.add(effect.getEffect());
            }
            for (var effect : harm) player.removeEffect(effect);
            player.clearFire();
        }
    }
}
