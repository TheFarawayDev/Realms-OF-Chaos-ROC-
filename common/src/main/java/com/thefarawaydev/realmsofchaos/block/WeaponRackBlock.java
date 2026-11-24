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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A weapon rack hung on a wall: a board of dark oak, two rails across it, and on them one of a spear, a sword, a
 * shield or a pair of crossed axes - which, the game chooses for each block. Racks set side by side or one over
 * another join up: the end posts and the cap stand only where a run of racks ends.
 */
public class WeaponRackBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Whether a rack of the same facing is on its left, its right, above or below - as seen from in front of it. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right"),
            UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
    private static final VoxelShape NORTH = Block.box(0, 0, 10, 16, 16, 16), SOUTH = Block.box(0, 0, 0, 16, 16, 6),
            EAST = Block.box(0, 0, 0, 6, 16, 16), WEST = Block.box(10, 0, 0, 16, 16, 16);

    public WeaponRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    private boolean same(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState there = level.getBlockState(pos);
        return there.is(this) && there.getValue(FACING) == facing;
    }

    /** This rack, joined to the racks about it that face the same way. */
    public BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        return state.setValue(LEFT, same(level, pos.relative(f.getClockWise()), f)).setValue(RIGHT, same(level, pos.relative(f.getCounterClockWise()), f))
                .setValue(UP, same(level, pos.above(), f)).setValue(DOWN, same(level, pos.below(), f));
    }

    /** A rack facing {@code facing}, ready to be joined. */
    public BlockState facing(Direction facing) {
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return joined(context.getLevel(), context.getClickedPos(), facing(context.getHorizontalDirection().getOpposite()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return joined(level, pos, state);
    }

    /** Joins every rack in a box: builders set blocks without asking each how it should look. */
    public static void settle(ServerLevel level, BlockPos from, BlockPos to) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                for (int y = from.getY(); y <= to.getY(); y++) {
                    at.set(x, y, z);
                    BlockState state = level.getBlockState(at);
                    if (state.getBlock() instanceof WeaponRackBlock rack) {
                        BlockState joined = rack.joined(level, at, state);
                        if (joined != state) level.setBlock(at, joined, 2 | 16);
                    }
                }
            }
        }
    }
}
