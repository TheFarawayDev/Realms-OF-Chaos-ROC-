package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the hub looks out on. Beyond each tower's gate, outside the plaza, a colossal statue of the tower's god
 * stands on a pedestal on its own floating rock, facing the plaza; and far off behind it, a little to one side, the
 * tower itself rises out of the haze - a model of it, not the real thing, banded floor by floor in the stone of each
 * of its stretches, lit windows, buttresses, cornices where its stone changes, and its summit crowned.
 *
 * <p>The statues are not built block by block by hand: each is a figure of a few simple solids - a robe that flares
 * to the ground, a bare chest under a sash, shoulders, arms, a bearded head, a crown - with its symbol raised in its
 * right hand, and the solids are cut into blocks at one and a half times their size. So the figure has the rounded
 * shapes of a carved one instead of the steps of a stacked one.
 */
public final class HubVista {
    private static final int Y = RealmBuilder.Y;
    /** How far out from the middle of the plaza a statue stands, and a tower. */
    public static final int STATUE_OUT = 60, TOWER_OUT = 125, TOWER_ASIDE = 36;
    /** A statue stands on a pedestal whose top is this far above the plaza. */
    public static final int STATUE_FOOT = 18;
    /** Blocks to a unit of the figure. */
    private static final double SCALE = 1.8;
    /** How tall the model of a tower is (fifteen floors of ten), and its radius at the foot. */
    public static final int TOWER_HEIGHT = 150;
    private static final double TOWER_RADIUS = 9.0;
    /** The model towers stand a little below the plaza: farther off, lower down. */
    public static final int TOWER_FOOT = -6;

    private HubVista() {}

    /** The direction from the middle of the plaza out towards a tower's gate. */
    public static Direction outward(Line line) {
        return switch (line) {
            case ZEUS -> Direction.NORTH;
            case POSEIDON -> Direction.EAST;
            case HADES -> Direction.WEST;
        };
    }

    /** Where a god's statue stands: the middle of the top of its pedestal. */
    public static BlockPos statueFoot(Line line) {
        return BlockPos.ZERO.relative(outward(line), STATUE_OUT).atY(Y + STATUE_FOOT);
    }

    /** Where the model of a tower stands: the middle of its foot. */
    public static BlockPos towerFoot(Line line) {
        Direction out = outward(line);
        return BlockPos.ZERO.relative(out, TOWER_OUT).relative(out.getClockWise(), TOWER_ASIDE).atY(Y + TOWER_FOOT);
    }

    public static void build(ServerLevel level) {
        underside(level);
        for (Line line : Line.values()) {
            statue(level, line);
            tower(level, line);
        }
    }

    /**
     * The hub's earlier designs put the statues and towers nearer in; a hub rebuilt from one of those would keep
     * them hanging in the sky beside the new ones. Whatever stands where they stood goes first.
     */
    public static void clearEarlier(ServerLevel level) {
        for (Line line : Line.values()) {
            Direction out = outward(line);
            BlockPos statue = BlockPos.ZERO.relative(out, 44).atY(Y);
            BlockPos tower = BlockPos.ZERO.relative(out, 105).relative(out.getClockWise(), 30).atY(Y - 6);
            clearBox(level, statue, 14, -40, 72);
            clearBox(level, tower, 22, -40, TOWER_HEIGHT + 22);
        }
    }

    private static void clearBox(ServerLevel level, BlockPos centre, int half, int below, int above) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int a = -half; a <= half; a++) for (int b = -half; b <= half; b++) {
            for (int y = below; y <= above; y++) {
                at.set(centre.getX() + a, centre.getY() + y, centre.getZ() + b);
                if (!level.getBlockState(at).isAir()) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // ---- shared -----------------------------------------------------------------------------------------------

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    /** A number in [0, 1) that is always the same for the same place. */
    private static double noise(int x, int y, int z) {
        long h = x * 3129871L ^ z * 116129781L ^ y * 7342477L;
        h = h * h * 42317861L + h * 11L;
        return Math.floorMod(h >> 16, 1000L) / 1000.0;
    }

    /**
     * A floating island: a disc of {@code top} at {@code surface}, and under it a rough cone of rock that narrows to
     * a point {@code depth} below, darker the deeper it goes.
     */
    private static void island(ServerLevel level, BlockPos surface, double radius, int depth, Block top, Block soil, Block rock, Block deep) {
        int r = (int) Math.ceil(radius) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = radius - 1.2 + noise(dx, 0, dz) * 1.6;
                if (d > edge) continue;
                double t = d / Math.max(1.0, edge);
                int down = (int) Math.round((1.0 - Math.pow(t, 1.4)) * depth * (0.75 + 0.5 * noise(dx, 7, dz))) + 1;
                for (int k = 0; k <= down; k++) {
                    BlockPos at = surface.offset(dx, -k, dz);
                    Block b = k == 0 ? top : k <= 2 ? soil : k > depth * 0.55 ? deep : rock;
                    if (k > 2 && noise(at.getX(), at.getY(), at.getZ()) < 0.12) b = k > depth * 0.55 ? rock : soil;
                    set(level, at, b);
                }
            }
        }
    }

    /** The plaza is not a slab hung in the sky but the top of an island of rock, its rim carried out on corbels. */
    private static void underside(ServerLevel level) {
        int R = RealmBuilder.HUB_RADIUS + 2;
        double rim = RealmBuilder.HUB_RADIUS + 1.5;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= rim) {
                    if (d < rim + 1) set(level, new BlockPos(dx, Y - 3, dz), Mason.stair(Blocks.STONE_BRICK_STAIRS, Mason.outward(dx, dz).getOpposite(), true));
                    continue;
                }
                double t = d / rim;
                int down = (int) Math.round((1.0 - Math.pow(t, 1.3)) * 40 * (0.8 + 0.4 * noise(dx, 3, dz))) + 1;
                for (int k = 3; k <= 3 + down; k++) {
                    BlockPos at = new BlockPos(dx, Y - k, dz);
                    Block b = k == 3 ? Blocks.STONE_BRICKS : k < 7 ? Blocks.STONE : k < 18 ? (noise(dx, k, dz) < 0.3 ? Blocks.ANDESITE : Blocks.STONE)
                            : (noise(dx, k, dz) < 0.3 ? Blocks.TUFF : Blocks.DEEPSLATE);
                    set(level, at, b);
                }
            }
        }
        // a few lights hung beneath, so the island is seen from the plaza's edge
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 8;
            BlockPos under = new BlockPos((int) Math.round(Math.cos(a) * (rim - 3)), Y - 4, (int) Math.round(Math.sin(a) * (rim - 3)));
            for (int k = 0; k < 6 && !level.getBlockState(under.below(k)).isAir(); k++) {
                if (level.getBlockState(under.below(k + 1)).isAir()) {
                    Mason.chandelier(level, under.below(k), 3, Blocks.LANTERN);
                    break;
                }
            }
        }
    }

    // ---- the statues ------------------------------------------------------------------------------------------

    /** The parts of a figure, each given its stone by the god. */
    private enum Part { ROBE, FOLD, SKIN, HAIR, TRIM, SYMBOL, SHAFT, CROWN, HELM }

    private record Stones(Block robe, Block fold, Block skin, Block hair, Block trim, Block symbol, Block shaft, Block crown, Block helm,
                          Block plinth, Block plinthTrim, Block rock, Block deep, Block top, Block light) {
        Block of(Part part) {
            return switch (part) {
                case ROBE -> robe;
                case FOLD -> fold;
                case SKIN -> skin;
                case HAIR -> hair;
                case TRIM -> trim;
                case SYMBOL -> symbol;
                case SHAFT -> shaft;
                case CROWN -> crown;
                case HELM -> helm;
            };
        }
    }

    private static Stones stones(Line line) {
        return switch (line) {
            case ZEUS -> new Stones(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR, Blocks.CALCITE, Blocks.POLISHED_DIORITE, Blocks.GOLD_BLOCK,
                    Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.QUARTZ_BRICKS,
                    Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.STONE, Blocks.DEEPSLATE, Blocks.MOSS_BLOCK, Blocks.GLOWSTONE);
            case POSEIDON -> new Stones(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.GOLD_BLOCK,
                    Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.PRISMARINE_BRICKS,
                    Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SANDSTONE, Blocks.PRISMARINE, Blocks.SAND, Blocks.SEA_LANTERN);
            case HADES -> new Stones(Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE,
                    Blocks.GOLD_BLOCK, Blocks.CRYING_OBSIDIAN, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                    Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SOUL_SOIL, Blocks.SHROOMLIGHT);
        };
    }

    // the solids a figure is made of, in its own units: x to its right, y up from its feet, z out in front of it

    private static boolean ellipsoid(double x, double y, double z, double cx, double cy, double cz, double rx, double ry, double rz) {
        double a = (x - cx) / rx, b = (y - cy) / ry, c = (z - cz) / rz;
        return a * a + b * b + c * c <= 1.0;
    }

    private static boolean capsule(double x, double y, double z, double ax, double ay, double az, double bx, double by, double bz, double r) {
        double vx = bx - ax, vy = by - ay, vz = bz - az, wx = x - ax, wy = y - ay, wz = z - az;
        double len = vx * vx + vy * vy + vz * vz;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / len));
        double dx = wx - vx * t, dy = wy - vy * t, dz = wz - vz * t;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** An upright solid whose level cuts are ellipses, growing or narrowing evenly from its foot to its head. */
    private static boolean frustum(double x, double y, double z, double y0, double y1, double rx0, double rz0, double rx1, double rz1, double cz) {
        if (y < y0 || y > y1) return false;
        double t = (y - y0) / (y1 - y0), rx = rx0 + (rx1 - rx0) * t, rz = rz0 + (rz1 - rz0) * t;
        double a = x / rx, c = (z - cz) / rz;
        return a * a + c * c <= 1.0;
    }

    /** What part of the god, if any, is at this point of the figure. Earlier tests win: a hand over a robe. */
    private static Part part(Line line, double x, double y, double z) {
        // the symbol in the raised right hand
        switch (line) {
            case ZEUS -> {
                double[][] bolt = {{3.9, 11.0}, {4.9, 13.4}, {3.5, 15.6}, {4.3, 17.6}, {3.3, 20.0}, {4.7, 22.2}, {3.9, 25.0}};
                for (int i = 0; i + 1 < bolt.length; i++) {
                    if (capsule(x, y, z, bolt[i][0], bolt[i][1], 0.3, bolt[i + 1][0], bolt[i + 1][1], 0.3, 0.45)) return Part.SYMBOL;
                }
            }
            case POSEIDON -> {
                if (capsule(x, y, z, 2.7, 22.5, 0.3, 5.1, 22.5, 0.3, 0.34)) return Part.SYMBOL;
                if (capsule(x, y, z, 2.7, 22.5, 0.3, 2.7, 24.8, 0.3, 0.34) || capsule(x, y, z, 5.1, 22.5, 0.3, 5.1, 24.8, 0.3, 0.34)
                        || capsule(x, y, z, 3.9, 22.5, 0.3, 3.9, 25.6, 0.3, 0.34)) return Part.SYMBOL;
                if (capsule(x, y, z, 3.9, 0.4, 0.3, 3.9, 22.5, 0.3, 0.34)) return Part.SHAFT;
            }
            case HADES -> {
                if (capsule(x, y, z, 3.1, 22.2, 0.3, 4.7, 22.2, 0.3, 0.34)) return Part.SYMBOL;
                if (capsule(x, y, z, 3.1, 22.2, 0.3, 3.1, 25.0, 0.3, 0.34) || capsule(x, y, z, 4.7, 22.2, 0.3, 4.7, 25.0, 0.3, 0.34)) return Part.SYMBOL;
                if (capsule(x, y, z, 3.9, 0.4, 0.3, 3.9, 22.2, 0.3, 0.34)) return Part.SHAFT;
            }
        }
        // what it wears on its head
        double ring = Math.sqrt(x * x + (z - 0.1) * (z - 0.1));
        switch (line) {
            case ZEUS -> {
                // a wreath of laurel
                if (Math.hypot(ring - 1.3, y - 16.6) < 0.42) return Part.CROWN;
            }
            case POSEIDON -> {
                // a crown of points
                if (Math.hypot(ring - 1.3, y - 16.9) < 0.38) return Part.CROWN;
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * (0.1 + 0.2 * i);
                    double px = Math.cos(a) * 1.2, pz = Math.sin(a) * 1.2 + 0.1;
                    if (capsule(x, y, z, px, 16.9, pz, px * 1.15, 18.5, pz * 1.1, 0.3)) return Part.CROWN;
                }
            }
            case HADES -> {
                // a helm with a crest
                if (capsule(x, y, z, 0, 18.0, -1.3, 0, 18.3, 1.0, 0.42)) return Part.CROWN;
                if (y > 15.6 && ellipsoid(x, y, z, 0, 16.2, 0.05, 1.45, 1.55, 1.45) && !(z > 0.9 && y < 16.8 && Math.abs(x) < 0.9)) return Part.HELM;
            }
        }
        // the head, its hair and its beard
        if (ellipsoid(x, y, z, 0, 14.6, 0.8, 1.05, 1.25, 0.75)) return Part.HAIR;
        if (ellipsoid(x, y, z, 0, 15.9, 0.1, 1.25, 1.55, 1.3)) return z < -0.25 || y > 16.9 ? Part.HAIR : Part.SKIN;
        if (capsule(x, y, z, 0, 13.0, 0, 0, 14.6, 0.1, 0.75)) return Part.SKIN;
        // the arms: the right raised to hold the symbol, the left lowered and held a little forward
        if (ellipsoid(x, y, z, 3.9, 17.6, 0.3, 0.8, 0.8, 0.8)) return Part.SKIN;
        if (capsule(x, y, z, 2.6, 12.8, 0, 3.6, 15.0, 0.2, 0.72) || capsule(x, y, z, 3.6, 15.0, 0.2, 3.9, 17.4, 0.3, 0.62)) return Part.SKIN;
        if (ellipsoid(x, y, z, -2.5, 8.3, 1.8, 0.75, 0.75, 0.75)) return Part.SKIN;
        if (capsule(x, y, z, -2.6, 12.8, 0, -3.2, 10.2, 0.3, 0.72)) return Part.FOLD;     // (the cloak falls over this shoulder)
        if (capsule(x, y, z, -3.2, 10.2, 0.3, -2.6, 8.6, 1.6, 0.62)) return Part.SKIN;
        // shoulders and chest, a sash from the left shoulder to the right hip
        boolean chest = frustum(x, y, z, 9.0, 13.2, 2.1, 1.35, 2.7, 1.5, 0)
                || ellipsoid(x, y, z, 2.5, 12.8, 0, 1.0, 0.9, 1.0) || ellipsoid(x, y, z, -2.5, 12.8, 0, 1.05, 0.95, 1.05);
        if (chest) {
            double sash = Math.abs((x + 0.1) + (y - 11.2) * 1.05) / Math.sqrt(1 + 1.05 * 1.05);
            if (x < -1.6 && y > 11.6) return Part.FOLD;
            return sash < 0.55 ? Part.FOLD : Part.SKIN;
        }
        // the robe, a belt at its top, a hem at its foot, folds running down it; feet in sandals under its hem
        if (frustum(x, y, z, 0, 9.2, 3.0, 2.1, 2.1, 1.4, 0)) {
            if (y > 8.6 || y < 0.6) return Part.TRIM;
            return Math.sin(Math.atan2(z, x) * 8 + y * 0.15) > 0.35 ? Part.FOLD : Part.ROBE;
        }
        if (ellipsoid(x, y, z, 1.0, 0.4, 1.9, 0.75, 0.55, 0.9) || ellipsoid(x, y, z, -1.0, 0.4, 1.9, 0.75, 0.55, 0.9)) return Part.SKIN;
        return null;
    }

    /** A colossus of the tower's god, on a pedestal on its own island, facing the plaza. */
    private static void statue(ServerLevel level, Line line) {
        Stones s = stones(line);
        BlockPos foot = statueFoot(line);
        Direction front = outward(line).getOpposite(), right = front.getClockWise();
        Mason.Stone cut = Mason.of(s.plinth());
        // clear the space it stands in, so a rebuild leaves nothing of an earlier one
        for (int a = -15; a <= 15; a++) for (int b = -15; b <= 15; b++) for (int y = -44; y <= 52; y++) {
            BlockPos at = foot.offset(a, y, b);
            if (!level.getBlockState(at).isAir()) set(level, at, Blocks.AIR);
        }
        // the island, and the pedestal on it: a stepped base, a die with the god's mark, a cornice
        island(level, foot.below(6), 13.5, 30, s.top(), line == Line.HADES ? Blocks.SOUL_SOIL : line == Line.POSEIDON ? Blocks.SANDSTONE : Blocks.DIRT, s.rock(), s.deep());
        for (int a = -8; a <= 8; a++) {
            for (int b = -8; b <= 8; b++) {
                int ring = Math.max(Math.abs(a), Math.abs(b));
                Direction out = Mason.outward(a, b);
                BlockPos at = foot.offset(a, 0, b);
                if (ring == 8) set(level, at.below(5), Mason.stair(cut.stairs(), out.getOpposite(), false));
                else set(level, at.below(5), s.plinthTrim());
                if (ring <= 7) set(level, at.below(4), ring == 7 ? Mason.stair(cut.stairs(), out.getOpposite(), false) : s.plinth().defaultBlockState());
                if (ring <= 6) {
                    for (int y = 3; y >= 2; y--) set(level, at.below(y), ring == 6 && Math.abs(a) == Math.abs(b) ? s.plinthTrim() : s.plinth());
                    set(level, at.below(1), s.plinthTrim());
                }
                if (ring == 7) set(level, at.below(1), Mason.stair(cut.stairs(), out.getOpposite(), true));
            }
        }
        // the god's mark on the face of the die that looks at the plaza, and a lamp at each corner of the top
        BlockPos face = foot.relative(front, 6);
        for (int k = -1; k <= 1; k++) set(level, face.relative(right, k).below(3), k == 0 ? s.light() : s.plinthTrim());
        set(level, face.below(2), s.trim());
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            set(level, foot.offset(a * 6, 0, b * 6), s.plinthTrim());
            set(level, foot.offset(a * 6, 1, b * 6), s.light());
        }
        // the figure
        for (int bx = -11; bx <= 14; bx++) {
            for (int by = 0; by <= 49; by++) {
                for (int bz = -6; bz <= 8; bz++) {
                    Part p = part(line, bx / SCALE, by / SCALE, bz / SCALE);
                    if (p == null) continue;
                    set(level, foot.relative(right, bx).relative(front, bz).above(by), s.of(p));
                }
            }
        }
        // Hades' eyes burn
        if (line == Line.HADES) {
            for (int side = -1; side <= 1; side += 2) {
                BlockPos eye = foot.relative(right, (int) Math.round(side * 0.5 * SCALE)).relative(front, 2).above((int) Math.round(16.1 * SCALE));
                set(level, eye, Blocks.SEA_LANTERN);
            }
        }
    }

    // ---- the towers -------------------------------------------------------------------------------------------

    /** A model of a tower far off: its stretches in their own stone, lit, buttressed, corniced, crowned. */
    private static void tower(ServerLevel level, Line line) {
        Stones s = stones(line);
        BlockPos foot = towerFoot(line);
        Direction front = Direction.getApproximateNearest(-foot.getX(), 0, -foot.getZ());
        int reach = 22;
        for (int a = -reach; a <= reach; a++) for (int b = -reach; b <= reach; b++) for (int y = -40; y <= TOWER_HEIGHT + 22; y++) {
            BlockPos at = foot.offset(a, y, b);
            if (!level.getBlockState(at).isAir()) set(level, at, Blocks.AIR);
        }
        island(level, foot.below(), 19, 34, s.top(), line == Line.HADES ? Blocks.SOUL_SOIL : line == Line.POSEIDON ? Blocks.SANDSTONE : Blocks.DIRT, s.rock(), s.deep());
        int R = (int) Math.ceil(TOWER_RADIUS) + 3;
        for (int h = 0; h <= TOWER_HEIGHT; h++) {
            int floor = Math.min(Tower.FLOORS, h / 10 + 1), inFloor = h % 10;
            TowerBuilder.Palette p = TowerBuilder.palette(line, Tower.tier(floor));
            Mason.Stone stone = Mason.of(p.wall());
            double r = TOWER_RADIUS - h * 0.012;
            boolean tierStarts = h > 0 && Tower.tier(floor) != Tower.tier(Math.max(1, (h - 1) / 10 + 1));
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz), ang = Math.atan2(dz, dx);
                    BlockPos at = foot.offset(dx, h, dz);
                    Direction out = Mason.outward(dx, dz);
                    if (d <= r && d > r - 1.7) {
                        // the shell: a band of trim at every floor, lit windows in it, the stone weathered
                        Block b = inFloor == 0 ? p.trim() : Mason.weather(p.wall(), at.getX(), at.getY(), at.getZ(), 20);
                        double sector = (ang / (Math.PI * 2) * 8 + (floor & 1) * 0.5 + 8) % 1.0;
                        if (inFloor >= 4 && inFloor <= 7 && Math.abs(sector - 0.5) < 0.14 && d > r - 1.0) b = inFloor == 7 ? p.trim() : p.light();
                        set(level, at, b);
                    } else if (d <= r - 1.7 && inFloor == 0) {
                        set(level, at, p.fill());                   // floors inside, so no light shows through it
                    } else if (d > r && d <= r + 1.5) {
                        // buttresses between the windows, the full height
                        double rib = (ang / (Math.PI * 2) * 8 + 8) % 1.0;
                        if (Math.min(rib, 1 - rib) * Math.PI * 2 / 8 * d < 0.75) set(level, at, inFloor == 0 ? p.trim() : p.pillar());
                        else if (tierStarts || h == TOWER_HEIGHT) set(level, at, Mason.stair(stone.stairs(), out.getOpposite(), true));     // a cornice
                        else if (inFloor == 0 && floor % 5 == 0 && d <= r + 1.0) set(level, at, Mason.slab(stone.slab(), true));
                    }
                }
            }
        }
        // the door, facing the plaza, under a round head
        Direction side = front.getClockWise();
        BlockPos door = foot.relative(front, (int) TOWER_RADIUS - 1);
        Mason.Stone base = Mason.of(TowerBuilder.palette(line, 1).wall());
        for (int k = -1; k <= 1; k++) {
            for (int y = 1; y <= 4; y++) set(level, door.relative(side, k).above(y), Blocks.AIR);
            set(level, door.relative(front).relative(side, k), base.block());
            set(level, door.relative(front, 2).relative(side, k), Mason.stair(base.stairs(), front.getOpposite(), false));
        }
        for (int k = -1; k <= 1; k += 2) set(level, door.relative(side, k).above(4), Mason.stair(base.stairs(), k < 0 ? side.getOpposite() : side, true));
        // the summit: a crown of battlements, eight pinnacles, and a light at its heart
        TowerBuilder.Palette top = TowerBuilder.palette(line, 4);
        Mason.Stone topStone = Mason.of(top.wall());
        int H = TOWER_HEIGHT;
        double r = TOWER_RADIUS - H * 0.012;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                BlockPos at = foot.offset(dx, H, dz);
                if (d <= r - 1.7) set(level, at, top.top());
                if (d <= r && d > r - 1.2) {
                    set(level, at.above(), top.trim());
                    if (((dx + dz) & 1) == 0) set(level, at.above(2), top.wall());
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            BlockPos pin = foot.offset((int) Math.round(Math.cos(a) * (r + 0.6)), H, (int) Math.round(Math.sin(a) * (r + 0.6)));
            for (int y = 1; y <= 6; y++) set(level, pin.above(y), y == 3 ? top.trim() : top.pillar());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pin.above(6).relative(d)).isAir()) set(level, pin.above(6).relative(d), Mason.stair(topStone.stairs(), d.getOpposite(), true));
            }
            set(level, pin.above(7), top.light());
            set(level, pin.above(8), line == Line.ZEUS ? Blocks.END_ROD : line == Line.HADES ? Blocks.SOUL_SOIL : top.trim());
            if (line == Line.HADES) set(level, pin.above(9), Blocks.SOUL_FIRE);
        }
        for (int y = 1; y <= 9; y++) set(level, foot.above(H + y), y == 9 ? top.light() : top.pillar());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            set(level, foot.above(H + 1).relative(d), Mason.stair(topStone.stairs(), d.getOpposite(), false));
            set(level, foot.above(H + 8).relative(d), Mason.stair(topStone.stairs(), d.getOpposite(), true));
        }
        set(level, foot.above(H + 10), line == Line.ZEUS ? Blocks.END_ROD : line == Line.HADES ? Blocks.SOUL_SOIL : Blocks.SEA_LANTERN);
        if (line == Line.HADES) set(level, foot.above(H + 11), Blocks.SOUL_FIRE);
        // clouds drift about the Sky's and the Tides' towers; the Shades' is wreathed in nothing
        if (line != Line.HADES) {
            int[][] clouds = {{14, 46, 6}, {-12, 78, -9}, {9, 112, -13}, {-15, 128, 10}, {3, 64, 16}};
            for (int[] c : clouds) cloud(level, foot.offset(c[0], c[1], c[2]));
        }
    }

    /** A puff of cloud: flat beneath, heaped above, ragged at its edges. */
    private static void cloud(ServerLevel level, BlockPos centre) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    double e = (dx * dx) / 36.0 + (dz * dz) / 16.0 + (dy * dy) / 9.0;
                    BlockPos at = centre.offset(dx, dy, dz);
                    if (e > 1.0 || (e > 0.6 && noise(at.getX(), at.getY(), at.getZ()) < 0.45)) continue;
                    if (level.getBlockState(at).isAir()) set(level, at, dy == 0 ? Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY) : Blocks.WOOL.white());
                }
            }
        }
    }
}
