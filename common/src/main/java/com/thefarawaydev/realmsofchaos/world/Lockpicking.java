package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.CacheBlock;
import com.thefarawaydev.realmsofchaos.fx.RocParticles;
import com.thefarawaydev.realmsofchaos.ui.BlockLabel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Picking a locked cache. With a lockpick in hand, right-click the cache and a panel opens over it: a bar with a bright
 * mark sweeping to and fro across it, and a gap in the lock - the sweet spot. Right-click again while the mark is in the
 * gap and that pin is set; three pins (four in the higher towers) and the lock gives. Miss, and the pins all drop - and
 * the pick may snap. Walk away and the attempt is over.
 */
public final class Lockpicking {
    /** How wide the bar is, how long the mark takes to cross it, and how far a player may stray. */
    public static final int WIDTH = 21, SWEEP = 22, REACH2 = 25, TIMEOUT = 20 * 40;
    public static final float SNAP_CHANCE = 0.3F;

    public static final class Attempt {
        final ServerLevel level;
        final BlockPos pos;
        final int pins;
        int set, gap, gapWidth;
        long started, lastPress;
        final java.util.Random random;

        Attempt(ServerLevel level, BlockPos pos, int pins, long now, long seed) {
            this.level = level;
            this.pos = pos.immutable();
            this.pins = pins;
            this.started = now;
            this.random = new java.util.Random(seed);
            newGap();
        }

        void newGap() {
            gapWidth = Math.max(2, 5 - set);
            gap = 2 + random.nextInt(WIDTH - gapWidth - 3);
        }

        /** Where the mark is now: 0 to WIDTH - 1, there and back. */
        int mark(long now) {
            long t = (now - started) % (2L * SWEEP);
            double f = t < SWEEP ? t / (double) SWEEP : 2.0 - t / (double) SWEEP;
            return (int) Math.round(f * (WIDTH - 1));
        }

        boolean inGap(int mark) {
            return mark >= gap && mark < gap + gapWidth;
        }

        public int set() {
            return set;
        }

        public int pins() {
            return pins;
        }

        public int gap() {
            return gap;
        }

        public int gapWidth() {
            return gapWidth;
        }
    }

    private static final Map<UUID, Attempt> ATTEMPTS = new HashMap<>();

    private Lockpicking() {}

    public static Attempt attempt(ServerPlayer player) {
        return ATTEMPTS.get(player.getUUID());
    }

    public static boolean holdsPick(ServerPlayer player) {
        return player.getMainHandItem().is(ModContent.LOCKPICK.get()) || player.getOffhandItem().is(ModContent.LOCKPICK.get());
    }

    private static ItemStack pick(ServerPlayer player) {
        return player.getMainHandItem().is(ModContent.LOCKPICK.get()) ? player.getMainHandItem() : player.getOffhandItem();
    }

    /** How many pins a cache's lock has: more the higher its tower. */
    public static int pins(ServerLevel level, BlockPos pos) {
        Passages.Plan plan = Passages.planAt(level, pos);
        return plan == null ? 3 : 2 + plan.tier;
    }

    /**
     * A player right-clicks a locked cache with a lockpick: the first click begins; every click after is a try at the
     * pin. @return true once the lock has given
     */
    public static boolean press(ServerLevel level, BlockPos pos, ServerPlayer player) {
        long now = level.getServer().getTickCount();
        Attempt a = ATTEMPTS.get(player.getUUID());
        if (a == null || !a.pos.equals(pos) || a.level != level) {
            a = new Attempt(level, pos, pins(level, pos), now, now * 31 + pos.asLong() + player.getUUID().getLeastSignificantBits());
            ATTEMPTS.put(player.getUUID(), a);
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.7F, 1.6F);
            draw(a, now, null);
            return false;
        }
        if (now - a.lastPress < 4) return false;                       // (a held button is one try, not many)
        a.lastPress = now;
        int mark = a.mark(now);
        if (a.inGap(mark)) {
            a.set++;
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.6F + 0.1F * a.set);
            level.sendParticles(RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.0);
            if (a.set >= a.pins) {
                ATTEMPTS.remove(player.getUUID());
                unlock(level, pos);
                BlockLabel.show(level, pos, Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".lockpick.open"), 0x55FF55);
                return true;
            }
            a.newGap();
            draw(a, now, true);
            return false;
        }
        // a miss: the pins drop, and the pick may go
        a.set = 0;
        a.newGap();
        boolean snapped = !player.isCreative() && a.random.nextFloat() < SNAP_CHANCE;
        if (snapped) {
            pick(player).shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
        } else {
            level.playSound(null, pos, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        draw(a, now, false);
        if (snapped) BlockLabel.show(level, pos, Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".lockpick.snapped"), 0xFF5555);
        if (snapped && !holdsPick(player)) ATTEMPTS.remove(player.getUUID());
        return false;
    }

    /** The lock gives: the cache is unlocked for everyone. */
    public static void unlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CacheBlock)) return;
        level.setBlock(pos, state.setValue(CacheBlock.LOCKED, false), 3);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.5F);
        level.sendParticles(RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.02);
    }

    /** The panel over the lock: the pins set so far, and the bar with its gap and the sweeping mark. */
    private static void draw(Attempt a, long now, Boolean last) {
        int mark = a.mark(now);
        MutableComponent bar = Component.literal("");
        for (int i = 0; i < WIDTH; i++) {
            boolean gap = i >= a.gap && i < a.gap + a.gapWidth;
            String ch = i == mark ? "┃" : gap ? "▬" : "─";
            int colour = i == mark ? (a.inGap(mark) ? 0x7CFF7C : 0xFFFFFF) : gap ? 0xF5B82E : 0x6A6470;
            bar.append(Component.literal(ch).withStyle(Style.EMPTY.withColor(colour)));
        }
        MutableComponent pins = Component.literal("");
        for (int i = 0; i < a.pins; i++) pins.append(Component.literal(i < a.set ? "● " : "○ ").withStyle(Style.EMPTY.withColor(i < a.set ? 0xF5B82E : 0x8A8490)));
        MutableComponent text = Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".lockpick.title").withStyle(Style.EMPTY.withColor(0xE8DCC0));
        text.append("\n").append(pins).append("\n").append(bar);
        if (last != null) text.append("\n").append(Component.translatable("hud." + RealmsOfChaos.MOD_ID + (last ? ".lockpick.set" : ".lockpick.miss"))
                .withStyle(Style.EMPTY.withColor(last ? 0x7CFF7C : 0xFF6A6A)));
        BlockLabel.show(a.level, a.pos, text, 0xC9A35A, 30);
    }

    /** Keeps every attempt's panel moving, and ends those whose player has gone. Run every tick. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        ATTEMPTS.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            boolean gone = p == null || p.level() != a.level || p.distanceToSqr(a.pos.getX() + 0.5, a.pos.getY() + 0.5, a.pos.getZ() + 0.5) > REACH2
                    || !holdsPick(p) || now - a.started > TIMEOUT || !(a.level.getBlockState(a.pos).getBlock() instanceof CacheBlock)
                    || !a.level.getBlockState(a.pos).getValue(CacheBlock.LOCKED);
            if (gone) return true;
            if (now % 2 == 0) draw(a, now, null);
            return false;
        });
    }

    /** Begins an attempt for a test, as if the player had right-clicked. */
    public static Attempt begin(ServerLevel level, BlockPos pos, ServerPlayer player) {
        press(level, pos, player);
        return ATTEMPTS.get(player.getUUID());
    }

    /** Tries a pin as if the mark were at {@code mark}. For tests: the server's clock is not a player's hand. */
    public static boolean tryAt(ServerLevel level, BlockPos pos, ServerPlayer player, boolean inGap) {
        Attempt a = ATTEMPTS.get(player.getUUID());
        if (a == null) return false;
        long now = level.getServer().getTickCount();
        // set the sweep so the mark stands where asked
        int target = inGap ? a.gap : (a.gap > 3 ? 0 : WIDTH - 1);
        a.started = now - Math.round(target / (double) (WIDTH - 1) * SWEEP);
        a.lastPress = now - 10;
        return press(level, pos, player);
    }
}
