package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
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
    public static final int LAYOUT_VERSION = 3;

    public enum Layout { FLAT, MOUNDS, PILLARS, PIT, DAIS, WAVES, MOAT, TERRACES, SPLIT, ZIGGURAT, SUMMIT }

    private static final Layout[] BY_FLOOR = {
            Layout.FLAT, Layout.MOUNDS, Layout.PILLARS, Layout.PIT, Layout.DAIS,            // tier 1
            Layout.WAVES, Layout.MOAT, Layout.TERRACES, Layout.SPLIT, Layout.ZIGGURAT,      // tier 2
            Layout.PILLARS, Layout.MOUNDS, Layout.TERRACES, Layout.MOAT,                    // tier 3
            Layout.SUMMIT                                                                   // the final god
    };

    private record Palette(Block top, Block topAlt, Block fill, Block wall, Block trim, Block pillar,
                           Block light, Block inlay, Block glass, Block fluid) {}

    private TowerBuilder() {}

    public static Layout layout(int floor) {
        return BY_FLOOR[Tower.clampFloor(floor) - 1];
    }

    private static Palette palette(Line line, int tier) {
        return switch (line) {
            case ZEUS -> switch (tier) {
                case 1 -> new Palette(Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                        Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_LOG, Blocks.GLOWSTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GLASS, Blocks.WATER);
                case 2 -> new Palette(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS,
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.WATER);
                case 3 -> new Palette(Blocks.SNOW_BLOCK, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE,
                        Blocks.GOLD_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.POLISHED_DIORITE, Blocks.GLASS, Blocks.WATER);
                default -> new Palette(Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS,
                        Blocks.GOLD_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GLOWSTONE, Blocks.GOLD_BLOCK, Blocks.GLASS, Blocks.WATER);
            };
            case POSEIDON -> switch (tier) {
                case 1 -> new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
                        Blocks.CHISELED_SANDSTONE, Blocks.PRISMARINE, Blocks.SEA_LANTERN, Blocks.PRISMARINE, Blocks.GLASS, Blocks.WATER);
                case 2 -> new Palette(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
                        Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE, Blocks.GLASS, Blocks.WATER);
                case 3 -> new Palette(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS,
                        Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, Blocks.CHISELED_DEEPSLATE, Blocks.TINTED_GLASS, Blocks.WATER);
                default -> new Palette(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                        Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.LAPIS_BLOCK, Blocks.GLASS, Blocks.WATER);
            };
            case HADES -> switch (tier) {
                case 1 -> new Palette(Blocks.SOUL_SOIL, Blocks.COARSE_DIRT, Blocks.SMOOTH_BASALT, Blocks.SMOOTH_BASALT,
                        Blocks.BONE_BLOCK, Blocks.POLISHED_BASALT, Blocks.SHROOMLIGHT, Blocks.BONE_BLOCK, Blocks.TINTED_GLASS, Blocks.LAVA);
                case 2 -> new Palette(Blocks.BLACKSTONE, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS,
                        Blocks.RED_NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.GILDED_BLACKSTONE, Blocks.TINTED_GLASS, Blocks.LAVA);
                case 3 -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                        Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.SHROOMLIGHT, Blocks.AMETHYST_BLOCK, Blocks.TINTED_GLASS, Blocks.LAVA);
                default -> new Palette(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                        Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.GILDED_BLACKSTONE, Blocks.TINTED_GLASS, Blocks.LAVA);
            };
        };
    }

    public static void ensureFloor(ServerLevel level, Line line, int floor) {
        floor = Tower.clampFloor(floor);
        ProgressData data = ProgressData.get(level.getServer());
        Realm realm = Realm.ofLine(line);
        String cleanup = realm.id() + "/cleanup@" + LAYOUT_VERSION;
        if (!data.isBuilt(cleanup)) {
            clearOldArena(level);
            data.markBuilt(cleanup);
        }
        String key = realm.id() + "/floor" + floor + "@" + LAYOUT_VERSION;
        if (data.isBuilt(key)) return;
        buildFloor(level, line, floor);
        data.markBuilt(key);
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
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int ri = (int) Math.ceil(Tower.WALL_OUTER) + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= Tower.WALL_OUTER) continue;

                if (r >= Tower.WALL_INNER) {
                    // ---- the tower shell
                    double sector = (Math.atan2(z, x) + Math.PI) / (Math.PI * 2) * 16.0;
                    boolean window = !summit && Math.abs(sector - Math.floor(sector) - 0.5) < 0.22;
                    int top = summit ? y0 + 4 : roofY;
                    for (int y = y0 - Tower.DEPTH; y <= top; y++) {
                        int h = y - y0;
                        Block b = (h == 1 || h == 5 || h == 12) ? p.trim() : p.wall();
                        if (window && h >= 6 && h <= 11) b = p.glass();
                        if (!summit && r < Tower.WALL_INNER + 1 && h == 3 && Math.floorMod(x * 7 + z * 13, 9) == 0) b = p.light();
                        set(level, x, y, z, b);
                    }
                    if (summit) {
                        if (((x + z) & 1) == 0) set(level, x, y0 + 5, z, p.trim());      // crenellations
                        else level.setBlock(pos.set(x, y0 + 5, z), Blocks.BARRIER.defaultBlockState(), 2);
                        for (int y = y0 + 6; y <= y0 + 30; y++) set(level, x, y, z, Blocks.BARRIER);
                    }
                    continue;
                }

                // ---- inside the tower: ground with this floor's terrain, then air, then the roof
                int h = height(layout, f, x, z);
                for (int y = y0 - Tower.DEPTH; y < y0 + h; y++) set(level, x, y, z, p.fill());
                set(level, x, y0 + h, z, surface(p, layout, f, x, z, r, h));
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
                    for (int y = 1; y <= Tower.ROOM_HEIGHT; y++) set(level, px, y0 + y, pz, y == 8 ? p.light() : p.pillar());
                }
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
        gate(level, 0, y0, 19, p, ModContent.GATE_HUB.get());
        if (!summit) gate(level, 0, y0, -19, p, ModContent.GATE_ASCEND.get());
    }

    private static Block surface(Palette p, Layout layout, int floor, int x, int z, double r, int h) {
        if (r < 2.5) return p.inlay();                                                  // centre medallion: the boss spot
        if (r >= Tower.FIGHT_RADIUS - 0.5 && r < Tower.FIGHT_RADIUS + 0.5) {             // the ring that marks the fight area
            return Math.floorMod(x + z, 5) == 0 ? p.light() : p.inlay();
        }
        if (Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0) return p.light();     // floor lights
        // step edges get the alternate block so height changes read clearly
        boolean edge = height(layout, floor, x + 1, z) != h || height(layout, floor, x - 1, z) != h
                || height(layout, floor, x, z + 1) != h || height(layout, floor, x, z - 1) != h;
        if (edge && r < Tower.FIGHT_RADIUS) return p.topAlt();
        // each floor shuffles its paving a little differently
        return Math.floorMod((x >> 1) * 3 + (z >> 1) * 5 + floor, 7) < 2 ? p.topAlt() : p.top();
    }

    /** A gate arch across X centred at (cx, cz), standing on the floor at y0. */
    private static void gate(ServerLevel level, int cx, int y0, int cz, Palette p, Block gateBlock) {
        for (int i = -3; i <= 3; i++) {
            int x = cx + i, a = Math.abs(i);
            set(level, x, y0, cz, p.inlay());
            if (a == 3) {
                for (int y = 1; y <= 3; y++) set(level, x, y0 + y, cz, p.pillar());
                set(level, x, y0 + 4, cz, p.light());
            } else if (a == 2) {
                for (int y = 1; y <= 5; y++) set(level, x, y0 + y, cz, p.pillar());
                set(level, x, y0 + 6, cz, p.trim());
            } else {
                for (int y = 1; y <= 4; y++) set(level, x, y0 + y, cz, gateBlock);
                set(level, x, y0 + 5, cz, p.trim());
                set(level, x, y0 + 6, cz, a == 0 ? p.light() : p.trim());
            }
        }
    }
}
