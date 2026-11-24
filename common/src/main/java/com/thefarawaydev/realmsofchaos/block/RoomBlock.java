package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.world.Rooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The things a passage's rooms are furnished with that do something: the glyph tiles of a vault, the crystals of a
 * chime room, the plates of a trembling floor, a beam room's mirrors, emitter and receiver, dart tiles, urns and
 * lore tablets. Each is the same simple block - at most a light that is on or off - and hands what was done to it
 * to {@link Rooms}, which knows the rules of the room it stands in.
 */
public class RoomBlock extends Block {
    public enum Kind { GLYPH, CHIME, PLATE, MIRROR, EMITTER, RECEIVER, DART, URN, TABLET }

    /** Lit, pressed down or - for a mirror - turned the other way: the one thing about the block that can change. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** A lens on its pedestal. */
    private static final VoxelShape LENS = Block.box(1, 0, 1, 15, 15, 15);
    private static final VoxelShape URN = Block.box(3, 0, 3, 13, 14, 13), CRYSTAL = Block.box(4, 0, 4, 12, 15, 12), SLAB = Block.box(2, 0, 2, 14, 16, 14);

    private final Kind kind;

    public RoomBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case URN -> URN;
            case CHIME -> CRYSTAL;
            case TABLET, MIRROR -> SLAB;
            case EMITTER, RECEIVER -> LENS;
            default -> super.getShape(state, level, pos, context);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            switch (kind) {
                case GLYPH -> Rooms.pressGlyph(serverLevel, pos, sp);
                case CHIME -> Rooms.pressChime(serverLevel, pos, sp);
                case MIRROR -> Rooms.turnMirror(serverLevel, pos, sp);
                case URN -> Rooms.smashUrn(serverLevel, pos, sp);
                case TABLET -> Rooms.readTablet(serverLevel, pos, sp);
                default -> {
                    return InteractionResult.PASS;
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Works with anything in hand - a player arrives here holding a weapon. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** Plates and dart tiles answer to being stood on. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player && !player.isSpectator()) {
            if (kind == Kind.PLATE) Rooms.stepPlate(serverLevel, pos, player);
            if (kind == Kind.DART) Rooms.stepDart(serverLevel, pos, player);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && kind != Kind.MIRROR && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.04, 0);
        }
    }
}
