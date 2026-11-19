package com.thefarawaydev.realmsofchaos.god;

import java.util.List;

/** Lookup helpers over the generated roster. */
public final class Gods {
    public static final List<God> ALL = GodRoster.ALL;

    private Gods() {}

    public static God byIndex(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static God byId(String id) {
        for (God g : ALL) {
            if (g.id().equals(id)) return g;
        }
        return null;
    }

    public static God get(Line line, int rank) {
        for (God g : ALL) {
            if (g.line() == line && g.rank() == rank) return g;
        }
        throw new IllegalArgumentException(line + " rank " + rank);
    }

    public static List<God> ofLine(Line line) {
        return ALL.stream().filter(g -> g.line() == line).toList();
    }
}
