package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
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
    public static final int CELL = 12, ROOM = 9, CENTRE = 5;
    public static final int HEADROOM = 5;
    public static final int MAX_SIZE = 6;
    public static final int X0 = 600, PITCH = 160;
    public static final int LAYOUT_VERSION = 3;
    public static final String GUARDIAN_TAG = "realms_of_chaos_guardian";
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".passage.";

    public enum Kind { START, EXIT, SEAL, TREASURE, SHRINE, HALL, ROOM, TRAP, GARDEN, LIBRARY, POOL, ARENA }

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

    /** How many levels passage {@code index} has: two in the lower third of a tower, three above. */
    public static int stages(int index) {
        return Tower.tier(index) == 1 ? 2 : 3;
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
        public Cell start, exit;
        public final List<Cell> seals = new ArrayList<>();
        /** Ordinary rooms and corridors that a patrol of sentinels walks. */
        public final Set<Cell> patrols = new LinkedHashSet<>();

        Plan(long worldSeed, Line line, int index, int stage) {
            this.line = line;
            this.index = index;
            this.stages = stages(index);
            this.stage = Math.max(0, Math.min(stages - 1, stage));
            this.tier = Tower.tier(index);
            // rooms: 4 x 4 low in the tower, 5 x 5 higher, and 6 x 6 for the last level of the highest passages
            this.size = tier == 1 ? 4 : tier == 3 && this.stage == stages - 1 ? 6 : 5;
            this.threat = Sentinel.threat(index, this.stage);
            this.seed = worldSeed ^ (0x9E3779B97F4A7C15L * (line.ordinal() * 100L + index + 1)) ^ (0xC2B2AE3D27D4EB4FL * this.stage);
            this.kinds = new Kind[size][size];
            this.east = new boolean[size][size];
            this.south = new boolean[size][size];
            generate(new Random(seed));
        }

        public boolean last() {
            return stage == stages - 1;
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

        private void generate(Random random) {
            // a maze: a random spanning tree, so every room can be reached, plus a few extra doors so there are loops
            start = new Cell(random.nextInt(size), random.nextInt(size));
            boolean[][] seen = new boolean[size][size];
            Deque<Cell> stack = new ArrayDeque<>();
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
                Cell next = fresh.get(random.nextInt(fresh.size()));
                open(cur, next);
                seen[next.x][next.z] = true;
                stack.push(next);
            }
            for (Cell c : cells()) {
                for (Cell n : neighbours(c)) {
                    if ((n.x > c.x || n.z > c.z) && !connected(c, n) && random.nextFloat() < 0.15F) open(c, n);
                }
            }

            // the way out is as far from the way in as the maze allows
            Map<Cell, Integer> dist = distances();
            exit = start;
            for (Cell c : cells()) if (dist.get(c) > dist.get(exit)) exit = c;
            kinds[start.x][start.z] = Kind.START;
            kinds[exit.x][exit.z] = Kind.EXIT;

            // seals hide in dead ends and far corners
            List<Cell> free = new ArrayList<>();
            for (Cell c : cells()) if (!c.equals(start) && !c.equals(exit)) free.add(c);
            Collections.shuffle(free, random);
            free.sort(Comparator.comparingInt((Cell c) -> doors(c) == 1 ? 0 : 1).thenComparingInt(c -> -dist.get(c)));
            int sealCount = tier + 1;
            for (int i = 0; i < sealCount; i++) {
                Cell c = free.remove(0);
                seals.add(c);
                kinds[c.x][c.z] = Kind.SEAL;
            }
            Collections.shuffle(free, random);
            Cell shrine = free.remove(0);
            kinds[shrine.x][shrine.z] = Kind.SHRINE;
            for (int i = 0; i < treasures(); i++) {
                Cell c = free.remove(0);
                kinds[c.x][c.z] = Kind.TREASURE;
            }
            for (int i = 0; i < arenas(); i++) {
                Cell c = free.remove(0);
                kinds[c.x][c.z] = Kind.ARENA;
            }
            for (Cell c : free) {
                float roll = random.nextFloat();
                kinds[c.x][c.z] = roll < 0.30F ? Kind.HALL : roll < 0.52F ? Kind.ROOM : roll < 0.68F ? Kind.TRAP
                        : roll < 0.79F ? Kind.GARDEN : roll < 0.90F ? Kind.LIBRARY : Kind.POOL;
            }
            // a third of the ordinary rooms have a patrol in them, and more on the levels further in
            float chance = 0.35F + 0.10F * stage;
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
            BlockPos c = centre(exit);
            for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) out.add(c.offset(dx, dy, 0));
            return out;
        }

        /** Which cell a position is in, or null when it is inside a wall or a doorway. */
        public Cell cellAt(BlockPos pos) {
            BlockPos o = origin();
            int dx = pos.getX() - o.getX(), dz = pos.getZ() - o.getZ();
            if (dx < 0 || dz < 0 || dx >= width() || dz >= width()) return null;
            int ox = dx % CELL, oz = dz % CELL;
            if (ox < 1 || ox > ROOM || oz < 1 || oz > ROOM) return null;
            return new Cell(dx / CELL, dz / CELL);
        }

        public AABB cellBox(Cell c) {
            BlockPos m = centre(c);
            return new AABB(m.getX() - 4, y(), m.getZ() - 4, m.getX() + 5, y() + HEADROOM + 2, m.getZ() + 5);
        }

        public AABB box() {
            BlockPos o = origin();
            return new AABB(o.getX(), y() - 1, o.getZ(), o.getX() + width(), y() + HEADROOM + 3, o.getZ() + width());
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
    public static int stageAt(int index, double y) {
        return Math.max(0, Math.min(stages(index) - 1, Math.floorDiv((int) Math.floor(y) - (Y - 2), LEVEL_PITCH)));
    }

    /** The passage and level a player is standing in, or null. */
    public static Spot locate(ServerPlayer player) {
        Realm realm = Realm.of(player.level().dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(player.getX());
        return index == 0 ? null : new Spot(realm.line(), index, stageAt(index, player.getY()));
    }

    /** True if a player in a passage has got outside the level they are on: fallen out, or risen through a ceiling. */
    public static boolean outside(ServerPlayer player, Spot spot) {
        int y = Y + spot.stage() * LEVEL_PITCH;
        return player.getY() < Y - 6 || player.getY() > Y + stages(spot.index()) * LEVEL_PITCH + 6
                || player.getY() < y - 1 || player.getY() > y + HEADROOM + 3;
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
        build(level, plan(level, line, index, stage));
        data.markBuilt(key);
    }

    public static void ensure(ServerLevel level, Line line, int index) {
        ensure(level, line, index, 0);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    /** Builds the whole level: a solid block of wall, carved into rooms and halls, then furnished. */
    public static void build(ServerLevel level, Plan plan) {
        TowerBuilder.Palette p = TowerBuilder.palette(plan.line, plan.tier);
        Random random = new Random(plan.seed * 31 + 7);
        BlockPos o = plan.origin();
        int w = plan.width();
        // stale sentinels of an earlier layout go first
        for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                for (int y = -1; y <= HEADROOM + 2; y++) {
                    set(level, o.offset(x, y, z), y <= 0 || y >= HEADROOM + 1 ? p.fill() : p.wall());
                }
            }
        }
        for (Cell c : plan.cells()) carve(level, plan, c, p);
        // doorways: three wide, three high, through the wall between two cells, under a lintel of trim
        for (Cell c : plan.cells()) {
            BlockPos m = plan.centre(c);
            Cell e = new Cell(c.x + 1, c.z), s = new Cell(c.x, c.z + 1);
            if (c.x + 1 < plan.size && plan.connected(c, e)) {
                for (int d = 5; d <= 7; d++) for (int k = -1; k <= 1; k++) doorway(level, m.offset(d, 0, k), p);
            }
            if (c.z + 1 < plan.size && plan.connected(c, s)) {
                for (int d = 5; d <= 7; d++) for (int k = -1; k <= 1; k++) doorway(level, m.offset(k, 0, d), p);
            }
        }
        for (Cell c : plan.cells()) furnish(level, plan, c, p, random);
    }

    private static void doorway(ServerLevel level, BlockPos floor, TowerBuilder.Palette p) {
        set(level, floor, p.inlay());
        for (int y = 1; y <= 3; y++) set(level, floor.above(y), Blocks.AIR);
        set(level, floor.above(4), p.trim());
    }

    private static void carve(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p) {
        BlockPos m = plan.centre(c);
        boolean hall = plan.kind(c) == Kind.HALL;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (hall && !inHall(plan, c, dx, dz)) continue;
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                set(level, m.offset(dx, 0, dz), hall || edge || ((dx + dz) & 1) == 0 ? p.top() : p.topAlt());
                for (int y = 1; y <= HEADROOM; y++) set(level, m.offset(dx, y, dz), Blocks.AIR);
            }
        }
        if (hall) {
            set(level, m.above(HEADROOM + 1), p.light());
            return;
        }
        // a proper room has a shallow dome over its middle, lit from its corners,
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) set(level, m.offset(dx, HEADROOM + 1, dz), Blocks.AIR);
        }
        for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {0, 0}}) set(level, m.offset(k[0], HEADROOM + 2, k[1]), p.light());
        for (int d = -3; d <= 3; d++) {
            for (int[] k : new int[][]{{d, -3}, {d, 3}, {-3, d}, {3, d}}) set(level, m.offset(k[0], HEADROOM + 1, k[1]), p.trim());
        }
        // a band of trim round its walls, and sconces set into them
        for (int d = -4; d <= 4; d++) {
            for (int[] k : new int[][]{{d, -5}, {d, 5}, {-5, d}, {5, d}}) {
                set(level, m.offset(k[0], 4, k[1]), p.trim());
                if (Math.abs(d) == 3) set(level, m.offset(k[0], 3, k[1]), p.light());
            }
        }
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
                set(level, m, p.light());
            }
            case EXIT -> {
                for (BlockPos g : plan.gatePositions()) set(level, g, ModContent.GATE_ASCEND.get());
                for (int dy = 1; dy <= 4; dy++) {
                    set(level, m.offset(-2, dy, 0), p.trim());
                    set(level, m.offset(2, dy, 0), p.trim());
                }
                for (int dx = -1; dx <= 1; dx++) set(level, m.offset(dx, 4, 0), p.trim());
                for (int dx = -2; dx <= 2; dx++) set(level, m.offset(dx, 0, 0), p.inlay());
            }
            case SEAL -> {
                set(level, m.above(), p.trim());
                set(level, plan.sealPos(c), ModContent.PASSAGE_SEAL.get());
                for (int[] k : CORNERS) {
                    for (int y = 1; y <= HEADROOM; y++) set(level, m.offset(k[0], y, k[1]), p.pillar());
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
                // a fighting floor: rings underfoot, a pillar in every corner, and the prize in the middle
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        int ring = Math.max(Math.abs(dx), Math.abs(dz));
                        set(level, m.offset(dx, 0, dz), ring == 3 || ring == 1 ? p.inlay() : ring == 0 ? p.light() : p.top());
                    }
                }
                set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
                for (int[] k : CORNERS) {
                    for (int y = 1; y <= 3; y++) set(level, m.offset(k[0], y, k[1]), p.pillar());
                    set(level, m.offset(k[0], 4, k[1]), p.light());
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
            case TRAP -> {
                // scorching tiles underfoot and webs to wade through: slow going, never a dead end
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (Math.abs(dx) == 4 && Math.abs(dz) <= 1 || Math.abs(dz) == 4 && Math.abs(dx) <= 1) continue;   // thresholds stay safe
                        if (random.nextFloat() < 0.28F) set(level, m.offset(dx, 0, dz), Blocks.MAGMA_BLOCK);
                        else if (random.nextFloat() < 0.10F) set(level, m.offset(dx, 1 + random.nextInt(2), dz), Blocks.COBWEB);
                    }
                }
            }
            case GARDEN -> {
                // moss underfoot, flowers and bushes in the four quarters; the lanes are left as paths
                Block[] flowers = {Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.SHORT_GRASS, Blocks.FERN};
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (lane(dx, dz)) continue;
                        set(level, m.offset(dx, 0, dz), Blocks.MOSS_BLOCK);
                        float roll = random.nextFloat();
                        if (roll < 0.35F) set(level, m.offset(dx, 1, dz), flowers[random.nextInt(flowers.length)]);
                        else if (roll < 0.42F) set(level, m.offset(dx, 1, dz), Blocks.MOSS_CARPET);
                    }
                }
                for (int[] k : CORNERS) set(level, m.offset(k[0], 1, k[1]), random.nextBoolean() ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA);
            }
            case LIBRARY -> {
                // shelves along the walls between the doors, a carpet down the middle, a lectern to one side
                for (int d = 2; d <= 4; d++) {
                    for (int[] k : new int[][]{{d, -4}, {-d, -4}, {d, 4}, {-d, 4}, {-4, d}, {-4, -d}, {4, d}, {4, -d}}) {
                        set(level, m.offset(k[0], 1, k[1]), Blocks.BOOKSHELF);
                        set(level, m.offset(k[0], 2, k[1]), Blocks.BOOKSHELF);
                        set(level, m.offset(k[0], 3, k[1]), random.nextFloat() < 0.3F ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
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
            case ROOM -> {
                switch (random.nextInt(4)) {
                    case 0 -> {
                        for (int[] k : CORNERS) {
                            for (int y = 1; y <= HEADROOM; y++) set(level, m.offset(k[0], y, k[1]), p.pillar());
                        }
                    }
                    case 1 -> {
                        // a medallion in the floor
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                                set(level, m.offset(dx, 0, dz), ring == 2 ? p.inlay() : ring == 0 ? p.light() : p.trim());
                            }
                        }
                    }
                    case 2 -> {
                        for (int dx = -4; dx <= 4; dx++) {
                            for (int dz = -4; dz <= 4; dz++) {
                                if (!lane(dx, dz) && random.nextFloat() < 0.12F) set(level, m.offset(dx, 1, dz), p.trim());
                            }
                        }
                    }
                    default -> {
                        for (int[] k : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                            set(level, m.offset(k[0], 1, k[1]), p.trim());
                            set(level, m.offset(k[0], 2, k[1]), p.light());
                        }
                    }
                }
            }
            case HALL -> { }
        }
    }

    // ---- treasure --------------------------------------------------------------------------------------------

    /** One player's share of a cache: always some of this tier's forge material, then a mixed handful. Arenas pay better. */
    public static List<ItemStack> lootFor(Plan plan, Cell cell, UUID player) {
        Random random = new Random(plan.seed ^ player.getLeastSignificantBits() ^ (cell.x * 131L + cell.z * 17L + 5));
        boolean arena = plan.kind(cell) == Kind.ARENA;
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier), 1 + random.nextInt(2) + (arena ? 1 : 0)));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3) + plan.tier + plan.stage));
        loot.add(new ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(4)));
        Item[] metal = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        loot.add(new ItemStack(metal[plan.tier - 1], 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.6F) loot.add(new ItemStack(Items.ARROW, 6 + random.nextInt(11)));
        if (arena || random.nextFloat() < 0.25F + 0.15F * plan.tier) loot.add(new ItemStack(Items.GOLDEN_APPLE));
        if (random.nextFloat() < 0.35F) loot.add(new ItemStack(Items.ENDER_PEARL, 1 + random.nextInt(2)));
        if (plan.tier >= 2 && random.nextFloat() < 0.4F) loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier - 1), 1 + random.nextInt(2)));
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
        if (cell == null || (plan.kind(cell) != Kind.TREASURE && plan.kind(cell) != Kind.ARENA)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "empty"), 0xAAAAAA));
            return false;
        }
        if (plan.kind(cell) == Kind.ARENA) {
            wake(level, plan, cell);
            int standing = guardians(level, plan, cell).size();
            if (standing > 0) {
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "guarded", standing), 0xFF5555));
                return false;
            }
        }
        ProgressData data = ProgressData.get(level.getServer());
        String key = lootKey(plan, cell, player.getUUID());
        if (data.isBuilt(key)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "taken"), 0xAAAAAA));
            return false;
        }
        data.markBuilt(key);
        List<ItemStack> loot = lootFor(plan, cell, player.getUUID());
        int stacks = loot.size();
        for (ItemStack stack : loot) {
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.6F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0.0);
        Hud.actionBar(player, Hud.colored(Component.translatable(hud + "loot", stacks), 0xF5B82E));
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
        return 2 + plan.tier + plan.stage;
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
            others = 3 + plan.stage;
            elites = 1;
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
            out.add(new Post(Sentinel.Kind.of(plan.line, role), i < elites));
        }
        return out;
    }

    /** True for rooms that have sentinels to wake: sealed chambers, arenas and patrolled rooms. */
    public static boolean garrisoned(Plan plan, Cell c) {
        return plan.kind(c) == Kind.SEAL || plan.kind(c) == Kind.ARENA || plan.patrols.contains(c);
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
            mob.setUp(post.kind(), plan.tier, plan.threat, post.elite());
            mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, woken * 90.0F, 0.0F);
            mob.setPersistenceRequired();
            mob.addTag(GUARDIAN_TAG);
            mob.addTag(GUARDIAN_TAG + "/" + c.x + "/" + c.z);
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
        for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        for (Cell c : plan.seals) set(level, plan.sealPos(c), ModContent.PASSAGE_SEAL.get());
        ProgressData.get(level.getServer()).unmarkPrefix(wokenKey(plan));
    }

    /** The level a block belongs to, with its plan - or null if it is not in a passage. */
    public static Plan planAt(ServerLevel level, BlockPos pos) {
        Realm realm = Realm.of(level.dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(pos.getX());
        return index == 0 ? null : plan(level, realm.line(), index, stageAt(index, pos.getY()));
    }

    /**
     * The seal at {@code pos} gives way, with a shockwave that throws up the floor round it and staggers anyone
     * close. When it was the last one on its level, that level's gate opens.
     */
    public static void breakSeal(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        if (!level.getBlockState(pos).is(ModContent.PASSAGE_SEAL.get())) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.3, 0.3, 0.3, 0.15);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 80, 0.6, 0.6, 0.6, 0.5);
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
            for (BlockPos g : plan.gatePositions()) level.sendParticles(ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ---- coming and going ------------------------------------------------------------------------------------

    private static boolean occupied(ServerLevel level, Line line, int index, ServerPlayer except) {
        for (int stage = 0; stage < stages(index); stage++) {
            Plan plan = plan(level, line, index, stage);
            if (!level.getEntitiesOfClass(ServerPlayer.class, plan.box(), p -> p != except && p.isAlive() && !p.isSpectator()).isEmpty()) return true;
        }
        return false;
    }

    /** True if every level of the passage that has been built has had all its seals broken. */
    public static boolean brokenOpen(ServerLevel level, Line line, int index) {
        if (!isBuilt(level, line, index, 0)) return false;
        for (int stage = 0; stage < stages(index); stage++) {
            if (isBuilt(level, line, index, stage) && sealsLeft(level, plan(level, line, index, stage)) > 0) return false;
        }
        return true;
    }

    /** Seals every built level of a passage again. This is how a passage adapts to a player who has not walked it. */
    public static void rearm(ServerLevel level, Line line, int index) {
        for (int stage = 0; stage < stages(index); stage++) {
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
        visited(player, plan).add(plan.start);
        int left = sealsLeft(level, plan);
        Hud.title(player, Hud.colored(Component.translatable(HUD + "title", plan.index + 1), plan.line.color()),
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

    public static Set<Cell> visited(ServerPlayer player, Plan plan) {
        return VISITED.computeIfAbsent(player.getUUID() + "/" + plan.seed, k -> new HashSet<>());
    }

    /** Wakes sentinels as players walk in, names the room they enter, and keeps the passage bar current. */
    public static void service(ServerPlayer player, ServerLevel level, Spot spot) {
        ensure(level, spot.line(), spot.index(), spot.stage());
        Plan plan = plan(level, spot);
        Cell cell = plan.cellAt(player.blockPosition());
        if (cell != null) {
            visited(player, plan).add(cell);
            Kind kind = plan.kind(cell);
            boolean sealed = kind == Kind.SEAL && level.getBlockState(plan.sealPos(cell)).is(ModContent.PASSAGE_SEAL.get());
            boolean wakes = sealed || kind == Kind.ARENA || plan.patrols.contains(cell);
            if (wakes && !player.isSpectator() && !player.isCreative()) wake(level, plan, cell);
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
                    case EXIT -> sealsLeft(level, plan) > 0 ? "exit.sealed" : plan.last() ? "exit.open" : "exit.deeper";
                    default -> null;
                };
                if (hint != null) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "room." + hint), 0xFFFF55));
                if (kind == Kind.SEAL && sealed) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.SEAL);
            }
        }
        RealmSafety.updatePassageBar(player, plan, plan.seals.size() - sealsLeft(level, plan));
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
                level.sendParticles(ParticleTypes.END_ROD, player.getX() + dx / dist * i * 0.6, player.getY() + 1.2, player.getZ() + dz / dist * i * 0.6, 1, 0, 0, 0, 0);
            }
        }
        return best;
    }
}
