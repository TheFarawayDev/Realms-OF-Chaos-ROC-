package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.world.Passages.Cell;
import com.thefarawaydev.realmsofchaos.world.Passages.Plan;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fills an ordinary room's four quarters - the floor between the lanes that run door to door - with set pieces
 * built of several blocks each: planters, a statue on its plinth, a table and its benches, a fallen column, a
 * basin, a brazier on a dais, a store of jars and barrels, a reading nook. Opposite quarters carry the same piece,
 * so a room reads as laid out rather than strewn; the lanes are never touched, and nothing stands higher than four.
 */
final class Furnish {
    enum Piece { PLANTER, STATUE, TABLE, RUBBLE, BASIN, DAIS, STORE, NOOK }

    private Furnish() {}

    /** The four quarters of a room, as signs of x and z. */
    private static final int[][] QUARTERS = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    static void room(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        Piece[] pieces = Piece.values();
        Piece a = pieces[random.nextInt(pieces.length)], b;
        do b = pieces[random.nextInt(pieces.length)]; while (b == a);
        boolean sunken = plan.form(c) == Passages.FORM_SUNKEN;
        int reach = plan.half(c);
        for (int i = 0; i < QUARTERS.length; i++) {
            Piece piece = i < 2 ? a : b;
            if (sunken) {
                // a sunken room: the corners of the ledge round its pit hold the pieces, two blocks square
                if (piece == Piece.TABLE || piece == Piece.RUBBLE) piece = Piece.STATUE;
                quarter(level, plan, c, p, random, QUARTERS[i][0], QUARTERS[i][1], piece, reach - 1, 2);
            } else {
                quarter(level, plan, c, p, random, QUARTERS[i][0], QUARTERS[i][1], piece, 2, reach - 1);
            }
        }
    }

    private static void put(ServerLevel level, BlockPos at, BlockState state) {
        level.setBlock(at, state, 2);
    }

    private static void put(ServerLevel level, BlockPos at, Block block) {
        level.setBlock(at, block.defaultBlockState(), 2);
    }

    /**
     * One quarter: the cells from 2 to the room's half-width out along both axes, counted from the room's middle.
     * {@code u} and {@code v} run from the lane's edge (0) to the wall (size - 1).
     */
    private static void quarter(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random, int sx, int sz, Piece piece, int start, int size) {
        BlockPos m = plan.centre(c);
        if (size < 2) return;
        Mason.Stone stone = Mason.of(p.wall());
        // (where the room's corners are cut away, so is the quarter)
        java.util.function.BiFunction<Integer, Integer, BlockPos> at = (u, v) -> m.offset(sx * (start + u), 0, sz * (start + v));
        java.util.function.BiPredicate<Integer, Integer> open = (u, v) -> {
            BlockPos f = at.apply(u, v);
            return level.getBlockState(f.above()).isAir() && !level.getBlockState(f).isAir() && !Passages.cutAt(plan, c, sx * (start + u), sz * (start + v));
        };
        int mid = size / 2;
        Direction toMiddleX = sx > 0 ? Direction.WEST : Direction.EAST, toMiddleZ = sz > 0 ? Direction.NORTH : Direction.SOUTH;
        switch (piece) {
            case PLANTER -> {
                // a raised bed kerbed in slabs, the tower's hedge in it, flowers round its foot
                for (int u = 0; u < size; u++) for (int v = 0; v < size; v++) {
                    if (!open.test(u, v)) continue;
                    BlockPos f = at.apply(u, v);
                    boolean rim = u == 0 || v == 0 || u == size - 1 || v == size - 1;
                    if (rim && size > 2) {
                        put(level, f.above(), Mason.slab(stone.slab(), false));
                    } else {
                        put(level, f, Blocks.MOSS_BLOCK);
                        put(level, f.above(), Passages.hedge(plan.line));
                        if (random.nextBoolean()) put(level, f.above(2), random.nextBoolean() ? Blocks.FLOWERING_AZALEA : Passages.hedge(plan.line));
                    }
                }
            }
            case STATUE -> {
                // a column of the tower on a stepped plinth, a light at its head
                BlockPos f = at.apply(mid, mid);
                if (!open.test(mid, mid)) return;
                put(level, f.above(), p.trim());
                for (int y = 2; y <= 3; y++) put(level, f.above(y), Passages.column(plan.line));
                put(level, f.above(4), p.light());
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos s = f.above().relative(d);
                    int u = Math.abs(s.getX() - m.getX()) - start, v = Math.abs(s.getZ() - m.getZ()) - start;
                    if (u >= 0 && v >= 0 && u < size && v < size && level.getBlockState(s).isAir()) put(level, s, Mason.stair(stone.stairs(), d.getOpposite(), false));
                }
                if (size >= 3) put(level, at.apply(size - 1, size - 1).above(), Passages.relief(plan.line));
            }
            case TABLE -> {
                // a table, a lamp of candles on it, benches drawn up along both its long sides
                if (!open.test(mid, mid)) return;
                int from = size > 3 ? mid - 1 : mid, to = size > 3 ? mid + 1 : mid;
                for (int u = from; u <= to; u++) {
                    if (!open.test(u, mid)) continue;
                    put(level, at.apply(u, mid).above(), Mason.slab(Blocks.DARK_OAK_SLAB, true));
                    if (mid + 1 < size && open.test(u, mid + 1)) put(level, at.apply(u, mid + 1).above(), Mason.stair(Blocks.DARK_OAK_STAIRS, sz > 0 ? Direction.SOUTH : Direction.NORTH, false));
                    if (mid - 1 >= 0 && open.test(u, mid - 1)) put(level, at.apply(u, mid - 1).above(), Mason.stair(Blocks.DARK_OAK_STAIRS, sz > 0 ? Direction.NORTH : Direction.SOUTH, false));
                }
                put(level, at.apply(mid, mid).above(2), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                        .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
                if (size >= 3 && open.test(size - 1, size - 1)) put(level, at.apply(size - 1, size - 1).above(), Blocks.BARREL);
            }
            case RUBBLE -> {
                // a column that has come down: its stump, its drums across the floor, chips of stone about them
                if (!open.test(size - 1, size - 1)) return;
                BlockPos stump = at.apply(size - 1, size - 1);
                put(level, stump.above(), Passages.column(plan.line));
                put(level, stump.above(2), Mason.slab(stone.slab(), false));
                for (int u = 0; u < size - 1; u++) {
                    if (open.test(u, size - 1)) put(level, at.apply(u, size - 1).above(), u % 2 == 0 ? p.pillar() : Passages.column(plan.line));
                }
                for (int u = 0; u < size; u++) for (int v = 0; v < size - 1; v++) {
                    if (!open.test(u, v) || random.nextFloat() > 0.45F) continue;
                    BlockPos f = at.apply(u, v).above();
                    put(level, f, random.nextBoolean() ? Mason.slab(stone.slab(), false) : Mason.stair(stone.stairs(), Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong())), false));
                }
                if (open.test(0, 0)) put(level, at.apply(0, 0).above(), Blocks.COBWEB);
            }
            case BASIN -> {
                // a basin of clear water, kerbed and lit from beneath
                for (int u = 0; u < size; u++) for (int v = 0; v < size; v++) {
                    if (!open.test(u, v)) continue;
                    BlockPos f = at.apply(u, v);
                    boolean rim = u == 0 || v == 0 || u == size - 1 || v == size - 1;
                    if (rim) {
                        put(level, f, p.trim());
                        put(level, f.above(), Mason.slab(stone.slab(), false));
                    } else {
                        put(level, f.below(), p.light());
                        put(level, f, Blocks.WATER);
                        if (random.nextFloat() < 0.3F) put(level, f.above(), Blocks.LILY_PAD);
                    }
                }
                if (size == 2) put(level, at.apply(1, 1).above(2), p.light());
            }
            case DAIS -> {
                // a dais a step up, its edge in stairs, the tower's brazier burning on it
                for (int u = 0; u < size; u++) for (int v = 0; v < size; v++) {
                    if (!open.test(u, v)) continue;
                    BlockPos f = at.apply(u, v).above();
                    boolean edgeU = u == 0, edgeV = v == 0;
                    if (edgeU || edgeV) put(level, f, edgeU && edgeV ? Mason.slab(stone.slab(), false) : Mason.stair(stone.stairs(), edgeU ? toMiddleX.getOpposite() : toMiddleZ.getOpposite(), false));
                    else put(level, f, Mason.slab(stone.slab(), false));
                }
                BlockPos top = at.apply(Math.min(size - 1, mid + 1), Math.min(size - 1, mid + 1));
                put(level, top.above(), p.trim());
                level.setBlock(top.above(2), ModContent.TOWER_BRAZIER.get().defaultBlockState()
                        .setValue(com.thefarawaydev.realmsofchaos.block.BrazierBlock.TOWER, plan.line.ordinal()), 2);
            }
            case STORE -> {
                // a cellar hatch in the floor of the store, shut
                if (open.test(0, 0)) put(level, at.apply(0, 0).above(), ModContent.BRONZE_TRAPDOOR.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM));
                // a store against the walls: barrels stacked, the jars the tower kept its oil in, a sack or two
                for (int u = 0; u < size; u++) for (int v = 0; v < size; v++) {
                    if (!open.test(u, v)) continue;
                    BlockPos f = at.apply(u, v).above();
                    boolean wall = u == size - 1 || v == size - 1;
                    if (!wall && random.nextFloat() < 0.6F) continue;
                    float roll = random.nextFloat();
                    if (roll < 0.4F) {
                        put(level, f, Blocks.BARREL);
                        if (u == size - 1 && v == size - 1) put(level, f.above(), Blocks.BARREL);
                    } else if (roll < 0.75F) {
                        put(level, f, ModContent.ANCIENT_URN.get());
                    } else {
                        put(level, f, Blocks.HAY_BLOCK);
                    }
                }
            }
            case NOOK -> {
                // a reading nook: a case of scrolls along the wall, a lectern before it, a bench
                for (int u = 0; u < size; u++) {
                    if (!open.test(u, size - 1)) continue;
                    for (int y = 1; y <= 2; y++) put(level, at.apply(u, size - 1).above(y), ModContent.SCROLL_SHELF.get());
                }
                if (open.test(mid, Math.max(0, size - 2))) put(level, at.apply(mid, Math.max(0, size - 2)).above(), Blocks.LECTERN);
                if (size >= 3 && open.test(size - 1, 0)) put(level, at.apply(size - 1, 0).above(), Mason.stair(Blocks.DARK_OAK_STAIRS, toMiddleX.getOpposite(), false));
            }
        }
    }
}
