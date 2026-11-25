package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A gate: walk into it (or right-click it) to travel. What it does depends on its {@link Kind}. */
public class GateBlock extends Block {
    public enum Kind {
        /** Back to the Hub of Realms. */
        HUB,
        /** Hub gates into the towers: they take you to the floor you are working on. */
        ZEUS, POSEIDON, HADES,
        /** Inside a tower: up one floor, once this floor's god is defeated. */
        ASCEND,
        /** In the hub: out of the realms, back to where you came from. */
        LEAVE
    }

    private final Kind kind;

    public GateBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private void use(ServerPlayer player) {
        switch (kind) {
            case HUB -> {
                // a gate to the hub standing outside the realms is a woken Ruined Gate: walking through it attunes
                if (com.thefarawaydev.realmsofchaos.world.Realm.of(player.level().dimension()) == null) {
                    com.thefarawaydev.realmsofchaos.world.Access.attune(player);
                }
                Teleports.toHub(player);
            }
            case ZEUS -> Teleports.enterTower(player, Line.ZEUS);
            case POSEIDON -> Teleports.enterTower(player, Line.POSEIDON);
            case HADES -> Teleports.enterTower(player, Line.HADES);
            case ASCEND -> Teleports.ascend(player);
            case LEAVE -> Teleports.leave(player);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            use(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            use(sp);
        }
        return InteractionResult.SUCCESS;
    }

    /** Client-side ambience: motes drift out of the gate in its own colour. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ParticleOptions particle = switch (kind) {
            case HUB -> com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type();
            case ZEUS -> com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK.type();
            case POSEIDON -> ParticleTypes.BUBBLE_POP;
            case HADES -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP.type();
            case ASCEND -> com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type();
            case LEAVE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type();
        };
        for (int i = 0; i < 2; i++) {
            level.addParticle(particle, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.3);
        }
    }
}
