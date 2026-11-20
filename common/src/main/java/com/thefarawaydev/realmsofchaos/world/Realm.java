package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The four dimensions of the Greek mythology path: the hub plus one zone per god line. */
public enum Realm {
    HUB("hub", null, new Vec3(0.5, 65, 0.5), 0f),
    OLYMPUS("olympus", Line.ZEUS, new Vec3(0.5, 65, 25.5), 180f),
    ABYSS("abyss", Line.POSEIDON, new Vec3(0.5, 65, 25.5), 180f),
    UNDERWORLD("underworld", Line.HADES, new Vec3(0.5, 65, 25.5), 180f);

    private final String id;
    private final Line line;
    private final Vec3 spawn;
    private final float yaw;
    private final ResourceKey<Level> key;

    Realm(String id, Line line, Vec3 spawn, float yaw) {
        this.id = id;
        this.line = line;
        this.spawn = spawn;
        this.yaw = yaw;
        this.key = ResourceKey.create(Registries.DIMENSION, RealmsOfChaos.id(id));
    }

    public String id() { return id; }
    public Line line() { return line; }
    public Vec3 spawn() { return spawn; }
    public float yaw() { return yaw; }
    public ResourceKey<Level> key() { return key; }

    public static Realm ofLine(Line line) {
        for (Realm r : values()) {
            if (r.line == line) return r;
        }
        throw new IllegalArgumentException(line.name());
    }

    /** Where a player appears in the hub when coming back from {@code from}: in front of that god's gate. */
    public static Vec3 hubArrival(Realm from) {
        return switch (from.line == null ? Line.ZEUS : from.line) {
            case ZEUS -> new Vec3(0.5, 65, -13.5);
            case POSEIDON -> new Vec3(14.5, 65, 0.5);
            case HADES -> new Vec3(-13.5, 65, 0.5);
        };
    }

    public static float hubArrivalYaw(Realm from) {
        return switch (from.line == null ? Line.ZEUS : from.line) {
            case ZEUS -> 0f;
            case POSEIDON -> 90f;
            case HADES -> -90f;
        };
    }

    /** Where bosses of this realm's arena spawn and are leashed to (the arena centre). */
    public Vec3 arenaCentre() {
        return new Vec3(0.5, 65, 0.5);
    }

    public static Realm of(ResourceKey<Level> dimension) {
        for (Realm r : values()) {
            if (r.key.equals(dimension)) return r;
        }
        return null;
    }
}
