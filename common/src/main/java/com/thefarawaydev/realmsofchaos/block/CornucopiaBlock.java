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
 * The Cornucopia, the horn of plenty, and the heart of a shrine of three parts: it stands on a Plinth of Plenty, with a
 * Column of Plenty two high at each corner of the three by three round it. Whole, and with a core set in its mouth,
 * the shrine brings forth every few minutes one or two of the resource that core belongs to - storm glass for a
 * thunder core, abyssal pearls for a tide core, raw stygian iron for a shade core, and now and then raw adamantine -
 * twice as fast where that core is at home: in a thunderstorm, by the sea, in the nether. What it makes goes into a
 * container beside or under the plinth, or spills out on top. Use it to see how the shrine stands; sneak with an
 * empty hand to take the core back.
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
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FORMED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("formed");
    /** How far toward its next yield the shrine has come, in steps of {@link #STEP} ticks. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty PROGRESS = net.minecraft.world.level.block.state.properties.IntegerProperty.create("progress", 0, 15);
    /** The shrine is looked over every second; it comes a step nearer its yield every twenty seconds (two steps where its core is at home). */
    public static final int CHECK = 20, STEP = 400, STEPS = 15;
    /** The chance a yield is raw adamantine instead. */
    public static final float ADAMANTINE_CHANCE = 0.08F;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public CornucopiaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORE, Core.NONE).setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(PROGRESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE, FACING, FORMED, PROGRESS);
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
            server.setBlock(pos, state.setValue(CORE, core).setValue(PROGRESS, 0), 3);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            server.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.2F);
            status(server, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Core core = state.getValue(CORE);
        if (level instanceof ServerLevel server) {
            if (core != Core.NONE && player.isShiftKeyDown()) {
                server.setBlock(pos, state.setValue(CORE, Core.NONE).setValue(PROGRESS, 0), 3);
                Block.popResource(server, pos.above(), new ItemStack(core.core()));
            } else {
                status(server, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** What is missing from the shrine round a Cornucopia here, as words for the panel over it; empty if it is whole. */
    public static java.util.List<net.minecraft.network.chat.Component> missing(Level level, BlockPos pos) {
        java.util.List<net.minecraft.network.chat.Component> out = new java.util.ArrayList<>();
        String key = "block." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".cornucopia.";
        if (!level.getBlockState(pos.below()).is(ModContent.PLENTY_PLINTH.get())) out.add(net.minecraft.network.chat.Component.translatable(key + "needs_plinth"));
        int columns = 0;
        for (BlockPos c : columns(pos)) if (level.getBlockState(c).is(ModContent.PLENTY_COLUMN.get())) columns++;
        if (columns < 8) out.add(net.minecraft.network.chat.Component.translatable(key + "needs_columns", columns / 2, 4));
        return out;
    }

    /** Where the columns of a shrine round a Cornucopia here stand: two high at each corner, level with the plinth and the horn. */
    public static java.util.List<BlockPos> columns(BlockPos pos) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
            out.add(pos.offset(dx, -1, dz));
            out.add(pos.offset(dx, 0, dz));
        }
        return out;
    }

    /** True if the shrine round a Cornucopia here is whole. */
    public static boolean formed(Level level, BlockPos pos) {
        return missing(level, pos).isEmpty();
    }

    /** True where this core is at home: a thunderstorm for the storm, the sea for the tide, the nether for the shade. */
    public static boolean atHome(ServerLevel level, BlockPos pos, Core core) {
        return switch (core) {
            case STORM -> level.isThundering() && level.canSeeSky(pos.above());
            case TIDE -> level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OCEAN) || level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_BEACH);
            case SHADE -> level.dimension() == Level.NETHER;
            case NONE -> false;
        };
    }

    /** Shows how the shrine stands, in a panel over the horn. */
    public static void status(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CornucopiaBlock)) return;
        String key = "block." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".cornucopia.";
        java.util.List<net.minecraft.network.chat.Component> missing = missing(level, pos);
        net.minecraft.network.chat.MutableComponent text;
        int colour;
        if (!missing.isEmpty()) {
            text = net.minecraft.network.chat.Component.translatable(key + "incomplete");
            for (var m : missing) text.append("\n").append(m);
            colour = 0xFF7A5A;
        } else if (state.getValue(CORE) == Core.NONE) {
            text = net.minecraft.network.chat.Component.translatable(key + "no_core");
            colour = 0xF0D070;
        } else {
            Core core = state.getValue(CORE);
            boolean home = atHome(level, pos, core);
            int left = (STEPS - state.getValue(PROGRESS)) * STEP / (home ? 2 : 1) / 20;
            text = net.minecraft.network.chat.Component.translatable(key + "working", new ItemStack(core.yield()).getHoverName(),
                    String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60));
            if (home) text.append("\n").append(net.minecraft.network.chat.Component.translatable(key + "at_home." + core.getSerializedName()));
            colour = 0x7CF0C0;
        }
        com.thefarawaydev.realmsofchaos.ui.BlockLabel.show(level, pos, text, colour);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) server.scheduleTick(pos, this, CHECK);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, CHECK);
        boolean formed = formed(level, pos);
        light(level, pos, formed);
        BlockState now = state.setValue(FORMED, formed);
        if (formed && state.getValue(CORE) != Core.NONE && level.getGameTime() % STEP < CHECK) {
            int progress = state.getValue(PROGRESS) + (atHome(level, pos, state.getValue(CORE)) ? 2 : 1);
            if (progress >= STEPS) {
                level.setBlock(pos, now.setValue(PROGRESS, 0), 3);
                produce(level, pos, random);
                return;
            }
            now = now.setValue(PROGRESS, progress);
        }
        if (now != state) level.setBlock(pos, now, 3);
    }

    /** Lights the plinth and the columns of a whole shrine, and puts them out when it is broken. */
    private static void light(ServerLevel level, BlockPos pos, boolean formed) {
        java.util.List<BlockPos> parts = new java.util.ArrayList<>(columns(pos));
        parts.add(pos.below());
        for (BlockPos p : parts) {
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(PlentyBlocks.LIT) && (s.is(ModContent.PLENTY_PLINTH.get()) || s.is(ModContent.PLENTY_COLUMN.get())) && s.getValue(PlentyBlocks.LIT) != formed)
                level.setBlock(p, s.setValue(PlentyBlocks.LIT, formed), 2);
        }
    }

    /** Brings forth one yield now: one or two of the core's resource, or now and then raw adamantine. @return what it brought forth (empty if it holds no core) */
    public static ItemStack produce(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CornucopiaBlock) || state.getValue(CORE) == Core.NONE) return ItemStack.EMPTY;
        Core core = state.getValue(CORE);
        ItemStack out = random.nextFloat() < ADAMANTINE_CHANCE ? new ItemStack(ModContent.RAW_ADAMANTINE.get()) : new ItemStack(core.yield(), 1 + random.nextInt(2));
        ItemStack made = out.copy();
        // into a container beside the plinth or under it
        BlockPos plinth = pos.below();
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (out.isEmpty()) break;
            if (level.getBlockEntity(plinth.relative(d)) instanceof Container box) out = HopperBlockEntity.addItem(null, box, out, d.getOpposite());
        }
        if (!out.isEmpty()) Block.popResource(level, pos.above(), out);
        var fx = switch (core) {
            case STORM -> com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK;
            case TIDE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY;
            default -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP;
        };
        level.sendParticles(fx.type(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 24, 0.35, 0.3, 0.35, 0.03);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.7F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5F, 1.6F);
        return made;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FORMED)) return;
        // threads of light run from the tops of the columns into the horn
        if (random.nextInt(2) == 0) {
            int dx = random.nextBoolean() ? 1 : -1, dz = random.nextBoolean() ? 1 : -1;
            double sx = pos.getX() + 0.5 + dx, sy = pos.getY() + 1.05, sz = pos.getZ() + 0.5 + dz;
            var kind = switch (state.getValue(CORE)) {
                case STORM -> com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK;
                case TIDE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY;
                case SHADE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP;
                case NONE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT;
            };
            level.addParticle(kind.type(), sx, sy, sz, -dx * 0.045, -0.01, -dz * 0.045);
        }
        if (state.getValue(CORE) != Core.NONE && random.nextInt(3) == 0)
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.85,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.012, 0);
    }
}
