package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ui.Screens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * The two stations that stand in the Hub of Realms and open a screen: the Heart of the Hub (the hub's tier, what
 * the next one asks for, and the button that hands it over) and the Bounty Board (today's bounties).
 */
public class HubStationBlock extends Block {
    public enum Kind { HEART, BOUNTIES }

    private final Kind kind;

    public HubStationBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            Screens.show(sp, kind == Kind.HEART ? Screens.hubCommand(sp) : Screens.bounties(sp));
        }
        return InteractionResult.SUCCESS;
    }

    /** Works with anything in hand - a player comes here carrying what the hub asks for. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind != Kind.HEART) return;
        double a = random.nextDouble() * Math.PI * 2;
        level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), pos.getX() + 0.5 + Math.cos(a) * 1.4, pos.getY() + 1.6, pos.getZ() + 0.5 + Math.sin(a) * 1.4, -Math.cos(a), -0.6, -Math.sin(a));
        if (random.nextInt(3) == 0) level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.04, 0);
    }
}
