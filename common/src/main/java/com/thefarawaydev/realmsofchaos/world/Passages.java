package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.block.ConnectedBlock;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.item.Tarot;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The passages: what lies between every two floors of a tower. Passage {@code n} leads from floor {@code n} to
 * floor {@code n + 1}. The Gate of Ascent on floor n opens into it the first time.
 *
 * A passage is not one level but several, stacked one above the other - two low in the tower, three higher up -
 * and each is a maze of rooms laid out procedurally from the world seed: sealed chambers with their sentinels,
 * treasure rooms, an arena, a shrine, gardens, libraries, pools, trapped rooms, plain rooms and corridors. The gate
 * at the far end of a level opens when every seal on that level is broken, and leads to the next level up; the
 * last one leads to the next floor of the tower. Every level is harder than the one below it, and every passage
 * harder than the one before.
 */
public final class Passages {
    public static final int COUNT = Tower.FLOORS - 1;
    /** Floor level of the lowest level of every passage; each further level is {@link #LEVEL_PITCH} higher. */
    public static final int Y = 60;
    public static final int LEVEL_PITCH = 12;
    /** One cell of the maze: a 9x9 room and the walls round it. */
    /** A room and the corridor beyond it are sixteen across: nine of room (eleven if wide), and seven of corridor to the next. */
    public static final int CELL = 16, ROOM = 9, CENTRE = 5;
    /** How far from a room's middle its corridor begins, and where the next room's wall ends it. */
    public static final int CORRIDOR_FROM = 5, CORRIDOR_TO = CELL - 5;
    /** What a gate is made of until every seal on its level is broken. */
    public static final Block GATE_SHUT = Blocks.TINTED_GLASS;
    public static final int HEADROOM = 5;
    /** The headroom of the tallest rooms. A level is a solid slab twelve blocks deep, from two below its floor. */
    public static final int TALL = 8;
    public static final int MAX_SIZE = 6;
    public static final int X0 = 600, PITCH = 160;
    public static final int LAYOUT_VERSION = 32;
    public static final String GUARDIAN_TAG = "realms_of_chaos_guardian";
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".passage.";

    public enum Kind { START, EXIT, SEAL, TREASURE, SHRINE, HALL, ROOM, TRAP, GARDEN, LIBRARY, POOL, ARENA, VAULT, CHIMES, BEAM, PLATES, CRUSHER, DARTS,
        SPIKES, FLAMES, BLADES, STAIRWELL, ARC, GAS, CELLS, STORE, GAUNTLET, OBSERVATORY, ARMORY, BATHS, SANCTUM,
        PROVING, BARRACKS, DUEL, AMBUSH, OVERLOOK, SHUTTERS, SMITHY, CRYPT, WAR_ROOM, DIALS, SLIDES }

    /** How the corridor between two rooms runs: straight, round a baffle to one side or either, or past a nook. */
    public static final int RUN_STRAIGHT = 0, RUN_BEND_LOW = 1, RUN_BEND_HIGH = 2, RUN_SPLIT = 3, RUN_NOOK_LOW = 4, RUN_NOOK_HIGH = 5, RUN_GALLERY = 6, RUN_CAUSEWAY = 7;
    /** What a corridor's vents let out: fire, frost, sparks or venom. */
    public static final int JET_FLAME = 0, JET_FROST = 1, JET_SHOCK = 2, JET_VENOM = 3;

    /**
     * What weighs on a level. The first three levels of a passage are clean; further in, most carry one: a status
     * effect laid on everyone there for as long as they stay. A shrine's blessing holds it off for a while.
     */
    public enum Affliction {
        NONE, GLOOM, WEIGHT, FRAILTY, HUNGER, FATIGUE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("affliction." + RealmsOfChaos.MOD_ID + "." + id());
        }

        public Component lore() {
            return Component.translatable("affliction." + RealmsOfChaos.MOD_ID + "." + id() + ".lore");
        }
    }

    /** How long a shrine's blessing holds a level's affliction off. */
    public static final int SHELTER_TICKS = 20 * 90;
    private static final Map<UUID, Long> SHELTERED = new HashMap<>();

    /** A shrine has blessed this player: the level's affliction leaves them be for a minute and a half. */
    public static void shelter(ServerPlayer player) {
        if (player.level().getServer() != null) SHELTERED.put(player.getUUID(), (long) player.level().getServer().getTickCount() + SHELTER_TICKS);
    }

    /** The blessing is gone: for tests. */
    public static void unshelter(ServerPlayer player) {
        SHELTERED.remove(player.getUUID());
    }

    public static boolean sheltered(ServerPlayer player) {
        Long until = SHELTERED.get(player.getUUID());
        return until != null && player.level().getServer() != null && player.level().getServer().getTickCount() < until;
    }

    /** Lays a level's affliction on a player: called once a second while they are on it. @return true if it was laid on */
    public static boolean afflict(ServerPlayer player, Plan plan) {
        Affliction a = plan.affliction();
        if (a == Affliction.NONE || player.isCreative() || player.isSpectator() || sheltered(player)) return false;
        long second = player.level().getServer().getTickCount() / 20L;
        var effect = switch (a) {
            case GLOOM -> second % 12 < 4 ? net.minecraft.world.effect.MobEffects.DARKNESS : null;         // (on and off: four seconds in twelve)
            case WEIGHT -> net.minecraft.world.effect.MobEffects.SLOWNESS;
            case FRAILTY -> net.minecraft.world.effect.MobEffects.WEAKNESS;
            case HUNGER -> net.minecraft.world.effect.MobEffects.HUNGER;
            case FATIGUE -> net.minecraft.world.effect.MobEffects.MINING_FATIGUE;
            default -> null;
        };
        if (effect == null) return false;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, 50, 0, true, false, true));
        return true;
    }

    /** How a room is shaped, for the kinds that are not all alike. */
    public static final int FORM_PLAIN = 0, FORM_CUT_CORNERS = 1, FORM_COLONNADE = 2, FORM_SUNKEN = 3;

    public record Cell(int x, int z) {}

    /** A player's place in the passages: which tower, which passage, which level of it (0 is the lowest). */
    public record Spot(Line line, int index, int stage) {
        public Spot(Line line, int index) {
            this(line, index, 0);
        }
    }

    /** One sentinel of a room's garrison. */
    public record Post(Sentinel.Kind kind, boolean elite) {}

    private Passages() {}

    /** The most levels a passage can have. */
    public static final int MAX_STAGES = 10;

    /**
     * How many levels a passage has: anything from one to ten, decided by the world's seed - one to four in the
     * lower third of a tower, two to seven in the middle, four to ten near the top.
     */
    public static int stages(long worldSeed, Line line, int index) {
        Random random = new Random(worldSeed ^ (0x5DEECE66DL * (line.ordinal() * 100L + index + 7)));
        random.nextInt();
        return switch (Tower.tier(index)) {
            case 1 -> 1 + random.nextInt(4);
            case 2 -> 2 + random.nextInt(6);
            default -> 4 + random.nextInt(7);
        };
    }

    public static int stages(ServerLevel level, Line line, int index) {
        return stages(level.getSeed(), line, index);
    }

    // ---- the plan --------------------------------------------------------------------------------------------

    /** The layout of one level of one passage. Pure data, worked out from the seed alone; building it is a separate step. */
    public static final class Plan {
        public final Line line;
        public final int index, stage, stages, size, tier;
        public final long seed;
        /** How dangerous this level is: see {@link Sentinel#threat(int, int)}. */
        public final double threat;
        private final Kind[][] kinds;
        /** east[x][z]: a doorway between (x, z) and (x + 1, z). south[x][z]: between (x, z) and (x, z + 1). */
        private final boolean[][] east, south;
        private final int[][] heights, forms;
        private Affliction affliction = Affliction.NONE;
        private final boolean[][] wides, smalls;
        /** Which trap hall a room is, for the rooms that are one. */
        private final Gauntlet.Variant[][] variants;
        public Cell start, exit;
        /** True if the exit room is entered from the east or west: its gate stands across that way, facing whoever comes in. */
        private boolean gateTurned;
        public final List<Cell> seals = new ArrayList<>();
        /** Ordinary rooms and corridors that a patrol of sentinels walks. */
        public final Set<Cell> patrols = new LinkedHashSet<>();

        Plan(long worldSeed, Line line, int index, int stage) {
            this.line = line;
            this.index = index;
            this.stages = stages(worldSeed, line, index);
            this.stage = Math.max(0, Math.min(stages - 1, stage));
            this.tier = Tower.tier(index);
            // rooms: 4 x 4 low in the tower and 5 x 5 higher; any level after a passage's first may be a row and a
            // column larger, and the last level of the highest passages always is
            Random shape = new Random(worldSeed ^ (0xD1B54A32D192ED03L * (line.ordinal() * 1000L + index * 16L + this.stage + 1)));
            // (a god's tower is no burrow: five rooms square at the least, six on its grander levels)
            int base = 5;
            boolean grand = tier == 3 && this.stage == stages - 1;
            this.size = Math.min(MAX_SIZE, base + (grand || (this.stage > 0 && shape.nextFloat() < 0.4F) ? 1 : 0));
            this.threat = Sentinel.threat(index, this.stage);
            this.seed = worldSeed ^ (0x9E3779B97F4A7C15L * (line.ordinal() * 100L + index + 1)) ^ (0xC2B2AE3D27D4EB4FL * this.stage);
            this.kinds = new Kind[size][size];
            this.east = new boolean[size][size];
            this.south = new boolean[size][size];
            this.heights = new int[size][size];
            this.forms = new int[size][size];
            this.wides = new boolean[size][size];
            this.smalls = new boolean[size][size];
            this.variants = new Gauntlet.Variant[size][size];
            generate(new Random(seed));
            shape(new Random(seed ^ 0x5DEECE66DL));
            Random ill = new Random(seed ^ 0xAFF11C7EDL);
            float chance = this.stage < 3 ? 0.0F : this.stage < 6 ? 0.6F : 0.85F;
            if (ill.nextFloat() < chance) affliction = Affliction.values()[1 + ill.nextInt(Affliction.values().length - 1)];
        }

        /**
         * No two levels are built alike: a room may be low or tall, a step wider than its neighbours, its corners
         * cut, ringed with columns, or sunk a step into the floor. Rooms whose workings depend on their size - the
         * puzzles and the trap halls - keep the plain one.
         */
        private void shape(Random random) {
            for (Cell c : cells()) {
                int h = HEADROOM, form = FORM_PLAIN;
                boolean wide = false;
                switch (kinds[c.x][c.z]) {
                    case START -> h = 6;
                    case EXIT, SEAL, SHRINE -> h = 7;
                    case TREASURE -> h = random.nextBoolean() ? 7 : HEADROOM;
                    case ARENA, STAIRWELL, PROVING -> {
                        h = TALL;
                        wide = true;
                    }
                    case SMITHY, CRYPT -> {
                        h = random.nextBoolean() ? 7 : HEADROOM;
                        wide = random.nextFloat() < 0.5F;
                    }
                    case WAR_ROOM -> {
                        h = 7;
                        wide = true;
                    }
                    case BARRACKS, DUEL -> {
                        h = random.nextBoolean() ? 7 : TALL;
                        wide = true;
                    }
                    case AMBUSH, OVERLOOK -> {
                        // an ambush needs a ceiling to drop from; an overlook, room for its gallery
                        h = TALL;
                        wide = true;
                    }
                    case SHUTTERS -> {
                        // a shutter hall needs the room: a lantern in every quarter, and space round them to shoot from
                        h = 7;
                        wide = true;
                    }
                    case BEAM -> {
                        // a beam room needs the room: mirrors stand all about it
                        h = 7;
                        wide = true;
                    }
                    case CELLS, STORE -> h = random.nextBoolean() ? HEADROOM : 7;
                    case GAUNTLET -> {
                        // a trap hall is the size its trap needs: a small one is close work, a wide one a long way across
                        Gauntlet.Variant v = variants[c.x][c.z];
                        wide = v.size == Gauntlet.Size.WIDE;
                        smalls[c.x][c.z] = v.size == Gauntlet.Size.SMALL;
                        h = v.hazard == Gauntlet.Hazard.LEVITATE ? TALL : v.size == Gauntlet.Size.SMALL ? HEADROOM : wide || random.nextBoolean() ? 7 : HEADROOM;
                    }
                    case ROOM, GARDEN, LIBRARY, POOL, OBSERVATORY, ARMORY, BATHS, SANCTUM -> {
                        float roll = random.nextFloat();
                        // (more of them are tall, and more than half a step wider: room to breathe between the halls)
                        h = roll < 0.15F ? HEADROOM : roll < 0.5F ? 7 : TALL;
                        wide = random.nextFloat() < 0.7F;
                        form = kinds[c.x][c.z] == Kind.ROOM ? random.nextInt(4) : random.nextInt(2);
                    }
                    default -> { }
                }
                heights[c.x][c.z] = h;
                forms[c.x][c.z] = form;
                wides[c.x][c.z] = wide;
            }
        }

        /** What weighs on this level, if anything. */
        public Affliction affliction() {
            return affliction;
        }

        /** How much air a room has above its floor: 5 for an ordinary room, up to {@link #TALL}. */
        public int height(Cell c) {
            return heights[c.x][c.z];
        }

        /** A wide room is eleven across instead of nine. */
        public boolean wide(Cell c) {
            return wides[c.x][c.z];
        }

        /** A small room is seven across. Only trap halls are. */
        public boolean small(Cell c) {
            return smalls[c.x][c.z];
        }

        /** Which trap hall a room is, or null if it is not one. */
        public Gauntlet.Variant variant(Cell c) {
            return variants[c.x][c.z];
        }

        /** How far a room reaches from its middle: 4, 5 for a wide one, 3 for a small one. */
        public int half(Cell c) {
            return wides[c.x][c.z] ? 5 : smalls[c.x][c.z] ? 3 : 4;
        }

        /**
         * How the corridor east (dir 0) or south (dir 1) of a room runs. Fewer than half run straight: the rest go
         * round a baffle that hides what is ahead, or pass a nook with something in it.
         */
        public int run(Cell c, int dir) {
            Cell other = dir == 0 ? new Cell(c.x + 1, c.z) : new Cell(c.x, c.z + 1);
            if (other.x >= size || other.z >= size || !connected(c, other)) return RUN_STRAIGHT;
            long h = seed * 131 + c.x * 15485863L + c.z * 7919L + dir * 104729L;
            h ^= h >>> 15;
            h *= 0xD6E8FEB86659FD93L;
            int roll = (int) Math.floorMod(h >>> 24, 100L);
            // (a quarter run straight; the rest bend, split, pass a nook, open into a pillared gallery or cross water on a causeway)
            return roll < 24 ? RUN_STRAIGHT : roll < 34 ? RUN_BEND_LOW : roll < 44 ? RUN_BEND_HIGH : roll < 54 ? RUN_SPLIT : roll < 62 ? RUN_NOOK_LOW : roll < 70 ? RUN_NOOK_HIGH
                    : roll < 86 ? RUN_GALLERY : RUN_CAUSEWAY;
        }

        public int form(Cell c) {
            return forms[c.x][c.z];
        }

        public boolean last() {
            return stage == stages - 1;
        }

        /** Levels come in wings of three: A, B, C and - for a passage of ten - D. Each wing is built of different stone. */
        public int wing() {
            return stage / 3;
        }

        /** The level's name, as on its bar and its chart: passage, wing, level - "3-B2". */
        public String label() {
            return index + "-" + (char) ('A' + wing()) + (stage % 3 + 1);
        }

        /** What this level is built of: its tower's stone, a different set for each wing. */
        TowerBuilder.Palette palette() {
            return TowerBuilder.palette(line, (tier - 1 + wing()) % 4 + 1);
        }

        private void open(Cell a, Cell b) {
            if (a.x != b.x) east[Math.min(a.x, b.x)][a.z] = true;
            else south[a.x][Math.min(a.z, b.z)] = true;
        }

        public boolean connected(Cell a, Cell b) {
            if (Math.abs(a.x - b.x) + Math.abs(a.z - b.z) != 1) return false;
            return a.x != b.x ? east[Math.min(a.x, b.x)][a.z] : south[a.x][Math.min(a.z, b.z)];
        }

        public List<Cell> neighbours(Cell c) {
            List<Cell> out = new ArrayList<>();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c.x + d[0], z = c.z + d[1];
                if (x >= 0 && z >= 0 && x < size && z < size) out.add(new Cell(x, z));
            }
            return out;
        }

        public List<Cell> cells() {
            List<Cell> out = new ArrayList<>();
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) out.add(new Cell(x, z));
            return out;
        }

        public Kind kind(Cell c) {
            return kinds[c.x][c.z];
        }

        /** Steps through doorways from the start to every cell. */
        public Map<Cell, Integer> distances() {
            Map<Cell, Integer> dist = new HashMap<>();
            Deque<Cell> queue = new ArrayDeque<>();
            dist.put(start, 0);
            queue.add(start);
            while (!queue.isEmpty()) {
                Cell c = queue.poll();
                for (Cell n : neighbours(c)) {
                    if (connected(c, n) && !dist.containsKey(n)) {
                        dist.put(n, dist.get(c) + 1);
                        queue.add(n);
                    }
                }
            }
            return dist;
        }

        private int doors(Cell c) {
            int n = 0;
            for (Cell o : neighbours(c)) if (connected(c, o)) n++;
            return n;
        }

        /** How many treasure rooms this level has. */
        public int treasures() {
            return tier == 1 ? 1 : 2;
        }

        /** The levels above the first each have an arena: an elite and its pack, guarding a cache. */
        public int arenas() {
            return stage >= 1 ? 1 : 0;
        }

        /** Takes the first room still free that no doorway joins to a room of this kind (or, there being none, the first). */
        private Cell take(List<Cell> free, Kind kind) {
            for (int i = 0; i < free.size(); i++) {
                Cell c = free.get(i);
                boolean meets = false;
                for (Cell n : neighbours(c)) if (connected(c, n) && kinds[n.x][n.z] == kind) meets = true;
                if (!meets) return free.remove(i);
            }
            return free.remove(0);
        }

        private void generate(Random random) {
            // a maze: a random spanning tree, so every room can be reached, plus a few extra doors so there are loops
            start = new Cell(random.nextInt(size), random.nextInt(size));
            boolean[][] seen = new boolean[size][size];
            Deque<Cell> stack = new ArrayDeque<>();
            Map<Cell, Cell> cameFrom = new HashMap<>();
            stack.push(start);
            seen[start.x][start.z] = true;
            while (!stack.isEmpty()) {
                Cell cur = stack.peek();
                List<Cell> fresh = new ArrayList<>();
                for (Cell n : neighbours(cur)) if (!seen[n.x][n.z]) fresh.add(n);
                if (fresh.isEmpty()) {
                    stack.pop();
                    continue;
                }
                // (a way that turns is three times as likely as one that carries straight on: the level winds)
                Cell before = cameFrom.get(cur);
                List<Cell> lots = new ArrayList<>();
                for (Cell n : fresh) {
                    boolean straight = before != null && n.x - cur.x == cur.x - before.x && n.z - cur.z == cur.z - before.z;
                    for (int k = 0; k < (straight ? 1 : 3); k++) lots.add(n);
                }
                Cell next = lots.get(random.nextInt(lots.size()));
                open(cur, next);
                cameFrom.put(next, cur);
                seen[next.x][next.z] = true;
                stack.push(next);
            }
            for (Cell c : cells()) {
                for (Cell n : neighbours(c)) {
                    if ((n.x > c.x || n.z > c.z) && !connected(c, n) && random.nextFloat() < 0.1F) open(c, n);
                }
            }

            // the way out is as far from the way in as the maze allows
            Map<Cell, Integer> dist = distances();
            exit = start;
            for (Cell c : cells()) if (dist.get(c) > dist.get(exit)) exit = c;
            kinds[start.x][start.z] = Kind.START;
            kinds[exit.x][exit.z] = Kind.EXIT;
            // the gate faces the doorway a climber comes through: the one nearest the way in
            Cell way = null;
            for (Cell n : neighbours(exit)) if (connected(exit, n) && (way == null || dist.get(n) < dist.get(way))) way = n;
            gateTurned = way != null && way.x != exit.x;

            // seals hide in dead ends and far corners
            List<Cell> free = new ArrayList<>();
            for (Cell c : cells()) if (!c.equals(start) && !c.equals(exit)) free.add(c);
            Collections.shuffle(free, random);
            free.sort(Comparator.comparingInt((Cell c) -> doors(c) == 1 ? 0 : 1).thenComparingInt(c -> -dist.get(c)));
            int sealCount = tier + 1;
            for (int i = 0; i < sealCount; i++) {
                Cell c = take(free, Kind.SEAL);
                seals.add(c);
                kinds[c.x][c.z] = Kind.SEAL;
            }
            Collections.shuffle(free, random);
            Cell shrine = free.remove(0);
            kinds[shrine.x][shrine.z] = Kind.SHRINE;
            for (int i = 0; i < treasures(); i++) {
                Cell c = take(free, Kind.TREASURE);
                kinds[c.x][c.z] = Kind.TREASURE;
            }
            for (int i = 0; i < arenas(); i++) {
                Cell c = take(free, Kind.ARENA);
                kinds[c.x][c.z] = Kind.ARENA;
            }
            // puzzle rooms: one on a small level, up to three on the largest, never the same kind twice
            List<Kind> puzzles = new ArrayList<>(List.of(Kind.VAULT, Kind.CHIMES, Kind.BEAM, Kind.PLATES, Kind.SHUTTERS, Kind.DIALS, Kind.SLIDES));
            Collections.shuffle(puzzles, random);
            for (int i = 0; i < size - 3 && !free.isEmpty(); i++) {
                Cell c = free.remove(0);
                kinds[c.x][c.z] = puzzles.get(i);
            }
            // one stairwell: a tall hall with a flight of steps at either side up to a loft, and a cache on it
            if (free.size() > 3) {
                Cell c = free.remove(0);
                kinds[c.x][c.z] = Kind.STAIRWELL;
            }
            // a proving ground: a great sanded floor ringed in beast gates, and a war drum that calls three waves
            if (free.size() > 3) {
                Cell c = take(free, Kind.PROVING);
                kinds[c.x][c.z] = Kind.PROVING;
            }
            // the rest, as a stronghold is laid out: corridors and crossings between rooms, no two rooms of a kind
            // joined by a doorway, and a kind already used on the level less likely to be used again
            Kind[] common = {Kind.HALL, Kind.ROOM, Kind.TRAP, Kind.CRUSHER, Kind.DARTS, Kind.SPIKES, Kind.FLAMES, Kind.BLADES, Kind.ARC, Kind.GAS,
                    Kind.GARDEN, Kind.LIBRARY, Kind.POOL, Kind.CELLS, Kind.STORE, Kind.GAUNTLET, Kind.OBSERVATORY, Kind.ARMORY, Kind.BATHS, Kind.SANCTUM, Kind.BARRACKS, Kind.DUEL, Kind.AMBUSH, Kind.OVERLOOK,
                    Kind.SMITHY, Kind.CRYPT, Kind.WAR_ROOM};
            float[] weight = {2.4F, 1.8F, 0.4F, 0.22F, 0.22F, 0.22F, 0.22F, 0.22F, 0.22F, 0.22F, 0.9F, 0.8F, 0.7F, 0.7F, 0.7F, 4.5F, 0.55F, 0.55F, 0.55F, 0.55F, 0.6F,
                    0.45F, 0.5F, 0.5F, 0.5F, 0.5F, 0.45F};
            // the trap halls this level is dangerous enough for, in an order of its own: no hall twice on a level
            List<Gauntlet.Variant> halls = new ArrayList<>();
            for (Gauntlet.Variant v : Gauntlet.Variant.values()) if (v.threat <= threat) halls.add(v);
            Collections.shuffle(halls, random);
            int[] times = new int[common.length];
            for (Cell c : free) {
                // each kind's share is its weight, less the more it has been used on this level already; a kind that
                // would meet itself across a doorway has none (not even a corridor does - but trap halls may, since
                // no two of them are the same hall)
                float[] share = new float[common.length];
                float all = 0.0F;
                for (int k = 0; k < common.length; k++) {
                    int same = 0;
                    for (Cell n : neighbours(c)) if (connected(c, n) && kinds[n.x][n.z] == common[k]) same++;
                    if (common[k] == Kind.GAUNTLET ? halls.isEmpty() : same > 0) continue;
                    share[k] = weight[k] / (1.0F + 1.5F * times[k] * (common[k] == Kind.HALL ? 0.25F : common[k] == Kind.GAUNTLET ? 0.45F : 1.0F));
                    all += share[k];
                }
                float roll = random.nextFloat() * all;
                int best = -1;
                for (int k = 0; k < common.length && best < 0; k++) {
                    if (share[k] <= 0.0F) continue;
                    roll -= share[k];
                    if (roll <= 0.0F) best = k;
                }
                if (best < 0) best = 0;
                kinds[c.x][c.z] = common[best];
                if (common[best] == Kind.GAUNTLET) variants[c.x][c.z] = halls.remove(0);
                times[best]++;
            }
            // a third of the ordinary rooms have a patrol in them, and more on the levels further in
            float chance = Math.min(0.75F, 0.35F + 0.06F * stage);
            for (Cell c : free) {
                if (random.nextFloat() < chance) patrols.add(c);
            }
        }

        public int width() {
            return size * CELL;
        }

        /** The maze drawn in text, north at the top: a letter per room, lines for doorways. */
        public List<String> ascii() {
            List<String> rows = new ArrayList<>();
            for (int z = 0; z < size; z++) {
                StringBuilder rooms = new StringBuilder(), below = new StringBuilder();
                for (int x = 0; x < size; x++) {
                    rooms.append(switch (kinds[x][z]) {
                        case START -> 'S';
                        case EXIT -> 'X';
                        case SEAL -> '*';
                        case TREASURE -> '$';
                        case SHRINE -> '+';
                        case TRAP -> '!';
                        case HALL -> 'o';
                        case ROOM -> '#';
                        case GARDEN -> 'g';
                        case LIBRARY -> 'b';
                        case POOL -> '~';
                        case ARENA -> 'A';
                        case VAULT -> 'v';
                        case SHUTTERS -> 'u';
                        case DIALS -> 'd';
                        case SLIDES -> 's';
                        case CHIMES -> 'c';
                        case BEAM -> '/';
                        case PLATES -> 'p';
                        case CRUSHER -> '=';
                        case DARTS -> ':';
                        case SPIKES -> '^';
                        case FLAMES -> 'f';
                        case BLADES -> '%';
                        case STAIRWELL -> 'Z';
                        case ARC -> '@';
                        case GAS -> '"';
                        case CELLS -> 'H';
                        case STORE -> 'B';
                        case GAUNTLET -> 'x';
                        case OBSERVATORY -> 'O';
                        case ARMORY -> 'W';
                        case BATHS -> 'w';
                        case SANCTUM -> 'S';
                        case PROVING -> 'R';
                        case BARRACKS -> 'K';
                        case DUEL -> 'D';
                        case AMBUSH -> 'Y';
                        case OVERLOOK -> 'L';
                        case SMITHY -> 'F';
                        case CRYPT -> 'C';
                        case WAR_ROOM -> 'M';
                    });
                    if (x + 1 < size) rooms.append(east[x][z] ? "--" : "  ");
                    below.append(z + 1 < size && south[x][z] ? "|  " : "   ");
                }
                rows.add(rooms.toString());
                if (z + 1 < size) rows.add(below.toString().stripTrailing());
            }
            return rows;
        }

        /** The height of this level's floor. */
        public int y() {
            return Y + stage * LEVEL_PITCH;
        }

        /** The low north-west corner of the level's box. */
        public BlockPos origin() {
            return new BlockPos(X0 + index * PITCH, y(), -width() / 2);
        }

        /** The floor block at the middle of a cell. */
        public BlockPos centre(Cell c) {
            BlockPos o = origin();
            return new BlockPos(o.getX() + c.x * CELL + CENTRE, y(), o.getZ() + c.z * CELL + CENTRE);
        }

        public Vec3 arrival() {
            BlockPos c = centre(start);
            return new Vec3(c.getX() + 0.5, y() + 1, c.getZ() + 0.5);
        }

        public BlockPos sealPos(Cell c) {
            return centre(c).above(2);
        }

        /** The treasure cache or the shrine of a cell stands on its centre. */
        public BlockPos featurePos(Cell c) {
            return centre(c).above();
        }

        /** The three gate columns of the exit room. */
        public List<BlockPos> gatePositions() {
            List<BlockPos> out = new ArrayList<>();
            for (int a = -1; a <= 1; a++) for (int dy = 1; dy <= 3; dy++) out.add(gateOffset(a, dy));
            return out;
        }

        /**
         * Corridors with fire in them: a row of vents across the middle of the corridor east (dir 0) or south (dir 1)
         * of a room, on a clock of their own. None next to the room a climber arrives in, more the deeper the level.
         */
        public boolean jets(Cell c, int dir) {
            Cell other = dir == 0 ? new Cell(c.x + 1, c.z) : new Cell(c.x, c.z + 1);
            if (other.x >= size || other.z >= size || !connected(c, other) || c.equals(start) || other.equals(start)) return false;
            // (only a straight corridor has room for jets across it)
            if (run(c, dir) != RUN_STRAIGHT) return false;
            long h = seed * 31 + c.x * 7919L + c.z * 104729L + dir * 15485863L;
            h ^= h >>> 17;
            h *= 0x9E3779B97F4A7C15L;
            return Math.floorMod(h >>> 20, 100) < Math.min(60, 24 + (int) (threat * 3.0));
        }

        /**
         * Corridors with a pit in them: three by three of bronze trapdoors across the middle, over iron spikes, that
         * spring open a moment after somebody steps on them. Never where there are jets, never by the arrival room.
         */
        public boolean pit(Cell c, int dir) {
            Cell other = dir == 0 ? new Cell(c.x + 1, c.z) : new Cell(c.x, c.z + 1);
            if (other.x >= size || other.z >= size || !connected(c, other) || c.equals(start) || other.equals(start)) return false;
            if (run(c, dir) != RUN_STRAIGHT || jets(c, dir)) return false;
            long h = seed * 53 + c.x * 15485863L + c.z * 7919L + dir * 104729L;
            h ^= h >>> 19;
            h *= 0xD6E8FEB86659FD93L;
            return Math.floorMod(h >>> 21, 100) < Math.min(40, 16 + (int) (threat * 2.0));
        }

        /** What the vents of a corridor with jets let out: the same in one corridor always, different from corridor to corridor. */
        public int jetKind(Cell c, int dir) {
            long h = seed * 17 + c.x * 104729L + c.z * 15485863L + dir * 7919L;
            h ^= h >>> 13;
            h *= 0xC2B2AE3D27D4EB4FL;
            return (int) Math.floorMod(h >>> 22, 4L);
        }

        /** The middle of the corridor east or south of a room. */
        public BlockPos corridorMiddle(Cell c, int dir) {
            int d = (CORRIDOR_FROM + CORRIDOR_TO) / 2;
            return dir == 0 ? centre(c).offset(d, 0, 0) : centre(c).offset(0, 0, d);
        }

        /** True if the gate stands north-south, facing east and west. */
        public boolean gateTurned() {
            return gateTurned;
        }

        /** A place in the gate's own plane: {@code along} blocks to one side of its middle, {@code up} above the floor. */
        public BlockPos gateOffset(int along, int up) {
            BlockPos c = centre(exit);
            return gateTurned ? c.offset(0, up, along) : c.offset(along, up, 0);
        }

        /** The doorway the gate faces, as a step from the exit room: the way a climber comes in. */
        public Cell approach() {
            Map<Cell, Integer> dist = distances();
            Cell way = null;
            for (Cell n : neighbours(exit)) if (connected(exit, n) && (way == null || dist.get(n) < dist.get(way))) way = n;
            return way;
        }

        /** Which cell a position is in, or null when it is inside a wall or a doorway. */
        public Cell cellAt(BlockPos pos) {
            BlockPos o = origin();
            int dx = pos.getX() - o.getX(), dz = pos.getZ() - o.getZ();
            if (dx < 0 || dz < 0 || dx >= width() || dz >= width()) return null;
            int ox = dx % CELL, oz = dz % CELL;
            Cell cell = new Cell(dx / CELL, dz / CELL);
            // (a wide room reaches one block further each way, a small one a block less)
            int reach = wide(cell) ? 1 : small(cell) ? -1 : 0;
            if (ox < 1 - reach || ox > ROOM + reach || oz < 1 - reach || oz > ROOM + reach) return null;
            return cell;
        }

        public AABB cellBox(Cell c) {
            BlockPos m = centre(c);
            int r = half(c);
            return new AABB(m.getX() - r, y(), m.getZ() - r, m.getX() + r + 1, y() + height(c) + 2, m.getZ() + r + 1);
        }

        public AABB box() {
            BlockPos o = origin();
            // (one block more on the low sides: a wide room in the first row or column still has a wall outside it)
            return new AABB(o.getX() - 1, y() - 2, o.getZ() - 1, o.getX() + width(), y() + LEVEL_PITCH - 2, o.getZ() + width());
        }
    }

    private static final Map<String, Plan> PLANS = new HashMap<>();

    public static Plan plan(long worldSeed, Line line, int index, int stage) {
        return PLANS.computeIfAbsent(worldSeed + "/" + line.id() + "/" + index + "/" + stage, k -> new Plan(worldSeed, line, index, stage));
    }

    /** The first level of a passage. */
    public static Plan plan(long worldSeed, Line line, int index) {
        return plan(worldSeed, line, index, 0);
    }

    public static Plan plan(ServerLevel level, Line line, int index, int stage) {
        return plan(level.getSeed(), line, index, stage);
    }

    public static Plan plan(ServerLevel level, Line line, int index) {
        return plan(level.getSeed(), line, index, 0);
    }

    public static Plan plan(ServerLevel level, Spot spot) {
        return plan(level.getSeed(), spot.line(), spot.index(), spot.stage());
    }

    // ---- where things are ------------------------------------------------------------------------------------

    /** Which passage a spot in a tower's dimension belongs to, or 0 for the tower itself. */
    public static int indexAt(double x) {
        int index = (int) Math.floor((x - X0 + 40) / PITCH);
        if (index < 1 || index > COUNT) return 0;
        double from = X0 + index * PITCH;
        return x >= from - 8 && x <= from + MAX_SIZE * CELL + 8 ? index : 0;
    }

    /** Which level of a passage a height belongs to. */
    public static int stageAt(ServerLevel level, Line line, int index, double y) {
        return Math.max(0, Math.min(stages(level, line, index) - 1, Math.floorDiv((int) Math.floor(y) - (Y - 2), LEVEL_PITCH)));
    }

    /** The passage and level a player is standing in, or null. */
    public static Spot locate(ServerPlayer player) {
        Realm realm = Realm.of(player.level().dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(player.getX());
        return index == 0 || !(player.level() instanceof ServerLevel level) ? null : new Spot(realm.line(), index, stageAt(level, realm.line(), index, player.getY()));
    }

    /** True if a player in a passage has got outside the level they are on: fallen out, or risen through a ceiling. */
    public static boolean outside(ServerPlayer player, Spot spot) {
        int y = Y + spot.stage() * LEVEL_PITCH;
        return player.getY() < Y - 6 || player.getY() > Y + MAX_STAGES * LEVEL_PITCH + 6
                || player.getY() < y - 1 || player.getY() > y + LEVEL_PITCH - 3;
    }

    private static String builtKey(Line line, int index, int stage) {
        return "passage/" + line.id() + "/" + index + "/" + stage + "@" + LAYOUT_VERSION;
    }

    private static String builtKey(Plan plan) {
        return builtKey(plan.line, plan.index, plan.stage);
    }

    public static boolean isBuilt(ServerLevel level, Line line, int index, int stage) {
        return ProgressData.get(level.getServer()).isBuilt(builtKey(line, index, stage));
    }

    // ---- building --------------------------------------------------------------------------------------------

    /** Builds one level of a passage if it is not standing yet. Levels are built one at a time, as they are reached. */
    public static void ensure(ServerLevel level, Line line, int index, int stage) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = builtKey(line, index, stage);
        if (data.isBuilt(key)) return;
        Job job = JOBS.remove(key);
        if (job != null) {
            // it was being built ahead: only what is left has to be done now
            while (!job.steps.isEmpty()) job.steps.poll().run();
            levelsBuilt++;
        } else {
            build(level, plan(level, line, index, stage));
        }
        data.markBuilt(key);
    }

    // ---- building ahead --------------------------------------------------------------------------------------

    /** A level being built a little at a time, so that nobody has to wait for it at the gate. */
    private static final class Job {
        final java.util.ArrayDeque<Runnable> steps;

        Job(java.util.ArrayDeque<Runnable> steps) {
            this.steps = steps;
        }
    }

    private static final Map<String, Job> JOBS = new java.util.LinkedHashMap<>();
    /** How much of a tick building ahead may take, in nanoseconds. */
    public static final long BUDGET_NANOS = 4_000_000L;
    /** How long the last level built in one go took, and how many levels have been built since the server started. */
    public static long lastBuildMillis;
    public static int levelsBuilt;

    /**
     * Starts building a level in the background, a few milliseconds a tick. Called for the level after the one a
     * player has just reached, and for a passage's first level when the trial below it begins.
     * @return true if a job was started
     */
    public static boolean prepare(ServerLevel level, Line line, int index, int stage) {
        if (index < 1 || index > COUNT || stage < 0 || stage >= stages(level, line, index)) return false;
        String key = builtKey(line, index, stage);
        if (ProgressData.get(level.getServer()).isBuilt(key) || JOBS.containsKey(key)) return false;
        JOBS.put(key, new Job(steps(level, plan(level, line, index, stage))));
        return true;
    }

    public static int pending() {
        return JOBS.size();
    }

    /** Called every server tick: a few milliseconds' worth of whatever is being built ahead. */
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        long until = System.nanoTime() + BUDGET_NANOS;
        var it = JOBS.entrySet().iterator();
        var entry = it.next();
        Job job = entry.getValue();
        while (!job.steps.isEmpty() && System.nanoTime() < until) job.steps.poll().run();
        if (job.steps.isEmpty()) {
            ProgressData.get(server).markBuilt(entry.getKey());
            it.remove();
            levelsBuilt++;
        }
    }

    public static void ensure(ServerLevel level, Line line, int index) {
        ensure(level, line, index, 0);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    /** Builds the whole level at once: a solid block of wall, carved into rooms and halls, then furnished. */
    public static void build(ServerLevel level, Plan plan) {
        long began = System.nanoTime();
        JOBS.remove(builtKey(plan));
        for (Runnable step : steps(level, plan)) step.run();
        lastBuildMillis = (System.nanoTime() - began) / 1_000_000L;
        levelsBuilt++;
    }

    /**
     * Everything building a level takes, cut into small pieces that can be done one after another on different
     * ticks: the solid block four columns at a time, then each room carved, the doorways, and each room furnished.
     */
    private static java.util.ArrayDeque<Runnable> steps(ServerLevel level, Plan plan) {
        java.util.ArrayDeque<Runnable> steps = new java.util.ArrayDeque<>();
        TowerBuilder.Palette p = plan.palette();
        Random random = new Random(plan.seed * 31 + 7);
        BlockPos o = plan.origin();
        int w = plan.width();
        // stale sentinels of an earlier layout go first
        steps.add(() -> {
            // (not reaching up or down: the levels above and below keep theirs)
            for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2, 0, 2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        });
        for (int from = -1; from < w; from += 4) {
            final int x0 = from;
            steps.add(() -> {
                BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
                for (int x = x0; x < Math.min(w, x0 + 4); x++) {
                    for (int z = -1; z < w; z++) {
                        for (int y = -2; y <= LEVEL_PITCH - 3; y++) {
                            Block block = y <= 0 || y >= TALL + 1 ? p.fill() : Mason.weather(p.wall(), o.getX() + x, o.getY() + y, o.getZ() + z, 14);
                            at.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                            // (what already stands is left alone: a rebuild only pays for what changed)
                            if (!level.getBlockState(at).is(block)) level.setBlock(at, block.defaultBlockState(), 2);
                        }
                    }
                }
            });
        }
        for (Cell c : plan.cells()) steps.add(() -> carve(level, plan, c, p));
        // doorways: three wide, three high, through the wall between two cells, under a lintel of trim
        steps.add(() -> {
            for (Cell c : plan.cells()) {
                BlockPos m = plan.centre(c);
                Cell e = new Cell(c.x + 1, c.z), s = new Cell(c.x, c.z + 1);
                int mid = (CORRIDOR_FROM + CORRIDOR_TO) / 2;
                for (int dir = 0; dir < 2; dir++) {
                    Cell next = dir == 0 ? e : s;
                    if ((dir == 0 ? c.x : c.z) + 1 >= plan.size || !plan.connected(c, next)) continue;
                    boolean jets = plan.jets(c, dir);
                    // (a small room's wall is a block nearer its middle: the corridor is a block longer at that end)
                    for (int d = CORRIDOR_FROM - (plan.small(c) ? 1 : 0); d <= CORRIDOR_TO + (plan.small(next) ? 1 : 0); d++) {
                        for (int k = -1; k <= 1; k++) {
                            BlockPos floor = dir == 0 ? m.offset(d, 0, k) : m.offset(k, 0, d);
                            doorway(level, floor, p);
                            // a runner down the middle, a lamp over the middle of the corridor, an arch at either end
                            // (one step in from each room: the doorway itself stays a clean three by three, for rooms that shut theirs)
                            if (k != 0 && (d == CORRIDOR_FROM + 1 || d == CORRIDOR_TO - 1)) {
                                // the arch's haunches: stairs hung from the lintel, their backs to the walls
                                net.minecraft.core.Direction side = dir == 0 ? (k < 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH)
                                        : (k < 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST);
                                level.setBlock(floor.above(3), Mason.stair(Mason.of(p.wall()).stairs(), side, true), 2);
                            }
                            if (k != 0) set(level, floor, p.top());
                            if (d == mid && k == 0) set(level, floor.above(4), p.light());
                            if (d == mid && jets) set(level, floor, Snares.vent());
                        }
                        // and a sconce in each wall, a third of the way along
                        if (d == CORRIDOR_FROM + 2 || d == CORRIDOR_TO - 2) {
                            set(level, dir == 0 ? m.offset(d, 2, -2) : m.offset(-2, 2, d), p.light());
                            set(level, dir == 0 ? m.offset(d, 2, 2) : m.offset(2, 2, d), p.light());
                        }
                    }
                    wind(level, plan, c, dir, p);
                    if (plan.pit(c, dir)) Snares.layPit(level, plan, c, dir);
                    // a special room is entered through double doors of bronze, thrown open against the walls
                    if (entrance(plan.kind(c))) doors(level, plan, c, dir, CORRIDOR_FROM + 1 - (plan.small(c) ? 1 : 0), door(plan, plan.kind(c)));
                    if (entrance(plan.kind(next))) doors(level, plan, c, dir, CORRIDOR_TO - 1 + (plan.small(next) ? 1 : 0), door(plan, plan.kind(next)));
                    // a puzzle room is shut behind a sliding door of its tower's stone, that glides into the wall when used
                    if (sliding(plan.kind(c))) slidingDoor(level, plan, c, dir, CORRIDOR_FROM + 1 - (plan.small(c) ? 1 : 0), slider(plan.line));
                    if (sliding(plan.kind(next))) slidingDoor(level, plan, c, dir, CORRIDOR_TO - 1 + (plan.small(next) ? 1 : 0), slider(plan.line));
                }
            }
        });
        for (Cell c : plan.cells()) steps.add(() -> furnish(level, plan, c, p, random));
        // the connecting blocks are joined once everything stands
        steps.add(() -> {
            com.thefarawaydev.realmsofchaos.block.ConnectedBlock.settle(level, o.offset(-1, -2, -1), o.offset(w, LEVEL_PITCH - 3, w));
            com.thefarawaydev.realmsofchaos.block.WeaponRackBlock.settle(level, o.offset(-1, -2, -1), o.offset(w, LEVEL_PITCH - 3, w));
            com.thefarawaydev.realmsofchaos.block.Furniture.Joining.settle(level, o.offset(-1, -2, -1), o.offset(w, LEVEL_PITCH - 3, w));
            settleCaches(level, plan);
        });
        return steps;
    }

    /** A place in a corridor: {@code d} blocks along it from the room it leaves, {@code k} to one side. */
    public static BlockPos along(Plan plan, Cell c, int dir, int d, int k, int up) {
        BlockPos m = plan.centre(c);
        return dir == 0 ? m.offset(d, up, k) : m.offset(k, up, d);
    }

    /**
     * Makes a corridor wind. A bend opens a bay to one side of the corridor's middle and stands a baffle across the
     * straight way, so the corridor goes round it and nothing ahead can be seen; a split does it on both sides. A
     * nook is a bay off a straight corridor, with an urn at the back of it.
     */
    private static void wind(ServerLevel level, Plan plan, Cell c, int dir, TowerBuilder.Palette p) {
        int run = plan.run(c, dir), mid = (CORRIDOR_FROM + CORRIDOR_TO) / 2;
        if (run == RUN_STRAIGHT) return;
        if (run == RUN_GALLERY || run == RUN_CAUSEWAY) {
            // a hall rather than a passage: seven or nine across and four high, lit from its ceiling. A gallery has a
            // pillar in each corner and a light in the floor at either side; a causeway is a walk three wide with
            // water lying to both sides of it
            int reach = run == RUN_GALLERY ? 3 : 4;
            for (int d = mid - 1; d <= mid + 1; d++) {
                for (int k = -reach; k <= reach; k++) {
                    boolean walk = Math.abs(k) <= 1;
                    set(level, along(plan, c, dir, d, k, 0), walk ? p.inlay() : run == RUN_CAUSEWAY ? Blocks.WATER : p.top());
                    for (int y = 1; y <= 4; y++) set(level, along(plan, c, dir, d, k, y), Blocks.AIR);
                    set(level, along(plan, c, dir, d, k, 5), d == mid && (k == 0 || Math.abs(k) == reach - 1) ? p.light() : p.trim());
                }
            }
            if (run == RUN_GALLERY) {
                for (int d = mid - 1; d <= mid + 1; d += 2) {
                    for (int k = -reach; k <= reach; k += reach * 2) {
                        for (int y = 1; y <= 4; y++) set(level, along(plan, c, dir, d, k, y), column(plan.line));
                    }
                }
                set(level, along(plan, c, dir, mid, -reach, 0), p.light());
                set(level, along(plan, c, dir, mid, reach, 0), p.light());
            }
            return;
        }
        boolean low = run == RUN_BEND_LOW || run == RUN_SPLIT || run == RUN_NOOK_LOW, high = run == RUN_BEND_HIGH || run == RUN_SPLIT || run == RUN_NOOK_HIGH;
        for (int d = mid - 1; d <= mid + 1; d++) {
            for (int k = 2; k <= 4; k++) {
                if (low) doorway(level, along(plan, c, dir, d, -k, 0), p);
                if (high) doorway(level, along(plan, c, dir, d, k, 0), p);
            }
        }
        if (run == RUN_NOOK_LOW || run == RUN_NOOK_HIGH) {
            int side = low ? -1 : 1;
            set(level, along(plan, c, dir, mid, side * 4, 1), ModContent.ANCIENT_URN.get());
            set(level, along(plan, c, dir, mid, side * 3, 0), p.light());
            return;
        }
        // the baffle: a slab of wall across the straight way, with a lamp let into each face
        for (int k = -1; k <= 1; k++) {
            for (int y = 1; y <= 3; y++) set(level, along(plan, c, dir, mid, k, y), k == 0 && y == 2 ? p.light() : p.wall());
        }
        for (int side = -1; side <= 1; side += 2) {
            if (side < 0 ? low : high) set(level, along(plan, c, dir, mid, side * 3, 0), p.light());
        }
    }

    private static void doorway(ServerLevel level, BlockPos floor, TowerBuilder.Palette p) {
        set(level, floor, p.inlay());
        for (int y = 1; y <= 3; y++) set(level, floor.above(y), Blocks.AIR);
        set(level, floor.above(4), p.trim());
    }

    /** The steps a tower's stairs are made of. */
    static BlockState stairs(Line line, net.minecraft.core.Direction rising) {
        Block block = line == Line.ZEUS ? Blocks.QUARTZ_STAIRS : line == Line.POSEIDON ? Blocks.PRISMARINE_BRICK_STAIRS : Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, rising);
    }

    /** Patches where the floor's other stone shows through: blotches two or three blocks across, the same each build. */
    static boolean wear(int x, int z) {
        double v = 0;
        for (int k = 0; k < 2; k++) {
            int s = k == 0 ? 3 : 2, gx = Math.floorDiv(x, s), gz = Math.floorDiv(z, s);
            long h = gx * 3129871L ^ gz * 116129781L ^ (k + 7) * 42317861L;
            h = h * h * 42317861L + h * 11L;
            v += (Math.floorMod(h >> 16, 1000L) / 1000.0) * (k == 0 ? 0.65 : 0.35);
        }
        return v > 0.58;
    }

    /** True where a room with cut corners keeps its wall: for the furnishers. */
    static boolean cutAt(Plan plan, Cell c, int dx, int dz) {
        return cut(plan, c, dx, dz);
    }

    /** True where a room with cut corners keeps its wall. */
    private static boolean cut(Plan plan, Cell c, int dx, int dz) {
        return plan.form(c) == FORM_CUT_CORNERS && Math.abs(dx) + Math.abs(dz) > 2 * plan.half(c) - 2;
    }

    private static void carve(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p) {
        BlockPos m = plan.centre(c);
        boolean hall = plan.kind(c) == Kind.HALL;
        int r = plan.half(c), h = plan.height(c);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (hall && !inHall(plan, c, dx, dz)) continue;
                if (cut(plan, c, dx, dz)) continue;
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                // a border band round the room, and within it worn patches of the other stone, never a plain chequer
                set(level, m.offset(dx, 0, dz), hall ? p.top() : edge ? p.inlay() : wear(m.getX() + dx, m.getZ() + dz) ? p.topAlt() : p.top());
                for (int y = 1; y <= h; y++) set(level, m.offset(dx, y, dz), Blocks.AIR);
            }
        }
        if (hall) {
            set(level, m.above(HEADROOM + 1), p.light());
            return;
        }
        if (h <= HEADROOM + 1) {
            // a low room has a shallow dome over its middle, lit from its corners,
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) set(level, m.offset(dx, h + 1, dz), Blocks.AIR);
            }
            for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {0, 0}}) set(level, m.offset(k[0], h + 2, k[1]), p.light());
            for (int d = -3; d <= 3; d++) {
                for (int[] k : new int[][]{{d, -3}, {d, 3}, {-3, d}, {3, d}}) set(level, m.offset(k[0], h + 1, k[1]), p.trim());
            }
        } else {
            // a tall one has a flat ceiling coffered in trim, with lights let into it
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    if (ring == r - 1 || ring == 1) set(level, m.offset(dx, h + 1, dz), p.trim());
                    if ((Math.abs(dx) == 3 && Math.abs(dz) == 3) || (dx == 0 && dz == 0)) set(level, m.offset(dx, h + 1, dz), p.light());
                }
            }
        }
        if (h > HEADROOM + 1) {
            // and lamps hung from it on chains, each tower's own kind
            Block lamp = plan.line == Line.ZEUS ? Blocks.LANTERN : plan.line == Line.POSEIDON ? Blocks.SEA_LANTERN : Blocks.SOUL_LANTERN;
            for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                if (plan.kind(c) == Kind.STAIRWELL && k[1] < 0) continue;      // (the loft is in the way)
                for (int y = h; y > 6; y--) set(level, m.offset(k[0], y, k[1]), Blocks.IRON_CHAIN);
                BlockState hung = lamp.defaultBlockState();
                if (hung.hasProperty(net.minecraft.world.level.block.LanternBlock.HANGING)) hung = hung.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
                level.setBlock(m.offset(k[0], 6, k[1]), hung, 2);
            }
        }
        // a tall room's walls meet its ceiling in a cornice of hanging stairs
        if (h > HEADROOM + 1) {
            Block cornice = Mason.of(p.wall()).stairs();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || cut(plan, c, dx, dz)) continue;
                    BlockPos at = m.offset(dx, h, dz);
                    if (level.getBlockState(at).isAir()) level.setBlock(at, Mason.stair(cornice, Mason.outward(dx, dz), true), 2);
                }
            }
        }
        // a carved relief of the tower's story either side of the middle of each wall, at eye height
        Block relief = relief(plan.line);
        for (int d : new int[]{-2, 2}) {
            for (int[] k : new int[][]{{d, -(r + 1)}, {d, r + 1}, {-(r + 1), d}, {r + 1, d}}) {
                BlockPos at = m.offset(k[0], 2, k[1]);
                if (!level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolidRender()) set(level, at, relief);
            }
        }
        // the tower's brick in the walls' face between the band and the ceiling of a tall room
        if (h > HEADROOM + 1) {
            for (int d = -r; d <= r; d++) {
                for (int[] k : new int[][]{{d, -(r + 1)}, {d, r + 1}, {-(r + 1), d}, {r + 1, d}}) {
                    for (int y = 5; y < h; y++) {
                        BlockPos at = m.offset(k[0], y, k[1]);
                        if (level.getBlockState(at).is(p.wall())) set(level, at, brick(plan.line));
                    }
                }
            }
        }
        // a band of trim round its walls, and sconces set into them; a tall room has a second band under its ceiling
        int wall = r + 1;
        for (int d = -r; d <= r; d++) {
            for (int[] k : new int[][]{{d, -wall}, {d, wall}, {-wall, d}, {wall, d}}) {
                set(level, m.offset(k[0], 4, k[1]), p.trim());
                if (Math.abs(d) == 3) set(level, m.offset(k[0], 3, k[1]), p.light());
                if (h > HEADROOM + 1) {
                    set(level, m.offset(k[0], h, k[1]), p.trim());
                    if (Math.abs(d) == 2 || d == 0) set(level, m.offset(k[0], h - 1, k[1]), Math.abs(d) == 2 ? p.light() : p.glass());
                }
            }
        }
    }

    /** The doors a room is entered by: bronze for the working rooms, the tower's own for the rest. */
    static Block door(Plan plan, Kind kind) {
        if (kind == Kind.STORE || kind == Kind.ARMORY) return ModContent.BRONZE_DOOR.get();
        if (kind == Kind.BARRACKS || kind == Kind.WAR_ROOM) return ModContent.GARRISON_DOOR.get();
        if (kind == Kind.SMITHY) return ModContent.BRONZE_DOOR.get();
        return switch (plan.line) {
            case ZEUS -> ModContent.SKY_DOOR.get();
            case POSEIDON -> ModContent.SEA_DOOR.get();
            case HADES -> ModContent.SHADE_DOOR.get();
        };
    }

    /** A tower's own trapdoor. */
    public static Block trapdoor(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.SKY_TRAPDOOR.get();
            case POSEIDON -> ModContent.SEA_TRAPDOOR.get();
            case HADES -> ModContent.SHADE_TRAPDOOR.get();
        };
    }

    /** The rooms entered through a pair of doors. */
    static boolean entrance(Kind kind) {
        return kind == Kind.STORE || kind == Kind.ARMORY || kind == Kind.LIBRARY || kind == Kind.SANCTUM || kind == Kind.OBSERVATORY
                || kind == Kind.SHRINE || kind == Kind.BATHS || kind == Kind.BARRACKS || kind == Kind.SMITHY || kind == Kind.WAR_ROOM;
    }

    /** The rooms shut behind a sliding door: the puzzle rooms. */
    public static boolean sliding(Kind kind) {
        return kind == Kind.VAULT || kind == Kind.CHIMES || kind == Kind.BEAM || kind == Kind.PLATES || kind == Kind.SHUTTERS
                || kind == Kind.DIALS || kind == Kind.SLIDES;
    }

    /** A tower's own sliding door. */
    public static Block slider(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.SKY_SLIDING_DOOR.get();
            case POSEIDON -> ModContent.SEA_SLIDING_DOOR.get();
            case HADES -> ModContent.SHADE_SLIDING_DOOR.get();
        };
    }

    /** The side of the corridor a sliding door {@code d} along it slides into (1 or -1), or 0 if neither wall is solid enough to take it. */
    public static int pocketSide(ServerLevel level, Plan plan, Cell c, int dir, int d) {
        for (int side = 1; side >= -1; side -= 2) {
            boolean ok = true;
            // the pocket and the stone round it: three deep into the wall, three high, walled in front, behind, above and below
            for (int k = 2; k <= 5 && ok; k++) {
                for (int y = 0; y <= 4 && ok; y++) {
                    for (int dd = -1; dd <= 1 && ok; dd++) {
                        boolean inside = k <= 4 && y >= 1 && y <= 3 && dd == 0;
                        BlockState s = level.getBlockState(along(plan, c, dir, d + dd, k * side, y));
                        if (!inside && (s.isAir() || s.canBeReplaced())) ok = false;
                        if (inside && s.isAir()) ok = false;
                    }
                }
            }
            if (ok) return side;
        }
        return 0;
    }

    /**
     * A sliding door three wide and three high across a corridor, {@code d} along it, shut; and the pocket in the wall
     * beside it that it slides into. Where neither wall is solid enough to hide a pocket, there is no door.
     */
    static void slidingDoor(ServerLevel level, Plan plan, Cell c, int dir, int d, Block door) {
        int side = pocketSide(level, plan, c, dir, d);
        if (side == 0) return;
        net.minecraft.core.Direction across = dir == 0 ? (side > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH)
                : (side > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST);
        BlockState panel = door.defaultBlockState().setValue(com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock.FACING, across.getCounterClockWise());
        for (int y = 1; y <= 3; y++) {
            for (int k = -1; k <= 1; k++) level.setBlock(along(plan, c, dir, d, k, y), panel, 2 | 16);
            for (int k = 2; k <= 4; k++) level.setBlock(along(plan, c, dir, d, k * side, y), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        // each panel joined to those round it: one door with one frame round it
        for (int y = 1; y <= 3; y++) for (int k = -1; k <= 1; k++) {
            BlockPos at = along(plan, c, dir, d, k, y);
            level.setBlock(at, com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock.joined(level, at, level.getBlockState(at)), 2 | 16);
        }
    }

    /** A pair of bronze doors either side of a corridor's middle line, {@code d} along it, open against the walls. */
    static void doors(ServerLevel level, Plan plan, Cell c, int dir, int d, Block leaf) {
        net.minecraft.core.Direction facing = dir == 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH;
        BlockState door = leaf.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true);
        for (int k = -1; k <= 1; k += 2) {
            BlockPos at = along(plan, c, dir, d, k, 1);
            // the hinge on whichever side lays the open leaf against this side's wall
            BlockState chosen = door;
            for (var hinge : net.minecraft.world.level.block.state.properties.DoorHingeSide.values()) {
                BlockState st = door.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, hinge);
                var box = st.getShape(level, at).bounds();
                double across = dir == 0 ? (box.minZ + box.maxZ) / 2 : (box.minX + box.maxX) / 2;
                if ((across - 0.5) * k > 0) chosen = st;
            }
            level.setBlock(at, chosen.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2 | 16);
            level.setBlock(at.above(), chosen.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2 | 16);
        }
    }

    /** Each tower's own mosaic (connecting) and garden hedge. */
    public static Block mosaic(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.SKY_MOSAIC.get();
            case POSEIDON -> ModContent.SEA_MOSAIC.get();
            case HADES -> ModContent.SHADE_MOSAIC.get();
        };
    }

    public static Block hedge(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.LAUREL_HEDGE.get();
            case POSEIDON -> ModContent.KELP_WEAVE.get();
            case HADES -> ModContent.ASPHODEL_BED.get();
        };
    }

    /** Each tower's own column (connecting), floor panel (connecting) and brick. */
    public static Block column(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.MARBLE_COLUMN.get();
            case POSEIDON -> ModContent.CORAL_COLUMN.get();
            case HADES -> ModContent.BONE_COLUMN.get();
        };
    }

    public static Block floorPanel(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.SUNSTONE_PANEL.get();
            case POSEIDON -> ModContent.PEARL_PANEL.get();
            case HADES -> ModContent.EMBER_PANEL.get();
        };
    }

    public static Block wallPanel(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.OLYMPIAN_PANEL.get();
            case POSEIDON -> ModContent.ABYSSAL_PANEL.get();
            case HADES -> ModContent.UNDERWORLD_PANEL.get();
        };
    }

    public static Block brick(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.STORM_BRICK.get();
            case POSEIDON -> ModContent.TIDE_BRICK.get();
            case HADES -> ModContent.ASH_BRICK.get();
        };
    }

    /** The frame a tower's gates stand in. */
    public static Block portalFrame(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.PORTAL_FRAME_SKY.get();
            case POSEIDON -> ModContent.PORTAL_FRAME_SEA.get();
            case HADES -> ModContent.PORTAL_FRAME_SHADE.get();
        };
    }

    /** The carved relief set into a tower's walls. */
    public static Block relief(Line line) {
        return switch (line) {
            case ZEUS -> ModContent.SKY_RELIEF.get();
            case POSEIDON -> ModContent.SEA_RELIEF.get();
            case HADES -> ModContent.SHADE_RELIEF.get();
        };
    }

    /** A hall is not a room but corridors: a small crossing in the middle and an arm towards every doorway. */
    private static boolean inHall(Plan plan, Cell c, int dx, int dz) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return true;
        if (Math.abs(dz) <= 1) {
            Cell side = new Cell(c.x + (dx > 0 ? 1 : -1), c.z);
            return side.x >= 0 && side.x < plan.size && plan.connected(c, side);
        }
        if (Math.abs(dx) <= 1) {
            Cell side = new Cell(c.x, c.z + (dz > 0 ? 1 : -1));
            return side.z >= 0 && side.z < plan.size && plan.connected(c, side);
        }
        return false;
    }

    /** True for the three-wide lanes that run door to door through the middle of a room: nothing solid goes there. */
    private static boolean lane(int dx, int dz) {
        return Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
    }

    private static final int[][] CORNERS = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};

    private static void furnish(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        switch (plan.kind(c)) {
            case START -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, m.offset(dx, 0, dz), p.inlay());
                set(level, m, ModContent.ARRIVAL_SIGIL.get());                  // where a climber arrives
            }
            case EXIT -> {
                // the gate stands across the way a climber comes in, in an arch of its own, lit at its feet
                for (BlockPos g : plan.gatePositions()) set(level, g, GATE_SHUT);
                for (int dy = 1; dy <= 4; dy++) {
                    set(level, plan.gateOffset(-2, dy), dy == 4 ? p.trim() : portalFrame(plan.line));
                    set(level, plan.gateOffset(2, dy), dy == 4 ? p.trim() : portalFrame(plan.line));
                }
                for (int a = -1; a <= 1; a++) set(level, plan.gateOffset(a, 4), p.trim());
                set(level, plan.gateOffset(0, 5), p.light());
                for (int a = -2; a <= 2; a++) set(level, plan.gateOffset(a, 0), Math.abs(a) == 2 ? p.light() : p.inlay());
            }
            case SEAL -> {
                set(level, m.above(), p.trim());
                set(level, plan.sealPos(c), ModContent.PASSAGE_SEAL.get());
                for (int[] k : CORNERS) {
                    for (int y = 1; y <= plan.height(c); y++) set(level, m.offset(k[0], y, k[1]), column(plan.line));
                }
                // a ring of inlay round the pedestal, and four rays out to the lanes
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if ((dx != 0 || dz != 0) && (Math.max(Math.abs(dx), Math.abs(dz)) == 1 || dx == 0 || dz == 0)) set(level, m.offset(dx, 0, dz), p.inlay());
                    }
                }
            }
            case TREASURE -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, m.offset(dx, 0, dz), p.inlay());
                set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
                for (int[] k : CORNERS) {
                    set(level, m.offset(k[0], 1, k[1]), p.trim());
                    set(level, m.offset(k[0], 2, k[1]), p.light());
                }
            }
            case ARENA -> {
                // a fighting floor: rings underfoot, a tall pillar in every corner, and the prize in the middle
                int reach = plan.half(c);
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        int ring = Math.max(Math.abs(dx), Math.abs(dz));
                        set(level, m.offset(dx, 0, dz), ring == reach - 1 || ring == 3 || ring == 1 ? p.inlay() : ring == 0 ? p.light() : p.top());
                    }
                }
                set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
                for (int[] k : CORNERS) {
                    int kx = k[0] * (reach - 1) / 3, kz = k[1] * (reach - 1) / 3;
                    for (int y = 1; y <= plan.height(c) - 1; y++) set(level, m.offset(kx, y, kz), y == 4 ? p.light() : p.pillar());
                    set(level, m.offset(kx, plan.height(c), kz), p.trim());
                }
            }
            case SHRINE -> {
                set(level, plan.featurePos(c), ModContent.PASSAGE_SHRINE.get());
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) set(level, m.offset(dx, 0, dz), p.inlay());
                    }
                }
                set(level, m, p.light());
                for (int[] k : CORNERS) set(level, m.offset(k[0], 1, k[1]), Blocks.FLOWERING_AZALEA);
                for (int[] k : CORNERS) set(level, m.offset(k[0], 0, k[1]), Blocks.MOSS_BLOCK);
            }
            case VAULT, CHIMES, BEAM, PLATES, DARTS, SHUTTERS, DIALS, SLIDES -> Rooms.build(level, plan, c, random);
            case SPIKES, FLAMES, BLADES, ARC, GAS -> Snares.build(level, plan, c, p);
            case GAUNTLET -> Gauntlets.build(level, plan, c, random);
            case CELLS -> {
                // four cells behind iron bars, one in each corner, each with a way in; what was kept in them is mostly gone
                List<BlockPos> bars = new ArrayList<>();
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        for (int d = 2; d <= 4; d++) {
                            for (int y = 1; y <= 3; y++) {
                                // (the gap at 3 on the side that faces east or west is the cell's door, two high)
                                if (!(d == 3 && y <= 2)) bars.add(m.offset(sx * 2, y, sz * d));
                                bars.add(m.offset(sx * d, y, sz * 2));
                            }
                        }
                        set(level, m.offset(sx * 4, 1, sz * 4), random.nextFloat() < 0.5F ? ModContent.ANCIENT_URN.get() : Blocks.SKELETON_SKULL);
                        set(level, m.offset(sx * 3, 0, sz * 3), p.light());
                    }
                }
                // (bars join the bars beside them, and the wall they run into)
                java.util.Set<BlockPos> barred = new java.util.HashSet<>(bars);
                for (BlockPos bar : bars) {
                    BlockState state = Blocks.IRON_BARS.defaultBlockState();
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos next = bar.relative(d);
                        boolean joins = barred.contains(next) || level.getBlockState(next).isSolidRender();
                        state = state.setValue(net.minecraft.world.level.block.CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d), joins);
                    }
                    level.setBlock(bar, state, 2);
                }
                for (int d = -1; d <= 1; d++) set(level, m.offset(d, 0, 0), p.inlay());
            }
            case STORE -> {
                // a storeroom: stacks in the corners, higher against the walls, and something worth finding among them
                Block[] goods = {Blocks.HAY_BLOCK, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.COMPOSTER, Blocks.BARREL};
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (lane(dx, dz) || random.nextFloat() > 0.5F) continue;
                        int high = Math.max(Math.abs(dx), Math.abs(dz)) == 4 ? 1 + random.nextInt(3) : 1;
                        for (int y = 1; y <= high; y++) set(level, m.offset(dx, y, dz), goods[random.nextInt(goods.length - (y > 1 ? 2 : 0))]);
                    }
                }
                set(level, m.offset(3, 0, 3), p.light());
                set(level, m.offset(-3, 0, -3), p.light());
            }
            case STAIRWELL -> buildStairwell(level, plan, c, p);
            case CRUSHER -> {
                // a crusher hall: its floor is striped row by row, so the row that is marked can be told from the next
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) set(level, m.offset(dx, 0, dz), (dz & 1) == 0 ? p.trim() : p.inlay());
                }
            }
            case TRAP -> {
                // a rune chamber: a rune stone on a pillar in each corner, and a floor that says "do not stand here"
                Traps.build(level, plan, c, p.pillar());
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                        set(level, m.offset(dx, 0, dz), ring ? p.inlay() : dx == 0 && dz == 0 ? p.light() : p.trim());
                    }
                }
            }
            case GARDEN -> {
                // moss underfoot, flowers and bushes in the four quarters; the lanes are left as paths
                Block[] flowers = {Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.SHORT_GRASS, Blocks.FERN};
                for (int dx = -plan.half(c); dx <= plan.half(c); dx++) {
                    for (int dz = -plan.half(c); dz <= plan.half(c); dz++) {
                        if (lane(dx, dz) || cut(plan, c, dx, dz)) continue;
                        set(level, m.offset(dx, 0, dz), Blocks.MOSS_BLOCK);
                        float roll = random.nextFloat();
                        if (roll < 0.35F) set(level, m.offset(dx, 1, dz), flowers[random.nextInt(flowers.length)]);
                        else if (roll < 0.42F) set(level, m.offset(dx, 1, dz), Blocks.MOSS_CARPET);
                    }
                }
                // in each corner a clipped hedge of the tower's own plant, two high
                for (int[] k : CORNERS) {
                    set(level, m.offset(k[0], 1, k[1]), hedge(plan.line));
                    set(level, m.offset(k[0], 2, k[1]), random.nextBoolean() ? hedge(plan.line) : Blocks.FLOWERING_AZALEA);
                }
            }
            case LIBRARY -> {
                // shelves along the walls between the doors, a carpet down the middle, a lectern to one side
                int far = plan.half(c), shelves = Math.min(plan.height(c) - 2, 5);
                for (int d = 2; d <= far; d++) {
                    for (int[] k : new int[][]{{d, -far}, {-d, -far}, {d, far}, {-d, far}, {-far, d}, {-far, -d}, {far, d}, {far, -d}}) {
                        if (cut(plan, c, k[0], k[1])) continue;
                        for (int y = 1; y <= shelves; y++) {
                            // (two walls of books, two of scrolls: a wall of scroll shelves is one case, framed round its outside)
                            boolean scrolls = Math.abs(k[1]) == far;
                            set(level, m.offset(k[0], y, k[1]), scrolls ? ModContent.SCROLL_SHELF.get()
                                    : y == 3 && random.nextFloat() < 0.3F ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
                        }
                    }
                }
                Block carpet = plan.line == Line.ZEUS ? Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.YELLOW)
                        : plan.line == Line.POSEIDON ? Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.CYAN) : Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.PURPLE);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, m.offset(dx, 1, dz), carpet);
                set(level, m.offset(2, 1, 2), Blocks.LECTERN);
                set(level, m.offset(-2, 1, -2), Blocks.ENCHANTING_TABLE);
            }
            case POOL -> {
                // four shallow pools in the quarters, lit from below; the lanes between them are the way through
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (Math.abs(dx) < 2 || Math.abs(dz) < 2) continue;
                        set(level, m.offset(dx, -1, dz), Math.abs(dx) == Math.abs(dz) ? p.light() : p.fill());
                        set(level, m.offset(dx, 0, dz), Blocks.WATER);
                        if (random.nextFloat() < 0.2F) set(level, m.offset(dx, 1, dz), Blocks.LILY_PAD);
                    }
                }
            }
            case OBSERVATORY -> {
                // a vault of stars over a floor of the tower's mosaic; a celestial globe on a bronze stand in each of two
                // corners, a lectern of star charts in the others
                int far = plan.half(c), h = plan.height(c);
                for (int dx = -far; dx <= far; dx++) {
                    for (int dz = -far; dz <= far; dz++) {
                        if (cut(plan, c, dx, dz)) continue;
                        set(level, m.offset(dx, h + 1, dz), ModContent.STAR_VAULT.get());
                        if (!lane(dx, dz)) set(level, m.offset(dx, 0, dz), mosaic(plan.line));
                    }
                }
                for (int i = 0; i < CORNERS.length; i++) {
                    int[] k = CORNERS[i];
                    if (i % 2 == 0) {
                        set(level, m.offset(k[0], 1, k[1]), ModContent.BRONZE_GRATE.get());
                        set(level, m.offset(k[0], 2, k[1]), ModContent.CELESTIAL_GLOBE.get());
                    } else {
                        set(level, m.offset(k[0], 1, k[1]), Blocks.LECTERN);
                    }
                }
            }
            case ARMORY -> {
                // racks of arms along the walls between the doors, a bronze grate floor in the quarters, a grindstone
                // and an anvil where the work is done
                int far = plan.half(c);
                for (int d = 2; d <= far; d++) {
                    for (int[] k : new int[][]{{d, -far}, {-d, -far}, {d, far}, {-d, far}, {-far, d}, {-far, -d}, {far, d}, {far, -d}}) {
                        if (cut(plan, c, k[0], k[1])) continue;
                        // racks hung on the walls, facing into the room
                        net.minecraft.core.Direction into = Mason.outward(k[0], k[1]).getOpposite();
                        BlockState rack = ((com.thefarawaydev.realmsofchaos.block.WeaponRackBlock) ModContent.WEAPON_RACK.get()).facing(into);
                        for (int y = 1; y <= 2; y++) level.setBlock(m.offset(k[0], y, k[1]), rack, 2);
                    }
                }
                for (int dx = -far + 1; dx <= far - 1; dx++) {
                    for (int dz = -far + 1; dz <= far - 1; dz++) if (!lane(dx, dz) && !cut(plan, c, dx, dz)) set(level, m.offset(dx, 0, dz), ModContent.BRONZE_GRATE.get());
                }
                set(level, m.offset(3, 1, 3), Blocks.ANVIL);
                set(level, m.offset(-3, 1, -3), Blocks.GRINDSTONE);
                set(level, m.offset(3, 1, -3), Blocks.SMITHING_TABLE);
            }
            case BATHS -> {
                // four basins in the quarters, tiled, their water warm and lit from below; tiled ways between them
                for (int dx = -plan.half(c); dx <= plan.half(c); dx++) {
                    for (int dz = -plan.half(c); dz <= plan.half(c); dz++) {
                        if (cut(plan, c, dx, dz)) continue;
                        boolean basin = Math.abs(dx) >= 2 && Math.abs(dz) >= 2 && Math.abs(dx) <= 3 && Math.abs(dz) <= 3;
                        if (basin) {
                            set(level, m.offset(dx, -1, dz), (dx + dz & 1) == 0 ? p.light() : ModContent.BATH_TILE.get());
                            set(level, m.offset(dx, 0, dz), Blocks.WATER);
                        } else {
                            set(level, m.offset(dx, 0, dz), ModContent.BATH_TILE.get());
                        }
                    }
                }
                if (plan.half(c) >= 5) {
                    for (int[] k : CORNERS) set(level, m.offset(k[0] + Integer.signum(k[0]), 1, k[1] + Integer.signum(k[1])), Blocks.CAMPFIRE);
                }
            }
            case PROVING -> buildProving(level, plan, c, p, random);
            case BARRACKS -> buildBarracks(level, plan, c, p, random);
            case DUEL -> buildDuel(level, plan, c, p, random);
            case AMBUSH -> buildAmbush(level, plan, c, p, random);
            case OVERLOOK -> buildOverlook(level, plan, c, p, random);
            case SMITHY -> buildSmithy(level, plan, c, p, random);
            case CRYPT -> buildCrypt(level, plan, c, p, random);
            case WAR_ROOM -> buildWarRoom(level, plan, c, p, random);
            case SANCTUM -> {
                // a quiet room: its whole floor one framed mosaic of the tower, its walls lined in the tower's panels, a
                // brazier of the tower's fire in each corner, a chaos lamp hung over the middle
                int far = plan.half(c), h = plan.height(c);
                for (int dx = -far; dx <= far; dx++) {
                    for (int dz = -far; dz <= far; dz++) if (!cut(plan, c, dx, dz)) set(level, m.offset(dx, 0, dz), mosaic(plan.line));
                }
                for (int d = -far; d <= far; d++) {
                    for (int[] k : new int[][]{{d, -(far + 1)}, {d, far + 1}, {-(far + 1), d}, {far + 1, d}}) {
                        for (int y = 1; y <= 3; y++) {
                            BlockPos at = m.offset(k[0], y, k[1]);
                            if (!level.getBlockState(at).isAir()) set(level, at, wallPanel(plan.line));
                        }
                    }
                }
                for (int[] k : CORNERS) level.setBlock(m.offset(k[0], 1, k[1]), brazier(plan, com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.STANDING, null), 2);
                for (int y = h; y > h - 2; y--) set(level, m.offset(0, y, 0), Blocks.IRON_CHAIN);
                set(level, m.offset(0, h - 2, 0), ModContent.CHAOS_LAMP.get());
            }
            case ROOM -> {
                int variant = random.nextInt(4), reach = plan.half(c), tall = plan.height(c);
                if (plan.form(c) == FORM_COLONNADE) {
                    // columns all the way round, a step in from the walls, clear of the ways through
                    for (int a : new int[]{-(reach - 1), reach - 1}) {
                        for (int b : new int[]{-(reach - 1), -2, 2, reach - 1}) {
                            for (int y = 1; y <= tall; y++) {
                                set(level, m.offset(a, y, b), y == 1 || y == tall ? p.trim() : column(plan.line));
                                set(level, m.offset(b, y, a), y == 1 || y == tall ? p.trim() : column(plan.line));
                            }
                        }
                    }
                    variant = 1;                    // and a medallion between them
                } else if (plan.form(c) == FORM_SUNKEN) {
                    // the middle of the floor is a step down, with stairs where the ways through cross its edge
                    int pit = reach - 2;
                    for (int dx = -pit; dx <= pit; dx++) {
                        for (int dz = -pit; dz <= pit; dz++) {
                            set(level, m.offset(dx, 0, dz), Blocks.AIR);
                            set(level, m.offset(dx, -1, dz), Math.max(Math.abs(dx), Math.abs(dz)) == pit ? p.trim() : ((dx + dz) & 1) == 0 ? p.inlay() : p.topAlt());
                        }
                    }
                    for (int k = -1; k <= 1; k++) {
                        level.setBlock(m.offset(pit, 0, k), stairs(plan.line, net.minecraft.core.Direction.EAST), 2);
                        level.setBlock(m.offset(-pit, 0, k), stairs(plan.line, net.minecraft.core.Direction.WEST), 2);
                        level.setBlock(m.offset(k, 0, pit), stairs(plan.line, net.minecraft.core.Direction.SOUTH), 2);
                        level.setBlock(m.offset(k, 0, -pit), stairs(plan.line, net.minecraft.core.Direction.NORTH), 2);
                    }
                    set(level, m.below(), p.light());
                    variant = -1;
                }
                if (variant >= 0) {
                    // a medallion in the floor where the ways through cross
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int ring = Math.max(Math.abs(dx), Math.abs(dz));
                            set(level, m.offset(dx, 0, dz), ring == 2 ? p.inlay() : ring == 0 ? p.light() : p.trim());
                        }
                    }
                }
                // and, unless columns stand there, every quarter furnished as a whole
                Furnish.room(level, plan, c, p, random);
            }
            case HALL -> { }
        }
        extras(level, plan, c, random);
    }

    /** The small things that make a room a place: the tower's braziers, urns to smash, a tablet to read. */
    private static void extras(ServerLevel level, Plan plan, Cell c, Random random) {
        Kind kind = plan.kind(c);
        if (kind != Kind.ROOM && kind != Kind.GARDEN && kind != Kind.LIBRARY && kind != Kind.POOL && kind != Kind.ARENA && kind != Kind.SHRINE && kind != Kind.STORE
                && kind != Kind.OBSERVATORY && kind != Kind.ARMORY && kind != Kind.BATHS && kind != Kind.SANCTUM && kind != Kind.BARRACKS && kind != Kind.DUEL && kind != Kind.AMBUSH
                && kind != Kind.SMITHY && kind != Kind.CRYPT && kind != Kind.WAR_ROOM) return;
        BlockPos m = plan.centre(c);
        // against the walls, clear of the lanes that run door to door
        int w = plan.half(c);
        int[][] spots = {{w, 2}, {-w, -2}, {2, w}, {-2, -w}, {w, -2}, {-w, 2}, {-2, w}, {2, -w}};
        int from = random.nextInt(spots.length), urns = random.nextInt(3), braziers = random.nextInt(2);
        int tablets = kind == Kind.LIBRARY || random.nextFloat() < 0.15F ? 1 : 0;
        for (int i = 0; i < spots.length && urns + braziers + tablets > 0; i++) {
            BlockPos at = m.offset(spots[(from + i) % spots.length][0], 1, spots[(from + i) % spots.length][1]);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isCollisionShapeFullBlock(level, at.below())) continue;
            if (braziers > 0) {
                braziers--;
                level.setBlock(at, brazier(plan, com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.STANDING, null), 2);
            } else if (tablets > 0) {
                tablets--;
                set(level, at, ModContent.LORE_TABLET.get());
            } else {
                urns--;
                set(level, at, ModContent.ANCIENT_URN.get());
            }
        }
    }

    /** A tower's brazier, mounted a given way: {@code facing} is the way a wall brazier faces out from its wall. */
    static BlockState brazier(Plan plan, com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount mount, net.minecraft.core.Direction facing) {
        return ((com.thefarawaydev.realmsofchaos.block.BrazierBlock) ModContent.TOWER_BRAZIER.get())
                .of(plan.line.ordinal(), mount, facing == null ? net.minecraft.core.Direction.NORTH : facing);
    }

    /** Hangs braziers on a room's two side walls, a third of the way along each, facing in. */
    private static void sideBraziers(ServerLevel level, Plan plan, Cell c) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int dz = -3; dz <= 3; dz += 6) {
                if (cut(plan, c, sx * far, dz)) continue;
                BlockPos at = m.offset(sx * far, 3, dz);
                if (level.getBlockState(at).isAir()) level.setBlock(at, brazier(plan, com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.WALL,
                        sx > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST), 2);
            }
        }
    }

    /**
     * A smithy: a hearth of brick in one quarter with its fires burning in two bowls, an anvil, a grindstone and a
     * smithing table in the next, a cauldron of water to quench in; ore and coal heaped in a third, racks of finished
     * arms in the fourth. Braziers on the walls.
     */
    private static void buildSmithy(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        var FLOOR = com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.FLOOR;
        // the hearth, two by two, its fires on top; a hood of brick stairs over it
        for (int dx = 2; dx <= 3; dx++) {
            for (int dz = 2; dz <= 3; dz++) {
                set(level, m.offset(dx, 1, dz), Blocks.BRICKS);
                level.setBlock(m.offset(dx, 2, dz), (dx + dz) % 2 == 0 ? brazier(plan, FLOOR, null) : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int dx = 2; dx <= 3; dx++) for (int dz = 2; dz <= 3; dz++) set(level, m.offset(dx, 4, dz), Blocks.BRICKS);
        for (int y = 5; y <= plan.height(c); y++) set(level, m.offset(3, y, 3), Blocks.BRICKS);
        set(level, m.offset(4, 1, 4), Blocks.BARREL);
        // the anvil and its tools
        set(level, m.offset(3, 1, -3), Blocks.ANVIL);
        level.setBlock(m.offset(2, 1, -2), Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), 2);
        set(level, m.offset(4, 1, -2), Blocks.GRINDSTONE);
        set(level, m.offset(2, 1, -4), Blocks.SMITHING_TABLE);
        // ore and coal, heaped
        Block[] heap = {Blocks.COAL_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.COAL_BLOCK, Blocks.RAW_COPPER_BLOCK};
        int n = 0;
        for (int dx = -4; dx <= -2; dx++) for (int dz = -4; dz <= -2; dz++) if (Math.max(-dx, -dz) >= 3 && dx >= -far && dz >= -far) set(level, m.offset(dx, 1, dz), heap[n++ % heap.length]);
        set(level, m.offset(-4, 2, -4), Blocks.COAL_BLOCK);
        // racks of finished arms on the walls of the last quarter
        var racks = (com.thefarawaydev.realmsofchaos.block.WeaponRackBlock) ModContent.WEAPON_RACK.get();
        for (int d = 2; d < far; d++) {
            if (!cut(plan, c, -far, d)) level.setBlock(m.offset(-far, 2, d), racks.facing(net.minecraft.core.Direction.EAST), 2);
            if (!cut(plan, c, -d, far)) level.setBlock(m.offset(-d, 2, far), racks.facing(net.minecraft.core.Direction.NORTH), 2);
        }
        sideBraziers(level, plan, c);
    }

    /**
     * A crypt: four stone coffins in its quarters, lidded, candles burning on them; a standing brazier in each corner,
     * cobwebs in the corners above.
     */
    private static void buildCrypt(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c), h = plan.height(c);
        Mason.Stone stone = Mason.of(p.wall());
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                // a coffin two long, its head to the wall
                for (int v = 2; v <= 3; v++) {
                    BlockPos at = m.offset(sx * 3, 1, sz * v);
                    set(level, at, p.wall());
                    level.setBlock(at.above(), Mason.slab(stone.slab(), false), 2);
                }
                level.setBlock(m.offset(sx * 3, 2, sz * 2).above(), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                        .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + random.nextInt(3)), 2);
                if (!cut(plan, c, sx * far, sz * far)) level.setBlock(m.offset(sx * far, 1, sz * far), brazier(plan, com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.STANDING, null), 2);
                set(level, m.offset(sx * far, h, sz * far), Blocks.COBWEB);
            }
        }
        // an aisle of the tower's inlay between them
        for (int d = -far; d <= far; d++) set(level, m.offset(0, 0, d), p.inlay());
    }

    /**
     * A war room: a long table across the middle of one half, chairs drawn up along both sides, a lectern at its head
     * for the orders; the tower's standards on plinths in the corners; braziers on the walls.
     */
    private static void buildWarRoom(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        for (int sz = -1; sz <= 1; sz += 2) {
            // a table in each half, clear of the lanes: three long, chairs facing it
            for (int dx = 2; dx <= 4; dx++) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    set(level, m.offset(sx * dx, 1, sz * 3), ModContent.TABLE.get());
                    level.setBlock(m.offset(sx * dx, 1, sz * 2), ModContent.CHAIR.get().defaultBlockState()
                            .setValue(com.thefarawaydev.realmsofchaos.block.Furniture.FACING, sz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH), 2);
                    if (far >= 5) level.setBlock(m.offset(sx * dx, 1, sz * 4), ModContent.CHAIR.get().defaultBlockState()
                            .setValue(com.thefarawaydev.realmsofchaos.block.Furniture.FACING, sz > 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH), 2);
                }
            }
            level.setBlock(m.offset(3, 2, sz * 3), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                    .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 2), 2);
            level.setBlock(m.offset(-3, 2, sz * 3), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true), 2);
        }
        // the tower's standards in the corners, on plinths
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (cut(plan, c, sx * far, sz * far)) continue;
                set(level, m.offset(sx * far, 1, sz * far), p.trim());
                level.setBlock(m.offset(sx * far, 2, sz * far), ModContent.WAR_STANDARD.get().defaultBlockState()
                        .setValue(com.thefarawaydev.realmsofchaos.block.WarStandardBlock.FACING, sx > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST), 2);
            }
        }
        set(level, m.offset(far, 1, 2), Blocks.LECTERN);
        sideBraziers(level, plan, c);
    }

    /** The stairwell's upper floor: how high it is, how far south the loft along the north wall comes, and where its
     *  side galleries run (along the east and west walls, two wide). */
    public static final int LOFT_Y = 4, LOFT_EDGE = -3, GALLERY_TO = 1;

    /**
     * A stairwell: a hall two storeys high. A straight flight of steps climbs each side wall from the south, northward,
     * to a gallery that runs along both side walls and across the north end - one railing along its inner edge, so
     * the whole upper floor can be seen from the hall. On the north side of it the cache stands between two braziers.
     * Below, two pillars carry the north end; the hall itself is left open.
     */
    private static void buildStairwell(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        Mason.Stone stone = Mason.of(p.wall());
        var N = net.minecraft.core.Direction.NORTH;
        var S = net.minecraft.core.Direction.SOUTH;
        var STANDING = com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.STANDING;
        var WALL = com.thefarawaydev.realmsofchaos.block.BrazierBlock.Mount.WALL;
        // ---- the upper floor: the loft across the north end, galleries two wide down each side
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= GALLERY_TO; dz++) {
                boolean loft = dz <= LOFT_EDGE, gallery = Math.abs(dx) >= far - 1;
                if (!loft && !gallery) continue;
                boolean edge = (loft && dz == LOFT_EDGE && Math.abs(dx) < far - 1) || (gallery && !loft && Math.abs(dx) == far - 1);
                set(level, m.offset(dx, LOFT_Y, dz), edge ? p.trim() : floorPanel(plan.line));
            }
        }
        // ---- one railing round its inner edge, posts at the corners and the ends
        List<BlockPos> rail = new ArrayList<>();
        for (int dx = -(far - 1); dx <= far - 1; dx++) rail.add(m.offset(dx, LOFT_Y + 1, LOFT_EDGE));
        for (int side = -1; side <= 1; side += 2) for (int dz = LOFT_EDGE + 1; dz <= GALLERY_TO; dz++) rail.add(m.offset(side * (far - 1), LOFT_Y + 1, dz));
        for (BlockPos at : rail) {
            int dx = at.getX() - m.getX(), dz = at.getZ() - m.getZ();
            boolean post = (Math.abs(dx) == far - 1 && (dz == LOFT_EDGE || dz == GALLERY_TO)) || (dx == 0 && dz == LOFT_EDGE);
            if (post) set(level, at, p.pillar());
            else level.setBlock(at, stone.wall().defaultBlockState(), 2);
        }
        Mason.join(level, rail);
        // ---- a straight flight up each side wall, against it, from the south end northward to the gallery
        for (int side = -1; side <= 1; side += 2) {
            for (int step = 1; step <= LOFT_Y; step++) {
                int dz = GALLERY_TO + 1 + (LOFT_Y - step);                       // the top step meets the gallery
                for (int y = 1; y < step; y++) set(level, m.offset(side * far, y, dz), p.wall());
                level.setBlock(m.offset(side * far, step, dz), stairs(plan.line, N), 2);
            }
        }
        // ---- two pillars carry the loft's edge (the middle left open: the way north runs under it)
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y < LOFT_Y; y++) set(level, m.offset(side * 2, y, LOFT_EDGE), y == 1 ? p.trim() : p.pillar());
        }
        // ---- up on the loft: the cache against the north wall between two braziers, a pair of wall braziers over it
        set(level, m.offset(0, LOFT_Y + 1, -far), ModContent.PASSAGE_CACHE.get());
        for (int side = -1; side <= 1; side += 2) {
            level.setBlock(m.offset(side * 2, LOFT_Y + 1, -far), brazier(plan, STANDING, null), 2);
            level.setBlock(m.offset(side * 3, LOFT_Y + 3, -far), brazier(plan, WALL, S), 2);
        }
        // ---- below: a runner of the tower's inlay up the middle, a brazier either side of the way in
        for (int dz = -far; dz <= far; dz++) set(level, m.offset(0, 0, dz), p.inlay());
        for (int side = -1; side <= 1; side += 2) level.setBlock(m.offset(side * 2, 1, far), brazier(plan, STANDING, null), 2);
    }

    /** True for the treasure rooms whose cache is locked: about half of them, decided by the level's seed. */
    public static boolean locked(Plan plan, Cell c) {
        return plan.kind(c) == Kind.TREASURE && new Random(plan.seed ^ (c.x * 7919L + c.z * 104729L)).nextBoolean();
    }

    /**
     * Turns every cache on a level to face the middle of its room, and dresses it in its tower's wood and metal.
     * Builders set caches without asking which way they should face.
     */
    static void settleCaches(ServerLevel level, Plan plan) {
        for (Cell c : plan.cells()) {
            AABB box = plan.cellBox(c);
            BlockPos m = plan.centre(c);
            for (BlockPos at : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
                BlockState state = level.getBlockState(at);
                if (!state.is(ModContent.PASSAGE_CACHE.get())) continue;
                int dx = m.getX() - at.getX(), dz = m.getZ() - at.getZ();
                var facing = dx == 0 && dz == 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.getApproximateNearest(dx, 0, dz);
                BlockState dressed = state.setValue(com.thefarawaydev.realmsofchaos.block.CacheBlock.TOWER, plan.line.ordinal())
                        .setValue(com.thefarawaydev.realmsofchaos.block.CacheBlock.FACING, facing)
                        .setValue(com.thefarawaydev.realmsofchaos.block.CacheBlock.LOCKED, locked(plan, c));
                if (dressed != state) level.setBlock(at, dressed, 2);
            }
        }
    }

    // ---- the proving ground and the barracks ---------------------------------------------------------------

    /** The beast gates of a proving ground: x (or z) out to the wall, and how far along it from the lane. */
    private static final int GATE_ALONG = 3;

    /** Where a proving ground's beast gates stand, as {dx, dz, outward x, outward z}: two in each wall, clear of the doorways. */
    private static int[][] gates(Plan plan, Cell c) {
        int w = plan.half(c) + 1;
        return new int[][]{{w, GATE_ALONG, 1, 0}, {w, -GATE_ALONG, 1, 0}, {-w, GATE_ALONG, -1, 0}, {-w, -GATE_ALONG, -1, 0},
                {GATE_ALONG, w, 0, 1}, {-GATE_ALONG, w, 0, 1}, {GATE_ALONG, -w, 0, -1}, {-GATE_ALONG, -w, 0, -1}};
    }

    /**
     * A proving ground: a sanded floor ringed in the tower's inlay, a chalk ring round its middle, the cache on a
     * lit stone at the heart of it. Two beast gates in every wall, each with a dark tunnel behind it; a war standard
     * in every corner; a ledge running round the walls overhead; and two war drums - either one calls the trial.
     */
    private static void buildProving(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c), h = plan.height(c);
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= far; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                Block floor = ring == far ? p.inlay() : ring == 3 && !lane(dx, dz) ? p.trim() : ModContent.ARENA_SAND.get();
                set(level, m.offset(dx, 0, dz), floor);
            }
        }
        set(level, m, p.light());
        set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
        for (int[] g : gates(plan, c)) {
            // the tunnel the beasts come up, dark behind the bars - only where there is rock behind the wall, so that a
            // corridor winding past is left alone (there the gate is shut fast, and the beasts come from its foot)
            boolean rock = true;
            for (int out = 1; out <= 2; out++) {
                for (int y = 0; y <= 4; y++) rock &= !level.getBlockState(m.offset(g[0] + out * g[2], y, g[1] + out * g[3])).isAir();
            }
            for (int y = 1; y <= 3; y++) {
                set(level, m.offset(g[0], y, g[1]), ModContent.BEAST_GATE.get());
                if (rock) set(level, m.offset(g[0] + g[2], y, g[1] + g[3]), Blocks.AIR);
            }
            set(level, m.offset(g[0], 4, g[1]), p.trim());
            if (rock) set(level, m.offset(g[0] + g[2], 0, g[1] + g[3]), Blocks.GRAVEL);
        }
        // a standard in every corner, on a plinth of the tower's stone
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                BlockPos foot = m.offset(sx * far, 0, sz * far);
                set(level, foot.above(), p.trim());
                set(level, foot.above(2), p.pillar());
                net.minecraft.core.Direction facing = sx > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
                level.setBlock(foot.above(3), ModContent.WAR_STANDARD.get().defaultBlockState()
                        .setValue(com.thefarawaydev.realmsofchaos.block.WarStandardBlock.FACING, facing), 2);
            }
        }
        // a ledge round the walls, high up, where the tower's watchers would stand
        Block ledge = Mason.of(p.wall()).slab();
        for (int d = -far; d <= far; d++) {
            for (int[] k : new int[][]{{d, -far}, {d, far}, {-far, d}, {far, d}}) {
                // (not over the doorways, nor over the standards in the corners)
                if (lane(k[0], k[1]) || (Math.abs(k[0]) == far && Math.abs(k[1]) == far)) continue;
                level.setBlock(m.offset(k[0], Math.min(5, h - 2), k[1]), Mason.slab(ledge, true), 2);
            }
        }
        // the drums: on the chalk ring, either side of the middle
        for (int s : new int[]{-1, 1}) set(level, m.offset(2 * s, 1, 2 * s), ModContent.WAR_DRUM.get());
    }

    /**
     * A barracks: two quarters of bunk beds, head to the wall, a footlocker at each foot and a dresser against the far
     * wall; a mess with a table, chairs drawn up to it and a bench along the wall; and a drill yard of training dummies
     * one, two and three blocks tall, racks of arms on the walls, and a sparring dummy sliding to and fro on its
     * boards. It is entered through garrison doors, and its soldiers are awake.
     */
    private static void buildBarracks(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        com.thefarawaydev.realmsofchaos.block.WeaponRackBlock racks = (com.thefarawaydev.realmsofchaos.block.WeaponRackBlock) ModContent.WEAPON_RACK.get();
        int[][] quarters = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        // which quarter is which: bunks, a mess, the drill yard, more bunks
        int[] use = {0, 1, 2, 0};
        for (int i = 0; i < quarters.length; i++) {
            int sx = quarters[i][0], sz = quarters[i][1];
            net.minecraft.core.Direction inX = sx > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
            net.minecraft.core.Direction inZ = sz > 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
            for (int u = 2; u <= far; u++) for (int v = 2; v <= far; v++) if (!cut(plan, c, sx * u, sz * v)) set(level, m.offset(sx * u, 0, sz * v), Blocks.SPRUCE_PLANKS);
            switch (use[i]) {
                case 0 -> {
                    // bunk beds, head to the x wall, every other row; a footlocker at each foot
                    for (int v = 2; v + 1 <= far; v += 2) {
                        if (cut(plan, c, sx * far, sz * v)) continue;
                        com.thefarawaydev.realmsofchaos.block.Furniture.BunkBed.build(level, m.offset(sx * (far - 1), 1, sz * v), inX.getOpposite(), ModContent.BUNK_BED.get());
                        if (far - 2 >= 2) footlocker(level, m.offset(sx * (far - 2), 1, sz * v), inX, random);
                    }
                    // a dresser against the far wall, and a light let into the wall between the bunks
                    dresser(level, m, sx, sz, far, inZ);
                    if (!cut(plan, c, sx * far, sz * 3)) set(level, m.offset(sx * (far + 1), 3, sz * 3), p.light());
                }
                case 1 -> {
                    // the mess: a table two long, chairs drawn up on both sides, a candle on it, a bench along the wall
                    for (int u = 3; u <= 4; u++) {
                        set(level, m.offset(sx * u, 1, sz * 3), ModContent.TABLE.get());
                        level.setBlock(m.offset(sx * u, 1, sz * 2), ModContent.CHAIR.get().defaultBlockState()
                                .setValue(com.thefarawaydev.realmsofchaos.block.Furniture.FACING, inZ.getOpposite()), 2);
                        level.setBlock(m.offset(sx * u, 1, sz * 4), ModContent.CHAIR.get().defaultBlockState()
                                .setValue(com.thefarawaydev.realmsofchaos.block.Furniture.FACING, inZ), 2);
                    }
                    level.setBlock(m.offset(sx * 3, 2, sz * 3), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                            .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3), 2);
                    for (int v = 2; v <= far - 1; v++) if (!cut(plan, c, sx * far, sz * v)) set(level, m.offset(sx * far, 1, sz * v), ModContent.BENCH.get());
                    dresser(level, m, sx, sz, far, inZ);
                    if (!cut(plan, c, sx * far, sz * far)) set(level, m.offset(sx * far, 1, sz * far), Blocks.BARREL);
                }
                default -> {
                    // the drill yard: dummies of three heights facing in, a sparring dummy on its boards, racks on both walls
                    com.thefarawaydev.realmsofchaos.block.TrainingDummyBlock.build(level, m.offset(sx * 2, 1, sz * 2), inX, 3, ModContent.TRAINING_DUMMY.get());
                    com.thefarawaydev.realmsofchaos.block.TrainingDummyBlock.build(level, m.offset(sx * 4, 1, sz * 2), inX, 1, ModContent.TRAINING_DUMMY.get());
                    com.thefarawaydev.realmsofchaos.block.TrainingDummyBlock.build(level, m.offset(sx * 2, 1, sz * 4), inZ, 2, ModContent.TRAINING_DUMMY.get());
                    level.setBlock(m.offset(sx * 3, 1, sz * 3), ModContent.MOVING_DUMMY.get().defaultBlockState()
                            .setValue(com.thefarawaydev.realmsofchaos.block.MovingDummyBlock.FACING, inX.getOpposite()), 3);
                    for (int d = 2; d < far; d++) {
                        if (!cut(plan, c, sx * far, sz * d)) level.setBlock(m.offset(sx * far, 2, sz * d), racks.facing(inX), 2);
                        if (!cut(plan, c, sx * d, sz * far)) level.setBlock(m.offset(sx * d, 2, sz * far), racks.facing(inZ), 2);
                    }
                    if (!cut(plan, c, sx * far, sz * far)) {
                        set(level, m.offset(sx * far, 1, sz * far), Blocks.BARREL);
                        set(level, m.offset(sx * far, 2, sz * far), ModContent.WAR_STANDARD.get());
                    }
                }
            }
        }
        // and where the lanes cross, the tower's inlay
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, m.offset(dx, 0, dz), p.inlay());
    }

    /** A soldier's footlocker: a chest with a few arrows, a loaf, now and then a bottle of honey. */
    private static void footlocker(ServerLevel level, BlockPos at, net.minecraft.core.Direction facing, Random random) {
        level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing), 2);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity kit) {
            kit.setItem(random.nextInt(9), new ItemStack(Items.ARROW, 3 + random.nextInt(6)));
            kit.setItem(9 + random.nextInt(9), new ItemStack(Items.BREAD, 1 + random.nextInt(2)));
            if (random.nextFloat() < 0.3F) kit.setItem(18 + random.nextInt(9), new ItemStack(Items.HONEY_BOTTLE));
            kit.setItem(26, new ItemStack(ModContent.LOCKPICK.get()));
        }
    }

    /** A dresser against a barracks quarter's far z wall, both halves inside the quarter. */
    private static void dresser(ServerLevel level, BlockPos m, int sx, int sz, int far, net.minecraft.core.Direction inZ) {
        int along = inZ.getCounterClockWise().getStepX();
        int lx = sx * 2;
        if (Math.abs(lx + along) < 2 || Math.abs(lx + along) > far - 1) lx = sx * 3;
        com.thefarawaydev.realmsofchaos.block.Furniture.Dresser.build(level, m.offset(lx, 1, sz * far), inZ, ModContent.DRESSER.get());
    }

    /**
     * A duelling circle: a ring of the tower's mosaic in the middle, posts round it with the tower's fire burning on
     * them, benches along the walls for those who watch, standards on the walls. Its champion waits in the circle;
     * the cache stands on a plinth in a corner and stays shut while the champion stands.
     */
    private static void buildDuel(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= far; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r <= 3.2) set(level, m.offset(dx, 0, dz), r > 2.5 ? p.trim() : mosaic(plan.line));
                else if (r <= 4.2) set(level, m.offset(dx, 0, dz), ModContent.ARENA_SAND.get());
            }
        }
        set(level, m, p.light());
        // eight posts round the circle, the tower's brazier on each
        for (int[] k : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {4, 2}, {-4, -2}, {2, -4}, {-2, 4}}) {
            BlockPos post = m.offset(k[0], 1, k[1]);
            set(level, post, Passages.column(plan.line));
            level.setBlock(post.above(), ModContent.TOWER_BRAZIER.get().defaultBlockState()
                    .setValue(com.thefarawaydev.realmsofchaos.block.BrazierBlock.TOWER, plan.line.ordinal()), 2);
        }
        // benches facing in along the walls, standards over them
        for (int d = 2; d <= far; d++) {
            for (int[] k : new int[][]{{far, d}, {far, -d}, {-far, d}, {-far, -d}, {d, far}, {-d, far}, {d, -far}, {-d, -far}}) {
                if (Math.abs(k[0]) == far && Math.abs(k[1]) == far) continue;
                if (cut(plan, c, k[0], k[1]) || !level.getBlockState(m.offset(k[0], 1, k[1])).isAir()) continue;
                net.minecraft.core.Direction into = Mason.outward(k[0], k[1]).getOpposite();
                level.setBlock(m.offset(k[0], 1, k[1]), Mason.stair(Mason.of(p.wall()).stairs(), into.getOpposite(), false), 2);
            }
        }
        // standards on plinths in the two corners either side of the prize's
        for (int[] k : new int[][]{{-far, far}, {far, -far}}) {
            if (cut(plan, c, k[0], k[1])) continue;
            set(level, m.offset(k[0], 1, k[1]), p.trim());
            level.setBlock(m.offset(k[0], 2, k[1]), ModContent.WAR_STANDARD.get().defaultBlockState()
                    .setValue(com.thefarawaydev.realmsofchaos.block.WarStandardBlock.FACING, k[0] > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST), 2);
        }
        // the prize, in a corner, on a plinth
        BlockPos plinth = duelCache(plan, c).below();
        set(level, plinth, p.trim());
        set(level, plinth.above(), ModContent.PASSAGE_CACHE.get());
    }

    /** Where a duelling circle keeps its cache: on a plinth in one corner. */
    public static BlockPos duelCache(Plan plan, Cell c) {
        int far = plan.half(c);
        return plan.centre(c).offset(far, 2, far);
    }

    /** The gallery of an overlook: a walk two blocks deep round the walls, this high above the floor. */
    public static final int GALLERY_Y = 4;

    /** Where on its gallery an overlook's n-th bowman stands. */
    static int[] gallerySpot(Plan plan, Cell c, int n) {
        // (against the wall: the rail runs along the gallery's inner edge)
        int g = plan.half(c);
        int[][] spots = {{g, 2}, {-g, -2}, {2, -g}, {-2, g}, {g, -4}, {-g, 4}, {-4, -g}, {4, g}};
        return spots[n % spots.length];
    }

    /** Where an overlook keeps its cache: up on the gallery, in a corner. */
    public static BlockPos overlookCache(Plan plan, Cell c) {
        int far = plan.half(c);
        return plan.centre(c).offset(-far, GALLERY_Y + 1, -far);
    }

    /**
     * An overlook: a tall hall with a gallery round all four walls, railed, reached by ladders in two corners. Its
     * bowmen and slingers stand up there; four great pillars on the floor are the cover from them. The cache is up
     * on the gallery too - whoever wants it has to climb.
     */
    private static void buildOverlook(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c), h = plan.height(c);
        Mason.Stone stone = Mason.of(p.wall());
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= far; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < far - 1 || cut(plan, c, dx, dz)) continue;
                // the gallery's floor, slabs high in the block so that the way under it stays clear
                level.setBlock(m.offset(dx, GALLERY_Y, dz), Mason.slab(stone.slab(), true), 2);
                // its rail, a wall along its inner edge, broken where the lanes run under
                if (ring == far - 1 && !lane(dx, dz)) level.setBlock(m.offset(dx, GALLERY_Y + 1, dz), stone.wall().defaultBlockState(), 2);
            }
        }
        // ladders up in two corners, against the wall
        for (int s : new int[]{-1, 1}) {
            int lx = s * far, lz = s * (far - 2);
            net.minecraft.core.Direction facing = s > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
            for (int y = 1; y <= GALLERY_Y; y++) {
                level.setBlock(m.offset(lx, y, lz), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, facing), 2);
            }
        }
        // four great pillars to stand behind
        for (int[] k : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            for (int y = 1; y <= h; y++) set(level, m.offset(k[0], y, k[1]), y == 1 || y == h ? p.trim() : p.pillar());
        }
        // arrow slits' worth of light along the walls above the gallery, and the floor of the hall in the tower's inlay
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) set(level, m.offset(dx, 0, dz), p.inlay());
        set(level, m, p.light());
        set(level, overlookCache(plan, c), ModContent.PASSAGE_CACHE.get());
        level.setBlock(m.offset(far, GALLERY_Y + 1, far), ModContent.WAR_STANDARD.get().defaultBlockState()
                .setValue(com.thefarawaydev.realmsofchaos.block.WarStandardBlock.FACING, net.minecraft.core.Direction.WEST), 2);
    }

    /**
     * A quiet hall: dust, cobwebs and fallen stone, a cache left on a lit plinth in the middle - and four dark holes
     * in the ceiling. Whoever walks up to the cache, or reaches for it, springs it: the doorways are barred, and the
     * room's guard drops from the holes. When they are down the bars lift.
     */
    private static void buildAmbush(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
        BlockPos m = plan.centre(c);
        int far = plan.half(c), h = plan.height(c);
        Mason.Stone stone = Mason.of(p.wall());
        set(level, m, p.trim());
        set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
        for (int[] k : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) set(level, m.offset(k[0], 0, k[1]), p.light());
        for (int[] k : ambushHoles()) {
            for (int y = h + 1; y <= h + 3; y++) set(level, m.offset(k[0], y, k[1]), Blocks.AIR);
            set(level, m.offset(k[0], h + 4, k[1]), Blocks.COBWEB);
        }
        glaze(level, plan, c);
        // the dust of a room nobody comes to: rubble and cobwebs in the quarters, bones here and there
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= far; dz++) {
                if (lane(dx, dz) || cut(plan, c, dx, dz)) continue;
                boolean under = false;
                for (int[] k : ambushHoles()) under |= Math.abs(dx - k[0]) <= 1 && Math.abs(dz - k[1]) <= 1;
                if (under) continue;
                BlockPos at = m.offset(dx, 1, dz);
                float roll = random.nextFloat();
                if (roll < 0.14F) level.setBlock(at, Mason.slab(stone.slab(), false), 2);
                else if (roll < 0.2F) level.setBlock(at, Mason.stair(stone.stairs(), net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong())), false), 2);
                else if (roll < 0.23F) set(level, at, Blocks.BONE_BLOCK);
                if (random.nextFloat() < 0.1F) set(level, m.offset(dx, h, dz), Blocks.COBWEB);
            }
        }
    }

    /** The skylights of a quiet hall: one-way glass over each of its holes. */
    static List<BlockPos> skylights(Plan plan, Cell c) {
        List<BlockPos> out = new ArrayList<>();
        if (plan.kind(c) != Kind.AMBUSH) return out;
        BlockPos m = plan.centre(c);
        for (int[] k : ambushHoles()) out.add(m.offset(k[0], plan.height(c) + 1, k[1]));
        return out;
    }

    /**
     * Glazes a quiet hall's holes with one-way glass facing down: from the floor they are four more panels of the
     * ceiling; from above, the whole hall can be watched.
     */
    static void glaze(ServerLevel level, Plan plan, Cell c) {
        for (BlockPos at : skylights(plan, c)) {
            level.setBlock(at, ModContent.ONE_WAY_GLASS.get().defaultBlockState()
                    .setValue(com.thefarawaydev.realmsofchaos.block.Glass.OneWay.FACING, net.minecraft.core.Direction.DOWN), 2);
        }
    }

    /** The holes an ambush drops from: over the middle of each quarter. */
    static int[][] ambushHoles() {
        return new int[][]{{3, 3}, {-3, -3}, {3, -3}, {-3, 3}};
    }

    private static String ambushKey(Plan plan, Cell c) {
        return "ambush/" + builtKey(plan) + "/" + c.x + "/" + c.z;
    }

    /** 0 untouched, 1 sprung (the doors barred, its guard down among you), 2 over. */
    public static int ambushStage(ServerLevel level, Plan plan, Cell c) {
        return ProgressData.get(level.getServer()).counter(ambushKey(plan, c));
    }

    /** Bars (or unbars) every doorway of a room: iron bars across the openings in its walls. */
    static void bar(ServerLevel level, Plan plan, Cell c, boolean shut) {
        BlockPos m = plan.centre(c);
        int w = plan.half(c) + 1;
        for (Cell n : plan.neighbours(c)) {
            if (!plan.connected(c, n)) continue;
            int ox = n.x - c.x, oz = n.z - c.z;
            for (int k = -3; k <= 3; k++) {
                for (int y = 1; y <= 4; y++) {
                    BlockPos at = m.offset(ox != 0 ? ox * w : k, y, oz != 0 ? oz * w : k);
                    BlockState here = level.getBlockState(at);
                    if (shut && here.isAir()) level.setBlock(at, Blocks.IRON_BARS.defaultBlockState(), 3);
                    else if (!shut && here.is(Blocks.IRON_BARS)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.playSound(null, m.above(2), shut ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, shut ? 0.5F : 0.7F);
    }

    /** Who drops on you in an ambush: the level's sentinels, quick ones mostly, more the deeper it is. */
    public static List<Post> ambushers(Plan plan, Cell c, int players) {
        Random random = new Random(plan.seed + c.x * 59L + c.z * 13L);
        List<Sentinel.Role> roles = new ArrayList<>(roles(plan));
        roles.remove(Sentinel.Role.MYSTIC);
        int n = 3 + (plan.threat >= 3.0 ? 1 : 0) + (plan.threat >= 7.0 ? 1 : 0) + Adapt.extraGuardians(players);
        List<Post> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Sentinel.Role role = i == 0 && roles.contains(Sentinel.Role.SKIRMISHER) ? Sentinel.Role.SKIRMISHER : roles.get(random.nextInt(roles.size()));
            out.add(new Post(Sentinel.Kind.pick(plan.line, role, plan.threat, random), i == 0 && plan.stage >= 1));
        }
        return out;
    }

    /** The ambush is sprung: the doors are barred, and the guard drops from the ceiling. @return how many dropped */
    public static int spring(ServerLevel level, Plan plan, Cell c) {
        if (ambushStage(level, plan, c) != 0) return 0;
        ProgressData.get(level.getServer()).setCounter(ambushKey(plan, c), 1);
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            ProgressData.get(level.getServer()).setCounter(ambushKey(plan, c), 2);
            return 0;
        }
        bar(level, plan, c, true);
        BlockPos m = plan.centre(c);
        List<ServerPlayer> here = within(level, plan, c);
        int n = 0, h = plan.height(c);
        int[][] holes = ambushHoles();
        // what looked like ceiling was glass, and the guard watching from above it comes through
        for (BlockPos pane : skylights(plan, c)) if (level.getBlockState(pane).is(ModContent.ONE_WAY_GLASS.get())) level.destroyBlock(pane, false);
        for (Post post : ambushers(plan, c, Math.max(1, here.size()))) {
            int[] k = holes[n % holes.length];
            Sentinel mob = raise(level, plan, c, post, m.offset(k[0], n < holes.length ? h + 2 : h - 1, k[1]), n * 90.0F);
            if (mob == null) continue;
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 30, 0));
            if (!here.isEmpty()) mob.setTarget(here.get(n % here.size()));
            n++;
        }
        level.playSound(null, m.above(3), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0F, 0.6F);
        for (ServerPlayer pl : here) {
            Hud.title(pl, Hud.colored(Component.translatable(HUD + "ambush"), 0xFF5555), Hud.colored(Component.translatable(HUD + "ambush.sub"), 0xFFDD88), 3, 30, 10);
        }
        // and if everyone leaves the room (or falls), the bars lift: nobody is shut out of their own passage
        watchAmbush(level, plan, c);
        return n;
    }

    private static void watchAmbush(ServerLevel level, Plan plan, Cell c) {
        Scheduler.after(level.getServer(), 20, () -> {
            if (ambushStage(level, plan, c) != 1) return;
            if (within(level, plan, c).isEmpty()) {
                bar(level, plan, c, false);
                return;
            }
            watchAmbush(level, plan, c);
        });
    }

    /** A player in a quiet hall: close to the cache, it springs; with the ambush down, the bars lift. */
    public static void ambushTick(ServerLevel level, Plan plan, Cell c, ServerPlayer player) {
        int stage = ambushStage(level, plan, c);
        BlockPos m = plan.centre(c);
        if (stage == 0) {
            double dx = player.getX() - (m.getX() + 0.5), dz = player.getZ() - (m.getZ() + 0.5);
            if (dx * dx + dz * dz < 9.0) spring(level, plan, c);
        } else if (stage == 1) {
            if (!guardians(level, plan, c).isEmpty()) {
                // (a player who walked back in after the bars lifted is shut in with them again)
                if (!level.getBlockState(barsCheck(plan, c)).is(Blocks.IRON_BARS) && within(level, plan, c).contains(player)) bar(level, plan, c, true);
                return;
            }
            ProgressData.get(level.getServer()).setCounter(ambushKey(plan, c), 2);
            bar(level, plan, c, false);
            level.playSound(null, m.above(2), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
            for (ServerPlayer pl : within(level, plan, c)) Hud.actionBar(pl, Hud.colored(Component.translatable(HUD + "ambush.over"), 0xF5B82E));
        }
    }

    /** One doorway block of a room, to see whether it is barred. */
    private static BlockPos barsCheck(Plan plan, Cell c) {
        for (Cell n : plan.neighbours(c)) {
            if (!plan.connected(c, n)) continue;
            int w = plan.half(c) + 1;
            return plan.centre(c).offset((n.x - c.x) * w, 1, (n.z - c.z) * w);
        }
        return plan.centre(c);
    }

    public static final int PROVING_WAVES = 3;

    private static String provingKey(Plan plan, Cell c) {
        return "proving/" + builtKey(plan) + "/" + c.x + "/" + c.z;
    }

    /** How far a proving ground has come: 0 untried, 1 to 3 that wave under way, 4 won. */
    public static int provingStage(ServerLevel level, Plan plan, Cell c) {
        return ProgressData.get(level.getServer()).counter(provingKey(plan, c));
    }

    /** Opens (or shuts) a proving ground's beast gates. */
    static void provingGates(ServerLevel level, Plan plan, Cell c, boolean open) {
        BlockPos m = plan.centre(c);
        for (int[] g : gates(plan, c)) {
            // (a gate opens onto its own tunnel - its gravel floor says so - never onto a corridor)
            if (open && !level.getBlockState(m.offset(g[0] + g[2], 0, g[1] + g[3])).is(Blocks.GRAVEL)) continue;
            for (int y = 1; y <= 3; y++) {
                BlockPos at = m.offset(g[0], y, g[1]);
                if (open ? level.getBlockState(at).is(ModContent.BEAST_GATE.get()) : level.getBlockState(at).isAir()) {
                    set(level, at, open ? Blocks.AIR : ModContent.BEAST_GATE.get());
                }
            }
        }
        level.playSound(null, m.above(2), open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    /** Who comes out of the gates in a wave. Each is harder than the last; the third has a lesser god leading it. */
    public static List<Post> wave(Plan plan, Cell c, int wave, int players) {
        Random random = new Random(plan.seed + c.x * 31L + c.z * 7L + wave * 977L);
        List<Sentinel.Role> roles = roles(plan);
        java.util.function.UnaryOperator<Sentinel.Role> have = r -> roles.contains(r) ? r : Sentinel.Role.SOLDIER;
        List<Sentinel.Role> out = new ArrayList<>();
        boolean champion = false;
        switch (wave) {
            case 1 -> {
                out.add(Sentinel.Role.SOLDIER);
                out.add(Sentinel.Role.SOLDIER);
                if (plan.stage >= 1 || plan.tier >= 2) out.add(Sentinel.Role.CASTER);
            }
            case 2 -> {
                out.add(have.apply(Sentinel.Role.SKIRMISHER));
                out.add(have.apply(Sentinel.Role.SKIRMISHER));
                out.add(Sentinel.Role.CASTER);
                out.add(have.apply(Sentinel.Role.BRUTE));
            }
            default -> {
                champion = true;
                out.add(have.apply(Sentinel.Role.BRUTE));
                out.add(roles.contains(Sentinel.Role.MYSTIC) ? Sentinel.Role.MYSTIC : Sentinel.Role.CASTER);
                out.add(Sentinel.Role.SOLDIER);
                out.add(have.apply(Sentinel.Role.SKIRMISHER));
            }
        }
        for (int i = 0; i < Adapt.extraGuardians(players); i++) out.add(roles.get(random.nextInt(roles.size())) == Sentinel.Role.MYSTIC ? Sentinel.Role.SOLDIER : roles.get(random.nextInt(roles.size())));
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Sentinel.Role role = out.get(i) == Sentinel.Role.KEEPER ? Sentinel.Role.SOLDIER : out.get(i);
            posts.add(new Post(Sentinel.Kind.pick(plan.line, role, plan.threat, random), champion && i == 0));
        }
        return posts;
    }

    /** Raises one sentinel of a room at a spot: a guardian of that room, tagged to it. */
    private static Sentinel raise(ServerLevel level, Plan plan, Cell c, Post post, BlockPos at, float yaw) {
        Sentinel mob = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return null;
        mob.setUp(post.kind(), plan.tier, plan.threat, post.elite());
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
        mob.setPersistenceRequired();
        mob.addTag(GUARDIAN_TAG);
        mob.addTag(GUARDIAN_TAG + "/" + c.x + "/" + c.z);
        if (post.elite()) Lesser.crown(mob, plan);
        level.addFreshEntity(mob);
        level.sendParticles(Signature.spark(plan.line), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.05);
        return mob;
    }

    private static List<ServerPlayer> within(ServerLevel level, Plan plan, Cell c) {
        return level.getEntitiesOfClass(ServerPlayer.class, plan.cellBox(c).inflate(4, 2, 4), pl -> pl.isAlive() && !pl.isSpectator());
    }

    /** Opens the gates and lets a wave out: one to each gate's mouth, in turn. */
    private static int letOut(ServerLevel level, Plan plan, Cell c, int wave) {
        ProgressData.get(level.getServer()).setCounter(provingKey(plan, c), wave);
        List<ServerPlayer> here = within(level, plan, c);
        BlockPos m = plan.centre(c);
        int far = plan.half(c);
        provingGates(level, plan, c, true);
        int[][] mouths = gates(plan, c);
        int from = new Random(plan.seed + wave).nextInt(mouths.length), n = 0;
        for (Post post : wave(plan, c, wave, Math.max(1, here.size()))) {
            int[] g = mouths[(from + n * 3) % mouths.length];
            BlockPos at = m.offset(g[0] - g[2], 1, g[1] - g[3]);
            float yaw = (float) Math.toDegrees(Math.atan2(g[2], -g[3]));
            if (raise(level, plan, c, post, at, yaw) != null) n++;
        }
        level.playSound(null, m.above(3), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.8F + 0.1F * wave);
        for (ServerPlayer pl : here) {
            Hud.title(pl, Hud.colored(Component.translatable(HUD + "proving.wave", wave, PROVING_WAVES), 0xFF5555),
                    Hud.colored(Component.translatable(HUD + "proving.wave." + wave), 0xFFDD88), 5, 40, 10);
        }
        return n;
    }

    /** A war drum is beaten: in an untried proving ground, the trial begins. */
    public static void drum(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Plan plan = planAt(level, pos);
        Cell c = plan == null ? null : plan.cellAt(pos);
        if (c == null || plan.kind(c) != Kind.PROVING || player.isSpectator()) return;
        int stage = provingStage(level, plan, c);
        if (stage > PROVING_WAVES) {
            Hud.above(player, pos, Component.translatable(HUD + "proving.done"), 0xAAAAAA);
        } else if (stage > 0) {
            Hud.above(player, pos, Component.translatable(HUD + "proving.underway", stage, PROVING_WAVES), 0xFFAA00);
        } else if (level.getDifficulty() == Difficulty.PEACEFUL) {
            ProgressData.get(level.getServer()).setCounter(provingKey(plan, c), PROVING_WAVES + 1);
            Hud.above(player, pos, Component.translatable(HUD + "proving.peaceful"), 0xAAAAAA);
        } else {
            letOut(level, plan, c, 1);
        }
    }

    /** A proving under way: when a wave is down, the next comes; when the last is down, the proving is won. */
    public static void provingTick(ServerLevel level, Plan plan, Cell c) {
        int stage = provingStage(level, plan, c);
        if (stage < 1 || stage > PROVING_WAVES || !guardians(level, plan, c).isEmpty()) return;
        if (stage < PROVING_WAVES) {
            letOut(level, plan, c, stage + 1);
            return;
        }
        ProgressData.get(level.getServer()).setCounter(provingKey(plan, c), PROVING_WAVES + 1);
        provingGates(level, plan, c, false);
        BlockPos m = plan.centre(c);
        level.playSound(null, m.above(2), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX() + 0.5, m.getY() + 2.0, m.getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.2);
        for (ServerPlayer pl : within(level, plan, c)) {
            Hud.title(pl, Hud.colored(Component.translatable(HUD + "proving.won"), 0xF5B82E),
                    Hud.colored(Component.translatable(HUD + "proving.won.sub"), 0xFFFFFF), 5, 50, 15);
        }
    }

    // ---- treasure --------------------------------------------------------------------------------------------

    /** One player's share of a cache: always some of this tier's forge material, then a mixed handful. Arenas pay better. */
    /** The chance a cache holds a tower's disc, and again the passages' disc. */
    public static final float DISC_CHANCE = 0.07F;

    public static List<ItemStack> lootFor(Plan plan, Cell cell, UUID player) {
        Random random = new Random(plan.seed ^ player.getLeastSignificantBits() ^ (cell.x * 131L + cell.z * 17L + 5));
        // an arena's cache and a puzzle's are the richer ones
        boolean arena = plan.kind(cell) == Kind.ARENA || plan.kind(cell) == Kind.PROVING || plan.kind(cell) == Kind.DUEL || plan.kind(cell) == Kind.OVERLOOK
                || Rooms.isPuzzle(plan.kind(cell));
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier), 1 + random.nextInt(2) + (arena ? 1 : 0)));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3) + plan.tier + plan.stage));
        loot.add(new ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(4)));
        Item[] metal = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        loot.add(new ItemStack(metal[plan.tier - 1], 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.6F) loot.add(new ItemStack(Items.ARROW, 6 + random.nextInt(11)));
        if (arena || random.nextFloat() < 0.25F + 0.15F * plan.tier) loot.add(new ItemStack(Items.GOLDEN_APPLE));
        if (random.nextFloat() < 0.35F) loot.add(new ItemStack(Items.ENDER_PEARL, 1 + random.nextInt(2)));
        if (random.nextFloat() < 0.4F) loot.add(new ItemStack(ModContent.LOCKPICK.get(), 1 + random.nextInt(2)));
        if (plan.tier >= 2 && random.nextFloat() < 0.4F) loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier - 1), 1 + random.nextInt(2)));
        if (plan.kind(cell) == Kind.PROVING) {
            // the proving pays best of all: a second share of the tier's material, and something to fight on with
            loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier), 1 + random.nextInt(2)));
            loot.add(new ItemStack(random.nextBoolean() ? Items.GOLDEN_APPLE : Items.TOTEM_OF_UNDYING, 1));
        }
        // now and then a disc: the tower's own music, or the passages'
        if (random.nextFloat() < DISC_CHANCE) {
            // the tower's own music - or, in the passages of its upper floors, the music of its heights
            boolean high = plan.tier >= 3;
            loot.add(new ItemStack(ModContent.disc(switch (plan.line) {
                case ZEUS -> high ? "olympus_peak" : "olympus";
                case POSEIDON -> high ? "abyss_trench" : "abyss";
                case HADES -> high ? "tartarus" : "underworld";
            })));
        }
        if (random.nextFloat() < DISC_CHANCE) loot.add(new ItemStack(ModContent.disc("passage")));
        // the war dance is won in the arenas
        if (arena && random.nextFloat() < 0.2F) loot.add(new ItemStack(ModContent.disc("arena")));
        if (plan.line == Line.HADES && random.nextFloat() < 0.5F) {
            Tarot card = Tarot.values()[random.nextInt(Tarot.values().length)];
            if (card != Tarot.DEVIL) loot.add(new ItemStack(ModContent.tarotItem(card)));
        }
        return loot;
    }

    private static String lootKey(Plan plan, Cell cell, UUID player) {
        return "looted/" + player + "/" + plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + cell.x + "/" + cell.z;
    }

    public static boolean hasLooted(ServerLevel level, Plan plan, Cell cell, UUID player) {
        return ProgressData.get(level.getServer()).isBuilt(lootKey(plan, cell, player));
    }

    /**
     * Right-clicking a treasure cache: the player takes their own share, once. Everybody gets one, whoever came
     * first. An arena's cache stays shut while its sentinels stand. @return true if a share was handed over.
     */
    public static boolean loot(ServerLevel level, BlockPos pos, ServerPlayer player) {
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".cache.";
        Plan plan = planAt(level, pos);
        Cell cell = plan == null ? null : plan.cellAt(pos);
        if (cell == null || (plan.kind(cell) != Kind.TREASURE && plan.kind(cell) != Kind.ARENA && plan.kind(cell) != Kind.STAIRWELL && plan.kind(cell) != Kind.PROVING
                && plan.kind(cell) != Kind.DUEL && plan.kind(cell) != Kind.AMBUSH && plan.kind(cell) != Kind.OVERLOOK && !Rooms.isPuzzle(plan.kind(cell)))) {
            Hud.above(player, pos, Component.translatable(hud + "empty"), 0xAAAAAA);
            return false;
        }
        if (Rooms.isPuzzle(plan.kind(cell)) && !Rooms.solved(level, plan, cell)) {
            Hud.above(player, pos, Component.translatable(hud + "locked"), 0xFF5555);
            return false;
        }
        if (plan.kind(cell) == Kind.PROVING && provingStage(level, plan, cell) <= PROVING_WAVES) {
            int stage = provingStage(level, plan, cell);
            Hud.above(player, pos, stage == 0 ? Component.translatable(HUD + "proving.locked")
                    : Component.translatable(HUD + "proving.underway", stage, PROVING_WAVES), 0xFF5555);
            return false;
        }
        if (plan.kind(cell) == Kind.AMBUSH && ambushStage(level, plan, cell) < 2) {
            // the bait: reaching for it springs the ambush, if walking in had not
            if (ambushStage(level, plan, cell) == 0) spring(level, plan, cell);
            Hud.above(player, pos, Component.translatable(HUD + "guarded", guardians(level, plan, cell).size()), 0xFF5555);
            return false;
        }
        if (plan.kind(cell) == Kind.ARENA || plan.kind(cell) == Kind.DUEL || plan.kind(cell) == Kind.OVERLOOK) {
            wake(level, plan, cell);
            int standing = guardians(level, plan, cell).size();
            if (standing > 0) {
                Hud.above(player, pos, Component.translatable(HUD + "guarded", standing), 0xFF5555);
                return false;
            }
        }
        ProgressData data = ProgressData.get(level.getServer());
        String key = lootKey(plan, cell, player.getUUID());
        if (data.isBuilt(key)) {
            Hud.above(player, pos, Component.translatable(hud + "taken"), 0xAAAAAA);
            return false;
        }
        data.markBuilt(key);
        Bounties.count(player, Bounties.Kind.CACHES, 1);
        List<ItemStack> loot = lootFor(plan, cell, player.getUUID());
        int fortune = Boons.count(player, Boons.Boon.FORTUNE);
        if (fortune > 0) loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier), fortune));
        int stacks = loot.size();
        for (ItemStack stack : loot) {
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        com.thefarawaydev.realmsofchaos.fx.RocSounds.at(level, pos, "cache_open", 1.0F, 1.0F);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0.0);
        Hud.above(player, pos, Component.translatable(hud + "loot", stacks), 0xF5B82E);
        return true;
    }

    // ---- seals and sentinels ---------------------------------------------------------------------------------

    public static int sealsLeft(ServerLevel level, Plan plan) {
        int n = 0;
        for (Cell c : plan.seals) if (level.getBlockState(plan.sealPos(c)).is(ModContent.PASSAGE_SEAL.get())) n++;
        return n;
    }

    /**
     * How many sentinels guard a seal on this level when one player comes: a Keeper, one more for each tier of the
     * tower, and one more again for each level into the passage.
     */
    public static int guardianCount(Plan plan) {
        return 2 + plan.tier + Math.min(3, plan.stage);
    }

    /** The sentinels of a room that are still standing. */
    public static List<Mob> guardians(ServerLevel level, Plan plan, Cell c) {
        return level.getEntitiesOfClass(Mob.class, plan.cellBox(c).inflate(6, 2, 6), m -> m.isAlive() && m.entityTags().contains(GUARDIAN_TAG + "/" + c.x + "/" + c.z));
    }

    private static String wokenKey(Plan plan) {
        return builtKey(plan) + "/woken/";
    }

    /** The kinds of sentinel a level can field: more kinds turn up the more dangerous the level is. */
    public static List<Sentinel.Role> roles(Plan plan) {
        List<Sentinel.Role> roles = new ArrayList<>(List.of(Sentinel.Role.SOLDIER, Sentinel.Role.CASTER));
        if (plan.threat >= 2.0) roles.add(Sentinel.Role.SKIRMISHER);
        if (plan.threat >= 5.0) roles.add(Sentinel.Role.BRUTE);
        if (plan.threat >= 7.0) roles.add(Sentinel.Role.MYSTIC);
        return roles;
    }

    /**
     * Who a room holds. A sealed chamber: a Keeper and its guard, with an elite among them on the last level. An
     * arena: an elite and its pack. A patrolled room: one or two. Groups of players wake more.
     */
    public static List<Post> garrison(Plan plan, Cell c, int players) {
        List<Post> out = new ArrayList<>();
        Random random = new Random(plan.seed + c.x * 131L + c.z * 17L);
        List<Sentinel.Role> roles = roles(plan);
        int others, elites;
        Kind kind = plan.kind(c);
        if (kind == Kind.SEAL) {
            out.add(new Post(Sentinel.Kind.of(plan.line, Sentinel.Role.KEEPER), false));
            others = guardianCount(plan) - 1;
            elites = plan.last() ? 1 : 0;
        } else if (kind == Kind.ARENA) {
            others = 3 + Math.min(3, plan.stage);
            elites = 1;
        } else if (kind == Kind.DUEL) {
            // a duelling circle: its champion, alone - a duelist of the tower, one of the lesser gods
            out.add(new Post(Sentinel.Kind.of(plan.line, Sentinel.Trait.DUELIST), true));
            for (int i = 0; i < Adapt.extraGuardians(players); i++) out.add(new Post(Sentinel.Kind.pick(plan.line, Sentinel.Role.SOLDIER, plan.threat, random), false));
            return out;
        } else if (kind == Kind.OVERLOOK) {
            // an overlook: bowmen and slingers up on the gallery, a guard below
            others = 3 + (plan.threat >= 4.0 ? 1 : 0) + (plan.threat >= 8.0 ? 1 : 0) + Adapt.extraGuardians(players);
            for (int i = 0; i < others; i++) {
                Sentinel.Role role = i % 2 == 0 ? Sentinel.Role.CASTER : roles.contains(Sentinel.Role.BRUTE) && i == 3 ? Sentinel.Role.BRUTE : Sentinel.Role.SOLDIER;
                out.add(new Post(Sentinel.Kind.pick(plan.line, role, plan.threat, random), false));
            }
            return out;
        } else if (kind == Kind.BARRACKS) {
            // a barracks is never empty: its soldiers are up, and an officer among them past the first level
            others = 2 + (plan.threat >= 4.0 ? 1 : 0) + (plan.threat >= 8.0 ? 1 : 0);
            elites = plan.stage >= 1 ? 1 : 0;
        } else {
            others = 1 + (plan.threat >= 8.0 ? 1 : 0);
            elites = 0;
        }
        others += Adapt.extraGuardians(players);
        boolean mystic = false;
        for (int i = 0; i < others; i++) {
            Sentinel.Role role = roles.get(random.nextInt(roles.size()));
            // one mystic to a room is plenty; an elite is always a fighter
            if (role == Sentinel.Role.MYSTIC && (mystic || i < elites)) role = Sentinel.Role.SOLDIER;
            mystic |= role == Sentinel.Role.MYSTIC;
            // which kind of that role: the plain one, or one of those with a trait that this level's threat has unlocked
            out.add(new Post(Sentinel.Kind.pick(plan.line, role, plan.threat, random), i < elites));
        }
        return out;
    }

    /** True for rooms that have sentinels to wake: sealed chambers, arenas and patrolled rooms. */
    public static boolean garrisoned(Plan plan, Cell c) {
        return plan.kind(c) == Kind.SEAL || plan.kind(c) == Kind.ARENA || plan.kind(c) == Kind.BARRACKS || plan.kind(c) == Kind.DUEL
                || plan.kind(c) == Kind.OVERLOOK || plan.patrols.contains(c);
    }

    /**
     * Wakes the sentinels of a room. Happens once per room (until the passage re-seals itself); more of them wake
     * for a group. Nothing wakes on Peaceful. @return how many woke.
     */
    public static int wake(ServerLevel level, Plan plan, Cell c) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = wokenKey(plan) + c.x + "/" + c.z;
        if (!garrisoned(plan, c) || data.isBuilt(key) || level.getDifficulty() == Difficulty.PEACEFUL) return 0;
        data.markBuilt(key);
        int players = Math.max(1, level.getEntitiesOfClass(ServerPlayer.class, plan.box(), p -> p.isAlive() && !p.isSpectator()).size());
        BlockPos m = plan.centre(c);
        // a corridor is only three wide: its sentinels stand in the crossing; in a room they stand on the lanes that
        // run door to door, which nothing is ever built on
        int[][] spots = plan.kind(c) == Kind.HALL ? new int[][]{{1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}}
                : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 1}, {-3, -1}, {1, -3}, {-1, 3}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        int woken = 0;
        for (Post post : garrison(plan, c, players)) {
            Sentinel mob = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
            if (mob == null) continue;
            BlockPos at = m.offset(spots[woken % spots.length][0], 1, spots[woken % spots.length][1]);
            if (plan.kind(c) == Kind.OVERLOOK && post.kind().role == Sentinel.Role.CASTER) {
                // up on the gallery, along the walls
                int[] g = gallerySpot(plan, c, woken);
                at = m.offset(g[0], GALLERY_Y + 1, g[1]);
            }
            mob.setUp(post.kind(), plan.tier, plan.threat, post.elite());
            mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, woken * 90.0F, 0.0F);
            mob.setPersistenceRequired();
            mob.addTag(GUARDIAN_TAG);
            mob.addTag(GUARDIAN_TAG + "/" + c.x + "/" + c.z);
            // an arena's champion is no nameless sentinel: one of the lesser gods holds it
            if (post.elite() && (plan.kind(c) == Kind.ARENA || plan.kind(c) == Kind.DUEL)) Lesser.crown(mob, plan);
            level.addFreshEntity(mob);
            level.sendParticles(Signature.spark(plan.line), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.05);
            woken++;
        }
        if (woken > 0) level.playSound(null, m.above(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.8F);
        return woken;
    }

    /**
     * One level seals itself again: every seal is restored and every room's sentinels will wake afresh.
     */
    public static void rearm(ServerLevel level, Plan plan) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2, 0, 2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        for (Cell c : plan.seals) set(level, plan.sealPos(c), ModContent.PASSAGE_SEAL.get());
        ProgressData.get(level.getServer()).unmarkPrefix(wokenKey(plan));
        Rooms.rearm(level, plan);
        ProgressData.get(level.getServer()).clearCounters("proving/" + builtKey(plan) + "/");
        ProgressData.get(level.getServer()).clearCounters("ambush/" + builtKey(plan) + "/");
        for (Cell c : plan.cells()) if (plan.kind(c) == Kind.AMBUSH) bar(level, plan, c, false);
        for (Cell c : plan.cells()) if (plan.kind(c) == Kind.AMBUSH) glaze(level, plan, c);
        settleCaches(level, plan);
        for (Cell c : plan.cells()) if (plan.kind(c) == Kind.PROVING) provingGates(level, plan, c, false);
        for (Cell c : plan.cells()) if (plan.kind(c) == Kind.SPIKES) Snares.lower(level, plan, c);
        shutGate(level, plan);
    }

    /** True once a level's gate has opened: every seal broken. */
    public static boolean gateOpen(ServerLevel level, Plan plan) {
        return level.getBlockState(plan.gatePositions().get(0)).is(ModContent.GATE_ASCEND.get());
    }

    /** The last seal is broken: the dark glass in the arch gives way to the gate itself. */
    public static void openGate(ServerLevel level, Plan plan) {
        if (gateOpen(level, plan)) return;
        for (BlockPos g : plan.gatePositions()) {
            level.setBlock(g, ModContent.GATE_ASCEND.get().defaultBlockState(), 3);
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.03);
        }
        BlockPos middle = plan.gateOffset(0, 2);
        com.thefarawaydev.realmsofchaos.fx.RocSounds.at(level, middle, "seal_break", 1.4F, 1.0F);
    }

    /** A level sealed again has its gate shut again. */
    public static void shutGate(ServerLevel level, Plan plan) {
        for (BlockPos g : plan.gatePositions()) {
            if (!level.getBlockState(g).is(GATE_SHUT)) level.setBlock(g, GATE_SHUT.defaultBlockState(), 3);
        }
    }

    /** The level a block belongs to, with its plan - or null if it is not in a passage. */
    public static Plan planAt(ServerLevel level, BlockPos pos) {
        Realm realm = Realm.of(level.dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(pos.getX());
        return index == 0 ? null : plan(level, realm.line(), index, stageAt(level, realm.line(), index, pos.getY()));
    }

    /**
     * The seal at {@code pos} gives way, with a shockwave that throws up the floor round it and staggers anyone
     * close. When it was the last one on its level, that level's gate opens.
     */
    public static void breakSeal(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        if (!level.getBlockState(pos).is(ModContent.PASSAGE_SEAL.get())) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (breaker != null) {
            Bounties.count(breaker, Bounties.Kind.SEALS, 1);
            // a seal gives something to whoever broke it: one boon of three, for the rest of the passage
            com.thefarawaydev.realmsofchaos.ui.BoonMenu.open(breaker, pos);
            com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(breaker, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.BOON);
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), x, y, z, 60, 0.3, 0.3, 0.3, 0.15);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), x, y + 0.5, z, 80, 0.6, 0.6, 0.6, 0.5);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 3, 0.4, 0.4, 0.4, 0.0);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 1.2F);
        for (int[] k : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) Fx.impact(level, new Vec3(x + k[0], pos.getY() - 1, z + k[1]), 3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(4.0), LivingEntity::isAlive)) {
            Vec3 away = e.position().subtract(x, e.getY(), z);
            double d = Math.max(0.3, Math.sqrt(away.x * away.x + away.z * away.z));
            e.setDeltaMovement(e.getDeltaMovement().add(away.x / d * 0.5, 0.3, away.z / d * 0.5));
            e.needsSync = true;
        }
        Plan plan = planAt(level, pos);
        if (plan == null) return;
        ExperienceOrb.award(level, new Vec3(x, y, z), 12 * plan.tier + 4 * plan.stage);
        int left = sealsLeft(level, plan);
        if (left == 0) openGate(level, plan);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, plan.box().inflate(4), e -> true)) {
            if (left > 0) {
                Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "seal.broken", left), 0x55FFFF));
            } else {
                Hud.title(p, Hud.colored(Component.translatable(HUD + "open"), plan.line.color()),
                        Hud.colored(plan.last() ? Component.translatable(HUD + "open.sub", plan.index + 1)
                                : Component.translatable(HUD + "open.deeper", plan.stage + 2, plan.stages), 0xCCCCCC));
                level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
        }
        if (left == 0) {
            for (BlockPos g : plan.gatePositions()) level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ---- coming and going ------------------------------------------------------------------------------------

    private static boolean occupied(ServerLevel level, Line line, int index, ServerPlayer except) {
        for (int stage = 0; stage < stages(level, line, index); stage++) {
            Plan plan = plan(level, line, index, stage);
            if (!level.getEntitiesOfClass(ServerPlayer.class, plan.box(), p -> p != except && p.isAlive() && !p.isSpectator()).isEmpty()) return true;
        }
        return false;
    }

    /** True if every level of the passage that has been built has had all its seals broken. */
    public static boolean brokenOpen(ServerLevel level, Line line, int index) {
        if (!isBuilt(level, line, index, 0)) return false;
        for (int stage = 0; stage < stages(level, line, index); stage++) {
            if (isBuilt(level, line, index, stage) && sealsLeft(level, plan(level, line, index, stage)) > 0) return false;
        }
        return true;
    }

    /** Seals every built level of a passage again. This is how a passage adapts to a player who has not walked it. */
    public static void rearm(ServerLevel level, Line line, int index) {
        for (int stage = 0; stage < stages(level, line, index); stage++) {
            if (isBuilt(level, line, index, stage)) rearm(level, plan(level, line, index, stage));
        }
    }

    /** Sends a player into the first level of passage {@code index} of the tower they are in. */
    public static void enter(ServerPlayer player, Line line, int index) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ServerLevel level = Realm.ofLine(line).level(server);
        if (level == null) return;
        ensure(level, line, index, 0);
        // someone who has not walked this passage finds it sealed, even if others broke it open before - unless
        // those others are still inside, in which case they are simply joined
        boolean rearmed = false;
        if (!ProgressData.get(server).hasPassed(player.getUUID(), line, index) && brokenOpen(level, line, index) && !occupied(level, line, index, player)) {
            rearm(level, line, index);
            rearmed = true;
        }
        arrive(player, level, plan(level, line, index, 0));
        if (rearmed) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "rearmed"), 0xFFAA00));
        com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.PASSAGE);
    }

    private static void arrive(ServerPlayer player, ServerLevel level, Plan plan) {
        Teleports.moveTo(player, level, plan.arrival(), 0.0F);
        see(player, plan, plan.start);
        // the level after this one is built while this one is explored; and everyone gets a chart to draw it on
        if (!plan.last()) prepare(level, plan.line, plan.index, plan.stage + 1);
        if (Charts.give(player)) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "chart"), 0xF5B82E));
        int left = sealsLeft(level, plan);
        if (plan.affliction() != Affliction.NONE) {
            // said once, on the way in: what weighs on this level, and that a shrine holds it off
            com.thefarawaydev.realmsofchaos.ui.Cards.notice(player, com.thefarawaydev.realmsofchaos.ui.Words.of(HUD + "afflicted", plan.affliction().displayName()),
                    com.thefarawaydev.realmsofchaos.ui.Words.of(plan.affliction().lore()));
        }
        Hud.title(player, Hud.colored(Component.translatable(HUD + "title", plan.label(), plan.index + 1), plan.line.color()),
                Hud.colored(Component.translatable(HUD + (left > 0 ? "level" : "level.open"), plan.stage + 1, plan.stages, left), 0xCCCCCC));
    }

    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    /**
     * The gate at the end of a level. While seals hold it shut it refuses. Otherwise it leads on: to the next level
     * of the passage, or - from the last - to the next floor of the tower.
     * @return true if the player went through
     */
    public static boolean tryExit(ServerPlayer player, Spot spot) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) return false;
        Plan plan = plan(level, spot);
        int left = sealsLeft(level, plan);
        long now = server.getTickCount();
        Long last = LAST_NOTICE.get(player.getUUID());
        boolean quiet = last != null && now >= last && now - last < 40;
        if (left > 0) {
            if (!quiet) {
                LAST_NOTICE.put(player.getUUID(), now);
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "sealed", left), 0xFF5555));
            }
            return false;
        }
        if (!plan.last()) {
            // a moment between gates, or standing in the next level's arrival would fling you on again
            if (quiet) return false;
            LAST_NOTICE.put(player.getUUID(), now);
            ensure(level, spot.line(), spot.index(), spot.stage() + 1);
            arrive(player, level, plan(level, spot.line(), spot.index(), spot.stage() + 1));
            return true;
        }
        ProgressData data = ProgressData.get(server);
        boolean first = data.markPassed(player.getUUID(), spot.line(), spot.index());
        if (first) player.giveExperiencePoints(20 + 15 * plan.tier + 10 * plan.stages);
        Teleports.toFloor(player, spot.line(), spot.index() + 1);
        return Realm.of(player.level().dimension()) != null && indexAt(player.getX()) == 0;
    }

    // ---- once a second, for everyone inside ------------------------------------------------------------------

    private static final Map<UUID, String> LAST_CELL = new HashMap<>();
    /** The rooms each player has walked into, per level: what their map shows. Kept for the session. */
    private static final Map<String, Set<Cell>> VISITED = new HashMap<>();

    private static String seenKey(ServerPlayer player, Plan plan, Cell c) {
        return "seen/" + player.getUUID() + "/" + plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + c.x + "." + c.z;
    }

    /** The rooms of a level a player has walked into. It is kept with the world: a chart does not go blank when the server stops. */
    public static Set<Cell> visited(ServerPlayer player, Plan plan) {
        return VISITED.computeIfAbsent(player.getUUID() + "/" + plan.seed, k -> {
            Set<Cell> seen = new HashSet<>();
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                ProgressData data = ProgressData.get(server);
                for (Cell c : plan.cells()) if (data.isBuilt(seenKey(player, plan, c))) seen.add(c);
            }
            return seen;
        });
    }

    /** A player has walked into a room. @return true if it is the first time */
    public static boolean see(ServerPlayer player, Plan plan, Cell cell) {
        if (!visited(player, plan).add(cell)) return false;
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).markBuilt(seenKey(player, plan, cell));
        return true;
    }

    /** Forgets where a player has been: a level sealed again is unknown again. Also clears what is held in memory, as a restart would. */
    public static void forgetSeen(ServerPlayer player, boolean kept) {
        VISITED.keySet().removeIf(k -> k.startsWith(player.getUUID() + "/"));
        MinecraftServer server = player.level().getServer();
        if (kept && server != null) ProgressData.get(server).unmarkPrefix("seen/" + player.getUUID() + "/");
    }

    /** Wakes sentinels as players walk in, names the room they enter, and keeps the passage bar current. */
    public static void service(ServerPlayer player, ServerLevel level, Spot spot) {
        ensure(level, spot.line(), spot.index(), spot.stage());
        Plan plan = plan(level, spot);
        Cell cell = plan.cellAt(player.blockPosition());
        afflict(player, plan);
        if (cell != null) {
            boolean fresh = see(player, plan, cell);
            Kind kind = plan.kind(cell);
            boolean sealed = kind == Kind.SEAL && level.getBlockState(plan.sealPos(cell)).is(ModContent.PASSAGE_SEAL.get());
            boolean wakes = sealed || kind == Kind.ARENA || kind == Kind.BARRACKS || kind == Kind.DUEL || kind == Kind.OVERLOOK || plan.patrols.contains(cell);
            if (kind == Kind.AMBUSH && !player.isSpectator() && !player.isCreative()) ambushTick(level, plan, cell, player);
            if (kind == Kind.PROVING) provingTick(level, plan, cell);
            if (wakes && !player.isSpectator() && !player.isCreative()) wake(level, plan, cell);
            if (kind == Kind.TRAP) Traps.service(player, level, plan, cell);
            Rooms.service(player, level, plan, cell);
            String here = spot.line().id() + "/" + spot.index() + "/" + spot.stage() + "/" + cell.x + "/" + cell.z;
            if (!here.equals(LAST_CELL.put(player.getUUID(), here))) {
                String hint = switch (kind) {
                    case SEAL -> sealed ? "seal" : "seal.done";
                    case TREASURE -> "treasure";
                    case ARENA -> "arena";
                    case SHRINE -> "shrine";
                    case TRAP -> "trap";
                    case GARDEN -> "garden";
                    case LIBRARY -> "library";
                    case POOL -> "pool";
                    case VAULT -> "vault";
                    case CHIMES -> "chimes";
                    case BEAM -> "beam";
                    case PLATES -> "plates";
                    case SHUTTERS -> "shutters";
                    case DIALS -> "dials";
                    case SLIDES -> "slides";
                    case CRUSHER -> "crusher";
                    case DARTS -> "darts";
                    case SPIKES -> "spikes";
                    case FLAMES -> "flames";
                    case BLADES -> "blades";
                    case STAIRWELL -> "stairwell";
                    case ARC -> "arc";
                    case GAS -> "gas";
                    case CELLS -> "cells";
                    case STORE -> "store";
                    case OBSERVATORY -> "observatory";
                    case ARMORY -> "armory";
                    case BATHS -> "baths";
                    case SANCTUM -> "sanctum";
                    case PROVING -> provingStage(level, plan, cell) > PROVING_WAVES ? "proving.done" : "proving";
                    case BARRACKS -> "barracks";
                    case DUEL -> "duel";
                    case AMBUSH -> "ambush";
                    case OVERLOOK -> "overlook";
                    case SMITHY -> "smithy";
                    case CRYPT -> "crypt";
                    case WAR_ROOM -> "war_room";
                    case EXIT -> sealsLeft(level, plan) > 0 ? "exit.sealed" : plan.last() ? "exit.open" : "exit.deeper";
                    default -> null;
                };
                // (a room says what it is the first time it is walked into; after that only a way out, or a puzzle still unsolved)
                if (hint != null && (fresh || kind == Kind.EXIT || (Rooms.isPuzzle(kind) && !Rooms.solved(level, plan, cell)))) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "room." + hint), 0xFFFF55));
                if (kind == Kind.SEAL && sealed) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.SEAL);
            }
        }
        RealmSafety.updatePassageBar(player, plan, plan.seals.size() - sealsLeft(level, plan));
        if (Charts.holds(player)) Charts.draw(player, level, plan);
    }

    /**
     * The Compass of Chaos inside a passage: which way to the nearest unbroken seal on this level, or to the gate
     * once they are all broken. @return the place pointed at.
     */
    public static BlockPos point(ServerPlayer player, ServerLevel level, Spot spot) {
        Plan plan = plan(level, spot);
        BlockPos best = null;
        for (Cell c : plan.seals) {
            BlockPos seal = plan.sealPos(c);
            if (!level.getBlockState(seal).is(ModContent.PASSAGE_SEAL.get())) continue;
            if (best == null || seal.distSqr(player.blockPosition()) < best.distSqr(player.blockPosition())) best = seal;
        }
        boolean toGate = best == null;
        if (toGate) best = plan.centre(plan.exit).above();
        double dx = best.getX() + 0.5 - player.getX(), dz = best.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + (toGate ? "compass.gate" : "compass.seal"), (int) Math.round(dist),
                Component.translatable(Ruins.directionKey(dx, dz))), plan.line.color()));
        if (dist > 0.5) {
            for (int i = 1; i <= 8; i++) {
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), player.getX() + dx / dist * i * 0.6, player.getY() + 1.2, player.getZ() + dz / dist * i * 0.6, 1, 0, 0, 0, 0);
            }
        }
        return best;
    }
}
