package com.thefarawaydev.realmsofchaos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Impacts: the ground answering a heavy blow. Chunks of whatever the floor is made of are thrown into the air,
 * dust bursts from it and it thuds. The chunks are only for show - they are flung up as falling blocks that vanish
 * when they land, so nothing is ever added to or taken from the world.
 */
public final class Fx {
    /** The most chunks one impact throws, however heavy it is. */
    public static final int MAX_CHUNKS = 8;

    private Fx() {}

    /** The solid block a thing at {@code pos} is standing on, looking a couple of blocks down; stone if there is none. */
    public static BlockState ground(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos p = pos.below(dy);
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, p) && !state.hasBlockEntity() && state.getFluidState().isEmpty()) return state;
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** Moves an entity to a place in its own level, at once - a player through the proper teleport, anything else directly. */
    public static void place(ServerLevel level, net.minecraft.world.entity.Entity entity, double x, double y, double z) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.teleportTo(level, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        } else {
            entity.teleportTo(x, y, z);
        }
    }

    /** Lifts an entity a block at a time until it is clear of the blocks around it (at most {@code most} blocks). */
    public static void unbury(ServerLevel level, net.minecraft.world.entity.Entity entity, int most) {
        for (int i = 0; i < most && !level.noCollision(entity); i++) place(level, entity, entity.getX(), entity.getY() + 1.0, entity.getZ());
    }

    /**
     * An impact at {@code at}. @param power 1 (a heavy hit) to 8 (the floor erupting): how many chunks fly and how
     * high. @return how many chunks were thrown.
     */
    public static int impact(ServerLevel level, Vec3 at, int power) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 0.2, at.z);
        BlockState ground = ground(level, pos);
        RandomSource random = level.getRandom();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 12 + power * 6, 0.5, 0.2, 0.5, 0.15);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), at.x, at.y + 0.1, at.z, 2 + power / 2, 0.4, 0.05, 0.4, 0.01);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.25F + power * 0.06F, 0.6F + random.nextFloat() * 0.3F);
        int thrown = 0;
        for (int i = 0; i < Math.min(MAX_CHUNKS, power); i++) {
            BlockPos from = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            // only out of open air just above the floor: a chunk never replaces a block
            if (!level.getBlockState(from).isAir()) continue;
            FallingBlockEntity chunk = FallingBlockEntity.fall(level, from, ground);
            chunk.dropItem = false;
            chunk.disableDrop();
            chunk.setDeltaMovement((random.nextDouble() - 0.5) * 0.5, 0.35 + random.nextDouble() * 0.12 * Math.min(6, power), (random.nextDouble() - 0.5) * 0.5);
            chunk.needsSync = true;
            thrown++;
        }
        return thrown;
    }
}
