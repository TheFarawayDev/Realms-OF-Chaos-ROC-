package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The fourteen kinds of signature move. Every god has one, under its own name, and its relic weapon casts the same
 * move for whoever wields it - so a move is written once, for any living user: a god aims at its target, a player
 * at whatever they are looking at. Each move also carries the god's hit power and curse, which is what makes
 * Ares' charge different from Zagreus'.
 */
public enum Signature {
    STORM, CHARGE, VOLLEY, VORTEX, NOVA, BLINK, MEND, SLUMBER, REAP, CURSE, FROST, GALE, WARD, ERUPTION;

    public Component description() {
        return Component.translatable("move." + RealmsOfChaos.MOD_ID + ".kind." + name().toLowerCase());
    }

    // ---- who and where ---------------------------------------------------------------------------------------

    public static List<LivingEntity> foes(ServerLevel level, LivingEntity user, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> Power.canHurt(user, e));
    }

    /** What the user is aiming at: a mob's target, or the foe closest to the middle of a player's view. */
    public static LivingEntity aim(ServerLevel level, LivingEntity user, double range) {
        if (user instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive() ? target : null;
        }
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.85;                                  // within about thirty degrees of the crosshair
        for (LivingEntity e : foes(level, user, range)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() < 0.01) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot && user.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    /** The horizontal direction the user is attacking in. */
    private static Vec3 facing(LivingEntity user, LivingEntity aim) {
        return flat(aim != null ? aim.position().subtract(user.position()) : user.getLookAngle());
    }

    // ---- small building blocks -------------------------------------------------------------------------------

    private static void strike(ServerLevel level, LivingEntity user, LivingEntity target, God god, float damage) {
        target.hurtServer(level, Power.source(level, user), damage);
        god.power().hit(level, target, user, god);
        god.applyTargetEffect(target);
    }

    private static void push(LivingEntity e, double x, double y, double z) {
        e.setDeltaMovement(e.getDeltaMovement().add(x, y, z));
        e.needsSync = true;
    }

    private static void sound(ServerLevel level, LivingEntity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.blockPosition(), sound, at instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, volume, pitch);
    }

    public static ParticleOptions spark(Line line) {
        return switch (line) {
            case ZEUS -> ParticleTypes.ELECTRIC_SPARK;
            case POSEIDON -> ParticleTypes.SPLASH;
            case HADES -> ParticleTypes.SOUL_FIRE_FLAME;
        };
    }

    private static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, double height) {
        int n = Math.max(10, (int) (radius * 7));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y + height, centre.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void bolt(ServerLevel level, double x, double y, double z) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setPos(x, y, z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** Removes every harmful status effect. */
    public static void cleanse(LivingEntity user) {
        for (MobEffectInstance effect : new ArrayList<>(user.getActiveEffects())) {
            if (!effect.getEffect().value().isBeneficial()) user.removeEffect(effect.getEffect());
        }
    }

    private static boolean fits(ServerLevel level, LivingEntity user, Vec3 at) {
        return level.noCollision(user, user.getBoundingBox().move(at.subtract(user.position())));
    }

    /**
     * Where the user can stand at this x/z coming from height {@code at.y}: level, one block up (a step), or one
     * block down when the ground falls away by exactly one. Null if the way is blocked.
     */
    private static Vec3 foothold(ServerLevel level, LivingEntity user, Vec3 at) {
        if (fits(level, user, at)) {
            Vec3 down = at.add(0, -1, 0);
            return fits(level, user, down) && !fits(level, user, at.add(0, -2, 0)) ? down : at;
        }
        Vec3 up = at.add(0, 1, 0);
        return fits(level, user, up) ? up : null;
    }

    // ---- the moves -------------------------------------------------------------------------------------------

    /**
     * Server side. Performs this move for {@code user}.
     *
     * @param damage the move's base damage; each move scales it to suit itself
     * @return false if the move could not be made (a blink with nobody to blink to) - nothing happened
     */
    public boolean perform(ServerLevel level, LivingEntity user, God god, float damage) {
        LivingEntity aim = aim(level, user, 18.0);
        ParticleOptions spark = spark(god.line());
        Vec3 at = user.position();
        boolean player = user instanceof Player;
        switch (this) {
            case STORM -> {
                for (LivingEntity e : foes(level, user, 14.0)) {
                    bolt(level, e.getX(), e.getY(), e.getZ());
                    strike(level, user, e, god, damage);
                }
                for (int i = 0; i < 3; i++) {
                    double a = user.getRandom().nextDouble() * Math.PI * 2, r = 4.0 + user.getRandom().nextDouble() * 5.0;
                    bolt(level, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.2, at.z, 80, 3.0, 1.5, 3.0, 0.2);
                sound(level, user, SoundEvents.TRIDENT_THUNDER.value(), 1.6F, 1.0F);
            }
            case CHARGE -> {
                Vec3 dir = facing(user, aim);
                double reach = aim == null ? 8.0 : Math.min(10.0, Math.max(0.0, flatDistance(user, aim) - 1.2));
                Vec3 end = at;
                for (double d = 0.5; d <= reach; d += 0.5) {
                    // the charge follows the ground: up a step, down a step, but not through a wall
                    Vec3 step = foothold(level, user, new Vec3(at.x + dir.x * d, end.y, at.z + dir.z * d));
                    if (step == null) break;
                    end = step;
                    level.sendParticles(ParticleTypes.CLOUD, step.x, step.y + 0.4, step.z, 2, 0.2, 0.2, 0.2, 0.01);
                    level.sendParticles(spark, step.x, step.y + 1.0, step.z, 3, 0.2, 0.4, 0.2, 0.02);
                }
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, end).inflate(1.8, 1.5, 1.8), x -> Power.canHurt(user, x))) {
                    strike(level, user, e, god, damage * 1.25F);
                    double s = e.position().subtract(at).dot(side) >= 0 ? 0.6 : -0.6;
                    push(e, side.x * s + dir.x * 0.4, 0.35, side.z * s + dir.z * 0.4);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY(1.0), e.getZ(), 1, 0, 0, 0, 0);
                }
                if (end != at) {
                    user.teleportTo(end.x, end.y, end.z);
                    user.resetFallDistance();
                }
                sound(level, user, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
                sound(level, user, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.8F);
            }
            case VOLLEY -> {
                for (int i = -2; i <= 2; i++) {
                    Ranged.fire(level, user, god, user.getMainHandItem(), damage * 0.6F, player ? null : aim, i * 9.0F);
                }
                level.sendParticles(spark, at.x, at.y + 1.4, at.z, 20, 0.4, 0.3, 0.4, 0.05);
            }
            case VORTEX -> {
                for (LivingEntity e : foes(level, user, 12.0)) {
                    // hurt first: a hit knocks its victim away from the attacker, and the pull has to win
                    e.hurtServer(level, Power.source(level, user), damage * 0.5F);
                    Vec3 to = at.subtract(e.position());
                    double d = Math.sqrt(to.x * to.x + to.z * to.z);
                    if (d > 1.5) {
                        e.setDeltaMovement(to.x / d * 1.1, 0.3, to.z / d * 1.1);
                        e.needsSync = true;
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
                for (double r = 2.0; r <= 10.0; r += 2.0) ring(level, spark, at, r, 0.3 + r * 0.08);
                sound(level, user, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.5F, 0.7F);
                Scheduler.after(level.getServer(), 14, () -> {
                    if (!user.isAlive() || user.level() != level) return;
                    for (LivingEntity e : foes(level, user, 4.5)) strike(level, user, e, god, damage);
                    level.sendParticles(ParticleTypes.EXPLOSION, user.getX(), user.getY() + 1.0, user.getZ(), 6, 1.5, 0.5, 1.5, 0.0);
                    ring(level, spark, user.position(), 4.5, 0.5);
                    sound(level, user, SoundEvents.GENERIC_SPLASH, 1.5F, 0.6F);
                });
            }
            case NOVA -> {
                for (LivingEntity e : foes(level, user, 7.0)) {
                    strike(level, user, e, god, damage);
                    Vec3 away = flat(e.position().subtract(at));
                    push(e, away.x * 1.3, 0.45, away.z * 1.3);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1.0, at.z, 4, 0.6, 0.3, 0.6, 0.0);
                ring(level, ParticleTypes.CLOUD, at, 3.0, 0.3);
                ring(level, spark, at, 5.0, 0.4);
                ring(level, ParticleTypes.CLOUD, at, 7.0, 0.3);
                sound(level, user, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F);
            }
            case BLINK -> {
                LivingEntity target = aim;
                if (target == null) {
                    double best = Double.MAX_VALUE;
                    for (LivingEntity e : foes(level, user, 16.0)) {
                        double d = e.distanceToSqr(user);
                        if (d < best && user.hasLineOfSight(e)) {
                            best = d;
                            target = e;
                        }
                    }
                }
                if (target == null) return false;
                Vec3 back = flat(target.getLookAngle()), side = new Vec3(-back.z, 0, back.x), t = target.position();
                for (Vec3 around : new Vec3[]{t.subtract(back.scale(1.6)), t.add(side.scale(1.6)), t.subtract(side.scale(1.6)), t.add(back.scale(1.6))}) {
                    Vec3 spot = foothold(level, user, around);
                    if (spot == null) continue;
                    level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 40, 0.3, 0.8, 0.3, 0.4);
                    user.teleportTo(spot.x, spot.y, spot.z);
                    user.resetFallDistance();
                    break;
                }
                user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                strike(level, user, target, god, damage * 1.6F);
                level.sendParticles(ParticleTypes.PORTAL, user.getX(), user.getY() + 1.0, user.getZ(), 40, 0.3, 0.8, 0.3, 0.4);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 25, 0.3, 0.5, 0.3, 0.3);
                sound(level, user, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.2F);
                sound(level, user, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.9F);
            }
            case MEND -> {
                float heal = player ? 6.0F + damage * 0.6F : Math.max(6.0F, user.getMaxHealth() * 0.05F);
                user.heal(heal);
                cleanse(user);
                user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (player) {
                    for (Player ally : level.getEntitiesOfClass(Player.class, user.getBoundingBox().inflate(8.0), p -> p != user && p.isAlive())) {
                        ally.heal(heal * 0.5F);
                        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
                        level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2.0, ally.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
                    }
                }
                for (LivingEntity e : foes(level, user, 7.0)) {
                    e.hurtServer(level, Power.source(level, user), damage * 0.4F);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                    god.applyTargetEffect(e);
                }
                level.sendParticles(ParticleTypes.HEART, at.x, at.y + 2.0, at.z, 8, 0.5, 0.3, 0.5, 0.0);
                ring(level, ParticleTypes.HAPPY_VILLAGER, at, 2.0, 0.4);
                ring(level, ParticleTypes.HAPPY_VILLAGER, at, 3.5, 1.2);
                sound(level, user, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.8F);
                sound(level, user, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.6F);
            }
            case SLUMBER -> {
                for (LivingEntity e : foes(level, user, 10.0)) {
                    strike(level, user, e, god, damage * 0.4F);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1));
                    if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                    if (e instanceof Mob mob && !(e instanceof GodBoss)) {
                        mob.setTarget(null);
                        mob.getNavigation().stop();
                    }
                    level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 3, 0.3, 0.2, 0.3, 0.5);
                }
                level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.0, at.z, 60, 3.0, 1.0, 3.0, 0.1);
                ring(level, ParticleTypes.WITCH, at, 6.0, 0.4);
                ring(level, ParticleTypes.WITCH, at, 10.0, 0.4);
                sound(level, user, SoundEvents.ILLUSIONER_CAST_SPELL, 1.5F, 0.6F);
            }
            case REAP -> {
                Vec3 dir = facing(user, aim);
                int hits = 0;
                for (LivingEntity e : foes(level, user, 5.5)) {
                    Vec3 to = e.position().subtract(at);
                    if (to.x * to.x + to.z * to.z > 0.25 && flat(to).dot(dir) < 0.2) continue;       // behind the reaper
                    strike(level, user, e, god, damage * 1.3F);
                    level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY(0.7), e.getZ(), 5, 0.2, 0.3, 0.2, 0.03);
                    hits++;
                }
                if (hits > 0) user.heal(hits * (player ? 2.0F + damage * 0.15F : Math.max(2.0F, user.getMaxHealth() * 0.02F)));
                for (int i = -4; i <= 4; i++) {
                    double a = Math.atan2(dir.z, dir.x) + i * 0.22;
                    for (double r : new double[]{2.2, 4.0}) {
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x + Math.cos(a) * r, at.y + 1.0, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
                sound(level, user, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
            }
            case CURSE -> {
                List<LivingEntity> marked = foes(level, user, 10.0);
                for (LivingEntity e : marked) {
                    e.hurtServer(level, Power.source(level, user), damage * 0.4F);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
                    god.applyTargetEffect(e);
                    level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
                }
                ring(level, ParticleTypes.WITCH, at, 2.5, 0.2);
                sound(level, user, SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.6F);
                // the hex bursts two seconds later - unless the cursed got well away from whoever laid it
                Scheduler.after(level.getServer(), 40, () -> {
                    if (!user.isAlive() || user.level() != level) return;
                    for (LivingEntity e : marked) {
                        if (!e.isAlive() || e.level() != level || !Power.canHurt(user, e) || e.distanceToSqr(user) > 14.0 * 14.0) continue;
                        strike(level, user, e, god, damage * 0.9F);
                        level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY(0.6), e.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                        level.playSound(null, e.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
                    }
                });
            }
            case FROST -> {
                for (LivingEntity e : foes(level, user, 8.0)) {
                    strike(level, user, e, god, damage * 0.8F);
                    e.setTicksFrozen(e.getTicksFrozen() + 200);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                }
                user.clearFire();
                for (double r = 2.0; r <= 8.0; r += 2.0) ring(level, ParticleTypes.SNOWFLAKE, at, r, 0.3);
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0, at.z, 60, 3.0, 0.8, 3.0, 0.05);
                sound(level, user, SoundEvents.GLASS_BREAK, 1.4F, 0.6F);
                sound(level, user, SoundEvents.POWDER_SNOW_BREAK, 1.6F, 0.8F);
            }
            case GALE -> {
                for (LivingEntity e : foes(level, user, 8.0)) {
                    strike(level, user, e, god, damage * 0.6F);
                    Vec3 away = flat(e.position().subtract(at));
                    push(e, away.x * 0.4, 0.95, away.z * 0.4);
                    level.sendParticles(ParticleTypes.GUST, e.getX(), e.getY() + 0.2, e.getZ(), 1, 0, 0, 0, 0);
                }
                user.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 50, 3.0, 0.3, 3.0, 0.08);
                ring(level, ParticleTypes.SMALL_GUST, at, 4.0, 0.6);
                sound(level, user, SoundEvents.WIND_CHARGE_BURST.value(), 1.6F, 0.9F);
            }
            case WARD -> {
                cleanse(user);
                user.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, player ? 1 : 2));
                user.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 1));
                for (LivingEntity e : foes(level, user, 5.0)) {
                    strike(level, user, e, god, damage * 0.5F);
                    Vec3 away = flat(e.position().subtract(at));
                    push(e, away.x * 0.9, 0.3, away.z * 0.9);
                }
                for (double h = 0.2; h <= 2.0; h += 0.6) ring(level, ParticleTypes.END_ROD, at, 1.6, h);
                ring(level, ParticleTypes.ENCHANTED_HIT, at, 5.0, 0.5);
                sound(level, user, SoundEvents.SHIELD_BLOCK.value(), 1.4F, 0.7F);
                sound(level, user, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
            }
            case ERUPTION -> {
                List<Vec3> marks = new ArrayList<>();
                for (LivingEntity e : foes(level, user, 16.0)) {
                    if (marks.size() < 8) marks.add(e.position());
                }
                if (marks.isEmpty()) marks.add(at.add(facing(user, aim).scale(5.0)));
                // the ground warns first: rings gather where it will break, and there is a moment to step out
                for (int delay = 0; delay <= 12; delay += 6) {
                    Runnable warn = () -> {
                        for (Vec3 m : marks) ring(level, spark, m, 2.5, 0.15);
                    };
                    if (delay == 0) warn.run();
                    else Scheduler.after(level.getServer(), delay, warn);
                }
                sound(level, user, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.6F, 0.6F);
                Scheduler.after(level.getServer(), 18, () -> {
                    if (!user.isAlive() || user.level() != level) return;
                    Set<LivingEntity> struck = new HashSet<>();
                    for (Vec3 m : marks) {
                        AABB area = new AABB(m.x - 2.6, m.y - 1.5, m.z - 2.6, m.x + 2.6, m.y + 3.5, m.z + 2.6);
                        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, x -> Power.canHurt(user, x))) {
                            if (!struck.add(e)) continue;
                            strike(level, user, e, god, damage * 1.4F);
                            push(e, 0, 0.6, 0);
                        }
                        for (double h = 0.2; h <= 3.2; h += 0.5) {
                            level.sendParticles(spark, m.x, m.y + h, m.z, 10, 0.8, 0.1, 0.8, 0.05);
                        }
                        level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.8, m.z, 2, 0.5, 0.2, 0.5, 0.0);
                        level.sendParticles(ParticleTypes.LAVA, m.x, m.y + 0.3, m.z, 8, 0.8, 0.1, 0.8, 0.0);
                        level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
                    }
                });
            }
        }
        god.applySelfEffect(user);
        return true;
    }

    private static double flatDistance(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
