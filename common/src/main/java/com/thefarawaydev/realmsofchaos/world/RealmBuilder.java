package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the Hub of Realms the first time somebody travels there (and again whenever {@link #LAYOUT_VERSION}
 * changes). The towers are built floor by floor by {@link TowerBuilder}.
 */
public final class RealmBuilder {
    public static final int LAYOUT_VERSION = TowerBuilder.LAYOUT_VERSION;
    public static final int Y = 64;
    private static final int HUB_RADIUS = 26;
    /** The hub has its own design number, so it can be rebuilt without rebuilding the towers. */
    public static final int HUB_VERSION = 7;
    /** The Oracle's Flame: just north of where arrivals appear, on the way to the gate of Zeus. */
    public static final BlockPos ORACLE = new BlockPos(0, Y + 1, -5);
    /** The Heart of the Hub stands south of the middle, facing the Oracle; the Bounty Board north-east of it. */
    public static final BlockPos HEART = new BlockPos(0, Y + 1, 5), BOUNTIES = new BlockPos(6, Y + 1, -6);
    /** The canal that rings the plaza, bridged where the four paths cross it. */
    public static final double CANAL_INNER = 11.0, CANAL_OUTER = 13.0;

    private RealmBuilder() {}

    public static void ensureHub(ServerLevel level) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = Realm.HUB.id() + "@" + LAYOUT_VERSION + "h" + HUB_VERSION;
        if (data.isBuilt(key)) return;
        buildHub(level);
        data.markBuilt(key);
    }

    /** Makes sure a player/boss arriving at this spot stands on solid ground with free space around them. */
    public static void makeSafe(ServerLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = feet.offset(dx, -1, dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (!state.isAir() && !state.getCollisionShape(level, p).isEmpty()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // --- shared helpers ------------------------------------------------------------------------

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void clear(ServerLevel level, int radius, int y0, int y1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = y0; y <= y1; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, 2);
                }
            }
        }
    }

    /** Every block column whose distance from the origin lies in [r0, r1). */
    private interface ColumnFn { void at(int x, int z, double dist); }

    private static void annulus(double r0, double r1, ColumnFn fn) {
        int ri = (int) Math.ceil(r1) + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= r0 && d < r1) fn.at(x, z, d);
            }
        }
    }

    private static void column(ServerLevel level, int x, int z, int y0, int height, Block block) {
        for (int h = 0; h < height; h++) set(level, x, y0 + h, z, block);
    }

    /** An invisible wall above the visible one so nobody can be knocked or jump out into the void. */
    private static void barrierWall(ServerLevel level, double r0, double r1, int y0, int y1) {
        annulus(r0, r1, (x, z, d) -> {
            for (int y = y0; y <= y1; y++) set(level, x, y, z, Blocks.BARRIER);
        });
    }

    /** A gate monument centred at (cx, cz); the 3x4 opening runs along X when alongX, otherwise along Z. */
    private static void gate(ServerLevel level, int cx, int cz, boolean alongX, Block pillar, Block lintel, Block accent, Block gateBlock, Block stone) {
        Mason.gateway(level, new BlockPos(cx, Y, cz), alongX ? Direction.NORTH : Direction.EAST, Mason.of(stone), pillar, lintel, accent, accent, gateBlock);
    }

    /** A pillar on a moulded base under a capital, with a light on top. */
    private static void beacon(ServerLevel level, int x, int z, Block shaft, Block stone, Block light) {
        Mason.column(level, new BlockPos(x, Y, z), 4, shaft, Mason.of(stone), true);
        set(level, x, Y + 5, z, light);
    }

    /**
     * A cherry tree that looks grown rather than built: a trunk that forks into three boughs, and a domed crown that
     * thins out raggedly at its edges, a little lopsided, never a box of leaves.
     */
    private static void tree(ServerLevel level, int gx, int gz) {
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        column(level, gx, gz, Y + 1, 4, Blocks.CHERRY_LOG);
        int[][] boughs = {{1, 0, 4}, {2, 0, 5}, {-1, 1, 5}, {-2, 1, 6}, {0, -1, 5}, {0, -2, 6}};
        for (int[] b : boughs) {
            net.minecraft.core.Direction.Axis axis = b[0] != 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z;
            set(level, gx + b[0], Y + b[2], gz + b[1], log.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis));
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 4; dy <= 9; dy++) {
                    double ox = (dx - 0.4) / 3.6, oy = (dy - 6.6) / 2.4, oz = (dz + 0.3) / 3.4;
                    double e = ox * ox + oy * oy + oz * oz;
                    if (e > 1.0) continue;
                    int h = Math.floorMod((gx + dx) * 73856093 ^ (Y + dy) * 19349663 ^ (gz + dz) * 83492791, 100);
                    if (e > 0.62 && h < 38) continue;                             // a ragged edge
                    BlockPos at = new BlockPos(gx + dx, Y + dy, gz + dz);
                    if (level.getBlockState(at).isAir()) level.setBlock(at, leaves, 2);
                }
            }
        }
        // a few blossoms trail below the crown
        for (int[] t : new int[][]{{3, 1}, {-3, -1}, {1, -3}, {-1, 3}, {2, 3}}) {
            BlockPos at = new BlockPos(gx + t[0], Y + 4, gz + t[1]);
            if (level.getBlockState(at.above()).is(Blocks.CHERRY_LEAVES) && level.getBlockState(at).isAir()) level.setBlock(at, leaves, 2);
        }
    }

    // --- hub -------------------------------------------------------------------------------------

    public static void buildHub(ServerLevel level) {
        clear(level, HUB_RADIUS + 4, Y - 3, Y + 34);
        // three-block-thick plaza; the floor is a compass rose pointing at the three gates
        annulus(0, HUB_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y - 2, z, Blocks.STONE_BRICKS);
            set(level, x, Y - 1, z, Blocks.STONE_BRICKS);
            Block top;
            boolean north = z < 0 && Math.abs(x) <= 2, east = x > 0 && Math.abs(z) <= 2, west = x < 0 && Math.abs(z) <= 2;
            boolean south = z > 0 && Math.abs(x) <= 2;
            if (d < 2.5) top = Blocks.CHISELED_STONE_BRICKS;
            else if (d < 4.5) top = Blocks.GILDED_BLACKSTONE;
            else if (d >= 9.5 && d < 10.5) top = Blocks.POLISHED_BLACKSTONE;
            else if (d >= 21.5 && d < 22.5) top = Blocks.POLISHED_BLACKSTONE;
            else if (north && d < 20) top = Math.abs(x) == 2 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ;
            else if (east && d < 20) top = Math.abs(z) == 2 ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS;
            else if (west && d < 20) top = Math.abs(z) == 2 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE_BRICKS;
            else if (south && d < 20) top = Math.abs(x) == 2 ? Blocks.VERDANT_FROGLIGHT : Blocks.MOSSY_STONE_BRICKS;
            else top = ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
            set(level, x, Y, z, top);
        });
        // low parapet with lantern posts, plus an invisible wall so nobody walks off the edge
        java.util.List<BlockPos> rail = new java.util.ArrayList<>();
        annulus(HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y + 1, z, d < HUB_RADIUS + 0.5 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
            set(level, x, Y + 2, z, Blocks.STONE_BRICK_WALL);
            rail.add(new BlockPos(x, Y + 2, z));
        });
        // the inner face of the parapet is finished with a plinth of stairs, the rail joined into a balustrade
        annulus(HUB_RADIUS - 1.5, HUB_RADIUS - 0.5, (x, z, d) -> {
            if (Math.abs(x) <= 2 || Math.abs(z) <= 2) return;
            set(level, x, Y + 1, z, Mason.stair(Blocks.POLISHED_ANDESITE_STAIRS, Mason.outward(x, z), false));
        });
        Mason.join(level, rail);
        barrierWall(level, HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, Y + 3, Y + 30);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 12;
            int px = (int) Math.round(Math.cos(a) * (HUB_RADIUS - 2)), pz = (int) Math.round(Math.sin(a) * (HUB_RADIUS - 2));
            set(level, px, Y + 1, pz, Blocks.CHISELED_STONE_BRICKS);
            column(level, px, pz, Y + 2, 2, Blocks.STONE_BRICK_WALL);
            set(level, px, Y + 4, pz, Blocks.LANTERN);
        }
        // Zeus (north), Poseidon (east), Hades (west): each gate is a small monument in its god's materials
        gate(level, 0, -18, true, Blocks.QUARTZ_PILLAR, Blocks.GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, ModContent.GATE_ZEUS.get(), Blocks.QUARTZ_BRICKS);
        gate(level, 18, 0, false, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, ModContent.GATE_POSEIDON.get(), Blocks.PRISMARINE_BRICKS);
        gate(level, -18, 0, false, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRYING_OBSIDIAN, Blocks.SHROOMLIGHT, ModContent.GATE_HADES.get(), Blocks.POLISHED_BLACKSTONE_BRICKS);
        // south: the way out, back to the world you came from
        gate(level, 0, 18, true, Blocks.MOSSY_STONE_BRICKS, Blocks.EMERALD_BLOCK, Blocks.VERDANT_FROGLIGHT, ModContent.GATE_LEAVE.get(), Blocks.MOSSY_STONE_BRICKS);
        // flanking beacons in front of each gate
        for (int side = -1; side <= 1; side += 2) {
            beacon(level, 5 * side, -15, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_BRICKS, Blocks.GLOWSTONE);
            beacon(level, 15, 5 * side, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN);
            beacon(level, -15, 5 * side, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SOUL_SOIL);
            set(level, -15, Y + 6, 5 * side, Blocks.SOUL_FIRE);
            beacon(level, 5 * side, 15, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.VERDANT_FROGLIGHT);
        }
        // two Forges of Chaos beside the central medallion
        set(level, 6, Y + 1, 6, ModContent.CHAOS_FORGE.get().defaultBlockState());
        set(level, -6, Y + 1, 6, ModContent.CHAOS_FORGE.get().defaultBlockState());

        // a canal rings the plaza: clear water over prismarine, lit from below, with lily pads, bridged by the four paths
        annulus(CANAL_INNER, CANAL_OUTER, (x, z, d) -> {
            if (Math.abs(x) <= 3 || Math.abs(z) <= 3) return;
            set(level, x, Y - 1, z, ((x * 7 + z * 13) & 3) == 0 ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS);
            set(level, x, Y, z, Blocks.WATER);
            if (Math.floorMod(x * 31 + z * 17, 7) == 0) set(level, x, Y + 1, z, Blocks.LILY_PAD);
        });
        // a garden in each corner: a cherry tree in a bed of moss and flowers
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int gx = sx * HubLife.GARDEN, gz = sz * HubLife.GARDEN;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        set(level, gx + dx, Y, gz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSS_BLOCK);
                    }
                }
                // a raised bed: a kerb of slabs round the moss
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) == 2 || Math.abs(dz) == 2) set(level, gx + dx, Y + 1, gz + dz, Mason.slab(Blocks.MOSSY_STONE_BRICK_SLAB, false));
                    }
                }
                Block[] flowers = {Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY};
                int n = 0;
                for (int[] f : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {1, 0}}) set(level, gx + f[0], Y + 1, gz + f[1], flowers[n++ % flowers.length]);
                tree(level, gx, gz);
            }
        }
        // the Oracle's Flame, on gilded stone
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, ORACLE.getX() + dx, Y, ORACLE.getZ() + dz, Blocks.GILDED_BLACKSTONE);
        set(level, ORACLE.getX(), ORACLE.getY(), ORACLE.getZ(), ModContent.ORACLE.get().defaultBlockState());
        // the Heart of the Hub on amethyst, and the Bounty Board on its post
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, HEART.getX() + dx, Y, HEART.getZ() + dz, Blocks.AMETHYST_BLOCK);
        set(level, HEART.getX(), HEART.getY(), HEART.getZ(), ModContent.HUB_HEART.get().defaultBlockState());
        set(level, BOUNTIES.getX(), Y, BOUNTIES.getZ(), Blocks.CHISELED_STONE_BRICKS);
        set(level, BOUNTIES.getX(), BOUNTIES.getY(), BOUNTIES.getZ(), ModContent.BOUNTY_BOARD.get().defaultBlockState());
        // plinths either side of each tower gate, for the echoes of its gods
        for (com.thefarawaydev.realmsofchaos.god.Line line : com.thefarawaydev.realmsofchaos.god.Line.values()) {
            for (BlockPos plinth : HubLife.plinths(line)) {
                set(level, plinth.getX(), plinth.getY(), plinth.getZ(), Blocks.CHISELED_STONE_BRICKS);
                for (int dy = 1; dy <= 4; dy++) set(level, plinth.getX(), plinth.getY() + dy, plinth.getZ(), Blocks.AIR);
            }
        }
        // what the hub looks out on: the gods' statues beyond their gates, their towers far off behind them
        HubVista.build(level);
        makeSafe(level, 0.5, Y + 1, 0.5);
        HubLife.populate(level);
    }
}
