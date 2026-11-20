package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the hub and the three god arenas the first time somebody travels there (and again whenever
 * {@link #LAYOUT_VERSION} changes). The dimensions themselves are void worlds declared by datapack JSON.
 *
 * Layout rules that keep fights fair: the inner arena (radius {@value #FIGHT_RADIUS}) is completely flat and empty,
 * all decoration sits outside it, and an invisible barrier above the outer wall stops anyone being thrown out.
 */
public final class RealmBuilder {
    public static final int LAYOUT_VERSION = 2;
    public static final int Y = 64;
    public static final int FIGHT_RADIUS = 23;
    private static final int WALL_RADIUS = 32;
    private static final int HUB_RADIUS = 26;

    private RealmBuilder() {}

    public static void ensureBuilt(ServerLevel level, Realm realm) {
        ProgressData data = ProgressData.get(level.getServer());
        String key = realm.id() + "@" + LAYOUT_VERSION;
        if (data.isBuilt(key)) return;
        build(level, realm);
        data.markBuilt(key);
    }

    /** Builds (or rebuilds) a realm around the origin of {@code level}. */
    public static void build(ServerLevel level, Realm realm) {
        if (realm == Realm.HUB) {
            buildHub(level);
        } else {
            buildZone(level, realm.line());
        }
    }

    /** Makes sure a player/boss arriving at this spot stands on solid ground with free space around them. */
    public static void makeSafe(ServerLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = feet.offset(dx, -1, dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (!state.isAir() && !state.getCollisionShape(level, p).isEmpty()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** Only realms are ours to edit; elsewhere (an altar placed in the overworld) the world is left alone. */
    public static void makeSafeIfRealm(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
        if (Realm.of(level.dimension()) != null) makeSafe(level, pos.x, pos.y, pos.z);
    }

    // --- shared helpers ------------------------------------------------------------------------

    private static void set(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void clear(ServerLevel level, int radius, int y0, int y1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = y0; y <= y1; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, 2);
                }
            }
        }
    }

    /** Every block column whose distance from the origin lies in [r0, r1). */
    private interface ColumnFn { void at(int x, int z, double dist); }

    private static void annulus(double r0, double r1, ColumnFn fn) {
        int ri = (int) Math.ceil(r1) + 1;
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= r0 && d < r1) fn.at(x, z, d);
            }
        }
    }

    private static void column(ServerLevel level, int x, int z, int y0, int height, Block block) {
        for (int h = 0; h < height; h++) set(level, x, y0 + h, z, block);
    }

    /** An invisible wall above the visible one so nobody can be knocked or jump out into the void. */
    private static void barrierWall(ServerLevel level, double r0, double r1, int y0, int y1) {
        annulus(r0, r1, (x, z, d) -> {
            for (int y = y0; y <= y1; y++) set(level, x, y, z, Blocks.BARRIER);
        });
    }

    /** A gate arch centred at (cx, cz); the 3x4 opening runs along X when alongX, otherwise along Z. */
    private static void gate(ServerLevel level, int cx, int cz, boolean alongX, Block pillar, Block lintel, Block accent, Block gateBlock) {
        for (int i = -3; i <= 3; i++) {
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            int a = Math.abs(i);
            set(level, x, Y, z, accent);
            if (a == 3) {                                   // outer buttress
                column(level, x, z, Y + 1, 3, pillar);
                set(level, x, Y + 4, z, accent);
            } else if (a == 2) {                            // main pillars
                column(level, x, z, Y + 1, 5, pillar);
                set(level, x, Y + 6, z, lintel);
            } else {                                        // the portal itself
                column(level, x, z, Y + 1, 4, gateBlock);
                set(level, x, Y + 5, z, lintel);
                set(level, x, Y + 6, z, a == 0 ? accent : lintel);
            }
        }
        set(level, cx, Y + 7, cz, lintel);
    }

    // --- hub -------------------------------------------------------------------------------------

    private static void buildHub(ServerLevel level) {
        clear(level, HUB_RADIUS + 4, Y - 3, Y + 34);
        // three-block-thick plaza; the floor is a compass rose pointing at the three gates
        annulus(0, HUB_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y - 2, z, Blocks.STONE_BRICKS);
            set(level, x, Y - 1, z, Blocks.STONE_BRICKS);
            Block top;
            boolean north = z < 0 && Math.abs(x) <= 2, east = x > 0 && Math.abs(z) <= 2, west = x < 0 && Math.abs(z) <= 2;
            if (d < 2.5) top = Blocks.CHISELED_STONE_BRICKS;
            else if (d < 4.5) top = Blocks.GILDED_BLACKSTONE;
            else if (d >= 9.5 && d < 10.5) top = Blocks.POLISHED_BLACKSTONE;
            else if (d >= 21.5 && d < 22.5) top = Blocks.POLISHED_BLACKSTONE;
            else if (north && d < 20) top = Math.abs(x) == 2 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ;
            else if (east && d < 20) top = Math.abs(z) == 2 ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS;
            else if (west && d < 20) top = Math.abs(z) == 2 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE_BRICKS;
            else top = ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
            set(level, x, Y, z, top);
        });
        // low parapet with lantern posts, plus an invisible wall so nobody walks off the edge
        annulus(HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y + 1, z, Blocks.STONE_BRICKS);
            set(level, x, Y + 2, z, Blocks.STONE_BRICK_WALL);
        });
        barrierWall(level, HUB_RADIUS - 0.5, HUB_RADIUS + 1.5, Y + 3, Y + 30);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 12;
            int px = (int) Math.round(Math.cos(a) * (HUB_RADIUS - 2)), pz = (int) Math.round(Math.sin(a) * (HUB_RADIUS - 2));
            column(level, px, pz, Y + 1, 3, Blocks.STONE_BRICK_WALL);
            set(level, px, Y + 4, pz, Blocks.LANTERN);
        }
        // Zeus (north), Poseidon (east), Hades (west): each gate is a small monument in its god's materials
        gate(level, 0, -18, true, Blocks.QUARTZ_PILLAR, Blocks.GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, ModContent.GATE_ZEUS.get());
        gate(level, 18, 0, false, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, ModContent.GATE_POSEIDON.get());
        gate(level, -18, 0, false, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRYING_OBSIDIAN, Blocks.SHROOMLIGHT, ModContent.GATE_HADES.get());
        // flanking beacons in front of each gate
        for (int side = -1; side <= 1; side += 2) {
            column(level, 5 * side, -15, Y + 1, 4, Blocks.QUARTZ_PILLAR);
            set(level, 5 * side, Y + 5, -15, Blocks.GLOWSTONE);
            column(level, 15, 5 * side, Y + 1, 4, Blocks.PRISMARINE_BRICKS);
            set(level, 15, Y + 5, 5 * side, Blocks.SEA_LANTERN);
            column(level, -15, 5 * side, Y + 1, 4, Blocks.POLISHED_BLACKSTONE);
            set(level, -15, Y + 5, 5 * side, Blocks.SOUL_SOIL);
            set(level, -15, Y + 6, 5 * side, Blocks.SOUL_FIRE);
        }
        makeSafe(level, 0.5, Y + 1, 0.5);
    }

    // --- zones -----------------------------------------------------------------------------------

    private record Theme(Block floorA, Block floorB, Block inlay, Block light, Block wall, Block wallTrim,
                         Block pillar, Block cap, Block moat) {}

    private static Theme theme(Line line) {
        return switch (line) {
            case ZEUS -> new Theme(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.GOLD_BLOCK, Blocks.GLOWSTONE,
                    Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.GOLD_BLOCK, null);
            case POSEIDON -> new Theme(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.SEA_LANTERN,
                    Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.WATER);
            case HADES -> new Theme(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.SHROOMLIGHT,
                    Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BASALT, Blocks.SOUL_SOIL, Blocks.LAVA);
        };
    }

    private static void buildZone(ServerLevel level, Line line) {
        Theme t = theme(line);
        clear(level, WALL_RADIUS + 4, Y - 3, Y + 38);

        // floor: three blocks thick, flat and unobstructed inside FIGHT_RADIUS
        annulus(0, WALL_RADIUS + 1.5, (x, z, d) -> {
            set(level, x, Y - 2, z, t.floorB());
            set(level, x, Y - 1, z, t.floorB());
            Block top;
            if (d < 2.5) top = t.inlay();
            else if (d < 3.5) top = t.light();
            else if (d >= 11.5 && d < 12.5) top = t.inlay();
            else if (d >= FIGHT_RADIUS - 0.5 && d < FIGHT_RADIUS + 0.5) top = (Math.floorMod(x + z, 4) == 0) ? t.light() : t.inlay();
            else if (d < 11.5 && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z))) top = t.floorB();
            else top = ((x + z) & 1) == 0 ? t.floorA() : (d < FIGHT_RADIUS ? t.floorA() : t.floorB());
            set(level, x, Y, z, top);
        });

        // moat (water in the Abyss, lava in the Underworld) outside the fight ring, bridged at the entrance
        if (t.moat() != null) {
            annulus(27.5, 29.6, (x, z, d) -> {
                if (z > 0 && Math.abs(x) <= 5) return;
                set(level, x, Y, z, t.moat());
            });
        }

        // outer wall with a trim band, crenellations and an invisible wall above it
        annulus(WALL_RADIUS - 1.2, WALL_RADIUS + 1.5, (x, z, d) -> {
            for (int h = 1; h <= 8; h++) {
                set(level, x, Y + h, z, h == 5 ? t.wallTrim() : t.wall());
            }
            if (((x + z) & 1) == 0) set(level, x, Y + 9, z, t.wall());
            if (Math.floorMod(x * 7 + z * 13, 11) == 0) set(level, x, Y + 4, z, t.light());
        });
        barrierWall(level, WALL_RADIUS - 1.2, WALL_RADIUS + 1.5, Y + 10, Y + 36);

        // twelve pillars between the fight ring and the wall (none in the entrance corridor)
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 12;
            int px = (int) Math.round(Math.cos(a) * 25.5), pz = (int) Math.round(Math.sin(a) * 25.5);
            if (pz > 16 && Math.abs(px) < 9) continue;
            switch (line) {
                case ZEUS -> {
                    set(level, px, Y + 1, pz, Blocks.CHISELED_QUARTZ_BLOCK);
                    column(level, px, pz, Y + 2, 8, t.pillar());
                    set(level, px, Y + 10, pz, t.cap());
                    set(level, px, Y + 11, pz, Blocks.END_ROD);
                }
                case POSEIDON -> {
                    column(level, px, pz, Y + 1, 7, t.pillar());
                    set(level, px, Y + 4, pz, Blocks.DARK_PRISMARINE);
                    set(level, px, Y + 8, pz, t.cap());
                }
                case HADES -> {
                    int height = 4 + Math.floorMod(i * 5, 6);          // jagged basalt spikes
                    column(level, px, pz, Y + 1, height, t.pillar());
                    set(level, px, Y + 1 + height, pz, t.cap());
                    set(level, px, Y + 2 + height, pz, Blocks.SOUL_FIRE);
                }
            }
        }
        if (line == Line.ZEUS) {
            // a ring beam joins the columns into an open-roofed temple
            annulus(25.0, 26.2, (x, z, d) -> {
                if (z > 16 && Math.abs(x) < 9) return;
                set(level, x, Y + 10, z, Blocks.SMOOTH_QUARTZ);
            });
        }

        // entrance: arrival pad, altar on a dais, and the gate home behind the player
        annulus(0, 3.2, (x, z, d) -> set(level, x, Y, z + 21, d < 1.5 ? t.light() : t.inlay()));
        BlockState altar = switch (line) {
            case ZEUS -> ModContent.ALTAR_ZEUS.get().defaultBlockState();
            case POSEIDON -> ModContent.ALTAR_POSEIDON.get().defaultBlockState();
            case HADES -> ModContent.ALTAR_HADES.get().defaultBlockState();
        };
        set(level, 0, Y + 1, 21, altar);
        gate(level, 0, 29, true, t.pillar(), t.wallTrim(), t.light(), ModContent.GATE_HUB.get());

        makeSafe(level, 0.5, Y + 1, 0.5);       // boss spawn
        makeSafe(level, 0.5, Y + 1, 25.5);      // player arrival
    }
}
