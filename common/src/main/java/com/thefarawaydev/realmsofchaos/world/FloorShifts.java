package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Living floors. During a trial a floor's terrain shifts into its alternate layout and back: a pit rises into a
 * dais, a hall folds into terraces. The ground moves one block at a time (twice a second), anything standing on a
 * rising column is carried up with it, and no step ever becomes taller than one block. When the fight is over the
 * floor settles back into its own design.
 */
public final class FloorShifts {
    private static final int STEP_INTERVAL = 10;
    private static final Map<String, Shift> ACTIVE = new HashMap<>();
    private static int ticker;

    private static final class Shift {
        final ResourceKey<Level> dimension;
        final Line line;
        final int floor;
        int position;        // 0 = the floor's own layout .. MORPH_STEPS = fully alternate
        int target;

        Shift(ResourceKey<Level> dimension, Line line, int floor) {
            this.dimension = dimension;
            this.line = line;
            this.floor = floor;
        }
    }

    private FloorShifts() {}

    private static AABB room(int floor) {
        int y0 = Tower.floorY(floor);
        return new AABB(-Tower.WALL_OUTER, y0 - Tower.DEPTH, -Tower.WALL_OUTER, Tower.WALL_OUTER, y0 + Tower.ROOM_HEIGHT + 1, Tower.WALL_OUTER);
    }

    /** True while the floor is, or is on its way to being, in its alternate shape. */
    public static boolean isShifted(Line line, int floor) {
        Shift s = ACTIVE.get(TowerBuilder.floorKey(line, floor));
        return s != null && s.target > 0;
    }

    public static int position(Line line, int floor) {
        Shift s = ACTIVE.get(TowerBuilder.floorKey(line, floor));
        return s == null ? 0 : s.position;
    }

    /** Starts the floor moving towards its other shape. */
    public static void toggle(ServerLevel level, Line line, int floor) {
        set(level, line, floor, !isShifted(line, floor));
    }

    public static void set(ServerLevel level, Line line, int floor, boolean alternate) {
        String key = TowerBuilder.floorKey(line, floor);
        Shift s = ACTIVE.computeIfAbsent(key, k -> new Shift(level.dimension(), line, floor));
        int target = alternate ? TowerBuilder.MORPH_STEPS : 0;
        if (s.target == target && (s.position != 0 || target != 0)) return;
        s.target = target;
        if (alternate) ProgressData.get(level.getServer()).markShifted(key);
        if (s.position != target) announce(level, floor);
    }

    private static void announce(ServerLevel level, int floor) {
        BlockPos centre = BlockPos.containing(Tower.centre(level, floor));
        level.playSound(null, centre, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, centre, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, room(floor), Player::isAlive)) {
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".shift"), 0xFFAA00));
        }
    }

    /** After a restart nothing is tracked in memory: a floor recorded as shifted is rebuilt to its own design. */
    public static void repairIfStale(ServerLevel level, Line line, int floor) {
        String key = TowerBuilder.floorKey(line, floor);
        ProgressData data = ProgressData.get(level.getServer());
        if (data.isShifted(key) && !ACTIVE.containsKey(key)) {
            TowerBuilder.buildFloor(level, line, floor);
            data.clearShifted(key);
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty() || ++ticker % STEP_INTERVAL != 0) return;
        stepAll(server, ticker % (STEP_INTERVAL * 2) == 0);
    }

    /** Advances every moving floor by one block; once a second also sends idle shifted floors home. */
    public static void stepAll(MinecraftServer server, boolean checkIdle) {
        for (Iterator<Map.Entry<String, Shift>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Shift> entry = it.next();
            Shift s = entry.getValue();
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            if (checkIdle && s.target > 0 && level.getEntitiesOfClass(GodBoss.class, room(s.floor), GodBoss::isAlive).isEmpty()) {
                s.target = 0;                       // the fight is over: settle back
                if (s.position != 0) announce(level, s.floor);
            }
            if (s.position != s.target) {
                int next = s.position + Integer.signum(s.target - s.position);
                TowerBuilder.reshape(level, s.line, s.floor, TowerBuilder.heights(s.floor, s.position), TowerBuilder.heights(s.floor, next));
                s.position = next;
                carry(level, s.floor);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.DUST.type(), 0.5, Tower.floorY(s.floor) + 1.5, 0.5, 40, 7.0, 0.6, 7.0, 0.01);
            }
            if (s.position == 0 && s.target == 0) {
                ProgressData.get(server).clearShifted(entry.getKey());
                it.remove();
            }
        }
    }

    /** Anything the rising ground moved into is lifted on top of it, so nobody is ever buried by a shift. */
    private static void carry(ServerLevel level, int floor) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, room(floor), en -> !en.isSpectator())) Fx.unbury(level, e, 3);
    }
}
