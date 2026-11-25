package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sprinkler: a bronze rose hung from a ceiling on a short pipe. A rune chamber's sprinklers pour while it floods (the
 * room does the pouring - see {@code Traps}); a sprinkler set anywhere else is only a fitting.
 */
public class SprinklerBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 8, 4, 12, 16, 12);

    public SprinklerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
