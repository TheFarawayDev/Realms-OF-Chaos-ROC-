package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the hub and the three god zones the first time somebody travels there. The dimensions themselves are
 * void worlds declared by datapack JSON; everything you see is placed here.
 */
public final class RealmBuilder {
    private static final int Y = 64;

    private RealmBuilder() {}

    public static void ensureBuilt(ServerLevel level, Realm realm) {
        ProgressData data = ProgressData.get(level.getServer());
        if (data.isBuilt(realm)) return;
        if (realm == Realm.HUB) {
            buildHub(level);
        } else {
            buildZone(level, realm);
        }
        data.markBuilt(realm);
    }

    // --- shared helpers ------------------------------------------------------------------------

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        set(level, x, y, z, block.defaultBlockState());
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void disc(ServerLevel level, int cx, int cz, int y, double r, Block a, Block b) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z <= r * r) {
                    set(level, cx + x, y, cz + z, ((x + z) & 1) == 0 ? a : b);
                }
            }
        }
    }

    private static void ring(ServerLevel level, int cx, int cz, int y0, int height, double r, Block block) {
        int ri = (int) Math.ceil(r) + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r && d > r - 1.2) {
                    for (int h = 0; h < height; h++) {
                        set(level, cx + x, y0 + h, cz + z, block);
                    }
                }
            }
        }
    }

    private static void pillar(ServerLevel level, int x, int z, int y0, int height, Block block, Block cap) {
        for (int h = 0; h < height; h++) {
            set(level, x, y0 + h, z, block);
        }
        set(level, x, y0 + height, z, cap);
    }

    /** A gate arch centred at (cx, cz); the 3x4 opening runs along X when alongX, otherwise along Z. */
    private static void gate(ServerLevel level, int cx, int cz, boolean alongX, Block pillar, Block lintel, Block accent, Block gateBlock) {
        for (int i = -2; i <= 2; i++) {
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            boolean edge = Math.abs(i) == 2;
            for (int h = 1; h <= 5; h++) {
                if (edge) {
                    set(level, x, Y + h, z, pillar);
                } else if (h <= 4) {
                    set(level, x, Y + h, z, gateBlock);
                } else {
                    set(level, x, Y + h, z, lintel);
                }
            }
            if (edge) {
                set(level, x, Y + 6, z, accent);
            }
        }
        set(level, cx, Y + 6, cz, accent);
        // thicker base so the arch reads as a monument
        for (int i = -3; i <= 3; i++) {
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            set(level, x, Y, z, accent);
        }
    }

    // --- hub -------------------------------------------------------------------------------------

    private static void buildHub(ServerLevel level) {
        disc(level, 0, 0, Y - 1, 23, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS);
        disc(level, 0, 0, Y, 22, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE);
        ring(level, 0, 0, Y + 1, 1, 22, Blocks.STONE_BRICK_WALL);
        disc(level, 0, 0, Y, 4, Blocks.POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE);
        set(level, 0, Y, 0, Blocks.CHISELED_STONE_BRICKS);
        // lanterns around the central plinth
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            pillar(level, c[0], c[1], Y + 1, 2, Blocks.STONE_BRICK_WALL, Blocks.LANTERN);
        }
        // Zeus (north), Poseidon (east), Hades (west) - each gate stands under its god's colours
        gate(level, 0, -14, true, Blocks.QUARTZ_PILLAR, Blocks.GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK,
                ModContent.GATE_ZEUS.get());
        set(level, 0, Y + 7, -14, Blocks.LIGHTNING_ROD.asList().get(0));
        gate(level, 14, 0, false, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN,
                ModContent.GATE_POSEIDON.get());
        gate(level, -14, 0, false, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRYING_OBSIDIAN, Blocks.SHROOMLIGHT,
                ModContent.GATE_HADES.get());
        set(level, -14, Y + 7, 0, Blocks.SOUL_LANTERN);
        // glowing approach paths to each gate
        for (int i = 5; i <= 12; i++) {
            set(level, 0, Y, -i, Blocks.SMOOTH_QUARTZ);
            set(level, i, Y, 0, Blocks.PRISMARINE);
            set(level, -i, Y, 0, Blocks.BLACKSTONE);
        }
    }

    // --- zones -----------------------------------------------------------------------------------

    private static void buildZone(ServerLevel level, Realm realm) {
        Line line = realm.line();
        Block floorA, floorB, wall, pillar, cap, accent;
        switch (line) {
            case ZEUS -> {
                floorA = Blocks.QUARTZ_BRICKS; floorB = Blocks.SMOOTH_QUARTZ; wall = Blocks.QUARTZ_BLOCK;
                pillar = Blocks.QUARTZ_PILLAR; cap = Blocks.GOLD_BLOCK; accent = Blocks.GLOWSTONE;
            }
            case POSEIDON -> {
                floorA = Blocks.PRISMARINE_BRICKS; floorB = Blocks.DARK_PRISMARINE; wall = Blocks.PRISMARINE;
                pillar = Blocks.PRISMARINE_BRICKS; cap = Blocks.SEA_LANTERN; accent = Blocks.SEA_LANTERN;
            }
            default -> {
                floorA = Blocks.BLACKSTONE; floorB = Blocks.POLISHED_BLACKSTONE_BRICKS; wall = Blocks.POLISHED_BLACKSTONE_BRICKS;
                pillar = Blocks.POLISHED_BLACKSTONE; cap = Blocks.SOUL_LANTERN; accent = Blocks.SHROOMLIGHT;
            }
        }
        disc(level, 0, 0, Y - 1, 33, floorB, floorB);
        disc(level, 0, 0, Y, 32, floorA, floorB);
        ring(level, 0, 0, Y + 1, 7, 32, wall);
        // inner arena trim and decorative pillars
        ring(level, 0, 0, Y, 11, 1, accent);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int px = (int) Math.round(Math.cos(a) * 18);
            int pz = (int) Math.round(Math.sin(a) * 18);
            pillar(level, px, pz, Y + 1, 6, pillar, cap);
        }
        // altar platform near the arrival point and the exit gate behind it
        disc(level, 0, 18, Y, 3, accent, floorB);
        BlockState altar = switch (line) {
            case ZEUS -> ModContent.ALTAR_ZEUS.get().defaultBlockState();
            case POSEIDON -> ModContent.ALTAR_POSEIDON.get().defaultBlockState();
            case HADES -> ModContent.ALTAR_HADES.get().defaultBlockState();
        };
        set(level, 0, Y + 1, 18, altar);
        gate(level, 0, 29, true, pillar, wall, accent, ModContent.GATE_HUB.get());
    }
}
