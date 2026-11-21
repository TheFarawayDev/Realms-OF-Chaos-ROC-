package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a tower floor does while its god fights. In a god's wrath the floor is lost from the wall inward: ring by
 * ring the paving turns to something nobody can stand on - charged stone under Zeus, black ice under Poseidon,
 * magma under Hades. Every block that is changed is remembered and put back when the fight ends, and a floor left
 * that way by a server stop is rebuilt the next time anyone sets foot on it.
 */
public final class Arena {
    /** The ring of the floor that is never lost: the fight always keeps this much ground. */
    public static final int MIN_SAFE = 9;

    private Arena() {}

    /** What the floor turns into under each line. */
    public static Block hazard(Line line) {
        return switch (line) {
            case ZEUS -> Blocks.OCHRE_FROGLIGHT;
            case POSEIDON -> Blocks.BLUE_ICE;
            case HADES -> Blocks.MAGMA_BLOCK;
        };
    }

    public static ParticleOptions hazardParticle(Line line) {
        return switch (line) {
            case ZEUS -> ParticleTypes.ELECTRIC_SPARK;
            case POSEIDON -> ParticleTypes.SNOWFLAKE;
            case HADES -> ParticleTypes.FLAME;
        };
    }

    public static String scorchKey(Line line, int floor) {
        return "scorched/" + TowerBuilder.floorKey(line, floor);
    }

    /** Parts of a floor that are never touched: the altar's dais and the two gates. */
    private static boolean kept(int x, int z) {
        BlockPos altar = Tower.altarPos(1);
        int ax = x - altar.getX(), az = z - altar.getZ();
        return ax * ax + az * az <= 8 || (Math.abs(x) <= 3 && Math.abs(z) >= 17);
    }

    /** The paving block of a column as the floor stands right now. */
    private static BlockPos top(Line line, int floor, int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        int h = r < Tower.FIGHT_RADIUS + 0.5 ? TowerBuilder.heights(floor, FloorShifts.position(line, floor)).at(x, z) : 0;
        return new BlockPos(x, Tower.floorY(floor) + h, z);
    }

    /**
     * Turns the paving between two radii into the line's hazard. Only plain, solid paving with open air above it
     * changes; whatever stood there is written into {@code record}. @return how many blocks changed.
     */
    public static int scorch(ServerLevel level, Line line, int floor, double inner, double outer, Map<BlockPos, BlockState> record) {
        BlockState hazard = hazard(line).defaultBlockState();
        int ri = (int) Math.ceil(outer) + 1, changed = 0;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < inner || r >= outer || r >= Tower.WALL_INNER || kept(x, z)) continue;
                BlockPos pos = top(line, floor, x, z);
                BlockState state = level.getBlockState(pos);
                if (state.is(hazard.getBlock()) || state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || !state.isCollisionShapeFullBlock(level, pos) || !level.getBlockState(pos.above()).isAir()) continue;
                record.putIfAbsent(pos, state);
                level.setBlock(pos, hazard, 2);
                changed++;
            }
        }
        if (changed > 0) ProgressData.get(level.getServer()).markBuilt(scorchKey(line, floor));
        return changed;
    }

    /** Puts back everything {@link #scorch} changed. */
    public static void restore(ServerLevel level, Line line, int floor, Map<BlockPos, BlockState> record) {
        Block hazard = hazard(line);
        TowerBuilder.HeightFn heights = TowerBuilder.heights(floor, FloorShifts.position(line, floor));
        int y0 = Tower.floorY(floor);
        for (Map.Entry<BlockPos, BlockState> entry : record.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.getBlockState(pos).is(hazard)) continue;         // the floor has shifted since and repainted it
            double r = Math.sqrt(pos.getX() * pos.getX() + pos.getZ() * pos.getZ());
            boolean terrain = r < Tower.FIGHT_RADIUS + 0.5 && pos.getY() == y0 + heights.at(pos.getX(), pos.getZ());
            // inside the fight ring the paving follows the floor's current shape; outside it nothing ever moves
            level.setBlock(pos, terrain ? TowerBuilder.surfaceAt(line, floor, pos.getX(), pos.getZ(), heights).defaultBlockState() : entry.getValue(), 2);
        }
        record.clear();
        ProgressData.get(level.getServer()).unmarkBuilt(scorchKey(line, floor));
    }

    /** A floor recorded as scorched with no god left on it (the server stopped mid-fight) is built again. */
    public static void repairIfStale(ServerLevel level, Line line, int floor) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = scorchKey(line, floor);
        if (!data.isBuilt(key)) return;
        int y0 = Tower.floorY(floor);
        AABB room = new AABB(-Tower.WALL_OUTER, y0 - Tower.DEPTH, -Tower.WALL_OUTER, Tower.WALL_OUTER, y0 + Tower.ROOM_HEIGHT + 1, Tower.WALL_OUTER);
        if (!level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive).isEmpty()) return;
        TowerBuilder.buildFloor(level, line, floor);
        data.unmarkBuilt(key);
    }

    /** The first open block above solid ground in a column, searching a few blocks either side of {@code y}; null if none. */
    public static BlockPos standing(ServerLevel level, int x, int y, int z) {
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            BlockState below = level.getBlockState(pos.below());
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && below.isCollisionShapeFullBlock(level, pos.below()) && below.getFluidState().isEmpty()) return pos;
        }
        return null;
    }

    /**
     * A shockwave across the floor: rings of dust and thrown paving running out from {@code from}, one ring every
     * few ticks. Only for show.
     */
    public static void shockwave(ServerLevel level, Vec3 from, ParticleOptions spark, int rings) {
        for (int ring = 1; ring <= rings; ring++) {
            final double radius = ring * 3.2;
            Runnable wave = () -> {
                int points = (int) (radius * 5);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    double x = from.x + Math.cos(a) * radius, z = from.z + Math.sin(a) * radius;
                    level.sendParticles(spark, x, from.y + 0.2, z, 1, 0.05, 0.05, 0.05, 0.0);
                    if (i % 3 == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Fx.ground(level, BlockPos.containing(x, from.y + 0.2, z))),
                                x, from.y + 0.15, z, 3, 0.2, 0.05, 0.2, 0.05);
                    }
                }
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i + radius;
                    Fx.impact(level, new Vec3(from.x + Math.cos(a) * radius, from.y, from.z + Math.sin(a) * radius), 2);
                }
            };
            Scheduler.after(level.getServer(), ring * 4, wave);
        }
    }
}
