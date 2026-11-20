package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ui.ForgeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Forge of Chaos: right-click it holding a relic weapon to see its level and forge stars onto it. */
public class ForgeBlock extends Block {
    public ForgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F);
            ForgeMenu.open(sp, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The forge is opened with a weapon in hand, so it must answer item clicks as well as empty-handed ones. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** Client-side ambience: the fire in the basin burns, spits and smokes, and runes drift in to be forged. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.5, y + 0.05, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 0.3, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 2.4, y + 1.2, z + (random.nextDouble() - 0.5) * 2.4, 0, -0.7, 0);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.LAVA, x, y + 0.1, z, 0, 0, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.7F + random.nextFloat() * 0.3F, false);
        }
    }
}
