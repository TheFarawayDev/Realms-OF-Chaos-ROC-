package com.thefarawaydev.realmsofchaos.entity;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
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
public class GodBoss extends Monster {
    private static final EntityDataAccessor<Integer> DATA_GOD = SynchedEntityData.defineId(GodBoss.class, EntityDataSerializers.INT);
    private static final String MINION_TAG = "realms_of_chaos_minion";

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int abilityCooldown = 100;
    private int lonelyTicks;
    private boolean enraged;

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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, true));
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

    private void configure(God god, boolean heal) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(god.bossHealth());
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
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
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

        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enraged = true;
            addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
            level.playSound(null, blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (abilityCooldown > 0) {
            abilityCooldown--;
            return;
        }
        double base = 220.0 - (Line.RANKS - god.rank()) * 8.0;
        abilityCooldown = (int) Math.max(70.0, enraged ? base * 0.6 : base);
        castAbility(level, god);
    }

    private List<Player> nearbyPlayers(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    /** Each god line has its own signature attack, strengthened by the god's rank. */
    private void castAbility(ServerLevel level, God god) {
        float s = (Line.RANKS + 1 - god.rank()) / (float) Line.RANKS;
        switch (god.line()) {
            case ZEUS -> {
                for (Player p : nearbyPlayers(level, 28)) {
                    LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setPos(p.getX(), p.getY(), p.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    p.hurtServer(level, damageSources().indirectMagic(this, this), 4.0F + 8.0F * s);
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
                    if (dist > 9.0) {
                        // tidal pull: drag distant targets in
                        p.setDeltaMovement(p.getDeltaMovement().add(-dir.x * 1.1, 0.25, -dir.z * 1.1));
                    } else {
                        p.setDeltaMovement(p.getDeltaMovement().add(dir.x * (1.0 + s), 0.5, dir.z * (1.0 + s)));
                    }
                    p.needsSync = true;
                    p.hurtServer(level, damageSources().mobAttack(this), 3.0F + 6.0F * s);
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                }
            }
            case HADES -> {
                level.playSound(null, blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5F, 0.5F);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 80, 2.0, 1.0, 2.0, 0.08);
                for (Player p : nearbyPlayers(level, 20)) {
                    p.hurtServer(level, damageSources().indirectMagic(this, this), 3.0F + 5.0F * s);
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
            minion.setPos(getX() + (random.nextDouble() - 0.5) * 6, getY(), getZ() + (random.nextDouble() - 0.5) * 6);
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
            onDefeated(serverLevel);
        }
    }

    private void onDefeated(ServerLevel level) {
        God god = god();
        ProgressData data = ProgressData.get(level.getServer());
        discardMinions(level);
        AABB area = getBoundingBox().inflate(96);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, Player::isAlive)) {
            boolean first = data.markDefeated(player.getUUID(), god);
            player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".victory", god.displayName()));
            if (!first) continue;
            if (god.isFinalBoss()) {
                player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".line_complete", god.line().displayName()));
            } else {
                God next = Gods.get(god.line(), god.rank() - 1);
                player.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".unlocked", next.displayName()));
            }
        }
        level.playSound(null, blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        var weapon = ModContent.weaponItem(god());
        if (weapon != null) {
            spawnAtLocation(level, new ItemStack(weapon));
        }
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_GOD, input.getIntOr("God", 0));
        this.enraged = input.getBooleanOr("Enraged", false);
        configure(god(), false);
    }
}
