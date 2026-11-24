package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mod's glass. Every kind connects: a run of it is one window, with one frame round its outside and none between
 * its blocks.
 *
 * <ul>
 * <li><b>Reinforced</b> - clear, framed in bronze, as hard to break as stone.
 * <li><b>Frosted</b> - milky: light comes through it, nothing else.
 * <li><b>Smart glass</b> - shaded until it has a redstone signal, then clear.
 * <li><b>Veil glass</b> - a solid-looking pane, untinted, that turns clear and back: on a redstone signal, or when
 *     anything shot or thrown strikes it.
 * <li><b>One-way glass</b> - a solid pane seen from the side it faces; from behind, nothing at all.
 * </ul>
 *
 * Smart and veil glass do not change all at once: the change runs through the window block by block from wherever
 * it started, like sand running.
 */
public final class Glass {
    private Glass() {}

    /** The most blocks of one window that change together, and how many ticks the change takes to go one block. */
    public static final int MAX_WINDOW = 512, RIPPLE_TICKS = 2;

    /** Every block of one window, nearest first: this block and all of the same kind joined to it, face to face. */
    public static List<BlockPos> window(Level level, BlockPos start, Block kind) {
        List<BlockPos> out = new ArrayList<>();
        Map<BlockPos, Boolean> seen = new HashMap<>();
        Deque<BlockPos> next = new ArrayDeque<>();
        next.add(start.immutable());
        seen.put(start.immutable(), true);
        while (!next.isEmpty() && out.size() < MAX_WINDOW) {
            BlockPos at = next.poll();
            if (!level.getBlockState(at).is(kind)) continue;
            out.add(at);
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (seen.putIfAbsent(n, true) == null && level.getBlockState(n).is(kind)) next.add(n);
            }
        }
        return out;
    }

    // ---- the ripple -----------------------------------------------------------------------------------------------------

    /** For each block a ripple is on its way to, which ripple: a later one overrules an earlier. */
    private static final Map<BlockPos, Long> STAMPS = new HashMap<>();
    private static final Map<BlockPos, Boolean> HEADING = new HashMap<>();
    private static long ripples;

    /** Where a ripple is heading for the block at {@code pos}, or null if none is on its way. */
    public static Boolean heading(BlockPos pos) {
        return HEADING.get(pos);
    }

    /**
     * Sets {@code property} to {@code value} through the whole window {@code pos} belongs to, the nearest blocks first:
     * one block further each {@link #RIPPLE_TICKS} ticks. @return how many blocks it will change
     */
    public static int ripple(ServerLevel level, BlockPos pos, Block kind, BooleanProperty property, boolean value) {
        List<BlockPos> all = window(level, pos, kind);
        Map<BlockPos, Integer> dist = new HashMap<>();
        dist.put(pos.immutable(), 0);
        for (BlockPos at : all) {
            // (the window comes nearest first, a step at a time, so each block's distance is one more than a neighbour's)
            if (dist.containsKey(at)) continue;
            int best = Integer.MAX_VALUE;
            for (Direction d : Direction.values()) {
                Integer n = dist.get(at.relative(d));
                if (n != null) best = Math.min(best, n + 1);
            }
            dist.put(at, best == Integer.MAX_VALUE ? 0 : best);
        }
        long stamp = ++ripples;
        int changing = 0;
        for (BlockPos at : all) {
            boolean already = level.getBlockState(at).getValue(property) == value;
            if (already && !HEADING.containsKey(at)) continue;
            STAMPS.put(at, stamp);
            HEADING.put(at, value);
            changing++;
            Runnable step = () -> {
                if (!Long.valueOf(stamp).equals(STAMPS.get(at))) return;
                STAMPS.remove(at);
                HEADING.remove(at);
                BlockState s = level.getBlockState(at);
                if (!s.is(kind) || s.getValue(property) == value) return;
                // (no neighbour updates: the window was told what to be as one)
                level.setBlock(at, s.setValue(property, value), 2);
            };
            int d = dist.getOrDefault(at, 0);
            if (d == 0) step.run();
            else Scheduler.after(level.getServer(), d * RIPPLE_TICKS, step);
        }
        if (changing > 0) level.playSound(null, pos, value ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 0.9F, value ? 1.5F : 0.8F);
        return changing;
    }

    /** Forgets every ripple on its way (a world closing). */
    public static void forget() {
        STAMPS.clear();
        HEADING.clear();
    }

    // ---- the kinds --------------------------------------------------------------------------------------------------------

    /** Glass that connects: faces between two blocks of it are not drawn, and light and sky pass through. */
    public static class Pane extends ConnectedBlock {
        public Pane(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
        }

        @Override
        protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1.0F;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState state) {
            return true;
        }
    }

    /** Glass whose look switches, the change rippling through its window: smart glass and veil glass. */
    public abstract static class Switching extends Pane {
        public static final BooleanProperty CLEAR = BooleanProperty.create("clear"), POWERED = BlockStateProperties.POWERED;

        protected Switching(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(CLEAR, false).setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(CLEAR, POWERED);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            // (set into a window, it takes on that window's look)
            for (Direction d : Direction.values()) {
                BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
                if (n.is(this)) return state.setValue(CLEAR, n.getValue(CLEAR)).setValue(POWERED, n.getValue(POWERED));
            }
            return state;
        }

        /** True if any block of this window has a redstone signal. */
        public boolean powered(Level level, BlockPos pos) {
            for (BlockPos at : window(level, pos, this)) if (level.hasNeighborSignal(at)) return true;
            return false;
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
            if (level instanceof ServerLevel server) refresh(server, pos);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server && !old.is(this)) refresh(server, pos);
        }

        /**
         * The signal round the window has come or gone: every block learns it at once, and the look follows in a ripple
         * from here. @return whether the window now has a signal
         */
        public boolean refresh(ServerLevel level, BlockPos pos) {
            boolean powered = powered(level, pos);
            if (level.getBlockState(pos).getValue(POWERED) == powered) return powered;
            for (BlockPos at : window(level, pos, this)) {
                BlockState s = level.getBlockState(at);
                if (s.getValue(POWERED) != powered) level.setBlock(at, s.setValue(POWERED, powered), 2);
            }
            ripple(level, pos, this, CLEAR, powered);
            return powered;
        }
    }

    /** Glass that is shaded until it has a redstone signal, then clear. */
    public static class Smart extends Switching {
        public Smart(Properties properties) {
            super(properties);
        }
    }

    /**
     * Veil glass: a solid-looking pane, untinted, that turns clear on a redstone signal - or when anything shot or thrown
     * strikes it, which turns it the other way from however it stands.
     */
    public static class Veil extends Switching {
        public Veil(Properties properties) {
            super(properties);
        }

        @Override
        protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
            if (level instanceof ServerLevel server) {
                Boolean going = heading(hit.getBlockPos());
                boolean now = going != null ? going : state.getValue(CLEAR);
                ripple(server, hit.getBlockPos(), this, CLEAR, !now);
            }
        }
    }

    /**
     * One-way glass: a solid pane from the side it faces, nothing at all from behind. Where a run of it steps (a curved
     * wall, a stair), the steps' sides are drawn too, so no gaps show from the front; between two blocks of it nothing is.
     * Set it down and its face is turned towards you.
     */
    public static class OneWay extends ConnectedBlock {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

        public OneWay(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(FACING);
        }

        private boolean same(BlockGetter level, BlockPos pos, BlockState state) {
            BlockState there = level.getBlockState(pos);
            return there.is(this) && there.getValue(FACING) == state.getValue(FACING);
        }

        @Override
        public BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
            for (Direction d : Direction.values()) state = state.setValue(side(d), same(level, pos.relative(d), state));
            return state;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return joined(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite()));
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            return state.setValue(side(direction), neighborState.is(this) && neighborState.getValue(FACING) == state.getValue(FACING));
        }

        @Override
        protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
        }

        @Override
        protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1.0F;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState state) {
            return true;
        }
    }
}
