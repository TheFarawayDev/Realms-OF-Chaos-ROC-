package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tower's brazier: a bowl on a foot, burning with that tower's own fire - storm-light under Zeus, sea-fire under
 * Poseidon, soul-fire under Hades. It only gives light and looks the part.
 */
public class BrazierBlock extends Block {
    /** 0 Zeus, 1 Poseidon, 2 Hades. */
    public static final IntegerProperty TOWER = IntegerProperty.create("tower", 0, 2);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(TOWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOWER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        var flame = switch (state.getValue(TOWER)) {
            case 0 -> ParticleTypes.ELECTRIC_SPARK;
            case 1 -> ParticleTypes.GLOW;
            default -> ParticleTypes.SOUL_FIRE_FLAME;
        };
        level.addParticle(flame, pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.85, pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0, 0.03, 0);
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0.02, 0);
    }
}
