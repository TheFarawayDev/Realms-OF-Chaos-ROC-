package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;

/**
 * The resources that are won from the world itself rather than from the gods. Here, fulgurite: where lightning strikes
 * sand, the sand fuses into glassy tubes - so a lightning rod set on sand in a thunderstorm, or a trident of
 * channelling, is how storm glass is farmed.
 */
public final class Grind {
    /** The chance each block of sand near a strike has of fusing. */
    public static final float FUSE_CHANCE = 0.6F;

    private Grind() {}

    /** Called by each loader as an entity joins a server level. */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (entity instanceof LightningBolt bolt) fuse(level, bolt.blockPosition(), level.getRandom().nextLong());
    }

    /**
     * Fuses the sand round a lightning strike into fulgurite: in the block struck and the two below it, a block out to
     * each side - under a lightning rod, the sand it stands on. @return how many blocks fused
     */
    public static int fuse(ServerLevel level, BlockPos strike, long seed) {
        java.util.Random random = new java.util.Random(seed);
        int fused = 0;
        for (BlockPos at : BlockPos.betweenClosed(strike.offset(-1, -2, -1), strike.offset(1, 0, 1))) {
            var state = level.getBlockState(at);
            if (!state.is(BlockTags.SAND) || state.is(Blocks.SUSPICIOUS_SAND)) continue;
            if (random.nextFloat() >= FUSE_CHANCE) continue;
            level.setBlock(at, ModContent.FULGURITE.get().defaultBlockState(), 3);
            fused++;
        }
        if (fused > 0) level.playSound(null, strike, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        return fused;
    }
}
