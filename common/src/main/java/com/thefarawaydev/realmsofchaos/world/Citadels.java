package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.item.Panoply;
import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Words;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The citadels: where the keystones are kept. Each of the three is a castle of its tower's stone - a curtain wall
 * with a tower at every corner, a gatehouse to the south, a courtyard strewn with the statues of gods who fell, and
 * a keep of three storeys with the keystone on its roof.
 *
 * <p>They are held. When somebody first comes within sight of the walls the garrison stands to: the <b>half-kin</b>
 * - children of the god whose keystone it is, soldiers at the gate and in the courtyard, archers and casters on the
 * towers - and on the roof of the keep, beside the keystone, a <b>fallen god</b>: one of the Titans the gods cast
 * down, Hyperion of the Sky, Oceanus of the Tides or Cronus of the Shades. It carries a bar of its own, and the
 * first time it falls to somebody it gives up a piece of the panoply.
 */
public final class Citadels {
    /** Half the width of a citadel, platform and all. */
    public static final int HALF = 21;
    /** The curtain wall stands this far from the middle; the corner towers are centred on its corners. */
    public static final int WALL = 18;
    /** The keep stands this far north of the middle, and reaches this far either way from its own middle. */
    public static final int KEEP_NORTH = 5, KEEP_HALF = 7;
    /** Where the floors of the keep are: the ground, the upper storey, the roof. */
    public static final int STOREY = 6, ROOF = 12;
    /** How close somebody has to come before the garrison stands to. */
    public static final int WAKE_DISTANCE = 48;
    public static final String TITAN_TAG = "roc_titan", HALFKIN_TAG = "roc_halfkin", CITADEL_TAG = "roc_citadel";

    /** Each citadel's stone. */
    private record Style(Block floor, Block floorAlt, Block wall, Block worn, Block trim, Block pillar, Block glow, Block lantern, Block accent,
                         Block stairs, Block statue, Block statueHead, Block fire, Block roof) {}

    private static Style style(Line line) {
        return switch (line) {
            case ZEUS -> new Style(Blocks.SMOOTH_QUARTZ, Blocks.CALCITE, Blocks.QUARTZ_BRICKS, Blocks.CALCITE, Blocks.POLISHED_ANDESITE, Blocks.QUARTZ_PILLAR,
                    Blocks.GLOWSTONE, Blocks.LANTERN, Blocks.GOLD_BLOCK, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CAMPFIRE, Blocks.DEEPSLATE_TILES);
            case POSEIDON -> new Style(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE,
                    Blocks.SEA_LANTERN, Blocks.LANTERN, Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.CAMPFIRE, Blocks.DARK_PRISMARINE);
            case HADES -> new Style(Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                    Blocks.POLISHED_BASALT, Blocks.POLISHED_BASALT, Blocks.CRYING_OBSIDIAN, Blocks.SOUL_LANTERN, Blocks.CRYING_OBSIDIAN,
                    Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.BASALT, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.SOUL_CAMPFIRE, Blocks.RED_NETHER_BRICKS);
        };
    }

    /** The fallen god who holds each citadel. */
    public enum Titan {
        HYPERION(Line.ZEUS), OCEANUS(Line.POSEIDON), CRONUS(Line.HADES);

        public final Line line;

        Titan(Line line) {
            this.line = line;
        }

        public static Titan of(Line line) {
            for (Titan t : values()) if (t.line == line) return t;
            return HYPERION;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("titan." + RealmsOfChaos.MOD_ID + "." + id());
        }
    }

    private Citadels() {}

    // ---- where things are, from the middle of the citadel's floor --------------------------------------------

    public static BlockPos keep(BlockPos c) {
        return c.offset(0, 0, -KEEP_NORTH);
    }

    /** The chest that keeps the keystone: on the roof of the keep. */
    public static BlockPos keystoneChest(BlockPos c) {
        return keep(c).offset(0, ROOF + 1, -3);
    }

    /** Where the fallen god stands to: on the roof, between the stair and the keystone. */
    public static BlockPos titanPost(BlockPos c) {
        return keep(c).offset(0, ROOF + 1, 0);
    }

    // ---- building ---------------------------------------------------------------------------------------------

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).equals(state)) level.setBlock(pos, state, 2);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        set(level, pos, block.defaultBlockState());
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** The ground a citadel is laid on: the middle height of nine points across its footprint, so it sits on the land rather than a single hilltop. */
    private static int ground(ServerLevel level, int x, int z) {
        int[] heights = new int[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk((x + dx * HALF) >> 4, (z + dz * HALF) >> 4);
                heights[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx * HALF, z + dz * HALF) - 1;
            }
        }
        Arrays.sort(heights);
        return Math.max(level.getMinY() + 12, Math.min(level.getMaxY() - 40, heights[4]));
    }

    /** Builds the citadel of one tower about {@code (x, z)}. @return the middle of its floor */
    public static BlockPos build(ServerLevel level, Line line, int x, int z, Random random) {
        Style s = style(line);
        BlockPos c = new BlockPos(x, ground(level, x, z), z);
        // the platform: a floor at the ground, a foundation under it down to solid ground, clear air over it
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                set(level, c.offset(dx, 0, dz), (dx + dz & 1) == 0 || random.nextInt(5) == 0 ? s.floor : s.floorAlt);
                for (int dy = 1; dy <= 30 && !solid(level, c.offset(dx, -dy, dz)); dy++) set(level, c.offset(dx, -dy, dz), Mason.weather(s.wall, c.getX() + dx, c.getY() - dy, c.getZ() + dz, 30));
                for (int dy = 1; dy <= 32; dy++) {
                    if (!level.getBlockState(c.offset(dx, dy, dz)).isAir()) level.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // the trees standing round the platform go too, so no crown is left hanging over the walls to rot onto the stones
        for (int dx = -HALF - 6; dx <= HALF + 6; dx++) {
            for (int dz = -HALF - 6; dz <= HALF + 6; dz++) {
                if (Math.abs(dx) <= HALF && Math.abs(dz) <= HALF) continue;
                for (int dy = -2; dy <= 34; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(at);
                    if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // a processional way from outside the gate to the door of the keep
        for (int dz = -KEEP_NORTH + KEEP_HALF; dz <= HALF; dz++) {
            for (int dx = -1; dx <= 1; dx++) set(level, c.offset(dx, 0, dz), dx == 0 ? s.trim : s.floor);
        }
        // the platform's edge is dressed: a course of stairs all round, stepping up to the floor
        Mason.Stone stone = Mason.of(s.wall);
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != HALF || (dz > 0 && Math.abs(dx) <= 2) || Math.abs(dx) == Math.abs(dz)) continue;
                set(level, c.offset(dx, 0, dz), Mason.stair(stone.stairs(), Mason.outward(dx, dz).getOpposite(), false));
            }
        }
        moat(level, line, c, s);
        curtain(level, c, s, random);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) tower(level, c.offset(sx * WALL, 0, sz * WALL), sx, sz, s);
        gatehouse(level, c, s);
        keepHouse(level, line, c, s, random);
        courtyard(level, line, c, s, random);
        return c;
    }

    /** Round the walls: water for the Tides, a ring of soul fire for the Shades, a gilded border for the Sky. */
    private static void moat(ServerLevel level, Line line, BlockPos c, Style s) {
        for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
            for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < WALL + 1 || ring > WALL + 2) continue;
                boolean bridge = dz > 0 && Math.abs(dx) <= 2;
                BlockPos at = c.offset(dx, 0, dz);
                switch (line) {
                    case POSEIDON -> {
                        if (bridge) break;
                        set(level, at.below(), s.trim);
                        set(level, at, Blocks.WATER);
                    }
                    case HADES -> {
                        if (bridge || ring != WALL + 2 || (dx + dz) % 3 != 0) break;
                        set(level, at, Blocks.SOUL_SOIL);
                        set(level, at.above(), Blocks.SOUL_FIRE);
                    }
                    case ZEUS -> {
                        if (ring == WALL + 2 && (dx + dz) % 4 == 0) set(level, at, s.accent);
                    }
                }
            }
        }
    }

    /**
     * The curtain wall: two thick and eight high on a battered plinth, buttressed every six blocks, with arrow slits
     * between the buttresses, and a parapet carried out over the face on a row of corbels, crenellated, walked along
     * its top.
     */
    private static void curtain(ServerLevel level, BlockPos c, Style s, Random random) {
        Mason.Stone stone = Mason.of(s.wall);
        for (int dx = -WALL - 1; dx <= WALL + 1; dx++) {
            for (int dz = -WALL - 1; dz <= WALL + 1; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < WALL - 1) continue;
                int along = Math.abs(dx) >= Math.abs(dz) ? dz : dx;
                Direction out = Mason.outward(dx, dz), in = out.getOpposite();
                boolean corner = Math.abs(dx) > WALL - 3 && Math.abs(dz) > WALL - 3;
                if (ring == WALL + 1) {
                    if (corner) continue;                                   // the corner towers stand here
                    boolean gate = dz > 0 && Math.abs(dx) <= 2;
                    if (!gate) set(level, c.offset(dx, 1, dz), Mason.stair(stone.stairs(), in, false));   // the plinth
                    boolean buttress = Math.floorMod(along, 6) == 0 && !gate;
                    if (buttress) {
                        for (int dy = 1; dy <= 5; dy++) set(level, c.offset(dx, dy, dz), dy == 1 ? s.trim : s.wall);
                        set(level, c.offset(dx, 6, dz), Mason.stair(stone.stairs(), in, false));
                    }
                    set(level, c.offset(dx, 7, dz), Mason.stair(stone.stairs(), in, true));             // corbels
                    set(level, c.offset(dx, 8, dz), s.trim);
                    if ((along & 1) == 0) set(level, c.offset(dx, 9, dz), s.wall);
                    if (Math.floorMod(along, 6) == 3) set(level, c.offset(dx, 10, dz), s.lantern);
                    continue;
                }
                for (int dy = 1; dy <= 8; dy++) {
                    Block b = random.nextInt(9) == 0 ? s.worn : dy == 1 || dy == 4 || dy == 8 ? s.trim : Mason.weather(s.wall, c.getX() + dx, c.getY() + dy, c.getZ() + dz, 22);
                    set(level, c.offset(dx, dy, dz), b);
                }
                // an arrow slit in the outer face between each pair of buttresses
                if (ring == WALL && Math.floorMod(along, 6) == 3 && !corner) {
                    set(level, c.offset(dx, 5, dz), Blocks.AIR);
                    set(level, c.offset(dx, 6, dz), Blocks.AIR);
                    set(level, c.offset(dx, 7, dz), Mason.stair(stone.stairs(), out, true));
                }
            }
        }
    }

    /**
     * A corner tower: round, banded, slit, its battlements carried out on corbels, an open lookout on top under a
     * conical roof on four posts; hollow within, a landing level with the wall-walk, scaffolding up its middle.
     */
    private static void tower(ServerLevel level, BlockPos t, int sx, int sz, Style s) {
        Mason.Stone stone = Mason.of(s.wall);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 5.5) continue;
                Direction out = Mason.outward(dx, dz), in = out.getOpposite();
                if (d > 4.5) {
                    // round the shaft: a plinth at its foot, corbels and a crenellated parapet at its head
                    BlockPos foot = t.offset(dx, 1, dz);
                    if (Math.abs(t.getX() + dx - (t.getX() - sx * WALL)) <= HALF && Math.abs(t.getZ() + dz - (t.getZ() - sz * WALL)) <= HALF
                            && level.getBlockState(foot).isAir()) set(level, foot, Mason.stair(stone.stairs(), in, false));
                    set(level, t.offset(dx, 13, dz), Mason.stair(stone.stairs(), in, true));
                    set(level, t.offset(dx, 14, dz), s.trim);
                    set(level, t.offset(dx, 15, dz), ((dx + dz) & 1) == 0 ? s.wall : Blocks.AIR);
                    continue;
                }
                for (int dy = 1; dy <= 14; dy++) {
                    BlockPos at = t.offset(dx, dy, dz);
                    if (d > 2.6) set(level, at, dy % 5 == 0 || dy == 1 ? s.trim : Mason.weather(s.wall, at.getX(), at.getY(), at.getZ(), 22));
                    else if (dy == 8 || dy == 14) set(level, at, s.floor);
                    else set(level, at, Blocks.AIR);
                }
                for (int dy = 15; dy <= 16; dy++) set(level, t.offset(dx, dy, dz), Blocks.AIR);
            }
        }
        // the floor of the lookout reaches out to the parapet, and four posts carry its roof
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 2.6 && d <= 5.5) set(level, t.offset(dx, 14, dz), d > 4.5 ? s.trim : s.floor);
            }
        }
        for (int qx = -1; qx <= 1; qx += 2) {
            for (int qz = -1; qz <= 1; qz += 2) {
                for (int dy = 15; dy <= 16; dy++) set(level, t.offset(qx * 3, dy, qz * 3), s.pillar);
            }
        }
        Mason.cone(level, t.above(17), 5, Mason.of(s.roof), s.accent);
        // arrow slits facing out across the country
        for (int dy : new int[]{4, 5, 10, 11}) set(level, t.offset(sx * 3, dy, sz * 3), Blocks.AIR);
        // the light in each tower's ceiling, and the scaffolding that is its stair
        set(level, t.offset(-sx, 14, -sz), s.glow);
        for (int dy = 1; dy <= 14; dy++) set(level, t.above(dy), Blocks.SCAFFOLDING);
        // a door from the courtyard, at ground level
        for (int k = 1; k <= 5; k++) {
            for (int dy = 1; dy <= 2; dy++) set(level, t.offset(-sx * k, dy, -sz * 3), Blocks.AIR);
        }
        // and two out onto the wall-walk, one along each wall
        for (int k = 3; k <= 4; k++) {
            for (int dy = 9; dy <= 10; dy++) {
                set(level, t.offset(-sx * k, dy, 0), Blocks.AIR);
                set(level, t.offset(-sx * k, dy, -sz), Blocks.AIR);
                set(level, t.offset(0, dy, -sz * k), Blocks.AIR);
                set(level, t.offset(-sx, dy, -sz * k), Blocks.AIR);
            }
        }
    }

    /**
     * The gatehouse: two square towers either side of the gate, joined over it into one block, the gate itself under
     * a round head with the portcullis drawn up, and battlements carried out on corbels all round its top.
     */
    private static void gatehouse(ServerLevel level, BlockPos c, Style s) {
        Mason.Stone stone = Mason.of(s.wall);
        int front = WALL + 2, back = WALL - 2;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = back - 1; dz <= front + 1; dz++) {
                boolean rim = Math.abs(dx) == 8 || dz == back - 1 || dz == front + 1;
                boolean passage = Math.abs(dx) <= 2;
                if (rim) {
                    // round the top: corbels, a parapet course, merlons
                    Direction out = Math.abs(dx) == 8 ? (dx > 0 ? Direction.EAST : Direction.WEST) : dz > WALL ? Direction.SOUTH : Direction.NORTH;
                    if (Math.abs(dx) == 8 && (dz == back - 1 || dz == front + 1)) continue;
                    set(level, c.offset(dx, 11, dz), Mason.stair(stone.stairs(), out.getOpposite(), true));
                    set(level, c.offset(dx, 12, dz), s.trim);
                    if (((dx + dz) & 1) == 0) set(level, c.offset(dx, 13, dz), s.wall);
                    continue;
                }
                for (int dy = passage ? 5 : 1; dy <= 12; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    set(level, at, dy % 4 == 0 || dy == 1 ? s.trim : Mason.weather(s.wall, at.getX(), at.getY(), at.getZ(), 22));
                }
            }
        }
        // the gate: open four high, a round head over it on the outer face, the portcullis drawn up into the wall
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = back; dz <= front; dz++) {
                for (int dy = 1; dy <= 4; dy++) set(level, c.offset(dx, dy, dz), Blocks.AIR);
                set(level, c.offset(dx, 5, dz), dz == WALL ? Blocks.IRON_BARS : s.trim);
            }
            set(level, c.offset(dx, 6, front), s.accent);
        }
        for (int dz = WALL + 1; dz <= front; dz++) {
            for (int side = -1; side <= 1; side += 2) set(level, c.offset(side * 2, 4, dz), Mason.stair(stone.stairs(), side < 0 ? Direction.WEST : Direction.EAST, true));
        }
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos bar = c.offset(dx, 5, WALL);
            level.setBlock(bar, Block.updateFromNeighbourShapes(Blocks.IRON_BARS.defaultBlockState(), level, bar), 2);
        }
        // arrow slits in the towers' faces, lanterns either side of the gate, a light over it
        for (int side = -1; side <= 1; side += 2) {
            for (int dy : new int[]{6, 7, 9, 10}) set(level, c.offset(side * 5, dy, front), Blocks.AIR);
            set(level, c.offset(side * 3, 3, front + 1), s.glow);
            set(level, c.offset(side * 5, 13, front), s.lantern);
            level.setBlock(c.offset(side * 3, 4, front + 1), Mason.stair(stone.stairs(), Direction.NORTH, true), 2);
        }
        set(level, c.offset(0, 9, front + 1), s.accent);
    }

    private static BlockState stair(Style s, Direction rising) {
        BlockState state = s.stairs.defaultBlockState();
        return state.hasProperty(StairBlock.FACING) ? state.setValue(StairBlock.FACING, rising) : state;
    }

    /** The keep: three storeys, a stair up each, windows, a roof with a parapet - and the keystone on it. */
    private static void keepHouse(ServerLevel level, Line line, BlockPos c, Style s, Random random) {
        BlockPos k = keep(c);
        for (int dx = -KEEP_HALF; dx <= KEEP_HALF; dx++) {
            for (int dz = -KEEP_HALF; dz <= KEEP_HALF; dz++) {
                boolean edge = Math.abs(dx) == KEEP_HALF || Math.abs(dz) == KEEP_HALF;
                for (int dy = 1; dy <= ROOF; dy++) {
                    BlockPos at = k.offset(dx, dy, dz);
                    if (edge) set(level, at, random.nextInt(11) == 0 ? s.worn : dy == STOREY || dy == ROOF ? s.trim : s.wall);
                    else set(level, at, dy == STOREY || dy == ROOF ? s.floor : Blocks.AIR);
                }
                if (edge) {
                    set(level, k.offset(dx, ROOF + 1, dz), s.wall);
                    set(level, k.offset(dx, ROOF + 2, dz), ((dx + dz) & 1) == 0 ? s.wall : Blocks.AIR);
                } else {
                    set(level, k.offset(dx, 0, dz), (dx + dz & 1) == 0 ? s.floor : s.floorAlt);
                }
            }
        }
        // a pillar up each corner, capped with the tower's own stone
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dy = 1; dy <= ROOF + 2; dy++) set(level, k.offset(sx * KEEP_HALF, dy, sz * KEEP_HALF), s.pillar);
                set(level, k.offset(sx * KEEP_HALF, ROOF + 3, sz * KEEP_HALF), s.accent);
            }
        }
        // the door, and windows on every face
        for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) set(level, k.offset(dx, dy, KEEP_HALF), Blocks.AIR);
        for (int along : new int[]{-4, 4}) {
            for (int dy : new int[]{2, 3, 8, 9}) {
                set(level, k.offset(along, dy, -KEEP_HALF), Blocks.AIR);
                set(level, k.offset(-KEEP_HALF, dy, along), Blocks.AIR);
                set(level, k.offset(KEEP_HALF, dy, along), Blocks.AIR);
                if (dy > 4) set(level, k.offset(along, dy, KEEP_HALF), Blocks.AIR);
            }
        }
        // lights set into the inner walls of both storeys
        for (int along : new int[]{-3, 3}) {
            for (int dy : new int[]{4, 10}) {
                set(level, k.offset(along, dy, -KEEP_HALF), s.glow);
                set(level, k.offset(-KEEP_HALF, dy, along), s.glow);
                set(level, k.offset(KEEP_HALF, dy, along), s.glow);
            }
        }
        // dressing the outside: a plinth, a string course between the storeys, corbels under the parapet
        Mason.Stone stone = Mason.of(s.wall);
        int o = KEEP_HALF + 1;
        for (int dx = -o; dx <= o; dx++) {
            for (int dz = -o; dz <= o; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != o || Math.abs(dx) == Math.abs(dz)) continue;
                Direction in = Mason.outward(dx, dz).getOpposite();
                if (!(dz == o && Math.abs(dx) <= 2)) set(level, k.offset(dx, 1, dz), Mason.stair(stone.stairs(), in, false));
                set(level, k.offset(dx, STOREY, dz), Mason.slab(stone.slab(), true));
                set(level, k.offset(dx, ROOF, dz), Mason.stair(stone.stairs(), in, true));
                int along = Math.abs(dx) == o ? dz : dx;
                if (Math.abs(along) == 4) {
                    // a hood over each window, a sill under each upper one
                    if (!(dz == o)) set(level, k.offset(dx, 4, dz), Mason.stair(stone.stairs(), in, true));
                    set(level, k.offset(dx, 10, dz), Mason.stair(stone.stairs(), in, true));
                    set(level, k.offset(dx, 7, dz), Mason.stair(stone.stairs(), in, true));
                }
            }
        }
        // a porch over the door: two columns, a round head, an entablature and a pediment
        for (int side = -1; side <= 1; side += 2) {
            for (int dy = 1; dy <= 4; dy++) set(level, k.offset(side * 2, dy, o), dy == 1 ? s.trim : s.pillar);
            set(level, k.offset(side, 4, o), Mason.stair(stone.stairs(), side < 0 ? Direction.WEST : Direction.EAST, true));
            set(level, k.offset(side * 2, 0, o + 1), s.trim);
            set(level, k.offset(side * 3, 1, o), s.fire);
        }
        set(level, k.offset(0, 4, o), Mason.slab(stone.slab(), true));
        for (int dx = -2; dx <= 2; dx++) set(level, k.offset(dx, 5, o), s.trim);
        Mason.pediment(level, k.offset(0, 6, o), Direction.NORTH, 7, stone, s.accent);
        // the roof is laid in a pattern: a border inside the parapet, a ring about the middle
        for (int dx = -KEEP_HALF + 1; dx <= KEEP_HALF - 1; dx++) {
            for (int dz = -KEEP_HALF + 1; dz <= KEEP_HALF - 1; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == KEEP_HALF - 1 || ring == 3) set(level, k.offset(dx, ROOF, dz), s.floorAlt);
            }
        }
        // the stairs: up the west side to the upper storey, up the east side to the roof, each through a hole in the floor above
        for (int flight = 0; flight < 2; flight++) {
            int x0 = flight == 0 ? -5 : 4, base = flight == 0 ? 0 : STOREY, top = flight == 0 ? STOREY : ROOF;
            for (int i = 0; i < top - base - 1; i++) {
                for (int w = 0; w <= 1; w++) {
                    set(level, k.offset(x0 + w, base + 1 + i, 4 - i), stair(s, Direction.NORTH));
                    for (int below = base + 1; below < base + 1 + i; below++) set(level, k.offset(x0 + w, below, 4 - i), s.wall);
                }
            }
            for (int w = 0; w <= 1; w++) for (int dz = 0; dz <= 4; dz++) set(level, k.offset(x0 + w, top, dz), Blocks.AIR);
        }
        // the upper storey keeps the garrison's stores
        chest(level, k.offset(-3, STOREY + 1, -5), stores(line, random));
        set(level, k.offset(3, STOREY + 1, -5), Blocks.BARREL);
        // and the roof the keystone, behind braziers
        chest(level, keystoneChest(c), keystoneLoot(line, random));
        set(level, k.offset(-2, ROOF + 1, -3), s.fire);
        set(level, k.offset(2, ROOF + 1, -3), s.fire);
    }

    /** The courtyard: the statues of gods who fell, some still standing, most on the ground; fires, rubble, a well. */
    private static void courtyard(ServerLevel level, Line line, BlockPos c, Style s, Random random) {
        // standing statues on plinths either side of the way in
        for (int side = -1; side <= 1; side += 2) {
            BlockPos plinth = c.offset(side * 6, 0, 11);
            set(level, plinth.above(), s.trim);
            for (Direction d : Direction.Plane.HORIZONTAL) set(level, plinth.above().relative(d), Mason.stair(Mason.of(s.wall).stairs(), d.getOpposite(), false));
            set(level, plinth.above(2), s.statue);
            set(level, plinth.above(3), s.statue);
            set(level, plinth.above(4), s.statueHead);
            set(level, c.offset(side * 6, 1, 15), s.fire);
        }
        // lamp posts along the processional way
        Block post = Mason.of(s.wall).wall();
        for (int side = -1; side <= 1; side += 2) {
            for (int dz : new int[]{4, 9}) {
                set(level, c.offset(side * 3, 1, dz), s.trim);
                set(level, c.offset(side * 3, 2, dz), post);
                set(level, c.offset(side * 3, 3, dz), post);
                set(level, c.offset(side * 3, 4, dz), s.lantern);
            }
        }
        // fallen ones: a body and a head lying on the stones, the plinth left empty
        int[][] fallen = {{-12, 4, 1}, {12, -2, -1}, {-11, -12, 1}, {11, 10, -1}};
        for (int[] f : fallen) {
            BlockPos at = c.offset(f[0], 0, f[1]);
            set(level, at.above(), s.trim);
            for (int i = 1; i <= 3; i++) set(level, at.offset(0, 1, f[2] * i), s.statue);
            set(level, at.offset(f[2], 1, f[2] * 4), s.statueHead);
        }
        // rubble from the walls
        for (int i = 0; i < 26; i++) {
            int dx = random.nextInt(WALL * 2 - 3) - WALL + 2, dz = random.nextInt(WALL * 2 - 3) - WALL + 2;
            if (Math.abs(dx) <= 2 || (Math.abs(dx) <= KEEP_HALF + 1 && dz >= -KEEP_NORTH - KEEP_HALF - 1 && dz <= -KEEP_NORTH + KEEP_HALF + 1)) continue;
            BlockPos at = c.offset(dx, 1, dz);
            if (level.getBlockState(at).isAir() && solid(level, at.below())) set(level, at, random.nextBoolean() ? s.worn : Blocks.COBBLESTONE_SLAB);
        }
        // a well behind the keep for the Sky and the Shades; the Tides have the moat
        if (line != Line.POSEIDON) {
            BlockPos well = c.offset(10, 0, -15);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(level, well.offset(dx, 0, dz), dx == 0 && dz == 0 ? (line == Line.HADES ? Blocks.LAVA : Blocks.WATER) : s.trim);
                    if (dx != 0 || dz != 0) set(level, well.offset(dx, 1, dz), Math.abs(dx) + Math.abs(dz) == 2 ? s.pillar : Blocks.AIR);
                }
            }
            set(level, well.below(), s.wall);
        }
    }

    private static void chest(ServerLevel level, BlockPos pos, List<ItemStack> loot) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            int slot = 10;
            for (ItemStack stack : loot) chest.setItem(slot++, stack);
        }
    }

    private static List<ItemStack> keystoneLoot(Line line, Random random) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModContent.keystoneItem(line)));
        loot.add((line == Line.ZEUS ? Story.Chapter.SKY : line == Line.POSEIDON ? Story.Chapter.SEA : Story.Chapter.SHADE).book());
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + random.nextInt(8)));
        loot.add(new ItemStack(Items.GOLDEN_APPLE, 2));
        loot.add(new ItemStack(Items.DIAMOND, 2 + random.nextInt(3)));
        loot.add(new ItemStack(ModContent.materialItem(line, 1), 6 + random.nextInt(5)));
        loot.add(new ItemStack(ModContent.disc("citadel")));
        return loot;
    }

    private static List<ItemStack> stores(Line line, Random random) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(8)));
        loot.add(new ItemStack(Items.ARROW, 16 + random.nextInt(16)));
        loot.add(new ItemStack(Items.COOKED_BEEF, 6 + random.nextInt(6)));
        loot.add(new ItemStack(Items.GOLDEN_CARROT, 4));
        loot.add(new ItemStack(ModContent.materialItem(line, 1), 3 + random.nextInt(3)));
        // the garrison kept the hub's music among its stores
        loot.add(new ItemStack(ModContent.disc("hub")));
        return loot;
    }

    // ---- the garrison ----------------------------------------------------------------------------------------

    private static String wokenKey(Ruins.Site site) {
        return "citadel/woken/" + site.kind() + "/" + site.x() + "/" + site.z();
    }

    public static boolean woken(ServerLevel level, Ruins.Site site) {
        return ProgressData.get(level.getServer()).isBuilt(wokenKey(site));
    }

    private static Sentinel.Kind pick(Line line, Sentinel.Role role, Random random) {
        List<Sentinel.Kind> kinds = new ArrayList<>();
        for (Sentinel.Kind kind : Sentinel.Kind.values()) {
            if (kind.line == line && kind.role == role && kind.trait != Sentinel.Trait.BOMB && kind.trait != Sentinel.Trait.MIRAGE) kinds.add(kind);
        }
        return kinds.get(random.nextInt(kinds.size()));
    }

    private static Sentinel spawn(ServerLevel level, BlockPos at, Sentinel.Kind kind, double threat, boolean elite, Ruins.Site site) {
        Sentinel mob = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return null;
        mob.setUp(kind, 1, threat, elite);
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.addTag(CITADEL_TAG);
        mob.addTag(CITADEL_TAG + "/" + site.kind() + "/" + site.x() + "/" + site.z());
        level.addFreshEntity(mob);
        return mob;
    }

    /**
     * The garrison stands to: half-kin at the gate, in the courtyard, on the towers and in the keep, and the fallen god
     * on the roof. Once for every citadel. @return how many stood to
     */
    public static int wake(ServerLevel level, Ruins.Site site, BlockPos c) {
        if (woken(level, site) || level.getDifficulty() == Difficulty.PEACEFUL || site.kind().line == null) return 0;
        ProgressData.get(level.getServer()).markBuilt(wokenKey(site));
        Line line = site.kind().line;
        Random random = new Random(level.getSeed() ^ (site.x() * 31L + site.z()));
        Component halfkin = Hud.colored(Component.translatable("citadel." + RealmsOfChaos.MOD_ID + ".halfkin", Tower.god(line, Tower.FLOORS).displayName()), line.color());
        List<Object[]> posts = new ArrayList<>();
        posts.add(new Object[]{c.offset(-3, 1, 14), Sentinel.Role.BRUTE});
        posts.add(new Object[]{c.offset(3, 1, 14), Sentinel.Role.BRUTE});
        posts.add(new Object[]{c.offset(-10, 1, 8), Sentinel.Role.SOLDIER});
        posts.add(new Object[]{c.offset(10, 1, 8), Sentinel.Role.SOLDIER});
        posts.add(new Object[]{c.offset(-12, 1, -5), Sentinel.Role.SKIRMISHER});
        posts.add(new Object[]{c.offset(12, 1, -5), Sentinel.Role.SOLDIER});
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) posts.add(new Object[]{c.offset(sx * (WALL - 1), 15, sz * (WALL - 1)), Sentinel.Role.CASTER});
        posts.add(new Object[]{keep(c).offset(2, 1, -2), Sentinel.Role.SOLDIER});
        posts.add(new Object[]{keep(c).offset(0, STOREY + 1, -3), Sentinel.Role.MYSTIC});
        posts.add(new Object[]{keep(c).offset(-3, ROOF + 1, -5), Sentinel.Role.KEEPER});
        posts.add(new Object[]{keep(c).offset(3, ROOF + 1, -5), Sentinel.Role.KEEPER});
        int stood = 0;
        for (Object[] post : posts) {
            Sentinel mob = spawn(level, (BlockPos) post[0], pick(line, (Sentinel.Role) post[1], random), 1.5, false, site);
            if (mob == null) continue;
            mob.addTag(HALFKIN_TAG);
            mob.setCustomName(halfkin);
            stood++;
        }
        Titan titan = Titan.of(line);
        com.thefarawaydev.realmsofchaos.entity.Titan fallen = ModContent.TITAN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (fallen != null) {
            BlockPos post = titanPost(c);
            fallen.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 180.0F, 0.0F);
            fallen.setUp(com.thefarawaydev.realmsofchaos.entity.Titan.Kind.of(line), post);
            fallen.addTag(TITAN_TAG);
            fallen.addTag(CITADEL_TAG);
            level.addFreshEntity(fallen);
            stood++;
        }
        level.playSound(null, c.above(4), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, line == Line.POSEIDON ? 0.7F : 1.0F);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c).inflate(WAKE_DISTANCE + 16, 40, WAKE_DISTANCE + 16), Player::isAlive)) {
            Cards.notice(p, Words.of("citadel." + RealmsOfChaos.MOD_ID + ".woken", site.kind().displayName()), Words.of("citadel." + RealmsOfChaos.MOD_ID + ".woken.sub", titan.displayName()));
        }
        return stood;
    }

    /** A fallen Titan has been beaten by a player: it gives up a piece of the panoply the first time. */
    public static ItemStack fallen(com.thefarawaydev.realmsofchaos.entity.Titan mob, ServerPlayer killer) {
        if (!(mob.level() instanceof ServerLevel level)) return ItemStack.EMPTY;
        Titan titan = Titan.of(mob.kind().line);
        ProgressData data = ProgressData.get(level.getServer());
        String key = "titan/" + killer.getUUID() + "/" + titan.id();
        boolean first = !data.isBuilt(key);
        data.markBuilt(key);
        mob.spawnAtLocation(level, new ItemStack(ModContent.materialItem(titan.line, 2), 4 + level.getRandom().nextInt(4)));
        mob.spawnAtLocation(level, new ItemStack(Items.EXPERIENCE_BOTTLE, 6));
        if (!first) return ItemStack.EMPTY;
        // the first time a Titan falls to somebody, its music on a disc
        mob.spawnAtLocation(level, new ItemStack(ModContent.disc("titan")));
        Panoply.Piece[] pieces = Panoply.Piece.values();
        ItemStack gift = pieces[level.getRandom().nextInt(pieces.length)].stack();
        mob.spawnAtLocation(level, gift.copy());
        Cards.notice(killer, Words.of("citadel." + RealmsOfChaos.MOD_ID + ".titan.fallen", titan.displayName()), Words.of("hud." + RealmsOfChaos.MOD_ID + ".lesser.gift", titan.displayName(), gift.getHoverName()));
        return gift;
    }

    public static boolean felled(ServerPlayer player, Line line) {
        return ProgressData.get(player.level().getServer()).isBuilt("titan/" + player.getUUID() + "/" + Titan.of(line).id());
    }

    /** Nothing to clear any more: a Titan carries its own bar. Kept for the tests that call it. */
    public static void clear() {}
}
