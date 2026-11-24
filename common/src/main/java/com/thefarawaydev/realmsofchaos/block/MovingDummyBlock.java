package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sparring dummy on a sledge: while anyone is near it slides to and fro along the floor, a block at a time, the way
 * it faces - and turns back where the way is blocked or its floor ends (it keeps to floor of the block it stands on). Hitting a target that will not stand still
 * is the point. It takes blows and teaches like any other dummy.
 */
public class MovingDummyBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Ticks between one slide and the next, and how near a player must be for it to move at all. */
    public static final int PAUSE = 14, IDLE = 40;
    public static final double WAKE_RANGE = 20.0;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public MovingDummyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // it starts off along the way the placer looks, across their view
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, PAUSE);
    }

    /**
     * True if the dummy could slide one block that way: open air, over a floor of the same block as the one it stands
     * on now - it keeps to its own boards, so a strip of floor is its track.
     */
    public static boolean canSlide(Level level, BlockPos pos, Direction d) {
        BlockPos to = pos.relative(d);
        BlockState floor = level.getBlockState(to.below());
        return level.getBlockState(to).isAir() && floor.isFaceSturdy(level, to.below(), Direction.UP) && floor.getBlock() == level.getBlockState(pos.below()).getBlock();
    }

    /**
     * Which way the next step goes: the way it faces, or - where that way is shut - the other way, turning round.
     * @return the way, or null if neither way is open
     */
    public static Direction way(Level level, BlockPos pos, BlockState state) {
        Direction d = state.getValue(FACING);
        if (canSlide(level, pos, d)) return d;
        return canSlide(level, pos, d.getOpposite()) ? d.getOpposite() : null;
    }

    /** One step, on the server: turns if it must, then slides. @return where it now stands (or is sliding to) */
    public static BlockPos step(ServerLevel level, BlockPos pos, BlockState state) {
        Direction d = way(level, pos, state);
        if (d == null) return pos;
        slide(level, pos, state.setValue(FACING, d), d);
        return pos.relative(d);
    }

    /**
     * The slide itself, as a piston moves a block: it glides into the next block over two ticks and sets itself down
     * there. Run on the server and on every client watching (through a block event), so that it is seen to glide.
     */
    static void slide(Level level, BlockPos pos, BlockState state, Direction d) {
        BlockPos to = pos.relative(d);
        BlockState moving = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, d).setValue(MovingPistonBlock.TYPE, PistonType.DEFAULT);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(to, moving, 2 | 16 | 64);
        level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(to, moving, state, d, true, false));
        if (!level.isClientSide()) level.playSound(null, to, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction d = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WAKE_RANGE, false) == null ? null : way(level, pos, state);
        if (d == null) {
            level.scheduleTick(pos, this, IDLE);
            return;
        }
        // (a block event: the clients slide it too, and see it glide rather than vanish and reappear)
        level.blockEvent(pos, this, 0, d.get3DDataValue());
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction d = Direction.from3DDataValue(param);
        if (!state.is(this) || d.getAxis().isVertical()) return false;
        slide(level, pos, state.setValue(FACING, d), d);
        return true;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) TrainingDummyBlock.struck(server, pos, sp);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) TrainingDummyBlock.lesson(sp, level, pos);
        return InteractionResult.SUCCESS;
    }
}
