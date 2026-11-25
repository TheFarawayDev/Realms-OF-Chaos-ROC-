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
 * The Orb of Plenty, and the heart of a shrine of three parts: it stands on a Plinth of Plenty, with a Column of Plenty
 * two high at each corner of the three by three round it. Whole, and with a core set in it, the shrine brings forth
 * every few minutes one or two of the resource that core belongs to - storm glass for a thunder core, abyssal pearls
 * for a tide core, raw stygian iron for a shade core, and now and then raw adamantine - twice as fast where that core
 * is at home: in a thunderstorm, by the sea, in the nether. Shown a pattern, and paid in Divine Ichor, it makes that
 * instead, given time.
 *
 * <p>Everything is done in the orb's own screen: use it to open it. What it makes goes into a container beside or
 * under the plinth, else into the orb's own slots. Hoppers can feed it cores and ichor and take what it makes,
 * through the orb or through its plinth, and a comparator reads how full it is.
 */
public class CornucopiaBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
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
    /** How many ranks of orb there are. */
    public static final int RANKS = 4;
    private static final String[] IDS = {"cornucopia", "greater_orb_of_plenty", "exalted_orb_of_plenty", "divine_orb_of_plenty"};
    /** This orb's rank, 1 to {@link #RANKS}: each rank works faster than the last, and from the third pays less ichor. */
    public final int rank;

    public CornucopiaBlock(int rank, Properties properties) {
        super(properties);
        this.rank = rank;
        registerDefaultState(stateDefinition.any().setValue(CORE, Core.NONE).setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(PROGRESS, 0));
    }

    /** The block id of an orb of a rank. */
    public static String id(int rank) {
        return IDS[rank - 1];
    }

    /** How many times as fast as the first an orb of a rank works: one, two, three, four. */
    public static int speed(int rank) {
        return rank;
    }

    /** What share of the ichor an orb of a rank pays, in percent: all of it at the first two ranks, three quarters at the third, half at the fourth. */
    public static int ichorShare(int rank) {
        return rank >= 4 ? 50 : rank == 3 ? 75 : 100;
    }

    /** How many more of its core's resource an orb of a rank brings forth each time. */
    public static int extraYield(int rank) {
        return rank - 1;
    }

    public static int rank(BlockState state) {
        return state.getBlock() instanceof CornucopiaBlock orb ? orb.rank : 1;
    }

    /** The lines under an orb's name: its rank, and what the rank does. */
    public net.minecraft.world.item.component.ItemLore lore() {
        String key = "block." + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + ".orb_rank.";
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.translatable(key + "rank", rank).withStyle(net.minecraft.ChatFormatting.GOLD));
        lines.add(net.minecraft.network.chat.Component.translatable(key + "speed", speed(rank)).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable(key + "ichor", ichorShare(rank)).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (rank > 1) lines.add(net.minecraft.network.chat.Component.translatable(key + "yield", extraYield(rank)).withStyle(net.minecraft.ChatFormatting.GRAY));
        return new net.minecraft.world.item.component.ItemLore(lines);
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
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbBlockEntity(pos, state);
    }

    public static OrbBlockEntity orb(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OrbBlockEntity o ? o : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && player instanceof net.minecraft.server.level.ServerPlayer sp && orb(level, pos) instanceof OrbBlockEntity orb) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new com.thefarawaydev.realmsofchaos.ui.OrbMenu(id, inventory, orb),
                    net.minecraft.network.chat.Component.translatable(getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    /** What is missing from the shrine round an orb here, in words; empty if it is whole. */
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

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) server.scheduleTick(pos, this, CHECK);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, CHECK);
        boolean formed = formed(level, pos);
        light(level, pos, formed);
        if (formed && !state.getValue(FORMED)) level.playSound(null, pos, ModContent.sound("shrine_awaken"), SoundSource.BLOCKS, 1.0F, 1.0F);
        BlockState now = state.setValue(FORMED, formed);
        OrbBlockEntity orb = orb(level, pos);
        if (formed && state.getValue(CORE) != Core.NONE && orb != null && orb.pattern() != null) {
            // making a pattern: it comes with time, and costs ichor
            ItemStack pattern = new ItemStack(orb.pattern());
            int steps = OrbBlockEntity.steps(pattern);
            if (level.getGameTime() % STEP < CHECK && orb.work() < steps) orb.addWork(speed(rank) * (atHome(level, pos, state.getValue(CORE)) ? 2 : 1));
            if (orb.work() >= steps && orb.spend(OrbBlockEntity.cost(pattern, rank))) deliver(level, pos, state.getValue(CORE), pattern);
            if (now != state) level.setBlock(pos, now, 3);
            return;
        }
        if (formed && state.getValue(CORE) != Core.NONE && level.getGameTime() % STEP < CHECK) {
            int progress = state.getValue(PROGRESS) + speed(rank) * (atHome(level, pos, state.getValue(CORE)) ? 2 : 1);
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
        int rank = rank(state);
        ItemStack out = random.nextFloat() < ADAMANTINE_CHANCE + 0.03F * (rank - 1) ? new ItemStack(ModContent.RAW_ADAMANTINE.get())
                : new ItemStack(core.yield(), 1 + random.nextInt(2) + extraYield(rank));
        ItemStack made = out.copy();
        deliver(level, pos, core, out);
        return made;
    }

    /** Puts what the shrine made into a container beside or under the plinth, else into the orb, with light and sound. */
    public static void deliver(ServerLevel level, BlockPos pos, Core core, ItemStack out) {
        out = out.copy();
        // into a container beside the plinth or under it
        BlockPos plinth = pos.below();
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (out.isEmpty()) break;
            if (level.getBlockEntity(plinth.relative(d)) instanceof Container box) out = HopperBlockEntity.addItem(null, box, out, d.getOpposite());
        }
        // else into the orb's own slots, to be taken from its screen or by a hopper
        if (!out.isEmpty() && orb(level, pos) instanceof OrbBlockEntity orb) out = orb.store(out);
        if (!out.isEmpty()) Block.popResource(level, pos.above(), out);
        var fx = switch (core) {
            case STORM -> com.thefarawaydev.realmsofchaos.fx.RocParticles.STORM_SPARK;
            case TIDE -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY;
            default -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP;
        };
        level.sendParticles(fx.type(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 24, 0.35, 0.3, 0.35, 0.03);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, pos, ModContent.sound("orb_make"), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FORMED)) return;
        // a whole shrine with a core in it hums at its work
        if (state.getValue(CORE) != Core.NONE) com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_orb", 0.04F, 0.45F);
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
