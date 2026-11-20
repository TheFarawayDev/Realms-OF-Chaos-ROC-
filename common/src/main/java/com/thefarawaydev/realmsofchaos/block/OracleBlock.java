package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.ui.StoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * The Oracle's Flame, at the heart of the hub. Right-click it to open the Oracle's Chronicle: the story so far, and
 * what to do next. The first time, the Oracle hands over the chapter about the hub itself.
 */
public class OracleBlock extends Block {
    public OracleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.6F);
            if (Story.Chapter.HUB.unlocked(sp)) Story.give(sp, Story.Chapter.HUB);
            StoryMenu.open(sp);
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
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.4, y + 0.05, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 3.0, y + 1.5, z + (random.nextDouble() - 0.5) * 3.0, 0, -0.8, 0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.5F + random.nextFloat() * 0.3F, false);
        }
    }
}
