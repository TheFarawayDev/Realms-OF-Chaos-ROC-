package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.fx.RocParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** One of the mod's particles: it plays through its sprites over its life, and moves the way its kind does. */
public class RocQuadParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final RocParticles kind;
    private final double spin;
    /** The size it starts at, and how fast it turns (radians a tick). */
    private final float baseSize, turn;

    protected RocQuadParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, RocParticles kind, RandomSource random) {
        super(level, x, y, z, 0, 0, 0, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.lifetime = kind.minLife + random.nextInt(Math.max(1, kind.maxLife - kind.minLife + 1));
        this.quadSize = 0.1F + random.nextFloat() * 0.06F;
        // each one a little different in tone, so a cloud of them is not one flat colour
        float tone = 0.88F + random.nextFloat() * 0.12F;
        setColor(tone, tone, Math.min(1.0F, tone + 0.04F));
        boolean spins = kind == RocParticles.STORM_SPARK || kind == RocParticles.GLINT || kind == RocParticles.STRIKE || kind == RocParticles.CHAOS_MOTE
                || kind == RocParticles.FROST || kind == RocParticles.DUST || kind == RocParticles.SMOKE || kind == RocParticles.GLOOM;
        this.turn = spins ? (random.nextFloat() - 0.5F) * 0.22F : 0.0F;
        this.roll = random.nextFloat() * (float) Math.PI * 2;
        this.oRoll = this.roll;
        this.hasPhysics = kind.motion == RocParticles.Motion.FALL;
        this.spin = random.nextDouble() * Math.PI * 2;
        // what it was sent with, plus a little of its kind's own motion
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        switch (kind.motion) {
            case RISE -> {
                this.yd += 0.02 + random.nextDouble() * 0.02;
                this.friction = 0.94F;
            }
            case FALL -> {
                this.gravity = 0.6F;
                this.friction = 0.98F;
            }
            case FLOAT -> {
                this.yd += 0.005;
                this.friction = 0.9F;
            }
            case DRIFT -> this.friction = 0.88F;
            case SWIRL -> this.friction = 0.92F;
            case STILL -> {
                this.xd = this.yd = this.zd = 0;
                this.quadSize = 0.16F;
            }
        }
        this.baseSize = this.quadSize;
        setSpriteFromAge(sprites);
    }

    /**
     * How big it is through its life: smoke and dust swell as they spread, fire and sparks shrink as they burn out,
     * a glint swells and fades, the rest hold their size.
     */
    @Override
    public float getQuadSize(float partialTick) {
        float t = Math.min(1.0F, (age + partialTick) / Math.max(1, lifetime));
        float k = switch (kind) {
            case DUST, SMOKE, GLOOM -> 0.7F + t * 0.9F;
            case FLAME_STORM, FLAME_SEA, FLAME_SHADE, SOUL_WISP, EMBER, STORM_SPARK -> 1.0F - t * 0.55F;
            case GLINT, STRIKE -> 0.6F + (float) Math.sin(t * Math.PI) * 0.7F;
            default -> 1.0F;
        };
        return baseSize * k;
    }

    @Override
    public void tick() {
        oRoll = roll;
        roll += turn;
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        if (kind.motion == RocParticles.Motion.SWIRL) {
            double a = spin + age * 0.35;
            xd += Math.cos(a) * 0.006;
            zd += Math.sin(a) * 0.006;
        }
        // the last third of its life it fades
        float left = 1.0F - (float) age / lifetime;
        float flicker = kind.glows ? 0.85F + 0.15F * (float) Math.sin(age * 1.7 + spin * 3) : 1.0F;
        setAlpha(Math.min(1.0F, left * 3.0F) * flicker);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return kind.glows ? 0xF000F0 : super.getLightCoords(partialTick);
    }

    public static ParticleProvider<SimpleParticleType> provider(RocParticles kind, SpriteSet sprites) {
        return (type, level, x, y, z, xd, yd, zd, random) -> new RocQuadParticle(level, x, y, z, xd, yd, zd, sprites, kind, random);
    }
}
