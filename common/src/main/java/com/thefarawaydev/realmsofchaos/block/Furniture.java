package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Seats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The barracks' furniture, built of several pieces each: chairs and benches to sit on, tables that join into one long
 * table, a dresser two blocks wide whose drawers open, and a bunk bed two long and two high.
 */
public final class Furniture {
    private Furniture() {}

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Something that can be sat on. */
    public interface Seat {
        double seatHeight();
    }

    private static InteractionResult sitOn(Level level, BlockPos pos, Player player, double height) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) return Seats.sit(server, pos, height, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    // ---- the chair -------------------------------------------------------------------------------------------------

    /** A chair: a seat on four legs, a back behind it. It faces the way whoever set it down was looking from. */
    public static class Chair extends Block implements Seat {
        private static final VoxelShape SEAT = Block.box(2, 0, 2, 14, 9, 14);

        public Chair(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            Direction f = state.getValue(FACING);
            // the back stands on the side away from where it faces
            VoxelShape back = switch (f) {
                case NORTH -> Block.box(2, 9, 12, 14, 20, 14);
                case SOUTH -> Block.box(2, 9, 2, 14, 20, 4);
                case EAST -> Block.box(2, 9, 2, 4, 20, 14);
                default -> Block.box(12, 9, 2, 14, 20, 14);
            };
            return Shapes.or(SEAT, back);
        }

        @Override
        public double seatHeight() {
            return 0.55;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return sitOn(level, pos, player, seatHeight());
        }
    }

    // ---- tables and benches: they join to their own kind ------------------------------------------------------------

    /** A block that joins to its own kind on all four sides. */
    public abstract static class Joining extends Block {
        public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
                WEST = BlockStateProperties.WEST;

        protected Joining(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(NORTH, EAST, SOUTH, WEST);
        }

        public static BooleanProperty side(Direction d) {
            return switch (d) {
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                default -> NORTH;
            };
        }

        public BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
            for (Direction d : Direction.Plane.HORIZONTAL) state = state.setValue(side(d), level.getBlockState(pos.relative(d)).is(this));
            return state;
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            return direction.getAxis().isHorizontal() ? state.setValue(side(direction), neighborState.is(this)) : state;
        }

        /** Joins every piece in a box: builders set blocks without asking each how it should look. */
        public static void settle(ServerLevel level, BlockPos from, BlockPos to) {
            for (BlockPos at : BlockPos.betweenClosed(from, to)) {
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof Joining j) {
                    BlockState joined = j.joined(level, at, state);
                    if (joined != state) level.setBlock(at, joined, 2 | 16);
                }
            }
        }
    }

    /** A table: a board on legs. Set side by side, tables make one long table, with legs only at its corners. */
    public static class Table extends Joining {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 13, 0, 16, 16, 16), Block.box(6, 0, 6, 10, 13, 10));

        public Table(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** A bench: a long seat. Benches set end to end make one long bench; any part of it can be sat on. */
    public static class Bench extends Joining implements Seat {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

        public Bench(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public double seatHeight() {
            return 0.45;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return sitOn(level, pos, player, seatHeight());
        }
    }

    // ---- pieces of more than one block --------------------------------------------------------------------------------

    /** A dresser, two blocks wide: three drawers in each half, brass pulls, a top of dark wood. Right-click a half to pull its drawers open, or shut. */
    public static class Dresser extends Block {
        /** The left half (as seen from the front) is the one that was placed; the right is set beside it. */
        public static final BooleanProperty RIGHT = BooleanProperty.create("right"), OPEN = BlockStateProperties.OPEN;

        public Dresser(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RIGHT, false).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, RIGHT, OPEN);
        }

        /** Which way the other half lies. */
        public static Direction toPartner(BlockState state) {
            Direction f = state.getValue(FACING);
            // seen from the front, a north-facing dresser's right hand is to the west
            return state.getValue(RIGHT) ? f.getClockWise() : f.getCounterClockWise();
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
            BlockPos other = context.getClickedPos().relative(toPartner(state));
            return context.getLevel().getBlockState(other).canBeReplaced(context) ? state : null;
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!level.isClientSide()) level.setBlock(pos.relative(toPartner(state)), state.setValue(RIGHT, true), 3);
        }

        /** Builds a dresser with its left half at {@code pos}. */
        public static void build(Level level, BlockPos pos, Direction facing, Block dresser) {
            BlockState left = dresser.defaultBlockState().setValue(FACING, facing);
            level.setBlock(pos, left, 2);
            level.setBlock(pos.relative(toPartner(left)), left.setValue(RIGHT, true), 2);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            if (direction == toPartner(state) && !(neighborState.is(this) && neighborState.getValue(RIGHT) != state.getValue(RIGHT))) return Blocks.AIR.defaultBlockState();
            if (direction == toPartner(state)) return state.setValue(OPEN, neighborState.getValue(OPEN));
            return state;
        }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            // (only the left half drops the dresser: breaking the right breaks the left with it)
            BlockPos other = pos.relative(toPartner(state));
            if (!level.isClientSide() && state.getValue(RIGHT) && level.getBlockState(other).is(this)) {
                if (player.isCreative()) level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                else level.destroyBlock(other, true, player);
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), 3);
            BlockPos other = pos.relative(toPartner(state));
            if (level.getBlockState(other).is(this)) level.setBlock(other, level.getBlockState(other).setValue(OPEN, open), 3);
            level.playSound(null, pos, open ? SoundEvents.CHISELED_BOOKSHELF_PICKUP : SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 0.9F, 0.8F);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Block.box(0, 0, 0, 16, 15, 16);
        }
    }

    /**
     * A bunk bed: two long and two high, a frame of dark wood, a mattress and a blanket on each bunk, a ladder up the
     * foot. It is put down from one item and taken up as one; it is a bunk to look at, not to sleep in.
     */
    public static class BunkBed extends Block {
        public static final BooleanProperty HEAD = BooleanProperty.create("head"), UPPER = BooleanProperty.create("upper");

        public BunkBed(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEAD, false).setValue(UPPER, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, HEAD, UPPER);
        }

        /** The foot of the lower bunk, where this piece is part of the bed at {@code pos}. */
        public static BlockPos root(BlockPos pos, BlockState state) {
            BlockPos at = state.getValue(UPPER) ? pos.below() : pos;
            return state.getValue(HEAD) ? at.relative(state.getValue(FACING).getOpposite()) : at;
        }

        /** The four pieces of a bunk whose lower foot is at {@code foot} and whose head lies {@code facing} of it. */
        public static BlockPos[] pieces(BlockPos foot, Direction facing) {
            return new BlockPos[]{foot, foot.relative(facing), foot.above(), foot.relative(facing).above()};
        }

        private static BlockState piece(Block bunk, Direction facing, int i) {
            return bunk.defaultBlockState().setValue(FACING, facing).setValue(HEAD, i % 2 == 1).setValue(UPPER, i >= 2);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction facing = context.getHorizontalDirection();
            for (BlockPos at : pieces(context.getClickedPos(), facing)) {
                if (!context.getLevel().getBlockState(at).canBeReplaced(context)) return null;
            }
            return defaultBlockState().setValue(FACING, facing);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (level.isClientSide()) return;
            BlockPos[] at = pieces(pos, state.getValue(FACING));
            for (int i = 1; i < 4; i++) level.setBlock(at[i], piece(this, state.getValue(FACING), i), 3);
        }

        /** Builds a bunk with its lower foot at {@code foot}, its head {@code facing} of it. */
        public static void build(Level level, BlockPos foot, Direction facing, Block bunk) {
            BlockPos[] at = pieces(foot, facing);
            for (int i = 0; i < 4; i++) level.setBlock(at[i], piece(bunk, facing, i), 2);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                         BlockPos neighborPos, BlockState neighborState, RandomSource random) {
            // a piece whose partner along the bed, or above or below it, has gone goes too
            Direction f = state.getValue(FACING);
            Direction along = state.getValue(HEAD) ? f.getOpposite() : f, up = state.getValue(UPPER) ? Direction.DOWN : Direction.UP;
            if ((direction == along || direction == up) && !neighborState.is(this)) return Blocks.AIR.defaultBlockState();
            return state;
        }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide()) {
                BlockPos root = root(pos, state);
                BlockPos[] at = pieces(root, state.getValue(FACING));
                for (BlockPos other : at) {
                    if (other.equals(pos) || !level.getBlockState(other).is(this)) continue;
                    // (the lower foot is the one that drops the bed)
                    if (other.equals(root) && !player.isCreative()) level.destroyBlock(other, true, player);
                    else level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(UPPER) ? Block.box(0, 0, 0, 16, 9, 16) : Block.box(0, 0, 0, 16, 7, 16);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer sp) {
                com.thefarawaydev.realmsofchaos.ui.Hud.above(sp, pos, net.minecraft.network.chat.Component.translatable("hud.realms_of_chaos.bunk"), 0xAAAAAA);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ---- the smaller pieces -----------------------------------------------------------------------------------------

    /**
     * One piece of furniture in a single block, set facing the way whoever placed it was looking from: a stool or an
     * armchair to sit on, a nightstand, an amphora, a rug; a lamp or a candelabrum that burns; a shelf hung on a wall.
     */
    public static class Piece extends Block implements Seat {
        private final java.util.Map<Direction, VoxelShape> shapes;
        private final double seat;
        private final boolean onWall;
        /** Where its flames burn, in sixteenths of the block, as drawn facing north; none for a piece that does not burn. */
        private final double[][] flames;

        /** @param north its shape facing north; @param seat how high it is sat on, or 0 if it is not a seat */
        public Piece(Properties properties, VoxelShape north, double seat, boolean onWall, double[][] flames) {
            super(properties);
            this.shapes = Shapes.rotateHorizontal(north);
            this.seat = seat;
            this.onWall = onWall;
            this.flames = flames;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            // a shelf hangs on the wall it was set against; anything else faces whoever set it down
            if (onWall) {
                Direction face = context.getClickedFace();
                if (face.getAxis().isHorizontal()) return defaultBlockState().setValue(FACING, face);
            }
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shapes.get(state.getValue(FACING));
        }

        @Override
        public double seatHeight() {
            return seat;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return seat > 0 ? sitOn(level, pos, player, seat) : InteractionResult.PASS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (flames.length == 0) return;
            com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_fire", 0.015F, 0.2F);
            Direction f = state.getValue(FACING);
            for (double[] at : flames) {
                // (drawn facing north: turned with the piece)
                double x = at[0] / 16 - 0.5, z = at[2] / 16 - 0.5;
                double rx = switch (f) { case EAST -> -z; case SOUTH -> -x; case WEST -> z; default -> x; };
                double rz = switch (f) { case EAST -> x; case SOUTH -> -z; case WEST -> -x; default -> z; };
                double px = pos.getX() + 0.5 + rx, py = pos.getY() + at[1] / 16, pz = pos.getZ() + 0.5 + rz;
                if (random.nextInt(3) == 0) level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), px, py, pz, 0, 0.004, 0);
                if (random.nextInt(6) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, px, py + 0.1, pz, 0, 0.01, 0);
            }
        }
    }
}
