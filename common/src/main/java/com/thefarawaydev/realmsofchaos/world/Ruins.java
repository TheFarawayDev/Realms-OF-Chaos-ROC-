package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The way into the realms lies in the Overworld. Each world has one <b>Ruined Gate</b>, a short walk from where the
 * world began - and one each of three citadels, further out in three different directions, each keeping one of the
 * keystones the gate needs: the Citadel of the Sky, of the Tides and of the Shades. Where they stand is worked out
 * from the world seed, and all four are raised soon after the world is first opened, so they are there to be found.
 *
 * A Ruined Gate holds the story so far and three seekers, compasses that each point to one citadel. Lay the three
 * keystones in the pedestal's cradles (its screen, or a hopper) and the gate wakes.
 */
public final class Ruins {
    public static final int VERSION = 4;
    /** How close a player has to come before a ruin not yet raised is raised at once: a citadel sooner, being seen from further off. */
    public static final int RAISE_DISTANCE = 96, CITADEL_RAISE_DISTANCE = 144;
    /** How far from the world's origin the gate stands, and the citadels. */
    public static final int GATE_NEAR = 160, GATE_FAR = 288, CITADEL_NEAR = 640, CITADEL_FAR = 1024;
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".";

    public enum Kind {
        GATE(null, 0x6A7E, 10), SKY(Line.ZEUS, 0x51C7, Citadels.HALF), SEA(Line.POSEIDON, 0x5EA5, Citadels.HALF), SHADE(Line.HADES, 0x5AADE, Citadels.HALF);

        /** The tower whose keystone a shrine keeps; null for the gate itself. */
        public final Line line;
        final long salt;
        public final int radius;

        Kind(Line line, long salt, int radius) {
            this.line = line;
            this.salt = salt;
            this.radius = radius;
        }

        public static Kind of(Line line) {
            return switch (line) {
                case ZEUS -> SKY;
                case POSEIDON -> SEA;
                case HADES -> SHADE;
            };
        }

        public Component displayName() {
            return Component.translatable("ruin." + RealmsOfChaos.MOD_ID + "." + (line == null ? "gate" : line.id()));
        }
    }

    /** One ruin: its kind, and where it stands. */
    public record Site(Kind kind, int x, int z) {}

    private Ruins() {}

    // ---- where the ruins are ---------------------------------------------------------------------------------

    /**
     * Where a world's one ruin of a kind stands. The gate lies a short walk from the origin; the three citadels much
     * further out, a third of the way round from one another, and none of them on the gate's side.
     */
    public static Site site(long seed, Kind kind) {
        Random random = new Random(seed ^ 0x5EED6A7EL * 0x9E3779B97F4A7C15L);
        double turn = random.nextDouble() * Math.PI * 2;
        int gate = GATE_NEAR + random.nextInt(GATE_FAR - GATE_NEAR);
        if (kind == Kind.GATE) return at(kind, turn, gate);
        Site out = null;
        for (int k = 0; k < 3; k++) {
            double a = turn + Math.PI / 3 + k * Math.PI * 2 / 3 + (random.nextDouble() - 0.5) * 0.7;
            int d = CITADEL_NEAR + random.nextInt(CITADEL_FAR - CITADEL_NEAR);
            if (k == kind.ordinal() - 1) out = at(kind, a, d);
        }
        return out;
    }

    private static Site at(Kind kind, double angle, int distance) {
        return new Site(kind, (int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance));
    }

    /** The ruin of a kind - there is only one in a world, wherever the asker stands. */
    public static Site nearest(long seed, Kind kind, double x, double z) {
        return site(seed, kind);
    }

    private static String key(Site site) {
        return "ruin/" + site.kind + "@" + VERSION + "/y=";
    }

    /** The centre of a raised ruin (its floor block), or null if it has not been raised yet. */
    public static BlockPos centre(ServerLevel level, Site site) {
        String found = ProgressData.get(level.getServer()).findBuilt(key(site));
        return found == null ? null : new BlockPos(site.x, Integer.parseInt(found.substring(key(site).length())), site.z);
    }

    // ---- once in a while, for everyone in the Overworld ------------------------------------------------------

    private static int ticker;

    /**
     * Raises the world's ruins: one every two seconds from when the world is opened, until all four stand - and at
     * once, any a player comes near before then.
     */
    public static void tick(MinecraftServer server) {
        if (++ticker % 40 != 0) return;
        pregenerate(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || Realm.of(level.dimension()) != null) continue;
            for (Kind kind : Kind.values()) {
                Site site = nearest(level.getSeed(), kind, player.getX(), player.getZ());
                double dx = site.x - player.getX(), dz = site.z - player.getZ();
                int raise = kind == Kind.GATE ? RAISE_DISTANCE : CITADEL_RAISE_DISTANCE;
                if (dx * dx + dz * dz <= raise * raise) ensure(level, site);
                // within sight of a citadel's walls, its garrison stands to
                if (kind != Kind.GATE && !player.isCreative() && !player.isSpectator() && dx * dx + dz * dz <= Citadels.WAKE_DISTANCE * Citadels.WAKE_DISTANCE) {
                    BlockPos c = centre(level, site);
                    if (c != null) Citadels.wake(level, site, c);
                }
                if (kind == Kind.GATE && dx * dx + dz * dz <= 40 * 40) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.RUIN);
            }
        }
    }

    /** Raises the next of the world's four ruins not yet standing, if any. @return true if one was raised */
    public static boolean pregenerate(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null || ProgressData.get(server).findBuilt("ruins/all@" + VERSION) != null) return false;
        for (Kind kind : Kind.values()) {
            Site site = site(level.getSeed(), kind);
            if (centre(level, site) != null) continue;
            BlockPos c = ensure(level, site);
            org.slf4j.LoggerFactory.getLogger(RealmsOfChaos.MOD_ID).info("Raised the {} at {}", kind, c);
            return true;
        }
        ProgressData.get(server).markBuilt("ruins/all@" + VERSION);
        return false;
    }

    /** Raises a ruin if it is not standing yet. @return its centre. */
    public static BlockPos ensure(ServerLevel level, Site site) {
        BlockPos centre = centre(level, site);
        if (centre != null) return centre;
        centre = build(level, site);
        ProgressData.get(level.getServer()).markBuilt(key(site) + centre.getY());
        return centre;
    }

    // ---- building --------------------------------------------------------------------------------------------

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2);
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /**
     * Picks the ground for a ruin and prepares it: a round floor at the surface (at the water line over water), a
     * foundation under it down to solid ground, and clear air above. @return the middle of the floor.
     */
    private static BlockPos prepare(ServerLevel level, Site site, Block floor) {
        level.getChunk(site.x >> 4, site.z >> 4);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.x, site.z);
        int y = Math.max(level.getMinY() + 12, Math.min(level.getMaxY() - 24, top - 1));
        BlockPos c = new BlockPos(site.x, y, site.z);
        int r = site.kind.radius + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) continue;
                set(level, c.offset(dx, 0, dz), floor);
                for (int dy = 1; dy <= 24 && !solid(level, c.offset(dx, -dy, dz)); dy++) set(level, c.offset(dx, -dy, dz), Blocks.STONE_BRICKS);
                for (int dy = 1; dy <= 24; dy++) {
                    if (!level.getBlockState(c.offset(dx, dy, dz)).isAir()) set(level, c.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
        // a hillside round the ruin is cut back in a slope rather than left standing as a wall over it, and grassed
        int bank = 10;
        for (int dx = -r - bank; dx <= r + bank; dx++) {
            for (int dz = -r - bank; dz <= r + bank; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r + 0.5 || d > r + bank) continue;
                int floorAt = 1 + (int) Math.round(d - r - 0.5);
                boolean cut = false;
                for (int dy = floorAt; dy <= 26; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    BlockState there = level.getBlockState(at);
                    if (there.isAir() || !there.getFluidState().isEmpty()) continue;
                    set(level, at, Blocks.AIR);
                    cut = true;
                }
                // and where the land falls away from it, it stands on an earth bank sloping down, not a sheer pillar
                int bankTop = 1 - floorAt;
                if (!cut && !level.getBlockState(c.offset(dx, bankTop, dz)).isCollisionShapeFullBlock(level, c.offset(dx, bankTop, dz))) {
                    boolean filled = false;
                    for (int dy = bankTop; dy >= bankTop - 24; dy--) {
                        BlockPos at = c.offset(dx, dy, dz);
                        BlockState there = level.getBlockState(at);
                        if (!there.getFluidState().isEmpty() || there.isCollisionShapeFullBlock(level, at)) break;
                        set(level, at, dy == bankTop ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                        filled = true;
                    }
                    if (filled && level.getBlockState(c.offset(dx, bankTop, dz)).is(Blocks.GRASS_BLOCK)) {
                        for (int dy = bankTop + 1; dy <= bankTop + 3; dy++) {
                            BlockPos above = c.offset(dx, dy, dz);
                            if (!level.getBlockState(above).isAir() && level.getBlockState(above).getFluidState().isEmpty()
                                    && !level.getBlockState(above).isCollisionShapeFullBlock(level, above)) set(level, above, Blocks.AIR);
                        }
                    }
                }
                if (!cut) continue;
                BlockPos ground = c.offset(dx, floorAt - 1, dz);
                BlockState under = level.getBlockState(ground);
                if (under.is(Blocks.DIRT) || under.is(Blocks.STONE) || under.is(Blocks.GRAVEL) || under.is(Blocks.COARSE_DIRT)) set(level, ground, Blocks.GRASS_BLOCK);
            }
        }
        return c;
    }

    private static void chest(ServerLevel level, BlockPos pos, List<ItemStack> loot) {
        set(level, pos, Blocks.CHEST);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            int slot = 10;
            for (ItemStack stack : loot) chest.setItem(slot++, stack);
        }
    }

    public static BlockPos build(ServerLevel level, Site site) {
        Random random = new Random(level.getSeed() ^ site.kind.salt ^ (site.x * 31L + site.z));
        return switch (site.kind) {
            case GATE -> buildGate(level, site, random);
            case SKY, SEA, SHADE -> Citadels.build(level, site.kind.line, site.x, site.z, random);
        };
    }

    // the pedestal stands three blocks in front of the arch; everything about the arch is counted from it
    public static final int ARCH_BACK = -3, LINTEL_UP = 4;

    public static BlockPos pedestalPos(BlockPos centre) {
        return centre.offset(0, 1, -1);
    }

    /** Where a tower's keystone sits, left to right: Poseidon's to the west, Zeus's in the middle, Hades's to the east. */
    public static int cradle(Line line) {
        return line == Line.POSEIDON ? -1 : line == Line.ZEUS ? 0 : 1;
    }

    /** The socket in the arch's lintel that takes this tower's keystone. */
    public static BlockPos socket(BlockPos pedestal, Line line) {
        return pedestal.offset(cradle(line), LINTEL_UP, ARCH_BACK);
    }

    public static Block socketFill(Line line) {
        return switch (line) {
            case ZEUS -> Blocks.GOLD_BLOCK;
            case POSEIDON -> Blocks.SEA_LANTERN;
            case HADES -> Blocks.CRYING_OBSIDIAN;
        };
    }

    /** The twelve blocks of the arch's opening: three wide, four high. */
    public static List<BlockPos> opening(BlockPos pedestal) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 3; dy++) out.add(pedestal.offset(dx, dy, ARCH_BACK));
        return out;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    /**
     * A ruined temple round a dead arch: a stepped round platform, a ring of twelve columns - a few still standing
     * under their capitals, most broken off, some fallen across the stones - and the arch itself, a gateway of two
     * columns under an entablature and a broken pediment, with a pedestal before it, braziers about it, grass and
     * moss coming up between the stones, a campfire still smoking and a chest.
     */
    private static BlockPos buildGate(ServerLevel level, Site site, Random random) {
        BlockPos c = prepare(level, site, Blocks.STONE_BRICKS);
        int r = site.kind.radius;
        Mason.Stone stone = Mason.of(Blocks.STONE_BRICKS), mossy = Mason.of(Blocks.MOSSY_STONE_BRICKS);
        Block[] worn = {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS};
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.5) continue;
                if (d > r + 0.5) {
                    // the platform's edge: one step down all round, worn away here and there
                    Direction in = Mason.outward(dx, dz).getOpposite();
                    if (random.nextInt(7) != 0) set(level, c.offset(dx, 0, dz), Mason.stair((random.nextInt(3) == 0 ? mossy : stone).stairs(), in, false));
                    continue;
                }
                // a paved floor: a ring of dressed stone round the edge, worn flags within
                set(level, c.offset(dx, 0, dz), d > r - 0.5 ? Blocks.POLISHED_ANDESITE : worn[random.nextInt(worn.length)]);
            }
        }
        BlockPos pedestal = pedestalPos(c);
        BlockPos archMid = pedestal.offset(0, 0, ARCH_BACK);
        // the gateway: a column either side of the opening, with base and capital, on a sill of polished stone
        for (int dx = -3; dx <= 3; dx++) set(level, archMid.offset(dx, -1, 0), Blocks.POLISHED_ANDESITE);
        for (int side = -1; side <= 1; side += 2) {
            BlockPos col = archMid.offset(side * 2, 0, 0);
            for (int dy = 0; dy < LINTEL_UP; dy++) set(level, col.above(dy), dy == 0 ? Blocks.CHISELED_STONE_BRICKS : dy == 2 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
            Direction outward = side < 0 ? Direction.WEST : Direction.EAST;
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, outward}) {
                set(level, col.relative(d), Mason.stair(stone.stairs(), d.getOpposite(), false));
                set(level, col.above(LINTEL_UP - 1).relative(d), Mason.stair(stone.stairs(), d.getOpposite(), true));
            }
        }
        // the entablature across the top, its three sockets waiting, and over it a pediment half fallen
        for (int dx = -3; dx <= 3; dx++) {
            set(level, archMid.offset(dx, LINTEL_UP, 0), Math.abs(dx) <= 1 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH}) set(level, archMid.offset(dx, LINTEL_UP, 0).relative(d), Mason.slab(stone.slab(), true));
        }
        for (Line line : Line.values()) set(level, socket(pedestal, line), Blocks.CHISELED_STONE_BRICKS);
        Mason.pediment(level, archMid.above(LINTEL_UP + 1), Direction.NORTH, 7, mossy, Blocks.CHISELED_STONE_BRICKS);
        int broken = random.nextBoolean() ? 1 : -1;                         // one side of the pediment has come down
        for (int dx = 2; dx <= 3; dx++) for (int dy = 1; dy <= 4; dy++) set(level, archMid.offset(broken * dx, LINTEL_UP + dy, 0), Blocks.AIR);
        set(level, archMid.offset(broken * 3, 0, 2), Blocks.CRACKED_STONE_BRICKS);
        set(level, archMid.offset(broken * 4, 0, 2), Mason.slab(mossy.slab(), false));
        set(level, pedestal, ModContent.GATE_PEDESTAL.get());
        // a processional way from the south edge to the pedestal
        for (int dz = 0; dz <= r; dz++) for (int dx = -1; dx <= 1; dx++) set(level, c.offset(dx, 0, dz), dx == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
        // a ring of twelve columns: some standing to their full height and lit, most broken, a few fallen across the stones
        int[] heights = new int[12];
        int[][] where = new int[12][];
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 12;
            int cx = (int) Math.round(Math.sin(a) * (r - 1.5)), cz = (int) Math.round(Math.cos(a) * (r - 1.5));
            where[i] = new int[]{cx, cz};
            if (Math.abs(cx) <= 2 && cz > 0) continue;                      // (the way in stays clear)
            int kind = random.nextInt(5), height = kind == 0 ? 6 : 1 + random.nextInt(4);
            BlockPos foot = c.offset(cx, 0, cz);
            set(level, foot.above(), Blocks.CHISELED_STONE_BRICKS);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(foot.above().relative(d)).isAir()) set(level, foot.above().relative(d), Mason.stair(stone.stairs(), d.getOpposite(), false));
            }
            if (kind == 1) {
                // fallen: its drums lie along the ground, pointing out of the ring, the capital beyond them
                int ox = Integer.signum(cx), oz = Integer.signum(cz);
                for (int k = 2; k <= 4; k++) {
                    BlockPos at = c.offset(cx + ox * k, 1, cz + oz * k);
                    if (Math.abs(cx + ox * k) <= r + 1 && Math.abs(cz + oz * k) <= r + 1)
                        set(level, at, k == 4 ? Mason.slab(mossy.slab(), false) : (k == 3 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
                }
                continue;
            }
            heights[i] = height;
            for (int dy = 2; dy <= height; dy++) set(level, c.offset(cx, dy, cz), (dy & 1) == 0 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS);
            if (kind == 0) {
                // still standing: a capital, and a lamp on it
                for (Direction d : Direction.Plane.HORIZONTAL) set(level, c.offset(cx, height, cz).relative(d), Mason.stair(stone.stairs(), d.getOpposite(), true));
                set(level, c.offset(cx, height + 1, cz), Blocks.CHISELED_STONE_BRICKS);
                set(level, c.offset(cx, height + 2, cz), Blocks.LANTERN);
            } else {
                // broken off: a jagged top
                Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong()));
                set(level, c.offset(cx, height + 1, cz), random.nextBoolean() ? Mason.stair(mossy.stairs(), lean, false) : Mason.slab(mossy.slab(), false));
            }
        }
        // four braziers about the arch, each a bowl on a stepped foot
        for (int[] b2 : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            BlockPos at = c.offset(b2[0], 1, b2[1]);
            set(level, at, Blocks.POLISHED_ANDESITE);
            for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(at.relative(d)).isAir()) set(level, at.relative(d), Mason.stair(Blocks.POLISHED_ANDESITE_STAIRS, d.getOpposite(), false));
            set(level, at.above(), Blocks.CAMPFIRE);
        }
        // grass and moss come up between the stones, away from the way in and the arch
        for (int i = 0; i < 70; i++) {
            int dx = random.nextInt(r * 2 + 1) - r, dz = random.nextInt(r * 2 + 1) - r;
            if (dx * dx + dz * dz > r * r || Math.abs(dx) <= 2 && dz >= -6) continue;
            BlockPos at = c.offset(dx, 1, dz);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isCollisionShapeFullBlock(level, at.below())) continue;
            int roll = random.nextInt(6);
            set(level, at, roll < 3 ? Blocks.MOSS_CARPET : roll < 5 ? Blocks.SHORT_GRASS : Blocks.FERN);
        }
        // whoever was here last left a fire going: its smoke is how the place is seen from far off
        set(level, c.offset(3, 1, 2), Blocks.CAMPFIRE);
        set(level, c.offset(3, 0, 2), Blocks.HAY_BLOCK);
        List<ItemStack> loot = new ArrayList<>();
        loot.add(Story.Chapter.SUNDERING.book());
        for (Line line : Line.values()) loot.add(new ItemStack(ModContent.seekerItem(line)));
        loot.add(new ItemStack(Items.BREAD, 6));
        loot.add(new ItemStack(Items.TORCH, 16));
        loot.add(new ItemStack(ModContent.disc("gate")));
        chest(level, c.offset(-3, 1, 2), loot);
        return c;
    }

    /** Where a citadel keeps its keystone, from the middle of its floor. */
    public static BlockPos keystoneChest(BlockPos centre) {
        return Citadels.keystoneChest(centre);
    }

    // ---- the pedestal ----------------------------------------------------------------------------------------

    /** True if an arch with sockets stands where a pedestal at {@code pedestal} expects it. */
    public static boolean hasArch(ServerLevel level, BlockPos pedestal) {
        for (Line line : Line.values()) {
            BlockState state = level.getBlockState(socket(pedestal, line));
            if (!state.is(Blocks.CHISELED_STONE_BRICKS) && !state.is(socketFill(line))) return false;
        }
        return true;
    }

    public static boolean isSet(ServerLevel level, BlockPos pedestal, Line line) {
        return level.getBlockState(socket(pedestal, line)).is(socketFill(line));
    }

    public static int keystonesSet(ServerLevel level, BlockPos pedestal) {
        int n = 0;
        for (Line line : Line.values()) if (isSet(level, pedestal, line)) n++;
        return n;
    }

    public static boolean awake(ServerLevel level, BlockPos pedestal) {
        return level.getBlockState(opening(pedestal).get(0)).is(ModContent.GATE_HUB.get());
    }

    /** Sets a keystone in its socket in the arch, and in its cradle on the pedestal; the third wakes the gate. */
    public static void setKeystone(ServerLevel level, BlockPos pedestal, Line line) {
        if (!hasArch(level, pedestal) || isSet(level, pedestal, line)) return;
        BlockPos socket = socket(pedestal, line);
        set(level, socket, socketFill(line));
        level.sendParticles(Signature.spark(line), socket.getX() + 0.5, socket.getY() + 0.5, socket.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), pedestal.getX() + 0.5 + cradle(line) * 0.28, pedestal.getY() + 1.0, pedestal.getZ() + 0.5, 12, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, pedestal, ModContent.sound("keystone_set"), SoundSource.BLOCKS, 1.0F, 0.9F + 0.1F * keystonesSet(level, pedestal));
        showPedestal(level, pedestal);
        if (keystonesSet(level, pedestal) == Line.values().length) wake(level, pedestal);
    }

    /** Brings the pedestal's look into step with the arch: a lit keystone in each cradle whose socket is set. */
    public static void showPedestal(ServerLevel level, BlockPos pedestal) {
        BlockState state = level.getBlockState(pedestal);
        if (!(state.getBlock() instanceof com.thefarawaydev.realmsofchaos.block.GatePedestalBlock)) return;
        BlockState now = state.setValue(com.thefarawaydev.realmsofchaos.block.GatePedestalBlock.AWAKE, awake(level, pedestal));
        for (Line line : Line.values()) now = now.setValue(com.thefarawaydev.realmsofchaos.block.GatePedestalBlock.set(line), isSet(level, pedestal, line));
        if (now != state) level.setBlock(pedestal, now, 3);
    }

    /** All three keystones are set: the arch fills with the gate to the hub. */
    public static void wake(ServerLevel level, BlockPos pedestal) {
        for (BlockPos p : opening(pedestal)) set(level, p, ModContent.GATE_HUB.get());
        BlockPos mid = pedestal.offset(0, 1, ARCH_BACK);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(mid.getX() + 0.5, mid.getY() + 4, mid.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 200, 1.0, 1.5, 0.3, 0.6);
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 60, 1.0, 1.5, 0.3, 0.1);
        level.playSound(null, mid, ModContent.sound("gate_awaken"), SoundSource.BLOCKS, 1.4F, 1.0F);
        showPedestal(level, pedestal);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(mid).inflate(32), e -> true)) {
            Hud.title(p, Hud.colored(Component.translatable(HUD + "gate.wakes"), 0xA55AD6), Hud.colored(Component.translatable(HUD + "gate.wakes.sub"), 0xCCCCCC));
        }
    }

    // ---- compasses -------------------------------------------------------------------------------------------

    public static String directionKey(double dx, double dz) {
        String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        // 0 = south (+z), turning clockwise seen from above: west is -x
        return "hud." + RealmsOfChaos.MOD_ID + ".passage.dir." + names[Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8)];
    }

    /**
     * A compass for ruins of one kind: says how far and which way the nearest is, and draws a short trail towards
     * it. Only in the Overworld. @return the ruin pointed at, or null.
     */
    public static Site point(ServerPlayer player, Kind kind) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || Realm.of(level.dimension()) != null) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "point.overworld"), 0xAAAAAA));
            return null;
        }
        Site site = nearest(level.getSeed(), kind, player.getX(), player.getZ());
        double dx = site.x + 0.5 - player.getX(), dz = site.z + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int color = kind.line == null ? 0xA55AD6 : kind.line.color();
        if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(player)) {
            // on the tab: whose seeker, what it points at, how far - and the needle, which keeps pointing as the player turns
            Component heading = (kind.line == null ? Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass") : new ItemStack(ModContent.seekerItem(kind.line)).getHoverName().copy())
                    .withStyle(style -> style.withColor(color));
            boolean here = dist < 12;
            com.thefarawaydev.realmsofchaos.ui.CompassTab.show(player, heading, kind.displayName(), here ? Component.translatable(HUD + "tab.here") : com.thefarawaydev.realmsofchaos.ui.CompassTab.distance(dist, false),
                    null, com.thefarawaydev.realmsofchaos.ui.CompassTab.pack(here ? -1 : com.thefarawaydev.realmsofchaos.ui.CompassTab.bearing(dx, dz), 5,
                            com.thefarawaydev.realmsofchaos.ui.CompassTab.LOCK, true, 0, 1.0, 0));
        } else if (dist < 12) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "point.here", kind.displayName()), color));
        } else {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "point", kind.displayName(), (int) Math.round(dist),
                    Component.translatable(directionKey(dx, dz))), color));
            for (int i = 1; i <= 10; i++) {
                level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), player.getX() + dx / dist * i * 0.6, player.getY() + 1.2, player.getZ() + dz / dist * i * 0.6, 1, 0, 0, 0, 0);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.6F, 1.2F);
        return site;
    }
}
