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

    private RealmsOfChaos() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
