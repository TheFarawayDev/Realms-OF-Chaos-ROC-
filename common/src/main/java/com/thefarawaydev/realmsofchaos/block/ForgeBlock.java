package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ui.ForgeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Forge of Chaos: right-click it holding a relic weapon to see its level and forge stars onto it.
 *
 * <p>Forges set side by side join up: each knows which of its six neighbours is a forge too, and its faces drop
 * their frame along every joined edge, so a bank of them reads as one great furnace with one long glowing mouth
 * rather than a row of boxes. Any block of the bank opens the forge.
 */
public class ForgeBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty NORTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH,
            EAST = net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, SOUTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH,
            WEST = net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, UP = net.minecraft.world.level.block.state.properties.BlockStateProperties.UP,
            DOWN = net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN;

    public ForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public static net.minecraft.world.level.block.state.properties.BooleanProperty side(net.minecraft.core.Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /** This forge, joined to whichever of its neighbours are forges. */
    public BlockState joined(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) state = state.setValue(side(d), level.getBlockState(pos.relative(d)).is(this));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                     net.minecraft.core.Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(side(direction), neighborState.is(this));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F);
            ForgeMenu.open(sp, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** The forge is opened with a weapon in hand, so it must answer item clicks as well as empty-handed ones. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** Client-side ambience: the fire in the basin burns, spits and smokes, and runes drift in to be forged. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(UP)) return;                       // (a forge with another on it is inside the bank: only the top one smokes)
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), x + (random.nextDouble() - 0.5) * 0.5, y + 0.05, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type(), x + (random.nextDouble() - 0.5) * 0.4, y + 0.3, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), x + (random.nextDouble() - 0.5) * 2.4, y + 1.2, z + (random.nextDouble() - 0.5) * 2.4, 0, -0.7, 0);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(com.thefarawaydev.realmsofchaos.fx.RocParticles.EMBER.type(), x, y + 0.1, z, 0, 0, 0);
        }
        if (random.nextInt(24) == 0) {
            com.thefarawaydev.realmsofchaos.fx.RocSounds.idle(level, pos, random, "idle_forge", 1.0F, 0.5F);
        }
    }
}
