package com.thefarawaydev.realmsofchaos.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
 * The mod's glass. Every kind connects: a run of it is one great window with a single frame round its outside.
 *
 * <ul>
 * <li><b>Reinforced</b> - clear, framed in bronze, and as hard to break as stone.
 * <li><b>Frosted</b> - milky: light through it, no faces.
 * <li><b>Smart glass</b> - shaded dark until it has a redstone signal, then clear; a whole connected window changes at once.
 * <li><b>Brittle glass</b> - struck by an arrow, a snowball, anything thrown or shot, the whole window it belongs to lets go
 *     and comes down like sand.
 * <li><b>One-way glass</b> - a mirror from the side it faces, clear as air from the other.
 * </ul>
 */
public final class Glass {
    private Glass() {}

    /** The most blocks of one window that change (or fall) together. */
    public static final int MAX_WINDOW = 512;

    /** Every block of one window: this block and all of the same kind joined to it, face to face. */
    public static List<BlockPos> window(Level level, BlockPos start, Block kind) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> next = new ArrayDeque<>();
        next.add(start.immutable());
        seen.add(start.immutable());
        while (!next.isEmpty() && out.size() < MAX_WINDOW) {
            BlockPos at = next.poll();
            if (!level.getBlockState(at).is(kind)) continue;
            out.add(at);
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (seen.add(n) && level.getBlockState(n).is(kind)) next.add(n);
            }
        }
        return out;
    }

    /** Clear glass that connects: faces between two blocks of it are not drawn, and light and sky pass through. */
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

    /** Glass that is shaded until it has a redstone signal: then the whole window it belongs to clears. */
    public static class Smart extends Pane {
        public static final BooleanProperty CLEAR = BooleanProperty.create("clear");

        public Smart(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(CLEAR, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(CLEAR);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            return state.setValue(CLEAR, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
            if (!level.isClientSide()) refresh(level, pos);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!level.isClientSide() && !old.is(this)) refresh(level, pos);
        }

        /** Works out whether any block of this window has a signal, and sets the whole window to match. @return clear or not */
        public boolean refresh(Level level, BlockPos pos) {
            List<BlockPos> all = window(level, pos, this);
            boolean powered = false;
            for (BlockPos at : all) {
                if (level.hasNeighborSignal(at)) {
                    powered = true;
                    break;
                }
            }
            boolean changed = false;
            for (BlockPos at : all) {
                BlockState s = level.getBlockState(at);
                if (s.getValue(CLEAR) != powered) {
                    // (no neighbour updates: the window decides as one, not block by block)
                    level.setBlock(at, s.setValue(CLEAR, powered), 2);
                    changed = true;
                }
            }
            if (changed) level.playSound(null, pos, powered ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 0.8F, powered ? 1.6F : 0.8F);
            return powered;
        }
    }

    /** Glass that lets go of everything when anything shot or thrown strikes it: the whole window falls like sand. */
    public static class Brittle extends Pane {
        public Brittle(Properties properties) {
            super(properties);
        }

        @Override
        protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
            if (level instanceof ServerLevel server) collapse(server, hit.getBlockPos());
        }

        /** The window gives way: every block of it falls, and lands as it was. @return how many fell */
        public int collapse(ServerLevel level, BlockPos pos) {
            List<BlockPos> all = window(level, pos, this);
            // the lowest first, so nothing is left hanging on a block that has already gone
            all.sort(java.util.Comparator.comparingInt(BlockPos::getY));
            for (BlockPos at : all) {
                BlockState s = level.getBlockState(at);
                for (Direction d : Direction.values()) s = s.setValue(side(d), false);
                FallingBlockEntity.fall(level, at, s);
            }
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2F, 0.7F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.6, 0.6, 0.6, 0.1);
            return all.size();
        }
    }

    /**
     * One-way glass: drawn only on the side it faces, as a silvered mirror. Look at it from that side and it is a
     * mirror; from behind, there is nothing to see but what lies beyond. Set it down and the mirror faces away from
     * you. It joins to its own kind facing the same way.
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
            return joined(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(FACING, context.getNearestLookingDirection()));
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            return state.setValue(side(direction), neighborState.is(this) && neighborState.getValue(FACING) == state.getValue(FACING));
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
