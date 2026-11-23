package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Onslaughts: attacks that take the whole floor of a trial rather than the spot a challenger stands on. Shockwaves
 * that run out from the middle and have to be jumped; fissures that split the floor in a star; a cross, and then
 * a saltire; rings that go off in turn; a wall that sweeps the arena with one gap in it.
 *
 * <p>Every one is drawn on the ground before it lands - the floor itself gives it away, lit along the lines it is
 * about to break on - and every one leaves somewhere to stand. What a place is doing at a moment is worked out
 * from where it is and how long the onslaught has run ({@link #state}); nothing is stored per block.
 *
 * <p>Between onslaughts the floor is not still either: a {@link #ripple} of dust runs out over it, following the
 * shape of the ground, every few seconds of a trial.
 */
public final class Onslaughts {
    public static final int SAFE = 0, WARN = 1, LIVE = 2;
    /** How long a place that does not move is lit before it goes off. */
    public static final int WARN_TICKS = 28;
    /** How far from the middle of the floor an onslaught reaches. */
    public static final int REACH = 15;

    public enum Kind {
        /** Three rings run out from the middle, one after another. Jump them. */
        SHOCKWAVE(150),
        /** The floor splits along five lines from the middle; then along the five between. */
        FISSURES(86),
        /** A cross through the middle, from wall to wall; then a saltire. */
        CROSS(84),
        /** Every other ring about the middle; then the rings between. */
        RINGS(82),
        /** A wall crosses the floor from one side to the other. There is one gap in it. */
        SWEEP(140);

        public final int duration;

        Kind(int duration) {
            this.duration = duration;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Where the gap in a sweeping wall lies, to one side of the floor's middle. */
    public static double gap(double aim) {
        return Math.sin(aim * 7.0) * 6.0;
    }

    /**
     * What a place is doing: {@code dx}, {@code dz} from the middle of the floor, {@code t} ticks after the
     * onslaught began. {@code aim} turns the whole figure, so no two onslaughts lie the same way.
     */
    public static int state(Kind kind, double dx, double dz, int t, double aim) {
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > REACH || t < 0 || t >= kind.duration) return SAFE;
        switch (kind) {
            case SHOCKWAVE -> {
                if (t < WARN_TICKS) return dist <= 2.5 ? WARN : SAFE;      // (the middle gathers itself first)
                int best = SAFE;
                for (int ring = 0; ring < 3; ring++) {
                    double radius = (t - WARN_TICKS - ring * 24) * 0.36;
                    if (radius > REACH + 1) continue;
                    if (radius < 0) {
                        // (the middle gathers itself again before each ring leaves it)
                        if (radius >= -2.6 && dist <= 2.5) best = WARN;
                        continue;
                    }
                    if (Math.abs(dist - radius) <= 0.8) return LIVE;
                    if (dist > radius && dist - radius <= 3.2) best = WARN;
                }
                return best;
            }
            case FISSURES -> {
                for (int volley = 0; volley < 2; volley++) {
                    int rel = t - volley * 42;
                    if (rel < 0 || rel >= WARN_TICKS + 14) continue;
                    for (int i = 0; i < 5; i++) {
                        double a = aim + (i + volley * 0.5) * Math.PI * 2 / 5, along = dx * Math.cos(a) + dz * Math.sin(a), across = -dx * Math.sin(a) + dz * Math.cos(a);
                        if (along >= 1.0 && Math.abs(across) <= 0.9) return rel < WARN_TICKS ? WARN : LIVE;
                    }
                }
                return SAFE;
            }
            case CROSS -> {
                for (int volley = 0; volley < 2; volley++) {
                    int rel = t - volley * 40;
                    if (rel < 0 || rel >= WARN_TICKS + 14) continue;
                    double a = aim + volley * Math.PI / 4, along = dx * Math.cos(a) + dz * Math.sin(a), across = -dx * Math.sin(a) + dz * Math.cos(a);
                    if (Math.abs(across) <= 1.2 || Math.abs(along) <= 1.2) return rel < WARN_TICKS ? WARN : LIVE;
                }
                return SAFE;
            }
            case RINGS -> {
                int band = (int) (dist / 3.0), rel = t - ((band & 1) == 1 ? 0 : 38);
                return rel < 0 || rel >= WARN_TICKS + 14 ? SAFE : rel < WARN_TICKS ? WARN : LIVE;
            }
            case SWEEP -> {
                double along = dx * Math.cos(aim) + dz * Math.sin(aim), across = -dx * Math.sin(aim) + dz * Math.cos(aim);
                if (Math.abs(across - gap(aim)) <= 2.2) return SAFE;        // (the gap: always safe, all the way across)
                if (t < WARN_TICKS) return along <= -REACH + 3.5 ? WARN : SAFE;
                double wall = -REACH + (t - WARN_TICKS) * 0.3;
                if (Math.abs(along - wall) <= 0.9) return LIVE;
                return along > wall && along - wall <= 4.5 ? WARN : SAFE;
            }
        }
        return SAFE;
    }

    private static final class Run {
        ResourceKey<Level> dimension;
        GodBoss boss;
        Line line;
        Kind kind;
        Vec3 centre;
        long start;
        double aim, reach;
        float damage;
        final Map<UUID, Long> hit = new HashMap<>();
        /** How high the floor stands in each column, as last looked at: the ground an onslaught runs over is not flat. */
        final int[] heights = new int[(REACH * 2 + 1) * (REACH * 2 + 1)];
        long measured = -1;
    }

    private record Ripple(ResourceKey<Level> dimension, Vec3 centre, long start, Line line) {}

    private static final List<Run> RUNS = new ArrayList<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();

    private Onslaughts() {}

    public static boolean active(GodBoss boss) {
        for (Run run : RUNS) if (run.boss == boss) return true;
        return false;
    }

    public static int running() {
        return RUNS.size();
    }

    /**
     * Begins an onslaught over the floor about {@code centre}. @param boss whose it is (it ends if the god does),
     * or null @param reach no further than this from the middle: a floor that is collapsing has less of itself left
     */
    public static void begin(ServerLevel level, GodBoss boss, Line line, Kind kind, Vec3 centre, double reach, float damage) {
        Run run = new Run();
        run.dimension = level.dimension();
        run.boss = boss;
        run.line = line;
        run.kind = kind;
        run.centre = centre;
        run.start = level.getServer().getTickCount();
        run.aim = level.getRandom().nextDouble() * Math.PI * 2;
        run.reach = Math.min(REACH, reach);
        run.damage = damage;
        RUNS.add(run);
        level.playSound(null, BlockPos.containing(centre), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6F, 0.8F);
        Component hint = Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".onslaught." + kind.id()), 0xFFAA00);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(centre, centre).inflate(REACH + 6, 10, REACH + 6), Player::isAlive)) Hud.actionBar(p, hint);
    }

    /** A ripple of dust over the floor, out from {@code centre}: the ground of a trial is never quite still. Only for show. */
    public static void ripple(ServerLevel level, Vec3 centre, Line line) {
        if (RIPPLES.size() < 8) RIPPLES.add(new Ripple(level.dimension(), centre, level.getServer().getTickCount(), line));
    }

    private static void measure(ServerLevel level, Run run, long now) {
        run.measured = now;
        int w = REACH * 2 + 1, y0 = Mth.floor(run.centre.y);
        for (int gx = -REACH; gx <= REACH; gx++) {
            for (int gz = -REACH; gz <= REACH; gz++) {
                BlockPos stand = Arena.standing(level, Mth.floor(run.centre.x) + gx, y0, Mth.floor(run.centre.z) + gz);
                run.heights[(gx + REACH) * w + gz + REACH] = stand == null ? y0 : stand.getY();
            }
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
            Ripple ripple = it.next();
            ServerLevel level = server.getLevel(ripple.dimension());
            double radius = (now - ripple.start()) * 0.45;
            if (level == null || radius > REACH + 2 || now < ripple.start()) {
                it.remove();
                continue;
            }
            if ((now - ripple.start()) % 2 != 0 || radius < 1.0) continue;
            int points = (int) (radius * 3.5);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points + radius * 0.3, x = ripple.centre().x + Math.cos(a) * radius, z = ripple.centre().z + Math.sin(a) * radius;
                BlockPos stand = Arena.standing(level, Mth.floor(x), Mth.floor(ripple.centre().y), Mth.floor(z));
                if (stand == null) continue;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Fx.ground(level, stand)), x, stand.getY() + 0.1, z, 2, 0.15, 0.03, 0.15, 0.04);
                if (i % 4 == 0) level.sendParticles(Arena.hazardParticle(ripple.line()), x, stand.getY() + 0.15, z, 1, 0.05, 0.02, 0.05, 0.0);
            }
        }
        for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
            Run run = it.next();
            ServerLevel level = server.getLevel(run.dimension);
            int t = (int) (now - run.start);
            if (level == null || t < 0 || t >= run.kind.duration || (run.boss != null && (!run.boss.isAlive() || run.boss.level() != level))) {
                it.remove();
                continue;
            }
            if (run.measured < 0 || now - run.measured >= 20) measure(level, run, now);
            int w = REACH * 2 + 1, cx = Mth.floor(run.centre.x), cz = Mth.floor(run.centre.z);
            boolean thump = false;
            if (t % 2 == 0) {
                // what there is to see: the ground lit where it is about to break, and breaking where it does
                for (int gx = -REACH; gx <= REACH; gx++) {
                    for (int gz = -REACH; gz <= REACH; gz++) {
                        if (gx * gx + gz * gz > run.reach * run.reach) continue;
                        int state = state(run.kind, gx + 0.5, gz + 0.5, t, run.aim);
                        if (state == SAFE) continue;
                        double x = cx + gx + 0.5, y = run.heights[(gx + REACH) * w + gz + REACH], z = cz + gz + 0.5;
                        if (state == WARN) {
                            if (t % 4 == 0 && ((gx + gz + (t >> 2)) & 1) == 0) level.sendParticles(Arena.hazardParticle(run.line), x, y + 0.12, z, 1, 0.2, 0.02, 0.2, 0.0);
                        } else {
                            thump = true;
                            if (((gx + gz + (t >> 1)) & 1) == 0) {
                                level.sendParticles(Arena.hazardParticle(run.line), x, y + 0.4, z, 2, 0.25, 0.45, 0.25, 0.03);
                                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Fx.ground(level, BlockPos.containing(x, y, z))), x, y + 0.2, z, 3, 0.25, 0.15, 0.25, 0.1);
                            }
                            // (now and then a piece of the floor is thrown clear)
                            if (level.getRandom().nextInt(90) == 0) Fx.impact(level, new Vec3(x, y, z), 2);
                        }
                    }
                }
                if (thump && t % 8 == 0) level.playSound(null, BlockPos.containing(run.centre), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
            }
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(run.centre, run.centre).inflate(REACH + 1, 8, REACH + 1), e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
                double dx = p.getX() - (cx + 0.5), dz = p.getZ() - (cz + 0.5);
                if (dx * dx + dz * dz > run.reach * run.reach || state(run.kind, dx, dz, t, run.aim) != LIVE) continue;
                // (a wave along the ground passes under whoever is in the air)
                if ((run.kind == Kind.SHOCKWAVE || run.kind == Kind.SWEEP) && !p.onGround()) continue;
                Long last = run.hit.get(p.getUUID());
                if (last != null && now - last < 14) continue;
                run.hit.put(p.getUUID(), now);
                p.hurtServer(level, run.boss != null ? run.boss.damageSources().indirectMagic(run.boss, run.boss) : level.damageSources().magic(), run.damage);
                switch (run.line) {
                    case ZEUS -> p.setDeltaMovement(p.getDeltaMovement().x * 0.4, 0.7, p.getDeltaMovement().z * 0.4);
                    case POSEIDON -> {
                        p.setDeltaMovement(p.getDeltaMovement().x * 0.4, 0.45, p.getDeltaMovement().z * 0.4);
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
                    }
                    case HADES -> {
                        p.setDeltaMovement(p.getDeltaMovement().x * 0.4, 0.45, p.getDeltaMovement().z * 0.4);
                        p.setRemainingFireTicks(50);
                    }
                }
                p.needsSync = true;
            }
        }
    }

    /** Ends everything: between tests. */
    public static void clear() {
        RUNS.clear();
        RIPPLES.clear();
    }
}
