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

    // Test seam: the developer self-test runs on a server without the realm dimensions, so it can make one
    // ordinary dimension stand in for a realm. Never set in normal play.
    private static ResourceKey<Level> aliasDimension;
    private static Realm aliasRealm;

    public static void testAlias(ResourceKey<Level> dimension, Realm realm) {
        aliasDimension = dimension;
        aliasRealm = realm;
    }

    /** The loaded level of this realm, or null if this world does not have it. */
    public net.minecraft.server.level.ServerLevel level(net.minecraft.server.MinecraftServer server) {
        if (aliasRealm == this && aliasDimension != null) return server.getLevel(aliasDimension);
        return server.getLevel(key);
    }

    public static Realm of(ResourceKey<Level> dimension) {
        if (aliasDimension != null) return aliasDimension.equals(dimension) ? aliasRealm : null;
        for (Realm r : values()) {
            if (r.key.equals(dimension)) return r;
        }
        return null;
    }
}
