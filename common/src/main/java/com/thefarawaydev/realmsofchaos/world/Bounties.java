package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The Bounty Board in the hub. Every day (of the world's own days) it posts three bounties, the same three for
 * everyone: fell so many sentinels, break so many seals, bring down a god, shatter shield pylons, open caches.
 * Each player works on them for themselves, and a finished bounty is claimed at the board for forge materials.
 * The board opens with the hub's second tier.
 */
public final class Bounties {
    public static final int PER_DAY = 3, NEEDS_TIER = 2;

    public enum Kind {
        SENTINELS(12), SEALS(4), GODS(1), PYLONS(4), CACHES(3);

        /** How many it asks for at the least; some days it asks for more. */
        public final int base;

        Kind(int base) {
            this.base = base;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One posted bounty: what to do, how often, and what it pays. */
    public record Bounty(int index, Kind kind, int goal, Line line, int tier, int reward) {
        public Component text() {
            return Component.translatable("bounty." + RealmsOfChaos.MOD_ID + "." + kind.id(), goal);
        }

        public ItemStack payment() {
            return new ItemStack(ModContent.materialItem(line, tier), reward);
        }
    }

    private Bounties() {}

    public static long day(MinecraftServer server) {
        return server.overworld().getGameTime() / 24000L;
    }

    /** Today's three bounties, each of a different kind. */
    public static List<Bounty> today(MinecraftServer server) {
        return on(server.overworld().getSeed(), day(server));
    }

    public static List<Bounty> on(long seed, long day) {
        Random random = new Random(seed * 31 + day * 0x9E3779B97F4A7C15L);
        List<Kind> kinds = new ArrayList<>(List.of(Kind.values()));
        List<Bounty> out = new ArrayList<>();
        for (int i = 0; i < PER_DAY; i++) {
            Kind kind = kinds.remove(random.nextInt(kinds.size()));
            int size = random.nextInt(3);                                   // 0 = small, 2 = large
            int goal = kind.base + (kind.base * size + 1) / 2;
            Line line = Line.values()[random.nextInt(Line.values().length)];
            out.add(new Bounty(i, kind, goal, line, 1 + size, 2 + size));
        }
        return out;
    }

    private static String progressKey(ServerPlayer player, long day, int index) {
        return "bounty/" + day + "/" + player.getUUID() + "/" + index;
    }

    private static String claimKey(ServerPlayer player, long day, int index) {
        return "bountyclaim/" + day + "/" + player.getUUID() + "/" + index;
    }

    public static int progress(ServerPlayer player, Bounty bounty) {
        MinecraftServer server = player.level().getServer();
        return Math.min(bounty.goal(), ProgressData.get(server).counter(progressKey(player, day(server), bounty.index())));
    }

    public static boolean claimed(ServerPlayer player, Bounty bounty) {
        MinecraftServer server = player.level().getServer();
        return ProgressData.get(server).isBuilt(claimKey(player, day(server), bounty.index()));
    }

    public static boolean open(MinecraftServer server) {
        return HubTiers.tier(server) >= NEEDS_TIER;
    }

    /** Something a bounty might ask for has just been done by this player. */
    public static void count(ServerPlayer player, Kind kind, int amount) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !open(server)) return;
        ProgressData data = ProgressData.get(server);
        long day = day(server);
        forgetOldDays(data, day);
        for (Bounty bounty : today(server)) {
            if (bounty.kind() != kind) continue;
            String key = progressKey(player, day, bounty.index());
            int before = data.counter(key);
            if (before >= bounty.goal()) continue;
            int now = Math.min(bounty.goal(), before + amount);
            data.setCounter(key, now);
            if (now >= bounty.goal()) {
                Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".bounty.done", bounty.text()), 0x55FF55));
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }
    }

    private static long lastDay = -1;

    /** Yesterday's tallies are of no use to anyone. */
    private static void forgetOldDays(ProgressData data, long day) {
        if (day == lastDay) return;
        lastDay = day;
        for (long old = Math.max(0, day - 3); old < day; old++) {
            data.clearCounters("bounty/" + old + "/");
            data.unmarkPrefix("bountyclaim/" + old + "/");
        }
    }

    /** Pays out a finished bounty. @return what was paid, or an empty stack if there was nothing to claim. */
    public static ItemStack claim(ServerPlayer player, int index) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !open(server)) return ItemStack.EMPTY;
        List<Bounty> posted = today(server);
        if (index < 0 || index >= posted.size()) return ItemStack.EMPTY;
        Bounty bounty = posted.get(index);
        if (claimed(player, bounty) || progress(player, bounty) < bounty.goal()) return ItemStack.EMPTY;
        ProgressData.get(server).markBuilt(claimKey(player, day(server), index));
        ItemStack paid = bounty.payment();
        ItemStack given = paid.copy();
        if (!player.getInventory().add(given) && !given.isEmpty()) player.drop(given, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
        return paid;
    }
}
