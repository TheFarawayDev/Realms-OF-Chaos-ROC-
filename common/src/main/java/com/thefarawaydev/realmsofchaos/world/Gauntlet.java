package com.thefarawaydev.realmsofchaos.world;

import java.util.Locale;

/**
 * What a trap hall does, and when: the patterns its floor runs through, with nothing of the game in it - only
 * arithmetic on where a tile is and what the clock says. A hall is a {@link Variant}: something that hurts
 * ({@link Hazard}), a way it moves across the floor ({@link Pattern}), and a size of room.
 *
 * <p>A pattern is a number of turns taken one after another, over and over. Each turn some tiles are warned of,
 * then live, then let go. Nothing here remembers anything: what a tile is doing is worked out from the clock, so a
 * hall nobody is in costs nothing, and the same hall is the same for everyone in it.
 *
 * <p>Every variant can be crossed by somebody who watches it: {@link #crossable} proves it, and the self-test asks.
 */
public final class Gauntlet {
    public static final int SAFE = 0, WARN = 1, LIVE = 2;
    /** Sides of a room, in the order doors are given: east, south, west, north. */
    public static final int[][] SIDES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    /** How many ticks somebody walking takes to cross one tile, as {@link #crossable} counts it: slower than a walk really is. */
    public static final int STEP_TICKS = 6;

    private Gauntlet() {}

    /** What a hall hurts with. */
    public enum Hazard { SPIKE, FLAME, SOULFIRE, FROST, VENOM, SHOCK, WITHER, GEYSER, GALE, LEVITATE, BLIGHT, CRUSH, SNARE, DRAIN }

    /** How far a hall reaches from its middle. */
    public enum Size {
        SMALL(3), NORMAL(4), WIDE(5);

        public final int half;

        Size(int half) {
            this.half = half;
        }
    }

    /** The way a hall's hazard moves across its floor. */
    public enum Pattern {
        /** A line that crosses the room, row after row. */
        ROWS,
        /** The same, from side to side. */
        COLUMNS,
        /** A line that crosses the room and comes back. */
        BOUNCE,
        /** Two lines that start at opposite walls and meet in the middle. */
        PINCER,
        /** A ring that grows from the middle to the walls. */
        RIPPLE,
        /** A ring that closes from the walls on the middle. */
        IMPLODE,
        /** The floor is taken from the walls inwards, until only the middle is left - then given back. */
        FLOOD,
        /** Every other tile, then the rest. */
        CHECKER,
        /** Every other row, then the rest. */
        STRIPES,
        /** Rows, then columns, then the other rows, then the other columns. */
        WEAVE,
        /** A quarter of the room at a time, round and round. */
        QUARTERS,
        /** A line that crosses the room corner to corner. */
        DIAGONAL,
        /** A lattice of single tiles that creeps across the floor. */
        SERPENT,
        /** The cross through the middle, then the diagonals, then everything else. */
        CROSSFIRE,
        /** An arm that turns about the middle. */
        SPIRAL,
        /** Two arms, opposite one another. */
        TWIN_SPIRAL,
        /** Every tile once a round - but which quarter of the round is anybody's guess, and changes each round. */
        SCATTER,
        /** A third of the floor at a time, never the same third. */
        HAIL,
        /** A row and a column that cross somewhere new each time. */
        CROSSHAIR,
        /** The straight ways from door to door, then everything that is not one. */
        LANES,
        /** All of the floor but one narrow path, which shows itself only for a moment. */
        PATH,
        /** Nothing until somebody stands still: then the tile they stood on. */
        CHASE,
        /** Every other ring about the middle, then the rings between. */
        RINGS,
        /** Teeth from one wall, then teeth from the other, meshing like a comb. */
        COMB,
        /** A line that swings from side wall to side wall and back. */
        PENDULUM,
        /** Two lines half a room apart, crossing it together. */
        TWIN_ROWS;

        /** How many turns make one round of this pattern, in a room reaching {@code r} from its middle. */
        public int slots(int r) {
            return switch (this) {
                case ROWS, COLUMNS, DIAGONAL -> 2 * r + 1;
                case BOUNCE, PENDULUM -> 4 * r;
                case PINCER, RIPPLE, IMPLODE, TWIN_ROWS -> r + 1;
                case FLOOD -> r;
                case CHECKER, STRIPES, LANES, RINGS, COMB -> 2;
                case WEAVE, QUARTERS, TWIN_SPIRAL, SCATTER, CROSSHAIR -> 4;
                case SERPENT -> 5;
                case CROSSFIRE, HAIL -> 3;
                case SPIRAL -> 8;
                case PATH, CHASE -> 1;
            };
        }

        /** Whether a tile is one of those taken on turn {@code s} of round {@code round}. */
        public boolean in(int s, int dx, int dz, int r, long round, long seed) {
            int ring = Math.max(Math.abs(dx), Math.abs(dz));
            return switch (this) {
                case ROWS -> dz + r == s;
                case COLUMNS -> dx + r == s;
                case BOUNCE -> dz + r == (s <= 2 * r ? s : 4 * r - s);
                case PINCER -> Math.abs(dz) == r - s;
                case RIPPLE -> ring == s;
                case IMPLODE -> ring == r - s;
                case FLOOD -> ring >= r - s;
                case CHECKER -> ((dx + dz) & 1) == s;
                case STRIPES -> (dz & 1) == s;
                case WEAVE -> (s & 1) == 0 ? (dz & 1) == (s >> 1) : (dx & 1) == (s >> 1);
                case QUARTERS -> switch (s) {
                    case 0 -> dx >= 0 && dz <= 0;
                    case 1 -> dx >= 0 && dz >= 0;
                    case 2 -> dx <= 0 && dz >= 0;
                    default -> dx <= 0 && dz <= 0;
                };
                case DIAGONAL -> (dx + dz + 2 * r) / 2 == s;
                case SERPENT -> Math.floorMod(dx + 2 * dz, 5) == s;
                case CROSSFIRE -> s == 0 ? dx == 0 || dz == 0 : s == 1 ? Math.abs(dx) == Math.abs(dz) && dx != 0 : dx != 0 && dz != 0 && Math.abs(dx) != Math.abs(dz);
                case SPIRAL -> ring > 0 && sector(dx, dz) == s;
                case TWIN_SPIRAL -> ring > 0 && (sector(dx, dz) & 3) == s;
                case SCATTER -> (hash(dx, dz, round, seed) & 3) == s;
                case HAIL -> Math.floorMod(hash(dx, dz, round * 3 + s, seed), 10) < 3;
                case CROSSHAIR -> dz == Math.floorMod(hash(1, 0, round * 4 + s, seed), 2 * r + 1) - r || dx == Math.floorMod(hash(0, 1, round * 4 + s, seed), 2 * r + 1) - r;
                case LANES -> (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) == (s == 0);
                case PATH, CHASE -> false;
                case RINGS -> (ring & 1) == s;
                case COMB -> ((dx & 1) == 0) == ((dz <= 0) == (s == 0));
                case PENDULUM -> dx + r == (s <= 2 * r ? s : 4 * r - s);
                case TWIN_ROWS -> Math.floorMod(dz + r - s, r + 1) == 0;
            };
        }
    }

    /** Which eighth of the room a tile is in, counting round from the east. */
    public static int sector(int dx, int dz) {
        return Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4.0)), 8);
    }

    static int hash(int a, int b, long c, long seed) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (c * 0x165667B19E3779F9L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) (h & 0x7FFFFFFF);
    }

    /**
     * The trap halls. {@code turn} is how long one turn of the pattern lasts; a tile taken on a turn is warned of
     * for {@code warn} ticks from its start and then live for {@code hold}. {@code threat} is how dangerous a level
     * has to be before the hall turns up on it.
     */
    public enum Variant {
        EMBER_TIDE(Hazard.FLAME, Pattern.ROWS, Size.NORMAL, 12, 16, 8, 0),
        EMBER_SHUTTLE(Hazard.FLAME, Pattern.BOUNCE, Size.WIDE, 11, 16, 7, 2),
        CINDER_RAIN(Hazard.FLAME, Pattern.HAIL, Size.NORMAL, 30, 16, 14, 0),
        PYRE_RING(Hazard.FLAME, Pattern.RIPPLE, Size.WIDE, 14, 16, 10, 1),
        FURNACE_FLOOR(Hazard.SOULFIRE, Pattern.CHECKER, Size.SMALL, 34, 14, 16, 4),
        HUNTING_FLAME(Hazard.FLAME, Pattern.CHASE, Size.NORMAL, 14, 14, 16, 3),
        EMBER_PATH(Hazard.SOULFIRE, Pattern.PATH, Size.WIDE, 110, 24, 70, 5),
        FANG_ROWS(Hazard.SPIKE, Pattern.STRIPES, Size.NORMAL, 32, 14, 14, 0),
        FANG_WEAVE(Hazard.SPIKE, Pattern.WEAVE, Size.WIDE, 22, 14, 14, 2),
        THE_JAW(Hazard.SPIKE, Pattern.PINCER, Size.NORMAL, 12, 14, 12, 1),
        NEEDLE_LATTICE(Hazard.SPIKE, Pattern.SERPENT, Size.NORMAL, 18, 14, 14, 3),
        THORN_BLOOM(Hazard.SPIKE, Pattern.IMPLODE, Size.SMALL, 14, 14, 12, 0),
        NARROW_WAY(Hazard.SPIKE, Pattern.PATH, Size.NORMAL, 100, 24, 60, 2),
        SALTIRE(Hazard.SPIKE, Pattern.CROSSFIRE, Size.NORMAL, 28, 14, 16, 1),
        FROST_CREEP(Hazard.FROST, Pattern.FLOOD, Size.NORMAL, 18, 16, 18, 1),
        RIME_WHEEL(Hazard.FROST, Pattern.SPIRAL, Size.WIDE, 10, 14, 12, 2),
        HOAR_QUARTERS(Hazard.FROST, Pattern.QUARTERS, Size.NORMAL, 36, 16, 26, 0),
        GLACIER_MARCH(Hazard.FROST, Pattern.COLUMNS, Size.SMALL, 12, 14, 8, 0),
        STORM_CROSS(Hazard.SHOCK, Pattern.CROSSHAIR, Size.NORMAL, 24, 14, 12, 2),
        SPARK_SCATTER(Hazard.SHOCK, Pattern.SCATTER, Size.NORMAL, 26, 14, 14, 1),
        THUNDER_WHEEL(Hazard.SHOCK, Pattern.TWIN_SPIRAL, Size.WIDE, 16, 14, 12, 4),
        LIVE_WIRE(Hazard.SHOCK, Pattern.LANES, Size.SMALL, 40, 14, 22, 3),
        STATIC_FIELD(Hazard.SHOCK, Pattern.DIAGONAL, Size.NORMAL, 9, 14, 12, 0),
        STALKING_STORM(Hazard.SHOCK, Pattern.CHASE, Size.WIDE, 12, 12, 14, 6),
        MIASMA_DRIFT(Hazard.VENOM, Pattern.DIAGONAL, Size.WIDE, 10, 16, 16, 1),
        SPORE_BLOOM(Hazard.VENOM, Pattern.RIPPLE, Size.NORMAL, 14, 16, 10, 0),
        BILE_POOLS(Hazard.VENOM, Pattern.HAIL, Size.SMALL, 30, 16, 16, 2),
        CREEPING_ROT(Hazard.WITHER, Pattern.FLOOD, Size.SMALL, 18, 16, 18, 5),
        GRAVE_BREATH(Hazard.WITHER, Pattern.QUARTERS, Size.WIDE, 36, 16, 26, 3),
        HOLLOW_STEPS(Hazard.WITHER, Pattern.CHASE, Size.NORMAL, 16, 14, 18, 4),
        GEYSER_FIELD(Hazard.GEYSER, Pattern.SCATTER, Size.NORMAL, 26, 14, 12, 0),
        BLOWHOLES(Hazard.GEYSER, Pattern.CHECKER, Size.SMALL, 34, 14, 14, 1),
        GALE_HALL(Hazard.GALE, Pattern.ROWS, Size.WIDE, 11, 14, 7, 2),
        SKY_SNARE(Hazard.LEVITATE, Pattern.HAIL, Size.NORMAL, 30, 14, 14, 3),
        FALSE_FLOOR(Hazard.LEVITATE, Pattern.STRIPES, Size.WIDE, 32, 14, 14, 5),
        BLIND_MARCH(Hazard.BLIGHT, Pattern.ROWS, Size.NORMAL, 12, 14, 8, 2),
        DARK_WEAVE(Hazard.BLIGHT, Pattern.WEAVE, Size.SMALL, 22, 14, 14, 4),
        BLACK_TIDE(Hazard.BLIGHT, Pattern.BOUNCE, Size.NORMAL, 10, 14, 7, 6),
        TOLLING_RINGS(Hazard.SHOCK, Pattern.RINGS, Size.NORMAL, 34, 14, 14, 1),
        FROST_RINGS(Hazard.FROST, Pattern.RINGS, Size.SMALL, 34, 14, 14, 0),
        THE_COMB(Hazard.SPIKE, Pattern.COMB, Size.NORMAL, 34, 14, 16, 2),
        VENOM_COMB(Hazard.VENOM, Pattern.COMB, Size.WIDE, 34, 16, 16, 3),
        PENDULUM_FIRE(Hazard.SOULFIRE, Pattern.PENDULUM, Size.NORMAL, 11, 14, 7, 4),
        GALE_PENDULUM(Hazard.GALE, Pattern.PENDULUM, Size.WIDE, 11, 14, 7, 2),
        TWIN_TIDES(Hazard.WITHER, Pattern.TWIN_ROWS, Size.WIDE, 12, 16, 8, 3),
        MARCHING_THORNS(Hazard.SPIKE, Pattern.TWIN_ROWS, Size.NORMAL, 12, 14, 8, 1),
        // stones from the ceiling, roots from the floor, and floors that feed on whoever crosses them
        CRUSHING_ROWS(Hazard.CRUSH, Pattern.ROWS, Size.NORMAL, 12, 14, 8, 2),
        FALLING_SKY(Hazard.CRUSH, Pattern.HAIL, Size.NORMAL, 30, 14, 14, 3),
        MILLSTONE(Hazard.CRUSH, Pattern.SPIRAL, Size.WIDE, 10, 14, 12, 4),
        ROOT_TANGLE(Hazard.SNARE, Pattern.CHECKER, Size.SMALL, 34, 14, 14, 1),
        BRIAR_RINGS(Hazard.SNARE, Pattern.RINGS, Size.NORMAL, 34, 14, 14, 2),
        STRANGLEVINE(Hazard.SNARE, Pattern.CHASE, Size.NORMAL, 16, 14, 18, 4),
        LEECH_FLOOR(Hazard.DRAIN, Pattern.QUARTERS, Size.NORMAL, 36, 16, 26, 2),
        HUNGER_TIDE(Hazard.DRAIN, Pattern.BOUNCE, Size.NORMAL, 10, 14, 7, 5),
        SAPPING_WEAVE(Hazard.DRAIN, Pattern.WEAVE, Size.WIDE, 22, 14, 14, 3);

        public final Hazard hazard;
        public final Pattern pattern;
        public final Size size;
        public final int turn, warn, hold;
        public final double threat;

        Variant(Hazard hazard, Pattern pattern, Size size, int turn, int warn, int hold, double threat) {
            this.hazard = hazard;
            this.pattern = pattern;
            this.size = size;
            this.turn = turn;
            this.warn = warn;
            this.hold = hold;
            this.threat = threat;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** One whole round of the pattern, in ticks. */
        public int round() {
            return pattern.slots(size.half) * turn;
        }

        /** How long a tile is warned of on a level this dangerous: shorter the deeper, never less than a third of a second. */
        public int warn(double levelThreat) {
            return Math.max(7, warn - (int) Math.min(8.0, levelThreat * 0.5));
        }
    }

    /** True for the three tiles just inside a doorway: where somebody stands to watch a hall before crossing it. */
    public static boolean doormat(int r, boolean[] doors, int dx, int dz) {
        for (int side = 0; side < 4; side++) {
            if (!doors[side]) continue;
            int along = dx * SIDES[side][0] + dz * SIDES[side][1], across = dx * SIDES[side][1] + dz * SIDES[side][0];
            if (along == r && Math.abs(across) <= 1) return true;
        }
        return false;
    }

    /**
     * The one way across a hall that keeps to a path: the middle, and from it an arm to every doorway, each with a
     * jog in it that the seed chooses.
     */
    public static boolean onPath(int r, boolean[] doors, long seed, int dx, int dz) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return true;
        for (int side = 0; side < 4; side++) {
            if (!doors[side]) continue;
            int along = dx * SIDES[side][0] + dz * SIDES[side][1], across = dx * SIDES[side][1] + dz * SIDES[side][0];
            if (along < 2 || along > r) continue;
            int near = Math.floorMod(hash(side, 1, 0, seed), 3) - 1, far = Math.floorMod(hash(side, 2, 0, seed), 3) - 1;
            int jog = 2 + Math.floorMod(hash(side, 3, 0, seed), Math.max(1, r - 2));
            if (along < jog && across == near) return true;
            if (along > jog && across == far) return true;
            if (along == jog && across >= Math.min(near, far) && across <= Math.max(near, far)) return true;
        }
        return false;
    }

    /**
     * What a tile of a hall is doing at a moment: {@link #SAFE}, {@link #WARN} or {@link #LIVE}. Not for
     * {@link Pattern#CHASE}, which goes by where people stand rather than by the clock.
     */
    public static int state(Variant v, int warn, boolean[] doors, long seed, int dx, int dz, long now) {
        int r = v.size.half;
        if (Math.abs(dx) > r || Math.abs(dz) > r || doormat(r, doors, dx, dz)) return SAFE;
        long clock = now + Math.floorMod(seed, 1000L);
        if (v.pattern == Pattern.PATH) {
            if (onPath(r, doors, seed, dx, dz)) return SAFE;
            long rel = Math.floorMod(clock, (long) v.turn);
            return rel < warn ? WARN : rel < warn + v.hold ? LIVE : SAFE;
        }
        if (v.pattern == Pattern.CHASE) return SAFE;
        int slots = v.pattern.slots(r), best = SAFE;
        long round = Math.floorDiv(clock, (long) slots * v.turn);
        for (long k = round; k >= round - 1; k--) {
            for (int s = 0; s < slots; s++) {
                long rel = clock - (k * slots + s) * v.turn;
                if (rel < 0 || rel >= warn + v.hold || !v.pattern.in(s, dx, dz, r, k, seed)) continue;
                if (rel >= warn) return LIVE;
                best = WARN;
            }
        }
        return best;
    }

    /**
     * Whether somebody who watches a hall can walk from the doorway on one side to every other doorway without
     * ever standing on a live tile - waiting where they like, and taking {@link #STEP_TICKS} ticks to a tile.
     */
    public static boolean crossable(Variant v, int warn, boolean[] doors, long seed, int from) {
        int r = v.size.half, w = 2 * r + 1, horizon = Math.min(2400, 2 * v.round() + 500);
        boolean[][] live = new boolean[horizon + STEP_TICKS + 1][w * w];
        for (int t = 0; t < live.length; t++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) live[t][(dx + r) * w + dz + r] = state(v, warn, doors, seed, dx, dz, t) == LIVE;
            }
        }
        boolean[][] reach = new boolean[horizon + STEP_TICKS + 1][w * w];
        reach[0][(SIDES[from][0] * r + r) * w + SIDES[from][1] * r + r] = true;
        boolean[] got = new boolean[4];
        for (int t = 0; t < horizon; t++) {
            for (int i = 0; i < w * w; i++) {
                if (!reach[t][i]) continue;
                int x = i / w - r, z = i % w - r;
                for (int side = 0; side < 4; side++) if (x == SIDES[side][0] * r && z == SIDES[side][1] * r) got[side] = true;
                if (!live[t + 1][i]) reach[t + 1][i] = true;
                for (int[] d : SIDES) {
                    int nx = x + d[0], nz = z + d[1];
                    if (Math.abs(nx) > r || Math.abs(nz) > r) continue;
                    int j = (nx + r) * w + nz + r;
                    boolean clear = true;
                    // (half the step on the tile being left, half on the one being stepped onto)
                    for (int k = 1; k <= STEP_TICKS && clear; k++) clear = !live[t + k][k <= STEP_TICKS / 2 ? i : j];
                    if (clear) reach[t + STEP_TICKS][j] = true;
                }
            }
        }
        for (int side = 0; side < 4; side++) if (doors[side] && !got[side]) return false;
        return true;
    }

    /** The share of a hall's floor that is live, taken over a whole round: how much of the time it has to be minded. */
    public static double danger(Variant v, int warn, boolean[] doors, long seed) {
        int r = v.size.half, round = Math.max(1, v.round());
        long live = 0, all = 0;
        for (int t = 0; t < round; t += 2) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    all++;
                    if (state(v, warn, doors, seed, dx, dz, t) == LIVE) live++;
                }
            }
        }
        return live / (double) all;
    }
}
