package com.thefarawaydev.realmsofchaos.entity;

import net.minecraft.server.level.ServerPlayer;
import com.thefarawaydev.realmsofchaos.world.Combat;
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
 * The passages' own enemies. One entity type, seventy-five kinds: twenty-five to a tower.
 *
 * <p>Every kind has a <i>role</i>, which decides how it fights and how tough it is - soldiers close in, casters
 * stand off and shoot, skirmishers are quick and leap, brutes hit slowly and send you flying, mystics hang back and
 * work rites, and a Keeper stands on every seal. Most kinds also have a <i>trait</i>: the one thing that kind does
 * that no other in its tower does. Traits are what make a room of sentinels more than the sum of its parts:
 *
 * <ul>
 * <li><b>Shield</b> - blows from the front glance off it, and allies standing by it are hurt less.
 * <li><b>Banner</b> - a rite that strengthens every ally near; when it falls, they falter.
 * <li><b>Bomb</b> - runs at you, hisses for a second and a half, and bursts.
 * <li><b>Bind</b> - its bolt roots you (Zeus), drags you to it (Poseidon) or blinds you (Hades).
 * <li><b>Horn</b> - sounds the alarm: wakes the rooms next door and sets every sentinel near on you.
 * <li><b>Disarm</b> - its blow knocks your weapon out of your hand and into your pack.
 * <li><b>Trap</b> - its bolts leave a patch of ground that hurts to stand on.
 * <li><b>Mirage</b> - hurt, it splits into three: two of them are not really there.
 * <li><b>Link</b> - takes half of every blow that lands on an ally near it.
 * <li><b>Frenzy</b> - grows stronger each time an ally falls, and wild when it is nearly beaten.
 * <li><b>Revive</b> - a rite that stands a fallen ally back up.
 * <li><b>Snipe</b> - a long, slow, heavy shot along a line of light you can step out of.
 * <li><b>Charge</b> - puts its head down and runs you through.
 * <li><b>Silence</b> - its bolt stops your relic's move from being cast for a while.
 * <li><b>Swap</b> - trades places with a wounded ally, and mends it.
 * <li><b>Duelist</b> - turns the first blow struck at it from the front, and answers it; then its guard is down a while.
 * <li><b>Volley</b> - looses a fan of five bolts at once.
 * <li><b>Whirl</b> - spins up for a second, then cuts everything round it.
 * <li><b>Slam</b> - marks the ground under you, leaps, and comes down on the mark in a shockwave.
 * </ul>
 *
 * How strong a sentinel is follows its <i>threat</i>: how far up the tower its passage lies and how deep into that
 * passage it stands. An elite is half as tough again, glows, and always gives up forge material.
 */
public class Sentinel extends Monster implements RangedAttackMob {
    public enum Role { SOLDIER, CASTER, SKIRMISHER, BRUTE, MYSTIC, KEEPER }

    /** The one thing a kind does that no other in its tower does. */
    public enum Trait {
        NONE(0), SHIELD(0), BIND(1), BOMB(2), FRENZY(2), DISARM(3), TRAP(3), SNIPE(4), SWAP(4), CHARGE(5), MIRAGE(5), LINK(6), SILENCE(6), BANNER(7), HORN(8), REVIVE(9),
        // (added later)
        DUELIST(2), VOLLEY(1), WHIRL(3), SLAM(5);

        /** The threat a level needs before kinds with this trait turn up on it. */
        public final double unlock;

        Trait(double unlock) {
            this.unlock = unlock;
        }
    }

    public enum Kind {
        STORM_HOPLITE(Line.ZEUS, Role.SOLDIER), SKY_ARCHER(Line.ZEUS, Role.CASTER), THUNDER_BRUTE(Line.ZEUS, Role.BRUTE), KEEPER_SKY(Line.ZEUS, Role.KEEPER),
        TIDE_HOPLITE(Line.POSEIDON, Role.SOLDIER), SIREN(Line.POSEIDON, Role.CASTER), REEF_BRUTE(Line.POSEIDON, Role.BRUTE), KEEPER_TIDES(Line.POSEIDON, Role.KEEPER),
        SHADE(Line.HADES, Role.SOLDIER), LAMPAD(Line.HADES, Role.CASTER), TARTARUS_BRUTE(Line.HADES, Role.BRUTE), KEEPER_SHADES(Line.HADES, Role.KEEPER),
        // (added later: kept at the end so saved sentinels keep their kind)
        HARPY(Line.ZEUS, Role.SKIRMISHER), STORM_AUGUR(Line.ZEUS, Role.MYSTIC),
        TELCHINE(Line.POSEIDON, Role.SKIRMISHER), TIDE_PRIEST(Line.POSEIDON, Role.MYSTIC),
        FURY(Line.HADES, Role.SKIRMISHER), NECROMANCER(Line.HADES, Role.MYSTIC),
        // ---- fifteen more to a tower, each with a trait of its own
        AEGIS_BEARER(Line.ZEUS, Role.SOLDIER, Trait.SHIELD), EAGLE_STANDARD(Line.ZEUS, Role.MYSTIC, Trait.BANNER), SPARK_RUNNER(Line.ZEUS, Role.SKIRMISHER, Trait.BOMB),
        CHAIN_THROWER(Line.ZEUS, Role.CASTER, Trait.BIND), THUNDER_HERALD(Line.ZEUS, Role.MYSTIC, Trait.HORN), GUST_THIEF(Line.ZEUS, Role.SKIRMISHER, Trait.DISARM),
        STORM_SOWER(Line.ZEUS, Role.CASTER, Trait.TRAP), MIRAGE(Line.ZEUS, Role.CASTER, Trait.MIRAGE), OATHKEEPER(Line.ZEUS, Role.BRUTE, Trait.LINK),
        MAENAD(Line.ZEUS, Role.SOLDIER, Trait.FRENZY), PAEAN_SINGER(Line.ZEUS, Role.MYSTIC, Trait.REVIVE), BOLT_HURLER(Line.ZEUS, Role.CASTER, Trait.SNIPE),
        BULL_CHARGER(Line.ZEUS, Role.BRUTE, Trait.CHARGE), ORACLE_BREAKER(Line.ZEUS, Role.CASTER, Trait.SILENCE), WIND_DANCER(Line.ZEUS, Role.SKIRMISHER, Trait.SWAP),
        SHELL_GUARD(Line.POSEIDON, Role.SOLDIER, Trait.SHIELD), TIDE_HERALD(Line.POSEIDON, Role.MYSTIC, Trait.BANNER), PUFFERLING(Line.POSEIDON, Role.SKIRMISHER, Trait.BOMB),
        NET_CASTER(Line.POSEIDON, Role.CASTER, Trait.BIND), CONCH_BLOWER(Line.POSEIDON, Role.MYSTIC, Trait.HORN), WRECKER(Line.POSEIDON, Role.SKIRMISHER, Trait.DISARM),
        BRINE_LAYER(Line.POSEIDON, Role.CASTER, Trait.TRAP), MIST_DANCER(Line.POSEIDON, Role.CASTER, Trait.MIRAGE), TIDE_WARDEN(Line.POSEIDON, Role.BRUTE, Trait.LINK),
        SEA_RAIDER(Line.POSEIDON, Role.SOLDIER, Trait.FRENZY), PEARL_MENDER(Line.POSEIDON, Role.MYSTIC, Trait.REVIVE), HARPOONER(Line.POSEIDON, Role.CASTER, Trait.SNIPE),
        WAVE_RIDER(Line.POSEIDON, Role.BRUTE, Trait.CHARGE), LULL_SINGER(Line.POSEIDON, Role.CASTER, Trait.SILENCE), CURRENT_SLIDER(Line.POSEIDON, Role.SKIRMISHER, Trait.SWAP),
        GATE_WARDEN(Line.HADES, Role.SOLDIER, Trait.SHIELD), STYX_HERALD(Line.HADES, Role.MYSTIC, Trait.BANNER), EMBER_WRETCH(Line.HADES, Role.SKIRMISHER, Trait.BOMB),
        SHACKLER(Line.HADES, Role.CASTER, Trait.BIND), DEATH_KNELL(Line.HADES, Role.MYSTIC, Trait.HORN), GRAVE_ROBBER(Line.HADES, Role.SKIRMISHER, Trait.DISARM),
        MIASMA_SOWER(Line.HADES, Role.CASTER, Trait.TRAP), PHANTASM(Line.HADES, Role.CASTER, Trait.MIRAGE), SOUL_WARDEN(Line.HADES, Role.BRUTE, Trait.LINK),
        REVENANT(Line.HADES, Role.SOLDIER, Trait.FRENZY), PSYCHOPOMP(Line.HADES, Role.MYSTIC, Trait.REVIVE), BONE_LANCER(Line.HADES, Role.CASTER, Trait.SNIPE),
        CHARIOT_SHADE(Line.HADES, Role.BRUTE, Trait.CHARGE), LETHE_DRINKER(Line.HADES, Role.CASTER, Trait.SILENCE), SHADOW_STEPPER(Line.HADES, Role.SKIRMISHER, Trait.SWAP),
        // ---- four more to a tower, each with an attack of its own
        STORM_DUELIST(Line.ZEUS, Role.SOLDIER, Trait.DUELIST), GALE_SLINGER(Line.ZEUS, Role.CASTER, Trait.VOLLEY),
        CYCLONE_REAVER(Line.ZEUS, Role.BRUTE, Trait.WHIRL), SKYFALL_BRUTE(Line.ZEUS, Role.BRUTE, Trait.SLAM),
        CORAL_DUELIST(Line.POSEIDON, Role.SOLDIER, Trait.DUELIST), SPINE_THROWER(Line.POSEIDON, Role.CASTER, Trait.VOLLEY),
        MAELSTROM_REAVER(Line.POSEIDON, Role.BRUTE, Trait.WHIRL), DEPTH_CRUSHER(Line.POSEIDON, Role.BRUTE, Trait.SLAM),
        GRAVE_FENCER(Line.HADES, Role.SOLDIER, Trait.DUELIST), PYRE_ARCHER(Line.HADES, Role.CASTER, Trait.VOLLEY),
        SCYTHE_REAPER(Line.HADES, Role.BRUTE, Trait.WHIRL), TOMB_BREAKER(Line.HADES, Role.BRUTE, Trait.SLAM);

        public final Line line;
        public final Role role;
        public final Trait trait;

        Kind(Line line, Role role) {
            this(line, role, Trait.NONE);
        }

        Kind(Line line, Role role, Trait trait) {
            this.line = line;
            this.role = role;
            this.trait = trait;
        }

        public String id() {
            return name().toLowerCase();
        }

        public Component displayName() {
            return Component.translatable("entity." + RealmsOfChaos.MOD_ID + ".sentinel." + id());
        }

        /** The plain kind of a role in a tower: the one without a trait. */
        public static Kind of(Line line, Role role) {
            for (Kind k : values()) if (k.line == line && k.role == role) return k;
            throw new IllegalArgumentException(line + " " + role);
        }

        public static Kind of(Line line, Trait trait) {
            for (Kind k : values()) if (k.line == line && k.trait == trait && (trait != Trait.NONE)) return k;
            throw new IllegalArgumentException(line + " " + trait);
        }

        public static java.util.List<Kind> of(Line line) {
            java.util.List<Kind> out = new java.util.ArrayList<>();
            for (Kind k : values()) if (k.line == line) out.add(k);
            return out;
        }

        /** Every kind of a role a level of this threat can field: the plain one first, then those whose trait it has unlocked. */
        public static java.util.List<Kind> pool(Line line, Role role, double threat) {
            java.util.List<Kind> out = new java.util.ArrayList<>();
            for (Kind k : values()) if (k.line == line && k.role == role && (k.trait == Trait.NONE || threat >= k.trait.unlock)) out.add(k);
            return out;
        }

        /** Who fills a post: the plain kind half the time, otherwise one of the others the level has unlocked. */
        public static Kind pick(Line line, Role role, double threat, java.util.Random random) {
            java.util.List<Kind> pool = pool(line, role, threat);
            return pool.size() == 1 || random.nextBoolean() ? pool.get(0) : pool.get(1 + random.nextInt(pool.size() - 1));
        }
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(Sentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ELITE = SynchedEntityData.defineId(Sentinel.class, EntityDataSerializers.BOOLEAN);
    private static final int MOVE_EVERY = 160, WARN_TICKS = 20, RITE_EVERY = 120;
    public static final String RAISED_TAG = "realms_of_chaos_raised";
    /** How long a bomber hisses before it bursts, an illusion lasts, and a sniper aims. */
    public static final int FUSE_TICKS = 30, ILLUSION_TICKS = 20 * 15, AIM_TICKS = 25;
    public static final int MAX_FRENZY = 4;

    /** What a reviver remembers of an ally that fell near it. */
    private record Fallen(Kind kind, int tier, double threat, boolean elite, Vec3 at, java.util.Set<String> tags) {}

    private int tier = 1;
    private double threat;
    private int riteCooldown = 80;
    private Goal attackGoal, leapGoal;
    private int moveCooldown = 100;
    private int heavyCooldown = 60, heavyWindup;
    // ---- traits
    private int fuse = -1, trickCooldown = 60, frenzy, revives, life;
    private boolean illusion, split, wild, sharing;
    private Fallen fallen;
    // ---- the attacks of duelists, reavers and slammers
    private int guardCooldown = 40, specialCooldown = 60, whirlWindup, slamPhase, slamTimer;
    private Vec3 slamMark;
    /** How long a duelist's guard is down after it turns a blow; a reaver's spin-up; a slammer's crouch. */
    public static final int GUARD_EVERY = 100, WHIRL_WINDUP = 20, SLAM_WINDUP = 25, COUNTER_DELAY = 6;
    public static final double WHIRL_RADIUS = 3.2, SLAM_RADIUS = 3.0;

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
        builder.define(DATA_ELITE, false);
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

    public double threat() {
        return threat;
    }

    public boolean isElite() {
        return this.entityData.get(DATA_ELITE);
    }

    /** Makes this the given kind of sentinel of a tier-1 passage's first level: the weakest there is. */
    public void setUp(Kind kind, int tier) {
        setUp(kind, tier, 0.0, false);
    }

    /**
     * Makes this the given kind of sentinel.
     *
     * @param tier   the tower tier (1-3) its passage belongs to: decides what forge material it can drop
     * @param threat how dangerous its place is - see {@link #threat(int, int)}
     * @param elite  half as tough again, glowing, and sure to drop material
     */
    public void setUp(Kind kind, int tier, double threat, boolean elite) {
        this.entityData.set(DATA_KIND, kind.ordinal());
        this.entityData.set(DATA_ELITE, elite);
        this.tier = Math.max(1, Math.min(3, tier));
        this.threat = Math.max(0.0, threat);
        configure(true);
    }

    /** The threat of a place: each floor further up the tower adds one, each level deeper into a passage three quarters. */
    public static double threat(int passage, int stage) {
        return Math.max(0, passage - 1) + 0.75 * Math.max(0, stage);
    }

    /** A kind's health: its role's, shifted by its trait - a bomber is frail, a shield-bearer and a warden stout. */
    public static double health(Kind kind, double threat) {
        double trait = switch (kind.trait) {
            case BOMB -> 0.6;
            case SHIELD, LINK -> 1.2;
            case MIRAGE, SWAP -> 0.9;
            default -> 1.0;
        };
        return health(kind.role, threat) * trait;
    }

    public static double health(Role role, double threat) {
        double base = switch (role) {
            case SOLDIER -> 22.0;
            case CASTER -> 18.0;
            case SKIRMISHER -> 16.0;
            case BRUTE -> 40.0;
            case MYSTIC -> 20.0;
            case KEEPER -> 70.0;
        };
        return base * (1.0 + 0.07 * threat);
    }

    public static double damage(Role role, double threat) {
        double base = switch (role) {
            case SOLDIER -> 4.0;
            case CASTER -> 3.0;
            case SKIRMISHER -> 3.5;
            case BRUTE -> 7.0;
            case MYSTIC -> 2.0;
            case KEEPER -> 6.0;
        };
        return base * (1.0 + 0.045 * threat);
    }

    private void configure(boolean heal) {
        Kind kind = kind();
        Role role = kind.role;
        boolean elite = isElite();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(illusion ? 1.0 : health(kind, threat) * (elite ? 1.5 : 1.0));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(illusion ? 0.0 : damage(role, threat) * (elite ? 1.25 : 1.0) * (1.0 + 0.15 * frenzy));
        double speed = role == Role.BRUTE ? 0.22 : role == Role.SKIRMISHER ? 0.36 : kind == Kind.SHADE ? 0.33 : 0.28;
        if (kind.trait == Trait.SHIELD) speed = 0.24;
        if (kind.trait == Trait.DUELIST) speed = 0.31;
        if (kind.trait == Trait.BOMB) speed = 0.40;
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed * (1.0 + 0.05 * frenzy));
        getAttribute(Attributes.ARMOR).setBaseValue(kind.trait == Trait.SHIELD ? 8.0 : kind.trait == Trait.DUELIST ? 5.0 : 2.0);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(kind.trait == Trait.SNIPE ? 36.0 : 24.0);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(role == Role.BRUTE ? 0.6 : role == Role.KEEPER ? 0.4 : 0.0);
        getAttribute(Attributes.SCALE).setBaseValue((role == Role.KEEPER ? 1.25 : role == Role.BRUTE ? 1.15 : role == Role.SKIRMISHER ? 0.9 : 1.0) * (elite ? 1.1 : 1.0));
        if (heal) setHealth(getMaxHealth());
        this.xpReward = (role == Role.KEEPER ? 20 : role == Role.BRUTE ? 10 : 6) * (elite ? 2 : 1);
        if (illusion) this.xpReward = 0;
        setGlowingTag(elite);
        if (attackGoal != null) this.goalSelector.removeGoal(attackGoal);
        if (leapGoal != null) this.goalSelector.removeGoal(leapGoal);
        boolean ranged = role == Role.CASTER || role == Role.MYSTIC;
        attackGoal = kind.trait == Trait.SNIPE ? new RangedAttackGoal(this, 0.9, 80 - 5 * tier, 22.0F)
                : ranged ? new RangedAttackGoal(this, 1.0, Math.max(25, 45 - 5 * tier), role == Role.MYSTIC ? 10.0F : 12.0F) : new MeleeAttackGoal(this, 1.1, true);
        this.goalSelector.addGoal(2, attackGoal);
        // a skirmisher does not walk up to you: it jumps
        leapGoal = role == Role.SKIRMISHER ? new net.minecraft.world.entity.ai.goal.LeapAtTargetGoal(this, 0.45F) : null;
        if (leapGoal != null) this.goalSelector.addGoal(1, leapGoal);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(heldItem(kind)));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static Item heldItem(Kind kind) {
        return switch (kind.trait) {
            case SHIELD -> Items.SHIELD;
            case BANNER -> kind.line == Line.ZEUS ? Items.GOLDEN_SPEAR : kind.line == Line.POSEIDON ? Items.PRISMARINE_SHARD : Items.WITHER_ROSE;
            case BOMB -> kind.line == Line.ZEUS ? Items.GLOWSTONE_DUST : kind.line == Line.POSEIDON ? Items.PUFFERFISH : Items.FIRE_CHARGE;
            case BIND -> kind.line == Line.ZEUS ? Items.IRON_CHAIN : kind.line == Line.POSEIDON ? Items.COBWEB : Items.LEAD;
            case HORN -> kind.line == Line.HADES ? Items.BELL : Items.GOAT_HORN;
            case DISARM -> kind.line == Line.HADES ? Items.IRON_SHOVEL : Items.SHEARS;
            case TRAP -> kind.line == Line.ZEUS ? Items.GLOWSTONE_DUST : kind.line == Line.POSEIDON ? Items.KELP : Items.FERMENTED_SPIDER_EYE;
            case MIRAGE -> Items.GLASS_BOTTLE;
            case LINK -> Items.IRON_AXE;
            case FRENZY -> kind.line == Line.ZEUS ? Items.STICK : kind.line == Line.POSEIDON ? Items.IRON_AXE : Items.NETHERITE_SWORD;
            case REVIVE -> kind.line == Line.ZEUS ? Items.GOLDEN_APPLE : kind.line == Line.POSEIDON ? Items.NAUTILUS_SHELL : Items.SOUL_LANTERN;
            case SNIPE -> kind.line == Line.POSEIDON ? Items.TRIDENT : Items.CROSSBOW;
            case CHARGE -> Items.MACE;
            case SILENCE -> kind.line == Line.ZEUS ? Items.AMETHYST_SHARD : kind.line == Line.POSEIDON ? Items.NAUTILUS_SHELL : Items.POTION;
            case SWAP -> Items.FEATHER;
            case DUELIST -> kind.line == Line.ZEUS ? Items.DIAMOND_SWORD : kind.line == Line.POSEIDON ? Items.IRON_SPEAR : Items.NETHERITE_SPEAR;
            case VOLLEY -> kind.line == Line.ZEUS ? Items.WIND_CHARGE : kind.line == Line.POSEIDON ? Items.PRISMARINE_CRYSTALS : Items.MAGMA_CREAM;
            case WHIRL -> kind.line == Line.ZEUS ? Items.DIAMOND_AXE : kind.line == Line.POSEIDON ? Items.TRIDENT : Items.NETHERITE_HOE;
            case SLAM -> kind.line == Line.ZEUS ? Items.HEAVY_CORE : kind.line == Line.POSEIDON ? Items.ANVIL : Items.NETHERITE_PICKAXE;
            case NONE -> switch (kind) {
                case STORM_HOPLITE -> Items.IRON_SWORD;
                case TIDE_HOPLITE -> Items.TRIDENT;
                case SHADE -> Items.GOLDEN_SWORD;
                case SKY_ARCHER -> Items.BOW;
                case SIREN -> Items.NAUTILUS_SHELL;
                case LAMPAD -> Items.SOUL_TORCH;
                case THUNDER_BRUTE, TARTARUS_BRUTE -> Items.MACE;
                case REEF_BRUTE -> Items.IRON_AXE;
                case HARPY -> Items.FEATHER;
                case TELCHINE -> Items.GOLDEN_AXE;
                case FURY -> Items.BLAZE_POWDER;
                case STORM_AUGUR -> Items.END_ROD;
                case TIDE_PRIEST -> Items.HEART_OF_THE_SEA;
                case NECROMANCER -> Items.BONE;
                default -> Items.BLAZE_ROD;
            };
        };
    }

    public Trait trait() {
        return kind().trait;
    }

    /** True for one of the two copies a Mirage splits into: it has one point of health and hurts nobody. */
    public boolean isIllusion() {
        return illusion;
    }

    public int frenzy() {
        return frenzy;
    }

    public boolean isWild() {
        return wild;
    }

    public int fuse() {
        return fuse;
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
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) + 1.0F;
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
        // sirens sing a bolt that slows; lampads throw torchlight that weakens; a mystic's bolt is a nuisance that marks you
        Power orb = switch (kind.line) {
            case ZEUS -> Power.LIGHTNING;
            case POSEIDON -> Power.FREEZE;
            case HADES -> Power.FIRE;
        };
        if (kind.trait == Trait.SNIPE) {
            snipe(level, target, orb, damage * 2.2F);
            return;
        }
        if (kind.trait == Trait.VOLLEY) {
            volley(level, target, orb, damage * 0.6F);
            return;
        }
        RelicProjectile bolt = new RelicProjectile(level, this, new ItemStack(ModContent.orbItem(orb)));
        bolt.setDamage(illusion ? 0.0F : damage);
        bolt.setCurse(kind.role == Role.MYSTIC ? MobEffects.GLOWING : kind == Kind.SIREN ? MobEffects.SLOWNESS : MobEffects.WEAKNESS);
        if (!illusion) {
            if (kind.trait == Trait.BIND || kind.trait == Trait.SILENCE) bolt.setRider(hit -> rider(level, hit));
            // a sower's bolt leaves its mark wherever it comes down, whether it found anyone or not
            if (kind.trait == Trait.TRAP) bolt.setLanding(at -> com.thefarawaydev.realmsofchaos.world.Hazards.add(level, at, 2.5, 160, kind.line, moveDamage() * 0.5F));
        }
        bolt.shoot(dx, target.getY(0.5) - bolt.getY(), dz, 1.4F, 5.0F);
        level.addFreshEntity(bolt);
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.0F, kind == Kind.SIREN ? 1.6F : 0.8F);
    }

    /**
     * A sniper's shot: a line of light from it to where its target stands, for a second and a quarter, and then a
     * fast, heavy bolt down that line. It is aimed at where the target was when the line appeared.
     */
    private void snipe(ServerLevel level, LivingEntity target, Power orb, float damage) {
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1, getZ()), to = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        level.playSound(null, blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        for (int delay = 0; delay < AIM_TICKS; delay += 5) {
            Runnable line = () -> {
                if (!isAlive()) return;
                for (int i = 1; i <= 24; i++) {
                    Vec3 at = from.lerp(to, i / 24.0);
                    level.sendParticles(Signature.spark(kind().line), at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            };
            if (delay == 0) line.run();
            else com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), delay, line);
        }
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), AIM_TICKS, () -> {
            if (!isAlive() || level() != level) return;
            RelicProjectile bolt = new RelicProjectile(level, this, new ItemStack(ModContent.orbItem(orb)));
            bolt.setDamage(damage);
            bolt.setCurse(MobEffects.SLOWNESS);                 // a hit that heavy staggers
            bolt.shoot(to.x - getX(), to.y - bolt.getY(), to.z - getZ(), 2.6F, 0.0F);
            level.addFreshEntity(bolt);
            level.playSound(null, blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.5F, 0.7F);
        });
    }

    /** A slinger's volley: five bolts at once, fanned across a little over thirty degrees. Step between them, or out. */
    public void volley(ServerLevel level, LivingEntity target, Power orb, float each) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        for (int i = -2; i <= 2; i++) {
            double a = Math.toRadians(i * 8.0), rx = dx * Math.cos(a) - dz * Math.sin(a), rz = dx * Math.sin(a) + dz * Math.cos(a);
            RelicProjectile bolt = new RelicProjectile(level, this, new ItemStack(ModContent.orbItem(orb)));
            bolt.setDamage(illusion ? 0.0F : each);
            bolt.setCurse(MobEffects.WEAKNESS);
            bolt.shoot(rx, target.getY(0.5) - bolt.getY(), rz, 1.3F, 1.0F);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(Signature.spark(kind().line), getX(), getY() + 1.4, getZ(), 16, 0.4, 0.3, 0.4, 0.05);
        level.playSound(null, blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.2F, 1.3F);
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    /** True while a duelist's guard is up: the next blow from the front will be turned. */
    public boolean guardUp() {
        return trait() == Trait.DUELIST && guardCooldown <= 0 && !Combat.isStaggered(this);
    }

    /** Puts a duelist's guard up at once, as if it had been fighting a while. */
    public void readyGuard() {
        guardCooldown = 0;
    }

    /** True while a reaver spins up for its whirl, or a slammer crouches or is in the air. */
    public boolean windingSpecial() {
        return whirlWindup > 0 || slamPhase > 0;
    }

    /**
     * A duelist turns a blow: no harm done, a ring of steel, and a moment later it answers - a blow of its own that
     * can be parried or dodged like any other. Then its guard is down for five seconds.
     */
    private void turn(ServerLevel level, ServerPlayer striker) {
        guardCooldown = GUARD_EVERY;
        level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.2F, 1.3F);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4F, 2.0F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.3, getZ(), 14, 0.4, 0.4, 0.4, 0.2);
        com.thefarawaydev.realmsofchaos.ui.Hud.actionBar(striker, com.thefarawaydev.realmsofchaos.ui.Hud.colored(
                Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".sentinel.guarded", kind().displayName()), 0xFFAA00));
        com.thefarawaydev.realmsofchaos.world.Scheduler.after(level.getServer(), COUNTER_DELAY, () -> {
            if (!isAlive() || level() != level || !striker.isAlive() || Combat.isStaggered(this) || distanceToSqr(striker) > 12.25) return;
            lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, striker.getEyePosition());
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 1.2F);
            doHurtTarget(level, striker);
        });
    }

    /** A reaver draws its weapon round: a ring on the floor shows how far the cut will reach. */
    public void beginWhirl(ServerLevel level) {
        whirlWindup = WHIRL_WINDUP;
        getNavigation().stop();
        level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2F, 0.7F);
        if (getTarget() instanceof ServerPlayer watcher) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(watcher, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.PARRY);
    }

    /** The whirl: everyone within its reach is cut and thrown outward - unless they parried it or dodged it. @return how many were hit */
    public int whirl(ServerLevel level) {
        whirlWindup = 0;
        specialCooldown = 120;
        int hit = 0;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4F;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(WHIRL_RADIUS, 1.0, WHIRL_RADIUS), p -> p.isAlive() && Power.canHurt(this, p))) {
            if (p.distanceToSqr(this) > WHIRL_RADIUS * WHIRL_RADIUS + 1.0) continue;
            if (p instanceof ServerPlayer sp && Combat.tryParry(level, sp, this)) continue;
            if (p.hurtServer(level, damageSources().mobAttack(this), damage)) {
                Vec3 away = p.position().subtract(position());
                double d = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
                p.setDeltaMovement(away.x / d * 1.0, 0.35, away.z / d * 1.0);
                p.needsSync = true;
                if (p instanceof ServerPlayer sp) Combat.struck(sp);
                hit++;
            }
        }
        ring(level, ParticleTypes.SWEEP_ATTACK, WHIRL_RADIUS, getY() + 1.0, 14);
        ring(level, Signature.spark(kind().line), WHIRL_RADIUS - 0.6, getY() + 0.8, 20);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6F, 0.6F);
        return hit;
    }

    /** A slammer crouches and marks where its target stands: a ring on the ground, a second's warning. */
    public void beginSlam(ServerLevel level, LivingEntity target) {
        slamPhase = 1;
        slamTimer = SLAM_WINDUP;
        slamMark = target.position();
        getNavigation().stop();
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.9F, 1.3F);
        if (target instanceof ServerPlayer watcher) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(watcher, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.PARRY);
    }

    /** Where a slammer means to come down, while it is crouching or in the air. */
    public Vec3 slamMark() {
        return slamPhase > 0 ? slamMark : null;
    }

    /**
     * The slammer comes down: everyone within reach of where it lands is hurt and thrown into the air - unless they
     * dodged clear or met it with a parry. @return how many were hit
     */
    public int slam(ServerLevel level) {
        slamPhase = 0;
        specialCooldown = 140;
        resetFallDistance();
        int hit = 0;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS), p -> p.isAlive() && Power.canHurt(this, p))) {
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) continue;
            if (p instanceof ServerPlayer sp && Combat.tryParry(level, sp, this)) continue;
            if (p.hurtServer(level, damageSources().mobAttack(this), damage)) {
                double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                p.setDeltaMovement(dx / d * 0.5, 0.7, dz / d * 0.5);
                p.needsSync = true;
                if (p instanceof ServerPlayer sp) Combat.struck(sp);
                hit++;
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 1.0, 0.2, 1.0, 0.0);
        ring(level, Signature.spark(kind().line), SLAM_RADIUS, getY() + 0.2, 24);
        com.thefarawaydev.realmsofchaos.world.Fx.impact(level, position(), 5);
        level.playSound(null, blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.6F, 0.8F);
        return hit;
    }

    private void ring(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, double radius, double y, int points) {
        ring(level, particle, position(), radius, y, points);
    }

    private static void ring(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 at, double radius, double y, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, at.x + Math.cos(a) * radius, y, at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** The reavers' and slammers' attacks, on their own clock. @return true while one is under way: nothing else is done */
    private boolean tendSpecial(ServerLevel level, LivingEntity target) {
        Trait t = trait();
        if (t != Trait.WHIRL && t != Trait.SLAM) return false;
        if (whirlWindup > 0) {
            if (Combat.isStaggered(this)) {
                whirlWindup = 0;
                return true;
            }
            getNavigation().stop();
            setYRot(getYRot() + 36.0F);
            yBodyRot = getYRot();
            if (whirlWindup % 4 == 0) ring(level, ParticleTypes.CRIT, WHIRL_RADIUS, getY() + 0.1, 24);
            if (--whirlWindup == 0) whirl(level);
            return true;
        }
        if (slamPhase == 1) {
            getNavigation().stop();
            if (slamTimer % 5 == 0) ring(level, Signature.spark(kind().line), slamMark, SLAM_RADIUS, slamMark.y + 0.1, 24);
            if (Combat.isStaggered(this)) {
                slamPhase = 0;
                return true;
            }
            if (--slamTimer <= 0) {
                // the leap: high, and far enough to come down on the mark
                double dx = slamMark.x - getX(), dz = slamMark.z - getZ();
                setDeltaMovement(dx / 20.0, 0.85, dz / 20.0);
                needsSync = true;
                slamPhase = 2;
                slamTimer = 0;
                level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            return true;
        }
        if (slamPhase == 2) {
            resetFallDistance();
            if ((++slamTimer > 4 && onGround()) || slamTimer > 40) slam(level);
            return true;
        }
        if (specialCooldown > 0) {
            specialCooldown--;
            return false;
        }
        if (!hasLineOfSight(target)) return false;
        double d2 = distanceToSqr(target);
        if (t == Trait.WHIRL && d2 < 9.0) {
            beginWhirl(level);
            return true;
        }
        if (t == Trait.SLAM && d2 > 16.0 && d2 < 144.0 && onGround()) {
            beginSlam(level, target);
            return true;
        }
        return false;
    }

    /** What a binder's or a silencer's bolt does to whoever it hits, on top of its damage. */
    public void rider(ServerLevel level, LivingEntity hit) {
        Line line = kind().line;
        if (trait() == Trait.BIND) {
            switch (line) {
                case ZEUS -> hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 50, 5));          // chained to the spot
                case POSEIDON -> {
                    // netted and hauled in
                    Vec3 to = position().subtract(hit.position());
                    double d = Math.max(0.01, Math.sqrt(to.x * to.x + to.z * to.z));
                    hit.setDeltaMovement(to.x / d * 1.2, 0.35, to.z / d * 1.2);
                    hit.needsSync = true;
                    hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                }
                case HADES -> {
                    hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                    hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                }
            }
            level.playSound(null, hit.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5F, 0.7F);
        } else if (trait() == Trait.SILENCE) {
            if (hit instanceof Player player) com.thefarawaydev.realmsofchaos.item.GodWeaponItem.silence(player, 20 * 6);
            switch (line) {
                case ZEUS -> {
                    // an oracle-breaker also takes one blessing away
                    for (var effect : new java.util.ArrayList<>(hit.getActiveEffects())) {
                        if (effect.getEffect().value().isBeneficial()) {
                            hit.removeEffect(effect.getEffect());
                            break;
                        }
                    }
                }
                case POSEIDON -> hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 1));
                case HADES -> hit.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NAUSEA, 120, 0));
            }
            level.playSound(null, hit.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0F, 1.6F);
        }
    }

    /** How long a heavy blow is drawn back for, and how often one comes. */
    public static final int HEAVY_WINDUP = 18, HEAVY_EVERY = 110;
    public static final float HEAVY = 1.8F;

    /** Which sentinels strike heavy blows: those that fight hand to hand, once a level is dangerous enough - and every elite. */
    public boolean strikesHeavy() {
        Role role = kind().role;
        return !illusion && role != Role.MYSTIC && role != Role.CASTER && (threat >= 1.0 || isElite());
    }

    public boolean windingUp() {
        return heavyWindup > 0;
    }

    /** @return true while the sentinel is busy with a heavy blow, or reeling from a parry */
    private boolean tendHeavy(ServerLevel level, LivingEntity target) {
        if (!strikesHeavy()) return false;
        if (Combat.isStaggered(this)) {
            heavyWindup = 0;
            return true;
        }
        if (heavyWindup > 0) {
            getNavigation().stop();
            getLookControl().setLookAt(target);
            if (heavyWindup % 4 == 0) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.4, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            if (--heavyWindup == 0) heavyBlow(level, target);
            return true;
        }
        if (heavyCooldown > 0) {
            heavyCooldown--;
            return false;
        }
        if (distanceToSqr(target) > 9.0 || !hasLineOfSight(target)) return false;
        beginHeavy(level, target);
        return true;
    }

    /** Draws back for a heavy blow: it stops, it is loud about it, and anyone watching has most of a second. */
    public void beginHeavy(ServerLevel level, LivingEntity target) {
        heavyWindup = HEAVY_WINDUP;
        heavyCooldown = HEAVY_EVERY + random.nextInt(40);
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 0.8F, 1.4F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.7, getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        if (target instanceof ServerPlayer watcher) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(watcher, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.PARRY);
    }

    /**
     * The heavy blow lands - unless its target has stepped out of reach, or meets it: a heavy blow parried leaves
     * the sentinel reeling twice as long. @return true if it did harm
     */
    public boolean heavyBlow(ServerLevel level, LivingEntity target) {
        heavyWindup = 0;
        if (!target.isAlive() || distanceToSqr(target) > 12.25) return false;
        if (target instanceof ServerPlayer met && Combat.tryParry(level, met, this)) {
            Combat.stagger(level, this, Combat.STAGGER_TICKS * 2);
            return false;
        }
        boolean hit = target.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * HEAVY);
        if (hit) {
            Vec3 away = target.position().subtract(position());
            double d = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
            target.setDeltaMovement(away.x / d * 0.9, 0.35, away.z / d * 0.9);
            target.needsSync = true;
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.6F);
            if (target instanceof ServerPlayer struck) Combat.struck(struck);
        }
        return hit;
    }

    /** A brute's blow throws its target back; a Fury's sets it alight; a thief's empties your hand. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (illusion || Combat.isStaggered(this) || heavyWindup > 0 || windingSpecial()) return false;
        if (target instanceof ServerPlayer met && Combat.tryParry(level, met, this)) return false;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer struck) Combat.struck(struck);
        if (hit && kind() == Kind.FURY) target.igniteForSeconds(3.0F);
        if (hit && trait() == Trait.DISARM && target instanceof Player player && trickCooldown <= 0 && disarm(player)) trickCooldown = 160;
        if (hit && kind().role == Role.BRUTE && target instanceof LivingEntity living) {
            Vec3 away = target.position().subtract(position());
            double d = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
            living.setDeltaMovement(away.x / d * 1.1, 0.4, away.z / d * 1.1);
            living.needsSync = true;
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            com.thefarawaydev.realmsofchaos.world.Fx.impact(level, target.position(), 3);
        }
        return hit;
    }

    private java.util.List<Sentinel> allies(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Sentinel.class, getBoundingBox().inflate(radius), s -> s != this && s.isAlive() && !s.illusion);
    }

    /** A thief's trick: the weapon in the player's hand ends up somewhere in their pack. Nothing is ever lost. */
    public static boolean disarm(Player player) {
        var inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        int hand = inventory.getSelectedSlot(), to = -1;
        for (int i = 9; i < 36 && to < 0; i++) if (inventory.getItem(i).isEmpty()) to = i;
        if (to < 0) to = 9 + player.getRandom().nextInt(27);            // a full pack: it changes places with something
        ItemStack other = inventory.getItem(to);
        inventory.setItem(to, held);
        inventory.setItem(hand, other);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.HOSTILE, 0.8F, 1.4F);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.thefarawaydev.realmsofchaos.ui.Hud.actionBar(sp, com.thefarawaydev.realmsofchaos.ui.Hud.colored(
                    Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".sentinel.disarmed"), 0xFF5555));
        }
        return true;
    }

    /** True if {@code from} lies in front of this sentinel, within sixty degrees of where it faces. */
    public boolean facing(Vec3 from) {
        Vec3 to = from.subtract(position());
        double d = Math.sqrt(to.x * to.x + to.z * to.z);
        if (d < 0.01) return true;
        double yaw = Math.toRadians(yBodyRot);
        return (-Math.sin(yaw) * to.x + Math.cos(yaw) * to.z) / d > 0.5;
    }

    /**
     * Sentinels cover for each other. A shield-bearer takes less than half of a blow from its front, and anyone
     * standing by one is hurt less; a warden takes half of whatever lands on an ally near it.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && !sharing && !illusion) {
            Vec3 from = source.getSourcePosition();
            // a duelist on guard turns a blow struck at it from the front by whoever it is fighting - though not one
            // heavier than all the life left in it
            if (guardUp() && source.getDirectEntity() instanceof ServerPlayer striker && striker == getTarget() && from != null && facing(from) && amount < getHealth()) {
                turn(level, striker);
                return false;
            }
            if (source.getEntity() instanceof ServerPlayer striker) amount = Combat.strike(level, striker, this, source, amount);
            if (trait() == Trait.SHIELD) {
                if (from != null && facing(from)) {
                    amount *= 0.4F;
                    level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                }
            } else {
                for (Sentinel ally : allies(level, 3.5)) {
                    if (ally.trait() == Trait.SHIELD) {
                        amount *= 0.7F;
                        break;
                    }
                }
            }
            if (trait() != Trait.LINK) {
                for (Sentinel ally : allies(level, 8.0)) {
                    if (ally.trait() != Trait.LINK) continue;
                    float share = amount * 0.5F;
                    amount -= share;
                    ally.sharing = true;
                    try {
                        ally.hurtServer(level, source, share);
                    } finally {
                        ally.sharing = false;
                    }
                    Vec3 a = position().add(0, 1.0, 0), b = ally.position().add(0, 1.0, 0);
                    for (int i = 0; i <= 8; i++) {
                        Vec3 at = a.lerp(b, i / 8.0);
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    }
                    break;
                }
            }
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            if (trait() == Trait.MIRAGE && !illusion && !split && getHealth() < getMaxHealth() * 0.7F) split(level);
            if (trait() == Trait.FRENZY && !wild && getHealth() < getMaxHealth() * 0.4F) goWild(level);
        }
        return hurt;
    }

    /** A mirage, hurt, is suddenly three: two of them are not there at all, and it is no longer where it was. */
    public java.util.List<Sentinel> split(ServerLevel level) {
        java.util.List<Sentinel> copies = new java.util.ArrayList<>();
        if (split || illusion) return copies;
        split = true;
        Vec3 here = position();
        for (int i = 0; i < 2; i++) {
            Sentinel copy = ModContent.SENTINEL.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (copy == null) continue;
            copy.illusion = true;
            copy.setUp(kind(), tier, threat, false);
            double a = Math.PI * 2 * (i + 1) / 3 + random.nextDouble();
            copy.snapTo(here.x + Math.cos(a) * 2.0, here.y, here.z + Math.sin(a) * 2.0, getYRot(), 0.0F);
            if (!level.noCollision(copy)) copy.snapTo(here.x, here.y, here.z, getYRot(), 0.0F);
            for (String tag : entityTags()) copy.addTag(tag);
            copy.addTag(RAISED_TAG);
            copy.setTarget(getTarget());
            level.addFreshEntity(copy);
            copies.add(copy);
        }
        // the real one trades places with one of its copies, so nobody knows which is which
        if (!copies.isEmpty()) {
            Sentinel other = copies.get(random.nextInt(copies.size()));
            Vec3 there = other.position();
            other.snapTo(here.x, here.y, here.z, other.getYRot(), 0.0F);
            snapTo(there.x, there.y, there.z, getYRot(), 0.0F);
        }
        level.sendParticles(ParticleTypes.CLOUD, here.x, here.y + 1.0, here.z, 40, 1.2, 0.8, 1.2, 0.02);
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.5F, 1.0F);
        return copies;
    }

    /** Nearly beaten, a berserker stops caring: faster and stronger until it falls. */
    private void goWild(ServerLevel level) {
        wild = true;
        addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.STRENGTH, 20 * 600, 0));
        addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 20 * 600, 0));
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    /** An ally has just fallen near this sentinel: a berserker is roused by it, a reviver marks where. */
    private void witness(Sentinel dead) {
        if (trait() == Trait.FRENZY && frenzy < MAX_FRENZY) {
            frenzy++;
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage(kind().role, threat) * (isElite() ? 1.25 : 1.0) * (1.0 + 0.15 * frenzy));
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28 * (1.0 + 0.05 * frenzy));
            if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
        if (trait() == Trait.REVIVE && fallen == null && dead.trait() != Trait.REVIVE && !dead.entityTags().contains(RAISED_TAG)) {
            fallen = new Fallen(dead.kind(), dead.tier, dead.threat, dead.isElite(), dead.position(), new java.util.HashSet<>(dead.entityTags()));
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level) || illusion) return;
        if (source.getEntity() instanceof ServerPlayer killer) {
            com.thefarawaydev.realmsofchaos.ui.BestiaryMenu.record(killer, kind());
            com.thefarawaydev.realmsofchaos.world.Boons.onKill(killer);
            com.thefarawaydev.realmsofchaos.world.Lesser.fallen(this, killer);
        }
        for (Sentinel ally : allies(level, 12.0)) ally.witness(this);
        if (trait() == Trait.BANNER) {
            // the standard falls: those who fought under it falter
            for (Sentinel ally : allies(level, 9.0)) {
                ally.removeEffect(MobEffects.SPEED);
                ally.removeEffect(MobEffects.STRENGTH);
                ally.removeEffect(MobEffects.RESISTANCE);
                ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            }
        }
        if (trait() == Trait.MIRAGE) {
            for (Sentinel copy : level.getEntitiesOfClass(Sentinel.class, getBoundingBox().inflate(24), s -> s.illusion && s.kind() == kind())) copy.discard();
        }
    }

    /** A bomber's end: everyone near is hurt and marked by its tower, and it is gone. */
    public void burst(ServerLevel level) {
        if (!isAlive()) return;
        float damage = moveDamage() * 2.0F;
        Line line = kind().line;
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(3.5), p -> p.isAlive() && Power.canHurt(this, p))) {
            p.hurtServer(level, damageSources().mobAttack(this), damage);
            switch (line) {
                case ZEUS -> {
                    p.setDeltaMovement(p.getDeltaMovement().x, 0.8, p.getDeltaMovement().z);
                    p.needsSync = true;
                }
                case POSEIDON -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                case HADES -> {
                    p.igniteForSeconds(4.0F);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 60, 0));
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 3, 0.6, 0.4, 0.6, 0.0);
        level.sendParticles(Signature.spark(line), getX(), getY() + 1.0, getZ(), 60, 1.2, 0.8, 1.2, 0.2);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 1.3F);
        com.thefarawaydev.realmsofchaos.world.Fx.impact(level, position(), 5);
        discard();
    }

    /** A dancer's trick: it changes places with the worst-hurt ally near it, and that ally starts to mend. */
    public boolean swap(ServerLevel level) {
        Sentinel worst = null;
        for (Sentinel ally : allies(level, 12.0)) {
            float share = ally.getHealth() / ally.getMaxHealth();
            if (share < 0.6F && (worst == null || share < worst.getHealth() / worst.getMaxHealth())) worst = ally;
        }
        if (worst == null) return false;
        Vec3 mine = position(), theirs = worst.position();
        snapTo(theirs.x, theirs.y, theirs.z, getYRot(), 0.0F);
        worst.snapTo(mine.x, mine.y, mine.z, worst.getYRot(), 0.0F);
        worst.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.REGENERATION, 80, 1));
        if (worst.getTarget() != null) setTarget(worst.getTarget());
        for (Vec3 at : new Vec3[]{mine, theirs}) level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.3);
        level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.4F);
        return true;
    }

    /** What a trait does on its own clock, every tick the sentinel has a target. */
    private void tickTrait(ServerLevel level, LivingEntity target) {
        if (trickCooldown > 0) trickCooldown--;
        switch (trait()) {
            case DUELIST -> {
                // its guard, when up, glints on its blade
                if (guardCooldown > 0) guardCooldown--;
                else if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.2, getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            }
            case BOMB -> {
                if (fuse < 0 && distanceToSqr(target) < 6.25) {
                    fuse = FUSE_TICKS;
                    setGlowingTag(true);
                    level.playSound(null, blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.5F, 1.2F);
                } else if (fuse >= 0) {
                    getNavigation().stop();
                    if (fuse % 5 == 0) level.sendParticles(Signature.spark(kind().line), getX(), getY() + 1.0, getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                    if (--fuse < 0) burst(level);
                }
            }
            case SWAP -> {
                if (trickCooldown <= 0) trickCooldown = swap(level) ? 120 : 20;
            }
            case TRAP -> {
                // now and then it sows one where it stands, to cover its own retreat
                if (trickCooldown <= 0 && distanceToSqr(target) < 36.0) {
                    com.thefarawaydev.realmsofchaos.world.Hazards.add(level, position(), 2.5, 160, kind().line, moveDamage() * 0.5F);
                    trickCooldown = 200;
                }
            }
            default -> { }
        }
    }

    /**
     * A mystic's rite, performed every few seconds while it has a target: the Storm Augur quickens and emboldens
     * the sentinels near it, the Tide Priest mends them, the Necromancer raises a Shade (two at most).
     * @return true if the rite did anything
     */
    public boolean rite(ServerLevel level) {
        if (kind().role != Role.MYSTIC) return false;
        java.util.List<Sentinel> near = allies(level, 9.0);
        boolean did = false;
        switch (trait()) {
            case BANNER -> {
                // every tower's standard gives what that tower fights with: speed, a thick hide, or a heavy hand
                for (Sentinel ally : near) {
                    ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(switch (kind().line) {
                        case ZEUS -> MobEffects.SPEED;
                        case POSEIDON -> MobEffects.RESISTANCE;
                        case HADES -> MobEffects.STRENGTH;
                    }, 160, kind().line == Line.ZEUS ? 1 : 0));
                    level.sendParticles(Signature.spark(kind().line), ally.getX(), ally.getY() + 1.2, ally.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
                    did = true;
                }
            }
            case HORN -> {
                // (two rites of rest after each call: a horn that never stops is no alarm)
                if (riteCharge > 0) riteCharge--;
                else if (soundHorn(level) > 0) {
                    did = true;
                    riteCharge = 2;
                }
            }
            case REVIVE -> {
                if (fallen != null && revives < (tier >= 3 ? 2 : 1)) {
                    Sentinel back = ModContent.SENTINEL.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                    if (back != null) {
                        back.setUp(fallen.kind(), fallen.tier(), fallen.threat(), fallen.elite());
                        back.setHealth(back.getMaxHealth() * 0.4F);
                        back.snapTo(fallen.at().x, fallen.at().y, fallen.at().z, getYRot(), 0.0F);
                        for (String tag : fallen.tags()) back.addTag(tag);
                        back.addTag(RAISED_TAG);
                        back.setPersistenceRequired();
                        back.setTarget(getTarget());
                        level.addFreshEntity(back);
                        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, fallen.at().x, fallen.at().y + 1.0, fallen.at().z, 40, 0.4, 0.8, 0.4, 0.3);
                        revives++;
                        fallen = null;
                        did = true;
                    }
                }
            }
            default -> { }
        }
        switch (kind()) {
            case STORM_AUGUR -> {
                for (Sentinel ally : near) {
                    ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 140, 0));
                    ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.STRENGTH, 140, 0));
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ally.getX(), ally.getY() + 1.2, ally.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
                    did = true;
                }
            }
            case TIDE_PRIEST -> {
                for (Sentinel ally : near) {
                    if (ally.getHealth() >= ally.getMaxHealth()) continue;
                    ally.heal(5.0F + (float) threat * 0.4F);
                    level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.2, ally.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
                    did = true;
                }
            }
            case NECROMANCER -> {
                long raised = near.stream().filter(s -> s.entityTags().contains(RAISED_TAG)).count();
                if (raised < 2) {
                    Sentinel shade = ModContent.SENTINEL.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                    if (shade != null) {
                        shade.setUp(Kind.SHADE, tier, threat * 0.5, false);
                        shade.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                        // what it raises belongs to the same room, so the room is not clear until they fall too
                        for (String tag : entityTags()) shade.addTag(tag);
                        shade.addTag(RAISED_TAG);
                        shade.setPersistenceRequired();
                        shade.setTarget(getTarget());
                        level.addFreshEntity(shade);
                        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 20, 0.4, 0.5, 0.4, 0.05);
                        did = true;
                    }
                }
            }
            default -> { }
        }
        if (did) level.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.3F);
        return did;
    }

    private int riteCharge;

    /**
     * The alarm. Every sentinel within earshot turns on whoever the herald is fighting and runs; in a passage the
     * rooms next door wake as well and come too. @return how many answered.
     */
    public int soundHorn(ServerLevel level) {
        LivingEntity target = getTarget();
        var plan = com.thefarawaydev.realmsofchaos.world.Passages.planAt(level, blockPosition());
        if (plan != null) {
            var cell = plan.cellAt(blockPosition());
            if (cell != null) {
                for (var next : plan.neighbours(cell)) {
                    if (plan.connected(cell, next)) com.thefarawaydev.realmsofchaos.world.Passages.wake(level, plan, next);
                }
            }
        }
        int answered = 0;
        for (Sentinel ally : allies(level, 28.0)) {
            if (target != null) ally.setTarget(target);
            ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 120, 0));
            answered++;
        }
        level.playSound(null, blockPosition(), kind().line == Line.HADES ? SoundEvents.BELL_BLOCK : SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, kind().line == Line.POSEIDON ? 0.7F : 1.0F);
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(28.0), Player::isAlive)) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.thefarawaydev.realmsofchaos.ui.Hud.actionBar(sp, com.thefarawaydev.realmsofchaos.ui.Hud.colored(
                        Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".sentinel.horn", kind().displayName()), 0xFFAA00));
            }
        }
        return answered;
    }

    /** The move a Keeper, a Shade or a charger makes now and then, or null for kinds that have none. */
    public Signature move() {
        if (trait() == Trait.CHARGE) return Signature.CHARGE;
        return switch (kind()) {
            case KEEPER_SKY -> Signature.STORM;
            case KEEPER_TIDES -> Signature.VORTEX;
            case KEEPER_SHADES -> Signature.CURSE;
            case SHADE -> Signature.BLINK;
            default -> null;
        };
    }

    public float moveDamage() {
        return 2.0F + tier + (float) threat * 0.15F;
    }

    /** Makes the sentinel's move at once. @return false if it has none or could not make it. */
    public boolean castMove(ServerLevel level) {
        Signature move = move();
        return move != null && move.perform(level, this, patron(), moveDamage());
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (illusion && ++life > ILLUSION_TICKS) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 10, 0.3, 0.6, 0.3, 0.02);
            discard();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        tickTrait(level, target);
        if (!isAlive() || isRemoved()) return;
        if (tendSpecial(level, target)) return;
        if (tendHeavy(level, target)) return;
        if (kind().role == Role.MYSTIC) {
            if (--riteCooldown <= 0) riteCooldown = rite(level) ? RITE_EVERY : 30;
            return;
        }
        if (move() == null) return;
        if (moveCooldown > 0) {
            moveCooldown--;
            if (moveCooldown == WARN_TICKS && (kind().role == Role.KEEPER || trait() == Trait.CHARGE)) {
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
        if (!killedByPlayer || illusion) return;
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer killer) {
            com.thefarawaydev.realmsofchaos.world.Bounties.count(killer, com.thefarawaydev.realmsofchaos.world.Bounties.Kind.SENTINELS, 1);
        }
        // a Keeper always gives up a piece of this tier's forge material; the rest do now and then
        if (kind().role == Role.KEEPER || isElite() || random.nextFloat() < 0.15F) {
            spawnAtLocation(level, new ItemStack(ModContent.materialItem(kind().line, tier)));
        }
    }

    @Override
    protected Component getTypeName() {
        return isElite() ? Component.translatable("entity." + RealmsOfChaos.MOD_ID + ".sentinel.elite", kind().displayName()) : kind().displayName();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Kind", kind().ordinal());
        output.putInt("Tier", tier);
        output.putDouble("Threat", threat);
        output.putBoolean("Elite", isElite());
        output.putBoolean("Illusion", illusion);
        output.putInt("Life", life);
        output.putInt("Frenzy", frenzy);
        output.putBoolean("Split", split);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_KIND, input.getIntOr("Kind", 0));
        this.tier = Math.max(1, Math.min(3, input.getIntOr("Tier", 1)));
        this.threat = Math.max(0.0, input.getDoubleOr("Threat", 0.0));
        this.entityData.set(DATA_ELITE, input.getBooleanOr("Elite", false));
        this.illusion = input.getBooleanOr("Illusion", false);
        this.life = input.getIntOr("Life", 0);
        this.frenzy = Math.max(0, Math.min(MAX_FRENZY, input.getIntOr("Frenzy", 0)));
        this.split = input.getBooleanOr("Split", false);
        configure(false);
    }
}
