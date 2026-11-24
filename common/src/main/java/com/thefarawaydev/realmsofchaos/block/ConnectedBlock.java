package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

/**
 * A block with connecting textures: it knows which of its six neighbours are the same block, and each of its faces
 * drops its frame along the edges where it meets one. Laid side by side, a run of them reads as one great panel
 * with a single frame round its outside.
 */
public class ConnectedBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST, UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;

    public ConnectedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /** This block, joined to whichever of its neighbours are the same block. */
    public BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) state = state.setValue(side(d), level.getBlockState(pos.relative(d)).is(this));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(side(direction), neighborState.is(this));
    }

    /**
     * Joins up every connecting block in a box. Builders set blocks without asking each how it should look, so a
     * structure that uses these calls this once it stands.
     */
    public static void settle(ServerLevel level, BlockPos from, BlockPos to) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                for (int y = from.getY(); y <= to.getY(); y++) {
                    at.set(x, y, z);
                    BlockState state = level.getBlockState(at);
                    if (state.getBlock() instanceof ConnectedBlock block) {
                        BlockState joined = block.joined(level, at, state);
                        if (joined != state) level.setBlock(at, joined, 2 | 16);
                    }
                }
            }
        }
    }
}
