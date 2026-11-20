package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry and naming of the three towers. Each god line is a 15-floor tower: floor 1 holds the starter god
 * (rank 15) and floor 15, the open summit, holds the final god (rank 1). You climb one floor per god.
 */
public final class Tower {
    public static final int FLOORS = Line.RANKS;
    /** Walking level of floor 1. Terrain on a floor may rise or dip two blocks around this level. */
    public static final int BASE_Y = 28;
    public static final int SPACING = 20;
    /** Solid blocks below the walking level (also the ceiling thickness of the floor underneath). */
    public static final int DEPTH = 3;
    public static final int ROOM_HEIGHT = SPACING - DEPTH - 1;
    public static final int FIGHT_RADIUS = 17;
    public static final int WALL_INNER = 21;
    public static final double WALL_OUTER = 23.2;
    public static final float ARRIVAL_YAW = 180.0F;

    private Tower() {}

    public static int clampFloor(int floor) {
        return Math.max(1, Math.min(FLOORS, floor));
    }

    public static int floorY(int floor) {
        return BASE_Y + (clampFloor(floor) - 1) * SPACING;
    }

    /** Which floor a height belongs to (a floor owns its ground slab and the room above it). */
    public static int floorAt(double y) {
        return clampFloor(Math.floorDiv((int) Math.floor(y) - (BASE_Y - DEPTH), SPACING) + 1);
    }

    public static int floorOf(God god) {
        return FLOORS + 1 - god.rank();
    }

    public static God god(Line line, int floor) {
        return Gods.get(line, FLOORS + 1 - clampFloor(floor));
    }

    /** 1 = lower floors, 2 = middle, 3 = upper, 4 = the summit. Each tier has its own materials. */
    public static int tier(int floor) {
        return floor <= 5 ? 1 : floor <= 10 ? 2 : floor <= 14 ? 3 : 4;
    }

    public static Component tierName(Line line, int floor) {
        return Component.translatable("tier." + RealmsOfChaos.MOD_ID + "." + line.id() + "." + tier(floor));
    }

    public static Component towerName(Line line) {
        return Component.translatable("tower." + RealmsOfChaos.MOD_ID + "." + line.id());
    }

    /**
     * The floor a player is working on: the floor of their next trial, or the summit once the tower is conquered.
     * A floor is only reached by walking the passage that leads up to it, so a player who has beaten a floor's god
     * but not yet come out of its passage is still on that floor.
     */
    public static int currentFloor(ProgressData data, UUID player, Line line) {
        int next = data.nextRank(player, line);
        if (next == 0) return FLOORS;
        int floor = FLOORS + 1 - next;
        return floor > 1 && !data.hasPassed(player, line, floor - 1) ? floor - 1 : floor;
    }

    /**
     * May this player be on this floor by their own right? Yes for every floor up to the one they are working on,
     * and for a floor above that if they have beaten the god of the floor below it and walked the passage up - as
     * happens when a party carries someone past floors they have not done themselves.
     */
    public static boolean canStand(ProgressData data, UUID player, Line line, int floor) {
        floor = clampFloor(floor);
        if (floor <= currentFloor(data, player, line)) return true;
        return data.isDefeated(player, god(line, floor - 1)) && data.hasPassed(player, line, floor - 1);
    }

    /** Where players appear on a floor: a flat pad by the south gate, facing the altar. */
    public static Vec3 arrival(int floor) {
        return new Vec3(0.5, floorY(floor) + 1, 15.5);
    }

    public static BlockPos altarPos(int floor) {
        return new BlockPos(0, floorY(floor) + 1, 12);
    }

    /** The boss spot: on top of whatever terrain the floor has at its centre. */
    public static Vec3 centre(ServerLevel level, int floor) {
        int y0 = floorY(floor);
        for (int y = y0 + 3; y >= y0 - DEPTH; y--) {
            BlockPos p = new BlockPos(0, y, 0);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return new Vec3(0.5, y + 1, 0.5);
            }
        }
        return new Vec3(0.5, y0 + 1, 0.5);
    }
}
