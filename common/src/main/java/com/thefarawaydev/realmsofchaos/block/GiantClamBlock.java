package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A giant clam: set it under the sea, deep in an ocean, and in time it grows an abyssal pearl. Open it then and the
 * pearl is yours; it begins another. Out of water, or in shallow water, or anywhere but an ocean, it grows nothing.
 */
public class GiantClamBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PEARL = BooleanProperty.create("pearl");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** How deep a clam must lie to grow (no higher than this), and the chance a random tick grows its pearl. */
    public static final int MAX_Y = 50;
    public static final float GROW_CHANCE = 1.0F / 12.0F;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

    public GiantClamBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PEARL, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PEARL, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(PEARL);
    }

    /** True if a clam here can grow: under water, deep, in an ocean. */
    public static boolean canGrow(Level level, BlockPos pos, BlockState state) {
        return state.getValue(WATERLOGGED) && pos.getY() <= MAX_Y && level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PEARL) && canGrow(level, pos, state) && random.nextFloat() < GROW_CHANCE) level.setBlock(pos, state.setValue(PEARL, true), 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(PEARL)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            Block.popResource(server, pos.above(), new ItemStack(ModContent.ABYSSAL_PEARL.get()));
            server.setBlock(pos, state.setValue(PEARL, false), 2);
            server.playSound(null, pos, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        return InteractionResult.SUCCESS;
    }
}
