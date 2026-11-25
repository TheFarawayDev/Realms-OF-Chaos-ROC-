package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A swinging door made of panels: stack them side by side and on top of each other and they make one door, of any
 * size, hung on one edge. Used, or given a redstone signal, the whole leaf swings a quarter turn on its hinge, away
 * from whoever set it, and is seen to swing; used again, or when the signal goes, it swings back. The hinge is on the
 * setter's left, or on their right if they set the door sneaking.
 */
public class SwingDoorBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** The most panels in one door, and how many ticks a swing takes. */
    public static final int MAX_PANELS = 64, SWING = 10;
    private static final VoxelShape ACROSS_X = Block.box(0, 0, 6, 16, 16, 10), ACROSS_Z = Block.box(6, 0, 0, 10, 16, 16);
    /** Where a door is swinging, and until what game tick. */
    private static final Map<BlockPos, Long> BUSY = new HashMap<>();
    private static final Map<String, java.lang.reflect.Method> METHODS = new HashMap<>();

    public SwingDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HINGE, DoorHingeSide.LEFT).setValue(OPEN, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, OPEN, POWERED);
    }

    /** From the hinge toward the free edge, while the door is shut. */
    public static Direction across(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(HINGE) == DoorHingeSide.LEFT ? f.getClockWise() : f.getCounterClockWise();
    }

    /** From the hinge toward the free edge, as the door stands now: across when shut, the way it swings when open. */
    public static Direction leaf(BlockState state) {
        return state.getValue(OPEN) ? state.getValue(FACING) : across(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (against.is(this)) return defaultBlockState().setValue(FACING, against.getValue(FACING)).setValue(HINGE, against.getValue(HINGE)).setValue(OPEN, against.getValue(OPEN));
        boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(HINGE, sneaking ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return leaf(state).getAxis() == Direction.Axis.X ? ACROSS_X : ACROSS_Z;
    }

    /** Every panel of the door this one belongs to. */
    public static List<BlockPos> door(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<BlockPos> out = new ArrayList<>();
        if (!(state.getBlock() instanceof SwingDoorBlock)) return out;
        Direction along = leaf(state);
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(pos.immutable());
        seen.add(pos.immutable());
        while (!open.isEmpty() && out.size() < MAX_PANELS) {
            BlockPos at = open.poll();
            out.add(at);
            for (Direction d : new Direction[]{along, along.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos next = at.relative(d);
                if (seen.contains(next)) continue;
                BlockState s = level.getBlockState(next);
                if (s.is(state.getBlock()) && s.getValue(FACING) == state.getValue(FACING) && s.getValue(HINGE) == state.getValue(HINGE) && s.getValue(OPEN) == state.getValue(OPEN)) {
                    seen.add(next);
                    open.add(next);
                }
            }
        }
        return out;
    }

    /** Where each panel goes when the door swings: from {@code from} to {@code to}, turning on the hinge column. */
    private static Map<BlockPos, BlockPos> targets(List<BlockPos> panels, Direction from, Direction to) {
        int hinge = Integer.MAX_VALUE;
        for (BlockPos p : panels) hinge = Math.min(hinge, p.get(from.getAxis()) * from.getAxisDirection().getStep());
        Map<BlockPos, BlockPos> out = new HashMap<>();
        for (BlockPos p : panels) {
            int i = p.get(from.getAxis()) * from.getAxisDirection().getStep() - hinge;
            BlockPos h = p.relative(from, -i);
            out.put(p, h.relative(to, i));
        }
        return out;
    }

    /**
     * Swings the whole door open or shut. With {@code show} the leaf is seen to swing (as display blocks, turned on the
     * hinge) and lands a moment later; without, it is turned at once (for tests). @return whether it swung
     */
    public static boolean swing(ServerLevel level, BlockPos pos, boolean open, boolean show) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SwingDoorBlock) || state.getValue(OPEN) == open) return false;
        List<BlockPos> panels = door(level, pos);
        Direction from = leaf(state), to = open ? state.getValue(FACING) : across(state);
        Map<BlockPos, BlockPos> moves = targets(panels, from, to);
        Set<BlockPos> mine = new HashSet<>(panels);
        for (BlockPos t : moves.values()) {
            if (mine.contains(t)) continue;
            BlockState there = level.getBlockState(t);
            if (!there.isAir() && !there.canBeReplaced()) return false;
        }
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos p : panels) before.put(p, level.getBlockState(p));
        long until = level.getGameTime() + SWING + 6;
        for (BlockPos p : panels) {
            BUSY.put(p, until);
            BUSY.put(moves.get(p), until);
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
        Runnable land = () -> {
            for (BlockPos p : panels) {
                BlockPos t = moves.get(p);
                BlockState there = level.getBlockState(t);
                BlockState now = before.get(p).setValue(OPEN, open);
                if (there.isAir() || there.canBeReplaced()) level.setBlock(t, now, 3);
                else Block.popResource(level, t, new net.minecraft.world.item.ItemStack(now.getBlock()));
            }
        };
        if (!show) {
            land.run();
            return true;
        }
        // the leaf, seen swinging: a display block for each panel, set at the hinge and turned on it
        float turn = to == from.getCounterClockWise() ? -90.0F : 90.0F;
        List<Display.BlockDisplay> shown = new ArrayList<>();
        for (BlockPos p : panels) {
            BlockPos h = p.relative(from, -(moves.get(p).equals(p) ? 0 : distance(p, moves.get(p), from, to)));
            Display.BlockDisplay d = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (d == null) continue;
            d.setPos(h.getX() + 0.5, p.getY(), h.getZ() + 0.5);
            call(Display.BlockDisplay.class, d, "setBlockState", BlockState.class, before.get(p));
            call(Display.class, d, "setTransformation", com.mojang.math.Transformation.class, new com.mojang.math.Transformation(
                    new org.joml.Vector3f(p.getX() - h.getX() - 0.5F, 0.0F, p.getZ() - h.getZ() - 0.5F), new org.joml.Quaternionf(), new org.joml.Vector3f(1.0F, 1.0F, 1.0F), new org.joml.Quaternionf()));
            call(Display.class, d, "setPosRotInterpolationDuration", int.class, SWING);
            level.addFreshEntity(d);
            shown.add(d);
        }
        Scheduler.after(level.getServer(), 2, () -> {
            for (Display.BlockDisplay d : shown) d.setYRot(turn);
        });
        // (the blocks land first and the shown leaf goes a moment later, once clients have drawn the blocks: no gap)
        Scheduler.after(level.getServer(), SWING + 3, land);
        Scheduler.after(level.getServer(), SWING + 13, () -> {
            for (Display.BlockDisplay d : shown) d.discard();
        });
        return true;
    }

    /** How far a panel stands from the hinge column, given where it starts and where it lands. */
    private static int distance(BlockPos p, BlockPos t, Direction from, Direction to) {
        // p = h + i*from and t = h + i*to, so p - t = i*(from - to): read i off either axis
        int dx = p.getX() - t.getX(), dz = p.getZ() - t.getZ();
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    private static void call(Class<?> owner, Object target, String name, Class<?> type, Object arg) {
        try {
            java.lang.reflect.Method m = METHODS.get(owner.getName() + "#" + name);
            if (m == null) {
                m = owner.getDeclaredMethod(name, type);
                m.setAccessible(true);
                METHODS.put(owner.getName() + "#" + name, m);
            }
            m.invoke(target, arg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("A swinging door could not set {}", name, e);
        }
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
        if (level instanceof ServerLevel server && !busy(level, pos)) swing(server, pos, !state.getValue(OPEN), true);
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
        if (powered != state.getValue(OPEN)) swing(server, pos, powered, true);
    }
}
