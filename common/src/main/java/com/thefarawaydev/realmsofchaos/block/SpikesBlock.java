package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A cluster of iron spikes thrust up through a spike hall's floor. Its hall does the hurting; it only stands there. */
public class SpikesBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public SpikesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
