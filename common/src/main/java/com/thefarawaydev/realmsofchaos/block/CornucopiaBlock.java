package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cornucopia, the horn of plenty: once a core is set in its mouth it brings forth, every few minutes, the
 * resource that core belongs to - storm glass for a thunder core, abyssal pearls for a tide core, raw stygian iron
 * for a shade core, and now and then a lump of raw adamantine. What it makes goes into a container under it, or
 * spills out on top. Sneak with an empty hand to take the core back.
 */
public class CornucopiaBlock extends Block {
    public enum Core implements StringRepresentable {
        NONE, STORM, TIDE, SHADE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The core item that sets this, and what it brings forth. */
        public Item core() {
            return switch (this) {
                case STORM -> ModContent.THUNDER_CORE.get();
                case TIDE -> ModContent.TIDE_CORE.get();
                case SHADE -> ModContent.SHADE_CORE.get();
                case NONE -> null;
            };
        }

        public Item yield() {
            return switch (this) {
                case STORM -> ModContent.STORM_GLASS.get();
                case TIDE -> ModContent.ABYSSAL_PEARL.get();
                case SHADE -> ModContent.RAW_STYGIAN_IRON.get();
                case NONE -> null;
            };
        }

        public static Core of(ItemStack stack) {
            for (Core c : values()) if (c != NONE && stack.is(c.core())) return c;
            return NONE;
        }
    }

    public static final EnumProperty<Core> CORE = EnumProperty.create("core", Core.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Ticks between one yield and the next (five minutes), and the chance a yield is raw adamantine instead. */
    public static final int PERIOD = 20 * 60 * 5;
    public static final float ADAMANTINE_CHANCE = 0.06F;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public CornucopiaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORE, Core.NONE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Core core = Core.of(stack);
        if (core == Core.NONE || state.getValue(CORE) != Core.NONE) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, state.setValue(CORE, core), 3);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            server.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.2F);
            server.scheduleTick(pos, this, PERIOD);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Core core = state.getValue(CORE);
        if (core == Core.NONE || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, state.setValue(CORE, Core.NONE), 3);
            Block.popResource(server, pos.above(), new ItemStack(core.core()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server && state.getValue(CORE) != Core.NONE && !server.getBlockTicks().hasScheduledTick(pos, this))
            server.scheduleTick(pos, this, PERIOD);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(CORE) == Core.NONE) return;
        produce(level, pos, random);
        level.scheduleTick(pos, this, PERIOD);
    }

    /** Brings forth one yield now. @return what it brought forth (empty if it holds no core) */
    public static ItemStack produce(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CornucopiaBlock) || state.getValue(CORE) == Core.NONE) return ItemStack.EMPTY;
        Core core = state.getValue(CORE);
        ItemStack out = new ItemStack(random.nextFloat() < ADAMANTINE_CHANCE ? ModContent.RAW_ADAMANTINE.get() : core.yield());
        ItemStack made = out.copy();
        if (level.getBlockEntity(pos.below()) instanceof Container below) out = HopperBlockEntity.addItem(null, below, out, Direction.UP);
        if (!out.isEmpty()) Block.popResource(level, pos.above(), out);
        var fx = switch (core) {
            case STORM -> com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK;
            case TIDE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY;
            default -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP;
        };
        level.sendParticles(fx.type(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
        return made;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(CORE) == Core.NONE || random.nextInt(3) != 0) return;
        level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.9,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
    }
}
