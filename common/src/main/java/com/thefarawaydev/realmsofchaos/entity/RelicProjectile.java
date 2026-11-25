package com.thefarawaydev.realmsofchaos.entity;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Power;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Everything the mod shoots that is not an arrow: staff bolts, thrown thunderbolts and javelins, Hades' tarot cards.
 * It is drawn as the model of whatever item it carries, lying along its flight; it flies almost straight, and on
 * hitting a valid target deals its damage and applies its god's power. A bolt from a relic that has been levelled
 * also breaks the ground where it comes down - whether it found anyone or not - and the crater it leaves is wider
 * the higher the relic's level.
 */
public class RelicProjectile extends ThrowableItemProjectile {
    private int godIndex = -1;
    private float damage = 4.0F;
    private Holder<MobEffect> curse;
    /** How wide a crater this bolt leaves where it lands; 0 for none. */
    private int crater;
    /** Extra things a hit or a landing does. Not saved: a bolt lives four seconds. */
    private java.util.function.Consumer<LivingEntity> rider;
    private java.util.function.Consumer<net.minecraft.world.phys.Vec3> landing;

    public RelicProjectile(EntityType<? extends RelicProjectile> type, Level level) {
        super(type, level);
    }

    public RelicProjectile(Level level, LivingEntity owner, ItemStack display) {
        super(ModContent.RELIC_PROJECTILE.get(), owner, level, display);
    }

    public void setGod(God god) {
        this.godIndex = god == null ? -1 : god.index();
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float damage() {
        return damage;
    }

    public void setCrater(int radius) {
        this.crater = Math.max(0, radius);
    }

    public int crater() {
        return crater;
    }

    /** Something more to do to whoever the bolt hits, after its damage. */
    public void setRider(java.util.function.Consumer<LivingEntity> rider) {
        this.rider = rider;
    }

    /** Something to do where the bolt comes down, whatever it hit. */
    public void setLanding(java.util.function.Consumer<net.minecraft.world.phys.Vec3> landing) {
        this.landing = landing;
    }

    /** An extra harmful effect applied on hit (used by thrown tarot cards). */
    public void setCurse(Holder<MobEffect> curse) {
        this.curse = curse;
    }

    @Override
    protected Item getDefaultItem() {
        return ModContent.orbItem(Power.NONE);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.01;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 80) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level)) return;
        Entity hit = result.getEntity();
        Entity owner = getOwner();
        if (hit instanceof LivingEntity target && owner instanceof LivingEntity shooter) {
            impact(level, shooter, target);
        }
    }

    /** What a hit does: damage, the god's power and curse, and any card curse. Friendly targets are spared. */
    public void impact(ServerLevel level, LivingEntity shooter, LivingEntity target) {
        if (!Power.canHurt(shooter, target)) return;
        // a bolt met at the right moment goes back to whoever loosed it
        if (target instanceof net.minecraft.server.level.ServerPlayer met && !(shooter instanceof net.minecraft.world.entity.player.Player)
                && com.thefarawaydev.realmsofchaos.world.Combat.tryParry(level, met, null)) {
            shooter.hurtServer(level, damageSources().thrown(this, met), damage);
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type(), shooter.getX(), shooter.getY() + 1.0, shooter.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
            return;
        }
        target.hurtServer(level, damageSources().thrown(this, shooter), damage);
        if (godIndex >= 0) {
            God god = Gods.byIndex(godIndex);
            god.power().hit(level, target, shooter, god);
            god.applyTargetEffect(target);
        }
        if (curse != null) target.addEffect(new MobEffectInstance(curse, 100, 1));
        if (rider != null && target.isAlive()) rider.accept(target);
    }

    /** What coming down does, whatever was hit: the landing hook, and the crater of a levelled relic's bolt. */
    public void land(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        if (landing != null) landing.accept(at);
        if (crater > 0) {
            com.thefarawaydev.realmsofchaos.world.Craters.blast(level, at, crater, getOwner() instanceof LivingEntity shooter ? shooter : null, damage * 0.35F);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.DUST.type(), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            land(level, result.getLocation());
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("God", godIndex);
        output.putInt("Damage", Math.round(damage * 100));
        output.putInt("Crater", crater);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.godIndex = input.getIntOr("God", -1);
        this.damage = input.getIntOr("Damage", 400) / 100.0F;
        this.crater = input.getIntOr("Crater", 0);
    }
}
