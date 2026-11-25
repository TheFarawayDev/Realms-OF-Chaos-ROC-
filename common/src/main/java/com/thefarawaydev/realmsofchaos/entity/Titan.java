package com.thefarawaydev.realmsofchaos.entity;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.Onslaughts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A fallen Titan: the god who holds a citadel, on the roof of its keep beside the keystone.
 *
 * <p>A Titan fights in three phases, each with a skin of its own - itself, then lit with its power at three-fifths of
 * its health, then cracked open in its fury at three-tenths - and has four moves of its own, each wound up for a
 * moment with its arms raised and its name on its bar, and each drawn on the ground or in the air before it lands.
 * Two moves it has from the start, a third from its second phase, and its greatest only at the last.
 *
 * <ul>
 *     <li><b>Hyperion</b>, the Fallen Light - a <i>Solar Lance</i> along a line; <i>Blinding Dawn</i>, which only
 *     blinds those who are looking at him; <i>Falling Stars</i> on marked spots; and the <i>Corona</i>, rings of fire
 *     running out across the roof.</li>
 *     <li><b>Oceanus</b>, the Fallen Sea - an <i>Undertow</i> that drags everyone to him for a slam;
 *     <i>Waterspouts</i> under whoever stands still; a <i>Tidal Wall</i> with one gap in it; and the <i>Call of the
 *     Deep</i>, which brings the sea's half-kin up the walls.</li>
 *     <li><b>Cronus</b>, the Fallen King - a <i>Scythe Sweep</i> in front of him; <i>Devour Time</i>, which slows
 *     everyone and quickens him; <i>Rewind</i>, which throws his target back to where they stood three seconds ago;
 *     and the <i>Age of Iron</i>, when the roof splits in a star and he fights the faster for it.</li>
 * </ul>
 */
public class Titan extends Monster {
    public enum Kind {
        HYPERION(Line.ZEUS), OCEANUS(Line.POSEIDON), CRONUS(Line.HADES);

        public final Line line;

        Kind(Line line) {
            this.line = line;
        }

        public static Kind of(Line line) {
            for (Kind k : values()) if (k.line == line) return k;
            return HYPERION;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("titan." + RealmsOfChaos.MOD_ID + "." + id());
        }
    }

    /** A Titan's moves. {@code phase}: the first phase it is used in; {@code windup}: ticks from its name to its blow. */
    public enum Move {
        SOLAR_LANCE(Kind.HYPERION, 1, 26), BLINDING_DAWN(Kind.HYPERION, 1, 34), FALLING_STARS(Kind.HYPERION, 2, 18), CORONA(Kind.HYPERION, 3, 20),
        UNDERTOW(Kind.OCEANUS, 1, 24), WATERSPOUTS(Kind.OCEANUS, 1, 16), TIDAL_WALL(Kind.OCEANUS, 2, 16), CALL_OF_THE_DEEP(Kind.OCEANUS, 3, 24),
        SCYTHE_SWEEP(Kind.CRONUS, 1, 20), DEVOUR_TIME(Kind.CRONUS, 1, 32), REWIND(Kind.CRONUS, 2, 26), AGE_OF_IRON(Kind.CRONUS, 3, 22);

        public final Kind titan;
        public final int phase, windup;

        Move(Kind titan, int phase, int windup) {
            this.titan = titan;
            this.phase = phase;
            this.windup = windup;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("titan." + RealmsOfChaos.MOD_ID + ".move." + id());
        }

        public static List<Move> of(Kind titan) {
            List<Move> out = new ArrayList<>();
            for (Move m : values()) if (m.titan == titan) out.add(m);
            return out;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(Titan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Titan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(Titan.class, EntityDataSerializers.BOOLEAN);
    public static final String ADD_TAG = "roc_titan_add";
    /** How long a phase change keeps the Titan untouchable, while the roof is thrown off it. */
    public static final int SHIELD_TICKS = 40;
    /** How far a Titan may wander from its post before it is set back on it. */
    public static final int LEASH = 9;

    private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private BlockPos post;
    private Move casting, last;
    private int windup, cooldown = 60, shieldTicks;
    /** Where the move being wound up is aimed: a direction for the lance and the scythe, spots for the stars and spouts. */
    private Vec3 aim = Vec3.ZERO;
    private final List<Vec3> marks = new ArrayList<>();
    private UUID marked;
    /** Things to happen a few ticks from now, if the Titan is still standing. */
    private final List<Object[]> later = new ArrayList<>();
    /** Where the players near Cronus stood, every ten ticks: what Rewind throws them back to. */
    private final Map<UUID, ArrayDeque<Vec3>> trail = new HashMap<>();

    public Titan(EntityType<? extends Titan> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        bar.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.SCALE, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, 0);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_CASTING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, true));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ---- what it is ------------------------------------------------------------------------------------------

    public Kind kind() {
        int k = entityData.get(DATA_KIND);
        return Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, k))];
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    public Move casting() {
        return casting;
    }

    public BlockPos post() {
        return post;
    }

    public ServerBossEvent bar() {
        return bar;
    }

    public boolean shielded() {
        return shieldTicks > 0;
    }

    private float scale() {
        Difficulty d = level().getDifficulty();
        return d == Difficulty.EASY ? 0.7F : d == Difficulty.HARD ? 1.3F : 1.0F;
    }

    /** Makes this the Titan of one citadel, standing at its post. */
    public void setUp(Kind kind, BlockPos post) {
        entityData.set(DATA_KIND, kind.ordinal());
        this.post = post;
        setHomeTo(post, LEASH);
        setPersistenceRequired();
        // (a few more for a world that is harder, a few less for an easier)
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(300.0 * scale());
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(switch (kind) {
            case HYPERION -> Items.GOLDEN_SWORD;
            case OCEANUS -> Items.TRIDENT;
            case CRONUS -> Items.NETHERITE_HOE;
        }));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        // (half as big again as a man: the scale is set on its type's attributes, and its size follows it from here)
        refreshDimensions();
        if (kind == Kind.CRONUS) addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false));
        if (kind == Kind.OCEANUS) addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, MobEffectInstance.INFINITE_DURATION, 0, true, false));
        refreshBar();
    }

    @Override
    public Component getName() {
        return hasCustomName() ? super.getName() : kind().displayName();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    private void refreshBar() {
        Component name = kind().displayName().copy().withStyle(style -> style.withColor(kind().line.color()));
        bar.setName(casting == null ? name : Component.empty().append(name).append(Component.literal("  -  ")).append(Hud.colored(casting.displayName(), 0xFFFFFF)));
        bar.setColor(phase() >= 3 ? BossEvent.BossBarColor.RED : switch (kind()) {
            case HYPERION -> BossEvent.BossBarColor.YELLOW;
            case OCEANUS -> BossEvent.BossBarColor.BLUE;
            case CRONUS -> BossEvent.BossBarColor.PURPLE;
        });
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, getHealth() / getMaxHealth())));
    }

    // ---- the fight -------------------------------------------------------------------------------------------

    private List<Player> players(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 8, radius), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private DamageSource blow() {
        return damageSources().indirectMagic(this, this);
    }

    private void after(int ticks, Runnable action) {
        later.add(new Object[]{tickCount + ticks, action});
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (shieldTicks > 0) {
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.STRIKE.type(), getX(), getY() + 1.5, getZ(), 6, 0.6, 1.0, 0.6, 0.1);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) updatePhase(level);
        return hurt;
    }

    private void updatePhase(ServerLevel level) {
        float share = getHealth() / getMaxHealth();
        int next = share <= 0.3F ? 3 : share <= 0.6F ? 2 : 1;
        if (next <= phase()) return;
        entityData.set(DATA_PHASE, next);
        // a phase begins: the Titan is untouchable a moment while it throws everyone back and the roof answers
        shieldTicks = SHIELD_TICKS;
        casting = null;
        entityData.set(DATA_CASTING, false);
        cooldown = SHIELD_TICKS + 20;
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, kind() == Kind.CRONUS ? 0.6F : 0.9F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 1, 0, 0, 0, 0);
        for (Player p : players(level, 10)) {
            Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
            p.setDeltaMovement(away.x * 1.2, 0.6, away.z * 1.2);
            p.needsSync = true;
        }
        Onslaughts.begin(level, null, kind().line, next == 2 ? Onslaughts.Kind.RINGS : Onslaughts.Kind.SHOCKWAVE, position(), 12, 5.0F * scale());
        if (next == 3) {
            addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, true, false));
            addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, true, false));
        }
        String hud = "titan." + RealmsOfChaos.MOD_ID + ".phase" + next;
        for (Player p : players(level, 40)) {
            if (p instanceof ServerPlayer sp) {
                com.thefarawaydev.realmsofchaos.ui.Cards.show(sp, next == 2 ? com.thefarawaydev.realmsofchaos.ui.HudArt.Card.AEGIS : com.thefarawaydev.realmsofchaos.ui.HudArt.Card.WRATH,
                        com.thefarawaydev.realmsofchaos.ui.Words.of(kind().displayName()), com.thefarawaydev.realmsofchaos.ui.Words.of(hud), true);
            }
        }
        refreshBar();
    }

    /** Begins winding up a move: its name on the bar, its arms raised, its marks on the ground. */
    public void begin(ServerLevel level, Move move) {
        LivingEntity target = getTarget();
        casting = move;
        windup = move.windup;
        marks.clear();
        marked = target == null ? null : target.getUUID();
        Vec3 to = target == null ? getLookAngle() : target.position().subtract(position());
        aim = new Vec3(to.x, 0, to.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(to.x, 0, to.z).normalize();
        switch (move) {
            case FALLING_STARS -> {
                for (Player p : players(level, 24)) marks.add(p.position());
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 6;
                    marks.add(new Vec3(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r));
                }
            }
            case WATERSPOUTS -> {
                for (Player p : players(level, 24)) marks.add(p.position());
            }
            default -> { }
        }
        entityData.set(DATA_CASTING, true);
        level.playSound(null, blockPosition(), switch (kind()) {
            case HYPERION -> SoundEvents.BEACON_POWER_SELECT;
            case OCEANUS -> SoundEvents.CONDUIT_ACTIVATE;
            case CRONUS -> SoundEvents.WARDEN_SONIC_CHARGE;
        }, SoundSource.HOSTILE, 2.0F, 0.8F);
        String cue = "titan." + RealmsOfChaos.MOD_ID + ".cue." + move.id();
        for (Player p : players(level, 32)) if (p instanceof ServerPlayer sp) Hud.actionBar(sp, Hud.colored(Component.translatable(cue), 0xFFAA00));
        refreshBar();
    }

    private void spark(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread) {
        level.sendParticles(particle, at.x, at.y + 0.15, at.z, count, spread, 0.05, spread, 0.0);
    }

    /** One tick of a move being wound up: what it is about to do, drawn where it will happen. */
    private void telegraph(ServerLevel level) {
        if (windup % 3 != 0) return;
        switch (casting) {
            case SOLAR_LANCE -> {
                for (int i = 2; i <= 20; i += 2) spark(level, com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), position().add(aim.scale(i)), 1, 0.15);
            }
            case BLINDING_DAWN -> level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), getX(), getY() + 2.5, getZ(), 8 + (casting.windup - windup) / 2, 0.4, 0.4, 0.4, 0.02);
            case FALLING_STARS -> {
                for (Vec3 m : marks) for (int k = 0; k < 8; k++) spark(level, com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), m.add(Math.cos(k * 0.785) * 1.8, 0, Math.sin(k * 0.785) * 1.8), 1, 0.05);
            }
            case WATERSPOUTS -> {
                for (Vec3 m : marks) spark(level, ParticleTypes.BUBBLE_POP, m, 10, 0.8);
            }
            case UNDERTOW -> {
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8 + windup * 0.3;
                    spark(level, com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type(), position().add(Math.cos(a) * 9, 0, Math.sin(a) * 9), 2, 0.2);
                }
            }
            case SCYTHE_SWEEP -> {
                double base = Math.atan2(aim.z, aim.x);
                for (int k = -6; k <= 6; k++) {
                    double a = base + k * Math.toRadians(10);
                    spark(level, com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP.type(), position().add(Math.cos(a) * 5.5, 0, Math.sin(a) * 5.5), 1, 0.05);
                }
            }
            case DEVOUR_TIME -> {
                level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 1.4F, 0.5F + (casting.windup - windup) / 40.0F);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), getX(), getY() + 1.5, getZ(), 20, 3.0, 1.0, 3.0, 0.05);
            }
            case REWIND -> {
                Player p = marked == null ? null : level.getPlayerByUUID(marked);
                Vec3 back = p == null ? null : rewindTo(p);
                if (back != null) spark(level, com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), back, 16, 0.4);
            }
            default -> level.sendParticles(spell(), getX(), getY() + 2.5, getZ(), 6, 0.8, 0.8, 0.8, 0.05);
        }
    }

    private ParticleOptions spell() {
        return switch (kind()) {
            case HYPERION -> com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type();
            case OCEANUS -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type();
            case CRONUS -> com.thefarawaydev.realmsofchaos.fx.RocParticles.SOUL_WISP.type();
        };
    }

    /** Where Rewind would send a player: where they stood three seconds ago, or null if they have not been seen that long. */
    public Vec3 rewindTo(Player player) {
        ArrayDeque<Vec3> seen = trail.get(player.getUUID());
        return seen == null || seen.size() < 6 ? null : seen.peekFirst();
    }

    /** The move lands. @return how many it caught */
    public int release(ServerLevel level, Move move) {
        float s = scale();
        int caught = 0;
        switch (move) {
            case SOLAR_LANCE -> {
                level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0F, 1.4F);
                for (int i = 1; i <= 20; i++) level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), getX() + aim.x * i, getY() + 1.4, getZ() + aim.z * i, 6, 0.2, 0.2, 0.2, 0.02);
                for (Player p : players(level, 22)) {
                    Vec3 d = p.position().subtract(position());
                    double along = d.x * aim.x + d.z * aim.z, across = Math.abs(-d.x * aim.z + d.z * aim.x);
                    if (along < 0 || along > 20 || across > 1.4 || Math.abs(d.y) > 3) continue;
                    p.hurtServer(level, blow(), 9.0F * s);
                    p.setRemainingFireTicks(80);
                    caught++;
                }
            }
            case BLINDING_DAWN -> {
                level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 1.6F);
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), getX(), getY() + 2.0, getZ(), 160, 1.5, 1.5, 1.5, 0.6);
                for (Player p : players(level, 22)) {
                    // only those looking at him: anyone who turned away is spared
                    Vec3 toHim = position().add(0, 2, 0).subtract(p.getEyePosition()).normalize();
                    if (p.getLookAngle().dot(toHim) < 0.45) continue;
                    p.hurtServer(level, blow(), 6.0F * s);
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    caught++;
                }
            }
            case FALLING_STARS -> {
                List<Vec3> spots = new ArrayList<>(marks);
                after(12, () -> {
                    for (Vec3 m : spots) {
                        level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 1, 0, 0, 0, 0);
                        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.EMBER.type(), m.x, m.y + 0.5, m.z, 10, 0.6, 0.4, 0.6, 0.1);
                        level.playSound(null, BlockPos.containing(m), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9F, 1.2F);
                        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(2.4, 3, 2.4), e -> e.isAlive() && !e.isCreative() && !e.isSpectator())) {
                            p.hurtServer(level, blow(), 8.0F * scale());
                            p.setRemainingFireTicks(60);
                        }
                    }
                });
                caught = spots.size();
            }
            case CORONA -> {
                Onslaughts.begin(level, null, Line.ZEUS, Onslaughts.Kind.SHOCKWAVE, position(), 13, 6.0F * s);
                caught = 1;
            }
            case UNDERTOW -> {
                level.playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 3.0F, 0.5F);
                for (Player p : players(level, 18)) {
                    Vec3 in = position().subtract(p.position()).multiply(1, 0, 1);
                    double d = in.length();
                    if (d < 1.5) continue;
                    Vec3 pull = in.normalize().scale(Math.min(1.6, 0.25 + d * 0.12));
                    p.setDeltaMovement(pull.x, 0.35, pull.z);
                    p.needsSync = true;
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    caught++;
                }
                after(14, () -> {
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type(), getX(), getY() + 0.5, getZ(), 80, 3.0, 0.5, 3.0, 0.4);
                    level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
                    for (Player p : players(level, 4.5)) {
                        p.hurtServer(level, blow(), 9.0F * scale());
                        p.setDeltaMovement(p.getDeltaMovement().x, 0.9, p.getDeltaMovement().z);
                        p.needsSync = true;
                    }
                });
            }
            case WATERSPOUTS -> {
                for (Vec3 m : marks) {
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type(), m.x, m.y + 1.0, m.z, 60, 0.4, 2.0, 0.4, 0.3);
                    level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, m.x, m.y + 0.5, m.z, 40, 0.3, 1.5, 0.3, 0.2);
                    for (Player p : level.getEntitiesOfClass(Player.class, new AABB(m, m).inflate(1.9, 3, 1.9), e -> e.isAlive() && !e.isCreative() && !e.isSpectator())) {
                        p.hurtServer(level, blow(), 6.0F * s);
                        p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.35, p.getDeltaMovement().z * 0.3);
                        p.needsSync = true;
                        caught++;
                    }
                }
                level.playSound(null, blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 2.0F, 0.7F);
            }
            case TIDAL_WALL -> {
                Onslaughts.begin(level, null, Line.POSEIDON, Onslaughts.Kind.SWEEP, position(), 14, 6.0F * s);
                caught = 1;
            }
            case CALL_OF_THE_DEEP -> {
                int already = level.getEntitiesOfClass(Sentinel.class, getBoundingBox().inflate(24), e -> e.entityTags().contains(ADD_TAG)).size();
                for (int i = 0; i < 3 - already; i++) {
                    Sentinel add = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                    if (add == null) continue;
                    double a = Math.PI * 2 * i / 3;
                    add.setUp(Sentinel.Kind.TIDE_HOPLITE, 1, 2.0, false);
                    add.snapTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, 0, 0);
                    add.addTag(ADD_TAG);
                    add.setCustomName(Hud.colored(Component.translatable("citadel." + RealmsOfChaos.MOD_ID + ".halfkin", com.thefarawaydev.realmsofchaos.world.Tower.god(Line.POSEIDON, com.thefarawaydev.realmsofchaos.world.Tower.FLOORS).displayName()), Line.POSEIDON.color()));
                    if (getTarget() != null) add.setTarget(getTarget());
                    level.addFreshEntity(add);
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.SEA_SPRAY.type(), add.getX(), add.getY() + 1, add.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
                    caught++;
                }
                Onslaughts.begin(level, null, Line.POSEIDON, Onslaughts.Kind.RINGS, position(), 13, 5.0F * s);
            }
            case SCYTHE_SWEEP -> {
                level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
                double base = Math.atan2(aim.z, aim.x);
                for (int k = -12; k <= 12; k++) {
                    double a = base + k * Math.toRadians(5);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(a) * 4, getY() + 1.2, getZ() + Math.sin(a) * 4, 1, 0, 0, 0, 0);
                }
                for (Player p : players(level, 7)) {
                    Vec3 d = p.position().subtract(position()).multiply(1, 0, 1);
                    if (d.length() > 6.5 || d.normalize().dot(aim) < Math.cos(Math.toRadians(62))) continue;
                    p.hurtServer(level, blow(), 10.0F * s);
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                    caught++;
                }
            }
            case DEVOUR_TIME -> {
                level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.4F);
                for (Player p : players(level, 20)) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                    p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 80, 1));
                    caught++;
                }
                heal(getMaxHealth() * 0.04F * Math.max(1, caught));
                addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 1, true, false));
            }
            case REWIND -> {
                Player p = marked == null ? null : level.getPlayerByUUID(marked);
                Vec3 back = p == null ? null : rewindTo(p);
                if (p != null && back != null && p.isAlive() && p.level() == level && p.distanceToSqr(this) < 40 * 40) {
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
                    com.thefarawaydev.realmsofchaos.world.Fx.place(level, p, back.x, back.y, back.z);
                    p.hurtServer(level, blow(), 5.0F * s);
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
                    caught = 1;
                }
            }
            case AGE_OF_IRON -> {
                Onslaughts.begin(level, null, Line.HADES, Onslaughts.Kind.FISSURES, position(), 13, 6.0F * s);
                addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1, true, false));
                addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1, true, false));
                caught = 1;
            }
        }
        return caught;
    }

    /** Picks the next move: one it has in this phase, never the same twice running. */
    public Move choose() {
        List<Move> open = new ArrayList<>();
        for (Move m : Move.of(kind())) if (m.phase <= phase() && m != last) open.add(m);
        if (open.isEmpty()) return null;
        // (its greatest move, once it has it, comes up twice as often)
        for (Move m : new ArrayList<>(open)) if (m.phase == 3) open.add(m);
        return open.get(random.nextInt(open.size()));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (shieldTicks > 0) {
            shieldTicks--;
            if (shieldTicks % 4 == 0) level.sendParticles(spell(), getX(), getY() + 1.5, getZ(), 12, 1.0, 1.2, 1.0, 0.05);
        }
        // things set to happen a few ticks after a move
        for (Iterator<Object[]> it = later.iterator(); it.hasNext(); ) {
            Object[] job = it.next();
            if (tickCount >= (int) job[0]) {
                it.remove();
                ((Runnable) job[1]).run();
            }
        }
        // where everyone near has stood lately, for Rewind
        if (kind() == Kind.CRONUS && tickCount % 10 == 0) {
            for (Player p : players(level, 32)) {
                ArrayDeque<Vec3> seen = trail.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
                seen.addLast(p.position());
                while (seen.size() > 6) seen.removeFirst();
            }
        }
        // its bar, for whoever is near
        if (tickCount % 10 == 0) {
            refreshBar();
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (p.level() != level || p.distanceToSqr(this) > 48 * 48) bar.removePlayer(p);
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) <= 36 * 36 && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        // kept to its post: knocked off the roof, it is set back on it
        if (post != null && tickCount % 20 == 0 && (distanceToSqr(Vec3.atBottomCenterOf(post)) > (LEASH + 4) * (LEASH + 4) || getY() < post.getY() - 3)) {
            com.thefarawaydev.realmsofchaos.world.Fx.place(level, this, post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
            level.sendParticles(spell(), getX(), getY() + 1, getZ(), 30, 0.6, 1.0, 0.6, 0.1);
        }
        LivingEntity target = getTarget();
        if (casting != null) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            telegraph(level);
            if (--windup <= 0) {
                Move done = casting;
                casting = null;
                last = done;
                entityData.set(DATA_CASTING, false);
                release(level, done);
                cooldown = phase() >= 3 ? 50 : phase() == 2 ? 65 : 80;
                refreshBar();
            }
            return;
        }
        if (target == null || !target.isAlive() || shieldTicks > 0) return;
        if (--cooldown <= 0) {
            Move next = choose();
            if (next != null) begin(level, next);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        if (!(level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 0.5, 0.5, 0.5, 0);
        level.playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0F, 0.7F);
        for (Sentinel add : level.getEntitiesOfClass(Sentinel.class, getBoundingBox().inflate(32), e -> e.entityTags().contains(ADD_TAG))) add.discard();
        if (source.getEntity() instanceof ServerPlayer killer) com.thefarawaydev.realmsofchaos.world.Citadels.fallen(this, killer);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("TitanKind", kind().ordinal());
        output.putInt("TitanPhase", phase());
        if (post != null) {
            output.putInt("PostX", post.getX());
            output.putInt("PostY", post.getY());
            output.putInt("PostZ", post.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_KIND, Mth.clamp(input.getIntOr("TitanKind", 0), 0, Kind.values().length - 1));
        entityData.set(DATA_PHASE, Mth.clamp(input.getIntOr("TitanPhase", 1), 1, 3));
        int y = input.getIntOr("PostY", Integer.MIN_VALUE);
        if (y != Integer.MIN_VALUE) {
            post = new BlockPos(input.getIntOr("PostX", 0), y, input.getIntOr("PostZ", 0));
            setHomeTo(post, LEASH);
        }
        refreshBar();
    }

    /** Test seam: the Titan's health set to a share of its most, and its phase brought up to match. */
    public void woundTo(ServerLevel level, float share) {
        setHealth(getMaxHealth() * share);
        updatePhase(level);
    }

    /** Test seam: what a Titan remembers of a player's movements, as if it had watched them for three seconds. */
    public void remember(Player player, Vec3 where) {
        ArrayDeque<Vec3> seen = trail.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        seen.clear();
        for (int i = 0; i < 6; i++) seen.addLast(where);
    }

    /** Test seam: winds up a move and lets it land at once. @return how many it caught */
    public int force(ServerLevel level, Move move) {
        begin(level, move);
        casting = null;
        entityData.set(DATA_CASTING, false);
        last = move;
        return release(level, move);
    }

    /** Test seam: runs everything set to happen later, now. */
    public void flush() {
        for (Object[] job : new ArrayList<>(later)) ((Runnable) job[1]).run();
        later.clear();
    }
}
