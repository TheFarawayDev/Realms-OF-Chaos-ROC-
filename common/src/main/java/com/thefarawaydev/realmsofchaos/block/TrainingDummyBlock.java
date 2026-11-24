package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Words;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A training dummy: a sack of straw on a post, a crossbar for arms, a painted target on its chest. Strike it and it
 * takes the blow with a thump; right-click it and it teaches the two ways of not being hit - the parry and the
 * dodge. The barracks and proving grounds of the passages keep them.
 */
public class TrainingDummyBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public TrainingDummyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server)) return;
        server.playSound(null, pos, SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.3F);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                10, 0.2, 0.2, 0.2, 0.05);
        server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.1);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            String k = "hud." + RealmsOfChaos.MOD_ID + ".dummy.";
            Cards.notice(sp, Words.of(k + "parry"), Words.of(k + "parry.sub"));
            Cards.notice(sp, Words.of(k + "dodge"), Words.of(k + "dodge.sub"));
            level.playSound(null, pos, SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }
}
