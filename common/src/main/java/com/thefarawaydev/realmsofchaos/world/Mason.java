package com.thefarawaydev.realmsofchaos.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The mason's kit: what turns walls of whole blocks into buildings. A stone has its stair, slab and wall; a wall is
 * textured with a few stones of one family rather than one; an edge is finished with a stair turned the right way, a
 * ledge with a slab, a top with a wall; an opening is an arch, a wall a plinth and a cornice. Everything that builds
 * in the mod uses these, so every building is finished the same way.
 */
public final class Mason {
    /** A family of stone: the block itself and the stair, slab and wall cut from it. */
    public record Stone(Block block, Block stairs, Block slab, Block wall) {}

    private static final Map<Block, Stone> CUTS = Map.ofEntries(
            cut(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL),
            cut(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_WALL),
            cut(Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL),
            cut(Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL),
            cut(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL),
            cut(Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL),
            cut(Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.CALCITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.SNOW_BLOCK, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL),
            cut(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL),
            cut(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL),
            cut(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL),
            cut(Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL),
            cut(Blocks.PRISMARINE, Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_WALL),
            cut(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_WALL),
            cut(Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, Blocks.PRISMARINE_WALL),
            cut(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL),
            cut(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL),
            cut(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_WALL),
            cut(Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL),
            cut(Blocks.BLACKSTONE, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL),
            cut(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
            cut(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL),
            cut(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL),
            cut(Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
            cut(Blocks.GILDED_BLACKSTONE, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL),
            cut(Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL),
            cut(Blocks.POLISHED_BASALT, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
            cut(Blocks.BASALT, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL),
            cut(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_BRICK_SLAB, Blocks.NETHER_BRICK_WALL),
            cut(Blocks.RED_NETHER_BRICKS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL),
            cut(Blocks.OBSIDIAN, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
            cut(Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_WALL),
            cut(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB, Blocks.ANDESITE_WALL),
            cut(Blocks.SMOOTH_STONE, Blocks.STONE_STAIRS, Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_BRICK_WALL));

    private static Map.Entry<Block, Stone> cut(Block block, Block stairs, Block slab, Block wall) {
        return Map.entry(block, new Stone(block, stairs, slab, wall));
    }

    private Mason() {}

    /** The stair, slab and wall that go with a block; stone bricks' if it has none of its own. */
    public static Stone of(Block block) {
        Stone stone = CUTS.get(block);
        return stone != null ? stone : new Stone(block, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL);
    }

    // ---- single pieces --------------------------------------------------------------------------------------

    /** A stair whose tall back is towards {@code facing}; upside down hangs from above (a cornice, the haunch of an arch). */
    public static BlockState stair(Block stairs, Direction facing, boolean upsideDown) {
        BlockState state = stairs.defaultBlockState();
        if (state.hasProperty(StairBlock.FACING)) state = state.setValue(StairBlock.FACING, facing);
        if (state.hasProperty(StairBlock.HALF)) state = state.setValue(StairBlock.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
        return state;
    }

    public static BlockState slab(Block slab, boolean top) {
        BlockState state = slab.defaultBlockState();
        return state.hasProperty(SlabBlock.TYPE) ? state.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : state;
    }

    public static BlockState hanging(Block lantern) {
        BlockState state = lantern.defaultBlockState();
        return state.hasProperty(LanternBlock.HANGING) ? state.setValue(LanternBlock.HANGING, true) : state;
    }

    /** Of the four sides, the one a point {@code dx}, {@code dz} from a middle lies furthest towards. */
    public static Direction outward(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // ---- texture --------------------------------------------------------------------------------------------

    /**
     * A wall of mixed stones: mostly the first, now and then the others, never in stripes. The same place always
     * gets the same stone, so a rebuild changes nothing.
     */
    public static Block mix(int x, int y, int z, Block... stones) {
        if (stones.length == 1) return stones[0];
        long h = x * 3129871L ^ z * 116129781L ^ y * 7342477L;
        h = h * h * 42317861L + h * 11L;
        int roll = (int) Math.floorMod(h >> 16, 100L);
        return roll < 64 ? stones[0] : stones[1 + (roll % (stones.length - 1))];
    }

    private static final Map<Block, Block[]> WEATHER = Map.ofEntries(
            Map.entry(Blocks.STONE_BRICKS, new Block[]{Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS}),
            Map.entry(Blocks.COBBLESTONE, new Block[]{Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE}),
            Map.entry(Blocks.QUARTZ_BRICKS, new Block[]{Blocks.QUARTZ_BLOCK}),
            Map.entry(Blocks.CALCITE, new Block[]{Blocks.POLISHED_DIORITE, Blocks.DIORITE}),
            Map.entry(Blocks.CUT_SANDSTONE, new Block[]{Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE}),
            Map.entry(Blocks.SANDSTONE, new Block[]{Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE}),
            Map.entry(Blocks.PRISMARINE_BRICKS, new Block[]{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS}),
            Map.entry(Blocks.DARK_PRISMARINE, new Block[]{Blocks.PRISMARINE_BRICKS}),
            Map.entry(Blocks.DEEPSLATE_BRICKS, new Block[]{Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES}),
            Map.entry(Blocks.DEEPSLATE_TILES, new Block[]{Blocks.CRACKED_DEEPSLATE_TILES}),
            Map.entry(Blocks.NETHER_BRICKS, new Block[]{Blocks.CRACKED_NETHER_BRICKS}),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICKS, new Block[]{Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS}),
            Map.entry(Blocks.SMOOTH_BASALT, new Block[]{Blocks.BASALT, Blocks.SMOOTH_BASALT}),
            Map.entry(Blocks.BLACKSTONE, new Block[]{Blocks.POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE}),
            Map.entry(Blocks.POLISHED_BLACKSTONE, new Block[]{Blocks.BLACKSTONE}));

    /**
     * The stone a block of wall made of {@code stone} is, at that place: one in five or so a weathered or worked
     * neighbour of it, as a wall laid by hand is. Stones with no such neighbour are left alone.
     */
    public static Block weather(Block stone, int x, int y, int z, int percent) {
        Block[] others = WEATHER.get(stone);
        if (others == null) return stone;
        long h = x * 3129871L ^ z * 116129781L ^ y * 7342477L;
        h = h * h * 42317861L + h * 11L;
        int roll = (int) Math.floorMod(h >> 16, 100L);
        return roll >= percent ? stone : others[roll % others.length];
    }

    // ---- larger pieces --------------------------------------------------------------------------------------

    /** Placed walls, bars and panes only join up once their neighbours are there: call this after setting them. */
    public static void join(ServerLevel level, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), 2);
        }
    }

    /**
     * A round arch over an opening {@code width} wide (odd), its middle at {@code base}, its springing {@code up}
     * blocks above it, running along {@code along}: upside-down stairs at the haunches and a slab or block at the
     * crown. @return the blocks set
     */
    public static List<BlockPos> arch(ServerLevel level, BlockPos base, Direction along, int width, int up, Stone stone) {
        List<BlockPos> set = new ArrayList<>();
        int half = width / 2;
        Direction left = along.getCounterClockWise(), right = along.getClockWise();
        for (int i = -half; i <= half; i++) {
            BlockPos at = base.relative(right, i).above(up);
            BlockState state;
            if (Math.abs(i) == half) state = stair(stone.stairs(), i < 0 ? left : right, true);
            else state = slab(stone.slab(), true);
            level.setBlock(at, state, 2);
            set.add(at);
            level.setBlock(at.above(), stone.block().defaultBlockState(), 2);
            set.add(at.above());
        }
        return set;
    }

    /**
     * A classical pediment over a span {@code width} wide (odd), running along {@code along}: a course of the stone,
     * then a low gable rising half a block a column, slab and block in turn, to {@code peak} at its middle.
     */
    public static void pediment(ServerLevel level, BlockPos base, Direction along, int width, Stone stone, Block peak) {
        int half = width / 2;
        Direction right = along.getClockWise();
        for (int i = -half; i <= half; i++) {
            BlockPos at = base.relative(right, i);
            level.setBlock(at, stone.block().defaultBlockState(), 2);
            int j = half - Math.abs(i);                   // columns in from the eave
            int halves = j + 1;                           // its height in half blocks
            for (int y = 0; y < halves / 2; y++) level.setBlock(at.above(1 + y), stone.block().defaultBlockState(), 2);
            if (halves % 2 == 1) level.setBlock(at.above(1 + halves / 2), slab(stone.slab(), false), 2);
        }
        level.setBlock(base.above(1 + (half + 1) / 2), peak.defaultBlockState(), 2);
    }

    /**
     * A gateway seven wide across {@code along}'s axis, standing on {@code base}: the gate itself, three wide and four
     * high under a round head, between two pilasters with bases and capitals and two corbelled buttresses, carrying
     * an entablature and a pediment nine wide with {@code light} at its peak.
     */
    public static void gateway(ServerLevel level, BlockPos base, Direction along, Stone stone, Block pillar, Block trim, Block light, Block inlay, Block gate) {
        Direction right = along.getClockWise(), left = along.getCounterClockWise();
        for (int i = -3; i <= 3; i++) {
            BlockPos at = base.relative(right, i);
            int a = Math.abs(i);
            Direction toMiddle = i < 0 ? right : left;
            level.setBlock(at, inlay.defaultBlockState(), 2);
            if (a == 3) {
                for (int y = 1; y <= 3; y++) level.setBlock(at.above(y), (y == 1 ? trim : pillar).defaultBlockState(), 2);
                level.setBlock(at.above(4), stair(stone.stairs(), toMiddle, false), 2);
                level.setBlock(at.above(5), stair(stone.stairs(), toMiddle, true), 2);
            } else if (a == 2) {
                for (int y = 1; y <= 5; y++) level.setBlock(at.above(y), (y == 1 || y == 5 ? trim : pillar).defaultBlockState(), 2);
            } else {
                for (int y = 1; y <= 4; y++) level.setBlock(at.above(y), gate.defaultBlockState(), 2);
                level.setBlock(at.above(5), a == 0 ? slab(stone.slab(), true) : stair(stone.stairs(), toMiddle.getOpposite(), true), 2);
            }
            level.setBlock(at.above(6), trim.defaultBlockState(), 2);
        }
        pediment(level, base.above(7), along, 9, stone, light);
    }

    /**
     * A column with a base and a capital: a stair at each side of its foot and head, turned outward, round a shaft
     * of {@code shaft}. Stands on {@code foot} and is {@code height} tall.
     */
    public static void column(ServerLevel level, BlockPos foot, int height, Block shaft, Stone stone, boolean flaredSides) {
        for (int y = 1; y <= height; y++) level.setBlock(foot.above(y), shaft.defaultBlockState(), 2);
        if (!flaredSides) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos base = foot.above(1).relative(d), head = foot.above(height).relative(d);
            if (level.getBlockState(base).isAir()) level.setBlock(base, stair(stone.stairs(), d.getOpposite(), false), 2);
            if (level.getBlockState(head).isAir()) level.setBlock(head, stair(stone.stairs(), d.getOpposite(), true), 2);
        }
    }

    /** A lantern on a chain, hung {@code drop} blocks below {@code from}. */
    public static void chandelier(ServerLevel level, BlockPos from, int drop, Block lantern) {
        for (int i = 1; i < drop; i++) level.setBlock(from.below(i), Blocks.IRON_CHAIN.defaultBlockState(), 2);
        level.setBlock(from.below(drop), hanging(lantern), 2);
    }

    /** A cone of stairs for a round tower's roof: rings that step in one block a course, a finial at the top. */
    public static void cone(ServerLevel level, BlockPos centre, int radius, Stone stone, Block finial) {
        int y = 0;
        for (int r = radius; r >= 0; r--, y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r + 0.5 || d <= r - 0.6) continue;
                    BlockPos at = centre.offset(dx, y, dz);
                    level.setBlock(at, r == 0 ? stone.block().defaultBlockState() : stair(stone.stairs(), outward(dx, dz).getOpposite(), false), 2);
                }
            }
            if (r > 0) {
                // (filled within, so the roof is a roof and not a shell)
                for (int dx = -r + 1; dx <= r - 1; dx++) {
                    for (int dz = -r + 1; dz <= r - 1; dz++) {
                        if (Math.sqrt(dx * dx + dz * dz) <= r - 0.6) level.setBlock(centre.offset(dx, y, dz), stone.block().defaultBlockState(), 2);
                    }
                }
            }
        }
        level.setBlock(centre.above(y), finial.defaultBlockState(), 2);
    }
}
