package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The signature hit-effect of a god's weapon (also used by the boss that wields it). */
public enum Power {
    NONE, LIGHTNING, FIRE, KNOCKBACK, DRAIN, FREEZE, SWEEP;

    public Component description() {
        return Component.translatable("power." + RealmsOfChaos.MOD_ID + "." + name().toLowerCase());
    }

    /** Server side. Applies this power's bonus effect for a hit from {@code attacker} on {@code target}. */
    public void hit(ServerLevel level, LivingEntity target, LivingEntity attacker, God god) {
        float strength = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS; // 1/15 .. 1.0
        switch (this) {
            case LIGHTNING -> {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setPos(target.getX(), target.getY(), target.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                target.hurtServer(level, source(level, attacker), 3.0F + 6.0F * strength);
            }
            case FIRE -> target.igniteForSeconds(3.0F + 5.0F * strength);
            case KNOCKBACK -> {
                Vec3 away = target.position().subtract(attacker.position());
                away = new Vec3(away.x, 0, away.z).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * (0.8 + strength), 0.35, away.z * (0.8 + strength)));
                target.needsSync = true;
            }
            case DRAIN -> attacker.heal(1.0F + 5.0F * strength);
            case FREEZE -> target.setTicksFrozen(target.getTicksFrozen() + 80 + (int) (140 * strength));
            case SWEEP -> sweep(level, target, attacker, 3.5F * strength + 2.0F, 3.0);
            case NONE -> { }
        }
    }

    /** Server side. The right-click "Divine Surge": the power in an area around {@code center}. */
    public static void surge(ServerLevel level, LivingEntity user, God god) {
        double radius = 6.0;
        AABB box = user.getBoundingBox().inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> canHurt(user, x))) {
            e.hurtServer(level, source(level, user), 2.0F + (Line.RANKS + 1 - god.rank()) * 0.4F);
            god.power().hit(level, e, user, god);
            god.applyTargetEffect(e);
        }
        god.applySelfEffect(user);
    }

    static boolean canHurt(LivingEntity user, LivingEntity other) {
        if (other == user || !other.isAlive()) return false;
        if (user instanceof Player && other instanceof Player) return false;
        if (user instanceof com.thefarawaydev.realmsofchaos.entity.GodBoss && !(other instanceof Player)) return false;
        return true;
    }

    private static void sweep(ServerLevel level, LivingEntity center, LivingEntity attacker, float dmg, double radius) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), x -> x != center && canHurt(attacker, x))) {
            e.hurtServer(level, source(level, attacker), dmg);
        }
    }

    static DamageSource source(ServerLevel level, LivingEntity attacker) {
        return attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
    }
}
