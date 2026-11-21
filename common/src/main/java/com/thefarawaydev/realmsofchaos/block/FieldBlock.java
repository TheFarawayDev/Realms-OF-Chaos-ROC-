package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Screens;
import com.thefarawaydev.realmsofchaos.world.Network;
import com.thefarawaydev.realmsofchaos.world.Realm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of the field network: a Relay Tower Core, a Signal Jammer or a Waygate Obelisk. Unlike the mod's other
 * blocks these are made, carried, set down and picked up again by players. Setting one down adds it to the
 * network under its owner's name; breaking it takes it out.
 */
public class FieldBlock extends Block {
    private static final VoxelShape RELAY = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(5, 4, 5, 11, 16, 11));
    private static final VoxelShape JAMMER = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(4, 6, 4, 12, 12, 12));
    private static final VoxelShape WAYGATE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 16, 12));

    private final Network.Kind kind;

    public FieldBlock(Network.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Network.Kind kind() {
        return kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case RELAY -> RELAY;
            case JAMMER -> JAMMER;
            case WAYGATE -> WAYGATE;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)) return;
        Network.Node node = Network.register(serverLevel, pos, kind, player.getUUID());
        if (kind == Network.Kind.WAYGATE) Network.discover(player, node);
        serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.2, 1.0, 0.2, 0.05);
        serverLevel.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.2F);
        Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".field.placed." + kind.id(), kind.range), 0x55FFFF));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) Network.unregister(serverLevel, pos, kind);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** What a right-click tells: who set it down and what it does. A waygate opens its travel screen. */
    public static void inspect(ServerLevel level, BlockPos pos, ServerPlayer player, Network.Kind kind) {
        Network.Node node = Network.at(level, pos, kind);
        if (node == null) {
            // set down by something other than a player's hand: it joins the network under whoever touches it first
            if (Realm.of(level.dimension()) != null) return;
            node = Network.register(level, pos, kind, player.getUUID());
        }
        if (kind == Network.Kind.WAYGATE) {
            if (Network.discover(player, node)) {
                Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".waygate.found"), 0x55FF55));
                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            Screens.show(player, Screens.waygate(player, level, pos));
            com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.WAYGATE);
            return;
        }
        boolean mine = node.owner().equals(player.getUUID());
        Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".field.status." + kind.id() + (mine ? ".mine" : ""), kind.range), 0x55FFFF));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) inspect(serverLevel, pos, sp, kind);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        switch (kind) {
            case RELAY -> {
                // a thin beam going up, so a relay can be picked out from a distance
                level.addParticle(ParticleTypes.END_ROD, x, y + random.nextDouble() * 3.0, z, 0, 0.08, 0);
            }
            case JAMMER -> {
                if (random.nextInt(2) == 0) {
                    double a = random.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * 0.6, y - 0.2, z + Math.sin(a) * 0.6, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
                }
            }
            case WAYGATE -> {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.PORTAL, x + Math.cos(a) * 0.9, y - 0.6 + random.nextDouble(), z + Math.sin(a) * 0.9, -Math.cos(a) * 0.6, 0.1, -Math.sin(a) * 0.6);
            }
        }
    }
}
