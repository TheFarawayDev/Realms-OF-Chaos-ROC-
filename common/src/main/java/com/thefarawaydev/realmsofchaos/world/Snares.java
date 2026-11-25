package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.world.Passages.Cell;
import com.thefarawaydev.realmsofchaos.world.Passages.Kind;
import com.thefarawaydev.realmsofchaos.world.Passages.Plan;
import java.util.HashMap;
import net.minecraft.world.level.block.state.BlockState;
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
    /** The two colours of a spike hall's floor plates, the vents of a flame hall and the corridors, the spikes that rise. */
    public static Block spikeTileA() { return com.thefarawaydev.realmsofchaos.ModContent.SPIKE_PLATE.get(); }
    public static Block spikeTileB() { return com.thefarawaydev.realmsofchaos.ModContent.SPIKE_PLATE_DARK.get(); }
    public static Block vent() { return com.thefarawaydev.realmsofchaos.ModContent.FLAME_VENT.get(); }
    public static Block spike() { return com.thefarawaydev.realmsofchaos.ModContent.TRAP_SPIKES.get(); }
    /** The arc: once round the hall in this many ticks, reaching this far from the pillar. */
    public static final int ARC_PERIOD = 160;
    public static final double ARC_REACH = 4.4, ARC_WIDTH = 0.6;
    /** Gas: each half of the hall is warned of, then foul, in turn - north, east, south, west. */
    public static final int GAS_TURN = 50, GAS_WARN = 15, GAS_PERIOD = GAS_TURN * 4;
    /** A corridor's jets: warned of, then burning, once in this many ticks. */
    public static final int JET_PERIOD = 70, JET_WARN = 20, JET_BURN = 18;
    public static Block gasVent() { return com.thefarawaydev.realmsofchaos.ModContent.GAS_VENT.get(); }

    private static final Map<String, Long> TENDED = new HashMap<>();
    private static final Map<UUID, Long> CUT = new HashMap<>();

    private Snares() {}

    public static boolean is(Kind kind) {
        return kind == Kind.SPIKES || kind == Kind.FLAMES || kind == Kind.BLADES || kind == Kind.ARC || kind == Kind.GAS;
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
                    case SPIKES -> inside ? (spikeGroup(dx, dz) == 0 ? spikeTileA() : spikeTileB()) : p.trim();
                    case FLAMES -> inside ? ((dx & 1) == 0 ? vent() : p.inlay()) : p.trim();
                    // an arc hall's floor is rings about its pillar; a gas hall's is quartered, a vent in the middle of each side
                    case ARC -> (int) Math.round(Math.hypot(dx, dz)) % 2 == 0 ? p.inlay() : p.top();
                    case GAS -> (Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0) ? gasVent() : dx == 0 || dz == 0 ? p.trim() : p.topAlt();
                    // a blade hall's floor is scored from wall to wall, the way the blade runs
                    default -> Math.abs(dz) == 4 ? p.trim() : (dx & 1) == 0 ? p.inlay() : p.top();
                };
                level.setBlock(m.offset(dx, 0, dz), floor.defaultBlockState(), 2);
                if (!level.getBlockState(m.offset(dx, 1, dz)).isAir()) level.setBlock(m.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        if (kind == Kind.ARC) {
            // the pillar the arc turns about
            level.setBlock(m.above(), p.pillar().defaultBlockState(), 2);
            level.setBlock(m.above(2), p.light().defaultBlockState(), 2);
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
            if (!Passages.isBuilt(level, plan.line, plan.index, plan.stage)) continue;
            if (cell == null) {
                // between rooms: the corridors near them, if any has jets
                for (Cell c : plan.cells()) {
                    for (int dir = 0; dir < 2; dir++) {
                        if (plan.jets(c, dir) && plan.corridorMiddle(c, dir).distSqr(player.blockPosition()) < 144) jets(level, plan, c, dir, now);
                        if (plan.pit(c, dir) && plan.corridorMiddle(c, dir).distSqr(player.blockPosition()) < 144) pit(level, plan, c, dir, now);
                    }
                }
                continue;
            }
            if (is(plan.kind(cell))) tend(level, plan, cell, now);
            else if (plan.kind(cell) == Kind.GAUNTLET) Gauntlets.tend(level, plan, cell, now);
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
            case ARC -> arc(level, plan, cell, now);
            case GAS -> gas(level, plan, cell, now);
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
                        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type(), at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.0);
                    } else if (rise) {
                        if (level.getBlockState(at).isAir()) level.setBlock(at, spike().defaultBlockState(), 2);
                    } else if (level.getBlockState(at).is(spike())) {
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
                if (level.getBlockState(m.offset(dx, 1, dz)).is(spike())) level.setBlock(m.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
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
                    if (warn && phase % 4 == 0) level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), x, y, z, 1, 0.1, 0.05, 0.1, 0.01);
                    if (burn && phase % 2 == 0) level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), x, y + 0.2, z, 3, 0.25, 0.5, 0.25, 0.02);
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
            for (int dz = -4; dz <= 4; dz++) level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type(), x, y + 0.1, m.getZ() + dz + 0.5, 1, 0.0, 0.0, 0.3, 0.0);
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

    // ---- the arc ------------------------------------------------------------------------------------------

    /** Which way the arc points at this moment, in radians: it and its opposite, a line through the pillar. */
    public static double arcAngle(long now) {
        return Math.PI * 2 * Math.floorMod(now, (long) ARC_PERIOD) / ARC_PERIOD;
    }

    /** How far a point (measured from the pillar) is from the arc's line, or a large number if it is beyond the arc's reach. */
    public static double arcDistance(long now, double dx, double dz) {
        double a = arcAngle(now), ux = Math.cos(a), uz = Math.sin(a);
        double along = dx * ux + dz * uz;
        return Math.abs(along) > ARC_REACH ? 99.0 : Math.abs(dx * uz - dz * ux);
    }

    private static void arc(ServerLevel level, Plan plan, Cell cell, long now) {
        BlockPos m = plan.centre(cell);
        double cx = m.getX() + 0.5, cz = m.getZ() + 0.5, a = arcAngle(now);
        if (now % 2 == 0) {
            for (double r = 0.8; r <= ARC_REACH; r += 0.6) {
                for (int side = -1; side <= 1; side += 2) {
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type(), cx + Math.cos(a) * r * side, m.getY() + 1.2, cz + Math.sin(a) * r * side, 1, 0.0, 0.25, 0.0, 0.0);
                }
            }
        }
        if (Math.floorMod(now, (long) ARC_PERIOD) == 0) level.playSound(null, m, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.8F);
        for (Player p : inside(level, plan, cell)) {
            if (arcDistance(now, p.getX() - cx, p.getZ() - cz) > ARC_WIDTH || p.getY() > m.getY() + 2.6) continue;
            Long cut = CUT.get(p.getUUID());
            if (cut != null && now - cut < 15 && now >= cut) continue;
            CUT.put(p.getUUID(), now);
            p.hurtServer(level, level.damageSources().lightningBolt(), damage(plan));
            level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }

    // ---- gas ----------------------------------------------------------------------------------------------

    /** Whether a place in a gas hall is in a half (0 north, 1 east, 2 south, 3 west). The middle lines belong to both sides: nowhere is always safe. */
    public static boolean inHalf(int half, int dx, int dz) {
        return switch (half) {
            case 0 -> dz <= 0;
            case 1 -> dx >= 0;
            case 2 -> dz >= 0;
            default -> dx <= 0;
        };
    }

    /** The half whose turn it is, and whether the gas is out yet or only being warned of. */
    public static int gasHalf(long now) {
        return (int) (Math.floorMod(now, (long) GAS_PERIOD) / GAS_TURN);
    }

    public static boolean gasOut(long now) {
        return Math.floorMod(now, (long) GAS_TURN) >= GAS_WARN;
    }

    private static void gas(ServerLevel level, Plan plan, Cell cell, long now) {
        BlockPos m = plan.centre(cell);
        int half = gasHalf(now), phase = (int) Math.floorMod(now, (long) GAS_TURN);
        boolean out = gasOut(now);
        if (phase == 0) level.playSound(null, m, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.6F);
        if (now % (out ? 3 : 6) == 0) {
            for (int dx = -4; dx <= 4; dx += 2) {
                for (int dz = -4; dz <= 4; dz += 2) {
                    if (!inHalf(half, dx, dz)) continue;
                    level.sendParticles(out ? ParticleTypes.SNEEZE : com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), m.getX() + dx + 0.5, m.getY() + (out ? 1.4 : 1.1), m.getZ() + dz + 0.5, out ? 2 : 1, 0.5, 0.3, 0.5, 0.01);
                }
            }
        }
        if (!out || phase % 10 != 5) return;
        for (Player p : inside(level, plan, cell)) {
            int dx = p.getBlockX() - m.getX(), dz = p.getBlockZ() - m.getZ();
            if (!inHalf(half, dx, dz)) continue;
            p.hurtServer(level, level.damageSources().magic(), damage(plan) * 0.4F);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 60, 0));
        }
    }

    // ---- corridor pits -------------------------------------------------------------------------------------

    /** How long a pit's lids creak before they drop, and how long they hang open. */
    public static final int PIT_WARN = 10, PIT_HOLD = 60;
    private static final Map<String, Long> PIT_SPRING = new HashMap<>(), PIT_OPEN = new HashMap<>();

    private static java.util.List<BlockPos> lids(Plan plan, Cell c, int dir) {
        BlockPos mid = plan.corridorMiddle(c, dir);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) out.add(mid.offset(a, 0, b));
        return out;
    }

    private static BlockState lid(Plan plan, boolean open) {
        return Passages.trapdoor(plan.line).defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, open);
    }

    /** Digs a corridor's pit: its floor bronze lids over a drop with iron spikes in it. */
    public static void layPit(ServerLevel level, Plan plan, Cell c, int dir) {
        for (BlockPos at : lids(plan, c, dir)) {
            level.setBlock(at, lid(plan, false), 2);
            level.setBlock(at.below(), spike().defaultBlockState(), 2);
        }
    }

    private static void setLids(ServerLevel level, Plan plan, Cell c, int dir, boolean open) {
        for (BlockPos at : lids(plan, c, dir)) if (level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) level.setBlock(at, lid(plan, open), 2);
    }

    /** One tick of the pit in the corridor east (dir 0) or south (dir 1) of a room. */
    public static void pit(ServerLevel level, Plan plan, Cell c, int dir, long now) {
        String key = key(plan, c) + "/pit" + dir;
        Long last = TENDED.put(key, now);
        if (last != null && last == now) return;
        BlockPos mid = plan.corridorMiddle(c, dir);
        net.minecraft.world.phys.AABB over = new net.minecraft.world.phys.AABB(mid.getX() - 1, mid.getY() - 1, mid.getZ() - 1, mid.getX() + 2, mid.getY() + 3, mid.getZ() + 2);
        java.util.List<Player> inside = level.getEntitiesOfClass(Player.class, over, e -> e.isAlive() && !e.isSpectator() && !e.isCreative());
        Long until = PIT_OPEN.get(key);
        if (until != null) {
            // whoever went down is on the spikes
            if (now % 10 == 0) {
                for (Player p : inside) {
                    if (p.getY() >= mid.getY() + 0.5) continue;
                    p.hurtServer(level, level.damageSources().stalagmite(), damage(plan) * 0.5F);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 2));
                }
            }
            if (now >= until) {
                // the lids swing shut: anybody still down there is heaved up onto them first
                for (Player p : inside) if (p.getY() < mid.getY() + 0.9) p.teleportTo(p.getX(), mid.getY() + 1.05, p.getZ());
                setLids(level, plan, c, dir, false);
                level.playSound(null, mid, SoundEvents.COPPER_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
                PIT_OPEN.remove(key);
            }
            return;
        }
        Long spring = PIT_SPRING.get(key);
        if (spring == null) {
            boolean stood = false;
            for (Player p : inside) stood |= p.getY() >= mid.getY() + 0.9 && p.getY() < mid.getY() + 1.3;
            if (stood) {
                PIT_SPRING.put(key, now + PIT_WARN);
                level.playSound(null, mid, SoundEvents.COPPER_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.5F);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type(), mid.getX() + 0.5, mid.getY() + 1.05, mid.getZ() + 0.5, 12, 1.0, 0.0, 1.0, 0.0);
            } else if (inside.isEmpty() && level.getBlockState(mid).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && level.getBlockState(mid).getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
                setLids(level, plan, c, dir, false);                 // (left open by a server that stopped mid-spring)
            }
        } else if (now >= spring) {
            setLids(level, plan, c, dir, true);
            level.playSound(null, mid, SoundEvents.COPPER_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.7F);
            PIT_OPEN.put(key, now + PIT_HOLD);
            PIT_SPRING.remove(key);
        }
    }

    /** Forgets which pits are springing: between tests. */
    public static void forgetPits() {
        PIT_SPRING.clear();
        PIT_OPEN.clear();
    }

    // ---- corridor jets -------------------------------------------------------------------------------------

    public static boolean jetsBurning(long now) {
        int phase = (int) Math.floorMod(now, (long) JET_PERIOD);
        return phase >= JET_WARN && phase < JET_WARN + JET_BURN;
    }

    /** One tick of the jets in the corridor east (dir 0) or south (dir 1) of a room. */
    public static void jets(ServerLevel level, Plan plan, Cell c, int dir, long now) {
        Long last = TENDED.put(key(plan, c) + "/jets" + dir, now);
        if (last != null && last == now) return;
        BlockPos mid = plan.corridorMiddle(c, dir);
        int phase = (int) Math.floorMod(now, (long) JET_PERIOD);
        boolean burn = jetsBurning(now);
        if (phase == JET_WARN) level.playSound(null, mid, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.8F);
        int kind = plan.jetKind(c, dir);
        var wisp = kind == Passages.JET_FROST ? com.thefarawaydev.realmsofchaos.fx.RocParticles.FROST.type() : kind == Passages.JET_SHOCK ? com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type() : kind == Passages.JET_VENOM ? ParticleTypes.ITEM_SLIME : com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type();
        var burst = kind == Passages.JET_FROST ? com.thefarawaydev.realmsofchaos.fx.RocParticles.FROST.type() : kind == Passages.JET_SHOCK ? com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type() : kind == Passages.JET_VENOM ? ParticleTypes.SNEEZE : com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type();
        for (int k = -1; k <= 1; k++) {
            BlockPos vent = dir == 0 ? mid.offset(0, 0, k) : mid.offset(k, 0, 0);
            double x = vent.getX() + 0.5, y = vent.getY() + 1.05, z = vent.getZ() + 0.5;
            if (phase < JET_WARN && phase % 4 == 0) level.sendParticles(wisp, x, y, z, 1, 0.1, 0.05, 0.1, 0.01);
            if (burn && phase % 2 == 0) level.sendParticles(burst, x, y + 0.3, z, 3, 0.2, 0.6, 0.2, 0.02);
        }
        if (!burn || phase % 5 != 0) return;
        net.minecraft.world.phys.AABB reach = new net.minecraft.world.phys.AABB(mid).inflate(dir == 0 ? 0.0 : 1.0, 0.0, dir == 0 ? 1.0 : 0.0).expandTowards(0, 2.6, 0);
        for (Player p : level.getEntitiesOfClass(Player.class, reach, e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
            switch (kind) {
                case Passages.JET_FROST -> {
                    p.hurtServer(level, level.damageSources().freeze(), damage(plan) * 0.5F);
                    p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 100));
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1));
                }
                case Passages.JET_SHOCK -> p.hurtServer(level, level.damageSources().lightningBolt(), damage(plan) * 0.8F);
                case Passages.JET_VENOM -> {
                    p.hurtServer(level, level.damageSources().magic(), damage(plan) * 0.4F);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 80, 0));
                }
                default -> {
                    p.hurtServer(level, level.damageSources().inFire(), damage(plan) * 0.6F);
                    p.setRemainingFireTicks(60);
                }
            }
        }
    }

    /** Forgets who was cut when: between tests. */
    public static void forget() {
        TENDED.clear();
        CUT.clear();
    }
}
