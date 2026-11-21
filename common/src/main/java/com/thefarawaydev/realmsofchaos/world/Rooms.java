package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.RoomBlock;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The rooms of a passage that are more than walls and sentinels.
 *
 * <p><b>Puzzle rooms</b> keep a cache locked until they are solved, for everyone at once:
 * <ul>
 * <li>a <b>vault</b> - nine glyph tiles in the floor; pressing one turns it and its neighbours; all nine must be lit;
 * <li>a <b>chime room</b> - four crystals, all to be rung before the first one falls silent;
 * <li>a <b>beam room</b> - a beam of light, two mirrors to turn, and a receiver it has to reach;
 * <li>a <b>trembling floor</b> - a ring of plates of which only one path holds; it shows itself every few seconds,
 *     and has to be walked in order.
 * </ul>
 * <b>Trap rooms</b> are simply to be got through: a <b>crusher hall</b> where the ceiling comes down row by row, a
 * breath after the row is marked, and a <b>dart hall</b> whose marked tiles loose arrows from the walls.
 *
 * <p>And the small things: <b>urns</b> to smash - for a little loot, for nothing, or for the sentinel that was
 * hiding in one - and <b>lore tablets</b> that tell a line of the tower's story.
 *
 * <p>What a puzzle looks like is kept in its blocks and whether it is solved in the world's saved data, so nothing
 * here is lost by a server stop; only the clocks (a chime's time, a crusher's rhythm) live in memory.
 */
public final class Rooms {
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".room.";
    private static final int[][] CORNERS = {{-3, -3}, {3, -3}, {3, 3}, {-3, 3}};
    /** How long before a marked row comes down, and how often a new row is marked. */
    public static final int CRUSH_WARNING = 20, CRUSH_EVERY = 50;
    public static final int DART_REST = 40, LORE_LINES = 8;

    private Rooms() {}

    // ---- shared ------------------------------------------------------------------------------------------------

    public static boolean isPuzzle(Passages.Kind kind) {
        return kind == Passages.Kind.VAULT || kind == Passages.Kind.CHIMES || kind == Passages.Kind.BEAM || kind == Passages.Kind.PLATES;
    }

    private static String key(Passages.Plan plan, Passages.Cell cell) {
        return plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + cell.x() + "/" + cell.z();
    }

    private static String flag(Passages.Plan plan, Passages.Cell cell) {
        return "puzzle/" + key(plan, cell);
    }

    public static boolean solved(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        return ProgressData.get(level.getServer()).isBuilt(flag(plan, cell));
    }

    private static List<ServerPlayer> inside(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        return level.getEntitiesOfClass(ServerPlayer.class, plan.cellBox(cell), p -> p.isAlive() && !p.isSpectator());
    }

    /** The room a block belongs to, if it is of the kind asked for. */
    private record Place(Passages.Plan plan, Passages.Cell cell) {}

    private static Place place(ServerLevel level, BlockPos pos, Passages.Kind kind) {
        Passages.Plan plan = Passages.planAt(level, pos);
        Passages.Cell cell = plan == null ? null : plan.cellAt(pos);
        return cell != null && plan.kind(cell) == kind ? new Place(plan, cell) : null;
    }

    private static void light(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RoomBlock && state.getValue(RoomBlock.LIT) != lit) level.setBlock(pos, state.setValue(RoomBlock.LIT, lit), 3);
    }

    private static boolean lit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof RoomBlock && state.getValue(RoomBlock.LIT);
    }

    private static void solve(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        ProgressData.get(level.getServer()).markBuilt(flag(plan, cell));
        BlockPos m = plan.centre(cell);
        level.playSound(null, m, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.3F);
        level.sendParticles(ParticleTypes.END_ROD, m.getX() + 0.5, m.getY() + 2.0, m.getZ() + 0.5, 80, 2.5, 1.0, 2.5, 0.05);
        for (ServerPlayer p : inside(level, plan, cell)) {
            Hud.title(p, Hud.colored(Component.translatable(HUD + "solved"), 0x55FF55), Hud.colored(Component.translatable(HUD + "solved.sub"), 0xCCCCCC), 5, 50, 15);
        }
    }

    private static void hint(ServerPlayer player, String key, int color, Object... args) {
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + key, args), color));
    }

    /** Where the cache of a puzzle room stands: in the middle, except in a vault, whose middle is the puzzle. */
    public static BlockPos cache(Passages.Plan plan, Passages.Cell cell) {
        return plan.kind(cell) == Passages.Kind.VAULT ? plan.centre(cell).offset(3, 1, 3) : plan.centre(cell).above();
    }

    /** Furnishes one of this class's rooms. Called while the level is being built. */
    public static void build(ServerLevel level, Passages.Plan plan, Passages.Cell cell, Random random) {
        Passages.Kind kind = plan.kind(cell);
        if (isPuzzle(kind)) level.setBlock(cache(plan, cell), ModContent.PASSAGE_CACHE.get().defaultBlockState(), 2);
        switch (kind) {
            case VAULT -> buildVault(level, plan, cell, random);
            case CHIMES -> {
                for (int n = 0; n < 4; n++) level.setBlock(chime(plan, cell, n), ModContent.CHIME_CRYSTAL.get().defaultBlockState(), 2);
            }
            case BEAM -> buildBeam(level, plan, cell, random);
            case PLATES -> {
                for (BlockPos plate : plates(plan, cell)) level.setBlock(plate, ModContent.MEMORY_PLATE.get().defaultBlockState(), 2);
            }
            case DARTS -> {
                BlockPos m = plan.centre(cell);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (random.nextFloat() < 0.38F) level.setBlock(m.offset(dx, 0, dz), ModContent.DART_TILE.get().defaultBlockState(), 2);
                    }
                }
            }
            default -> { }
        }
    }

    /** A passage sealing itself again locks its puzzles again too, and sets them differently. */
    public static void rearm(ServerLevel level, Passages.Plan plan) {
        ProgressData data = ProgressData.get(level.getServer());
        Random random = new Random(plan.seed ^ level.getGameTime());
        for (Passages.Cell cell : plan.cells()) {
            Passages.Kind kind = plan.kind(cell);
            if (!isPuzzle(kind)) continue;
            data.unmarkBuilt(flag(plan, cell));
            switch (kind) {
                case VAULT -> buildVault(level, plan, cell, random);
                case CHIMES -> {
                    for (int n = 0; n < 4; n++) light(level, chime(plan, cell, n), false);
                }
                case BEAM -> buildBeam(level, plan, cell, random);
                default -> {
                    for (BlockPos plate : plates(plan, cell)) light(level, plate, false);
                }
            }
        }
    }

    // ---- the vault: lights out ---------------------------------------------------------------------------------

    public static BlockPos glyph(Passages.Plan plan, Passages.Cell cell, int i, int j) {
        return plan.centre(cell).offset(i - 1, 0, j - 1);
    }

    private static void toggle(ServerLevel level, Passages.Plan plan, Passages.Cell cell, int i, int j) {
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x = i + d[0], z = j + d[1];
            if (x < 0 || z < 0 || x > 2 || z > 2) continue;
            BlockPos at = glyph(plan, cell, x, z);
            light(level, at, !lit(level, at));
        }
    }

    public static boolean allLit(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) if (!lit(level, glyph(plan, cell, i, j))) return false;
        return true;
    }

    /** Lays the nine tiles lit, then presses a few at random: so there is always a way back to all lit. */
    private static void buildVault(ServerLevel level, Passages.Plan plan, Passages.Cell cell, Random random) {
        BlockState tile = ModContent.GLYPH_TILE.get().defaultBlockState().setValue(RoomBlock.LIT, true);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) level.setBlock(glyph(plan, cell, i, j), tile, 2);
        for (int n = 0; n < 2 + plan.tier; n++) toggle(level, plan, cell, random.nextInt(3), random.nextInt(3));
        if (allLit(level, plan, cell)) toggle(level, plan, cell, 1, 1);
    }

    /** Presses a glyph tile: it and the tiles beside it change. @return true if that solved the vault. */
    public static boolean pressGlyph(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Place at = place(level, pos, Passages.Kind.VAULT);
        if (at == null) return false;
        if (solved(level, at.plan, at.cell)) {
            hint(player, "already", 0xAAAAAA);
            return false;
        }
        BlockPos m = at.plan.centre(at.cell);
        int i = pos.getX() - m.getX() + 1, j = pos.getZ() - m.getZ() + 1;
        if (i < 0 || j < 0 || i > 2 || j > 2 || pos.getY() != m.getY()) return false;
        toggle(level, at.plan, at.cell, i, j);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.8F + 0.1F * (i + j));
        if (!allLit(level, at.plan, at.cell)) return false;
        solve(level, at.plan, at.cell);
        return true;
    }

    // ---- the chime room: four crystals against the clock ----------------------------------------------------------

    private static final Map<String, Long> CHIME_UNTIL = new HashMap<>();

    public static BlockPos chime(Passages.Plan plan, Passages.Cell cell, int n) {
        return plan.centre(cell).offset(CORNERS[n][0], 1, CORNERS[n][1]);
    }

    /** How long the crystals keep ringing once the first is touched: eleven seconds low in a tower, seven at the top. */
    public static int chimeTicks(Passages.Plan plan) {
        return 20 * (13 - 2 * Math.max(1, Math.min(3, plan.tier)));
    }

    /** Rings a crystal. The first starts the clock; all four inside it solve the room. @return true if that solved it. */
    public static boolean pressChime(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Place at = place(level, pos, Passages.Kind.CHIMES);
        if (at == null) return false;
        if (solved(level, at.plan, at.cell)) {
            hint(player, "already", 0xAAAAAA);
            return false;
        }
        String key = key(at.plan, at.cell);
        long now = level.getServer().getTickCount();
        Long until = CHIME_UNTIL.get(key);
        if (until == null || now >= until || until - now > 20 * 60) {
            // a new attempt: whatever was still ringing is quiet first
            for (int n = 0; n < 4; n++) light(level, chime(at.plan, at.cell, n), false);
            CHIME_UNTIL.put(key, now + chimeTicks(at.plan));
            hint(player, "chimes.start", 0x55FFFF, chimeTicks(at.plan) / 20);
        }
        light(level, pos, true);
        int ringing = 0;
        for (int n = 0; n < 4; n++) {
            if (lit(level, chime(at.plan, at.cell, n))) ringing++;
            if (chime(at.plan, at.cell, n).equals(pos)) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0F, 0.6F + 0.25F * n);
        }
        if (ringing < 4) return false;
        CHIME_UNTIL.remove(key);
        solve(level, at.plan, at.cell);
        return true;
    }

    // ---- the beam room: two mirrors --------------------------------------------------------------------------------

    /** Where the pieces of a beam room stand. The beam leaves the emitter eastwards. */
    public record Beam(BlockPos emitter, BlockPos first, BlockPos second, BlockPos receiver, boolean firstTurned, boolean secondTurned) {}

    public record Trace(List<BlockPos> path, boolean reached) {}

    public static Beam beam(Passages.Plan plan, Passages.Cell cell) {
        Random random = new Random(plan.seed ^ (cell.x() * 911L + cell.z() * 359L) ^ 0xBEA3L);
        int a = (random.nextBoolean() ? 1 : -1) * (2 + random.nextInt(2));
        BlockPos m = plan.centre(cell);
        // the beam runs east to the first mirror, along the room to the second, and back west to the receiver
        return new Beam(m.offset(-3, 1, a), m.offset(2, 1, a), m.offset(2, 1, -a), m.offset(-3, 1, -a), a > 0, a < 0);
    }

    private static void buildBeam(ServerLevel level, Passages.Plan plan, Passages.Cell cell, Random random) {
        Beam beam = beam(plan, cell);
        level.setBlock(beam.emitter, ModContent.BEAM_EMITTER.get().defaultBlockState(), 2);
        level.setBlock(beam.receiver, ModContent.BEAM_RECEIVER.get().defaultBlockState(), 2);
        BlockState mirror = ModContent.BEAM_MIRROR.get().defaultBlockState();
        // the first mirror always starts wrong, the second as chance has it: never solved to begin with
        level.setBlock(beam.first, mirror.setValue(RoomBlock.LIT, !beam.firstTurned), 2);
        level.setBlock(beam.second, mirror.setValue(RoomBlock.LIT, random.nextBoolean()), 2);
    }

    /** Follows the beam from the emitter: straight through air, turned by mirrors, stopped by anything else. */
    public static Trace trace(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        Beam beam = beam(plan, cell);
        BlockPos m = plan.centre(cell);
        List<BlockPos> path = new ArrayList<>();
        BlockPos at = beam.emitter;
        int dx = 1, dz = 0;
        for (int step = 0; step < 48; step++) {
            at = at.offset(dx, 0, dz);
            if (Math.abs(at.getX() - m.getX()) > 4 || Math.abs(at.getZ() - m.getZ()) > 4) break;
            BlockState state = level.getBlockState(at);
            if (state.is(ModContent.BEAM_RECEIVER.get())) return new Trace(path, true);
            if (state.is(ModContent.BEAM_MIRROR.get())) {
                // a mirror one way sends east to south; turned, east to north
                int ox = dx;
                if (state.getValue(RoomBlock.LIT)) {
                    dx = -dz;
                    dz = -ox;
                } else {
                    dx = dz;
                    dz = ox;
                }
                path.add(at);
                continue;
            }
            if (!state.isAir()) break;
            path.add(at);
        }
        return new Trace(path, false);
    }

    /** Turns a mirror. @return true if the beam now reaches the receiver for the first time. */
    public static boolean turnMirror(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Place at = place(level, pos, Passages.Kind.BEAM);
        if (at == null) return false;
        if (solved(level, at.plan, at.cell)) {
            hint(player, "already", 0xAAAAAA);
            return false;
        }
        light(level, pos, !lit(level, pos));
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8F, 1.4F);
        Trace trace = trace(level, at.plan, at.cell);
        draw(level, trace);
        if (!trace.reached) return false;
        light(level, beam(at.plan, at.cell).receiver, true);
        solve(level, at.plan, at.cell);
        return true;
    }

    private static void draw(ServerLevel level, Trace trace) {
        for (BlockPos p : trace.path) level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.0);
    }

    // ---- the trembling floor: one path holds ---------------------------------------------------------------------

    private static final Map<String, Integer> PROGRESS = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_PLATE = new HashMap<>();
    private static final Map<UUID, Long> LAST_STOOD = new HashMap<>();

    /** Every plate of the floor: the two rings round the cache. */
    public static List<BlockPos> plates(Passages.Plan plan, Passages.Cell cell) {
        BlockPos m = plan.centre(cell);
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (dx != 0 || dz != 0) out.add(m.offset(dx, 0, dz));
        return out;
    }

    /** The plates that hold, in the order they have to be walked: in from the outer ring to one beside the cache. */
    public static List<BlockPos> path(Passages.Plan plan, Passages.Cell cell) {
        Random random = new Random(plan.seed ^ (cell.x() * 577L + cell.z() * 193L) ^ 0x9A7EL);
        List<int[]> steps = new ArrayList<>();
        int t = random.nextInt(5) - 2;
        steps.add(new int[]{t, -2});
        while (Math.abs(t) > 1) {
            t -= Integer.signum(t);
            steps.add(new int[]{t, -2});
        }
        if (t == 0 && random.nextBoolean()) {
            t = random.nextBoolean() ? 1 : -1;
            steps.add(new int[]{t, -2});
        }
        steps.add(new int[]{t, -1});
        if (random.nextBoolean()) {
            int side = t != 0 ? 0 : random.nextBoolean() ? 1 : -1;
            if (side != t) steps.add(new int[]{side, -1});
        }
        int turn = random.nextInt(4);
        BlockPos m = plan.centre(cell);
        List<BlockPos> out = new ArrayList<>();
        for (int[] s : steps) {
            int x = s[0], z = s[1];
            for (int i = 0; i < turn; i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            out.add(m.offset(x, 0, z));
        }
        return out;
    }

    /**
     * A player has stepped onto a plate. The path has to be walked in order from its first plate: a plate out of
     * turn, or one that is not on the path at all, throws the walker off and they begin again.
     * @return true if that step solved the room
     */
    public static boolean stepPlate(ServerLevel level, BlockPos pos, ServerPlayer player) {
        // (a plate is stepped on once however long it is stood on: only coming back to it after leaving counts again)
        long now = level.getServer().getTickCount();
        Long stood = LAST_STOOD.put(player.getUUID(), now);
        boolean same = pos.equals(LAST_PLATE.put(player.getUUID(), pos.immutable())) && stood != null && now - stood >= 0 && now - stood <= 3;
        if (same) return false;
        Place at = place(level, pos, Passages.Kind.PLATES);
        if (at == null || solved(level, at.plan, at.cell) || player.isCreative()) return false;
        List<BlockPos> path = path(at.plan, at.cell);
        String key = player.getUUID() + "/" + key(at.plan, at.cell);
        int reached = PROGRESS.getOrDefault(key, 0), index = path.indexOf(pos);
        if (index >= 0 && index <= reached) {
            if (index == reached) PROGRESS.put(key, reached + 1);
            light(level, pos, true);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2F, 0.7F + 0.15F * index);
            if (index < path.size() - 1) return false;
            PROGRESS.remove(key);
            solve(level, at.plan, at.cell);
            return true;
        }
        // the floor gives: thrown back off it, and the path goes dark
        PROGRESS.remove(key);
        for (BlockPos plate : path) light(level, plate, false);
        BlockPos m = at.plan.centre(at.cell);
        Vec3 away = new Vec3(player.getX() - (m.getX() + 0.5), 0, player.getZ() - (m.getZ() + 0.5));
        double d = Math.max(0.01, away.length());
        player.hurtServer(level, level.damageSources().magic(), 2.0F + at.plan.tier);
        player.setDeltaMovement(away.x / d * 0.9, 0.45, away.z / d * 0.9);
        player.needsSync = true;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 2.0F, 0.5F);
        level.sendParticles(Signature.spark(at.plan.line), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.1, 0.3, 0.1);
        hint(player, "plates.wrong", 0xFF5555);
        return false;
    }

    // ---- the dart hall ---------------------------------------------------------------------------------------------

    private static final Map<BlockPos, Long> DART_READY = new HashMap<>();

    /** A marked tile has been stood on: two arrows come out of the walls at whoever is on it. @return how many were loosed. */
    public static int stepDart(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (player.isCreative()) return 0;
        long now = level.getServer().getTickCount();
        Long ready = DART_READY.get(pos);
        if (ready != null && now < ready && ready - now <= DART_REST) return 0;
        Place at = place(level, pos, Passages.Kind.DARTS);
        if (at == null) return 0;
        if (DART_READY.size() > 512) DART_READY.clear();
        DART_READY.put(pos.immutable(), now + DART_REST);
        BlockPos m = at.plan.centre(at.cell);
        int loosed = 0;
        for (int side = -1; side <= 1; side += 2) {
            // out of the east and west walls, level with whoever trod on the tile
            Vec3 from = new Vec3(m.getX() + 0.5 + side * 4.3, m.getY() + 2.2, player.getZ());
            Arrow dart = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            dart.pickup = AbstractArrow.Pickup.DISALLOWED;
            dart.setBaseDamage(1.5 + 0.5 * at.plan.tier);
            dart.shoot(player.getX() - from.x, player.getY(0.6) - from.y, player.getZ() - from.z, 1.4F, 2.0F);
            level.addFreshEntity(dart);
            loosed++;
        }
        level.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.2F, 1.3F);
        level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.8F);
        return loosed;
    }

    // ---- urns and tablets ------------------------------------------------------------------------------------------

    public enum Urn { LOOT, EMPTY, AMBUSH }

    /** What an urn holds is fixed by where it stands: most a little loot, some nothing, a few a sentinel. */
    public static Urn urn(long seed, BlockPos pos) {
        int roll = Math.floorMod(new Random(seed ^ pos.asLong()).nextInt(), 100);
        return roll < 60 ? Urn.LOOT : roll < 82 ? Urn.EMPTY : Urn.AMBUSH;
    }

    /** Smashes an urn. @return what was in it. */
    public static Urn smashUrn(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!level.getBlockState(pos).is(ModContent.ANCIENT_URN.get())) return Urn.EMPTY;
        BlockState was = level.getBlockState(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.2F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.25, 0.3, 0.25, 0.1);
        Urn urn = urn(level.getSeed(), pos);
        Passages.Plan plan = Passages.planAt(level, pos);
        int tier = plan == null ? 1 : plan.tier;
        Random random = new Random(level.getSeed() ^ pos.asLong() ^ 77L);
        if (urn == Urn.LOOT) {
            List<ItemStack> loot = new ArrayList<>();
            loot.add(switch (random.nextInt(4)) {
                case 0 -> new ItemStack(Items.ARROW, 4 + random.nextInt(6));
                case 1 -> new ItemStack(Items.BREAD, 1 + random.nextInt(3));
                case 2 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + random.nextInt(2));
                default -> new ItemStack(Items.GOLD_NUGGET, 3 + random.nextInt(6));
            });
            if (plan != null && random.nextFloat() < 0.15F + 0.05F * Skills.rank(player, Skills.Skill.SCAVENGER)) loot.add(new ItemStack(ModContent.materialItem(plan.line, tier)));
            for (ItemStack stack : loot) {
                ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, stack);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        } else if (urn == Urn.AMBUSH && plan != null) {
            Sentinel hidden = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
            if (hidden != null) {
                hidden.setUp(Sentinel.Kind.of(plan.line, Sentinel.Role.SKIRMISHER), tier, plan.threat, false);
                hidden.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
                hidden.addTag(Passages.GUARDIAN_TAG);
                hidden.setPersistenceRequired();
                hidden.setTarget(player);
                level.addFreshEntity(hidden);
                hint(player, "urn.ambush", 0xFF5555);
            }
        }
        return urn;
    }

    /** Reads a tablet: a line of the tower's story, and - the first time this player reads this one - a little experience. */
    public static int readTablet(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Realm realm = Realm.of(level.dimension());
        String tower = realm == null || realm.line() == null ? "zeus" : realm.line().id();
        int line = 1 + Math.floorMod(new Random(level.getSeed() ^ pos.asLong()).nextInt(), LORE_LINES);
        player.sendSystemMessage(Hud.colored(Component.translatable("lore." + RealmsOfChaos.MOD_ID + "." + tower + "." + line), 0xF5B82E));
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        ProgressData data = ProgressData.get(level.getServer());
        String read = "lore/" + player.getUUID() + "/" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        if (!data.isBuilt(read)) {
            data.markBuilt(read);
            player.giveExperiencePoints(12);
        }
        return line;
    }

    // ---- rooms with a clock: the crusher, the beam's light, the path's glimmer ---------------------------------------

    private static final class Active {
        final ServerLevel level;
        final Passages.Plan plan;
        final Passages.Cell cell;
        long seen;
        int ticks, row = -5, step = 1;

        Active(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
            this.level = level;
            this.plan = plan;
            this.cell = cell;
        }
    }

    private static final Map<String, Active> ACTIVE = new HashMap<>();

    /** Once a second for a player standing in one of these rooms: keeps its clock running while anyone is there. */
    public static void service(ServerPlayer player, ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        Passages.Kind kind = plan.kind(cell);
        if (kind != Passages.Kind.CRUSHER && kind != Passages.Kind.BEAM && kind != Passages.Kind.PLATES && kind != Passages.Kind.CHIMES) return;
        Active active = ACTIVE.computeIfAbsent(key(plan, cell), k -> new Active(level, plan, cell));
        active.seen = level.getServer().getTickCount();
    }

    /** The row a crusher hall has marked, as an offset from the middle of the room (-4..4), or -5 before the first. */
    public static int crusherRow(Passages.Plan plan, Passages.Cell cell) {
        Active active = ACTIVE.get(key(plan, cell));
        return active == null ? -5 : active.row;
    }

    /** Marks the next row of a crusher hall. */
    public static int markRow(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        Active active = ACTIVE.computeIfAbsent(key(plan, cell), k -> new Active(level, plan, cell));
        active.seen = level.getServer().getTickCount();
        if (active.row <= -5) active.row = -4;
        else {
            if (active.row + active.step > 4 || active.row + active.step < -4) active.step = -active.step;
            active.row += active.step;
        }
        BlockPos m = plan.centre(cell);
        for (int dx = -4; dx <= 4; dx++) {
            level.sendParticles(ParticleTypes.FLAME, m.getX() + dx + 0.5, m.getY() + 1.1, m.getZ() + active.row + 0.5, 3, 0.2, 0.02, 0.2, 0.0);
            level.sendParticles(ParticleTypes.CRIT, m.getX() + dx + 0.5, m.getY() + Passages.HEADROOM, m.getZ() + active.row + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
        }
        level.playSound(null, m.offset(0, 1, active.row), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5F, 0.5F);
        return active.row;
    }

    /** The marked row comes down. @return how many players it caught. */
    public static int slam(ServerLevel level, Passages.Plan plan, Passages.Cell cell) {
        Active active = ACTIVE.get(key(plan, cell));
        if (active == null || active.row <= -5) return 0;
        BlockPos m = plan.centre(cell);
        double rowZ = m.getZ() + active.row + 0.5;
        Block stone = plan.palette().pillar();
        for (int dx = -4; dx <= 4; dx += 2) {
            // the stone itself is seen coming - and is gone when it lands
            BlockPos from = m.offset(dx, Passages.HEADROOM, active.row);
            if (!level.getBlockState(from).isAir()) continue;
            FallingBlockEntity block = FallingBlockEntity.fall(level, from, stone.defaultBlockState());
            block.dropItem = false;
            block.disableDrop();
            block.setDeltaMovement(0, -1.2, 0);
            block.needsSync = true;
        }
        Fx.impact(level, new Vec3(m.getX() + 0.5, m.getY() + 1, rowZ), 4);
        level.playSound(null, m.offset(0, 1, active.row), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2F, 0.6F);
        int caught = 0;
        for (ServerPlayer p : inside(level, plan, cell)) {
            if (p.isCreative() || Math.abs(p.getZ() - rowZ) > 0.8) continue;
            p.hurtServer(level, level.damageSources().fallingBlock(p), 4.0F + 1.5F * plan.tier);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 2));
            caught++;
        }
        return caught;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (!CHIME_UNTIL.isEmpty()) {
            for (Iterator<Map.Entry<String, Long>> it = CHIME_UNTIL.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> e = it.next();
                if (now < e.getValue()) continue;
                // too slow: the crystals fall silent (they are put out by whoever touches one next, or here if the room is watched)
                Active active = ACTIVE.get(e.getKey());
                if (active != null && !solved(active.level, active.plan, active.cell)) {
                    for (int n = 0; n < 4; n++) light(active.level, chime(active.plan, active.cell, n), false);
                    for (ServerPlayer p : inside(active.level, active.plan, active.cell)) hint(p, "chimes.slow", 0xFF5555);
                }
                it.remove();
            }
        }
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Active> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (now - a.seen > 60 || now < a.seen) {
                it.remove();
                continue;
            }
            a.ticks++;
            switch (a.plan.kind(a.cell)) {
                case CRUSHER -> {
                    if (a.ticks % CRUSH_EVERY == 1) markRow(a.level, a.plan, a.cell);
                    if (a.ticks % CRUSH_EVERY == 1 + CRUSH_WARNING) slam(a.level, a.plan, a.cell);
                }
                case BEAM -> {
                    if (a.ticks % 10 == 0) draw(a.level, trace(a.level, a.plan, a.cell));
                }
                case PLATES -> {
                    // every six seconds the path shows itself for a second and a half
                    if (a.ticks % 120 < 30 && a.ticks % 5 == 0 && !solved(a.level, a.plan, a.cell)) {
                        for (BlockPos p : path(a.plan, a.cell)) a.level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.15, p.getZ() + 0.5, 2, 0.2, 0.02, 0.2, 0.0);
                    }
                }
                default -> { }
            }
        }
    }

    /** Test seam: forgets every clock and every walker's progress. */
    public static void forget() {
        ACTIVE.clear();
        CHIME_UNTIL.clear();
        PROGRESS.clear();
        LAST_PLATE.clear();
        LAST_STOOD.clear();
        DART_READY.clear();
    }
}
