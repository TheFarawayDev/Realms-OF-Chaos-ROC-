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
import com.thefarawaydev.realmsofchaos.ui.HudArt;
import com.thefarawaydev.realmsofchaos.ui.Options;
import com.thefarawaydev.realmsofchaos.world.RocAdvancements;
import com.thefarawaydev.realmsofchaos.world.Adapt;
import com.thefarawaydev.realmsofchaos.world.Insight;
import com.thefarawaydev.realmsofchaos.world.Arena;
import com.thefarawaydev.realmsofchaos.world.Fx;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
 *
 * <p>A trial has three phases. The god fights as itself down to 65% of its health. There it raises its
 * <b>aegis</b>: nothing can hurt it while its shield pylons stand around the floor, and the challengers have to
 * break them. Once the aegis is down the god is enraged, and at 30% comes its <b>wrath</b>: a last stand, the
 * floor lost ring by ring from the wall inward, and strikes from above that are marked a moment before they land.
 * No single blow carries a god through a phase: its health stops at each threshold. Between phases the god looks
 * at how it was fought - from a distance or hand to hand, quickly or slowly - and changes its stance to answer.
 */
public class GodBoss extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> DATA_GOD = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.INT);
    private static final String MINION_TAG = "realms_of_chaos_minion";
    private static final int WINDUP_TICKS = 24;
    /** The share of its health at which a god raises its aegis, and at which its wrath begins. */
    public static final float AEGIS_AT = 0.65F, WRATH_AT = 0.30F;
    /** How many blows a shield pylon takes, and how long an aegis holds if nobody breaks it. */
    public static final int PYLON_HITS = 3, AEGIS_LIMIT = 20 * 75;
    /** How long a pylon has to be left between blows that count, in ticks: it cannot be broken in one flurry. */
    public static final int PYLON_REST = 8;
    /** How long a strike from above is marked on the floor before it lands. */
    public static final int STRIKE_WARNING = 30;

    /** How a god answers the way it has been fought. */
    public enum Stance {
        /** Fought every way at once: it simply presses harder. */
        BALANCED,
        /** Fought from a distance: shots are turned aside and it closes in. */
        DEFLECT,
        /** Fought hand to hand: its guard lashes back and the floor erupts more often. */
        BRISTLE
    }

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
    /** Once enraged, a god makes the floor erupt under its challengers every so often. */
    private int upheavalCooldown = 160;
    /** Below a fifth of its health a god makes one last stand. */
    private boolean lastStand;
    public static final int UPHEAVAL_WARNING = 20;
    // ---- phases
    private boolean aegis;
    private int aegisTicks;
    /** Where this god's shield pylons stand, and how many blows each can still take. */
    private final Map<BlockPos, Integer> pylons = new LinkedHashMap<>();
    private final Map<BlockPos, Long> pylonStruck = new HashMap<>();
    private Stance stance = Stance.BALANCED;
    /** Damage taken this phase by how it was dealt, and how long the phase has lasted. */
    private float meleeTaken, rangedTaken;
    private int phaseTicks;
    /** +1 after a phase that was over quickly, -1 after a long one: it sets pylons and strikes for the next. */
    private int tempo;
    /** How the challengers' gear measured against the floor when the trial was called. */
    private double might = 1.0, guard = 1.0;
    /** True only while a blow is being applied: health then stops at the current phase's threshold. */
    private boolean gating;
    /** Server tick until which nothing can hurt this god (the opening of its last stand). */
    private long guardUntil;
    private long bristleReady, hintReady;
    /** The cast bar: shown, while the god winds up, to those who have asked for it. */
    private final ServerBossEvent castEvent = new ServerBossEvent(
            UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private String castName = "";
    /** What the bar last showed, so its picture is only sent again when it would look different. */
    private int barKey = -1, pylonTotal;
    /** What each challenger did in this trial, for the gods' longer memory; and the answers already given. */
    private final java.util.Map<UUID, Insight.Tally> tallies = new java.util.HashMap<>();
    private final java.util.Map<UUID, Float> lastHealth = new java.util.HashMap<>(), mended = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> farStreak = new java.util.HashMap<>();
    private final java.util.Set<UUID> greeted = new java.util.HashSet<>();
    private long riposteReady, mendReady, haulReady;
    private boolean concluded;
    // ---- wrath
    private double safeRadius = -1.0;
    private int collapseTicks, strikeCooldown = 80;
    /** Paving the wrath has taken, and what it was before. */
    private final Map<BlockPos, BlockState> scorched = new HashMap<>();
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
        builder.define(DATA_PHASE, 1);
    }

    /** 1 = the god as itself, 2 = aegis and rage, 3 = wrath. Synced, so the god's look changes with it. */
    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean aegisUp() {
        return aegis;
    }

    public Stance stance() {
        return stance;
    }

    public int tempo() {
        return tempo;
    }

    public java.util.Set<BlockPos> pylons() {
        return java.util.Collections.unmodifiableSet(pylons.keySet());
    }

    public boolean ownsPylon(BlockPos pos) {
        return pylons.containsKey(pos);
    }

    /** How far from the middle of the floor is still safe in the god's wrath; negative before it. */
    public double safeRadius() {
        return safeRadius;
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
     * Living floors: the terrain shifts into its alternate shape when the god's aegis breaks and back when its
     * wrath begins; on the upper floors it also keeps shifting on a timer.
     */
    public void updateFloorShift(ServerLevel level) {
        Realm realm = Realm.of(level.dimension());
        if (realm == null || realm.line() == null) return;
        int floor = Tower.floorAt(home().y);
        int stage = phase() >= 3 ? 2 : phase() == 2 && !aegis ? 1 : 0;
        if (stage > shiftStage) {
            shiftStage = stage;
            FloorShifts.set(level, realm.line(), floor, stage == 1);
        }
        if (Tower.tier(floor) >= 3 && phase() < 3 && !aegis && tickCount > 0 && tickCount % 500 == 0) {
            FloorShifts.toggle(level, realm.line(), floor);
        }
    }

    /** The tower line whose floor this god is fighting on, or null when it was called somewhere else. */
    private Line towerLine() {
        Realm realm = Realm.of(level().dimension());
        return realm == null ? null : realm.line();
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
        castEvent.removeAllPlayers();
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
        setTrial(challengers, mercy, Adapt.Measure.NONE);
    }

    /** As {@link #setTrial(int, int)}, and risen to match what the challengers carry. */
    public void setTrial(int challengers, int mercy, Adapt.Measure measure) {
        this.challengers = Math.max(1, Math.min(Adapt.MAX_CHALLENGERS, challengers));
        this.mercy = Math.max(0, Math.min(Adapt.MAX_MERCY, mercy));
        this.might = measure.might();
        this.guard = measure.guard();
        // a group that comes with bows is met by a god already braced for them
        if (measure.ranged() && Adapt.gearHealth(might) > 1.0) stance = Stance.DEFLECT;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(trialHealth(god()));
        setHealth(getMaxHealth());
    }

    public double might() {
        return might;
    }

    /** How many times its own health this god has for this trial. */
    public double trialScale() {
        return getMaxHealth() / god().bossHealth();
    }

    public int challengers() {
        return challengers;
    }

    public int mercy() {
        return mercy;
    }

    private double trialHealth(God god) {
        return god.bossHealth() * Adapt.healthScale(challengers) * Adapt.mercyScale(mercy)
                * Adapt.gearHealth(might) * Adapt.difficultyHealth(level().getDifficulty());
    }

    /** How much harder this god's moves and shots hit: for the size of the group, and for armour past what the floor expects. */
    public float damageScale() {
        return Adapt.damageScale(challengers) * Adapt.gearDamage(guard);
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
        barKey = -1;
        refreshBar();
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
        if (!statue) {
            bossEvent.addPlayer(player);
            // a god that has met this challenger's kind before says so, once, a few seconds into the trial
            if (greeted.add(player.getUUID()) && level().getServer() != null) {
                Scheduler.after(level().getServer(), 70, () -> {
                    if (isAlive() && !statue && player.isAlive() && !player.hasDisconnected()) Insight.announce(player, god());
                });
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        castEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (statue) return;
        bossEvent.setProgress(getHealth() / getMaxHealth());
        refreshBar();
        God god = god();

        // Stand down if everyone left, so a forgotten boss never blocks the altar.
        boolean anyPlayer = !level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(80), p -> p.isAlive() && !p.isSpectator()).isEmpty();
        lonelyTicks = anyPlayer ? 0 : lonelyTicks + 1;
        if (lonelyTicks > 20 * 45) {
            concludeInsight(level);
            discardMinions(level);
            discard();
            return;
        }

        updatePhase(level);
        phaseTicks++;
        if (tickCount % WATCH_EVERY == 0) watch(level);
        if (aegis) tendAegis(level);
        if (phase() >= 3) tendWrath(level);
        if (enraged && --upheavalCooldown <= 0) {
            upheavalCooldown = (int) ((lastStand ? 140 : 220) * (stance == Stance.BRISTLE ? 0.7 : 1.0));
            upheaval(level);
        }

        if (tickCount % 20 == 0) keepUnstuck(level);
        if (tickCount % 10 == 0) updateFloorShift(level);
        if (tickCount % 6 == 0) {
            level.sendParticles(lineParticle(), getX(), getY() + 1.1, getZ(), phase() * 2, 0.35, 0.7, 0.35, 0.02);
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            endCast();
            return;
        }

        if (god.id().equals("hades") && tickCount % 45 == 0 && distanceToSqr(target) > 9.0) {
            throwCard(level, target);
        }
        if (tickCount % 20 == 0 && !isCasting()) maneuver(level, target);
        if (stance == Stance.DEFLECT && tickCount % 60 == 0 && !isCasting()) close(level, target);

        if (abilityCooldown > 0) {
            abilityCooldown--;
            if (abilityCooldown == WINDUP_TICKS) {
                // telegraph: arms go up, a ring of particles gathers, and players get a moment to react
                this.entityData.set(DATA_CASTING, true);
                level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6F, 0.8F);
                // the move is named and counted down on a cast bar - for those who have turned it on
                beginCast(level, god);
            }
            if (isCasting()) tendCast();
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
        abilityCooldown = castInterval(base);
        if (!nextIsSignature() || !castSignature(level)) castAbility(level, god);
        casts++;
        endCast();
    }

    // ---- the bars -------------------------------------------------------------------------------------------

    /** Redraws the god's bar when it would look different: another pixel of health gone, a new phase, a shield up or down. */
    public void refreshBar() {
        God god = god();
        float progress = getMaxHealth() <= 0.0F ? 0.0F : getHealth() / getMaxHealth();
        // (the aegis, not the moment of its last stand: that belongs to the wrath, and wears the wrath's frame)
        boolean shield = aegis;
        // what the phase counts: pylons under an aegis, rings of floor in the wrath
        int all = 0, lit = 0;
        if (shield) {
            pylonTotal = Math.max(pylonTotal, pylons.size());
            all = Math.min(HudArt.MAX_WIDGETS, pylonTotal);
            lit = Math.min(all, pylons.size());
        } else {
            pylonTotal = 0;
            if (phase() >= 3) {
                double start = collapseStart(), left = safeRadius < 0 ? start : safeRadius;
                all = 4;
                lit = (int) Math.ceil(4.0 * Math.max(0.0, left - Arena.MIN_SAFE) / Math.max(1.0, start - Arena.MIN_SAFE));
            }
        }
        int key = HudArt.fillWidth(progress) | (phase() << 8) | (shield ? 1 << 12 : 0) | (god.index() << 13) | (lit << 20) | (all << 24);
        if (key == barKey) return;
        barKey = key;
        bossEvent.setName(HudArt.bossBar(god.line(), phase(), shield, progress, god.englishName(), all, lit));
    }

    /** The bar as it is drawn now. */
    public Component barName() {
        return bossEvent.getName();
    }

    public void beginCast(ServerLevel level, God god) {
        castName = com.thefarawaydev.realmsofchaos.ui.Words.of(nextIsSignature() ? god.moveName() : Component.translatable("cast." + RealmsOfChaos.MOD_ID + "." + god.line().id()));
        castEvent.setName(HudArt.castBar(0.0F, castName));
        for (Player p : nearbyPlayers(level, 40)) {
            if (p instanceof ServerPlayer sp && Options.on(sp, Options.Option.CASTS)) castEvent.addPlayer(sp);
        }
    }

    private void tendCast() {
        if (castEvent.getPlayers().isEmpty()) return;
        castEvent.setName(HudArt.castBar(1.0F - abilityCooldown / (float) WINDUP_TICKS, castName));
    }

    public void endCast() {
        if (isCasting()) this.entityData.set(DATA_CASTING, false);
        if (!castEvent.getPlayers().isEmpty()) castEvent.removeAllPlayers();
    }

    /** The move on the cast bar, and who is being shown it. */
    public String castName() {
        return castName;
    }

    public java.util.Collection<ServerPlayer> castWatchers() {
        return castEvent.getPlayers();
    }

    public Component castBarName() {
        return castEvent.getName();
    }

    // ---- what the god sees of its challengers --------------------------------------------------------------

    private static final int WATCH_EVERY = 5;
    /** Further than this from the god is "keeping away". */
    public static final double FAR = 10.0;

    public Insight.Tally tally(Player player) {
        return tallies.computeIfAbsent(player.getUUID(), id -> new Insight.Tally());
    }

    /** A few times a second: who keeps away, who is mending - and, for habits the gods already know, the answer. */
    public void watch(ServerLevel level) {
        long now = level.getServer().getTickCount();
        for (Player p : nearbyPlayers(level, 40)) {
            if (!(p instanceof ServerPlayer sp) || sp.isCreative()) continue;
            UUID id = sp.getUUID();
            Insight.Tally t = tally(sp);
            t.ticks += WATCH_EVERY;
            boolean far = distanceToSqr(sp) > FAR * FAR;
            if (far) t.farTicks += WATCH_EVERY;
            int streak = far ? farStreak.merge(id, WATCH_EVERY, Integer::sum) : 0;
            if (!far) farStreak.remove(id);
            Float before = lastHealth.put(id, sp.getHealth());
            if (before != null && sp.getHealth() > before) {
                t.healed += sp.getHealth() - before;
                mended.merge(id, sp.getHealth() - before, Float::sum);
            }
            if (tickCount % 60 == 0) mended.computeIfPresent(id, (k, v) -> v * 0.5F);
            if (isCasting()) continue;
            int mender = Insight.level(sp, Insight.Habit.MENDER);
            if (mender > 0 && mended.getOrDefault(id, 0.0F) >= 5.0F && now >= mendReady) {
                mendReady = now + 240 - 50L * mender;
                mended.remove(id);
                strikeAt(level, sp.position());
            }
            int runner = Insight.level(sp, Insight.Habit.RUNNER);
            if (runner > 0 && streak >= 60 && now >= haulReady && hasLineOfSight(sp)) {
                haulReady = now + 200 - 40L * runner;
                farStreak.remove(id);
                haul(level, sp, runner);
            }
            int archer = Insight.level(sp, Insight.Habit.ARCHER);
            if (archer > 0 && far && getTarget() == sp && tickCount % (80 - 15 * archer) < WATCH_EVERY) close(level, sp);
        }
    }

    /** One marked strike, where a challenger stood when the god saw them mending. */
    public void strikeAt(ServerLevel level, Vec3 mark) {
        God god = god();
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS;
        float damage = (2.0F + 3.0F * s) * damageScale();
        level.playSound(null, BlockPos.containing(mark), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0F, 1.5F);
        for (int delay = 0; delay < STRIKE_WARNING; delay += 5) {
            final double radius = 2.4 * (1.0 - delay / (double) STRIKE_WARNING * 0.6);
            Scheduler.after(level.getServer(), delay, () -> {
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    level.sendParticles(lineParticle(), mark.x + Math.cos(a) * radius, mark.y + 0.15, mark.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
                }
            });
        }
        Scheduler.after(level.getServer(), STRIKE_WARNING, () -> {
            if (!isAlive() || level() != level) return;
            land(level, god.line(), mark);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(mark.x - 2.4, mark.y - 2.0, mark.z - 2.4, mark.x + 2.4, mark.y + 3.0, mark.z + 2.4),
                    e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
                p.hurtServer(level, damageSources().indirectMagic(this, this), damage);
            }
        });
    }

    /** A runner is hauled back towards the god: harder the better the gods know them. */
    public void haul(ServerLevel level, ServerPlayer runner, int known) {
        Vec3 to = position().subtract(runner.position());
        double d = Math.max(0.01, Math.sqrt(to.x * to.x + to.z * to.z)), pull = 0.9 + 0.3 * known;
        runner.setDeltaMovement(to.x / d * pull, 0.42, to.z / d * pull);
        runner.needsSync = true;
        level.sendParticles(lineParticle(), runner.getX(), runner.getY() + 1.0, runner.getZ(), 24, 0.4, 0.6, 0.4, 0.15);
        level.playSound(null, runner.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        // a blow met at the right moment is turned aside, even a god's
        if (!statue && target instanceof ServerPlayer p && com.thefarawaydev.realmsofchaos.world.Combat.tryParry(level, p, this)) return false;
        ItemStack raised = target instanceof ServerPlayer p && p.isBlocking() ? p.getUseItem().copy() : ItemStack.EMPTY;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer p) com.thefarawaydev.realmsofchaos.world.Combat.struck(p);
        if (!statue && !raised.isEmpty() && target instanceof ServerPlayer p) {
            tally(p).blocks++;
            int known = Insight.level(p, Insight.Habit.GUARD);
            if (known > 0) {
                // the gods know this one hides behind a shield: the blow knocks it aside
                p.getCooldowns().addCooldown(raised, 20 * (1 + known));
                p.stopUsingItem();
                level.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
            }
        }
        return hit;
    }

    /** The trial is over, one way or the other: what it showed goes into the gods' memory, and the challengers are told. */
    public void concludeInsight(ServerLevel level) {
        if (concluded || statue) return;
        concluded = true;
        for (java.util.Map.Entry<UUID, Insight.Tally> e : tallies.entrySet()) {
            List<Insight.Habit> changed = Insight.learn(level.getServer(), e.getKey(), e.getValue());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null && level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer here) player = here;
            if (changed.isEmpty() || player == null || !Options.on(player, Options.Option.INSIGHT)) continue;
            StringBuilder text = new StringBuilder();
            for (Insight.Habit habit : changed) {
                int known = Insight.level(player, habit);
                if (text.length() > 0) text.append(' ');
                text.append(com.thefarawaydev.realmsofchaos.ui.Words.of("insight." + RealmsOfChaos.MOD_ID + (known == 0 ? ".forgotten" : ".learnt"),
                        habit.displayName(), Insight.numeral(known))).append('.');
            }
            com.thefarawaydev.realmsofchaos.ui.Cards.insight(player,
                    com.thefarawaydev.realmsofchaos.ui.Words.of("insight." + RealmsOfChaos.MOD_ID + ".changed"), text.toString());
        }
        tallies.clear();
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

    /** How long this god waits between moves: shorter in each phase, on Hard, and when it has no stance to lean on. */
    public int castInterval(double base) {
        double pace = phase() >= 3 ? 0.5 : enraged ? 0.65 : 1.0;
        if (stance == Stance.BALANCED && phase() > 1) pace *= 0.9;
        return (int) Math.max(60.0, base * pace * Adapt.difficultyPace(level().getDifficulty()));
    }

    // ---- phases ---------------------------------------------------------------------------------------------

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
        God god = god();
        refreshBar();
        bossEvent.setDarkenScreen(god.rank() <= 3 || phase >= 3);
        bossEvent.setCreateWorldFog(phase >= 3);
    }

    /** Moves the trial on when the god's health has reached the next threshold. Safe to call at any time. */
    public void updatePhase(ServerLevel level) {
        if (statue || !isAlive()) return;
        float max = getMaxHealth();
        if (phase() == 1 && getHealth() <= max * AEGIS_AT + 0.001F) beginAegis(level);
        else if (phase() == 2 && !aegis && getHealth() <= max * WRATH_AT + 0.001F) lastStand(level);
    }

    /** The health a blow cannot take this god below: the threshold of the phase it is in. */
    private float healthGate() {
        return switch (phase()) {
            case 1 -> getMaxHealth() * AEGIS_AT;
            case 2 -> getMaxHealth() * WRATH_AT;
            default -> 0.0F;
        };
    }

    @Override
    public void setHealth(float health) {
        if (gating) health = Math.max(health, healthGate());
        super.setHealth(health);
    }

    private boolean shielded() {
        return aegis || (level() instanceof ServerLevel level && level.getServer().getTickCount() < guardUntil);
    }

    @Override
    public boolean isInvulnerable() {
        return super.isInvulnerable() || (!statue && shielded());
    }

    private static boolean fromAfar(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (statue || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, amount);
        if (shielded()) {
            // the aegis rings like a bell and tells whoever struck it what to do instead
            if (aegis && source.getEntity() instanceof ServerPlayer player && level.getServer().getTickCount() >= hintReady) {
                hintReady = level.getServer().getTickCount() + 30;
                level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 14, 0.5, 0.8, 0.5, 0.2);
                Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".aegis.shielded", pylons.size()), 0x55FFFF));
            }
            return false;
        }
        boolean afar = fromAfar(source);
        ServerPlayer striker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (striker != null && !afar) amount = com.thefarawaydev.realmsofchaos.world.Combat.strike(level, striker, this, source, amount);
        // (a god that knows an archer is not waiting to find out again)
        if (afar && striker != null) amount *= 1.0F - 0.12F * Insight.level(striker, Insight.Habit.ARCHER);
        if (afar && stance == Stance.DEFLECT) {
            amount *= 0.4F;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 8, 0.4, 0.7, 0.4, 0.2);
        }
        float before = getHealth();
        boolean hurt;
        gating = true;
        try {
            hurt = super.hurtServer(level, source, amount);
        } finally {
            gating = false;
        }
        if (hurt && isAlive()) {
            float dealt = Math.max(0.0F, before - getHealth());
            if (afar) rangedTaken += dealt;
            else meleeTaken += dealt;
            if (striker != null) {
                Insight.Tally t = tally(striker);
                if (afar) t.ranged += dealt;
                else t.melee += dealt;
                int known = afar || stance == Stance.BRISTLE ? 0 : Insight.level(striker, Insight.Habit.DUELLIST);
                long now = level.getServer().getTickCount();
                if (known > 0 && now >= riposteReady) {
                    riposteReady = now + 130 - 25L * known;
                    bristle(level, striker);
                }
            }
            if (!afar && stance == Stance.BRISTLE && source.getEntity() instanceof LivingEntity attacker) bristle(level, attacker);
            updatePhase(level);
        }
        return hurt;
    }

    /** A braced god's guard lashing back at whoever struck it hand to hand: a shove and a sting, every few seconds. */
    private void bristle(ServerLevel level, LivingEntity attacker) {
        long now = level.getServer().getTickCount();
        if (now < bristleReady || !(attacker instanceof Player player) || player.isCreative()) return;
        bristleReady = now + 50;
        Vec3 away = attacker.position().subtract(position());
        double d = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
        attacker.hurtServer(level, damageSources().thorns(this), 2.0F * damageScale());
        attacker.setDeltaMovement(away.x / d * 1.1, 0.4, away.z / d * 1.1);
        attacker.needsSync = true;
        level.sendParticles(lineParticle(), getX(), getY() + 1.0, getZ(), 40, 0.8, 0.6, 0.8, 0.3);
        level.playSound(null, blockPosition(), SoundEvents.THORNS_HIT, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    /** A god that has learnt to turn shots aside does not wait to be shot: it throws itself at whoever stands off. */
    public boolean close(ServerLevel level, LivingEntity target) {
        if (!onGround() || !hasLineOfSight(target) || distanceToSqr(target) < 49.0) return false;
        Vec3 to = target.position().subtract(position());
        Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
        setDeltaMovement(dir.x * 1.25, 0.45, dir.z * 1.25);
        needsSync = true;
        level.sendParticles(lineParticle(), getX(), getY() + 0.4, getZ(), 16, 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.5F);
        return true;
    }

    /**
     * Between phases a god looks at how the last one went. Mostly shot at: it turns shots aside and closes in.
     * Mostly struck hand to hand: its guard lashes back. A phase that was over quickly makes the next one heavier,
     * a long one lighter.
     */
    private void adapt(ServerLevel level) {
        float total = meleeTaken + rangedTaken;
        Stance next = total < 1.0F ? stance : rangedTaken / total > 0.6F ? Stance.DEFLECT : meleeTaken / total > 0.75F ? Stance.BRISTLE : Stance.BALANCED;
        // (a phase shorter than two seconds says nothing about pace)
        tempo = phaseTicks < 40 ? 0 : phaseTicks < 20 * 25 ? 1 : phaseTicks > 20 * 110 ? -1 : 0;
        boolean changed = next != stance;
        stance = next;
        meleeTaken = rangedTaken = 0.0F;
        phaseTicks = 0;
        if (changed && stance != Stance.BALANCED) {
            Component told = Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".adapt." + stance.name().toLowerCase(java.util.Locale.ROOT),
                    god().displayName()), 0xFFAA00);
            for (Player p : nearbyPlayers(level, 48)) {
                if (p instanceof ServerPlayer sp) sp.sendSystemMessage(told);
            }
        }
    }

    /** Test hook: what the god has taken this phase, as if it had been fought that way. */
    public void recordDamage(float melee, float ranged) {
        this.meleeTaken += melee;
        this.rangedTaken += ranged;
    }

    /**
     * At 65% the god raises its aegis. Shield pylons rise around the floor and nothing can hurt it while one of
     * them stands. It roars, everyone near is shoved back a step, and it goes on fighting from behind its shield.
     */
    public void beginAegis(ServerLevel level) {
        if (phase() != 1 || statue) return;
        God god = god();
        adapt(level);
        enraged = true;
        aegis = true;
        aegisTicks = 0;
        setPhase(2);
        setGlowingTag(true);
        addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
        abilityCooldown = Math.min(abilityCooldown, WINDUP_TICKS + 10);
        level.playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.8F);
        level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(lineParticle(), getX(), getY() + 1.2, getZ(), 120, 0.8, 1.2, 0.8, 0.25);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3, getZ(), 6, 0.4, 0.2, 0.4, 0.0);
        Arena.shockwave(level, position(), lineParticle(), 5);
        BlockState pylon = ModContent.SHIELD_PYLON.get().defaultBlockState();
        for (BlockPos spot : pylonSpots(level, Adapt.pylons(level.getDifficulty(), challengers, tempo))) {
            level.setBlock(spot, pylon, 3);
            pylons.put(spot, PYLON_HITS);
            level.sendParticles(ParticleTypes.END_ROD, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 30, 0.2, 1.2, 0.2, 0.05);
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        setPhase(2);
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".";
        for (Player p : nearbyPlayers(level, 40)) {
            // the roar shoves everyone back a step: a breath before the second part of the fight
            Vec3 away = p.position().subtract(position());
            double d = Math.sqrt(away.x * away.x + away.z * away.z);
            if (d > 0.01 && d < 7.0) {
                p.setDeltaMovement(p.getDeltaMovement().add(away.x / d * 0.9, 0.35, away.z / d * 0.9));
                p.needsSync = true;
            }
            if (p instanceof ServerPlayer sp) {
                // a card of the phase's own, put up at once
                com.thefarawaydev.realmsofchaos.ui.Cards.show(sp, HudArt.Card.AEGIS, com.thefarawaydev.realmsofchaos.ui.Words.of(hud + "aegis", god.displayName()),
                        com.thefarawaydev.realmsofchaos.ui.Words.of(hud + "aegis.sub", pylons.size()), true);
                com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(sp, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.AEGIS);
            }
        }
        if (pylons.isEmpty()) endAegis(level, false);       // nowhere to stand one: no aegis
    }

    /** Where the pylons go: evenly round the god's home, on open ground, clear of the altar and the gates. */
    private List<BlockPos> pylonSpots(ServerLevel level, int count) {
        Vec3 h = home();
        boolean tower = towerLine() != null;
        double radius = tower ? 12.5 : 7.0, start = random.nextDouble() * Math.PI * 2;
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                double a = start + Math.PI * 2 * i / count + attempt * 0.3, r = radius - attempt / 2;
                int x = Mth.floor(h.x + Math.cos(a) * r), z = Mth.floor(h.z + Math.sin(a) * r);
                if (tower && Math.abs(x) <= 4 && Math.abs(z) >= 8) continue;
                BlockPos spot = Arena.standing(level, x, Mth.floor(h.y), z);
                if (spot == null || spots.contains(spot)) continue;
                spots.add(spot);
                break;
            }
        }
        return spots;
    }

    /**
     * One blow against a shield pylon. @return true if this god owned it. The last blow breaks it, and when the
     * last pylon breaks the aegis is gone.
     */
    public boolean strikePylon(ServerLevel level, BlockPos pos, Player by) {
        Integer left = pylons.get(pos);
        if (left == null) return false;
        long now = level.getServer().getTickCount();
        Long last = pylonStruck.get(pos);
        if (last != null && now >= last && now - last < PYLON_REST) return true;       // too soon: this blow glances off
        pylonStruck.put(pos, now);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.3, 0.5, 0.3, 0.3);
        if (left > 1) {
            pylons.put(pos, left - 1);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.6F, 0.6F + 0.25F * (PYLON_HITS - left));
            if (by instanceof ServerPlayer sp) {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".pylon.cracks", left - 1), 0x55FFFF));
            }
            return true;
        }
        pylons.remove(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (by instanceof ServerPlayer breaker) {
            com.thefarawaydev.realmsofchaos.world.Bounties.count(breaker, com.thefarawaydev.realmsofchaos.world.Bounties.Kind.PYLONS, 1);
        }
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 1.2F);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 2, 0.2, 0.3, 0.2, 0.0);
        level.sendParticles(lineParticle(), x, y, z, 50, 0.4, 0.8, 0.4, 0.25);
        Fx.impact(level, new Vec3(x, pos.getY(), z), 5);
        // the god feels it: it reels, and its next move comes later
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1.4, getZ(), 10, 0.4, 0.5, 0.4, 0.1);
        abilityCooldown += 30;
        for (Player p : nearbyPlayers(level, 48)) {
            if (p instanceof ServerPlayer sp && !pylons.isEmpty()) {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".aegis.left", pylons.size(), god().displayName()), 0x55FFFF));
            }
        }
        if (pylons.isEmpty()) endAegis(level, true);
        return true;
    }

    /** Lowers the aegis: because its pylons were broken, or because it could not be held any longer. */
    public void endAegis(ServerLevel level, boolean broken) {
        if (!aegis) return;
        aegis = false;
        clearPylons(level);
        setPhase(2);
        abilityCooldown = Math.max(abilityCooldown, broken ? 90 : 50);
        level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.4F);
        level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 80, 0.8, 1.0, 0.8, 0.5);
        Arena.shockwave(level, position(), lineParticle(), 3);
        for (Player p : nearbyPlayers(level, 48)) {
            if (p instanceof ServerPlayer sp) {
                Hud.title(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + (broken ? ".aegis.broken" : ".aegis.faded")), 0x55FF55),
                        Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".aegis.broken.sub", god().displayName()), 0xCCCCCC), 5, 40, 10);
            }
        }
        updateFloorShift(level);
    }

    private void clearPylons(ServerLevel level) {
        for (BlockPos pos : pylons.keySet()) {
            if (level.getBlockState(pos).is(ModContent.SHIELD_PYLON.get())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        pylons.clear();
        pylonStruck.clear();
    }

    /** Every tick of an aegis: light runs from each pylon to the god, pylons the floor moved are stood up again. */
    private void tendAegis(ServerLevel level) {
        aegisTicks++;
        if (aegisTicks % 10 == 0) {
            for (BlockPos pos : pylons.keySet()) {
                Vec3 from = new Vec3(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5), to = position().add(0, 1.2, 0);
                for (int i = 0; i <= 10; i++) {
                    Vec3 at = from.lerp(to, i / 10.0);
                    level.sendParticles(lineParticle(), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }
        if (aegisTicks % 20 == 0) reseatPylons(level);
        if (aegisTicks % 100 == 0) {
            for (Player p : nearbyPlayers(level, 48)) {
                if (p instanceof ServerPlayer sp) {
                    Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".aegis.left", pylons.size(), god().displayName()), 0x55FFFF));
                }
            }
        }
        if (pylons.isEmpty() || aegisTicks >= AEGIS_LIMIT) endAegis(level, pylons.isEmpty());
    }

    /** A pylon the shifting floor swallowed or left hanging is stood on the new ground, still as cracked as it was. */
    public void reseatPylons(ServerLevel level) {
        Map<BlockPos, Integer> moved = new LinkedHashMap<>();
        BlockState pylon = ModContent.SHIELD_PYLON.get().defaultBlockState();
        for (Map.Entry<BlockPos, Integer> entry : pylons.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState below = level.getBlockState(pos.below());
            boolean stands = level.getBlockState(pos).is(pylon.getBlock()) && below.isCollisionShapeFullBlock(level, pos.below());
            if (stands) {
                moved.put(pos, entry.getValue());
                continue;
            }
            if (level.getBlockState(pos).is(pylon.getBlock())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            BlockPos spot = Arena.standing(level, pos.getX(), pos.getY(), pos.getZ());
            if (spot == null) continue;                    // nowhere left for it: one pylon fewer
            level.setBlock(spot, pylon, 3);
            moved.put(spot, entry.getValue());
        }
        pylons.clear();
        pylons.putAll(moved);
    }

    // ---- wrath ----------------------------------------------------------------------------------------------

    /** Every tick of a god's wrath: the floor is lost from the wall inward, and strikes fall from above. */
    private void tendWrath(ServerLevel level) {
        collapseTicks++;
        if (collapseTicks % collapseInterval() == 0) collapse(level);
        Vec3 h = home();
        if (collapseTicks % 10 == 0 && safeRadius > 0) {
            // the edge of what is left, drawn in the line's own hazard
            int points = (int) (safeRadius * 4);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points + collapseTicks * 0.01;
                level.sendParticles(Arena.hazardParticle(god().line()), h.x + Math.cos(a) * (safeRadius + 0.5), h.y + 0.3,
                        h.z + Math.sin(a) * (safeRadius + 0.5), 1, 0.05, 0.1, 0.05, 0.0);
            }
        }
        if (collapseTicks % 20 == 0) burnOutside(level);
        if (--strikeCooldown <= 0) {
            strikeCooldown = strikeInterval();
            strikes(level);
        }
    }

    private int collapseInterval() {
        return Math.max(30, (int) ((60 - tempo * 15) * Adapt.difficultyPace(level().getDifficulty())));
    }

    private int strikeInterval() {
        return Math.max(50, (int) ((110 - tempo * 20) * Adapt.difficultyPace(level().getDifficulty())));
    }

    /** The radius the floor starts being lost from: the tower wall, or a wide ring round a god called elsewhere. */
    private double collapseStart() {
        return towerLine() != null ? Tower.WALL_INNER : 16.0;
    }

    /**
     * Takes one more ring of the floor. In a tower the paving of that ring turns to the line's hazard; anywhere
     * the ground outside the ring simply hurts to stand on. The middle of the floor is never lost.
     * @return the radius that is still safe
     */
    public double collapse(ServerLevel level) {
        if (safeRadius < 0) safeRadius = collapseStart();
        if (safeRadius <= Arena.MIN_SAFE) return safeRadius;
        safeRadius -= 1.0;
        Line line = towerLine();
        Vec3 h = home();
        if (line != null) {
            Arena.scorch(level, line, Tower.floorAt(h.y), safeRadius + 0.5, collapseStart() + 1.0, scorched);
        }
        level.playSound(null, BlockPos.containing(h), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, BlockPos.containing(h), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.6F);
        return safeRadius;
    }

    /** Anyone standing on the lost part of the floor is hurt, once a second, and told to move in. */
    public int burnOutside(ServerLevel level) {
        if (safeRadius < 0) return 0;
        Vec3 h = home();
        int hurt = 0;
        float s = (Line.RANKS + 1 - god().rank()) / (float) Line.RANKS;
        for (Player p : nearbyPlayers(level, 40)) {
            double dx = p.getX() - h.x, dz = p.getZ() - h.z;
            if (dx * dx + dz * dz <= (safeRadius + 0.5) * (safeRadius + 0.5) || Math.abs(p.getY() - h.y) > 8) continue;
            p.hurtServer(level, damageSources().indirectMagic(this, this), (1.5F + 2.5F * s) * damageScale());
            switch (god().line()) {
                case ZEUS -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
                case POSEIDON -> p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                case HADES -> p.setRemainingFireTicks(60);
            }
            if (p instanceof ServerPlayer sp) {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".collapse"), 0xFF5555));
            }
            hurt++;
        }
        return hurt;
    }

    /**
     * Strikes from above: lightning under Zeus, the sea bursting up under Poseidon, burning stone under Hades. One
     * is marked beside every challenger and a few more are scattered over what is left of the floor. The marks
     * show for a second and a half, then every one of them lands. @return how many were marked
     */
    public int strikes(ServerLevel level) {
        God god = god();
        Vec3 h = home();
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS;
        float damage = (3.0F + 6.0F * s) * damageScale();
        List<Vec3> marks = new ArrayList<>();
        String key = "hud." + RealmsOfChaos.MOD_ID + ".strike." + god.line().id();
        for (Player p : nearbyPlayers(level, 30)) {
            marks.add(new Vec3(p.getX() + (random.nextDouble() - 0.5) * 2.0, p.getY(), p.getZ() + (random.nextDouble() - 0.5) * 2.0));
            if (p instanceof ServerPlayer sp) {
                Hud.actionBar(sp, Hud.colored(Component.translatable(key), 0xFFAA00));
                com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(sp, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.WRATH);
            }
        }
        if (marks.isEmpty()) return 0;
        int extra = Math.max(1, 2 + tempo + (level.getDifficulty() == net.minecraft.world.Difficulty.HARD ? 1 : 0));
        double reach = safeRadius > 0 ? safeRadius : 12.0;
        for (int i = 0; i < extra; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * reach;
            BlockPos spot = Arena.standing(level, Mth.floor(h.x + Math.cos(a) * r), Mth.floor(h.y), Mth.floor(h.z + Math.sin(a) * r));
            if (spot != null) marks.add(new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
        }
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5F, 1.2F);
        for (int delay = 0; delay < STRIKE_WARNING; delay += 5) {
            final double shrink = 1.0 - delay / (double) STRIKE_WARNING * 0.6;
            Runnable warn = () -> {
                for (Vec3 m : marks) {
                    for (int i = 0; i < 16; i++) {
                        double a = Math.PI * 2 * i / 16;
                        level.sendParticles(Arena.hazardParticle(god.line()), m.x + Math.cos(a) * 2.5 * shrink, m.y + 0.15,
                                m.z + Math.sin(a) * 2.5 * shrink, 1, 0, 0, 0, 0);
                    }
                    level.sendParticles(lineParticle(), m.x, m.y + 6.0 * shrink, m.z, 3, 0.1, 0.1, 0.1, 0.0);
                }
            };
            if (delay == 0) warn.run();
            else Scheduler.after(level.getServer(), delay, warn);
        }
        if (god.line() == Line.HADES) {
            // the stone itself is seen coming
            Scheduler.after(level.getServer(), STRIKE_WARNING - 12, () -> {
                for (Vec3 m : marks) {
                    BlockPos from = BlockPos.containing(m.x, m.y + 9.0, m.z);
                    if (!level.getBlockState(from).isAir()) continue;
                    var stone = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, from, Blocks.MAGMA_BLOCK.defaultBlockState());
                    stone.dropItem = false;
                    stone.disableDrop();
                    stone.setDeltaMovement(0, -0.9, 0);
                    stone.needsSync = true;
                }
            });
        }
        Scheduler.after(level.getServer(), STRIKE_WARNING, () -> {
            if (!isAlive() || level() != level) return;
            java.util.Set<Player> struck = new java.util.HashSet<>();
            for (Vec3 m : marks) {
                land(level, god.line(), m);
                for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m.x - 2.6, m.y - 2.0, m.z - 2.6, m.x + 2.6, m.y + 3.0, m.z + 2.6),
                        e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
                    if (!struck.add(p)) continue;
                    p.hurtServer(level, damageSources().indirectMagic(this, this), damage);
                    if (god.line() == Line.POSEIDON) {
                        p.setDeltaMovement(p.getDeltaMovement().x * 0.5, 0.9, p.getDeltaMovement().z * 0.5);
                        p.needsSync = true;
                    } else if (god.line() == Line.HADES) {
                        p.setRemainingFireTicks(80);
                    }
                }
            }
        });
        return marks.size();
    }

    private void land(ServerLevel level, Line line, Vec3 m) {
        Fx.impact(level, m, 6);
        switch (line) {
            case ZEUS -> {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setPos(m.x, m.y, m.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            case POSEIDON -> {
                level.sendParticles(ParticleTypes.SPLASH, m.x, m.y + 0.5, m.z, 120, 1.0, 1.5, 1.0, 0.5);
                level.sendParticles(ParticleTypes.BUBBLE_POP, m.x, m.y + 1.5, m.z, 60, 0.8, 1.5, 0.8, 0.1);
                level.playSound(null, BlockPos.containing(m), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
            }
            case HADES -> {
                level.sendParticles(ParticleTypes.LAVA, m.x, m.y + 0.3, m.z, 30, 1.0, 0.2, 1.0, 0.0);
                level.sendParticles(ParticleTypes.FLAME, m.x, m.y + 0.3, m.z, 60, 1.0, 0.3, 1.0, 0.08);
                level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 2, 0.5, 0.2, 0.5, 0.0);
                level.playSound(null, BlockPos.containing(m), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
            }
        }
    }

    /** Takes down everything a fight put on the floor: pylons, and the paving the wrath took. */
    private void clearArena(ServerLevel level) {
        clearPylons(level);
        Line line = towerLine();
        if (line != null && !scorched.isEmpty()) Arena.restore(level, line, Tower.floorAt(home().y), scorched);
        scorched.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        // a god that is gone for good leaves the floor as it found it (one that is only unloaded keeps its fight)
        if (reason.shouldDestroy() && !statue && level() instanceof ServerLevel level) clearArena(level);
        super.remove(reason);
    }

    /**
     * The floor throws itself up. Wherever a challenger is standing the ground is marked - dust and a rumble, for a
     * second - and then erupts: chunks of the floor fly, and anyone still on the spot is thrown into the air.
     * @return how many places were marked
     */
    public int upheaval(ServerLevel level) {
        God god = god();
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS;
        float damage = (2.0F + 4.0F * s) * damageScale();
        List<Vec3> marks = new java.util.ArrayList<>();
        for (Player p : nearbyPlayers(level, 26)) {
            marks.add(p.position());
            if (p instanceof ServerPlayer sp) {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".upheaval"), 0xFFAA00));
                com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(sp, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.UPHEAVAL);
            }
        }
        if (marks.isEmpty()) return 0;
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5F, 0.6F);
        for (int delay = 0; delay < UPHEAVAL_WARNING; delay += 6) {
            Runnable warn = () -> {
                for (Vec3 m : marks) {
                    BlockPos under = BlockPos.containing(m.x, m.y + 0.2, m.z);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, com.thefarawaydev.realmsofchaos.world.Fx.ground(level, under)),
                            m.x, m.y + 0.1, m.z, 14, 0.9, 0.05, 0.9, 0.05);
                    level.sendParticles(lineParticle(), m.x, m.y + 0.15, m.z, 6, 0.9, 0.05, 0.9, 0.0);
                }
            };
            if (delay == 0) warn.run();
            else com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), delay, warn);
        }
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), UPHEAVAL_WARNING, () -> {
            if (!isAlive() || level() != level) return;
            java.util.Set<Player> thrown = new java.util.HashSet<>();
            for (Vec3 m : marks) {
                com.thefarawaydev.realmsofchaos.world.Fx.impact(level, m, 8);
                level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 2, 0.5, 0.2, 0.5, 0.0);
                for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m.x - 2.0, m.y - 1.5, m.z - 2.0, m.x + 2.0, m.y + 3.0, m.z + 2.0),
                        e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
                    if (!thrown.add(p)) continue;
                    p.hurtServer(level, damageSources().mobAttack(this), damage);
                    p.setDeltaMovement(p.getDeltaMovement().x * 0.5, 0.95, p.getDeltaMovement().z * 0.5);
                    p.needsSync = true;
                }
            }
        });
        return marks.size();
    }

    public boolean madeLastStand() {
        return lastStand;
    }

    /**
     * A god's wrath opens with its last stand, once, at 30% of its health. For two seconds nothing can touch it.
     * It calls two sentinels of its tower to its side, the ground breaks round it, and then its signature move and
     * its line's ability follow one after the other. From here on the floor erupts more often, the floor is lost
     * ring by ring from the wall inward, and strikes fall from above.
     */
    public void lastStand(ServerLevel level) {
        if (lastStand || statue) return;
        lastStand = true;
        God god = god();
        if (aegis) endAegis(level, false);
        adapt(level);
        enraged = true;
        setGlowingTag(true);
        setPhase(3);
        addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1));
        safeRadius = collapseStart();
        collapseTicks = 0;
        strikeCooldown = 100;
        guardUntil = level.getServer().getTickCount() + 40;
        Arena.shockwave(level, position(), lineParticle(), 6);
        level.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 0.7F);
        level.sendParticles(lineParticle(), getX(), getY() + 1.2, getZ(), 200, 1.0, 1.4, 1.0, 0.4);
        com.thefarawaydev.realmsofchaos.world.Fx.impact(level, position(), 8);
        for (Player p : nearbyPlayers(level, 40)) {
            if (p instanceof ServerPlayer sp) {
                com.thefarawaydev.realmsofchaos.ui.Cards.show(sp, HudArt.Card.WRATH, com.thefarawaydev.realmsofchaos.ui.Words.of("hud." + RealmsOfChaos.MOD_ID + ".laststand", god.displayName()),
                        com.thefarawaydev.realmsofchaos.ui.Words.of("hud." + RealmsOfChaos.MOD_ID + ".laststand.sub"), true);
            }
        }
        // two of its tower's sentinels answer the call, as strong as the floor asks
        int floor = Tower.floorOf(god);
        com.thefarawaydev.realmsofchaos.entity.Sentinel.Role[] called = {com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.SOLDIER, com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.CASTER};
        for (int i = 0; i < called.length; i++) {
            Sentinel guard = ModContent.SENTINEL.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (guard == null) continue;
            guard.setUp(Sentinel.Kind.of(god.line(), called[i]), Math.min(3, Tower.tier(floor)), Sentinel.threat(floor, 0), false);
            double a = Math.PI * i + random.nextDouble();
            guard.snapTo(getX() + Math.cos(a) * 2.0, getY() + 0.1, getZ() + Math.sin(a) * 2.0, getYRot(), 0.0F);
            if (!level.noCollision(guard)) guard.snapTo(getX(), getY() + 0.1, getZ(), getYRot(), 0.0F);
            guard.addTag(MINION_TAG);
            guard.setPersistenceRequired();
            guard.setTarget(getTarget());
            level.addFreshEntity(guard);
        }
        // and then everything it has, one after the other
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), 45, () -> {
            if (isAlive() && level() == level) castSignature(level);
        });
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), 80, () -> {
            if (isAlive() && level() == level) castAbility(level, god());
        });
        abilityCooldown = 120;
        upheavalCooldown = 100;
        updateFloorShift(level);
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
            concludeInsight(serverLevel);
            onDefeated(serverLevel, source);
            // the floor is given back the moment the god falls, not when its body has faded
            if (!statue) clearArena(serverLevel);
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
            com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.VICTORY);
            com.thefarawaydev.realmsofchaos.world.Adapt.clearLosses(player.getUUID(), god);
            com.thefarawaydev.realmsofchaos.world.Bounties.count(player, com.thefarawaydev.realmsofchaos.world.Bounties.Kind.GODS, 1);
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
        output.putBoolean("LastStand", lastStand);
        output.putInt("Phase", phase());
        output.putBoolean("Aegis", aegis);
        output.putInt("AegisTicks", aegisTicks);
        StringBuilder stood = new StringBuilder();
        for (Map.Entry<BlockPos, Integer> e : pylons.entrySet()) {
            stood.append(e.getKey().getX()).append(',').append(e.getKey().getY()).append(',').append(e.getKey().getZ()).append(',').append(e.getValue()).append(';');
        }
        output.putString("Pylons", stood.toString());
        output.putString("Stance", stance.name());
        output.putInt("Tempo", tempo);
        output.putDouble("Might", might);
        output.putDouble("Guard", guard);
        output.putDouble("SafeRadius", safeRadius);
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
        this.lastStand = input.getBooleanOr("LastStand", false);
        this.aegis = input.getBooleanOr("Aegis", false);
        this.aegisTicks = input.getIntOr("AegisTicks", 0);
        this.pylons.clear();
        for (String part : input.getStringOr("Pylons", "").split(";")) {
            String[] f = part.split(",");
            if (f.length != 4) continue;
            try {
                this.pylons.put(new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])), Integer.parseInt(f[3]));
            } catch (NumberFormatException ignored) {
                // a pylon that cannot be read is simply not this god's any more
            }
        }
        try {
            this.stance = Stance.valueOf(input.getStringOr("Stance", "BALANCED"));
        } catch (IllegalArgumentException e) {
            this.stance = Stance.BALANCED;
        }
        this.tempo = input.getIntOr("Tempo", 0);
        this.might = input.getDoubleOr("Might", 1.0);
        this.guard = input.getDoubleOr("Guard", 1.0);
        this.safeRadius = input.getDoubleOr("SafeRadius", -1.0);
        int savedPhase = Math.max(1, Math.min(3, input.getIntOr("Phase", lastStand ? 3 : enraged ? 2 : 1)));
        this.challengers = Math.max(1, input.getIntOr("Challengers", 1));
        this.mercy = input.getIntOr("Mercy", 0);
        double hy = input.getDoubleOr("HomeY", Double.NaN);
        if (!Double.isNaN(hy)) {
            this.home = new Vec3(input.getDoubleOr("HomeX", getX()), hy, input.getDoubleOr("HomeZ", getZ()));
        }
        configure(god(), false);
        setPhase(savedPhase);
        if (input.getBooleanOr("Statue", false)) setStatue(true);
    }
}
