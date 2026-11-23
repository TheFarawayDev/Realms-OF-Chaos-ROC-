package com.thefarawaydev.realmsofchaos;

import net.minecraft.resources.Identifier;

/**
 * Realms of Chaos - a mythology boss-rush mod by TheFarawayDev.
 * Shared (loader independent) constants.
 */
public final class RealmsOfChaos {
    public static final String MOD_ID = "realms_of_chaos";
    public static final String MOD_NAME = "Realms of Chaos";
    public static final String AUTHOR = "TheFarawayDev";

    /**
     * Which build of the mod this is, as its loader knows it: "1.2.0-alpha.57" for build 57 of the alpha, or just
     * the version when run from source. Every release has its own, so two copies of the mod can be told apart.
     */
    private static String version = "dev";

    private RealmsOfChaos() {}

    public static String version() {
        return version;
    }

    /** Set once by each loader as the mod starts. */
    public static void version(String found) {
        if (found != null && !found.isBlank()) version = found;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
