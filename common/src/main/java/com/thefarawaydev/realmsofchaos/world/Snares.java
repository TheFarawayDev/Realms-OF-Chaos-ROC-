package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.world.Passages.Cell;
import com.thefarawaydev.realmsofchaos.world.Passages.Kind;
import com.thefarawaydev.realmsofchaos.world.Passages.Plan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Three halls that never rest. A spike hall's floor is two colours of tile, and each colour's spikes come up in
 * turn. A flame hall's vents erupt a row at a time, the fire walking down the hall. A blade hall has a blade that
 * sweeps from wall to wall and back. None of them can be switched off: each is a matter of watching, and of going
 * when the way is clear. Every one shows what it is about to do before it does it.
 *
 * <p>They keep no state at all: what a hall is doing is worked out from the server's clock, and a hall is only
 * tended while somebody is in it.
 */
public final class Snares {
    /** Spikes: each colour is warned of, up, and down again once in this many ticks, the two colours half a turn apart. */
    public static final int SPIKE_PERIOD = 80, SPIKE_WARN = 15, SPIKE_UP = 25;
    /** Flames: three sets of rows, a third of a turn apart. */
    public static final int FLAME_PERIOD = 90, FLAME_WARN = 20, FLAME_BURN = 22;
    /** Blades: wall to wall and back in this many ticks. */
    public static final int BLADE_PERIOD = 120;
    public static final double BLADE_REACH = 0.7;
    /** The traps fill the middle of a hall and leave a step of plain floor inside each wall. */
    public static final int SPAN = 3;
    public static final Block SPIKE_TILE_A = Blocks.POLISHED_TUFF, SPIKE_TILE_B = Blocks.TUFF_BRICKS, VENT = Blocks.SMOOTH_BASALT;

    private static final Map<String, Long> TENDED = new HashMap<>();
    private static final Map<UUID, Long> CUT = new HashMap<>();

    private Snares() {}

    public static boolean is(Kind kind) {
        return kind == Kind.SPIKES || kind == Kind.FLAMES || kind == Kind.BLADES;
    }

    /** Which of a spike hall's two colours a tile is. */
    public static int spikeGroup(int dx, int dz) {
        return (dx + dz) & 1;
    }

    /** Which of a flame hall's three sets of rows a row is in. */
    public static int flameGroup(int dz) {
        return Math.floorMod(dz, 3);
    }

    public static float damage(Plan plan) {
        return (float) Math.min(8.0, 2.0 + plan.threat * 0.3);
    }

    public static void build(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p) {
        BlockPos m = plan.centre(c);
        Kind kind = plan.kind(c);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean inside = Math.abs(dx) <= SPAN && Math.abs(dz) <= SPAN;
                Block floor = switch (kind) {
                    case SPIKES -> inside ? (spikeGroup(dx, dz) == 0 ? SPIKE_TILE_A : SPIKE_TILE_B) : p.trim();
                    case FLAMES -> inside ? ((dx & 1) == 0 ? VENT : p.inlay()) : p.trim();
                    // a blade hall's floor is scored from wall to wall, the way the blade runs
                    default -> Math.abs(dz) == 4 ? p.trim() : (dx & 1) == 0 ? p.inlay() : p.top();
                };
                level.setBlock(m.offset(dx, 0, dz), floor.defaultBlockState(), 2);
                if (!level.getBlockState(m.offset(dx, 1, dz)).isAir()) level.setBlock(m.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // the hall says what it is from its corners: lights low in the walls, where the floor can be read by them
        for (int[] k : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) level.setBlock(m.offset(k[0], 0, k[1]), p.light().defaultBlockState(), 2);
    }

    private static String key(Plan plan, Cell c) {
        return plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + c.x() + "/" + c.z();
    }

    /** Every tick: the halls that have someone in them. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Passages.Spot spot = Passages.locate(player);
            if (spot == null || !(player.level() instanceof ServerLevel level)) continue;
            Plan plan = Passages.plan(level, spot);
            Cell cell = plan.cellAt(player.blockPosition());
            if (cell == null || !is(plan.kind(cell)) || !Passages.isBuilt(level, plan.line, plan.index, plan.stage)) continue;
            tend(level, plan, cell, now);
        }
        if (now % 200 == 0) {
            TENDED.values().removeIf(t -> now - t > 200);
            CUT.values().removeIf(t -> now - t > 200);
        }
    }

    /** One tick of one hall, as the clock stands at {@code now}. A hall is only tended once in any tick. */
    public static void tend(ServerLevel level, Plan plan, Cell cell, long now) {
        Long last = TENDED.put(key(plan, cell), now);
        if (last != null && last == now) return;
        switch (plan.kind(cell)) {
            case SPIKES -> spikes(level, plan, cell, now);
            case FLAMES -> flames(level, plan, cell, now);
            case BLADES -> blades(level, plan, cell, now);
            default -> { }
        }
    }

    private static java.util.List<Player> inside(ServerLevel level, Plan plan, Cell cell) {
        return level.getEntitiesOfClass(Player.class, plan.cellBox(cell), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    // ---- spikes ---------------------------------------------------------------------------------------------

    /** Where a colour is in its turn: 0 at the first warning. */
    public static int spikePhase(long now, int group) {
        return (int) Math.floorMod(now + group * (SPIKE_PERIOD / 2L), (long) SPIKE_PERIOD);
    }

    public static boolean spikesUp(long now, int group) {
        int phase = spikePhase(now, group);
        return phase >= SPIKE_WARN && phase < SPIKE_WARN + SPIKE_UP;
    }

    private static void spikes(ServerLevel level, Plan plan, Cell cell, long now) {
        BlockPos m = plan.centre(cell);
        for (int group = 0; group < 2; group++) {
            int phase = spikePhase(now, group);
            if (phase == 0) level.playSound(null, m, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.9F, 0.6F);
            boolean warn = phase < SPIKE_WARN && phase % 5 == 0, rise = phase == SPIKE_WARN, fall = phase == SPIKE_WARN + SPIKE_UP;
            if (!warn && !rise && !fall) continue;
            for (int dx = -SPAN; dx <= SPAN; dx++) {
                for (int dz = -SPAN; dz <= SPAN; dz++) {
                    if (spikeGroup(dx, dz) != group) continue;
                    BlockPos at = m.offset(dx, 1, dz);
                    if (warn) {
                        level.sendParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.0);
                    } else if (rise) {
                        if (level.getBlockState(at).isAir()) level.setBlock(at, Blocks.POINTED_DRIPSTONE.defaultBlockState(), 2);
                    } else if (level.getBlockState(at).is(Blocks.POINTED_DRIPSTONE)) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            if (rise) {
                level.playSound(null, m, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 1.0F, 0.7F);
                for (Player p : inside(level, plan, cell)) {
                    int dx = p.getBlockX() - m.getX(), dz = p.getBlockZ() - m.getZ();
                    if (Math.abs(dx) > SPAN || Math.abs(dz) > SPAN || spikeGroup(dx, dz) != group || p.getY() > m.getY() + 1.6) continue;
                    p.hurtServer(level, level.damageSources().stalagmite(), damage(plan));
                    // (thrown up onto the spikes rather than left standing inside them)
                    p.setDeltaMovement(p.getDeltaMovement().x, 0.5, p.getDeltaMovement().z);
                    p.needsSync = true;
                }
            }
        }
    }

    /** Puts every spike of a hall down: when the level is re-armed. */
    public static void lower(ServerLevel level, Plan plan, Cell cell) {
        BlockPos m = plan.centre(cell);
        for (int dx = -SPAN; dx <= SPAN; dx++) {
            for (int dz = -SPAN; dz <= SPAN; dz++) {
                if (level.getBlockState(m.offset(dx, 1, dz)).is(Blocks.POINTED_DRIPSTONE)) level.setBlock(m.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // ---- flames ---------------------------------------------------------------------------------------------

    public static int flamePhase(long now, int group) {
        return (int) Math.floorMod(now + group * (FLAME_PERIOD / 3L), (long) FLAME_PERIOD);
    }

    public static boolean burning(long now, int group) {
        int phase = flamePhase(now, group);
        return phase >= FLAME_WARN && phase < FLAME_WARN + FLAME_BURN;
    }

    private static void flames(ServerLevel level, Plan plan, Cell cell, long now) {
        BlockPos m = plan.centre(cell);
        for (int group = 0; group < 3; group++) {
            int phase = flamePhase(now, group);
            boolean warn = phase < FLAME_WARN, burn = burning(now, group);
            if (!warn && !burn) continue;
            if (phase == FLAME_WARN) level.playSound(null, m, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.7F);
            for (int dz = -SPAN; dz <= SPAN; dz++) {
                if (flameGroup(dz) != group) continue;
                for (int dx = -SPAN; dx <= SPAN; dx += 2) {
                    double x = m.getX() + dx + 0.5, y = m.getY() + 1.05, z = m.getZ() + dz + 0.5;
                    if (warn && phase % 4 == 0) level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.1, 0.05, 0.1, 0.01);
                    if (burn && phase % 2 == 0) level.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 3, 0.25, 0.5, 0.25, 0.02);
                }
            }
            if (burn && phase % 5 == 0) {
                for (Player p : inside(level, plan, cell)) {
                    int dx = p.getBlockX() - m.getX(), dz = p.getBlockZ() - m.getZ();
                    if (Math.abs(dx) > SPAN || Math.abs(dz) > SPAN || flameGroup(dz) != group || p.getY() > m.getY() + 2.6) continue;
                    p.hurtServer(level, level.damageSources().inFire(), damage(plan) * 0.6F);
                    p.setRemainingFireTicks(60);
                }
            }
        }
    }

    // ---- blades ---------------------------------------------------------------------------------------------

    /** Where the blade is across the hall, from -4 at the west wall to 4 at the east, at this moment. */
    public static double bladeAt(long now) {
        double t = Math.floorMod(now, (long) BLADE_PERIOD) / (BLADE_PERIOD / 2.0);       // 0..2
        return t <= 1.0 ? -4.0 + 8.0 * t : 4.0 - 8.0 * (t - 1.0);
    }

    private static void blades(ServerLevel level, Plan plan, Cell cell, long now) {
        BlockPos m = plan.centre(cell);
        double x = m.getX() + 0.5 + bladeAt(now), y = m.getY() + 1.0;
        if (now % 2 == 0) {
            for (int dz = -4; dz <= 4; dz += 2) level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + 0.8, m.getZ() + dz + 0.5, 1, 0, 0, 0, 0);
            for (int dz = -4; dz <= 4; dz++) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.1, m.getZ() + dz + 0.5, 1, 0.0, 0.0, 0.3, 0.0);
        }
        if (Math.floorMod(now, (long) (BLADE_PERIOD / 2)) == 0) level.playSound(null, m, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 1.0F, 0.6F);
        boolean eastward = Math.floorMod(now, (long) BLADE_PERIOD) < BLADE_PERIOD / 2;
        for (Player p : inside(level, plan, cell)) {
            if (Math.abs(p.getX() - x) > BLADE_REACH || Math.abs(p.getZ() - (m.getZ() + 0.5)) > 4.5 || p.getY() > m.getY() + 2.4) continue;
            Long cut = CUT.get(p.getUUID());
            if (cut != null && now - cut < 20 && now >= cut) continue;
            CUT.put(p.getUUID(), now);
            p.hurtServer(level, level.damageSources().magic(), damage(plan));
            p.setDeltaMovement(eastward ? 0.9 : -0.9, 0.35, p.getDeltaMovement().z);
            p.needsSync = true;
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    /** Forgets who was cut when: between tests. */
    public static void forget() {
        TENDED.clear();
        CUT.clear();
    }
}
