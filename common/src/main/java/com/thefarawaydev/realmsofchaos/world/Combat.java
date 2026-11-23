package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.HudArt;
import com.thefarawaydev.realmsofchaos.ui.Options;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fighting the creatures of the realms is more than trading blows.
 *
 * <p><b>Parry.</b> Tap sneak - or raise a shield - just as a blow would land and it is turned aside: no harm done,
 * the attacker staggers, and the next blow struck within three seconds is a riposte. A bolt parried goes back to
 * whoever loosed it. The moment is short, and a tap that parries nothing has to be waited out before the next.
 *
 * <p><b>Momentum.</b> Every blow landed without being hit builds it, to five; each point adds a little to every
 * blow. Being struck loses it all, and it fades when the fighting stops.
 *
 * <p><b>Riposte, exposure, plunge.</b> A riposte and a blow on a staggered creature each count half as much again;
 * a blow struck while falling shakes everything round the one it lands on.
 *
 * <p>All of it is worked out on the server from what a player is already doing: it needs no key of its own.
 */
public final class Combat {
    /** How long before a blow a tap still parries it, and how long a tap that parried nothing has to be waited out. */
    public static final int PARRY_WINDOW = 7, PARRY_RECOVERY = 16;
    public static final int RIPOSTE_TICKS = 60, STAGGER_TICKS = 40, MOMENTUM_FADE = 120, PLUNGE_REST = 60;
    public static final int MAX_MOMENTUM = 5;
    public static final float MOMENTUM_STEP = 0.04F, RIPOSTE = 1.5F, EXPOSED = 1.5F, PLUNGE = 1.25F;
    /** A fall of at least this far makes a blow a plunge. */
    public static final double PLUNGE_FALL = 2.0;

    private static final class State {
        boolean wasSneaking, wasGuarding;
        long pressedAt = Long.MIN_VALUE / 2, riposteUntil, lastBlow, plungeReady;
        int momentum, shown = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Long> STAGGERED = new HashMap<>();
    private static boolean shocking;

    private Combat() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static long now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    /** How long before a blow this player's tap still parries it: a Keen boon lengthens it. */
    public static int window(ServerPlayer player) {
        return PARRY_WINDOW + 3 * Boons.count(player, Boons.Boon.KEEN);
    }

    /** Every tick: who has just tapped sneak or raised a shield, and whose momentum has run out. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) watch(player, now);
        if (now % 100 == 0) STAGGERED.values().removeIf(until -> until < now);
    }

    /** One player, one tick. */
    public static void watch(ServerPlayer player, long now) {
        State st = STATES.get(player.getUUID());
        boolean sneaking = player.isShiftKeyDown(), guarding = player.isUsingItem() && player.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
        if (st == null) {
            if (!sneaking && !guarding) return;
            st = state(player);
        }
        if ((sneaking && !st.wasSneaking) || (guarding && !st.wasGuarding)) press(player, now);
        st.wasSneaking = sneaking;
        st.wasGuarding = guarding;
        if (st.momentum > 0 && now - st.lastBlow > MOMENTUM_FADE) {
            st.momentum = 0;
            show(player, st, now);
        }
        if (st.riposteUntil != 0 && now > st.riposteUntil) {
            st.riposteUntil = 0;
            show(player, st, now);
        }
    }

    /** The tap itself. @return true if it opened a moment in which a blow would be parried */
    public static boolean press(ServerPlayer player, long now) {
        State st = state(player);
        if (now - st.pressedAt < PARRY_RECOVERY) return false;
        st.pressedAt = now;
        return true;
    }

    public static boolean ready(ServerPlayer player) {
        long since = now(player) - state(player).pressedAt;
        return since >= 0 && since <= window(player);
    }

    /**
     * A blow is about to land on a player. If they tapped in time it is turned aside: the attacker staggers and
     * the player has a riposte coming. @return true if the blow should do nothing
     */
    public static boolean tryParry(ServerLevel level, ServerPlayer player, LivingEntity attacker) {
        if (!ready(player) || player.isCreative() || player.isSpectator()) return false;
        State st = state(player);
        long now = now(player);
        st.pressedAt = Long.MIN_VALUE / 2;                      // one tap, one blow
        st.riposteUntil = now + RIPOSTE_TICKS;
        st.momentum = Math.min(MAX_MOMENTUM, st.momentum + 1);
        st.lastBlow = now;
        if (attacker != null) stagger(level, attacker, STAGGER_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5F, 1.8F);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.1, player.getZ(), 18, 0.4, 0.4, 0.4, 0.3);
        ProgressData.get(level.getServer()).addCounter("parries/" + player.getUUID(), 1);
        show(player, st, now);
        return true;
    }

    /** Leaves a creature reeling: slowed, weakened, and open - a blow on it counts half as much again. */
    public static void stagger(ServerLevel level, LivingEntity creature, int ticks) {
        STAGGERED.put(creature.getUUID(), (long) level.getServer().getTickCount() + ticks);
        creature.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 5, true, false, false));
        creature.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2, true, false, false));
        if (creature instanceof Mob mob) mob.getNavigation().stop();
        level.sendParticles(ParticleTypes.CRIT, creature.getX(), creature.getY() + creature.getBbHeight() + 0.3, creature.getZ(), 12, 0.3, 0.1, 0.3, 0.2);
    }

    public static boolean isStaggered(LivingEntity creature) {
        Long until = STAGGERED.get(creature.getUUID());
        return until != null && creature.level().getServer() != null && creature.level().getServer().getTickCount() < until;
    }

    /** A player has been struck: their momentum is gone. */
    public static void struck(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        if (st == null || st.momentum == 0) return;
        st.momentum = 0;
        show(player, st, now(player));
    }

    public static int momentum(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st == null ? 0 : st.momentum;
    }

    public static boolean riposteReady(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st != null && st.riposteUntil != 0 && now(player) <= st.riposteUntil;
    }

    /**
     * A player's own blow, hand to hand, on one of the realms' creatures: what it comes to once momentum, a riposte,
     * the creature's stagger and a plunge have had their say.
     */
    public static float strike(ServerLevel level, ServerPlayer player, LivingEntity victim, DamageSource source, float amount) {
        if (shocking || source.getDirectEntity() != player || amount <= 0.0F) return amount;
        State st = state(player);
        long now = now(player);
        float out = amount * (1.0F + MOMENTUM_STEP * st.momentum);
        if (isStaggered(victim)) out *= EXPOSED;
        if (st.riposteUntil != 0 && now <= st.riposteUntil) {
            out *= RIPOSTE;
            st.riposteUntil = 0;
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY() + 1.0, victim.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
        if (player.fallDistance >= PLUNGE_FALL && !player.onGround() && now >= st.plungeReady) {
            st.plungeReady = now + PLUNGE_REST;
            out *= PLUNGE;
            plunge(level, player, victim, amount * 0.5F);
        }
        st.momentum = Math.min(MAX_MOMENTUM, st.momentum + 1);
        st.lastBlow = now;
        show(player, st, now);
        return out;
    }

    /** A blow from above shakes everything round the creature it lands on. */
    private static void plunge(ServerLevel level, ServerPlayer player, LivingEntity victim, float damage) {
        shocking = true;
        try {
            for (Mob near : level.getEntitiesOfClass(Mob.class, new AABB(victim.blockPosition()).inflate(3.0, 1.5, 3.0), e -> e.isAlive() && e != victim && e.getTarget() == player)) {
                near.hurtServer(level, level.damageSources().playerAttack(player), damage);
                Vec3 away = near.position().subtract(victim.position());
                double d = Math.max(0.3, Math.sqrt(away.x * away.x + away.z * away.z));
                near.setDeltaMovement(away.x / d * 0.6, 0.35, away.z / d * 0.6);
                near.needsSync = true;
            }
        } finally {
            shocking = false;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 0.2, victim.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, victim.getX(), victim.getY() + 0.2, victim.getZ(), 24, 1.2, 0.1, 1.2, 0.05);
        level.playSound(null, victim.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The meter above the hotbar: momentum, whether a parry is to hand, a riposte waiting. Sent only when it changes. */
    private static void show(ServerPlayer player, State st, long now) {
        boolean riposte = st.riposteUntil != 0 && now <= st.riposteUntil;
        int look = st.momentum | (riposte ? 16 : 0);
        if (look == st.shown) return;
        st.shown = look;
        if (!Options.on(player, Options.Option.COMBAT)) return;
        if (Options.on(player, Options.Option.ART)) {
            // the player's own HUD draws it; with nothing to show it is taken away
            if (st.momentum == 0 && !riposte) com.thefarawaydev.realmsofchaos.ui.HudFeed.clear(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.METER);
            else com.thefarawaydev.realmsofchaos.ui.HudFeed.set(player, com.thefarawaydev.realmsofchaos.ui.HudFeed.METER, st.momentum | (now - st.pressedAt >= PARRY_RECOVERY ? 16 : 0) | (riposte ? 32 : 0), null);
        } else {
            Hud.actionBar(player, HudArt.plainMeter(st.momentum, riposte));
        }
    }

    /** Between tests, and when a player leaves. */
    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static void forgetAll() {
        STATES.clear();
        STAGGERED.clear();
    }
}
