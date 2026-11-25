package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The other two parts of the shrine of plenty: the plinth the Cornucopia stands on, and the columns round it. */
public final class PlentyBlocks {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private PlentyBlocks() {}

    /** The Plinth of Plenty: an altar of porphyry and gold. It glows when the shrine it belongs to is whole. */
    public static class Plinth extends Block {
        public Plinth(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }
    }

    /**
     * A Column of Plenty: fluted porphyry banded in gold, its inlay lit while its shrine is whole. Stacked, the lowest
     * wears the base and the highest the capital.
     */
    public static class Column extends Block {
        public static final BooleanProperty BASE = BooleanProperty.create("base"), CAPITAL = BooleanProperty.create("capital");
        private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 16, 13));

        public Column(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(BASE, true).setValue(CAPITAL, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT, BASE, CAPITAL);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockGetter level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            return defaultBlockState().setValue(BASE, !level.getBlockState(pos.below()).is(this)).setValue(CAPITAL, !level.getBlockState(pos.above()).is(this));
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            if (direction == Direction.DOWN) return state.setValue(BASE, !neighborState.is(this));
            if (direction == Direction.UP) return state.setValue(CAPITAL, !neighborState.is(this));
            return state;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(BASE) || state.getValue(CAPITAL) ? Shapes.block() : SHAPE;
        }
    }
}
