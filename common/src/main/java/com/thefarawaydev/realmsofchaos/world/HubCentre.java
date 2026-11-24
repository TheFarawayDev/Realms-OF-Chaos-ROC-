package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The middle of the hub, built as a place rather than a few blocks on a floor:
 * <ul>
 *   <li>a <b>rotunda</b> over the spawn - eight marble columns in a ring, an entablature on them, and a dome of
 *   verdigris copper with a chandelier hung under it; the Oracle's Flame and the Heart stand in its openings;</li>
 *   <li>the <b>Bounty Board</b>, a real notice board: nine boards of notices in a timber frame under a gabled roof,
 *   every one of them the board itself, so clicking anywhere on it opens the bounties;</li>
 *   <li>two <b>Forges of Chaos</b>, each a smithy: a bank of ten forge mouths (every one a Forge of Chaos) under a
 *   hood, a great chimney rising twenty blocks with smoke at its top, a hipped roof round it on posts, an anvil, a
 *   grindstone, and a quenching trough fed by the canal.</li>
 * </ul>
 */
public final class HubCentre {
    private static final int Y = RealmBuilder.Y;
    /** How far the smithies stand out from the middle, past the rotunda. */
    private static final int OFF = 5;

    private HubCentre() {}

    public static void build(ServerLevel level) {
        // (the rotunda goes up last: the others clear their ground first, and it reaches nearly to them)
        board(level);
        for (int sx = -1; sx <= 1; sx += 2) forge(level, sx);
        pool(level);
        rotunda(level);
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static Block copper(com.thefarawaydev.realmsofchaos.world.HubCentre.Cut cut) {
        var family = switch (cut) {
            case BLOCK -> Blocks.CUT_COPPER;
            case STAIRS -> Blocks.CUT_COPPER_STAIRS;
            case SLAB -> Blocks.CUT_COPPER_SLAB;
        };
        return family.waxed().pick(WeatheringCopper.WeatherState.OXIDIZED);
    }

    private enum Cut { BLOCK, STAIRS, SLAB }

    // ---- the rotunda ------------------------------------------------------------------------------------------

    /** The columns of the rotunda: eight, between the four paths, about the spawn. */
    public static List<BlockPos> columns() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 8;
            out.add(new BlockPos((int) Math.round(Math.cos(a) * 7.0), Y, (int) Math.round(Math.sin(a) * 7.0)));
        }
        return out;
    }

    private static void rotunda(ServerLevel level) {
        Mason.Stone marble = Mason.of(Blocks.QUARTZ_BRICKS);
        for (BlockPos foot : columns()) {
            Mason.column(level, foot, 6, ModContent.OLYMPIAN_MARBLE_PILLAR.get(), marble, true);
            set(level, foot.getX(), Y + 1, foot.getZ(), Blocks.CHISELED_QUARTZ_BLOCK);
        }
        // the entablature: a ring of marble on the columns, a gilded frieze, a cornice of hanging stairs outside it
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                Direction out = Mason.outward(dx, dz);
                if (d >= 5.9 && d < 8.1) set(level, dx, Y + 7, dz, ModContent.GILDED_FRIEZE.get());
                else if (d >= 8.1 && d < 9.1) set(level, dx, Y + 7, dz, Mason.stair(marble.stairs(), out.getOpposite(), true));
                if (d >= 5.9 && d < 8.1) set(level, dx, Y + 8, dz, ModContent.OLYMPIAN_MARBLE.get());
            }
        }
        // the dome: rings of verdigris copper stepping in to a gilded lantern at its crown
        Block stairs = copper(Cut.STAIRS), block = copper(Cut.BLOCK), slab = copper(Cut.SLAB);
        double R = 7.8, H = 6.5;
        List<BlockPos> roof = new ArrayList<>();
        for (int y = 0; y <= (int) H; y++) {
            double r = R * Math.sqrt(Math.max(0, 1 - (y / H) * (y / H))), above = R * Math.sqrt(Math.max(0, 1 - ((y + 1) / H) * ((y + 1) / H)));
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    BlockPos at = new BlockPos(dx, Y + 9 + y, dz);
                    if (d > above - 0.3 && d > 0.6) {
                        level.setBlock(at, Mason.stair(stairs, Mason.outward(dx, dz).getOpposite(), false), 2);
                        roof.add(at);
                    } else {
                        level.setBlock(at, (d < 0.6 && y == (int) H) ? slab.defaultBlockState() : block.defaultBlockState(), 2);
                    }
                }
            }
        }
        Mason.join(level, roof);
        BlockPos crown = new BlockPos(0, Y + 10 + (int) H, 0);
        while (!level.getBlockState(crown).isAir()) crown = crown.above();
        set(level, 0, crown.getY(), 0, ModContent.CHAOS_LAMP.get());
        set(level, 0, crown.getY() + 1, 0, Blocks.LANTERN);
        // under the dome, a chandelier over the spawn - high enough to walk under
        Mason.chandelier(level, new BlockPos(0, Y + 9, 0), 3, Blocks.LANTERN);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Mason.chandelier(level, new BlockPos((int) Math.round(Math.cos(a) * 4), Y + 9, (int) Math.round(Math.sin(a) * 4)), 2, Blocks.LANTERN);
        }
    }

    // ---- the pool ---------------------------------------------------------------------------------------------

    /** The middle of the still pool in the fourth quarter of the plaza, opposite the board. */
    public static final BlockPos POOL = new BlockPos(-10, Y, -10);

    /** A still pool with an obelisk standing in it, banded in the three towers' stones, gilded at its tip. */
    private static void pool(ServerLevel level) {
        int px = POOL.getX(), pz = POOL.getZ();
        Mason.Stone marble = Mason.of(Blocks.QUARTZ_BRICKS);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                for (int y = Y + 1; y <= Y + 14; y++) set(level, px + dx, y, pz + dz, Blocks.AIR);
                if (ring <= 2) {
                    set(level, px + dx, Y - 1, pz + dz, (dx + dz & 1) == 0 ? Blocks.PRISMARINE_BRICKS : Blocks.SEA_LANTERN);
                    set(level, px + dx, Y, pz + dz, Blocks.WATER);
                } else if (ring == 3) {
                    set(level, px + dx, Y, pz + dz, Blocks.QUARTZ_BRICKS);
                    set(level, px + dx, Y + 1, pz + dz, Math.abs(dx) == 3 && Math.abs(dz) == 3 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
                            : Mason.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
                } else if (Math.hypot(px + dx, pz + dz) < RealmBuilder.CANAL_INNER - 0.1) {
                    set(level, px + dx, Y, pz + dz, Blocks.POLISHED_DIORITE);
                }
            }
        }
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) set(level, px + c[0], Y + 2, pz + c[1], Blocks.LANTERN);
        // the obelisk, on a plinth in the water
        set(level, px, Y, pz, Blocks.CHISELED_QUARTZ_BLOCK);
        for (Direction d : Direction.Plane.HORIZONTAL) set(level, px + d.getStepX(), Y + 1, pz + d.getStepZ(), Mason.stair(marble.stairs(), d.getOpposite(), false));
        Block marbleBlock = ModContent.OLYMPIAN_MARBLE.get();
        Block[] bands = {marbleBlock, marbleBlock, ModContent.GILDED_FRIEZE.get(), marbleBlock, ModContent.ABYSSAL_TILE.get(), marbleBlock,
                ModContent.UNDERWORLD_BRICK.get(), marbleBlock, marbleBlock, ModContent.CHAOS_LAMP.get()};
        for (int y = 1; y <= bands.length; y++) set(level, px, Y + y, pz, bands[y - 1]);
        set(level, px, Y + bands.length + 1, pz, Blocks.END_ROD);
    }

    // ---- the bounty board -------------------------------------------------------------------------------------

    private static void board(ServerLevel level) {
        int bx = RealmBuilder.BOUNTIES.getX(), bz = RealmBuilder.BOUNTIES.getZ();
        BlockState board = ModContent.BOUNTY_BOARD.get().defaultBlockState();
        Block post = Blocks.DARK_OAK_LOG, planks = Blocks.DARK_OAK_PLANKS, stair = Blocks.DARK_OAK_STAIRS, slab = Blocks.DARK_OAK_SLAB;
        // clear its ground
        for (int x = bx - 4; x <= bx + 4; x++) for (int z = bz - 3; z <= bz + 3; z++) for (int y = Y + 1; y <= Y + 7; y++) {
            double d = Math.hypot(x, z);
            if (d >= 9.5 && d < 16.5) set(level, x, y, z, Blocks.AIR);
        }
        // the boards: three by three, every one of them the board, so it opens from wherever it is clicked
        for (int dx = -1; dx <= 1; dx++) for (int y = 1; y <= 3; y++) set(level, bx + dx, Y + y, bz, board);
        // a backing of planks behind, a post either side, a header over the top
        for (int dx = -2; dx <= 2; dx++) for (int y = 1; y <= 4; y++) set(level, bx + dx, Y + y, bz - 1, y == 4 ? post : planks);
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 4; y++) set(level, bx + side * 2, Y + y, bz, post);
            set(level, bx + side * 2, Y, bz, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int dx = -1; dx <= 1; dx++) set(level, bx + dx, Y + 4, bz, planks);
        // a gabled roof over it, its ridge running along the board, its eaves well out in front
        List<BlockPos> roof = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            int x = bx + dx;
            set(level, x, Y + 4, bz + 2, Mason.stair(stair, Direction.NORTH, false));
            set(level, x, Y + 4, bz - 3, Mason.stair(stair, Direction.SOUTH, false));
            set(level, x, Y + 5, bz + 1, Mason.stair(stair, Direction.NORTH, false));
            set(level, x, Y + 5, bz - 2, Mason.stair(stair, Direction.SOUTH, false));
            set(level, x, Y + 5, bz, planks);
            set(level, x, Y + 5, bz - 1, planks);
            set(level, x, Y + 6, bz, Mason.slab(slab, false));
            set(level, x, Y + 6, bz - 1, Mason.slab(slab, false));
            for (int dz : new int[]{2, -3, 1, -2}) roof.add(new BlockPos(x, Y + (dz == 2 || dz == -3 ? 4 : 5), bz + dz));
        }
        Mason.join(level, roof);
        // lanterns under the eaves, a barrel and a crate of scrolls beside it, notices pinned to the posts
        for (int side = -1; side <= 1; side += 2) {
            set(level, bx + side * 2, Y + 3, bz + 2, Mason.hanging(Blocks.LANTERN));
            BlockState note = Blocks.BIRCH_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.SOUTH);
            set(level, bx + side * 2, Y + 2, bz + 1, note);
        }
        set(level, bx - 3, Y + 1, bz, Blocks.BARREL);
        set(level, bx - 3, Y + 1, bz - 1, Blocks.CHISELED_BOOKSHELF);
        set(level, bx + 3, Y + 1, bz, Blocks.BARREL);
        set(level, bx + 3, Y + 1, bz + 1, Blocks.LECTERN);
    }

    // ---- the forges -------------------------------------------------------------------------------------------

    /** The ten mouths of one forge's bank: its two faces towards the middle, two high. The first is the tested one. */
    public static List<BlockPos> mouths(int sx) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 1; y <= 2; y++) {
            out.add(new BlockPos(sx * (6 + OFF), Y + y, 6 + OFF));
            for (int k = 7; k <= 8; k++) {
                out.add(new BlockPos(sx * (6 + OFF), Y + y, k + OFF));
                out.add(new BlockPos(sx * (k + OFF), Y + y, 6 + OFF));
            }
        }
        return out;
    }

    private static void forge(ServerLevel level, int sx) {
        Mason.Stone brick = Mason.of(Blocks.POLISHED_BLACKSTONE_BRICKS), tile = Mason.of(Blocks.DEEPSLATE_TILES);
        // clear it and lay its floor across the canal (the trough keeps the canal's water)
        for (int u = 4; u <= 11; u++) {
            for (int z = 4; z <= 11; z++) {
                int x = sx * (u + OFF);
                for (int y = Y + 1; y <= Y + 24; y++) set(level, x, y, z + OFF, Blocks.AIR);
                if (u >= 5 && z >= 5) {
                    set(level, x, Y - 1, z + OFF, Blocks.DEEPSLATE_BRICKS);
                    set(level, x, Y, z + OFF, u == 5 || z == 5 || u == 11 || z == 11 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.DEEPSLATE_TILES);
                }
            }
        }
        // the furnace: a block of brick three by three, its two faces to the middle a bank of forge mouths, hot within
        for (int u = 6; u <= 8; u++) {
            for (int z = 6; z <= 8; z++) {
                for (int y = 1; y <= 3; y++) set(level, sx * (u + OFF), Y + y, z + OFF, y == 3 ? Blocks.POLISHED_BLACKSTONE_BRICKS : u == 6 || z == 6 ? Blocks.DEEPSLATE_BRICKS : Blocks.MAGMA_BLOCK);
            }
        }
        BlockState mouth = ModContent.CHAOS_FORGE.get().defaultBlockState();
        for (BlockPos p : mouths(sx)) level.setBlock(p, mouth, 2);
        // (then each is joined to the others, so the bank reads as one furnace)
        for (BlockPos p : mouths(sx)) {
            if (level.getBlockState(p).getBlock() instanceof com.thefarawaydev.realmsofchaos.block.ForgeBlock forge) level.setBlock(p, forge.joined(level, p, level.getBlockState(p)), 2);
        }
        // a hood over the bank, carried out on hanging stairs
        for (int k = 5; k <= 9; k++) {
            set(level, sx * (5 + OFF), Y + 3, k + OFF, Mason.stair(brick.stairs(), sx > 0 ? Direction.EAST : Direction.WEST, true));
            set(level, sx * (k + OFF), Y + 3, 5 + OFF, Mason.stair(brick.stairs(), Direction.SOUTH, true));
        }
        set(level, sx * (5 + OFF), Y + 3, 5 + OFF, Blocks.POLISHED_BLACKSTONE_BRICKS);
        // the chimney: tapering in two stages to twenty blocks, banded, smoking at its top
        for (int y = 4; y <= 20; y++) {
            for (int u = 6; u <= 8; u++) {
                for (int z = 6; z <= 8; z++) {
                    boolean middle = u == 7 && z == 7;
                    boolean corner = (u != 7 && z != 7);
                    if (y > 13 && corner) continue;              // the upper stage is narrower
                    Block b = y % 4 == 0 ? ModContent.UNDERWORLD_BRICK.get() : Mason.weather(Blocks.DEEPSLATE_BRICKS, sx * u, Y + y, z, 25);
                    set(level, sx * (u + OFF), Y + y, z + OFF, middle && y < 19 ? Blocks.AIR : b);
                }
            }
            if (y == 13) {
                // the set-off between the stages: hanging stairs at the corners
                for (int[] c : new int[][]{{6, 6}, {6, 8}, {8, 6}, {8, 8}}) set(level, sx * (c[0] + OFF), Y + 14, (c[1]) + OFF, Mason.stair(brick.stairs(), Mason.outward(sx * (c[0] - 7), c[1] - 7).getOpposite(), false));
            }
        }
        set(level, sx * (7 + OFF), Y + 19, 7 + OFF, Blocks.HAY_BLOCK);
        set(level, sx * (7 + OFF), Y + 20, 7 + OFF, Blocks.CAMPFIRE);
        for (Direction d : Direction.Plane.HORIZONTAL) set(level, sx * (7 + d.getStepX() + OFF), Y + 21, 7 + OFF + d.getStepZ(), Mason.stair(brick.stairs(), d.getOpposite(), true));
        // a hipped roof round the chimney on four posts
        List<BlockPos> roof = new ArrayList<>();
        for (int u = 4; u <= 11; u++) {
            for (int z = 4; z <= 11; z++) {
                if (u >= 6 && u <= 8 && z >= 6 && z <= 8) continue;
                int du = u < 6 ? 6 - u : u > 8 ? u - 8 : 0, dz = z < 6 ? 6 - z : z > 8 ? z - 8 : 0;
                int ring = Math.max(du, dz);
                if (u < 5 || z < 5) continue;                      // (the front stays open to the plaza under the hood)
                int y = Y + 8 - ring;
                BlockPos at = new BlockPos(sx * (u + OFF), y, z + OFF);
                Direction toChimney = Mason.outward(sx * (u - 7), z - 7).getOpposite();
                level.setBlock(at, Mason.stair(Blocks.DARK_OAK_STAIRS, toChimney, false), 2);
                roof.add(at);
            }
        }
        Mason.join(level, roof);
        for (int[] p : new int[][]{{5, 11}, {11, 5}, {11, 11}}) {              // (none at the front corner: the forge mouths are seen whole)
            for (int y = 1; y < 8 - Math.max(p[0] < 6 ? 6 - p[0] : p[0] - 8, p[1] < 6 ? 6 - p[1] : p[1] - 8); y++) set(level, sx * (p[0] + OFF), Y + y, (p[1]) + OFF, Blocks.DARK_OAK_LOG);
        }
        // the quenching trough, fed by the canal, kerbed in slabs; an anvil and a grindstone at hand; lamps under the roof
        for (int u = 9; u <= 10; u++) for (int z = 8; z <= 9; z++) set(level, sx * (u + OFF), Y, z + OFF, Blocks.WATER);
        for (int[] k : new int[][]{{9, 7}, {10, 7}, {11, 8}, {11, 9}, {9, 10}, {10, 10}, {8, 9}}) set(level, sx * (k[0] + OFF), Y + 1, (k[1]) + OFF, Mason.slab(brick.slab(), false));
        set(level, sx * (5 + OFF), Y + 1, 9 + OFF, Blocks.ANVIL);
        set(level, sx * (9 + OFF), Y + 1, 5 + OFF, Blocks.GRINDSTONE);
        set(level, sx * (10 + OFF), Y + 1, 5 + OFF, Blocks.SMITHING_TABLE);
        set(level, sx * (5 + OFF), Y + 1, 10 + OFF, Blocks.BARREL);
        for (int[] l : new int[][]{{9, 10}, {10, 9}}) set(level, sx * (l[0] + OFF), Y + 5, (l[1]) + OFF, Mason.hanging(Blocks.LANTERN));
    }
}
