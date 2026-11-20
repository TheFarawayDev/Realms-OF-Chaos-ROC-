package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.entity.RelicProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ranged attacks shared by players and gods: bows loose real arrows, everything else a {@link RelicProjectile}. */
public final class Ranged {
    private Ranged() {}

    /**
     * Fires one shot of {@code god}'s weapon. With a {@code target} the shot is aimed at it (gods); without, it flies
     * where the shooter is looking (players).
     */
    public static Projectile fire(ServerLevel level, LivingEntity shooter, God god, ItemStack weapon, float damage, LivingEntity target) {
        Projectile projectile;
        SoundEvent sound;
        float speed;
        if (god.kind() == WeaponKind.BOW) {
            Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), weapon.isEmpty() ? null : weapon);
            arrow.setBaseDamage(damage / 2.4);              // arrows multiply base damage by their speed
            EffectSpec.addTo(arrow, god);
            if (god.power() == Power.FIRE) arrow.igniteForSeconds(100);
            projectile = arrow;
            sound = SoundEvents.ARROW_SHOOT;
            speed = 2.4F;
        } else {
            ItemStack display = god.kind() == WeaponKind.THROWN && !weapon.isEmpty() ? weapon.copyWithCount(1) : new ItemStack(ModContent.orbItem(god.power()));
            RelicProjectile bolt = new RelicProjectile(level, shooter, display);
            bolt.setGod(god);
            bolt.setDamage(damage);
            projectile = bolt;
            sound = god.kind() == WeaponKind.THROWN ? SoundEvents.ENDER_PEARL_THROW : SoundEvents.ILLUSIONER_PREPARE_MIRROR;
            speed = 1.8F;
        }
        if (target == null) {
            projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0.0F, speed, 1.0F);
        } else {
            double dx = target.getX() - shooter.getX(), dz = target.getZ() - shooter.getZ();
            double dy = target.getY(0.5) - projectile.getY();
            double lift = projectile instanceof Arrow ? Math.sqrt(dx * dx + dz * dz) * 0.12 : 0.0;
            projectile.shoot(dx, dy + lift, dz, speed * 0.85F, 4.0F);
        }
        level.addFreshEntity(projectile);
        level.playSound(null, shooter.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.1F);
        return projectile;
    }
}
