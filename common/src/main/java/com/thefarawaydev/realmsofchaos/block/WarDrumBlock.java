package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Passages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A war drum: a bronze-hooped drum of stretched hide. Beat it and it booms. In a passage's proving ground it is
 * the call to the trial: the beast gates open, and the waves come.
 */
public class WarDrumBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public WarDrumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** One beat: a boom, and a ring of dust off the hide. */
    public static void beat(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.2F, 0.7F);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.DUST.type(), pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 6, 0.3, 0.02, 0.3, 0.01);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            beat(server, pos);
            Passages.drum(server, pos, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }
}
