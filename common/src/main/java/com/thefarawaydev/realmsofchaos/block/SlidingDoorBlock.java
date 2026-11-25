package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sliding door: a panel, stacked with others of its kind into a door of any size. Used, or given a redstone signal,
 * the whole door glides sideways a block at a time - to the right of whoever set it, or to the left if they set it
 * sneaking - until it has moved its own width (four blocks at most) or meets something in the way; used again, or
 * when the signal goes, it glides back. Each block keeps how far it has moved, so the door always knows its way home.
 */
public class SlidingDoorBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How many blocks the door stands from where it was shut. */
    public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 4);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Whether the panel joins another of its door above, below, toward the door's home or hinge side, and toward its far side. */
    public static final BooleanProperty TOP = BooleanProperty.create("top"), BOTTOM = BooleanProperty.create("bottom"),
            NEAR = BooleanProperty.create("near"), FAR = BooleanProperty.create("far");
    /** The most a door travels, the most panels one door holds, and the ticks between one block of travel and the next. */
    public static final int MAX = 4, MAX_PANELS = 64, STEP = 5;
    private static final VoxelShape ACROSS_X = Block.box(0, 0, 6, 16, 16, 10), ACROSS_Z = Block.box(6, 0, 0, 10, 16, 16);
    /** Where a door is gliding, and until what game tick: what happens there meanwhile is the door's own doing. */
    private static final Map<BlockPos, Long> BUSY = new HashMap<>();

    public SlidingDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHIFT, 0).setValue(POWERED, false)
                .setValue(TOP, false).setValue(BOTTOM, false).setValue(NEAR, false).setValue(FAR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHIFT, POWERED, TOP, BOTTOM, NEAR, FAR);
    }

    /** The way the door slides to open. */
    public static Direction opens(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // set against another panel of the same door, it joins it; otherwise it opens to the setter's right (left if sneaking)
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        BlockState placed;
        if (against.is(this)) placed = defaultBlockState().setValue(FACING, against.getValue(FACING)).setValue(SHIFT, against.getValue(SHIFT));
        else {
            Direction facing = context.getHorizontalDirection();
            if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) facing = facing.getOpposite();
            placed = defaultBlockState().setValue(FACING, facing);
        }
        return joined(context.getLevel(), context.getClickedPos(), placed);
    }


    /** The side of the door its home (a sliding door) or hinge (a swinging one) is on, and the other. */
    public static Direction nearSide(BlockState state) {
        return opens(state).getOpposite();
    }

    public static Direction farSide(BlockState state) {
        return opens(state);
    }

    /** True if two panels belong to one door. */
    public static boolean joins(BlockState a, BlockState b) {
        return b.getBlock() == a.getBlock() && b.getValue(FACING) == a.getValue(FACING) && b.getValue(SHIFT).equals(a.getValue(SHIFT));
    }

    /** The panel with its joins to the panels round it worked out: it draws its frame only where the door ends. */
    public static BlockState joined(BlockGetter level, BlockPos pos, BlockState state) {
        return state.setValue(TOP, joins(state, level.getBlockState(pos.above()))).setValue(BOTTOM, joins(state, level.getBlockState(pos.below())))
                .setValue(NEAR, joins(state, level.getBlockState(pos.relative(nearSide(state))))).setValue(FAR, joins(state, level.getBlockState(pos.relative(farSide(state)))));
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        boolean j = joins(state, neighborState);
        if (direction == Direction.UP) return state.setValue(TOP, j);
        if (direction == Direction.DOWN) return state.setValue(BOTTOM, j);
        if (direction == nearSide(state)) return state.setValue(NEAR, j);
        if (direction == farSide(state)) return state.setValue(FAR, j);
        return state;
    }

    /**
     * Set where it joins no other door, a panel makes a regular door: two high, the upper panel taken from the same
     * stack (so a door broken gives back what it cost). Set against another door, it joins that door instead.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level.isClientSide() || state.getValue(TOP) || state.getValue(BOTTOM) || state.getValue(NEAR) || state.getValue(FAR)) return;
        BlockPos up = pos.above();
        if (!level.getBlockState(up).canBeReplaced()) return;
        boolean free = placer instanceof Player p && p.getAbilities().instabuild;
        if (!free) {
            if (stack.getCount() < 2) return;
            stack.shrink(1);
        }
        level.setBlock(up, state.setValue(BOTTOM, true), 3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_X : ACROSS_Z;
    }

    /** Every panel of the door this one belongs to: its kind, facing the same way and moved as far, side by side or stacked. */
    public static List<BlockPos> door(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<BlockPos> out = new ArrayList<>();
        if (!(state.getBlock() instanceof SlidingDoorBlock)) return out;
        Direction side = opens(state);
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(pos.immutable());
        seen.add(pos.immutable());
        while (!open.isEmpty() && out.size() < MAX_PANELS) {
            BlockPos at = open.poll();
            out.add(at);
            for (Direction d : new Direction[]{side, side.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos next = at.relative(d);
                if (seen.contains(next)) continue;
                BlockState s = level.getBlockState(next);
                if (s.is(state.getBlock()) && s.getValue(FACING) == state.getValue(FACING) && s.getValue(SHIFT).equals(state.getValue(SHIFT))) {
                    seen.add(next);
                    open.add(next);
                }
            }
        }
        return out;
    }

    /** How far the door travels to open: its own width, four at most. */
    public static int travel(BlockGetter level, BlockPos pos) {
        List<BlockPos> panels = door(level, pos);
        if (panels.isEmpty()) return 0;
        Direction.Axis axis = opens(level.getBlockState(pos)).getAxis();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (BlockPos p : panels) {
            int v = p.get(axis);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return Math.min(MAX, hi - lo + 1);
    }

    /**
     * Moves the whole door one block, opening or shutting. With {@code glide} the move is a block event, so that
     * every client sees it glide; without, it happens at once on the server (for tests).
     * @return whether it moved
     */
    public static boolean move(Level level, BlockPos pos, boolean open, boolean glide) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SlidingDoorBlock)) return false;
        int shift = state.getValue(SHIFT);
        if (open ? shift >= travel(level, pos) : shift <= 0) return false;
        Direction d = open ? opens(state) : opens(state).getOpposite();
        List<BlockPos> panels = door(level, pos);
        Set<BlockPos> mine = new HashSet<>(panels);
        for (BlockPos p : panels) {
            BlockPos to = p.relative(d);
            if (mine.contains(to)) continue;
            BlockState there = level.getBlockState(to);
            if (!there.isAir() && !there.canBeReplaced()) return false;
        }
        // the leading edge first, so each block glides into room already made
        panels.sort(Comparator.comparingInt((BlockPos p) -> p.get(d.getAxis()) * d.getAxisDirection().getStep()).reversed());
        long until = level.getGameTime() + STEP + 2;
        for (BlockPos p : panels) {
            BUSY.put(p, until);
            BUSY.put(p.relative(d), until);
            if (glide) level.blockEvent(p, state.getBlock(), open ? 1 : 2, d.get3DDataValue());
            else glide(level, p, level.getBlockState(p), open, d);
        }
        return true;
    }

    private static void glide(Level level, BlockPos pos, BlockState state, boolean open, Direction d) {
        int shift = Math.max(0, Math.min(MAX, state.getValue(SHIFT) + (open ? 1 : -1)));
        MovingDummyBlock.slide(level, pos, state.setValue(SHIFT, shift), d);
    }

    /** Opens or shuts the door all the way, a block every few ticks, stopping early if something is in its way. */
    public static void run(ServerLevel level, BlockPos pos, boolean open) {
        if (!(level.getBlockState(pos).getBlock() instanceof SlidingDoorBlock)) return;
        com.thefarawaydev.realmsofchaos.fx.RocSounds.at(level, pos, "sliding_door", 0.9F, 0.9F + level.getRandom().nextFloat() * 0.15F);
        keepGoing(level, pos, open);
    }

    private static void keepGoing(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SlidingDoorBlock) || !move(level, pos, open, true)) return;
        BlockPos next = pos.relative(open ? opens(state) : opens(state).getOpposite());
        Scheduler.after(level.getServer(), STEP, () -> keepGoing(level, next, open));
    }

    /** Forgets every door in motion (for tests, where the clock does not run). */
    public static void forget() {
        BUSY.clear();
    }

    private static boolean busy(Level level, BlockPos pos) {
        Long until = BUSY.get(pos);
        if (until == null) return false;
        if (level.getGameTime() < until) return true;
        BUSY.remove(pos);
        if (BUSY.size() > 512) BUSY.values().removeIf(t -> t < level.getGameTime());
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !busy(level, pos)) run(server, pos, state.getValue(SHIFT) == 0);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        if (!(level instanceof ServerLevel server) || busy(level, pos)) return;
        List<BlockPos> panels = door(level, pos);
        boolean powered = false;
        for (BlockPos p : panels) if (level.hasNeighborSignal(p)) { powered = true; break; }
        if (powered == state.getValue(POWERED)) return;
        for (BlockPos p : panels) level.setBlock(p, level.getBlockState(p).setValue(POWERED, powered), 2);
        run(server, pos, powered);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction d = Direction.from3DDataValue(param);
        if (!state.is(this) || d.getAxis().isVertical() || (id != 1 && id != 2)) return false;
        glide(level, pos, state, id == 1, d);
        return true;
    }
}
