package com.thefarawaydev.realmsofchaos.god;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A parsed "effect:seconds:amplifier" string from the generated roster. */
public record EffectSpec(Holder<MobEffect> effect, int seconds, int amplifier) {

    public static final EffectSpec NONE = null;

    public static EffectSpec parse(String spec) {
        if (spec == null || spec.isEmpty()) return null;
        String[] p = spec.split(":");
        Holder<MobEffect> holder = lookup(p[0]);
        if (holder == null) {
            throw new IllegalArgumentException("Unknown effect in god roster: " + spec);
        }
        return new EffectSpec(holder, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    /** Tips an arrow with a god's target effect, so bows carry their god's curse like every other relic. */
    public static void addTo(net.minecraft.world.entity.projectile.arrow.Arrow arrow, God god) {
        EffectSpec spec = god.targetEffect();
        if (spec != null) arrow.addEffect(spec.instance(1.0));
    }

    public MobEffectInstance instance(double durationScale) {
        return new MobEffectInstance(effect, (int) (seconds * 20 * durationScale), amplifier);
    }

    private static Holder<MobEffect> lookup(String id) {
        return switch (id) {
            case "slowness" -> MobEffects.SLOWNESS;
            case "weakness" -> MobEffects.WEAKNESS;
            case "glowing" -> MobEffects.GLOWING;
            case "nausea" -> MobEffects.NAUSEA;
            case "darkness" -> MobEffects.DARKNESS;
            case "blindness" -> MobEffects.BLINDNESS;
            case "poison" -> MobEffects.POISON;
            case "wither" -> MobEffects.WITHER;
            case "levitation" -> MobEffects.LEVITATION;
            case "mining_fatigue" -> MobEffects.MINING_FATIGUE;
            case "speed" -> MobEffects.SPEED;
            case "strength" -> MobEffects.STRENGTH;
            case "regeneration" -> MobEffects.REGENERATION;
            case "resistance" -> MobEffects.RESISTANCE;
            case "absorption" -> MobEffects.ABSORPTION;
            case "jump_boost" -> MobEffects.JUMP_BOOST;
            case "water_breathing" -> MobEffects.WATER_BREATHING;
            default -> null;
        };
    }
}
