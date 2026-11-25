package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Passages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The treasure of a passage: a banded chest in its tower's wood and metal, a gold lock on its front, a domed lid. It is
 * not a chest that the first comer empties: every player who right-clicks it gets their own share, once - and its lid
 * swings open for a moment as they do.
 */
public class CacheBlock extends Block {
    /** 0 Zeus, 1 Poseidon, 2 Hades. */
    public static final IntegerProperty TOWER = IntegerProperty.create("tower", 0, 2);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public CacheBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOWER, 0).setValue(FACING, Direction.SOUTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOWER, FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp && Passages.loot(serverLevel, pos, sp)) lift(serverLevel, pos);
        return InteractionResult.SUCCESS;
    }

    /** The lid swings up, and a couple of seconds later falls shut. */
    public static void lift(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CacheBlock)) return;
        level.setBlock(pos, state.setValue(OPEN, true), 3);
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), 40, () -> {
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() instanceof CacheBlock && now.getValue(OPEN)) {
                level.setBlock(pos, now.setValue(OPEN, false), 3);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.9F);
            }
        });
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.95, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
        }
        if (state.getValue(OPEN) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0, 0.05, 0);
        }
    }
}
