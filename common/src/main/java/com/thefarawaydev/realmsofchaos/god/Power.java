package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
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
        double x = target.getX(), y = target.getY(0.6), z = target.getZ();
        switch (this) {
            case LIGHTNING -> {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setPos(target.getX(), target.getY(), target.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                // for a player the bolt is a second hit in its own right; a god's is folded into the blow it rides on
                if (attacker instanceof Player) target.damageCooldownTime = 0;
                target.hurtServer(level, source(level, attacker), 3.0F + 6.0F * strength);
            }
            case FIRE -> {
                target.igniteForSeconds(3.0F + 5.0F * strength);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), x, y, z, 10, 0.25, 0.35, 0.25, 0.03);
            }
            case KNOCKBACK -> {
                Vec3 away = target.position().subtract(attacker.position());
                away = new Vec3(away.x, 0, away.z).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * (0.8 + strength), 0.35, away.z * (0.8 + strength)));
                target.needsSync = true;
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.DUST.type(), x, y, z, 8, 0.2, 0.2, 0.2, 0.06);
            }
            case DRAIN -> {
                attacker.heal(1.0F + 5.0F * strength);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 4, 0.2, 0.3, 0.2, 0.1);
                level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + attacker.getBbHeight() + 0.2, attacker.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
            case FREEZE -> {
                target.setTicksFrozen(target.getTicksFrozen() + 80 + (int) (140 * strength));
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.FROST.type(), x, y, z, 12, 0.25, 0.4, 0.25, 0.02);
            }
            case SWEEP -> {
                sweep(level, target, attacker, 3.5F * strength + 2.0F, 3.0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.6, 0.1, 0.6, 0.0);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type(), x, y, z, 20, 1.2, 0.3, 1.2, 0.2);
            }
            case NONE -> { }
        }
    }

    public static boolean canHurt(LivingEntity user, LivingEntity other) {
        if (other == user || !other.isAlive()) return false;
        if (other instanceof ArmorStand) return false;
        if (user instanceof Player) {
            // never other players, and never the wielder's own pets
            return !(other instanceof Player) && !(other instanceof TamableAnimal pet && pet.isOwnedBy(user));
        }
        if (user instanceof com.thefarawaydev.realmsofchaos.entity.GodBoss || user instanceof com.thefarawaydev.realmsofchaos.entity.Sentinel) {
            return other instanceof Player p && !p.isCreative() && !p.isSpectator();
        }
        return true;
    }

    private static void sweep(ServerLevel level, LivingEntity center, LivingEntity attacker, float dmg, double radius) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius), x -> x != center && canHurt(attacker, x))) {
            e.hurtServer(level, source(level, attacker), dmg);
        }
    }

    public static DamageSource source(ServerLevel level, LivingEntity attacker) {
        return attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
    }
}
