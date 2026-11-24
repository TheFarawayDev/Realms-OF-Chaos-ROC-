package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds tower floors on demand, the first time a player reaches them. Every floor combines a terrain
 * {@link Layout} with the material {@link Palette} of its tier, so all 15 floors of a tower differ.
 *
 * Rules that keep fights fair on any terrain: neighbouring columns never differ by more than one block, nothing
 * solid stands inside the fight ring, the entrance and exit corridors are flat, and the whole floor is enclosed.
 */
public final class TowerBuilder {
    public static final int LAYOUT_VERSION = 9;

    public enum Layout { FLAT, MOUNDS, PILLARS, PIT, DAIS, WAVES, MOAT, TERRACES, SPLIT, ZIGGURAT, SUMMIT }

    private static final Layout[] BY_FLOOR = {
            Layout.FLAT, Layout.MOUNDS, Layout.PILLARS, Layout.PIT, Layout.DAIS,            // tier 1
            Layout.WAVES, Layout.MOAT, Layout.TERRACES, Layout.SPLIT, Layout.ZIGGURAT,      // tier 2
            Layout.PILLARS, Layout.MOUNDS, Layout.TERRACES, Layout.MOAT,                    // tier 3
            Layout.SUMMIT                                                                   // the final god
    };

    /** The layout each floor shifts into during a trial; chosen so the change is dramatic (a pit becomes a dais...). */
    private static final Layout[] ALT_BY_FLOOR = {
            Layout.DAIS, Layout.PIT, Layout.WAVES, Layout.DAIS, Layout.TERRACES,
            Layout.ZIGGURAT, Layout.SPLIT, Layout.ZIGGURAT, Layout.WAVES, Layout.PIT,
            Layout.TERRACES, Layout.SPLIT, Layout.MOUNDS, Layout.ZIGGURAT,
            Layout.TERRACES
    };
    /** A shift moves every column at most one block per step, so it takes this many steps at most. */
    public static final int MORPH_STEPS = 4;

    /** Terrain height of a column relative to the floor's walking level. */
    public interface HeightFn { int at(int x, int z); }

    record Palette(Block top, Block topAlt, Block fill, Block wall, Block trim, Block pillar,
                           Block light, Block inlay, Block glass, Block fluid) {}

    private TowerBuilder() {}

    public static Layout layout(int floor) {
        return BY_FLOOR[Tower.clampFloor(floor) - 1];
    }

    public static Layout altLayout(int floor) {
        return ALT_BY_FLOOR[Tower.clampFloor(floor) - 1];
    }

    /**
     * Height of a column while a floor is {@code step} steps (0..{@value #MORPH_STEPS}) of the way from its own
     * layout to its alternate one. Every column moves one block per step towards its target.
     */
    public static int morphHeight(int floor, int x, int z, int step) {
        int a = height(layout(floor), floor, x, z), b = height(altLayout(floor), floor, x, z);
        return a + Math.max(-step, Math.min(step, b - a));
    }

    public static HeightFn heights(int floor, int step) {
        return (x, z) -> morphHeight(floor, x, z, step);
    }

    static Palette palette(Line line, int tier) {
        return switch (line) {
            case ZEUS -> switch (tier) {
                case 1 -> new Palette(Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                        Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_LOG, Blocks.GLOWSTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GLASS, Blocks.WATER);
                case 2 -> new Palette(ModContent.OLYMPIAN_TILES.get(), Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK, ModContent.OLYMPIAN_MARBLE.get(),
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.WATER);
                case 3 -> new Palette(ModContent.CLOUD_TILE.get(), Blocks.CALCITE, Blocks.CALCITE, ModContent.CLOUDSTONE.get(),
                        Blocks.GOLD_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.POLISHED_DIORITE, Blocks.GLASS, Blocks.WATER);
                default -> new Palette(ModContent.CLOUD_TILE.get(), Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_BLOCK, ModContent.OLYMPIAN_MARBLE.get(),
                        Blocks.GOLD_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.WATER);
            };
            case POSEIDON -> switch (tier) {
                case 1 -> new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
                        Blocks.CHISELED_SANDSTONE, Blocks.PRISMARINE, Blocks.SEA_LANTERN, Blocks.PRISMARINE, Blocks.GLASS, Blocks.WATER);
                case 2 -> new Palette(ModContent.ABYSSAL_TILE.get(), Blocks.PRISMARINE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
                        Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE, Blocks.GLASS, Blocks.WATER);
                case 3 -> new Palette(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, ModContent.TRENCH_STONE.get(),
                        Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, Blocks.CHISELED_DEEPSLATE, Blocks.TINTED_GLASS, Blocks.WATER);
                default -> new Palette(ModContent.NACRE_TILE.get(), Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, ModContent.TRENCH_STONE.get(),
                        Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.LAPIS_BLOCK, Blocks.GLASS, Blocks.WATER);
            };
            case HADES -> switch (tier) {
                case 1 -> new Palette(Blocks.SOUL_SOIL, Blocks.COARSE_DIRT, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT,
                        Blocks.BONE_BLOCK, Blocks.POLISHED_BASALT, Blocks.SHROOMLIGHT, Blocks.BONE_BLOCK, Blocks.TINTED_GLASS, Blocks.LAVA);
                case 2 -> new Palette(Blocks.BLACKSTONE, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, ModContent.UNDERWORLD_BRICK.get(),
                        Blocks.RED_NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.GILDED_BLACKSTONE, Blocks.TINTED_GLASS, Blocks.LAVA);
                case 3 -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, ModContent.TARTARUS_STONE.get(),
                        Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.SHROOMLIGHT, Blocks.AMETHYST_BLOCK, Blocks.TINTED_GLASS, Blocks.LAVA);
                default -> new Palette(ModContent.THRONE_TILE.get(), Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE, ModContent.TARTARUS_STONE.get(),
                        Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.GILDED_BLACKSTONE, Blocks.TINTED_GLASS, Blocks.LAVA);
            };
        };
    }

    public static void ensureFloor(ServerLevel level, Line line, int floor) {
        floor = Tower.clampFloor(floor);
        ProgressData data = ProgressData.get(level.getServer());
        Realm realm = Realm.ofLine(line);
        String cleanup = realm.id() + "/cleanup@3";      // fixed key: the pre-tower arena is removed once, ever
        if (!data.isBuilt(cleanup)) {
            clearOldArena(level);
            data.markBuilt(cleanup);
        }
        String key = floorKey(line, floor);
        if (data.isBuilt(key)) {
            // a floor left mid-shift by a server stop is put back to its own design
            FloorShifts.repairIfStale(level, line, floor);
            Arena.repairIfStale(level, line, floor);
            return;
        }
        buildFloor(level, line, floor);
        data.markBuilt(key);
    }

    public static String floorKey(Line line, int floor) {
        return Realm.ofLine(line).id() + "/floor" + Tower.clampFloor(floor) + "@" + LAYOUT_VERSION;
    }

    /** Versions before 1.2 had one flat arena per realm at y 61..102; remove what a tower will not overwrite. */
    private static void clearOldArena(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -37; x <= 37; x++) {
            for (int z = -37; z <= 37; z++) {
                for (int y = 61; y <= 102; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, 2);
                }
            }
        }
    }

    /** Terrain height of a column relative to the floor's walking level, always within -2..+2. */
    public static int height(Layout layout, int floor, int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (r >= Tower.FIGHT_RADIUS + 0.5) return 0;                        // outer walkway is flat
        if (Math.abs(x) <= 5 && (z >= 9 || z <= -14)) return 0;              // entrance and exit corridors are flat
        return switch (layout) {
            case FLAT, PILLARS, MOAT -> 0;
            case PIT -> r < 9.5 ? -1 : 0;
            case DAIS -> r < 6.5 ? 1 : 0;
            case SUMMIT -> r < 7.5 ? 1 : 0;
            case WAVES -> ((int) (r / 4)) % 2 == 1 ? -1 : 0;
            case TERRACES -> r < 5 ? -2 : r < 10.5 ? -1 : 0;
            case ZIGGURAT -> r < 4.5 ? 2 : r < 9 ? 1 : 0;
            case SPLIT -> (x > 0 && z < 0) ? 1 : (x < 0 && z > 0) ? -1 : 0;
            case MOUNDS -> {
                int hit = 0;
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * 2 * i / 5 + floor * 0.7;
                    double cx = Math.cos(a) * 9.5, cz = Math.sin(a) * 9.5;
                    if ((x - cx) * (x - cx) + (z - cz) * (z - cz) < 12.5) hit = 1;
                }
                yield hit;
            }
        };
    }

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static boolean corridor(int x, int z) {
        return Math.abs(x) <= 5 && (z > 0 || z < 0) && Math.abs(z) >= 9;
    }

    /** Builds (or rebuilds) one complete floor: ground, terrain, walls, windows, roof, lights, altar and gates. */
    public static void buildFloor(ServerLevel level, Line line, int floor) {
        floor = Tower.clampFloor(floor);
        final int y0 = Tower.floorY(floor);
        final Layout layout = layout(floor);
        final Palette p = palette(line, Tower.tier(floor));
        final boolean summit = floor == Tower.FLOORS;
        final int roofY = y0 + Tower.ROOM_HEIGHT + 1;
        final int f = floor;
        final HeightFn hf = heights(floor, 0);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final Mason.Stone wallStone = Mason.of(p.wall());

        int ri = (int) Math.ceil(Tower.WALL_OUTER) + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= Tower.WALL_OUTER) continue;

                if (r >= Tower.WALL_INNER) {
                    // ---- the tower shell
                    double sector = (Math.atan2(z, x) + Math.PI) / (Math.PI * 2) * 16.0;
                    double off = Math.abs(sector - Math.floor(sector) - 0.5);
                    boolean window = !summit && off < 0.22;
                    boolean face = r < Tower.WALL_INNER + 1;            // the course a player sees from inside
                    Direction out = Mason.outward(x, z);
                    int top = summit ? y0 + 4 : roofY;
                    for (int y = y0 - Tower.DEPTH; y <= top; y++) {
                        int h = y - y0;
                        BlockState b = ((h == 1 || h == 5 || h == 12) ? p.trim() : Mason.weather(p.wall(), x, y, z, 18)).defaultBlockState();
                        if (window && h >= 6 && h <= 11) {
                            // a window set deep in the wall under a round head: glass at the back, the reveal open
                            boolean head = h == 11 && off >= 0.12;
                            if (face) b = head ? Mason.stair(wallStone.stairs(), out, true) : Blocks.AIR.defaultBlockState();
                            else if (!head) b = p.glass().defaultBlockState();
                        }
                        // the face of the wall: a dado of the tower's panels, its brick between the windows
                        if (!summit && face && h >= 2 && h <= 4) b = Passages.wallPanel(line).defaultBlockState();
                        if (!summit && face && h >= 6 && h <= 11 && !window) b = Passages.brick(line).defaultBlockState();
                        if (!summit && face && h == 3 && Math.floorMod(x * 7 + z * 13, 9) == 0) b = p.light().defaultBlockState();
                        if (summit && face && h == 4) b = Mason.stair(wallStone.stairs(), out, true);
                        level.setBlock(pos.set(x, y, z), b, 2);
                    }
                    if (summit) {
                        if (((x + z) & 1) == 0) set(level, x, y0 + 5, z, p.trim());      // crenellations
                        else level.setBlock(pos.set(x, y0 + 5, z), Blocks.BARRIER.defaultBlockState(), 2);
                        for (int y = y0 + 6; y <= y0 + 30; y++) set(level, x, y, z, Blocks.BARRIER);
                    }
                    continue;
                }

                // ---- inside the tower: ground with this floor's terrain, then air, then the roof
                int h = hf.at(x, z);
                for (int y = y0 - Tower.DEPTH; y < y0 + h; y++) set(level, x, y, z, p.fill());
                set(level, x, y0 + h, z, surface(p, hf, f, x, z, r, h));
                int airTop = summit ? y0 + 30 : roofY - 1;
                for (int y = y0 + h + 1; y <= airTop; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, 2);
                }
                if (!summit) {
                    set(level, x, roofY, z, p.fill());
                    if (Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0 && r < 20) set(level, x, roofY - 1, z, p.light());
                }
            }
        }

        // ---- layout features that stand outside the fight ring
        if (layout == Layout.MOAT) {
            int rj = Tower.WALL_INNER;
            for (int x = -rj; x <= rj; x++) {
                for (int z = -rj; z <= rj; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r >= 18.3 && r < 20.2 && !corridor(x, z)) set(level, x, y0, z, p.fluid());
                }
            }
        }
        if (layout == Layout.PILLARS || layout == Layout.SUMMIT) {
            int count = layout == Layout.SUMMIT ? 8 : 12;
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * (i + 0.5) / count;
                int px = (int) Math.round(Math.cos(a) * 19), pz = (int) Math.round(Math.sin(a) * 19);
                if (corridor(px, pz)) continue;
                if (summit) {
                    for (int y = 1; y <= 3; y++) set(level, px, y0 + y, pz, p.pillar());
                    if (line == Line.HADES) {
                        set(level, px, y0 + 4, pz, Blocks.SOUL_SOIL);
                        set(level, px, y0 + 5, pz, Blocks.SOUL_FIRE);
                    } else {
                        set(level, px, y0 + 4, pz, p.light());
                        if (line == Line.ZEUS) set(level, px, y0 + 5, pz, Blocks.END_ROD);
                    }
                } else {
                    for (int y = 1; y <= Tower.ROOM_HEIGHT; y++) set(level, px, y0 + y, pz, y == 8 ? p.light() : Passages.column(line));
                }
            }
        }

        decorate(level, line, floor, layout, p);
        // the walkway is paved in the tower's floor panels, a frame round the whole ring of them
        int wr = (int) Math.ceil(Tower.WALL_INNER);
        for (int x = -wr; x <= wr; x++) {
            for (int z = -wr; z <= wr; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < Tower.FIGHT_RADIUS + 0.5 || r >= 20 || (x == 0 && Math.abs(z) >= 13)) continue;
                BlockPos at = new BlockPos(x, y0, z);
                if (level.getBlockState(at).is(p.top()) || level.getBlockState(at).is(p.topAlt())) set(level, x, y0, z, Passages.floorPanel(line));
            }
        }

        // ---- altar on a small dais in the entrance corridor
        BlockPos altar = Tower.altarPos(floor);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 5) set(level, altar.getX() + dx, y0, altar.getZ() + dz, (dx == 0 && dz == 0) ? p.light() : p.inlay());
            }
        }
        level.setBlock(altar, (switch (line) {
            case ZEUS -> ModContent.ALTAR_ZEUS.get();
            case POSEIDON -> ModContent.ALTAR_POSEIDON.get();
            case HADES -> ModContent.ALTAR_HADES.get();
        }).defaultBlockState(), 2);

        // ---- gates: home to the hub on the south side, up to the next floor on the north side
        gate(level, 0, y0, 19, p, ModContent.GATE_HUB.get(), Passages.portalFrame(line));
        if (!summit) gate(level, 0, y0, -19, p, ModContent.GATE_ASCEND.get(), Passages.portalFrame(line));
        com.thefarawaydev.realmsofchaos.block.ConnectedBlock.settle(level, new BlockPos(-ri, y0 - 1, -ri), new BlockPos(ri, y0 + Tower.ROOM_HEIGHT + 1, ri));
    }

    /** The paving block a column of the fight ring has under the given height map. */
    public static Block surfaceAt(Line line, int floor, int x, int z, HeightFn hf) {
        return surface(palette(line, Tower.tier(floor)), hf, floor, x, z, Math.sqrt(x * x + z * z), hf.at(x, z));
    }

    private static Block surface(Palette p, HeightFn hf, int floor, int x, int z, double r, int h) {
        if (x == 0 && z == 15) return ModContent.ARRIVAL_SIGIL.get();                    // where a climber arrives
        if (r < 2.5) return p.inlay();                                                  // centre medallion: the boss spot
        if (r >= Tower.FIGHT_RADIUS - 0.5 && r < Tower.FIGHT_RADIUS + 0.5) {             // the ring that marks the fight area
            return Math.floorMod(x + z, 5) == 0 ? p.light() : p.inlay();
        }
        if (r >= Tower.FIGHT_RADIUS + 0.5) {
            // the outer walkway: a kerb against the wall, a processional path from each gate, chequered paving
            if (r >= 20) return p.trim();
            if (x == 0 && Math.abs(z) >= 13) return p.inlay();
            return ((x + z) & 1) == 0 ? p.topAlt() : p.top();
        }
        if (x == 0 && (z >= 14 || z <= -14)) return p.inlay();                          // the path continues to the ring
        if (Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0) return p.light();     // floor lights
        // step edges get the alternate block so height changes read clearly
        boolean edge = hf.at(x + 1, z) != h || hf.at(x - 1, z) != h || hf.at(x, z + 1) != h || hf.at(x, z - 1) != h;
        if (edge) return p.topAlt();
        // each floor shuffles its paving a little differently
        return Math.floorMod((x >> 1) * 3 + (z >> 1) * 5 + floor, 7) < 2 ? p.topAlt() : p.top();
    }

    /**
     * Moves the terrain of a built floor from one height map to another (used by floor shifts). Only the fight ring
     * changes; walkway, walls, altar and gates stay. @return true if any block changed.
     */
    public static boolean reshape(ServerLevel level, Line line, int floor, HeightFn from, HeightFn to) {
        floor = Tower.clampFloor(floor);
        int y0 = Tower.floorY(floor);
        Palette p = palette(line, Tower.tier(floor));
        boolean changed = false;
        int ri = Tower.FIGHT_RADIUS + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= Tower.FIGHT_RADIUS + 0.5) continue;
                int a = from.at(x, z), b = to.at(x, z);
                // repaint the top block even when the height stays, so step edges follow the new shape
                boolean edgeMayChange = to.at(x + 1, z) != from.at(x + 1, z) || to.at(x - 1, z) != from.at(x - 1, z)
                        || to.at(x, z + 1) != from.at(x, z + 1) || to.at(x, z - 1) != from.at(x, z - 1);
                if (a == b && !edgeMayChange) continue;
                for (int y = y0 + Math.min(a, b); y < y0 + b; y++) set(level, x, y, z, p.fill());
                set(level, x, y0 + b, z, surface(p, to, floor, x, z, r, b));
                for (int y = y0 + b + 1; y <= y0 + Math.max(a, b); y++) set(level, x, y, z, Blocks.AIR);
                changed |= a != b;
            }
        }
        return changed;
    }

    // ---- decoration: everything here stands outside the fight ring or hangs well above head height ------------

    private static Block[] prop(Line line, int tier, boolean variant) {
        return switch (line) {
            case ZEUS -> switch (tier) {
                case 1 -> variant ? new Block[]{Blocks.MOSS_BLOCK, Blocks.FLOWERING_AZALEA} : new Block[]{Blocks.OAK_LOG, Blocks.LANTERN};
                case 2 -> variant ? new Block[]{Blocks.CHISELED_QUARTZ_BLOCK, Blocks.GOLD_BLOCK} : new Block[]{Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE};
                default -> variant ? new Block[]{Blocks.SNOW_BLOCK, Blocks.END_ROD} : new Block[]{Blocks.CALCITE, Blocks.PEARLESCENT_FROGLIGHT};
            };
            case POSEIDON -> switch (tier) {
                case 1 -> variant ? new Block[]{Blocks.PRISMARINE, Blocks.SEA_LANTERN} : new Block[]{Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.LANTERN};
                case 2 -> variant ? new Block[]{Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN} : new Block[]{Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN};
                default -> variant ? new Block[]{Blocks.SCULK, Blocks.SOUL_LANTERN} : new Block[]{Blocks.CHISELED_DEEPSLATE, Blocks.SEA_LANTERN};
            };
            case HADES -> switch (tier) {
                case 1 -> variant ? new Block[]{Blocks.BONE_BLOCK, Blocks.BONE_BLOCK, Blocks.SOUL_LANTERN} : new Block[]{Blocks.SMOOTH_BASALT, Blocks.SOUL_SOIL, Blocks.SOUL_FIRE};
                case 2 -> variant ? new Block[]{Blocks.NETHER_BRICKS, Blocks.SOUL_SOIL, Blocks.SOUL_FIRE} : new Block[]{Blocks.GILDED_BLACKSTONE, Blocks.SHROOMLIGHT};
                default -> variant ? new Block[]{Blocks.OBSIDIAN, Blocks.AMETHYST_BLOCK, Blocks.SOUL_LANTERN} : new Block[]{Blocks.CRYING_OBSIDIAN, Blocks.SOUL_LANTERN};
            };
        };
    }

    private static void decorate(ServerLevel level, Line line, int floor, Layout layout, Palette p) {
        int y0 = Tower.floorY(floor);
        boolean summit = floor == Tower.FLOORS;
        int roofY = y0 + Tower.ROOM_HEIGHT + 1;
        Mason.Stone stone = Mason.of(p.wall());
        Block lantern = line == Line.HADES ? Blocks.SOUL_LANTERN : Blocks.LANTERN;

        if (summit) {
            // four obelisks on the diagonals crown the summit: a stepped base, a banded shaft, a capped lamp
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = 14 * sx, z = 14 * sz;
                    BlockPos foot = new BlockPos(x, y0, z);
                    Mason.column(level, foot, 7, p.pillar(), stone, true);
                    set(level, x, y0 + 1, z, p.trim());
                    set(level, x, y0 + 4, z, p.trim());
                    set(level, x, y0 + 8, z, p.light());
                    for (Direction d : Direction.Plane.HORIZONTAL) level.setBlock(foot.above(8).relative(d), Mason.slab(stone.slab(), false), 2);
                    set(level, x, y0 + 9, z, line == Line.ZEUS ? Blocks.END_ROD : line == Line.HADES ? Blocks.SOUL_SOIL : p.trim());
                    if (line == Line.HADES) set(level, x, y0 + 10, z, Blocks.SOUL_FIRE);
                }
            }
            return;
        }

        // a cornice where the wall meets the ceiling: a stepped band of hanging stairs and slabs all the way round
        int ri = Tower.WALL_INNER;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                Direction out = Mason.outward(x, z);
                if (r >= 20 && r < 21) {
                    level.setBlock(new BlockPos(x, roofY - 1, z), Mason.stair(stone.stairs(), out, true), 2);
                    set(level, x, roofY - 2, z, p.trim());
                    level.setBlock(new BlockPos(x, roofY - 3, z), Mason.stair(stone.stairs(), out, true), 2);
                } else if (r >= 19 && r < 20) {
                    level.setBlock(new BlockPos(x, roofY - 1, z), Mason.stair(stone.stairs(), out, true), 2);
                }
            }
        }

        // engaged columns against the wall between the windows: a moulded base, a banded shaft, a capital
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            int x = (int) Math.round(Math.cos(a) * 20.2), z = (int) Math.round(Math.sin(a) * 20.2);
            if (Math.abs(x) <= 4) continue;                                  // leave the gates clear
            BlockPos foot = new BlockPos(x, y0, z);
            for (int y = y0 + 1; y < roofY; y++) {
                int h = y - y0;
                set(level, x, y, z, (h == 1 || h == Tower.ROOM_HEIGHT - 3 || h == Tower.ROOM_HEIGHT) ? p.trim() : Passages.column(line));
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos base = foot.above(1).relative(d), head = foot.above(Tower.ROOM_HEIGHT - 4).relative(d);
                if (layout != Layout.MOAT && level.getBlockState(base).isAir() && Math.hypot(base.getX(), base.getZ()) > Tower.FIGHT_RADIUS + 1.5)
                    level.setBlock(base, Mason.stair(stone.stairs(), d.getOpposite(), false), 2);
                if (level.getBlockState(head).isAir()) level.setBlock(head, Mason.stair(stone.stairs(), d.getOpposite(), true), 2);
            }
        }

        // a ribbed ceiling: two rings and eight ribs running out from the inner ring to the columns
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                if ((r >= 7.5 && r < 8.5) || (r >= 15.5 && r < 16.5)) set(level, x, roofY - 1, z, p.trim());
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            for (double t = 8; t < 19; t += 0.5) {
                int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
                set(level, x, roofY - 1, z, p.trim());
            }
        }

        // a great chandelier at the middle and four lesser ones, hung on chains well above anything on the floor
        set(level, 0, roofY - 1, 0, p.trim());
        for (int d = 2; d <= 3; d++) set(level, 0, roofY - d, 0, Blocks.IRON_CHAIN);
        set(level, 0, roofY - 4, 0, p.light());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos arm = new BlockPos(0, roofY - 4, 0).relative(d);
            set(level, arm.getX(), arm.getY(), arm.getZ(), p.trim());
            BlockPos tip = arm.relative(d);
            level.setBlock(tip, Mason.stair(stone.stairs(), d.getOpposite(), true), 2);
            level.setBlock(arm.below(), Mason.hanging(lantern), 2);
        }
        int[][] lesser = {{9, 9}, {-9, 9}, {9, -9}, {-9, -9}};
        for (int[] c : lesser) Mason.chandelier(level, new BlockPos(c[0], roofY, c[1]), 4, lantern);

        // a carved relief of the tower's story under every other window, set into the face of the wall
        Block relief = Passages.relief(line);
        for (int i = 0; i < 16; i += 2) {
            double a = Math.PI * 2 * (i + 0.5) / 16;
            int x = (int) Math.round(Math.cos(a) * 21.4), z = (int) Math.round(Math.sin(a) * 21.4);
            if (Math.abs(x) <= 5) continue;                                  // (not beside the gates)
            for (int y = 2; y <= 3; y++) set(level, x, y0 + y, z, relief);
        }

        // themed props under the windows (floors whose layout already fills the walkway keep it clear)
        if (layout != Layout.MOAT && layout != Layout.PILLARS) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * (i + 0.5) / 16;
                int x = (int) Math.round(Math.cos(a) * 19.3), z = (int) Math.round(Math.sin(a) * 19.3);
                if (Math.abs(x) <= 6 && Math.abs(z) >= 9) continue;         // nothing beside the gates or the path
                Block[] stack = prop(line, Tower.tier(floor), (i & 1) == 0);
                for (int k = 0; k < stack.length; k++) set(level, x, y0 + 1 + k, z, stack[k]);
            }
        }
    }

    /** A gate across X centred at (cx, cz), standing on the floor at y0. */
    private static void gate(ServerLevel level, int cx, int y0, int cz, Palette p, Block gateBlock, Block frame) {
        Mason.gateway(level, new BlockPos(cx, y0, cz), Direction.NORTH, Mason.of(p.wall()), frame, p.trim(), p.light(), p.inlay(), gateBlock);
    }
}
