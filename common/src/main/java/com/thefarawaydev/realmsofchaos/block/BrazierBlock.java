package com.thefarawaydev.realmsofchaos.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tower's brazier, burning with that tower's own fire - storm-light under Zeus, sea-fire under Poseidon, soul-fire
 * under Hades - in its tower's metal. It comes three ways: a low bowl on a foot, a tall brazier standing on four legs,
 * and a bowl held out from a wall on an iron bracket. Set down on a floor it stands; set against a wall it hangs there.
 */
public class BrazierBlock extends Block {
    public enum Mount implements StringRepresentable {
        FLOOR, STANDING, WALL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 0 Zeus, 1 Poseidon, 2 Hades. */
    public static final IntegerProperty TOWER = IntegerProperty.create("tower", 0, 2);
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
    /** For a wall brazier, the way it faces out from its wall. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape FLOOR = Block.box(2, 0, 2, 14, 7, 14), STANDING = Shapes.or(Block.box(2, 10, 2, 14, 15, 14), Block.box(4, 0, 4, 12, 10, 12));
    private static final java.util.Map<Direction, VoxelShape> WALL = java.util.Map.of(
            Direction.NORTH, Block.box(3, 3, 3, 13, 11, 16), Direction.SOUTH, Block.box(3, 3, 0, 13, 11, 13),
            Direction.EAST, Block.box(0, 3, 3, 13, 11, 13), Direction.WEST, Block.box(3, 3, 3, 16, 11, 13));

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(TOWER, 0).setValue(MOUNT, Mount.FLOOR).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOWER, MOUNT, FACING);
    }

    /** A brazier of a tower, mounted one of the three ways ({@code facing} matters only on a wall). */
    public BlockState of(int tower, Mount mount, Direction facing) {
        return defaultBlockState().setValue(TOWER, tower).setValue(MOUNT, mount).setValue(FACING, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) return defaultBlockState().setValue(MOUNT, Mount.WALL).setValue(FACING, face);
        return defaultBlockState().setValue(MOUNT, Mount.STANDING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> FLOOR;
            case STANDING -> STANDING;
            case WALL -> WALL.get(state.getValue(FACING));
        };
    }

    /** Where the fire burns, in the block. */
    private static double[] fire(BlockState state) {
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> new double[]{0.5, 0.5, 0.5};
            case STANDING -> new double[]{0.5, 0.98, 0.5};
            case WALL -> new double[]{0.5, 0.72, 0.5};
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        var flame = com.thefarawaydev.realmsofchaos.fx.RocParticles.flame(state.getValue(TOWER)).type();
        com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_fire", 0.03F, 0.4F);
        double[] at = fire(state);
        if (state.getValue(MOUNT) == Mount.WALL) {
            Direction f = state.getValue(FACING);
            at[0] = 0.5 + 0.06 * f.getStepX();
            at[2] = 0.5 + 0.06 * f.getStepZ();
        }
        level.addParticle(flame, pos.getX() + at[0] - 0.15 + random.nextDouble() * 0.3, pos.getY() + at[1], pos.getZ() + at[2] - 0.15 + random.nextDouble() * 0.3, 0, 0.03, 0);
        if (random.nextInt(4) == 0) level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), pos.getX() + at[0], pos.getY() + at[1] + 0.2, pos.getZ() + at[2], 0, 0.02, 0);
    }
}
