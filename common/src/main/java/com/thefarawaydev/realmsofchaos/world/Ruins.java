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
 * The way into the realms lies in the Overworld. Scattered across it, at places worked out from the world seed, are
 * <b>Ruined Gates</b> - and three kinds of shrine, each keeping one of the keystones a gate needs: the Shrine of
 * the Sky, of the Tides and of the Shades. A ruin is raised on the surface the first time a player comes near it.
 *
 * A Ruined Gate holds the story so far and three seekers, compasses that each point to the nearest shrine of one
 * keystone. Set the three keystones in the arch (right-click the pedestal) and the gate wakes.
 */
public final class Ruins {
    public static final int VERSION = 1;
    /** How close a player has to come before a ruin is raised. */
    public static final int RAISE_DISTANCE = 96;
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".";

    public enum Kind {
        GATE(null, 704, 0x6A7E, 6), SKY(Line.ZEUS, 576, 0x51C7, 5), SEA(Line.POSEIDON, 576, 0x5EA5, 5), SHADE(Line.HADES, 576, 0x5AADE, 5);

        /** The tower whose keystone a shrine keeps; null for the gate itself. */
        public final Line line;
        /** Ruins of a kind lie one to a square of this many blocks. */
        public final int spacing;
        final long salt;
        public final int radius;

        Kind(Line line, int spacing, long salt, int radius) {
            this.line = line;
            this.spacing = spacing;
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

    /** One ruin: its kind, the square of the world it belongs to, and where in that square it stands. */
    public record Site(Kind kind, int rx, int rz, int x, int z) {}

    private Ruins() {}

    // ---- where the ruins are ---------------------------------------------------------------------------------

    public static Site site(long seed, Kind kind, int rx, int rz) {
        Random random = new Random(seed ^ (kind.salt * 0x9E3779B97F4A7C15L) ^ (rx * 341873128712L) ^ (rz * 132897987541L));
        int margin = 96, room = kind.spacing - 2 * margin;
        return new Site(kind, rx, rz, rx * kind.spacing + margin + random.nextInt(room), rz * kind.spacing + margin + random.nextInt(room));
    }

    /** The ruin of this kind closest to a place. There is always one within a square or two. */
    public static Site nearest(long seed, Kind kind, double x, double z) {
        int rx = Math.floorDiv((int) Math.floor(x), kind.spacing), rz = Math.floorDiv((int) Math.floor(z), kind.spacing);
        Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Site s = site(seed, kind, rx + dx, rz + dz);
                double d = (s.x - x) * (s.x - x) + (s.z - z) * (s.z - z);
                if (d < bestDist) {
                    bestDist = d;
                    best = s;
                }
            }
        }
        return best;
    }

    private static String key(Site site) {
        return "ruin/" + site.kind + "/" + site.rx + "/" + site.rz + "@" + VERSION + "/y=";
    }

    /** The centre of a raised ruin (its floor block), or null if it has not been raised yet. */
    public static BlockPos centre(ServerLevel level, Site site) {
        String found = ProgressData.get(level.getServer()).findBuilt(key(site));
        return found == null ? null : new BlockPos(site.x, Integer.parseInt(found.substring(key(site).length())), site.z);
    }

    // ---- once in a while, for everyone in the Overworld ------------------------------------------------------

    private static int ticker;

    /** Raises the ruins players come near. */
    public static void tick(MinecraftServer server) {
        if (++ticker % 40 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || Realm.of(level.dimension()) != null) continue;
            for (Kind kind : Kind.values()) {
                Site site = nearest(level.getSeed(), kind, player.getX(), player.getZ());
                double dx = site.x - player.getX(), dz = site.z - player.getZ();
                if (dx * dx + dz * dz <= RAISE_DISTANCE * RAISE_DISTANCE) ensure(level, site);
                if (kind == Kind.GATE && dx * dx + dz * dz <= 40 * 40) com.thefarawaydev.realmsofchaos.ui.Tutorial.tip(player, com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.RUIN);
            }
        }
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
                for (int dy = 1; dy <= 9; dy++) {
                    if (!level.getBlockState(c.offset(dx, dy, dz)).isAir()) set(level, c.offset(dx, dy, dz), Blocks.AIR);
                }
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
            case SKY -> buildSky(level, site, random);
            case SEA -> buildSea(level, site, random);
            case SHADE -> buildShade(level, site, random);
        };
    }

    // the pedestal stands three blocks in front of the arch; everything about the arch is counted from it
    public static final int ARCH_BACK = -3, LINTEL_UP = 4;

    public static BlockPos pedestalPos(BlockPos centre) {
        return centre.offset(0, 1, -1);
    }

    /** The socket in the arch's lintel that takes this tower's keystone. */
    public static BlockPos socket(BlockPos pedestal, Line line) {
        return pedestal.offset(line == Line.POSEIDON ? -1 : line == Line.ZEUS ? 0 : 1, LINTEL_UP, ARCH_BACK);
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

    /** A broken plaza round a dead arch, with a pedestal before it, a campfire still smoking and a chest. */
    private static BlockPos buildGate(ServerLevel level, Site site, Random random) {
        BlockPos c = prepare(level, site, Blocks.STONE_BRICKS);
        int r = site.kind.radius;
        Block[] worn = {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS};
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) continue;
                set(level, c.offset(dx, 0, dz), worn[random.nextInt(worn.length)]);
            }
        }
        BlockPos pedestal = pedestalPos(c);
        // the arch: two pillars, a lintel with three empty sockets, and nothing in between - yet
        for (int dy = 0; dy <= LINTEL_UP; dy++) {
            set(level, pedestal.offset(-2, dy, ARCH_BACK), dy % 2 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
            set(level, pedestal.offset(2, dy, ARCH_BACK), dy % 2 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
        }
        for (Line line : Line.values()) set(level, socket(pedestal, line), Blocks.CHISELED_STONE_BRICKS);
        set(level, pedestal.offset(0, LINTEL_UP + 1, ARCH_BACK), Blocks.MOSSY_STONE_BRICKS);
        for (int dx = -1; dx <= 1; dx++) set(level, pedestal.offset(dx, -1, ARCH_BACK), Blocks.POLISHED_ANDESITE);
        set(level, pedestal, ModContent.GATE_PEDESTAL.get());
        // broken columns round the edge, two of them still carrying a light
        int[][] columns = {{-5, -2}, {5, -2}, {-5, 3}, {5, 3}, {-3, 5}, {3, 5}};
        for (int i = 0; i < columns.length; i++) {
            int height = 1 + random.nextInt(4);
            for (int dy = 1; dy <= height; dy++) set(level, c.offset(columns[i][0], dy, columns[i][1]), dy == height && random.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
            if (i < 2) set(level, c.offset(columns[i][0], height + 1, columns[i][1]), Blocks.LANTERN);
            else if (random.nextBoolean()) set(level, c.offset(columns[i][0], height + 1, columns[i][1]), Blocks.MOSS_CARPET);
        }
        // whoever was here last left a fire going: its smoke is how the place is seen from far off
        set(level, c.offset(3, 1, 2), Blocks.CAMPFIRE);
        set(level, c.offset(3, 0, 2), Blocks.HAY_BLOCK);
        List<ItemStack> loot = new ArrayList<>();
        loot.add(Story.Chapter.SUNDERING.book());
        for (Line line : Line.values()) loot.add(new ItemStack(ModContent.seekerItem(line)));
        loot.add(new ItemStack(Items.BREAD, 6));
        loot.add(new ItemStack(Items.TORCH, 16));
        chest(level, c.offset(-3, 1, 2), loot);
        return c;
    }

    private static List<ItemStack> shrineLoot(Line line, Story.Chapter chapter, Random random) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModContent.keystoneItem(line)));
        loot.add(chapter.book());
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + random.nextInt(5)));
        loot.add(new ItemStack(Items.GOLDEN_APPLE));
        loot.add(new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(5)));
        loot.add(new ItemStack(Items.COOKED_BEEF, 4 + random.nextInt(4)));
        return loot;
    }

    /** Shrine of the Sky: an open temple of white stone, four gold-capped columns round the keystone's chest. */
    private static BlockPos buildSky(ServerLevel level, Site site, Random random) {
        BlockPos c = prepare(level, site, Blocks.SMOOTH_QUARTZ);
        int r = site.kind.radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                set(level, c.offset(dx, 0, dz), d < 1.5 ? Blocks.CHISELED_QUARTZ_BLOCK : d > r - 0.6 ? Blocks.QUARTZ_BRICKS : Blocks.SMOOTH_QUARTZ);
            }
        }
        for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int dy = 1; dy <= 5; dy++) set(level, c.offset(k[0], dy, k[1]), Blocks.QUARTZ_PILLAR);
            set(level, c.offset(k[0], 6, k[1]), Blocks.GOLD_BLOCK);
        }
        // a broken ring beam across two of the columns, and a light over the middle
        for (int dx = -2; dx <= 2; dx++) set(level, c.offset(dx, 6, -3), Blocks.QUARTZ_BRICKS);
        set(level, c.offset(0, 6, 3), Blocks.GLOWSTONE);
        set(level, c.offset(-1, 6, 3), Blocks.QUARTZ_BRICKS);
        set(level, c.offset(1, 6, 3), Blocks.QUARTZ_BRICKS);
        chest(level, c.above(), shrineLoot(Line.ZEUS, Story.Chapter.SKY, random));
        return c;
    }

    /** Shrine of the Tides: a ring of prismarine round a pool, sea lanterns at the corners, the chest on an island. */
    private static BlockPos buildSea(ServerLevel level, Site site, Random random) {
        BlockPos c = prepare(level, site, Blocks.PRISMARINE_BRICKS);
        int r = site.kind.radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                boolean bridge = Math.abs(dx) == 0 || Math.abs(dz) == 0;
                if (d >= 2.2 && d < 3.6 && !bridge) {
                    set(level, c.offset(dx, -1, dz), Blocks.DARK_PRISMARINE);       // the pool has a floor of its own
                    set(level, c.offset(dx, 0, dz), Blocks.WATER);
                } else {
                    set(level, c.offset(dx, 0, dz), d < 1.5 ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE_BRICKS);
                }
            }
        }
        for (int[] k : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int dy = 1; dy <= 2; dy++) set(level, c.offset(k[0], dy, k[1]), Blocks.PRISMARINE);
            set(level, c.offset(k[0], 3, k[1]), Blocks.SEA_LANTERN);
        }
        chest(level, c.above(), shrineLoot(Line.POSEIDON, Story.Chapter.SEA, random));
        return c;
    }

    /** Shrine of the Shades: basalt standing stones round a pit, steps going down, the chest at the bottom. */
    private static BlockPos buildShade(ServerLevel level, Site site, Random random) {
        BlockPos c = prepare(level, site, Blocks.POLISHED_BLACKSTONE_BRICKS);
        int r = site.kind.radius, depth = 4;
        // the crypt: a walled box under the floor, open at the top
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int dy = 0; dy >= -depth - 1; dy--) {
                    set(level, c.offset(dx, dy, dz), wall || dy == -depth - 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR);
                }
            }
        }
        // steps down the west wall, one block at a time
        for (int i = 0; i < depth; i++) {
            for (int dy = -depth; dy <= -1 - i; dy++) set(level, c.offset(-2, dy, -2 + i), Blocks.POLISHED_BLACKSTONE);
        }
        set(level, c.offset(2, -depth, 2), Blocks.SOUL_SOIL);
        set(level, c.offset(2, -depth + 1, 2), Blocks.SOUL_LANTERN);
        set(level, c.offset(2, -depth, -2), Blocks.SOUL_SOIL);
        set(level, c.offset(2, -depth + 1, -2), Blocks.SOUL_LANTERN);
        chest(level, c.offset(1, -depth, 0), shrineLoot(Line.HADES, Story.Chapter.SHADE, random));
        // standing stones mark the place from above
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            int sx = (int) Math.round(Math.cos(a) * (r - 0.5)), sz = (int) Math.round(Math.sin(a) * (r - 0.5));
            int height = 2 + random.nextInt(3);
            for (int dy = 1; dy <= height; dy++) set(level, c.offset(sx, dy, sz), Blocks.POLISHED_BASALT);
            if (i % 2 == 0) set(level, c.offset(sx, height + 1, sz), Blocks.SOUL_LANTERN);
        }
        return c;
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

    /**
     * Right-clicking the pedestal. With a keystone in hand it is set in its socket; otherwise the pedestal says what
     * the gate still wants. @return true if a keystone was set.
     */
    public static boolean usePedestal(ServerLevel level, BlockPos pedestal, ServerPlayer player, ItemStack held) {
        if (!hasArch(level, pedestal)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "pedestal.nogate"), 0xFF5555));
            return false;
        }
        Line offered = null;
        for (Line line : Line.values()) if (held.is(ModContent.keystoneItem(line))) offered = line;
        if (offered != null && !isSet(level, pedestal, offered)) {
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            BlockPos socket = socket(pedestal, offered);
            set(level, socket, socketFill(offered));
            level.sendParticles(Signature.spark(offered), socket.getX() + 0.5, socket.getY() + 0.5, socket.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, socket, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.2F, 0.8F + 0.2F * keystonesSet(level, pedestal));
            int set = keystonesSet(level, pedestal);
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "pedestal.set", new ItemStack(ModContent.keystoneItem(offered)).getHoverName(), set), offered.color()));
            if (set == Line.values().length) wake(level, pedestal);
            return true;
        }
        if (offered != null) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "pedestal.already"), 0xAAAAAA));
            return false;
        }
        if (keystonesSet(level, pedestal) == Line.values().length) {
            if (!awake(level, pedestal)) wake(level, pedestal);
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "pedestal.awake"), 0x55FF55));
            return false;
        }
        net.minecraft.network.chat.MutableComponent missing = Component.empty();
        boolean first = true;
        for (Line line : Line.values()) {
            if (isSet(level, pedestal, line)) continue;
            if (!first) missing.append(Component.literal(", "));
            missing.append(new ItemStack(ModContent.keystoneItem(line)).getHoverName());
            first = false;
        }
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "pedestal.need", missing), 0xFFFF55));
        return false;
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
        level.sendParticles(ParticleTypes.PORTAL, mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 200, 1.0, 1.5, 0.3, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, mid.getX() + 0.5, mid.getY() + 0.5, mid.getZ() + 0.5, 60, 1.0, 1.5, 0.3, 0.1);
        level.playSound(null, mid, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.2F);
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
        if (dist < 12) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "point.here", kind.displayName()), color));
        } else {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "point", kind.displayName(), (int) Math.round(dist),
                    Component.translatable(directionKey(dx, dz))), color));
            for (int i = 1; i <= 10; i++) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX() + dx / dist * i * 0.6, player.getY() + 1.2, player.getZ() + dz / dist * i * 0.6, 1, 0, 0, 0, 0);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.6F, 1.2F);
        return site;
    }
}
