package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Signature;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Patches of ground that hurt to stand on, sown by the towers' trap-layers: a crackling field under Zeus that
 * throws you into the air, brine under Poseidon that clings to your legs, miasma under Hades that withers. A
 * patch is only light and harm - no block is changed - it lasts a few seconds, and only players feel it.
 */
public final class Hazards {
    private static final int MAX = 64;

    public static final class Patch {
        final ServerLevel level;
        final Vec3 at;
        final double radius;
        final Line line;
        final float damage;
        int ticks;

        Patch(ServerLevel level, Vec3 at, double radius, int ticks, Line line, float damage) {
            this.level = level;
            this.at = at;
            this.radius = radius;
            this.ticks = ticks;
            this.line = line;
            this.damage = damage;
        }

        public Vec3 at() {
            return at;
        }

        public Line line() {
            return line;
        }
    }

    private static final List<Patch> ACTIVE = new ArrayList<>();

    private Hazards() {}

    public static Patch add(ServerLevel level, Vec3 at, double radius, int ticks, Line line, float damage) {
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        Patch patch = new Patch(level, at, radius, ticks, line, damage);
        ACTIVE.add(patch);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.6F);
        return patch;
    }

    public static List<Patch> near(ServerLevel level, Vec3 at, double within) {
        List<Patch> out = new ArrayList<>();
        for (Patch p : ACTIVE) if (p.level == level && p.at.distanceTo(at) <= within) out.add(p);
        return out;
    }

    /** One second of a patch: everyone standing in it is hurt and marked. @return how many were. */
    public static int bite(Patch patch) {
        int bitten = 0;
        AABB box = new AABB(patch.at.x - patch.radius, patch.at.y - 1.0, patch.at.z - patch.radius, patch.at.x + patch.radius, patch.at.y + 2.5, patch.at.z + patch.radius);
        for (Player p : patch.level.getEntitiesOfClass(Player.class, box, e -> e.isAlive() && !e.isCreative() && !e.isSpectator())) {
            double dx = p.getX() - patch.at.x, dz = p.getZ() - patch.at.z;
            if (dx * dx + dz * dz > patch.radius * patch.radius) continue;
            p.hurtServer(patch.level, patch.level.damageSources().magic(), patch.damage);
            switch (patch.line) {
                case ZEUS -> {
                    p.setDeltaMovement(p.getDeltaMovement().x, 0.55, p.getDeltaMovement().z);
                    p.needsSync = true;
                }
                case POSEIDON -> p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 2));
                case HADES -> p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            }
            bitten++;
        }
        return bitten;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Patch> it = ACTIVE.iterator(); it.hasNext(); ) {
            Patch patch = it.next();
            if (--patch.ticks <= 0) {
                it.remove();
                continue;
            }
            if (patch.ticks % 5 == 0) {
                int points = (int) (patch.radius * 6);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points + patch.ticks * 0.05;
                    patch.level.sendParticles(Signature.spark(patch.line), patch.at.x + Math.cos(a) * patch.radius, patch.at.y + 0.15, patch.at.z + Math.sin(a) * patch.radius, 1, 0, 0, 0, 0);
                }
                patch.level.sendParticles(Signature.spark(patch.line), patch.at.x, patch.at.y + 0.2, patch.at.z, 3, patch.radius * 0.5, 0.05, patch.radius * 0.5, 0.0);
            }
            if (patch.ticks % 20 == 0) bite(patch);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
