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
            com.thefarawaydev.realmsofchaos.fx.RocSounds.at(serverLevel, pos, "oracle_speak", 1.2F, 1.0F);
            if (Story.Chapter.HUB.unlocked(sp)) Story.give(sp, Story.Chapter.HUB);
            // the first time someone comes to the flame, the Oracle gives them its music
            var data = com.thefarawaydev.realmsofchaos.world.ProgressData.get(serverLevel.getServer());
            if (!data.isBuilt("oracle_disc/" + sp.getUUID())) {
                data.markBuilt("oracle_disc/" + sp.getUUID());
                ItemStack disc = new ItemStack(com.thefarawaydev.realmsofchaos.ModContent.disc("oracle"));
                if (!sp.getInventory().add(disc)) sp.spawnAtLocation(serverLevel, disc);
            }
            com.thefarawaydev.realmsofchaos.ui.Screens.show(sp, com.thefarawaydev.realmsofchaos.ui.Screens.chronicle(sp));
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
        level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP.type(), x + (random.nextDouble() - 0.5) * 0.4, y + 0.05, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), x + (random.nextDouble() - 0.5) * 3.0, y + 1.5, z + (random.nextDouble() - 0.5) * 3.0, 0, -0.8, 0);
        }
        if (random.nextInt(40) == 0) {
            com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_oracle", 1.0F, 0.5F);
        }
    }
}
