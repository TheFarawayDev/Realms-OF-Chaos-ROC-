package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The pedestal before a Ruined Gate. Right-click it holding a keystone to set that keystone in the arch; with
 * anything else in hand it says which keystones the gate is still missing. Three keystones wake the gate.
 */
public class GatePedestalBlock extends Block {
    public GatePedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            Ruins.usePedestal(serverLevel, pos, sp, sp.getMainHandItem());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0, pos.getY() + 1.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0, 0, -0.5, 0);
        }
    }
}
