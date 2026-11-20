package com.thefarawaydev.realmsofchaos.entity;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.god.Signature;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The passages' own enemies. One entity type, twelve kinds: every tower has a soldier, an archer or caster, a brute
 * and a Keeper, each with its own look. Soldiers close in (the Shade steps behind you), casters stand off and
 * shoot, brutes hit slowly and send you flying, and a Keeper - one guards every seal - now and then lets loose a
 * small version of one of its tower's moves. All of them grow stronger higher up the tower.
 */
public class Sentinel extends Monster implements RangedAttackMob {
    public enum Role { SOLDIER, CASTER, BRUTE, KEEPER }

    public enum Kind {
        STORM_HOPLITE(Line.ZEUS, Role.SOLDIER), SKY_ARCHER(Line.ZEUS, Role.CASTER), THUNDER_BRUTE(Line.ZEUS, Role.BRUTE), KEEPER_SKY(Line.ZEUS, Role.KEEPER),
        TIDE_HOPLITE(Line.POSEIDON, Role.SOLDIER), SIREN(Line.POSEIDON, Role.CASTER), REEF_BRUTE(Line.POSEIDON, Role.BRUTE), KEEPER_TIDES(Line.POSEIDON, Role.KEEPER),
        SHADE(Line.HADES, Role.SOLDIER), LAMPAD(Line.HADES, Role.CASTER), TARTARUS_BRUTE(Line.HADES, Role.BRUTE), KEEPER_SHADES(Line.HADES, Role.KEEPER);

        public final Line line;
        public final Role role;

        Kind(Line line, Role role) {
            this.line = line;
            this.role = role;
        }

        public String id() {
            return name().toLowerCase();
        }

        public Component displayName() {
            return Component.translatable("entity." + RealmsOfChaos.MOD_ID + ".sentinel." + id());
        }

        public static Kind of(Line line, Role role) {
            for (Kind k : values()) if (k.line == line && k.role == role) return k;
            throw new IllegalArgumentException(line + " " + role);
        }
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(Sentinel.class, EntityDataSerializers.INT);
    private static final int MOVE_EVERY = 160, WARN_TICKS = 20;

    private int tier = 1;
    private Goal attackGoal;
    private int moveCooldown = 100;

    public Sentinel(EntityType<? extends Sentinel> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public Kind kind() {
        Kind[] kinds = Kind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_KIND), kinds.length)];
    }

    public int tier() {
        return tier;
    }

    /** Makes this the given kind of sentinel, as strong as the tower's tier (1-3) asks. */
    public void setUp(Kind kind, int tier) {
        this.entityData.set(DATA_KIND, kind.ordinal());
        this.tier = Math.max(1, Math.min(3, tier));
        configure(true);
    }

    public static double health(Role role, int tier) {
        double base = switch (role) {
            case SOLDIER -> 22.0;
            case CASTER -> 18.0;
            case BRUTE -> 40.0;
            case KEEPER -> 70.0;
        };
        return base * (1.0 + 0.35 * (tier - 1));
    }

    public static double damage(Role role, int tier) {
        double base = switch (role) {
            case SOLDIER -> 4.0;
            case CASTER -> 3.0;
            case BRUTE -> 7.0;
            case KEEPER -> 6.0;
        };
        return base * (1.0 + 0.25 * (tier - 1));
    }

    private void configure(boolean heal) {
        Kind kind = kind();
        Role role = kind.role;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health(role, tier));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage(role, tier));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role == Role.BRUTE ? 0.22 : kind == Kind.SHADE ? 0.33 : 0.28);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(role == Role.BRUTE ? 0.6 : role == Role.KEEPER ? 0.4 : 0.0);
        getAttribute(Attributes.SCALE).setBaseValue(role == Role.KEEPER ? 1.25 : role == Role.BRUTE ? 1.15 : 1.0);
        if (heal) setHealth(getMaxHealth());
        this.xpReward = role == Role.KEEPER ? 20 : role == Role.BRUTE ? 10 : 6;
        if (attackGoal != null) this.goalSelector.removeGoal(attackGoal);
        attackGoal = role == Role.CASTER ? new RangedAttackGoal(this, 1.0, 45 - 5 * tier, 12.0F) : new MeleeAttackGoal(this, 1.1, true);
        this.goalSelector.addGoal(2, attackGoal);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(heldItem(kind)));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static Item heldItem(Kind kind) {
        return switch (kind) {
            case STORM_HOPLITE -> Items.IRON_SWORD;
            case TIDE_HOPLITE -> Items.TRIDENT;
            case SHADE -> Items.GOLDEN_SWORD;
            case SKY_ARCHER -> Items.BOW;
            case SIREN -> Items.NAUTILUS_SHELL;
            case LAMPAD -> Items.SOUL_TORCH;
            case THUNDER_BRUTE, TARTARUS_BRUTE -> Items.MACE;
            case REEF_BRUTE -> Items.IRON_AXE;
            case KEEPER_SKY, KEEPER_TIDES, KEEPER_SHADES -> Items.BLAZE_ROD;
        };
    }

    public boolean fightsAtRange() {
        return attackGoal instanceof RangedAttackGoal;
    }

    /** The god whose hit power and curse flavour this sentinel's moves: the first god of its tower. */
    private God patron() {
        return Gods.get(kind().line, Line.RANKS);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel level)) return;
        Kind kind = kind();
        float damage = (float) damage(Role.CASTER, tier) + 1.0F;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (kind == Kind.SKY_ARCHER) {
            Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW), getMainHandItem());
            arrow.setBaseDamage(damage / 1.6);
            double dy = target.getY(0.33) - arrow.getY();
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, 8.0F);
            level.addFreshEntity(arrow);
            level.playSound(null, blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, 1.1F);
            return;
        }
        // sirens sing a bolt that slows; lampads throw torchlight that weakens
        RelicProjectile bolt = new RelicProjectile(level, this, new ItemStack(ModContent.orbItem(kind == Kind.SIREN ? Power.FREEZE : Power.FIRE)));
        bolt.setDamage(damage);
        bolt.setCurse(kind == Kind.SIREN ? MobEffects.SLOWNESS : MobEffects.WEAKNESS);
        bolt.shoot(dx, target.getY(0.5) - bolt.getY(), dz, 1.4F, 5.0F);
        level.addFreshEntity(bolt);
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.0F, kind == Kind.SIREN ? 1.6F : 0.8F);
    }

    /** A brute's blow throws its target back. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && kind().role == Role.BRUTE && target instanceof LivingEntity living) {
            Vec3 away = target.position().subtract(position());
            double d = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
            living.setDeltaMovement(away.x / d * 1.1, 0.4, away.z / d * 1.1);
            living.needsSync = true;
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        }
        return hit;
    }

    /** The move a Keeper or a Shade makes now and then, or null for kinds that have none. */
    public Signature move() {
        return switch (kind()) {
            case KEEPER_SKY -> Signature.STORM;
            case KEEPER_TIDES -> Signature.VORTEX;
            case KEEPER_SHADES -> Signature.CURSE;
            case SHADE -> Signature.BLINK;
            default -> null;
        };
    }

    public float moveDamage() {
        return 2.0F + tier;
    }

    /** Makes the sentinel's move at once. @return false if it has none or could not make it. */
    public boolean castMove(ServerLevel level) {
        Signature move = move();
        return move != null && move.perform(level, this, patron(), moveDamage());
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (move() == null) return;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        if (moveCooldown > 0) {
            moveCooldown--;
            if (moveCooldown == WARN_TICKS && kind().role == Role.KEEPER) {
                // a breath of warning before a Keeper's move
                level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2F, 1.2F);
                level.sendParticles(Signature.spark(kind().line), getX(), getY() + 1.2, getZ(), 30, 0.5, 0.8, 0.5, 0.1);
            }
            return;
        }
        double range = kind() == Kind.SHADE ? 14.0 : 10.0;
        // a Shade only steps behind a target that is keeping away from it
        boolean worth = kind() == Kind.SHADE ? distanceToSqr(target) > 25.0 && distanceToSqr(target) < range * range : distanceToSqr(target) < range * range;
        moveCooldown = worth && castMove(level) ? MOVE_EVERY - 20 * tier : 20;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (!killedByPlayer) return;
        // a Keeper always gives up a piece of this tier's forge material; the rest do now and then
        if (kind().role == Role.KEEPER || random.nextFloat() < 0.15F) {
            spawnAtLocation(level, new ItemStack(ModContent.materialItem(kind().line, tier)));
        }
    }

    @Override
    protected Component getTypeName() {
        return kind().displayName();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Kind", kind().ordinal());
        output.putInt("Tier", tier);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_KIND, input.getIntOr("Kind", 0));
        this.tier = Math.max(1, Math.min(3, input.getIntOr("Tier", 1)));
        configure(false);
    }
}
