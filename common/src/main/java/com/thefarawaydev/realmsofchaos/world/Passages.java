package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The passages: a level to explore between every two floors of a tower. Passage {@code n} lies between floor
 * {@code n} and floor {@code n + 1}; the Gate of Ascent on floor n leads into it the first time, and its own gate
 * leads on to floor n + 1 once every seal in it has been broken.
 *
 * A passage is a maze of rooms laid out procedurally from the world seed, so every world's towers are different:
 * sealed chambers with guardians, treasure rooms, a shrine, trapped rooms, plain halls and corridors. It is built
 * out to the side of the tower, in the tower's own dimension, the first time someone enters it.
 */
public final class Passages {
    public static final int COUNT = Tower.FLOORS - 1;
    /** Floor level of every passage. */
    public static final int Y = 80;
    /** One cell of the maze: a 9x9 room and the walls round it. */
    public static final int CELL = 12, ROOM = 9, CENTRE = 5;
    public static final int HEADROOM = 5;
    public static final int X0 = 600, PITCH = 160;
    public static final int LAYOUT_VERSION = 2;
    public static final String GUARDIAN_TAG = "realms_of_chaos_guardian";
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".passage.";

    public enum Kind { START, EXIT, SEAL, TREASURE, SHRINE, HALL, ROOM, TRAP }

    public record Cell(int x, int z) {}

    /** A player's place in the passages: which tower, which passage. */
    public record Spot(Line line, int index) {}

    private Passages() {}

    // ---- the plan --------------------------------------------------------------------------------------------

    /** The layout of one passage. Pure data, worked out from the seed alone; building it is a separate step. */
    public static final class Plan {
        public final Line line;
        public final int index, size, tier;
        public final long seed;
        private final Kind[][] kinds;
        /** east[x][z]: a doorway between (x, z) and (x + 1, z). south[x][z]: between (x, z) and (x, z + 1). */
        private final boolean[][] east, south;
        public Cell start, exit;
        public final List<Cell> seals = new ArrayList<>();
        /** Ordinary rooms and corridors that a patrol of sentinels walks. */
        public final java.util.Set<Cell> patrols = new java.util.LinkedHashSet<>();

        Plan(long worldSeed, Line line, int index) {
            this.line = line;
            this.index = index;
            this.tier = Tower.tier(index);
            this.size = tier == 1 ? 4 : 5;
            this.seed = worldSeed ^ (0x9E3779B97F4A7C15L * (line.ordinal() * 100L + index + 1));
            this.kinds = new Kind[size][size];
            this.east = new boolean[size][size];
            this.south = new boolean[size][size];
            generate(new Random(seed));
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
            for (int i = 0; i < (tier == 1 ? 1 : 2); i++) {
                Cell c = free.remove(0);
                kinds[c.x][c.z] = Kind.TREASURE;
            }
            for (Cell c : free) {
                float roll = random.nextFloat();
                kinds[c.x][c.z] = roll < 0.40F ? Kind.HALL : roll < 0.75F ? Kind.ROOM : Kind.TRAP;
            }
            // about a third of the ordinary rooms have a patrol in them
            for (Cell c : free) {
                if (random.nextFloat() < 0.35F) patrols.add(c);
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
                    });
                    if (x + 1 < size) rooms.append(east[x][z] ? "--" : "  ");
                    below.append(z + 1 < size && south[x][z] ? "|  " : "   ");
                }
                rows.add(rooms.toString());
                if (z + 1 < size) rows.add(below.toString().stripTrailing());
            }
            return rows;
        }

        /** The low north-west corner of the passage's box. */
        public BlockPos origin() {
            return new BlockPos(X0 + index * PITCH, Y, -width() / 2);
        }

        /** The floor block at the middle of a cell. */
        public BlockPos centre(Cell c) {
            BlockPos o = origin();
            return new BlockPos(o.getX() + c.x * CELL + CENTRE, Y, o.getZ() + c.z * CELL + CENTRE);
        }

        public Vec3 arrival() {
            BlockPos c = centre(start);
            return new Vec3(c.getX() + 0.5, Y + 1, c.getZ() + 0.5);
        }

        public BlockPos sealPos(Cell c) {
            return centre(c).above(2);
        }

        /** The treasure chest or the shrine of a cell stands on its centre. */
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
            return new AABB(m.getX() - 4, Y, m.getZ() - 4, m.getX() + 5, Y + HEADROOM + 1, m.getZ() + 5);
        }

        public AABB box() {
            BlockPos o = origin();
            return new AABB(o.getX(), Y - 1, o.getZ(), o.getX() + width(), Y + HEADROOM + 2, o.getZ() + width());
        }
    }

    private static final Map<String, Plan> PLANS = new HashMap<>();

    public static Plan plan(long worldSeed, Line line, int index) {
        return PLANS.computeIfAbsent(worldSeed + "/" + line.id() + "/" + index, k -> new Plan(worldSeed, line, index));
    }

    public static Plan plan(ServerLevel level, Line line, int index) {
        return plan(level.getSeed(), line, index);
    }

    // ---- where things are ------------------------------------------------------------------------------------

    /** Which passage a spot in a tower's dimension belongs to, or 0 for the tower itself. */
    public static int indexAt(double x) {
        int index = (int) Math.floor((x - X0 + 40) / PITCH);
        if (index < 1 || index > COUNT) return 0;
        double from = X0 + index * PITCH;
        return x >= from - 8 && x <= from + 5 * CELL + 8 ? index : 0;
    }

    /** The passage a player is standing in, or null. */
    public static Spot locate(ServerPlayer player) {
        Realm realm = Realm.of(player.level().dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(player.getX());
        return index == 0 ? null : new Spot(realm.line(), index);
    }

    private static String builtKey(Line line, int index) {
        return "passage/" + line.id() + "/" + index + "@" + LAYOUT_VERSION;
    }

    // ---- building --------------------------------------------------------------------------------------------

    public static void ensure(ServerLevel level, Line line, int index) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = builtKey(line, index);
        if (data.isBuilt(key)) return;
        build(level, plan(level, line, index));
        data.markBuilt(key);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    /** Builds the whole passage: a solid block of wall, carved into rooms and halls, then furnished. */
    public static void build(ServerLevel level, Plan plan) {
        TowerBuilder.Palette p = TowerBuilder.palette(plan.line, plan.tier);
        Random random = new Random(plan.seed * 31 + 7);
        BlockPos o = plan.origin();
        int w = plan.width();
        // stale guardians of an earlier layout go first
        for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                for (int y = -1; y <= HEADROOM + 1; y++) {
                    set(level, o.offset(x, y, z), y <= 0 || y == HEADROOM + 1 ? p.fill() : p.wall());
                }
            }
        }
        for (Cell c : plan.cells()) carve(level, plan, c, p, random);
        // doorways: three wide, three high, through the wall between two cells
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
    }

    private static void carve(ServerLevel level, Plan plan, Cell c, TowerBuilder.Palette p, Random random) {
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
        // a lamp in the ceiling of every cell, and in the corners of the proper rooms
        set(level, m.above(HEADROOM + 1), p.light());
        if (!hall) {
            for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) set(level, m.offset(k[0], HEADROOM + 1, k[1]), p.light());
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
                for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    for (int y = 1; y <= HEADROOM; y++) set(level, m.offset(k[0], y, k[1]), p.pillar());
                }
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) set(level, m.offset(dx, 0, dz), p.inlay());
            }
            case TREASURE -> {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, m.offset(dx, 0, dz), p.inlay());
                set(level, plan.featurePos(c), ModContent.PASSAGE_CACHE.get());
                for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    set(level, m.offset(k[0], 1, k[1]), p.trim());
                    set(level, m.offset(k[0], 2, k[1]), p.light());
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
            case ROOM -> {
                switch (random.nextInt(4)) {
                    case 0 -> {
                        for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                            for (int y = 1; y <= HEADROOM; y++) set(level, m.offset(k[0], y, k[1]), p.pillar());
                        }
                    }
                    case 1 -> {
                        for (int[] k : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {-3, -4}, {3, -4}, {-3, 4}, {3, 4}}) {
                            set(level, m.offset(k[0], 1, k[1]), Blocks.BOOKSHELF);
                            set(level, m.offset(k[0], 2, k[1]), Blocks.BOOKSHELF);
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

    /** One player's share of a passage's treasure: always some of this tier's forge material, then a mixed handful. */
    public static List<ItemStack> lootFor(Plan plan, Cell cell, UUID player) {
        Random random = new Random(plan.seed ^ player.getLeastSignificantBits() ^ (cell.x * 131L + cell.z * 17L + 5));
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier), 1 + random.nextInt(2)));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3) + plan.tier));
        loot.add(new ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(4)));
        Item[] metal = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        loot.add(new ItemStack(metal[plan.tier - 1], 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.6F) loot.add(new ItemStack(Items.ARROW, 6 + random.nextInt(11)));
        if (random.nextFloat() < 0.25F + 0.15F * plan.tier) loot.add(new ItemStack(Items.GOLDEN_APPLE));
        if (random.nextFloat() < 0.35F) loot.add(new ItemStack(Items.ENDER_PEARL, 1 + random.nextInt(2)));
        if (plan.tier >= 2 && random.nextFloat() < 0.4F) loot.add(new ItemStack(ModContent.materialItem(plan.line, plan.tier - 1), 1 + random.nextInt(2)));
        if (plan.line == Line.HADES && random.nextFloat() < 0.5F) {
            Tarot card = Tarot.values()[random.nextInt(Tarot.values().length)];
            if (card != Tarot.DEVIL) loot.add(new ItemStack(ModContent.tarotItem(card)));
        }
        return loot;
    }

    private static String lootKey(Plan plan, Cell cell, UUID player) {
        return "looted/" + player + "/" + plan.line.id() + "/" + plan.index + "/" + cell.x + "/" + cell.z;
    }

    public static boolean hasLooted(ServerLevel level, Plan plan, Cell cell, UUID player) {
        return ProgressData.get(level.getServer()).isBuilt(lootKey(plan, cell, player));
    }

    /**
     * Right-clicking a treasure cache: the player takes their own share, once. Everybody gets one, whoever came
     * first. @return true if a share was handed over.
     */
    public static boolean loot(ServerLevel level, BlockPos pos, ServerPlayer player) {
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".cache.";
        Plan plan = planAt(level, pos);
        Cell cell = plan == null ? null : plan.cellAt(pos);
        if (cell == null || plan.kind(cell) != Kind.TREASURE) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "empty"), 0xAAAAAA));
            return false;
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

    // ---- seals and guardians ---------------------------------------------------------------------------------

    public static int sealsLeft(ServerLevel level, Plan plan) {
        int n = 0;
        for (Cell c : plan.seals) if (level.getBlockState(plan.sealPos(c)).is(ModContent.PASSAGE_SEAL.get())) n++;
        return n;
    }

    /** How many sentinels guard a seal when one player comes: a Keeper and one more for each tier. */
    public static int guardianCount(int tier) {
        return 2 + tier;
    }

    /** The sentinels of a room that are still standing. */
    public static List<Mob> guardians(ServerLevel level, Plan plan, Cell c) {
        return level.getEntitiesOfClass(Mob.class, plan.cellBox(c).inflate(6, 2, 6), m -> m.isAlive() && m.entityTags().contains(GUARDIAN_TAG + "/" + c.x + "/" + c.z));
    }

    private static String wokenKey(Plan plan) {
        return builtKey(plan.line, plan.index) + "/woken/";
    }

    /** Who a room holds: a Keeper and its guard for a sealed chamber, one or two sentinels for a patrol. */
    public static List<com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind> garrison(Plan plan, Cell c, int players) {
        List<com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind> out = new ArrayList<>();
        Random random = new Random(plan.seed + c.x * 131L + c.z * 17L);
        var roles = new ArrayList<>(List.of(com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.SOLDIER, com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.CASTER));
        if (plan.tier >= 2) roles.add(com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.BRUTE);
        int others;
        if (plan.kind(c) == Kind.SEAL) {
            out.add(com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind.of(plan.line, com.thefarawaydev.realmsofchaos.entity.Sentinel.Role.KEEPER));
            others = guardianCount(plan.tier) - 1 + Adapt.extraGuardians(players);
        } else {
            others = (plan.tier >= 3 ? 2 : 1) + Adapt.extraGuardians(players);
        }
        for (int i = 0; i < others; i++) out.add(com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind.of(plan.line, roles.get(random.nextInt(roles.size()))));
        return out;
    }

    /** True for rooms that have sentinels to wake: sealed chambers and patrolled rooms. */
    public static boolean garrisoned(Plan plan, Cell c) {
        return plan.kind(c) == Kind.SEAL || plan.patrols.contains(c);
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
        // a corridor is only three wide: its sentinels stand in the crossing;
        // and in a room they stand on the lanes that run door to door, which nothing is ever built on
        int[][] spots = plan.kind(c) == Kind.HALL ? new int[][]{{1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}}
                : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 1}, {-3, -1}, {1, -3}, {-1, 3}};
        int woken = 0;
        for (com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind kind : garrison(plan, c, players)) {
            com.thefarawaydev.realmsofchaos.entity.Sentinel mob = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
            if (mob == null) continue;
            BlockPos at = m.offset(spots[woken % spots.length][0], 1, spots[woken % spots.length][1]);
            mob.setUp(kind, plan.tier);
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
     * The passage seals itself again: every seal is restored and every room's sentinels will wake afresh. This is
     * how a passage adapts to a player who has not walked it yet arriving after others broke it open.
     */
    public static void rearm(ServerLevel level, Plan plan) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, plan.box().inflate(2), e -> e.entityTags().contains(GUARDIAN_TAG))) m.discard();
        for (Cell c : plan.seals) set(level, plan.sealPos(c), ModContent.PASSAGE_SEAL.get());
        ProgressData.get(level.getServer()).unmarkPrefix(wokenKey(plan));
    }

    /** The passage a block belongs to, with its plan - or null if it is not in one. */
    public static Plan planAt(ServerLevel level, BlockPos pos) {
        Realm realm = Realm.of(level.dimension());
        if (realm == null || realm.line() == null) return null;
        int index = indexAt(pos.getX());
        return index == 0 ? null : plan(level, realm.line(), index);
    }

    /** The seal at {@code pos} gives way. When it was the last one, the passage's gate opens. */
    public static void breakSeal(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        if (!level.getBlockState(pos).is(ModContent.PASSAGE_SEAL.get())) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.3, 0.3, 0.3, 0.15);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 80, 0.6, 0.6, 0.6, 0.5);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 1.2F);
        Plan plan = planAt(level, pos);
        if (plan == null) return;
        ExperienceOrb.award(level, new Vec3(x, y, z), 12 * plan.tier);
        int left = sealsLeft(level, plan);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, plan.box().inflate(4), e -> true)) {
            if (left > 0) {
                Hud.actionBar(p, Hud.colored(Component.translatable(HUD + "seal.broken", left), 0x55FFFF));
            } else {
                Hud.title(p, Hud.colored(Component.translatable(HUD + "open"), plan.line.color()),
                        Hud.colored(Component.translatable(HUD + "open.sub", plan.index + 1), 0xCCCCCC));
                level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
        }
        if (left == 0) {
            for (BlockPos g : plan.gatePositions()) level.sendParticles(ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ---- coming and going ------------------------------------------------------------------------------------

    /** Sends a player into passage {@code index} of the tower they are in. */
    public static void enter(ServerPlayer player, Line line, int index) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ServerLevel level = Realm.ofLine(line).level(server);
        if (level == null) return;
        ensure(level, line, index);
        Plan plan = plan(level, line, index);
        // someone who has not walked this passage finds it sealed, even if others broke it open before - unless
        // those others are still inside, in which case they are simply joined
        boolean others = !level.getEntitiesOfClass(ServerPlayer.class, plan.box(), p -> p != player && p.isAlive() && !p.isSpectator()).isEmpty();
        boolean rearmed = false;
        if (!ProgressData.get(server).hasPassed(player.getUUID(), line, index) && sealsLeft(level, plan) == 0 && !others) {
            rearm(level, plan);
            rearmed = true;
        }
        Teleports.moveTo(player, level, plan.arrival(), 0.0F);
        visited(player, plan).add(plan.start);
        int left = sealsLeft(level, plan);
        Hud.title(player, Hud.colored(Component.translatable(HUD + "title", index + 1), line.color()),
                Hud.colored(left > 0 ? Component.translatable(HUD + "sub", left) : Component.translatable(HUD + "sub.open"), 0xCCCCCC));
        if (rearmed) Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".passage.rearmed"), 0xFFAA00));
    }

    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    /**
     * The gate at the end of a passage. @return true if the player went through to the next floor; false while
     * seals still hold it shut.
     */
    public static boolean tryExit(ServerPlayer player, Spot spot) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) return false;
        Plan plan = plan(level, spot.line(), spot.index());
        int left = sealsLeft(level, plan);
        if (left > 0) {
            long now = server.getTickCount();
            Long last = LAST_NOTICE.get(player.getUUID());
            if (last == null || now < last || now - last >= 40) {
                LAST_NOTICE.put(player.getUUID(), now);
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "sealed", left), 0xFF5555));
            }
            return false;
        }
        ProgressData data = ProgressData.get(server);
        boolean first = data.markPassed(player.getUUID(), spot.line(), spot.index());
        if (first) player.giveExperiencePoints(20 + 15 * plan.tier);
        Teleports.toFloor(player, spot.line(), spot.index() + 1);
        return Realm.of(player.level().dimension()) != null && indexAt(player.getX()) == 0;
    }

    // ---- once a second, for everyone inside ------------------------------------------------------------------

    private static final Map<UUID, String> LAST_CELL = new HashMap<>();
    /** The rooms each player has walked into, per passage: what their map shows. Kept for the session. */
    private static final Map<String, java.util.Set<Cell>> VISITED = new HashMap<>();

    public static java.util.Set<Cell> visited(ServerPlayer player, Plan plan) {
        return VISITED.computeIfAbsent(player.getUUID() + "/" + plan.seed, k -> new java.util.HashSet<>());
    }

    /** Wakes guardians as players walk in, names the room they enter, and keeps the passage bar current. */
    public static void service(ServerPlayer player, ServerLevel level, Spot spot) {
        ensure(level, spot.line(), spot.index());
        Plan plan = plan(level, spot.line(), spot.index());
        Cell cell = plan.cellAt(player.blockPosition());
        if (cell != null) {
            visited(player, plan).add(cell);
            Kind kind = plan.kind(cell);
            boolean sealed = kind == Kind.SEAL && level.getBlockState(plan.sealPos(cell)).is(ModContent.PASSAGE_SEAL.get());
            if ((sealed || plan.patrols.contains(cell)) && !player.isSpectator() && !player.isCreative()) wake(level, plan, cell);
            String here = spot.line().id() + "/" + spot.index() + "/" + cell.x + "/" + cell.z;
            if (!here.equals(LAST_CELL.put(player.getUUID(), here))) {
                String hint = switch (kind) {
                    case SEAL -> sealed ? "seal" : "seal.done";
                    case TREASURE -> "treasure";
                    case SHRINE -> "shrine";
                    case TRAP -> "trap";
                    case EXIT -> sealsLeft(level, plan) > 0 ? "exit.sealed" : "exit.open";
                    default -> null;
                };
                if (hint != null) Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "room." + hint), 0xFFFF55));
            }
        }
        RealmSafety.updatePassageBar(player, plan, plan.seals.size() - sealsLeft(level, plan));
    }

    /**
     * The Compass of Chaos inside a passage: which way to the nearest unbroken seal, or to the gate once they are
     * all broken. @return the place pointed at.
     */
    public static BlockPos point(ServerPlayer player, ServerLevel level, Spot spot) {
        Plan plan = plan(level, spot.line(), spot.index());
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
        String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        // 0 = south (+z), turning clockwise seen from above: west is -x
        int dir = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + (toGate ? "compass.gate" : "compass.seal"), (int) Math.round(dist),
                Component.translatable(HUD + "dir." + names[dir])), plan.line.color()));
        if (dist > 0.5) {
            for (int i = 1; i <= 8; i++) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX() + dx / dist * i * 0.6, player.getY() + 1.2, player.getZ() + dz / dist * i * 0.6, 1, 0, 0, 0, 0);
            }
        }
        return best;
    }
}
