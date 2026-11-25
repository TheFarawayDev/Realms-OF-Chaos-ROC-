package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Words;
import com.thefarawaydev.realmsofchaos.world.Combat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A training dummy: a sack of straw on a post, a crossbar for arms, a painted target on its chest. Set one dummy on
 * another and they become one taller dummy - up to three blocks: a post on its foot, a body, and a head above. Strike
 * one and it tells you what the blow was worth (a riposte, momentum, a combo of blows in quick succession);
 * right-click it and it teaches the parry and the dodge.
 */
public class TrainingDummyBlock extends Block {
    public enum Part implements StringRepresentable {
        SINGLE, FOOT, UPPER, TORSO, HEAD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final int MAX_HEIGHT = 3;
    private static final VoxelShape SINGLE = Block.box(3, 0, 3, 13, 16, 13), POST = Block.box(5, 0, 5, 11, 16, 11), BODY = Block.box(2, 0, 2, 14, 16, 14),
            HEAD = Block.box(4, 0, 4, 12, 13, 12);
    /** How long blows still count as one combo, in ticks. */
    public static final int COMBO_TICKS = 30;
    private static final Map<UUID, long[]> COMBOS = new HashMap<>();

    public TrainingDummyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Which part of a dummy {@code height} blocks tall the block {@code index} up from its foot is. */
    public static Part part(int height, int index) {
        if (height <= 1) return Part.SINGLE;
        if (index == 0) return Part.FOOT;
        if (height == 2) return Part.UPPER;
        return index == 1 ? Part.TORSO : Part.HEAD;
    }

    private int run(BlockGetter level, BlockPos pos, Direction d) {
        int n = 0;
        while (n < MAX_HEIGHT && level.getBlockState(pos.relative(d, n + 1)).is(this)) n++;
        return n;
    }

    /** This block, as the part of its stack it now is. */
    public BlockState stacked(BlockGetter level, BlockPos pos, BlockState state) {
        int below = run(level, pos, Direction.DOWN), above = run(level, pos, Direction.UP);
        return state.setValue(PART, part(Math.min(MAX_HEIGHT, below + above + 1), below));
    }

    /** A dummy of a given height, built at {@code foot}. Builders use this. */
    public static void build(Level level, BlockPos foot, Direction facing, int height, Block dummy) {
        height = Math.max(1, Math.min(MAX_HEIGHT, height));
        for (int i = 0; i < height; i++) {
            level.setBlock(foot.above(i), dummy.defaultBlockState().setValue(FACING, facing).setValue(PART, part(height, i)), 2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState below = context.getLevel().getBlockState(pos.below());
        // set on another dummy: it grows, to three at most, and faces the way that one does
        if (below.is(this)) {
            if (run(context.getLevel(), pos, Direction.DOWN) >= MAX_HEIGHT) return null;
            return stacked(context.getLevel(), pos, defaultBlockState().setValue(FACING, below.getValue(FACING)));
        }
        return stacked(context.getLevel(), pos, defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) || below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        if (direction.getAxis().isVertical()) return stacked(level, pos, state);
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case SINGLE -> SINGLE;
            case FOOT -> POST;
            case UPPER, TORSO -> BODY;
            case HEAD -> HEAD;
        };
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) struck(server, pos, sp);
    }

    /**
     * A blow on a dummy: what it was worth, as near as the dummy can tell - the hand's strength and how drawn back
     * the swing was, half as much again for a riposte, a little more for each point of momentum - and how many blows
     * in a row. @return the blow's worth
     */
    public static float struck(ServerLevel level, BlockPos pos, ServerPlayer player) {
        long now = level.getServer().getTickCount();
        long[] combo = COMBOS.computeIfAbsent(player.getUUID(), k -> new long[]{-1000, 0});
        combo[1] = now - combo[0] <= COMBO_TICKS ? combo[1] + 1 : 1;
        combo[0] = now;
        boolean riposte = Combat.riposteReady(player);
        int momentum = Combat.momentum(player);
        float worth = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * player.getAttackStrengthScale(0.5F);
        if (riposte) worth *= 1.5F;
        worth *= 1.0F + 0.04F * momentum;
        float pitch = 0.7F + Math.min(0.8F, worth / 12.0F);
        level.playSound(null, pos, SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 1.0F, pitch);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                6 + (int) worth, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(riposte ? com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type() : com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4 + (int) combo[1], 0.2, 0.2, 0.2, 0.1);
        String k = "hud." + RealmsOfChaos.MOD_ID + ".dummy.";
        Component line = Component.translatable(k + "hit", String.format(java.util.Locale.ROOT, "%.1f", worth), combo[1]);
        if (riposte) line = line.copy().append(Component.translatable(k + "riposte"));
        if (momentum > 0) line = line.copy().append(Component.translatable(k + "momentum", momentum));
        Hud.above(player, pos, line, riposte ? 0xFFAA00 : combo[1] >= 3 ? 0x55FF55 : 0xFFFFFF);
        return worth;
    }

    /** How many blows in a row this player has landed on dummies. */
    public static int combo(ServerPlayer player) {
        long[] c = COMBOS.get(player.getUUID());
        return c == null ? 0 : (int) c[1];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) lesson(sp, level, pos);
        return InteractionResult.SUCCESS;
    }

    static void lesson(ServerPlayer sp, Level level, BlockPos pos) {
        String k = "hud." + RealmsOfChaos.MOD_ID + ".dummy.";
        Cards.notice(sp, Words.of(k + "parry"), Words.of(k + "parry.sub"));
        Cards.notice(sp, Words.of(k + "dodge"), Words.of(k + "dodge.sub"));
        level.playSound(null, pos, SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
    }
}
