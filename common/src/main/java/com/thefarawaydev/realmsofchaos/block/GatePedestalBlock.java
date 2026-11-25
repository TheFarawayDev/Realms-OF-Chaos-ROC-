package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Keystone Pedestal before a Ruined Gate. Use it to open its screen: three cradles, one for each tower's
 * keystone. A keystone laid in its cradle is set in the arch above, and shows, lit, on the pedestal's top; three wake
 * the gate. A hopper can set them too.
 */
public class GatePedestalBlock extends Block implements WorldlyContainerHolder {
    public static final BooleanProperty ZEUS = BooleanProperty.create("zeus"), POSEIDON = BooleanProperty.create("poseidon"), HADES = BooleanProperty.create("hades"),
            AWAKE = BooleanProperty.create("awake");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 11, 12), Block.box(1, 11, 1, 15, 14, 15));

    public GatePedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ZEUS, false).setValue(POSEIDON, false).setValue(HADES, false).setValue(AWAKE, false));
    }

    public static BooleanProperty set(Line line) {
        return switch (line) {
            case ZEUS -> ZEUS;
            case POSEIDON -> POSEIDON;
            case HADES -> HADES;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ZEUS, POSEIDON, HADES, AWAKE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            Ruins.showPedestal(server, pos);
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new com.thefarawaydev.realmsofchaos.ui.PedestalMenu(id, inventory, server, pos),
                    Component.translatable(getDescriptionId())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how many keystones are set: five for each, fifteen when the gate is awake. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        int n = (state.getValue(ZEUS) ? 1 : 0) + (state.getValue(POSEIDON) ? 1 : 0) + (state.getValue(HADES) ? 1 : 0);
        return n * 5;
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        return new Sockets(level instanceof ServerLevel server ? server : null, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0, pos.getY() + 1.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0, 0, -0.5, 0);
        }
        if (state.getValue(ZEUS) || state.getValue(POSEIDON) || state.getValue(HADES)) com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_pedestal", 0.03F, 0.4F);
        // a set keystone breathes light up from its cradle
        for (Line line : Line.values()) {
            if (!state.getValue(set(line)) || random.nextInt(4) != 0) continue;
            double x = pos.getX() + 0.5 + Ruins.cradle(line) * 0.28;
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), x, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    /**
     * The pedestal's three cradles, as a container: slot 0 for Zeus's keystone, 1 for Poseidon's, 2 for Hades's. A
     * keystone put in is set in the arch at once - it is gone into the stone, and the slot shows it there; nothing
     * can be taken out. Made fresh for each screen and each hopper that reaches it.
     */
    public static final class Sockets implements WorldlyContainer {
        private static final int[] ALL = {0, 1, 2};
        private final ServerLevel level;
        private final BlockPos pos;

        public Sockets(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        private static Line line(int slot) {
            return Line.values()[slot];
        }

        private boolean set(int slot) {
            return level != null && Ruins.isSet(level, pos, line(slot));
        }

        @Override
        public int getContainerSize() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return !set(0) && !set(1) && !set(2);
        }

        @Override
        public ItemStack getItem(int slot) {
            return set(slot) ? new ItemStack(ModContent.keystoneItem(line(slot))) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (level != null && canPlaceItem(slot, stack)) Ruins.setKeystone(level, pos, line(slot));
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return level != null && stack.is(ModContent.keystoneItem(line(slot))) && !set(slot) && Ruins.hasArch(level, pos);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return level != null && level.getBlockState(pos).getBlock() instanceof GatePedestalBlock && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= 64;
        }

        @Override
        public void clearContent() {}

        @Override
        public int[] getSlotsForFace(Direction side) {
            return ALL;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            return canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return false;
        }
    }
}
