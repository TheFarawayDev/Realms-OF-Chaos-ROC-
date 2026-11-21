package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shield pylon: one of the pillars a god raises round the floor when it puts up its aegis. While any of them
 * stands, nothing can hurt the god. It cannot be mined - right-click it to strike it, and after a few blows it
 * breaks. A pylon no god owns any more (its god is gone) falls apart at a touch.
 */
public class ShieldPylonBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public ShieldPylonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** One blow. @return true if a god's pylon was struck, false if this one was nobody's and simply crumbled. */
    public static boolean strike(ServerLevel level, BlockPos pos, Player player) {
        for (GodBoss boss : level.getEntitiesOfClass(GodBoss.class, new AABB(pos).inflate(64), b -> b.isAlive() && b.ownsPylon(pos))) {
            return boss.strikePylon(level, pos, player);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.2, 0.4, 0.2, 0.02);
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) strike(serverLevel, pos, player);
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
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.05, 0);
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(a), pos.getY() + 1.4, pos.getZ() + 0.5 + Math.sin(a), -Math.cos(a), -0.4, -Math.sin(a));
        }
    }
}
