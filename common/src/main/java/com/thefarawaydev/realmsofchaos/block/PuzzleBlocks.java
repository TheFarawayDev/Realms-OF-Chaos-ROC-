package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Rooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The blocks of the dial hall and the sliding stones. */
public final class PuzzleBlocks {
    private PuzzleBlocks() {}

    /** Which of the four sigils a dial or a fresco shows: the sun, the trident, the skull, the star. */
    public static final IntegerProperty SIGIL = IntegerProperty.create("sigil", 0, 3);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int SIGILS = 4;

    private abstract static class Used extends Block {
        Used(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return useWithoutItem(state, level, pos, player, hit);
        }
    }

    /** A dial of stone with a bronze ring, a sigil on its face. Right-click to turn it (and, in a dial hall, the next). */
    public static class Dial extends Used {
        public Dial(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(SIGIL, 0).setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIGIL, FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !Rooms.turnDial(server, pos, sp)) {
                // (a dial set anywhere else simply turns)
                if (Rooms.dialHall(server, pos) == null) server.setBlock(pos, state.setValue(SIGIL, (state.getValue(SIGIL) + 1) % SIGILS), 3);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** A fresco painted with a sigil, set in a wall: it glows when the dial below it shows the same. */
    public static class Fresco extends Block {
        public Fresco(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(SIGIL, 0).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIGIL, LIT);
        }
    }

    /** A heavy carved stone: right-click to push it a block away from you; sneak and right-click to set it back. */
    public static class SlideStone extends Used {
        public SlideStone(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) Rooms.pushStone(server, pos, sp);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
            Direction d = Direction.from3DDataValue(param);
            if (!state.is(this) || d.getAxis().isVertical()) return false;
            MovingDummyBlock.slide(level, pos, state, d);
            return true;
        }
    }

    /** A socket let into a floor: it glows when a sliding stone sits on it. */
    public static class Socket extends Block {
        public Socket(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }
    }
}
