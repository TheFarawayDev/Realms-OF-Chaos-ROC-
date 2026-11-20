package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.SealMenu;
import com.thefarawaydev.realmsofchaos.world.Passages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
 * A seal on a passage's gate. It cannot be mined: right-click it to strike it apart - but only once the guardians
 * of its chamber have fallen.
 */
public class SealBlock extends Block {
    public SealBlock(Properties properties) {
        super(properties);
    }

    /** @return true if the strike screen was opened; false while guardians still stand. */
    public static boolean tryOpen(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Passages.Plan plan = Passages.planAt(level, pos);
        int tier = 1;
        if (plan != null) {
            tier = plan.tier;
            Passages.Cell cell = plan.cellAt(pos);
            if (cell != null) {
                Passages.wake(level, plan, cell);           // in case the player got here faster than the chamber noticed
                int standing = Passages.guardians(level, plan, cell).size();
                if (standing > 0) {
                    Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".passage.guarded", standing), 0xFF5555));
                    return false;
                }
            }
        }
        SealMenu.open(player, pos, tier);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) tryOpen(serverLevel, pos, sp);
        return InteractionResult.SUCCESS;
    }

    /** Works with anything in hand - a player arrives here holding a weapon. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(a) * 1.2, pos.getY() + 0.3 + random.nextDouble(), pos.getZ() + 0.5 + Math.sin(a) * 1.2,
                    -Math.cos(a) * 0.8, 0.1, -Math.sin(a) * 0.8);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.03, 0);
        }
    }
}
