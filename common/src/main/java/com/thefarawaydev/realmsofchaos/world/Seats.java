package com.thefarawaydev.realmsofchaos.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * Sitting on the mod's chairs and benches: an unseen seat is put down on the block and the player rides it. A seat
 * nobody sits on any more, or whose chair is gone, is cleared away.
 */
public final class Seats {
    public static final String TAG = "realms_of_chaos_seat";
    private static final List<Entity> SEATS = new ArrayList<>();

    private Seats() {}

    /** Sits a player down on the block at {@code pos}, {@code height} above its floor. @return true if they sat */
    public static boolean sit(ServerLevel level, BlockPos pos, double height, ServerPlayer player) {
        if (player.isPassenger() || player.isShiftKeyDown() || player.isSpectator()) return false;
        AABB here = new AABB(pos);
        for (Entity old : level.getEntitiesOfClass(Display.ItemDisplay.class, here, e -> e.entityTags().contains(TAG))) {
            if (!old.getPassengers().isEmpty()) return false;              // (somebody is already sitting there)
            old.discard();
        }
        Display.ItemDisplay seat = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (seat == null) return false;
        seat.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        seat.addTag(TAG);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            return false;
        }
        // (sat down facing the way the chair does)
        net.minecraft.world.level.block.state.BlockState chair = level.getBlockState(pos);
        if (chair.hasProperty(com.thefarawaydev.realmsofchaos.block.Furniture.FACING)) {
            float yaw = chair.getValue(com.thefarawaydev.realmsofchaos.block.Furniture.FACING).toYRot();
            seat.setYRot(yaw);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            if (player.connection != null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket(yaw, false, player.getXRot(), true));
        }
        SEATS.add(seat);
        return true;
    }

    /** Clears away seats nobody is on, or whose chair has gone. Run once a second. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) sweep();
    }

    /** Clears away, now, every seat nobody is on or whose chair has gone. */
    public static void sweep() {
        SEATS.removeIf(seat -> {
            if (seat.isRemoved()) return true;
            BlockPos at = seat.blockPosition();
            boolean chair = seat.level().getBlockState(at).getBlock() instanceof com.thefarawaydev.realmsofchaos.block.Furniture.Seat;
            if (seat.getPassengers().isEmpty() || !chair) {
                seat.ejectPassengers();
                seat.discard();
                return true;
            }
            return false;
        });
    }

    /** How many seats are out. */
    public static int count() {
        SEATS.removeIf(Entity::isRemoved);
        return SEATS.size();
    }
}
