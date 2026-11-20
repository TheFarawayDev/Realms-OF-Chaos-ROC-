package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
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

    private RealmBuilder() {}

    public static void ensureHub(ServerLevel level) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = Realm.HUB.id() + "@" + LAYOUT_VERSION;
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

    /** A gate arch centred at (cx, cz); the 3x4 opening runs along X when alongX, otherwise along Z. */
    private static void gate(ServerLevel level, int cx, int cz, boolean alongX, Block pillar, Block lintel, Block accent, Block gateBlock) {
        for (int i = -3; i <= 3; i++) {
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            int a = Math.abs(i);
            set(level, x, Y, z, accent);
            if (a == 3) {                                   // outer buttress
                column(level, x, z, Y + 1, 3, pillar);
                set(level, x, Y + 4, z, accent);
            } else if (a == 2) {                            // main pillars
                column(level, x, z, Y + 1, 5, pillar);
                set(level, x, Y + 6, z, lintel);
            } else {                                        // the portal itself
                column(level, x, z, Y + 1, 4, gateBlock);
                set(level, x, Y + 5, z, lintel);
                set(level, x, Y + 6, z, a == 0 ? accent : lintel);
            }
        }
        set(level, cx, Y + 7, cz, lintel);
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
        annulus(HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y + 1, z, Blocks.STONE_BRICKS);
            set(level, x, Y + 2, z, Blocks.STONE_BRICK_WALL);
        });
        barrierWall(level, HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, Y + 3, Y + 30);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 12;
            int px = (int) Math.round(Math.cos(a) * (HUB_RADIUS - 2)), pz = (int) Math.round(Math.sin(a) * (HUB_RADIUS - 2));
            column(level, px, pz, Y + 1, 3, Blocks.STONE_BRICK_WALL);
            set(level, px, Y + 4, pz, Blocks.LANTERN);
        }
        // Zeus (north), Poseidon (east), Hades (west): each gate is a small monument in its god's materials
        gate(level, 0, -18, true, Blocks.QUARTZ_PILLAR, Blocks.GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, ModContent.GATE_ZEUS.get());
        gate(level, 18, 0, false, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, ModContent.GATE_POSEIDON.get());
        gate(level, -18, 0, false, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRYING_OBSIDIAN, Blocks.SHROOMLIGHT, ModContent.GATE_HADES.get());
        // south: the way out, back to the world you came from
        gate(level, 0, 18, true, Blocks.MOSSY_STONE_BRICKS, Blocks.EMERALD_BLOCK, Blocks.VERDANT_FROGLIGHT, ModContent.GATE_LEAVE.get());
        // flanking beacons in front of each gate
        for (int side = -1; side <= 1; side += 2) {
            column(level, 5 * side, -15, Y + 1, 4, Blocks.QUARTZ_PILLAR);
            set(level, 5 * side, Y + 5, -15, Blocks.GLOWSTONE);
            column(level, 15, 5 * side, Y + 1, 4, Blocks.PRISMARINE_BRICKS);
            set(level, 15, Y + 5, 5 * side, Blocks.SEA_LANTERN);
            column(level, -15, 5 * side, Y + 1, 4, Blocks.POLISHED_BLACKSTONE);
            set(level, -15, Y + 5, 5 * side, Blocks.SOUL_SOIL);
            set(level, -15, Y + 6, 5 * side, Blocks.SOUL_FIRE);
            column(level, 5 * side, 15, Y + 1, 4, Blocks.MOSSY_STONE_BRICKS);
            set(level, 5 * side, Y + 5, 15, Blocks.VERDANT_FROGLIGHT);
        }
        // two Forges of Chaos beside the central medallion
        set(level, 6, Y + 1, 6, ModContent.CHAOS_FORGE.get().defaultBlockState());
        set(level, -6, Y + 1, 6, ModContent.CHAOS_FORGE.get().defaultBlockState());
        makeSafe(level, 0.5, Y + 1, 0.5);
    }
}
