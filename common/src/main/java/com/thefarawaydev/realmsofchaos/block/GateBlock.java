package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A gate: walk into it (or right-click it) to travel to {@link #destination()}. */
public class GateBlock extends Block {
    private final Realm destination;

    public GateBlock(Realm destination, Properties properties) {
        super(properties);
        this.destination = destination;
    }

    public Realm destination() {
        return destination;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            Teleports.travel(player, destination);
        }
    }

    /** Client-side ambience: motes drift out of the gate in its realm's colour. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ParticleOptions particle = switch (destination) {
            case HUB -> ParticleTypes.PORTAL;
            case OLYMPUS -> ParticleTypes.ELECTRIC_SPARK;
            case ABYSS -> ParticleTypes.BUBBLE_POP;
            case UNDERWORLD -> ParticleTypes.SOUL_FIRE_FLAME;
        };
        for (int i = 0; i < 2; i++) {
            level.addParticle(particle, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            Teleports.travel(sp, destination);
        }
        return InteractionResult.SUCCESS;
    }
}
