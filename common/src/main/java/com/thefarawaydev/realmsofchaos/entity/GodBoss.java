package com.thefarawaydev.realmsofchaos.entity;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Ranged;
import com.thefarawaydev.realmsofchaos.item.Tarot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.FloorShifts;
import com.thefarawaydev.realmsofchaos.world.Tower;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.RocAdvancements;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleOptions;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One entity type for all 45 gods: which god it is lives in a synced data field. Health, armour, weapon, boss bar
 * and special ability all derive from the god's line and rank.
 */
public class GodBoss extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> DATA_GOD = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.BOOLEAN);
    private static final String MINION_TAG = "realms_of_chaos_minion";
    private static final int WINDUP_TICKS = 24;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int abilityCooldown = 100;
    private int lonelyTicks;
    private boolean enraged;
    // anti-stuck: where this boss belongs, and how long it has failed to make progress towards its target
    private Vec3 home;
    private Vec3 lastProgressPos = Vec3.ZERO;
    private int stuckSeconds;
    private int shiftStage;
    /** How many moves this god has made: it alternates its signature move with its line's ability. */
    private int casts;
    /** A rematch against a god already beaten: it gives up forge materials, but not its relic a second time. */
    private boolean rematch;
    /** An echo in the hub: it has the god's look and relic but only stands and watches. */
    private boolean statue;
    /** How many players this trial was raised against, and how many times the god had already beaten its summoner. */
    private int challengers = 1, mercy;
    // the attack goal follows the god's weapon: melee for blades and the like, ranged for bows, staffs and relics
    private Goal attackGoal;

    public GodBoss(EntityType<? extends GodBoss> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.attackGoal = new MeleeAttackGoal(this, 1.05, true);
        this.goalSelector.addGoal(2, this.attackGoal);
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GOD, 0);
        builder.define(DATA_CASTING, false);
    }

    /** True while the boss winds up its line ability (the renderer raises its arms). */
    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    /** The point this boss is leashed to: the arena centre in a realm, otherwise where it was summoned. */
    public void setHome(Vec3 home) {
        this.home = home;
    }

    private Vec3 home() {
        if (home == null) home = position();
        Realm realm = Realm.of(level().dimension());
        if (realm != null && realm.line() != null && level() instanceof ServerLevel serverLevel) {
            // in a tower the floor can shift under the boss: home is always the top of the floor's centre
            home = Tower.centre(serverLevel, Tower.floorAt(home.y));
        }
        return home;
    }

    /**
     * Living floors: the terrain shifts into its alternate shape when the god drops below two thirds health and
     * back below one third; on the upper floors it also keeps shifting on a timer.
     */
    public void updateFloorShift(ServerLevel level) {
        Realm realm = Realm.of(level.dimension());
        if (realm == null || realm.line() == null) return;
        int floor = Tower.floorAt(home().y);
        float hp = getHealth() / getMaxHealth();
        int stage = hp < 0.33F ? 2 : hp < 0.66F ? 1 : 0;
        if (stage > shiftStage) {
            shiftStage = stage;
            FloorShifts.set(level, realm.line(), floor, stage == 1);
        }
        if (Tower.tier(floor) >= 3 && tickCount > 0 && tickCount % 500 == 0) {
            FloorShifts.toggle(level, realm.line(), floor);
        }
    }

    private double leashRadius() {
        return Realm.of(level().dimension()) != null ? Tower.FIGHT_RADIUS + 3.0 : 40.0;
    }

    private ParticleOptions lineParticle() {
        return com.thefarawaydev.realmsofchaos.god.Signature.spark(god().line());
    }

    /**
     * Turns this into an echo: no fight in it, no boss bar, nothing to drop. It stands where it is put and turns
     * its head to watch whoever comes by.
     */
    public void setStatue(boolean statue) {
        this.statue = statue;
        if (!statue) return;
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.attackGoal = null;
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 14.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setPermanentlyInvulnerable(true);
        setTarget(null);
        bossEvent.removeAllPlayers();
        bossEvent.setVisible(false);
    }

    public boolean isStatue() {
        return statue;
    }

    @Override
    public boolean isPushable() {
        return !statue && super.isPushable();
    }

    @Override
    public void checkDespawn() {
        if (!statue) super.checkDespawn();
    }

    /** Sets how hard this trial is: scaled up for a group, eased for a player the god keeps beating. */
    public void setTrial(int challengers, int mercy) {
        this.challengers = Math.max(1, Math.min(com.thefarawaydev.realmsofchaos.world.Adapt.MAX_CHALLENGERS, challengers));
        this.mercy = Math.max(0, Math.min(com.thefarawaydev.realmsofchaos.world.Adapt.MAX_MERCY, mercy));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(trialHealth(god()));
        setHealth(getMaxHealth());
    }

    public int challengers() {
        return challengers;
    }

    public int mercy() {
        return mercy;
    }

    private double trialHealth(God god) {
        return god.bossHealth() * com.thefarawaydev.realmsofchaos.world.Adapt.healthScale(challengers) * com.thefarawaydev.realmsofchaos.world.Adapt.mercyScale(mercy);
    }

    /** How much harder this god's moves and shots hit because of the size of the group it faces. */
    public float damageScale() {
        return com.thefarawaydev.realmsofchaos.world.Adapt.damageScale(challengers);
    }

    /** A god that fells a player remembers it: the next trial that player calls is a little gentler. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
        if (!statue && entity instanceof ServerPlayer player) com.thefarawaydev.realmsofchaos.world.Adapt.recordLoss(player.getUUID(), god());
        return super.killedEntity(level, entity, source);
    }

    public void setRematch(boolean rematch) {
        this.rematch = rematch;
    }

    public boolean isRematch() {
        return rematch;
    }

    public boolean isEnraged() {
        return enraged;
    }

    /** Puts the boss back somewhere it can fight from. Called when it is out of bounds, inside a block or stuck. */
    private void warpTo(ServerLevel level, Vec3 to) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        teleportTo(to.x, to.y, to.z);
        level.sendParticles(lineParticle(), to.x, to.y + 1, to.z, 40, 0.5, 1.0, 0.5, 0.1);
        level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
        stuckSeconds = 0;
    }

    /** Once a second: keep the boss inside its arena, out of walls, and moving towards its target. */
    public void keepUnstuck(ServerLevel level) {
        Vec3 h = home();
        double dx = getX() - h.x, dz = getZ() - h.z, r = leashRadius();
        // a tower floor is 20 blocks tall: leaving it vertically means the boss fell or was launched out
        boolean outside = dx * dx + dz * dz > r * r || getY() < h.y - 5 || getY() > h.y + 13;
        if (outside || isInWall()) {
            warpTo(level, h);
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            stuckSeconds = 0;
            return;
        }
        // a god that fights at range is meant to stand off and shoot: it is only stuck if it cannot see or reach
        boolean engaged = fightsAtRange() && distanceToSqr(target) <= 14.0 * 14.0 && hasLineOfSight(target);
        boolean noProgress = !engaged && position().distanceToSqr(lastProgressPos) < 1.0 && distanceToSqr(target) > 36.0;
        lastProgressPos = position();
        stuckSeconds = noProgress ? stuckSeconds + 1 : 0;
        if (stuckSeconds >= 5) {
            // step in next to the target, on the side facing the arena centre, if that spot is inside the leash and free
            Vec3 toHome = h.subtract(target.position());
            Vec3 dir = toHome.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(toHome.x, 0, toHome.z).normalize();
            Vec3 spot = new Vec3(target.getX() + dir.x * 2.5, target.getY(), target.getZ() + dir.z * 2.5);
            double sx = spot.x - h.x, sz = spot.z - h.z;
            boolean inside = sx * sx + sz * sz <= r * r && Math.abs(spot.y - h.y) < 5;
            boolean free = level.noCollision(this, getBoundingBox().move(spot.subtract(position())));
            warpTo(level, inside && free ? spot : h);
        }
    }

    public God god() {
        return Gods.byIndex(this.entityData.get(DATA_GOD));
    }

    public int godIndex() {
        return this.entityData.get(DATA_GOD);
    }

    /** Turns this entity into the given god: stats, weapon, name and boss bar. */
    public void setGod(God god) {
        this.entityData.set(DATA_GOD, god.index());
        configure(god, true);
    }

    /** Gods with a ranged weapon keep their distance and shoot; the rest close in. */
    private void chooseAttack(God god) {
        if (attackGoal != null) this.goalSelector.removeGoal(attackGoal);
        if (god.kind().ranged()) {
            int interval = 50 - (Line.RANKS - god.rank());            // 50 ticks for a starter down to 36 for a final god
            attackGoal = new RangedAttackGoal(this, 1.0, interval, 14.0F);
        } else {
            attackGoal = new MeleeAttackGoal(this, 1.05, true);
        }
        this.goalSelector.addGoal(2, attackGoal);
    }

    public boolean fightsAtRange() {
        return attackGoal instanceof RangedAttackGoal;
    }

    /** Damage of one of this god's ranged shots (gods also have their line ability, so shots hit softer than a player's). */
    public float shotDamage() {
        God god = god();
        return (float) (god.baseDamage() * god.kind().rangedScale() * 0.5) * damageScale();
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (level() instanceof ServerLevel level) {
            Ranged.fire(level, this, god(), getMainHandItem(), shotDamage(), target);
        }
    }

    /** Hades deals from the tarot: a thrown card that wounds and lays a curse. */
    public void throwCard(ServerLevel level, LivingEntity target) {
        Tarot card = Tarot.values()[random.nextInt(Tarot.values().length)];
        RelicProjectile thrown = new RelicProjectile(level, this, new ItemStack(ModContent.tarotItem(card)));
        thrown.setDamage(6.0F);
        thrown.setCurse(switch (random.nextInt(4)) {
            case 0 -> MobEffects.WITHER;
            case 1 -> MobEffects.SLOWNESS;
            case 2 -> MobEffects.BLINDNESS;
            default -> MobEffects.WEAKNESS;
        });
        double dx = target.getX() - getX(), dz = target.getZ() - getZ(), dy = target.getY(0.5) - thrown.getY();
        thrown.shoot(dx, dy, dz, 1.5F, 3.0F);
        level.addFreshEntity(thrown);
        level.playSound(null, blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.5F, 0.7F);
    }

    private void configure(God god, boolean heal) {
        chooseAttack(god);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(trialHealth(god));
        getAttribute(Attributes.ARMOR).setBaseValue(god.bossArmor());
        getAttribute(Attributes.SCALE).setBaseValue(1.0 + (Line.RANKS - god.rank()) * 0.03);
        if (heal) {
            setHealth(getMaxHealth());
        }
        setCustomName(god.bossName());
        bossEvent.setName(god.bossName());
        bossEvent.setColor(god.line().barColor());
        bossEvent.setOverlay(god.isFinalBoss() ? BossEvent.BossBarOverlay.NOTCHED_20 : BossEvent.BossBarOverlay.NOTCHED_10);
        bossEvent.setDarkenScreen(god.rank() <= 3);
        bossEvent.setCreateWorldFog(false);
        var weapon = ModContent.weaponItem(god);
        if (weapon != null) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!statue) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (statue) return;
        bossEvent.setProgress(getHealth() / getMaxHealth());
        God god = god();

        // Stand down if everyone left, so a forgotten boss never blocks the altar.
        boolean anyPlayer = !level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(80), p -> p.isAlive() && !p.isSpectator()).isEmpty();
        lonelyTicks = anyPlayer ? 0 : lonelyTicks + 1;
        if (lonelyTicks > 20 * 45) {
            discardMinions(level);
            discard();
            return;
        }

        if (!enraged && getHealth() < getMaxHealth() * 0.5F) enrage(level, god);

        if (tickCount % 20 == 0) keepUnstuck(level);
        if (tickCount % 10 == 0) updateFloorShift(level);
        if (tickCount % 6 == 0) {
            level.sendParticles(lineParticle(), getX(), getY() + 1.1, getZ(), enraged ? 4 : 2, 0.35, 0.7, 0.35, 0.02);
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            if (isCasting()) this.entityData.set(DATA_CASTING, false);
            return;
        }

        if (god.id().equals("hades") && tickCount % 45 == 0 && distanceToSqr(target) > 9.0) {
            throwCard(level, target);
        }
        if (tickCount % 20 == 0 && !isCasting()) maneuver(level, target);

        if (abilityCooldown > 0) {
            abilityCooldown--;
            if (abilityCooldown == WINDUP_TICKS) {
                // telegraph: arms go up, a ring of particles gathers, and players get a moment to react
                this.entityData.set(DATA_CASTING, true);
                level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6F, 0.8F);
                if (nextIsSignature()) {
                    // name the move on screen so it can be learned and answered
                    for (Player p : nearbyPlayers(level, 40)) {
                        if (p instanceof ServerPlayer sp) {
                            Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".move.windup",
                                    god.displayName(), god.moveName()), god.line().color()));
                        }
                    }
                }
            }
            if (isCasting() && abilityCooldown % 4 == 0) {
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + abilityCooldown * 0.2;
                    level.sendParticles(lineParticle(), getX() + Math.cos(a) * 2.2, getY() + 0.3 + (WINDUP_TICKS - abilityCooldown) * 0.08,
                            getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        double base = 220.0 - (Line.RANKS - god.rank()) * 8.0;
        abilityCooldown = (int) Math.max(70.0, enraged ? base * 0.6 : base);
        if (!nextIsSignature() || !castSignature(level)) castAbility(level, god);
        casts++;
        this.entityData.set(DATA_CASTING, false);
    }

    /** Gods open with their signature move, then alternate it with their line's ability. */
    public boolean nextIsSignature() {
        return casts % 2 == 0;
    }

    /** The god's own move, as hard as its rank allows. @return false if it could not be made right now. */
    public boolean castSignature(ServerLevel level) {
        God god = god();
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS;
        return god.signature().perform(level, this, god, (3.0F + 6.0F * s) * damageScale());
    }

    /** Below half health a god is enraged: it glows, moves faster, casts sooner - and answers at once. */
    private void enrage(ServerLevel level, God god) {
        enraged = true;
        setGlowingTag(true);
        addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
        abilityCooldown = Math.min(abilityCooldown, WINDUP_TICKS + 10);
        level.playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.8F);
        level.sendParticles(lineParticle(), getX(), getY() + 1.2, getZ(), 120, 0.8, 1.2, 0.8, 0.25);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 6, 0.4, 0.2, 0.4, 0.0);
        for (Player p : nearbyPlayers(level, 40)) {
            // the roar shoves everyone back a step: a breath before the second half of the fight
            Vec3 away = p.position().subtract(position());
            double d = Math.sqrt(away.x * away.x + away.z * away.z);
            if (d > 0.01 && d < 7.0) {
                p.setDeltaMovement(p.getDeltaMovement().add(away.x / d * 0.9, 0.35, away.z / d * 0.9));
                p.needsSync = true;
            }
            if (p instanceof ServerPlayer sp) {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".enraged", god.displayName()), 0xFF5555));
            }
        }
    }

    /**
     * Footwork, once a second. A god that fights hand to hand lunges at a target that keeps its distance; one that
     * fights at range hops away when crowded - towards the middle of the floor if its back is to the wall.
     */
    public boolean maneuver(ServerLevel level, LivingEntity target) {
        if (!onGround() || !hasLineOfSight(target)) return false;
        double d2 = distanceToSqr(target);
        Vec3 dir;
        if (!fightsAtRange() && d2 > 36.0 && d2 < 144.0 && random.nextInt(3) == 0) {
            Vec3 to = target.position().subtract(position());
            dir = new Vec3(to.x, 0, to.z).normalize();
        } else if (fightsAtRange() && d2 < 16.0 && random.nextInt(2) == 0) {
            Vec3 away = position().subtract(target.position());
            dir = away.x * away.x + away.z * away.z < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
            Vec3 h = home();
            Vec3 landing = position().add(dir.scale(4.0));
            double lx = landing.x - h.x, lz = landing.z - h.z, r = leashRadius() - 5.0;
            if (lx * lx + lz * lz > r * r) {
                Vec3 inward = h.subtract(position());
                if (inward.x * inward.x + inward.z * inward.z < 0.01) return false;
                dir = new Vec3(inward.x, 0, inward.z).normalize();
            }
        } else {
            return false;
        }
        setDeltaMovement(dir.x * 0.85, 0.42, dir.z * 0.85);
        needsSync = true;
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 8, 0.3, 0.05, 0.3, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.6F);
        return true;
    }

    /** Gods cannot be cheesed with fall damage or pushed around by the arena moats. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    private List<Player> nearbyPlayers(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    /** Test hook: makes the next ability fire after {@code ticks} ticks. */
    public void primeAbility(int ticks) {
        this.abilityCooldown = ticks;
    }

    /** Each god line has its own signature attack, strengthened by the god's rank. */
    public void castAbility(ServerLevel level, God god) {
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS, m = damageScale();
        switch (god.line()) {
            case ZEUS -> {
                for (Player p : nearbyPlayers(level, 28)) {
                    LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setPos(p.getX(), p.getY(), p.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    p.hurtServer(level, damageSources().indirectMagic(this, this), (4.0F + 8.0F * s) * m);
                    god.applyTargetEffect(p);
                }
            }
            case POSEIDON -> {
                level.playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.6F);
                level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, 3.0, 1.0, 3.0, 0.4);
                for (Player p : nearbyPlayers(level, 18)) {
                    Vec3 d = p.position().subtract(position());
                    double dist = d.length();
                    Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
                    // hurt first: a hit knocks its victim back by itself, which would undo the pull
                    p.hurtServer(level, damageSources().mobAttack(this), (3.0F + 6.0F * s) * m);
                    if (dist > 9.0) {
                        // tidal pull: drag distant targets in
                        p.setDeltaMovement(-dir.x * 1.1, 0.25, -dir.z * 1.1);
                    } else {
                        p.setDeltaMovement(dir.x * (1.0 + s), 0.5, dir.z * (1.0 + s));
                    }
                    p.needsSync = true;
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                }
            }
            case HADES -> {
                level.playSound(null, blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 0.5F);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 80, 2.0, 1.0, 2.0, 0.08);
                for (Player p : nearbyPlayers(level, 20)) {
                    p.hurtServer(level, damageSources().indirectMagic(this, this), (3.0F + 5.0F * s) * m);
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                    heal(2.0F + 4.0F * s);
                }
                if (god.rank() <= 8) summonMinions(level, god);
            }
        }
        god.applySelfEffect(this);
    }

    private void summonMinions(ServerLevel level, God god) {
        int existing = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(30), m -> m.entityTags().contains(MINION_TAG)).size();
        if (existing >= 4) return;
        for (int i = 0; i < 2; i++) {
            Mob minion = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
            if (minion == null) continue;
            minion.setPos(getX() + (random.nextDouble() - 0.5) * 4, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 4);
            if (!level.noCollision(minion)) minion.setPos(getX(), getY() + 0.1, getZ());
            minion.addTag(MINION_TAG);
            minion.setTarget(getTarget());
            level.addFreshEntity(minion);
        }
    }

    private void discardMinions(ServerLevel level) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(64), mob -> mob.entityTags().contains(MINION_TAG))) {
            m.discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel) {
            onDefeated(serverLevel, source);
        }
    }

    private void onDefeated(ServerLevel level, DamageSource source) {
        God god = god();
        if (statue) return;
        ProgressData data = ProgressData.get(level.getServer());
        discardMinions(level);
        AABB area = getBoundingBox().inflate(96);
        // everyone who was there shares the victory, and the killer always counts
        Set<ServerPlayer> victors = new LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class, area, Player::isAlive));
        if (source.getEntity() instanceof ServerPlayer killer) victors.add(killer);
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".";
        for (ServerPlayer player : victors) {
            boolean first = data.markDefeated(player.getUUID(), god);
            RocAdvancements.awardGod(player, god);
            if (first) com.thefarawaydev.realmsofchaos.story.Story.onDefeated(player, god);
            com.thefarawaydev.realmsofchaos.world.Adapt.clearLosses(player.getUUID(), god);
            dropFor(level, player, god, first);
            if (god.isFinalBoss()) {
                Hud.title(player, Hud.colored(Component.translatable(hud + "line_complete"), god.line().color()),
                        Hud.colored(Component.translatable(hud + "line_complete.sub", god.line().displayName()), 0xFFFFFF), 10, 100, 30);
            } else {
                Hud.title(player, Hud.colored(Component.translatable(hud + "victory"), 0x55FF55),
                        Hud.colored(Component.translatable(hud + "victory.sub", god.displayName()), 0xFFFFFF));
                Hud.actionBar(player, Hud.colored(Component.translatable(hud + "ascend", Tower.floorOf(god) + 1), 0x55FFFF));
            }
        }
        level.playSound(null, blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 2.0F, 1.0F);
        // the god comes apart in its line's light
        level.sendParticles(lineParticle(), getX(), getY() + 1.2, getZ(), 160, 0.6, 1.0, 0.6, 0.35);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 60, 0.4, 0.9, 0.4, 0.12);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.5, getZ(), 50, 0.5, 0.8, 0.5, 0.4);
        level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.2F, 1.4F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
    }

    /**
     * Everyone who shared a victory gets loot of their own, which only they can pick up: the floor's forge material
     * every time, and - the first time that player beats this god - its relic, its tarot cards and (from Hades) the
     * Deck of Fate.
     */
    private void dropFor(ServerLevel level, ServerPlayer player, God god, boolean first) {
        List<ItemStack> loot = new java.util.ArrayList<>();
        loot.add(new ItemStack(ModContent.materialItem(god.line(), Tower.tier(Tower.floorOf(god))), materialDrop(god)));
        if (first) {
            var weapon = ModContent.weaponItem(god);
            if (weapon != null) loot.add(new ItemStack(weapon));
            for (Tarot card : Tarot.dropsFor(god)) loot.add(new ItemStack(ModContent.tarotItem(card), 3));
            if (god.id().equals("hades")) loot.add(new ItemStack(ModContent.TAROT_DECK.get()));
        }
        for (ItemStack stack : loot) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level, getX(), getY() + 0.5, getZ(), stack);
            drop.setTarget(player.getUUID());
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /** How many forge materials a god drops: two, or three on the upper floors where the later stars are paid for. */
    public static int materialDrop(God god) {
        return Tower.tier(Tower.floorOf(god)) == 3 ? 3 : 2;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // gods shrug off most debuffs but still gain their own buffs
        return effect.getEffect().value().isBeneficial() && super.canBeAffected(effect);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("God", godIndex());
        output.putBoolean("Enraged", enraged);
        output.putInt("ShiftStage", shiftStage);
        output.putInt("Casts", casts);
        output.putBoolean("Rematch", rematch);
        output.putBoolean("Statue", statue);
        output.putInt("Challengers", challengers);
        output.putInt("Mercy", mercy);
        Vec3 h = home();
        output.putDouble("HomeX", h.x);
        output.putDouble("HomeY", h.y);
        output.putDouble("HomeZ", h.z);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_GOD, input.getIntOr("God", 0));
        this.enraged = input.getBooleanOr("Enraged", false);
        this.shiftStage = input.getIntOr("ShiftStage", 0);
        this.casts = input.getIntOr("Casts", 0);
        this.rematch = input.getBooleanOr("Rematch", false);
        this.challengers = Math.max(1, input.getIntOr("Challengers", 1));
        this.mercy = input.getIntOr("Mercy", 0);
        double hy = input.getDoubleOr("HomeY", Double.NaN);
        if (!Double.isNaN(hy)) {
            this.home = new Vec3(input.getDoubleOr("HomeX", getX()), hy, input.getDoubleOr("HomeZ", getZ()));
        }
        configure(god(), false);
        if (input.getBooleanOr("Statue", false)) setStatue(true);
    }
}
