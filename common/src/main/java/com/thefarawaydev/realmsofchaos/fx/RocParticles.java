package com.thefarawaydev.realmsofchaos.fx;

import com.thefarawaydev.realmsofchaos.ModContent;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The mod's own particles. Each is a plain particle type drawn from its own sprites (textures/particle/&lt;id&gt;_n.png);
 * how it moves - rising, falling, drifting, glowing - is the client's {@code RocQuadParticle}.
 */
public enum RocParticles {
    /** Zeus: a crackle of storm-light. */
    STORM_SPARK(Motion.DRIFT, true, 6, 12),
    /** Poseidon: a bead of sea-spray, falling. */
    SEA_SPRAY(Motion.FALL, false, 14, 22),
    /** Hades: a wisp of soul-fire, rising. */
    SOUL_WISP(Motion.RISE, true, 16, 26),
    /** The towers' fires, rising out of their braziers. */
    FLAME_STORM(Motion.RISE, true, 8, 14), FLAME_SEA(Motion.RISE, true, 8, 14), FLAME_SHADE(Motion.RISE, true, 8, 14),
    /** A four-pointed twinkle: treasure, solved puzzles, light. */
    GLINT(Motion.FLOAT, true, 14, 22),
    /** A glyph of the old script, rising and fading: rune stones, glyph tiles, seals. */
    RUNE(Motion.RISE, true, 18, 28),
    /** A flash where a blow lands. */
    STRIKE(Motion.STILL, true, 4, 6),
    /** A puff of stone dust. */
    DUST(Motion.DRIFT, false, 12, 20),
    /** A flake of frost, settling. */
    FROST(Motion.FALL, false, 20, 34),
    /** A mote of chaos, swirling. */
    CHAOS_MOTE(Motion.SWIRL, true, 16, 28),
    /** A drop of water from a sprinkler. */
    WATER_DROP(Motion.FALL, false, 18, 26),
    /** A curl of grey smoke over a fire, a forge or a spent trap. */
    SMOKE(Motion.RISE, false, 16, 26),
    /** A coil of violet-black gloom: blight, darkness, the shade. */
    GLOOM(Motion.SWIRL, true, 18, 30),
    /** A spitting ember, falling from a forge or a blast of fire. */
    EMBER(Motion.FALL, true, 10, 18);

    /** How a particle of this kind moves. */
    public enum Motion { RISE, FALL, FLOAT, DRIFT, SWIRL, STILL }

    public final Motion motion;
    public final boolean glows;
    public final int minLife, maxLife;

    RocParticles(Motion motion, boolean glows, int minLife, int maxLife) {
        this.motion = motion;
        this.glows = glows;
        this.minLife = minLife;
        this.maxLife = maxLife;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The registered type: the thing to send. */
    public SimpleParticleType type() {
        return ModContent.PARTICLES.get(id()).get();
    }

    /** A tower's fire. */
    public static RocParticles flame(int tower) {
        return tower == 0 ? FLAME_STORM : tower == 1 ? FLAME_SEA : FLAME_SHADE;
    }

    /** The registered type of a particle kind (the common code only ever makes these). */
    public static final class Type extends SimpleParticleType {
        public Type() {
            super(false);
        }
    }
}
