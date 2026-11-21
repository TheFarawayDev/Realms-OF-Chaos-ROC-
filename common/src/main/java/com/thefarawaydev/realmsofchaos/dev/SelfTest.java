package com.thefarawaydev.realmsofchaos.dev;

import com.mojang.authlib.GameProfile;
import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.AltarBlock;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.entity.RelicProjectile;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.god.WeaponKind;
import com.thefarawaydev.realmsofchaos.item.GodWeaponItem;
import com.thefarawaydev.realmsofchaos.item.Tarot;
import com.thefarawaydev.realmsofchaos.item.TarotDeckItem;
import com.thefarawaydev.realmsofchaos.item.TarotEffects;
import com.thefarawaydev.realmsofchaos.world.Hermitage;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import com.thefarawaydev.realmsofchaos.world.Passages;
import com.thefarawaydev.realmsofchaos.world.Ruins;
import com.thefarawaydev.realmsofchaos.world.Parties;
import com.thefarawaydev.realmsofchaos.world.Fx;
import com.thefarawaydev.realmsofchaos.ui.Screens;
import com.thefarawaydev.realmsofchaos.ui.Tutorial;
import com.thefarawaydev.realmsofchaos.ui.HandbookData;
import com.thefarawaydev.realmsofchaos.world.Adapt;
import com.thefarawaydev.realmsofchaos.world.Wards;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.world.Access;
import com.thefarawaydev.realmsofchaos.world.HubLife;
import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.ui.StoryMenu;
import com.thefarawaydev.realmsofchaos.ui.StrikeBar;
import com.thefarawaydev.realmsofchaos.ui.SealMenu;
import com.thefarawaydev.realmsofchaos.block.SealBlock;
import com.thefarawaydev.realmsofchaos.block.ShrineBlock;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.item.VoodooDollItem;
import com.thefarawaydev.realmsofchaos.item.WeaponProgress;
import com.thefarawaydev.realmsofchaos.ui.DeckMenu;
import com.thefarawaydev.realmsofchaos.ui.ForgeMenu;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import com.thefarawaydev.realmsofchaos.ui.AltarMenu;
import com.thefarawaydev.realmsofchaos.ui.CodexMenu;
import com.thefarawaydev.realmsofchaos.world.FloorShifts;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.RealmBuilder;
import com.thefarawaydev.realmsofchaos.world.RealmSafety;
import com.thefarawaydev.realmsofchaos.world.RocAdvancements;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import com.thefarawaydev.realmsofchaos.world.Tower;
import com.thefarawaydev.realmsofchaos.world.TowerBuilder;
import com.thefarawaydev.realmsofchaos.world.Welcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Developer self-test. It only runs when the JVM is started with {@code -Droc.selftest=true} (the loaders hook it to
 * "server started"), and exercises the mod against a real running server: data loading, the hub, every floor of
 * every tower, a full climb of all three towers through the altar menu, weapon powers, the Codex, leaving the
 * realms, the first-join welcome and the anti-stuck safeguards. Results go to the log and to
 * {@code roc-selftest.txt}. It is inert in normal play.
 */
public final class SelfTest {
    private static final List<String> RESULTS = new ArrayList<>();
    private static int failures;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("roc.selftest");
    }

    private interface Step { void run() throws Exception; }

    private static void step(String name, Step step) {
        try {
            step.run();
            RESULTS.add("PASS  " + name);
        } catch (Throwable t) {
            failures++;
            RESULTS.add("FAIL  " + name + " -> " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(8, st.length); i++) RESULTS.add("        at " + st[i]);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }

    private static ServerPlayer fakePlayer(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(GameType.SURVIVAL);
        // a real client reports "loaded" after joining; until then vanilla treats the player as invulnerable
        try {
            var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            loaded.setAccessible(true);
            loaded.invoke(player.connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }

    private static boolean solid(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** The height to stand at on this tower floor at x/z: floors are not flat, so tests must find the ground. */
    private static double ground(ServerLevel level, int floor, double x, double z) {
        int y0 = Tower.floorY(floor);
        for (int y = y0 + 4; y >= y0 - Tower.DEPTH; y--) {
            if (solid(level, BlockPos.containing(x, y, z))) return y + 1;
        }
        return y0 + 1;
    }

    /** Winds the clock on until the hammer of a strike bar is where a strike of the wanted quality needs it. */
    private static void waitFor(MinecraftServer server, StrikeBar bar, StrikeBar.Hit wanted) throws Exception {
        for (int i = 0; bar.hit(server.getTickCount()) != wanted; i++) {
            check(i < 400, "the hammer never gave a " + wanted);
            advance(server, 1);
        }
    }

    /** Strikes a forging round that is in progress, one strike of each given quality. */
    private static void strike(MinecraftServer server, ForgeMenu forge, ServerPlayer hero, StrikeBar.Hit... wanted) throws Exception {
        for (StrikeBar.Hit hit : wanted) {
            check(forge.strikeBar() != null, "a round is being struck");
            waitFor(server, forge.strikeBar(), hit);
            forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
        }
    }

    /** Presses the forge button and strikes the star with three good strikes: fine work, no temper. */
    private static void forgeFine(MinecraftServer server, ForgeMenu forge, ServerPlayer hero) throws Exception {
        forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
        strike(server, forge, hero, StrikeBar.Hit.GOOD, StrikeBar.Hit.GOOD, StrikeBar.Hit.GOOD);
    }

    /**
     * Nothing ticks while the self-test runs, so chunks the test has only just loaded may not have told the entity
     * tracker about themselves yet, and mobs in them would be invisible to lookups. This runs that pending work.
     */
    private static void settle(ServerLevel level) {
        for (int i = 0; i < 5000 && level.getChunkSource().pollTask(); i++) { }
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /**
     * Checks one built passage: the plan is sound, everything it promises is there, and - walking only where a
     * player fits - every seal, the chest, the shrine and the gate can be reached from the arrival spot without
     * ever being able to step out of the passage.
     */
    private static void checkPassage(ServerLevel level, Passages.Plan plan, String at) {
        check(plan.seals.size() == plan.tier + 1, at + "seals planned: " + plan.seals.size());
        check(!plan.start.equals(plan.exit), at + "the way in is not the way out");
        var dist = plan.distances();
        check(dist.size() == plan.size * plan.size, at + "every room can be reached through doorways: " + dist.size());
        check(dist.values().stream().allMatch(d -> d <= dist.get(plan.exit)), at + "the gate is in the farthest room");
        int treasure = 0, shrines = 0, arenas = 0;
        for (Passages.Cell c : plan.cells()) {
            Passages.Kind kind = plan.kind(c);
            check(kind != null, at + "room without a kind");
            if (kind == Passages.Kind.ARENA) {
                arenas++;
                check(level.getBlockState(plan.featurePos(c)).is(ModContent.PASSAGE_CACHE.get()), at + "the arena's cache");
            }
            if (kind == Passages.Kind.TREASURE) {
                treasure++;
                check(level.getBlockState(plan.featurePos(c)).is(ModContent.PASSAGE_CACHE.get()), at + "treasure cache");
                check(Passages.lootFor(plan, c, UUID.randomUUID()).stream().anyMatch(st -> st.is(ModContent.materialItem(plan.line, plan.tier))),
                        at + "every share of the treasure holds this tier's forge material");
            }
            if (kind == Passages.Kind.SHRINE) {
                shrines++;
                check(level.getBlockState(plan.featurePos(c)).is(ModContent.PASSAGE_SHRINE.get()), at + "shrine");
            }
            if (kind == Passages.Kind.SEAL) check(level.getBlockState(plan.sealPos(c)).is(ModContent.PASSAGE_SEAL.get()), at + "seal in place");
        }
        check(treasure == plan.treasures() && shrines == 1 && arenas == plan.arenas(), at + "one shrine, the treasure rooms and the arena: " + shrines + ", " + treasure + ", " + arenas);
        check(Passages.sealsLeft(level, plan) == plan.seals.size(), at + "all seals whole");
        for (BlockPos g : plan.gatePositions()) check(level.getBlockState(g).is(ModContent.GATE_ASCEND.get()), at + "gate");

        // flood from the arrival spot through every place a player can stand
        AABB box = plan.box();
        BlockPos start = BlockPos.containing(plan.arrival());
        check(open(level, start) && open(level, start.above()) && solid(level, start.below()), at + "arrival spot is free and has a floor");
        Set<BlockPos> seen = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            check(here.getX() > box.minX && here.getX() < box.maxX - 1 && here.getZ() > box.minZ && here.getZ() < box.maxZ - 1,
                    at + "a player could walk out of the passage at " + here);
            // (a pool is a floor too: shallow water over a solid bed, waded through)
            check(solid(level, here.below()) || (level.getBlockState(here.below()).is(Blocks.WATER) && solid(level, here.below(2))), at + "no floor under " + here);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos next = here.relative(d);
                if (seen.contains(next) || !open(level, next) || !open(level, next.above())) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        for (Passages.Cell c : plan.cells()) {
            // a hall is only its crossing and arms; every other room is open two steps south of its middle
            BlockPos beside = plan.kind(c) == Passages.Kind.HALL ? plan.centre(c).above() : plan.centre(c).offset(0, 1, 2);
            check(seen.contains(beside), at + "room " + c + " (" + plan.kind(c) + ") cannot be walked into");
        }
        check(seen.contains(plan.gatePositions().get(0)), at + "the gate cannot be walked into");
    }

    /** A god's health for a trial nobody has scaled: its own, at the world's difficulty. */
    private static double base(ServerLevel level, God god) {
        return god.bossHealth() * Adapt.difficultyHealth(level.getDifficulty());
    }

    /**
     * Brings a god down the short way. No blow carries one through a phase, so this is a blow to each threshold,
     * its aegis struck away in between and the opening of its last stand waited out.
     */
    private static void slay(MinecraftServer server, ServerLevel level, GodBoss boss, ServerPlayer by) throws Exception {
        for (int i = 0; i < 8 && boss.isAlive(); i++) {
            if (boss.aegisUp()) boss.endAegis(level, true);
            if (boss.isInvulnerable()) advance(server, 45);
            boss.damageCooldownTime = 0;
            boss.hurtServer(level, level.damageSources().playerAttack(by), 1.0e5F);
        }
    }

    /** Strikes a shield pylon, leaving it the rest it needs between blows that count. */
    private static void strikePylon(MinecraftServer server, ServerLevel level, BlockPos pylon, ServerPlayer by, int blows) throws Exception {
        for (int i = 0; i < blows; i++) {
            advance(server, GodBoss.PYLON_REST);
            com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock.strike(level, pylon, by);
        }
    }

    /** The first room of a kind in a tower's passages: its plan and its cell. */
    private static Object[] room(ServerLevel level, Line line, Passages.Kind kind) {
        for (int index = 1; index <= Passages.COUNT; index++) {
            for (int stage = 0; stage < Passages.stages(level, line, index); stage++) {
                Passages.Plan plan = Passages.plan(level, line, index, stage);
                for (Passages.Cell c : plan.cells()) if (plan.kind(c) == kind) return new Object[]{plan, c};
            }
        }
        throw new IllegalStateException("no " + kind + " room anywhere in the tower of " + line);
    }

    private static void clearBosses(ServerLevel level) {
        for (GodBoss b : level.getEntitiesOfClass(GodBoss.class, new AABB(-64, -64, -64, 64, 400, 64), e -> true)) b.discard();
    }

    /** Checks one built floor: landmarks, a walkable fight ring with this floor's terrain, and a free boss spot. */
    private static void checkFloor(ServerLevel level, Line line, int floor) {
        String at = line + " floor " + floor + ": ";
        int y0 = Tower.floorY(floor);
        TowerBuilder.Layout layout = TowerBuilder.layout(floor);
        check(level.getBlockState(Tower.altarPos(floor)).getBlock() instanceof AltarBlock altar && altar.line() == line, at + "altar");
        check(level.getBlockState(new BlockPos(0, y0 + 1, 19)).is(ModContent.GATE_HUB.get()), at + "gate to hub");
        boolean ascend = level.getBlockState(new BlockPos(0, y0 + 1, -19)).is(ModContent.GATE_ASCEND.get());
        check(ascend == (floor < Tower.FLOORS), at + "gate of ascent present=" + ascend);
        Vec3 arrival = Tower.arrival(floor);
        BlockPos pad = BlockPos.containing(arrival);
        check(level.getBlockState(pad).isAir() && level.getBlockState(pad.above()).isAir() && solid(level, pad.below()), at + "arrival pad");
        check(solid(level, new BlockPos(22, y0 + 2, 0)), at + "outer wall");
        if (floor < Tower.FLOORS) check(solid(level, new BlockPos(5, y0 + Tower.ROOM_HEIGHT + 1, 5)), at + "roof");

        int ring = Tower.FIGHT_RADIUS - 1;
        for (int x = -ring; x <= ring; x++) {
            for (int z = -ring; z <= ring; z++) {
                if (x * x + z * z > ring * ring) continue;
                if (x == 0 && z == 12) continue;                           // the altar itself
                int h = TowerBuilder.height(layout, floor, x, z);
                check(h >= -2 && h <= 2, at + "height range at " + x + "," + z);
                check(solid(level, new BlockPos(x, y0 + h, z)), at + "ground missing at " + x + "," + z);
                for (int y = y0 + h + 1; y <= y0 + h + 6; y++) {
                    check(level.getBlockState(new BlockPos(x, y, z)).isAir(), at + "obstacle at " + x + "," + y + "," + z);
                }
                // nothing higher than a one-block step anywhere in the ring: walkable for players and bosses
                check(Math.abs(h - TowerBuilder.height(layout, floor, x + 1, z)) <= 1
                        && Math.abs(h - TowerBuilder.height(layout, floor, x, z + 1)) <= 1, at + "step too high at " + x + "," + z);
            }
        }
        Vec3 centre = Tower.centre(level, floor);
        check(Math.abs(centre.y - (y0 + 1 + TowerBuilder.height(layout, floor, 0, 0))) < 0.01, at + "centre height");
    }

    public static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer[] holder = new ServerPlayer[1];
        server.setDifficulty(Difficulty.NORMAL, true);
        ProgressData data = ProgressData.get(server);

        step("dimension types are registered", () -> {
            var types = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            for (Realm r : Realm.values()) {
                check(types.get(ResourceKey.create(Registries.DIMENSION_TYPE, RealmsOfChaos.id(r.id()))).isPresent(), "dimension type " + r.id());
            }
            int present = 0;
            for (Realm r : Realm.values()) if (server.getLevel(r.key()) != null) present++;
            RESULTS.add("        realm levels present on this server: " + present + " / 4 (the test server has none; the overworld stands in)");
        });
        step("advancements loaded: root + 45 gods", () -> {
            check(server.getAdvancements().get(RocAdvancements.ROOT) != null, "root advancement");
            for (God g : Gods.ALL) check(server.getAdvancements().get(RocAdvancements.idOf(g)) != null, "advancement " + g.id());
        });
        step("fake player", () -> {
            holder[0] = fakePlayer(server, level, "RocSelfTest");
            holder[0].setPos(0.5, 65, 0.5);
        });
        ServerPlayer player = holder[0];
        if (player == null) { finish(); return; }

        step("hub builds: three tower gates, the exit gate, safe spawn and arrivals", () -> {
            RealmBuilder.buildHub(level);
            check(level.getBlockState(new BlockPos(0, 66, -18)).is(ModContent.GATE_ZEUS.get()), "zeus gate");
            check(level.getBlockState(new BlockPos(18, 66, 0)).is(ModContent.GATE_POSEIDON.get()), "poseidon gate");
            check(level.getBlockState(new BlockPos(-18, 66, 0)).is(ModContent.GATE_HADES.get()), "hades gate");
            check(level.getBlockState(new BlockPos(0, 66, 18)).is(ModContent.GATE_LEAVE.get()), "exit gate");
            check(level.getBlockState(new BlockPos(6, 65, 6)).is(ModContent.CHAOS_FORGE.get()), "forge of chaos");
            check(level.getBlockState(new BlockPos(0, 65, 0)).isAir() && level.getBlockState(new BlockPos(0, 66, 0)).isAir(), "hub spawn is free");
            check(solid(level, new BlockPos(0, 64, 0)), "hub floor");
            for (Realm r : Realm.values()) {
                Vec3 a = Realm.hubArrival(r);
                check(level.getBlockState(BlockPos.containing(a)).isAir() && solid(level, BlockPos.containing(a).below()), "hub arrival for " + r.id());
            }
            // the livelier hub: the Oracle's Flame, a canal bridged by the four paths, gardens, and residents
            check(level.getBlockState(RealmBuilder.ORACLE).is(ModContent.ORACLE.get()), "the Oracle's Flame");
            check(level.getBlockState(new BlockPos(9, 64, 8)).is(Blocks.WATER) && level.getBlockState(new BlockPos(-9, 64, -8)).is(Blocks.WATER), "the canal holds water");
            for (BlockPos bridge : new BlockPos[]{new BlockPos(12, 64, 0), new BlockPos(-12, 64, 2), new BlockPos(0, 64, 12), new BlockPos(-3, 64, -12)}) {
                check(solid(level, bridge) && level.getBlockState(bridge.above()).isAir(), "the paths bridge the canal at " + bridge);
            }
            // nothing spills: every canal block has floor or canal on all four sides and stone beneath
            for (int x = -14; x <= 14; x++) {
                for (int z = -14; z <= 14; z++) {
                    BlockPos w = new BlockPos(x, 64, z);
                    if (!level.getBlockState(w).is(Blocks.WATER)) continue;
                    check(solid(level, w.below()), "canal has a bed at " + w);
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        check(solid(level, w.relative(d)) || level.getBlockState(w.relative(d)).is(Blocks.WATER), "canal is contained at " + w);
                    }
                }
            }
            check(level.getBlockState(new BlockPos(HubLife.GARDEN, 65, HubLife.GARDEN)).is(Blocks.CHERRY_LOG)
                    && level.getBlockState(new BlockPos(-HubLife.GARDEN, 69, -HubLife.GARDEN)).is(Blocks.CHERRY_LEAVES), "garden trees");
            // the Heart of the Hub and the Bounty Board stand in the plaza, can be walked up to, and open their screens
            BlockPos heart = RealmBuilder.HEART, board = RealmBuilder.BOUNTIES;
            check(level.getBlockState(heart).is(ModContent.HUB_HEART.get()) && level.getBlockState(board).is(ModContent.BOUNTY_BOARD.get()), "the heart and the board stand in the plaza");
            for (BlockPos station : new BlockPos[]{heart, board}) {
                boolean reachable = false;
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    reachable |= open(level, station.relative(d)) && open(level, station.relative(d).above()) && solid(level, station.relative(d).below());
                }
                check(reachable, "a player can walk up to the station at " + station);
            }
            ServerPlayer visitor = fakePlayer(server, level, "RocVisitor");
            level.getBlockState(heart).useWithoutItem(level, visitor, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(heart), net.minecraft.core.Direction.NORTH, heart, false));
            check(Screens.last(visitor) != null && Screens.commands(Screens.last(visitor)).contains("/roc ui hubcore deposit"), "right-clicking the heart opens Hub Command");
            level.getBlockState(board).useWithoutItem(level, visitor, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(board), net.minecraft.core.Direction.NORTH, board, false));
            check(Screens.bodyLines(Screens.last(visitor)) >= 1 && Screens.commands(Screens.last(visitor)).isEmpty(), "right-clicking the board opens it (shut, at the hub's first tier)");
            settle(level);
            List<net.minecraft.world.entity.Entity> residents = HubLife.residents(level);
            List<GodBoss> echoes = residents.stream().filter(e -> e instanceof GodBoss).map(e -> (GodBoss) e).toList();
            check(echoes.size() == 6, "six echoes stand by the gates: " + echoes.size());
            for (Line line : Line.values()) {
                BlockPos[] plinths = HubLife.plinths(line);
                for (int i = 0; i < 2; i++) {
                    BlockPos plinth = plinths[i];
                    God expect = i == 0 ? Gods.get(line, Line.RANKS) : Gods.get(line, 1);
                    GodBoss echo = echoes.stream().filter(e -> e.blockPosition().equals(plinth.above())).findFirst().orElse(null);
                    check(echo != null && echo.god() == expect && echo.isStatue(), "echo of " + expect.id() + " on its plinth");
                    check(solid(level, plinth) && level.noCollision(echo) && !echo.isInWall(), "echo of " + expect.id() + " stands free");
                    check(echo.getMainHandItem().is(ModContent.weaponItem(expect)), "the echo holds its relic");
                    float hp = echo.getHealth();
                    check(!echo.hurtServer(level, level.damageSources().playerAttack(player), 50.0F) && echo.getHealth() == hp, "an echo cannot be hurt");
                    check(!echo.isPushable() && echo.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == 0.0, "an echo stays where it is");
                }
            }
            check(residents.stream().filter(e -> e instanceof net.minecraft.world.entity.npc.villager.Villager).count() == HubLife.PILGRIMS, "pilgrims");
            check(residents.stream().filter(e -> e instanceof net.minecraft.world.entity.animal.feline.Cat).count() == HubLife.CATS, "cats");
            check(residents.stream().filter(e -> e instanceof net.minecraft.world.entity.animal.allay.Allay).count() == HubLife.ALLAYS, "allays");
            for (net.minecraft.world.entity.Entity e : residents) check(level.noCollision(e), "a resident is stuck in a block: " + e.getType().toShortString() + " at " + e.blockPosition());
            // rebuilding does not double them up
            HubLife.populate(level);
            settle(level);
            check(HubLife.residents(level).size() == residents.size(), "repopulating replaces the residents: " + HubLife.residents(level).size());
            HubLife.clear(level);
            check(HubLife.residents(level).isEmpty(), "residents cleared");
        });

        step("the 15 floors of a tower all differ (layout + tier)", () -> {
            Set<String> seen = new HashSet<>();
            for (int f = 1; f <= Tower.FLOORS; f++) check(seen.add(TowerBuilder.layout(f) + "/" + Tower.tier(f)), "floor " + f + " repeats a design");
            for (int f = 1; f <= Tower.FLOORS; f++) check(Tower.floorAt(Tower.floorY(f) + 1) == f && Tower.floorAt(Tower.floorY(f) - Tower.DEPTH) == f
                    && Tower.floorAt(Tower.floorY(f) + Tower.ROOM_HEIGHT) == f, "floorAt for floor " + f);
        });

        step("living floors: every in-between shape of every floor stays walkable (no step over one block)", () -> {
            int ring = Tower.FIGHT_RADIUS + 1;
            for (int floor = 1; floor <= Tower.FLOORS; floor++) {
                check(TowerBuilder.altLayout(floor) != TowerBuilder.layout(floor), "floor " + floor + " has a different alternate layout");
                boolean differs = false;
                for (int st = 0; st <= TowerBuilder.MORPH_STEPS; st++) {
                    for (int x = -ring; x <= ring; x++) {
                        for (int z = -ring; z <= ring; z++) {
                            int h = TowerBuilder.morphHeight(floor, x, z, st);
                            check(h >= -2 && h <= 2, "floor " + floor + " step " + st + " height " + h);
                            check(Math.abs(h - TowerBuilder.morphHeight(floor, x + 1, z, st)) <= 1
                                    && Math.abs(h - TowerBuilder.morphHeight(floor, x, z + 1, st)) <= 1,
                                    "floor " + floor + " step " + st + ": step too high at " + x + "," + z);
                            if (st == TowerBuilder.MORPH_STEPS && h != TowerBuilder.morphHeight(floor, x, z, 0)) differs = true;
                            // the altar, arrival pad and gate corridors never move
                            if (Math.abs(x) <= 5 && (z >= 9 || z <= -14)) check(h == 0, "corridor moved on floor " + floor);
                        }
                    }
                }
                check(differs, "floor " + floor + " actually changes shape");
            }
        });

        for (Line line : Line.values()) {
            step("tower of " + line + ": all 15 floors build, are walkable and unobstructed", () -> {
                for (int floor = 1; floor <= Tower.FLOORS; floor++) {
                    TowerBuilder.buildFloor(level, line, floor);
                    checkFloor(level, line, floor);
                }
            });
        }

        step("living floors: a floor shifts under a mob and an item, carries them, and settles back", () -> {
            Line line = Line.HADES;
            int floor = 4, y0 = Tower.floorY(floor);                 // a pit that rises into a dais
            String key = TowerBuilder.floorKey(line, floor);
            LivingEntity mob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            Vec3 c = Tower.centre(level, floor);
            mob.setPos(c.x, c.y, c.z);
            level.addFreshEntity(mob);
            ItemEntity item = new ItemEntity(level, 3.5, y0 + TowerBuilder.morphHeight(floor, 3, 3, 0) + 1, 3.5, new ItemStack(Blocks.STONE));
            level.addFreshEntity(item);
            double startY = mob.getY();

            FloorShifts.set(level, line, floor, true);
            check(FloorShifts.isShifted(line, floor) && data.isShifted(key), "shift started and recorded");
            for (int i = 1; i <= TowerBuilder.MORPH_STEPS; i++) {
                FloorShifts.stepAll(server, false);
                check(level.noCollision(mob) && !mob.isInWall(), "mob buried at step " + i);
                check(level.noCollision(item), "item buried at step " + i);
            }
            check(FloorShifts.position(line, floor) == TowerBuilder.MORPH_STEPS, "fully shifted");
            int ring = Tower.FIGHT_RADIUS - 1;
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (x * x + z * z > ring * ring || (x == 0 && z == 12)) continue;
                    int h = TowerBuilder.morphHeight(floor, x, z, TowerBuilder.MORPH_STEPS);
                    check(solid(level, new BlockPos(x, y0 + h, z)) && level.getBlockState(new BlockPos(x, y0 + h + 1, z)).isAir(),
                            "shifted terrain wrong at " + x + "," + z);
                }
            }
            check(mob.getY() > startY + 1.5, "mob was carried up with the rising centre (" + startY + " -> " + mob.getY() + ")");
            check(Math.abs(Tower.centre(level, floor).y - (y0 + 1 + TowerBuilder.morphHeight(floor, 0, 0, TowerBuilder.MORPH_STEPS))) < 0.01, "boss spot follows the terrain");

            // with no god on the floor, the idle check sends it home
            FloorShifts.stepAll(server, true);
            for (int i = 0; i < TowerBuilder.MORPH_STEPS + 1; i++) FloorShifts.stepAll(server, false);
            check(!FloorShifts.isShifted(line, floor) && FloorShifts.position(line, floor) == 0 && !data.isShifted(key), "settled back");
            mob.discard();
            item.discard();
            checkFloor(level, line, floor);

            // a floor left shifted by a server stop is rebuilt on the next visit
            data.markShifted(key);
            TowerBuilder.reshape(level, line, floor, TowerBuilder.heights(floor, 0), TowerBuilder.heights(floor, 2));
            FloorShifts.repairIfStale(level, line, floor);
            check(!data.isShifted(key), "stale shift repaired");
            checkFloor(level, line, floor);
        });

        step("first join: thank-you, codex + compass given, only once, root advancement", () -> {
            Welcome.onJoin(player);
            check(count(player, ModContent.CHAOS_CODEX.get()) == 1, "codex given");
            check(count(player, ModContent.CHAOS_COMPASS.get()) == 1, "compass given");
            check(count(player, ModContent.FIELD_BAG.get()) == 1, "field bag given");
            check(Story.holds(player, Story.Chapter.PROLOGUE), "the Oracle's letter given");
            Welcome.onJoin(player);
            check(count(player, ModContent.CHAOS_CODEX.get()) == 1 && count(player, ModContent.FIELD_BAG.get()) == 1, "not given twice");
            // a player who joined before the bag existed is handed one, once
            data.unmarkBuilt("fieldkit/" + player.getUUID());
            Welcome.onJoin(player);
            Welcome.onJoin(player);
            check(count(player, ModContent.FIELD_BAG.get()) == 2 && count(player, ModContent.CHAOS_CODEX.get()) == 1, "an old hand gets the new bag once");
            check(player.getAdvancements().getOrStartProgress(server.getAdvancements().get(RocAdvancements.ROOT)).isDone(), "root advancement granted");
        });

        step("codex menu: opens, pages switch, has hub and leave buttons, never moves items", () -> {
            CodexMenu.open(player);
            check(player.containerMenu instanceof CodexMenu, "menu open");
            CodexMenu menu = (CodexMenu) player.containerMenu;
            check(menu.getSlot(CodexMenu.SLOT_HUB).getItem().is(ModContent.CHAOS_COMPASS.get()), "hub button");
            check(menu.getSlot(CodexMenu.SLOT_LEAVE).getItem().is(net.minecraft.world.item.Items.OAK_DOOR), "leave button");
            check(menu.slots.size() == 54 + 36, "double-chest layout, slots=" + menu.slots.size());
            menu.clicked(CodexMenu.SLOT_ZEUS + 18, 0, ContainerInput.PICKUP, player);      // a meter segment opens the tower too
            check(menu.page() == Line.ZEUS, "meter opens zeus page");
            menu.clicked(CodexMenu.SLOT_BACK, 0, ContainerInput.PICKUP, player);
            menu.clicked(CodexMenu.SLOT_ZEUS, 0, ContainerInput.PICKUP, player);
            check(menu.page() == Line.ZEUS, "zeus page");
            Set<Integer> slots = new HashSet<>();
            for (int floor = 1; floor <= Tower.FLOORS; floor++) {
                check(slots.add(CodexMenu.slotOfFloor(floor)), "floor " + floor + " shares a slot");
                check(!menu.getSlot(CodexMenu.slotOfFloor(floor)).getItem().isEmpty(), "icon for floor " + floor);
            }
            menu.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
            menu.clicked(60, 0, ContainerInput.PICKUP, player);
            menu.clicked(60, 0, ContainerInput.QUICK_MOVE, player);
            check(menu.getCarried().isEmpty(), "nothing picked up");
            menu.clicked(CodexMenu.SLOT_BACK, 0, ContainerInput.PICKUP, player);
            check(menu.page() == null, "back to main page");
            player.closeContainer();
        });

        // ---- climb each tower from floor 1 to the summit, the way a player does ------------------------------
        for (Line line : Line.values()) {
            final int[] passageRooms = {0};
            final int[] patrolRooms = {0};
            step("climb the tower of " + line + ": altar menu, 15 trials, 14 passages walked, advancements", () -> {
                Realm.testAlias(Level.OVERWORLD, Realm.ofLine(line));
                clearBosses(level);
                data.reset(player.getUUID());
                for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.setChunkForced(cx, cz, true);
                advance(server, 41);
                Teleports.enterTower(player, line);
                check(Tower.floorAt(player.getY()) == 1 && player.position().distanceTo(Tower.arrival(1)) < 0.1, "arrived on floor 1, at " + player.position());

                for (int floor = 1; floor <= Tower.FLOORS; floor++) {
                    String at = "floor " + floor + ": ";
                    God god = Tower.god(line, floor);
                    BlockPos altar = Tower.altarPos(floor);
                    check(AltarBlock.state(level, player, altar, line) == AltarBlock.State.READY, at + "altar ready, was " + AltarBlock.state(level, player, altar, line));

                    // the gate of ascent refuses until the god is beaten
                    if (floor < Tower.FLOORS) {
                        Teleports.ascend(player);
                        check(Tower.floorAt(player.getY()) == floor, at + "ascended without winning");
                    }
                    if (floor == 3) {
                        // a floor above the player's progress is locked, and Peaceful blocks trials
                        check(AltarBlock.state(level, player, Tower.altarPos(9), line) == AltarBlock.State.LOCKED, at + "higher floor locked");
                        server.setDifficulty(Difficulty.PEACEFUL, true);
                        check(AltarBlock.state(level, player, altar, line) == AltarBlock.State.PEACEFUL, at + "peaceful state");
                        server.setDifficulty(Difficulty.NORMAL, true);
                    }

                    // open the altar by right-clicking it and press "Begin the trial"
                    level.getBlockState(altar).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(altar), Direction.UP, altar, false));
                    check(Screens.last(player) != null && Screens.commands(Screens.last(player)).stream().anyMatch(c -> c.endsWith(" begin")), at + "right-clicking the altar opens its screen, ready to begin");
                    AltarMenu.open(player, line, altar);
                    check(player.containerMenu instanceof AltarMenu, at + "altar menu opened");
                    AltarMenu menu = (AltarMenu) player.containerMenu;
                    check(!menu.getSlot(AltarMenu.SLOT_GOD).getItem().isEmpty() && !menu.getSlot(AltarMenu.SLOT_ACTION).getItem().isEmpty(), at + "menu icons");
                    check(menu.getSlot(AltarMenu.SLOT_MOVE).getItem().is(net.minecraft.world.item.Items.BLAZE_POWDER), at + "altar names the god's move");
                    check(menu.getSlot(AltarMenu.SLOT_DROPS).getItem().is(ModContent.materialItem(line, Tower.tier(floor))), at + "altar shows the drops");
                    check(!AltarBlock.canRematch(level, player, altar, line), at + "no rematch before the first victory");
                    menu.clicked(AltarMenu.SLOT_ACTION, 0, ContainerInput.PICKUP, player);
                    check(!(player.containerMenu instanceof AltarMenu), at + "menu closed after starting");

                    int y0 = Tower.floorY(floor);
                    AABB room = new AABB(-24, y0 - 3, -24, 24, y0 + 17, 24);
                    List<GodBoss> bosses = level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive);
                    check(bosses.size() == 1, at + "one boss, got " + bosses.size());
                    GodBoss boss = bosses.get(0);
                    check(boss.god() == god, at + "boss is " + god.id() + ", got " + boss.god().id());
                    check(Math.abs(boss.getMaxHealth() - base(level, god)) < 0.01, at + "health: " + boss.getMaxHealth() + " against " + base(level, god));
                    check(boss.getMainHandItem().is(ModContent.weaponItem(god)), at + "holds its relic");
                    check(!boss.isInWall() && level.noCollision(boss), at + "boss spawned in free space at " + boss.position());
                    check(boss.position().distanceTo(Tower.centre(level, floor)) < 0.1, at + "boss stands on the floor centre");
                    check(AltarBlock.state(level, player, altar, line) == AltarBlock.State.UNDERWAY, at + "underway");
                    check(AltarBlock.begin(level, player, altar, line) == null, at + "no double summon");
                    boss.keepUnstuck(level);
                    check(boss.position().distanceTo(Tower.centre(level, floor)) < 0.1, at + "boss at home is left alone");

                    if (floor == 2 || floor == 8 || floor == 15) {
                        // at 65% the god raises its aegis; the floor holds still until its pylons are down, then shifts
                        boss.setHealth(boss.getMaxHealth() * 0.6F);
                        boss.updatePhase(level);
                        check(boss.phase() == 2 && boss.aegisUp() && boss.isInvulnerable() && !boss.pylons().isEmpty(), at + "the aegis is raised at 65%");
                        check(!FloorShifts.isShifted(line, floor), at + "the floor holds still behind the aegis");
                        for (BlockPos pylon : new ArrayList<>(boss.pylons())) {
                            check(level.getBlockState(pylon).is(ModContent.SHIELD_PYLON.get()) && solid(level, pylon.below()), at + "a pylon stands at " + pylon);
                            strikePylon(server, level, pylon, player, GodBoss.PYLON_HITS);
                            check(level.getBlockState(pylon).isAir(), at + "three blows break a pylon");
                        }
                        check(!boss.aegisUp() && !boss.isInvulnerable(), at + "the aegis is down with its last pylon");
                        check(FloorShifts.isShifted(line, floor), at + "floor shifts when the aegis breaks");
                        for (int i = 0; i < TowerBuilder.MORPH_STEPS; i++) {
                            FloorShifts.stepAll(server, false);
                            check(level.noCollision(boss) && !boss.isInWall(), at + "boss buried by the shift");
                        }
                        boss.keepUnstuck(level);
                        // (nothing ticks inside the self-test, so a boss on lowered ground has not fallen yet: compare x/z)
                        Vec3 c = Tower.centre(level, floor);
                        check(Math.hypot(boss.getX() - c.x, boss.getZ() - c.z) < 1.0 && boss.getY() >= c.y - 0.01, at + "boss still above the centre after the shift");
                        boss.setHealth(boss.getMaxHealth() * 0.3F);
                        boss.updatePhase(level);
                        check(boss.phase() == 3 && boss.madeLastStand(), at + "its wrath begins at 30%");
                        check(!FloorShifts.isShifted(line, floor), at + "floor shifts back when its wrath begins");
                        for (int i = 0; i < TowerBuilder.MORPH_STEPS + 1; i++) FloorShifts.stepAll(server, false);
                        check(level.noCollision(boss), at + "boss buried by the return shift");
                    }
                    slay(server, level, boss, player);
                    check(!boss.isAlive(), at + "boss died");
                    check(data.isDefeated(player.getUUID(), god), at + "progress saved");
                    check(!level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.weaponItem(god))).isEmpty(), at + "relic dropped");
                    Item material = ModContent.materialItem(line, Tower.tier(floor));
                    check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(material))
                            .stream().anyMatch(e -> e.getItem().getCount() == GodBoss.materialDrop(god)), at + "forge material of this tier dropped");
                    for (Tarot card : Tarot.dropsFor(god)) {
                        check(!level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.tarotItem(card))).isEmpty(), at + "tarot card " + card.id() + " dropped");
                    }
                    if (god.id().equals("hades")) {
                        check(!level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.TAROT_DECK.get())).isEmpty(), at + "Deck of Fate dropped");
                    }
                    check(boss.fightsAtRange() == god.kind().ranged(), at + "attack style matches the weapon kind");
                    for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
                    check(player.getAdvancements().getOrStartProgress(server.getAdvancements().get(RocAdvancements.idOf(god))).isDone(), at + "advancement");
                    check(AltarBlock.state(level, player, altar, line) == AltarBlock.State.DEFEATED, at + "altar shows defeated");

                    if (floor == 1 || floor == 8 || floor == 15) {
                        // rematch: the fallen god rises again from the altar menu and gives up materials, but no second relic
                        check(AltarBlock.canRematch(level, player, altar, line), at + "rematch offered");
                        AltarMenu.open(player, line, altar);
                        AltarMenu again = (AltarMenu) player.containerMenu;
                        check(again.getSlot(AltarMenu.SLOT_REMATCH).getItem().is(net.minecraft.world.item.Items.IRON_SWORD), at + "rematch button");
                        again.clicked(AltarMenu.SLOT_REMATCH, 0, ContainerInput.PICKUP, player);
                        check(!(player.containerMenu instanceof AltarMenu), at + "menu closed after the rematch began");
                        List<GodBoss> risen = level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive);
                        check(risen.size() == 1 && risen.get(0).isRematch() && risen.get(0).god() == god, at + "the god rises again");
                        check(!AltarBlock.canRematch(level, player, altar, line) && AltarBlock.rematch(level, player, altar, line) == null, at + "one rematch at a time");
                        slay(server, level, risen.get(0), player);
                        check(!risen.get(0).isAlive(), at + "rematch won");
                        check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(material))
                                .stream().anyMatch(e -> e.getItem().getCount() == GodBoss.materialDrop(god)), at + "rematch drops forge material");
                        check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.weaponItem(god))
                                || e.getItem().is(ModContent.TAROT_DECK.get()) || e.getItem().getItem() instanceof com.thefarawaydev.realmsofchaos.item.TarotCardItem).isEmpty(),
                                at + "rematch drops no second relic, cards or deck");
                        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
                        check(AltarBlock.canRematch(level, player, altar, line), at + "and can be fought again");
                    }

                    if (floor < Tower.FLOORS) {
                        // the gate of ascent leads into the passage to the next floor (travel has a 2 s cooldown, so move the clock by hand)
                        check(Tower.currentFloor(data, player.getUUID(), line) == floor, at + "the next floor is not reached until its passage is walked");
                        advance(server, 41);
                        Teleports.ascend(player);
                        for (int stage = 0; stage < Passages.stages(level, line, floor); stage++) {
                        Passages.Spot spot = Passages.locate(player);
                        check(spot != null && spot.line() == line && spot.index() == floor && spot.stage() == stage,
                                at + "the gate leads into level " + (stage + 1) + " of passage " + floor + ", got " + spot);
                        Passages.Plan plan = Passages.plan(level, spot);
                        String in = at + "passage level " + (stage + 1) + ": ";
                        check(plan.stage == stage && plan.stages == Passages.stages(level, line, floor) && plan.y() == Passages.Y + stage * Passages.LEVEL_PITCH, in + "the plan of this level");
                        check(player.position().distanceTo(plan.arrival()) < 0.1, in + "arrived at its start");
                        checkPassage(level, plan, in);
                        passageRooms[0] += plan.size * plan.size;

                        // its gate is sealed
                        advance(server, 41);
                        check(!Passages.tryExit(player, spot) && Passages.locate(player) != null, in + "the gate refuses while seals hold");
                        boolean thorough = (floor == 1 || floor == 6 || floor == 11) && (stage == 0 || stage == Passages.stages(level, line, floor) - 1);
                        for (Passages.Cell c : plan.seals) {
                            BlockPos seal = plan.sealPos(c);
                            if (!thorough) {
                                Passages.breakSeal(level, seal, player);
                                continue;
                            }
                            // walk into the chamber: its guardians wake, and the seal cannot be touched while they stand
                            BlockPos stand = plan.centre(c).offset(2, 1, 0);
                            player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                            check(plan.cellAt(player.blockPosition()).equals(c), in + "test setup: standing in the chamber");
                            check(Passages.guardians(level, plan, c).isEmpty(), in + "guardians sleep until someone comes");
                            Passages.service(player, level, spot);
                            settle(level);
                            List<net.minecraft.world.entity.Mob> guards = Passages.guardians(level, plan, c);
                            check(guards.size() == Passages.guardianCount(plan), in + "guardians woke: " + guards.size() + " of " + Passages.guardianCount(plan) + " - tagged mobs nearby: "
                                    + level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, plan.box().inflate(40), e -> e.entityTags().contains(Passages.GUARDIAN_TAG))
                                    .stream().map(e -> e.getType().toShortString() + (e.isAlive() ? "" : "(dead)") + "@" + e.blockPosition().toShortString() + e.entityTags()).toList());
                            for (net.minecraft.world.entity.Mob g : guards) check(level.noCollision(g) && plan.cellAt(g.blockPosition()).equals(c), in + "guardian stands free in its chamber");
                            check(guards.stream().allMatch(g -> g instanceof Sentinel s && s.kind().line == line && s.tier() == plan.tier && s.threat() == plan.threat), in + "the guardians are this tower's own sentinels, as strong as this level asks");
                            check(guards.stream().filter(g -> ((Sentinel) g).isElite()).count() == (plan.last() ? 1 : 0), in + "an elite stands guard on a passage's last level only");
                            check(guards.stream().filter(g -> ((Sentinel) g).kind().role == Sentinel.Role.KEEPER).count() == 1, in + "one Keeper guards the seal");
                            Passages.service(player, level, spot);
                            check(Passages.guardians(level, plan, c).size() == guards.size(), in + "guardians wake only once");
                            check(!SealBlock.tryOpen(level, seal, player) && !(player.containerMenu instanceof SealMenu), in + "the seal is warded while guardians stand");
                            for (net.minecraft.world.entity.Mob g : guards) g.hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
                            check(Passages.guardians(level, plan, c).isEmpty(), in + "guardians fell");
                            // now it can be struck: a miss stings, good strikes crack it, and it gives way
                            check(SealBlock.tryOpen(level, seal, player) && player.containerMenu instanceof SealMenu, in + "the strike screen opens");
                            SealMenu sealMenu = (SealMenu) player.containerMenu;
                            player.setHealth(player.getMaxHealth());
                            player.damageCooldownTime = 0;
                            waitFor(server, sealMenu.strikeBar(), StrikeBar.Hit.MISS);
                            sealMenu.clicked(SealMenu.SLOT_STRIKE, 0, ContainerInput.PICKUP, player);
                            check(sealMenu.cracks() == 0 && player.getHealth() < player.getMaxHealth(), in + "a missed strike cracks nothing and stings");
                            waitFor(server, sealMenu.strikeBar(), StrikeBar.Hit.GOOD);
                            sealMenu.clicked(SealMenu.SLOT_STRIKE, 0, ContainerInput.PICKUP, player);
                            check(sealMenu.cracks() == 1, in + "a good strike cracks it once");
                            for (int i = 0; i < 6 && player.containerMenu instanceof SealMenu; i++) {
                                waitFor(server, sealMenu.strikeBar(), StrikeBar.Hit.PERFECT);
                                sealMenu.clicked(SealMenu.SLOT_STRIKE, 0, ContainerInput.PICKUP, player);
                            }
                            check(!(player.containerMenu instanceof SealMenu) && level.getBlockState(seal).isAir(), in + "the seal broke and the screen closed");
                            player.setHealth(player.getMaxHealth());
                        }
                        check(Passages.sealsLeft(level, plan) == 0, in + "every seal is broken");
                        if (thorough) {
                            // the treasure: a share for every player, once each
                            Passages.Cell vault = plan.cells().stream().filter(c -> plan.kind(c) == Passages.Kind.TREASURE).findFirst().orElseThrow();
                            Item prize = ModContent.materialItem(line, plan.tier);
                            int had = count(player, prize);
                            check(Passages.loot(level, plan.featurePos(vault), player) && count(player, prize) > had, in + "the cache hands over a share");
                            had = count(player, prize);
                            check(!Passages.loot(level, plan.featurePos(vault), player) && count(player, prize) == had, in + "one share each");
                            ServerPlayer friend = fakePlayer(server, level, "RocFriendOf" + line + floor);
                            check(Passages.loot(level, plan.featurePos(vault), friend) && count(friend, prize) > 0, in + "a friend gets a share of their own");
                            check(level.getBlockState(plan.featurePos(vault)).is(ModContent.PASSAGE_CACHE.get()), in + "the cache stays for the next player");
                            // the arena (on the levels above the first): its cache stays shut while its elite and pack stand
                            for (Passages.Cell c : plan.cells()) {
                                if (plan.kind(c) != Passages.Kind.ARENA) continue;
                                BlockPos mid = plan.centre(c).offset(0, 1, 2);
                                player.setPos(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
                                check(!Passages.loot(level, plan.featurePos(c), player), in + "the arena's cache is guarded");
                                settle(level);
                                List<net.minecraft.world.entity.Mob> pack = Passages.guardians(level, plan, c);
                                check(pack.size() == 3 + Math.min(3, plan.stage) && pack.stream().filter(g -> ((Sentinel) g).isElite()).count() == 1, in + "an elite and its pack: " + pack.size());
                                for (net.minecraft.world.entity.Mob g : pack) {
                                    check(level.noCollision(g), in + "an arena sentinel is stuck in a block");
                                    g.discard();
                                }
                                check(Passages.loot(level, plan.featurePos(c), player), in + "the arena's cache opens when they have fallen");
                            }
                            // patrols: some ordinary rooms have sentinels of their own, which wake when walked into
                            for (Passages.Cell c : plan.patrols) {
                                check(!Set.of(Passages.Kind.START, Passages.Kind.EXIT, Passages.Kind.SEAL, Passages.Kind.TREASURE, Passages.Kind.SHRINE, Passages.Kind.ARENA).contains(plan.kind(c)),
                                        in + "patrols keep to ordinary rooms, not " + plan.kind(c));
                                BlockPos mid = plan.centre(c).above();
                                player.setPos(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
                                Passages.service(player, level, spot);
                                settle(level);
                                List<net.minecraft.world.entity.Mob> patrol = Passages.guardians(level, plan, c);
                                check(!patrol.isEmpty() && patrol.stream().noneMatch(g -> ((Sentinel) g).kind().role == Sentinel.Role.KEEPER), in + "a patrol woke in " + c + ": " + patrol.size());
                                for (net.minecraft.world.entity.Mob g : patrol) {
                                    check(level.noCollision(g), in + "a patrol sentinel is stuck in a block in " + plan.kind(c) + " " + c);
                                    g.discard();
                                }
                            }
                            patrolRooms[0] += plan.patrols.size();
                            // the map: opened with the Codex, it shows the rooms walked into and hints at the doors leading on
                            Passages.service(player, level, spot);
                            ModContent.CHAOS_CODEX.get().use(level, player, InteractionHand.MAIN_HAND);
                            check(player.containerMenu instanceof com.thefarawaydev.realmsofchaos.ui.PassageMenu, in + "the Codex opens the passage map here");
                            var map = (com.thefarawaydev.realmsofchaos.ui.PassageMenu) player.containerMenu;
                            Set<Passages.Cell> walked = Passages.visited(player, plan);
                            check(walked.contains(plan.start) && walked.containsAll(plan.seals) && walked.containsAll(plan.patrols)
                                    && walked.size() < plan.size * plan.size, in + "rooms walked into are remembered: " + walked);
                            for (Passages.Cell c : plan.cells()) {
                                boolean shown = !map.getSlot(com.thefarawaydev.realmsofchaos.ui.PassageMenu.slotOf(plan, c)).getItem().is(
                                        net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.BLACK));
                                boolean hinted = plan.neighbours(c).stream().anyMatch(n -> walked.contains(n) && plan.connected(c, n));
                                check(shown == (walked.contains(c) || hinted), in + "map shows " + c + " only if explored or a door leads there");
                            }
                            map.clicked(com.thefarawaydev.realmsofchaos.ui.PassageMenu.SLOT_CODEX, 0, ContainerInput.PICKUP, player);
                            check(!(player.containerMenu instanceof com.thefarawaydev.realmsofchaos.ui.PassageMenu) && Screens.commands(Screens.last(player)).contains("/roc ui map"),
                                    in + "the map leads on to the Codex, which offers the map again");
                            // the compass points at the gate once the seals are gone; the shrine blesses once
                            check(Passages.point(player, level, spot).equals(plan.centre(plan.exit).above()), in + "the compass points to the gate");
                            Passages.Cell shrine = plan.cells().stream().filter(c -> plan.kind(c) == Passages.Kind.SHRINE).findFirst().orElseThrow();
                            player.setHealth(4.0F);
                            check(ShrineBlock.bless(level, plan.featurePos(shrine), player) && player.getHealth() == player.getMaxHealth()
                                    && !player.getActiveEffects().isEmpty(), in + "the shrine heals and blesses");
                            check(!ShrineBlock.bless(level, plan.featurePos(shrine), player), in + "the shrine rests afterwards");
                            player.removeAllEffects();
                        }
                        if (stage + 1 < Passages.stages(level, line, floor)) {
                            // this level's gate leads up to the next level of the passage
                            advance(server, 41);
                            Teleports.ascend(player);
                            Passages.Spot up = Passages.locate(player);
                            check(up != null && up.stage() == stage + 1 && player.position().distanceTo(Passages.plan(level, up).arrival()) < 0.1, in + "the gate leads up a level, got " + up);
                            check(!data.hasPassed(player.getUUID(), line, floor), in + "the passage is not walked until its last level is");
                        }
                        }
                        // and the last level's gate leads on to the next floor
                        advance(server, 41);
                        Teleports.ascend(player);
                        check(Passages.locate(player) == null && Tower.floorAt(player.getY()) == floor + 1, at + "came out on floor " + (floor + 1) + ", is on " + Tower.floorAt(player.getY()));
                        check(player.position().distanceTo(Tower.arrival(floor + 1)) < 0.1, at + "arrived on the pad");
                        check(data.hasPassed(player.getUUID(), line, floor) && Tower.currentFloor(data, player.getUUID(), line) == floor + 1, at + "the passage is remembered");
                        if (floor == 1) {
                            // a passage already walked is skipped: the gate of ascent goes straight up
                            advance(server, 41);
                            Teleports.toFloor(player, line, 1);
                            check(Tower.floorAt(player.getY()) == 1, at + "back on floor 1");
                            advance(server, 41);
                            Teleports.ascend(player);
                            check(Passages.locate(player) == null && Tower.floorAt(player.getY()) == 2, at + "second time: straight up to floor 2");
                        }
                    }
                }
                check(data.nextRank(player.getUUID(), line) == 0 && data.count(player.getUUID(), line) == 15, "tower complete");
                RESULTS.add("        " + line + ": 14 passages built and walked, every level of each - " + passageRooms[0] + " rooms in all; " + patrolRooms[0] + " patrols woken in the levels checked closely");
                // a newcomer who has not walked a passage finds it sealed again, though others broke it open before
                ServerPlayer newcomer = fakePlayer(server, level, "RocNewcomer" + line);
                data.markDefeated(newcomer.getUUID(), Tower.god(line, 1));
                Passages.Plan first = Passages.plan(level, line, 1);
                check(Passages.sealsLeft(level, first) == 0, "test setup: passage 1 was broken open by the first climber");
                newcomer.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
                advance(server, 41);
                Teleports.ascend(newcomer);
                check(Passages.locate(newcomer) != null && Passages.sealsLeft(level, first) == first.seals.size(), "the passage sealed itself again for the newcomer");
                Passages.Cell chamber = first.seals.get(0);
                BlockPos inside = first.centre(chamber).offset(2, 1, 0);
                newcomer.setPos(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
                Passages.service(newcomer, level, new Passages.Spot(line, 1));
                settle(level);
                check(Passages.guardians(level, first, chamber).size() == Passages.guardianCount(first), "and its sentinels wake afresh");
                check(Passages.sealsLeft(level, Passages.plan(level, line, 1, 1)) == Passages.plan(level, line, 1, 1).seals.size(), "every level of it sealed again");
                // someone who has walked it is not sealed in again
                check(data.hasPassed(player.getUUID(), line, 1), "test setup: the first climber walked passage 1");
                Passages.rearm(level, line, 1);
                for (int st = 0; st < Passages.stages(level, line, 1); st++) {
                    Passages.Plan each = Passages.plan(level, line, 1, st);
                    for (Passages.Cell c : each.seals) Passages.breakSeal(level, each.sealPos(c), player);
                }
                data.reset(newcomer.getUUID());
                check(Tower.currentFloor(data, player.getUUID(), line) == Tower.FLOORS, "current floor is the summit");
                clearBosses(level);
            });
        }

        // ---- weapons: kinds, levelling, forging, ranged ---------------------------------------------------------
        Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
        final int arena = 5;
        final double ay = Tower.floorY(arena) + 1;

        step("weapon variety: nine kinds in use, swords are a minority, stats follow the kind", () -> {
            EnumSet<WeaponKind> used = EnumSet.noneOf(WeaponKind.class);
            int blades = 0, ranged = 0;
            for (God g : Gods.ALL) {
                used.add(g.kind());
                if (g.kind() == WeaponKind.BLADE) blades++;
                if (g.kind().ranged()) ranged++;
                Item item = ModContent.weaponItem(g);
                var mods = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
                check(mods != null, "attributes on " + g.id());
                double dmg = mods.modifiers().stream().filter(m -> m.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)).findFirst().orElseThrow().modifier().amount();
                check(Math.abs(dmg - g.baseDamage() * g.kind().meleeScale()) < 0.001, "melee damage of " + g.id());
                boolean reach = mods.modifiers().stream().anyMatch(m -> m.modifier().id().equals(RealmsOfChaos.id("relic_reach")));
                check(reach == (g.kind().reach() > 0), "reach of " + g.id());
            }
            check(used.size() == WeaponKind.values().length, "kinds used: " + used);
            check(blades <= 8, "too many plain blades: " + blades);
            check(ranged >= 12, "ranged gods: " + ranged);
            RESULTS.add("        " + blades + " blades, " + ranged + " ranged gods, " + used.size() + " weapon kinds");
        });

        step("weapon levelling: kills give experience, levels raise damage, the name shows it", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocHero");
            hero.setPos(0.5, ay, 0.5);
            ItemStack sword = new ItemStack(ModContent.weaponItem(Gods.byId("ares")));
            hero.setItemInHand(InteractionHand.MAIN_HAND, sword);
            check(WeaponProgress.level(sword) == 1 && WeaponProgress.stars(sword) == 0 && WeaponProgress.multiplier(sword) == 1.0, "fresh weapon");
            LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            zombie.setPos(2.5, ay, 2.5);
            level.addFreshEntity(zombie);
            GodWeaponItem.onKill(zombie, level.damageSources().playerAttack(hero));
            check(WeaponProgress.xp(hero.getMainHandItem()) == WeaponProgress.xpFor(zombie), "xp from a kill: " + WeaponProgress.xp(hero.getMainHandItem()));
            zombie.discard();
            ItemStack held = hero.getMainHandItem();
            check(WeaponProgress.addXp(held, WeaponProgress.xpNeeded(1)) == 1 && WeaponProgress.level(held) == 2, "level 2");
            check(Math.abs(WeaponProgress.multiplier(held) - 1.08) < 1e-9, "level 2 multiplier");
            check(held.getHoverName().getString().contains("+1"), "name shows the level: " + held.getHoverName().getString());
            WeaponProgress.addXp(held, 100000);
            check(WeaponProgress.level(held) == WeaponProgress.MAX_LEVEL && WeaponProgress.xp(held) == 0, "max level");
            float bonus = ((GodWeaponItem) held.getItem()).getAttackDamageBonus(null, 10.0F, level.damageSources().playerAttack(hero));
            check(Math.abs(bonus - 10.0F * (float) (WeaponProgress.multiplier(held) - 1.0)) < 0.01, "damage bonus " + bonus);
            // a god is worth far more than a zombie
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.byId("zeus"));
            check(WeaponProgress.xpFor(b) > 150, "god xp");
            b.discard();
        });

        step("forge of chaos: stars cost the tower's tier materials and need levels; one tower fully forges a weapon", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocSmith");
            hero.setPos(0.5, ay, 0.5);
            CodexMenu.open(hero);
            check(hero.containerMenu instanceof CodexMenu c && !c.getSlot(CodexMenu.SLOT_FORGE).getItem().isEmpty(), "codex has a forge button");
            hero.closeContainer();
            ForgeMenu.open(hero);
            check("none".equals(((ForgeMenu) hero.containerMenu).blocker()), "empty hand is refused");
            hero.closeContainer();
            ItemStack bow = new ItemStack(ModContent.weaponItem(Gods.byId("apollo")));
            hero.setItemInHand(InteractionHand.MAIN_HAND, bow);
            ForgeMenu.open(hero);
            ForgeMenu forge = (ForgeMenu) hero.containerMenu;
            check("level".equals(forge.blocker()), "level 1 weapon cannot take a star: " + forge.blocker());
            WeaponProgress.setLevel(hero.getMainHandItem(), 2);
            check("cost".equals(forge.blocker()), "no materials: " + forge.blocker());
            // the wrong tower's material does not count
            hero.getInventory().add(new ItemStack(ModContent.materialItem(Line.HADES, 1), 9));
            check("cost".equals(forge.blocker()), "another tower's material is refused");
            hero.getInventory().add(new ItemStack(ModContent.materialItem(Line.ZEUS, 1), 5));
            check(forge.blocker() == null, "ready to forge: " + forge.blocker());
            forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
            check(WeaponProgress.stars(hero.getMainHandItem()) == 0 && forge.strikeBar() != null, "pressing forge starts the strikes; the star is not forged yet");
            check(ForgeMenu.count(hero, ModContent.materialItem(Line.ZEUS, 1)) == 2, "the price is paid up front");
            check(forge.getSlot(ForgeMenu.slot(ForgeMenu.BAR_ROW, forge.strikeBar().target())).getItem().is(net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.YELLOW))
                    || forge.getSlot(ForgeMenu.slot(ForgeMenu.BAR_ROW, forge.strikeBar().target())).getItem().is(net.minecraft.world.item.Items.MACE), "the strike bar shows its mark");
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.PICKUP, hero);
            check(forge.strikeBar() != null, "other buttons wait while a round is struck");
            strike(server, forge, hero, StrikeBar.Hit.GOOD, StrikeBar.Hit.GOOD, StrikeBar.Hit.GOOD);
            check(forge.strikeBar() == null && forge.lastFinish() == ForgeMenu.Finish.FINE, "three good strikes: fine work");
            check(WeaponProgress.stars(hero.getMainHandItem()) == 1, "one star");
            check(WeaponProgress.temper(hero.getMainHandItem()) == 0 && WeaponProgress.xp(hero.getMainHandItem()) == ForgeMenu.FINE_XP, "fine work: experience, no temper");
            check(ForgeMenu.count(hero, ModContent.materialItem(Line.ZEUS, 1)) == 2, "three laurel leaves spent");
            check(ForgeMenu.count(hero, ModContent.materialItem(Line.HADES, 1)) == 9, "other materials untouched");
            check("level".equals(forge.blocker()), "second star needs level 4");
            check(Math.abs(WeaponProgress.multiplier(hero.getMainHandItem()) - 1.08 * 1.10) < 1e-9, "multiplier with a star");
            check(hero.getMainHandItem().getHoverName().getString().contains("\u2605"), "name shows a star");
            check(forge.getCarried().isEmpty(), "forge moves no items");
            // the screen: the weapon, a five-star track with the next star marked, the move, a preview, a guide, the price
            check(forge.getSlot(ForgeMenu.SLOT_WEAPON).getItem().is(ModContent.weaponItem(Gods.byId("apollo"))), "forge shows the weapon");
            check(forge.getSlot(ForgeMenu.slot(ForgeMenu.STAR_ROW, ForgeMenu.STAR_COLUMN)).getItem().is(net.minecraft.world.item.Items.NETHER_STAR), "first star lit");
            check(!forge.getSlot(ForgeMenu.slot(ForgeMenu.STAR_ROW, ForgeMenu.STAR_COLUMN + 1)).getItem().is(net.minecraft.world.item.Items.NETHER_STAR), "second star not yet");
            for (int slot : new int[]{ForgeMenu.SLOT_LEVEL, ForgeMenu.SLOT_MOVE, ForgeMenu.SLOT_PREVIEW, ForgeMenu.SLOT_GUIDE, ForgeMenu.SLOT_INFUSE,
                    ForgeMenu.SLOT_COST, ForgeMenu.SLOT_COST_2, ForgeMenu.SLOT_FORGE, ForgeMenu.SLOT_CLOSE}) {
                check(!forge.getSlot(slot).getItem().is(net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.BLACK))
                        && !forge.getSlot(slot).getItem().is(net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.ORANGE)), "forge slot " + slot + " is filled in");
            }
            check(forge.getSlot(ForgeMenu.SLOT_COST).getItem().is(ModContent.materialItem(Line.ZEUS, 1))
                    && forge.getSlot(ForgeMenu.SLOT_COST_2).getItem().is(ModContent.materialItem(Line.ZEUS, 2)), "price of star two shown");

            // forge the rest: every star, each tier's material, the summit essence last
            WeaponProgress.setLevel(hero.getMainHandItem(), 10);
            for (int tier = 1; tier <= 4; tier++) hero.getInventory().add(new ItemStack(ModContent.materialItem(Line.ZEUS, tier), 16));
            for (int star = 2; star <= WeaponProgress.MAX_STARS; star++) {
                check(forge.blocker() == null, "star " + star + " ready: " + forge.blocker());
                forgeFine(server, forge, hero);
                check(WeaponProgress.stars(hero.getMainHandItem()) == star, "star " + star);
            }
            check("max".equals(forge.blocker()), "fully forged");
            check(Boolean.TRUE.equals(hero.getMainHandItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "a fully forged relic shimmers");

            // what a whole tower drops covers everything one weapon needs
            int[] need = new int[5], drops = new int[5];
            for (int star = 1; star <= WeaponProgress.MAX_STARS; star++) {
                for (WeaponProgress.Cost cost : WeaponProgress.starCost(star)) need[cost.tier()] += cost.count();
            }
            for (God g : Gods.ofLine(Line.ZEUS)) drops[Tower.tier(Tower.floorOf(g))] += GodBoss.materialDrop(g);
            for (int tier = 1; tier <= 4; tier++) {
                check(need[tier] > 0 && drops[tier] >= need[tier], "tier " + tier + ": tower drops " + drops[tier] + ", a weapon needs " + need[tier]);
            }
            RESULTS.add("        a tower drops " + drops[1] + "/" + drops[2] + "/" + drops[3] + "/" + drops[4] + " materials; one weapon needs "
                    + need[1] + "/" + need[2] + "/" + need[3] + "/" + need[4]);
            hero.closeContainer();
        });

        step("forging is a quick-time event: the hammer, the mark, and the finish the strikes earn", () -> {
            // the bar: the hammer sweeps 0..8 and back, one slot every few ticks
            StrikeBar bar = new StrikeBar(1000, 2, 4);
            int[] expect = {0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1};
            for (int i = 0; i < expect.length; i++) check(bar.marker(1000 + i * 2L) == expect[i] && bar.marker(1001 + i * 2L) == expect[i], "hammer at step " + i);
            check(bar.hit(1008) == StrikeBar.Hit.PERFECT && bar.hit(1006) == StrikeBar.Hit.GOOD && bar.hit(1010) == StrikeBar.Hit.GOOD
                    && bar.hit(1004) == StrikeBar.Hit.MISS && bar.hit(1000) == StrikeBar.Hit.MISS, "strikes are judged by distance from the mark");
            check(bar.row(1008).length == 9 && bar.row(1008)[4].is(net.minecraft.world.item.Items.MACE), "the bar draws the hammer where it is");
            StrikeBar.Hit P = StrikeBar.Hit.PERFECT, G = StrikeBar.Hit.GOOD, M = StrikeBar.Hit.MISS;
            check(ForgeMenu.judge(List.of(P, P, G)) == ForgeMenu.Finish.MASTERWORK && ForgeMenu.judge(List.of(P, P, P)) == ForgeMenu.Finish.MASTERWORK, "masterwork: no misses, two perfect");
            check(ForgeMenu.judge(List.of(P, G, G)) == ForgeMenu.Finish.FINE && ForgeMenu.judge(List.of(P, P, M)) == ForgeMenu.Finish.FINE, "fine: at most one miss");
            check(ForgeMenu.judge(List.of(M, M, P)) == ForgeMenu.Finish.ROUGH, "rough: two misses");

            ServerPlayer hero = fakePlayer(server, level, "RocFarrier");
            hero.setPos(0.5, ay, 0.5);
            ItemStack blade = new ItemStack(ModContent.weaponItem(Gods.byId("chrysaor")));
            WeaponProgress.setLevel(blade, 9);
            hero.setItemInHand(InteractionHand.MAIN_HAND, blade);
            for (int tier = 1; tier <= 4; tier++) hero.getInventory().add(new ItemStack(ModContent.materialItem(Line.POSEIDON, tier), 20));
            ForgeMenu.open(hero);
            ForgeMenu forge = (ForgeMenu) hero.containerMenu;

            // star 1, masterwork: tempered, and the relic learns from it
            forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
            strike(server, forge, hero, P, G, P);
            ItemStack held = hero.getMainHandItem();
            check(forge.lastFinish() == ForgeMenu.Finish.MASTERWORK && WeaponProgress.stars(held) == 1 && WeaponProgress.temper(held) == 1, "masterwork tempers the star");
            check(WeaponProgress.xp(held) == ForgeMenu.MASTERWORK_XP, "masterwork experience");
            check(Math.abs(WeaponProgress.multiplier(held) - (1.0 + 0.08 * 8) * (1.0 + 0.10 + 0.02)) < 1e-9, "a tempered star is worth 12%");
            check("full".equals(forge.temperBlocker()), "nothing left to temper");

            // star 2, rough: forged all the same, but plain
            int xp = WeaponProgress.xp(held);
            forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
            strike(server, forge, hero, M, M, G);
            check(forge.lastFinish() == ForgeMenu.Finish.ROUGH && WeaponProgress.stars(held) == 2 && WeaponProgress.temper(held) == 1 && WeaponProgress.xp(held) == xp, "rough work still forges the star");

            // star 3: walking away mid-round does not lose what was paid - the round ends as it stands
            int tier2 = ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 2));
            forge.clicked(ForgeMenu.SLOT_FORGE, 0, ContainerInput.PICKUP, hero);
            strike(server, forge, hero, P);
            hero.closeContainer();
            check(!(hero.containerMenu instanceof ForgeMenu) && WeaponProgress.stars(held) == 3, "closing the forge mid-round still forges the star");
            check(ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 2)) == tier2 - 4 && WeaponProgress.temper(held) == 1, "paid once, finished roughly");

            // tempering: a second go at a plain star - only a masterwork takes
            ForgeMenu.open(hero);
            forge = (ForgeMenu) hero.containerMenu;
            check(forge.temperBlocker() == null && !forge.getSlot(ForgeMenu.SLOT_TEMPER).getItem().isEmpty(), "two plain stars can be tempered");
            int tier1 = ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 1));
            forge.clicked(ForgeMenu.SLOT_TEMPER, 0, ContainerInput.PICKUP, hero);
            check(ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 1)) == tier1 - ForgeMenu.TEMPER_COST, "tempering has a price");
            strike(server, forge, hero, G, G, G);
            check(WeaponProgress.temper(held) == 1 && WeaponProgress.stars(held) == 3, "good is not enough to temper");
            forge.clicked(ForgeMenu.SLOT_TEMPER, 0, ContainerInput.PICKUP, hero);
            strike(server, forge, hero, P, P, P);
            check(WeaponProgress.temper(held) == 2, "a masterwork tempers the second star");
            forge.clicked(ForgeMenu.SLOT_TEMPER, 0, ContainerInput.PICKUP, hero);
            strike(server, forge, hero, P, P, G);
            check(WeaponProgress.temper(held) == 3 && "full".equals(forge.temperBlocker()), "all three stars tempered");
            tier1 = ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 1));
            forge.clicked(ForgeMenu.SLOT_TEMPER, 0, ContainerInput.PICKUP, hero);
            check(forge.strikeBar() == null && ForgeMenu.count(hero, ModContent.materialItem(Line.POSEIDON, 1)) == tier1, "nothing more to temper, nothing taken");
            hero.closeContainer();
        });

        step("passages: procedural - laid out from the world seed, different from tower to tower and world to world", () -> {
            int changed = 0, acrossTowers = 0;
            Set<Passages.Kind> kinds = EnumSet.noneOf(Passages.Kind.class);
            for (int n = 1; n <= Passages.COUNT; n++) {
                Passages.Plan here = Passages.plan(level.getSeed(), Line.ZEUS, n);
                check(here == Passages.plan(level.getSeed(), Line.ZEUS, n), "one plan per passage");
                Passages.Plan other = Passages.plan(level.getSeed() + 1, Line.ZEUS, n);
                Passages.Plan hades = Passages.plan(level.getSeed(), Line.HADES, n);
                StringBuilder a = new StringBuilder(), b = new StringBuilder(), c = new StringBuilder();
                for (int st = 0; st < Passages.stages(level.getSeed(), Line.ZEUS, n); st++) {
                    Passages.Plan each = Passages.plan(level.getSeed(), Line.ZEUS, n, st);
                    for (Passages.Cell cell : each.cells()) kinds.add(each.kind(cell));
                    check(each.threat == Sentinel.threat(n, st) && (st == 0 || each.threat > Passages.plan(level.getSeed(), Line.ZEUS, n, st - 1).threat), "each level in is more dangerous");
                    check(each.seed != here.seed || st == 0, "each level has a maze of its own");
                }
                int levels = Passages.stages(level.getSeed(), Line.ZEUS, n);
                check(levels >= (n <= 5 ? 1 : n <= 10 ? 2 : 4) && levels <= (n <= 5 ? 4 : n <= 10 ? 7 : 10), "passage " + n + ": more levels the higher in the tower: " + levels);
                check(n == 1 || here.threat > Passages.plan(level.getSeed(), Line.ZEUS, n - 1).threat, "each passage is more dangerous than the last");
                for (Passages.Cell cell : here.cells()) {
                    a.append(here.kind(cell).ordinal());
                    b.append(other.kind(cell).ordinal());
                    c.append(hades.kind(cell).ordinal());
                }
                if (!a.toString().contentEquals(b)) changed++;
                if (!a.toString().contentEquals(c)) acrossTowers++;
                check(here.size == (n <= 5 ? 4 : 5) && here.seals.size() == Tower.tier(n) + 1, "passage " + n + " grows with its tier");
            }
            check(changed >= Passages.COUNT - 1, "another world seed lays the passages out differently: " + changed + " of " + Passages.COUNT);
            check(acrossTowers >= Passages.COUNT - 1, "another tower lays them out differently: " + acrossTowers);
            check(kinds.size() == Passages.Kind.values().length, "every kind of room turns up in one tower: " + kinds);
            // any seed gives a sound plan: every room reachable, the right seals, one shrine, a gate apart from the way in
            java.util.Random seeds = new java.util.Random(99);
            for (int i = 0; i < 300; i++) {
                long seed = seeds.nextLong();
                for (Line line : Line.values()) {
                    int n = 1 + seeds.nextInt(Passages.COUNT);
                    Passages.Plan plan = Passages.plan(seed, line, n, seeds.nextInt(Passages.stages(seed, line, n)));
                    check(plan.distances().size() == plan.size * plan.size && !plan.start.equals(plan.exit) && plan.seals.size() == plan.tier + 1,
                            "seed " + seed + " " + line + " " + n + ": unsound plan");
                    check(plan.cells().stream().filter(c -> plan.kind(c) == Passages.Kind.SHRINE).count() == 1
                            && plan.cells().stream().noneMatch(c -> plan.kind(c) == null), "seed " + seed + ": rooms");
                }
            }
            check(Passages.plan(level.getSeed(), Line.ZEUS, 12, Passages.stages(level.getSeed(), Line.ZEUS, 12) - 1).size == 6 && Passages.plan(level.getSeed(), Line.ZEUS, 12, 0).size == 5,
                    "the last level of the highest passages is the biggest");
            check(Passages.stageAt(level, Line.ZEUS, 6, Passages.Y + 1) == 0 && Passages.stageAt(level, Line.ZEUS, 6, Passages.Y + Passages.LEVEL_PITCH + 1) == 1
                    && Passages.stageAt(level, Line.ZEUS, 6, 5000) == Passages.stages(level, Line.ZEUS, 6) - 1, "a height tells which level you are on");
            RESULTS.add("        passage 6 of the tower of Zeus in this world, level by level (S in, X gate, * seal, $ treasure, A arena, + shrine, ! trap, g garden, b library, ~ pools, o corridors, # room):");
            for (int st = 0; st < Passages.stages(level, Line.ZEUS, 6); st++) {
                RESULTS.add("          level " + (st + 1) + ":");
                for (String row : Passages.plan(level.getSeed(), Line.ZEUS, 6, st).ascii()) RESULTS.add("            " + row);
            }
            // where a passage is: out to the side of the tower, never mistaken for it
            check(Passages.indexAt(0.5) == 0 && Passages.indexAt(Tower.WALL_OUTER) == 0, "the tower itself is no passage");
            for (int n = 1; n <= Passages.COUNT; n++) {
                Passages.Plan plan = Passages.plan(level.getSeed(), Line.ZEUS, n);
                check(Passages.indexAt(plan.arrival().x) == n && Passages.indexAt(plan.origin().getX()) == n
                        && Passages.indexAt(plan.origin().getX() + plan.width() - 1) == n, "passage " + n + " is found by where it is");
            }
        });

        step("forge of chaos: your own experience levels can be poured into a relic", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocScholar");
            hero.setPos(0.5, ay, 0.5);
            hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.weaponItem(Gods.byId("nike"))));
            ForgeMenu.open(hero);
            ForgeMenu forge = (ForgeMenu) hero.containerMenu;
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.PICKUP, hero);
            check(WeaponProgress.xp(hero.getMainHandItem()) == 0 && WeaponProgress.level(hero.getMainHandItem()) == 1, "nothing to give, nothing gained");
            hero.giveExperienceLevels(8);
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.PICKUP, hero);
            check(hero.experienceLevel == 7, "one level spent, has " + hero.experienceLevel);
            // 30 experience is exactly what level 1 needs
            check(WeaponProgress.level(hero.getMainHandItem()) == 2 && WeaponProgress.xp(hero.getMainHandItem()) == ForgeMenu.INFUSE_XP - WeaponProgress.xpNeeded(1),
                    "weapon gained " + ForgeMenu.INFUSE_XP + " xp");
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.QUICK_MOVE, hero);
            check(hero.experienceLevel == 2, "shift-click spends five, has " + hero.experienceLevel);
            check(WeaponProgress.level(hero.getMainHandItem()) == 3 && WeaponProgress.xp(hero.getMainHandItem()) == 6 * ForgeMenu.INFUSE_XP - 30 - 80, "five levels of xp");
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.QUICK_MOVE, hero);
            check(hero.experienceLevel == 0, "shift-click with fewer than five gives what is left");
            WeaponProgress.setLevel(hero.getMainHandItem(), WeaponProgress.MAX_LEVEL);
            hero.giveExperienceLevels(3);
            forge.clicked(ForgeMenu.SLOT_INFUSE, 0, ContainerInput.PICKUP, hero);
            check(hero.experienceLevel == 3, "a level 10 relic takes no more");
            forge.clicked(ForgeMenu.SLOT_CLOSE, 0, ContainerInput.PICKUP, hero);
            check(!(hero.containerMenu instanceof ForgeMenu), "step back closes the forge");
        });

        step("ranged weapons: bows loose arrows, staffs and thrown relics fire bolts, relics pulse, bolts hurt", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocArcher");
            hero.setPos(0.5, ay, 0.5);
            AABB near = new AABB(-8, ay - 2, -8, 8, ay + 6, 8);
            for (God g : Gods.ALL) {
                if (!g.kind().ranged()) continue;
                ItemStack stack = new ItemStack(ModContent.weaponItem(g));
                hero.setItemInHand(InteractionHand.MAIN_HAND, stack);
                LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                zombie.setPos(2.5, ay, 2.5);
                level.addFreshEntity(zombie);
                float before = zombie.getHealth();
                stack.getItem().use(level, hero, InteractionHand.MAIN_HAND);
                List<Projectile> shots = level.getEntitiesOfClass(Projectile.class, near, e -> true);
                if (g.kind() == WeaponKind.RELIC) {
                    check(shots.isEmpty() && zombie.getHealth() < before, g.id() + ": relic pulse hurts what is near");
                } else {
                    check(shots.size() == 1, g.id() + ": one shot, got " + shots.size());
                    check((shots.get(0) instanceof Arrow) == (g.kind() == WeaponKind.BOW), g.id() + ": projectile type");
                    if (shots.get(0) instanceof RelicProjectile bolt) {
                        check(!bolt.getItem().isEmpty(), g.id() + ": bolt has a sprite");
                        bolt.impact(level, hero, zombie);
                        check(zombie.getHealth() < before, g.id() + ": bolt hurts");
                    }
                }
                check(hero.getCooldowns().isOnCooldown(stack), g.id() + ": shot starts a cooldown");
                for (Projectile p : shots) p.discard();
                zombie.discard();
                hero.getCooldowns().removeCooldown(RealmsOfChaos.id(g.id() + "_weapon"));
            }
            // a bolt never hurts another player
            ServerPlayer friend = fakePlayer(server, level, "RocFriend");
            friend.setPos(2.5, ay, 2.5);
            RelicProjectile bolt = new RelicProjectile(level, hero, new ItemStack(ModContent.orbItem(Power.FIRE)));
            float hp = friend.getHealth();
            bolt.impact(level, hero, friend);
            check(friend.getHealth() == hp, "no friendly fire");
        });

        step("ranged gods shoot; melee gods do not; Hades throws tarot cards", () -> {
            ServerPlayer target = fakePlayer(server, level, "RocTarget");
            target.setPos(8.5, ay, 0.5);
            AABB near = new AABB(-12, ay - 2, -12, 12, ay + 8, 12);
            for (String id : new String[]{"apollo", "zeus", "hermes", "hebe", "nyx"}) {
                GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                b.setGod(Gods.byId(id));
                b.setPos(0.5, ay, 0.5);
                level.addFreshEntity(b);
                check(b.fightsAtRange(), id + " fights at range");
                b.performRangedAttack(target, 1.0F);
                List<Projectile> shots = level.getEntitiesOfClass(Projectile.class, near, e -> true);
                check(shots.size() == 1 && shots.get(0).getOwner() == b, id + " shot once");
                check((shots.get(0) instanceof Arrow) == (Gods.byId(id).kind() == WeaponKind.BOW), id + " projectile type");
                // standing off within range and sight is not "stuck"
                Vec3 at = b.position();
                b.setTarget(target);
                for (int i = 0; i < 8; i++) b.keepUnstuck(level);
                check(b.position().distanceTo(at) < 0.1, id + " holds its ground while shooting");
                shots.forEach(Projectile::discard);
                b.discard();
            }
            GodBoss melee = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            melee.setGod(Gods.byId("ares"));
            check(!melee.fightsAtRange(), "ares fights in melee");
            melee.discard();
            GodBoss hades = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            hades.setGod(Gods.byId("hades"));
            hades.setPos(0.5, ay, 0.5);
            level.addFreshEntity(hades);
            hades.throwCard(level, target);
            List<RelicProjectile> cards = level.getEntitiesOfClass(RelicProjectile.class, near, e -> true);
            check(cards.size() == 1 && cards.get(0).getItem().getItem() instanceof com.thefarawaydev.realmsofchaos.item.TarotCardItem, "hades throws a tarot card");
            float hp = target.getHealth();
            cards.get(0).impact(level, hades, target);
            check(target.getHealth() < hp, "the card wounds (" + hp + " -> " + target.getHealth() + ")");
            check(!target.getActiveEffects().isEmpty(), "and lays a curse");
            cards.forEach(RelicProjectile::discard);
            hades.discard();
        });

        step("tarot: Hades' tower drops all 22 cards; every card casts", () -> {
            EnumSet<Tarot> dropped = EnumSet.noneOf(Tarot.class);
            for (God g : Gods.ALL) {
                List<Tarot> cards = Tarot.dropsFor(g);
                check(g.line() == Line.HADES || cards.isEmpty(), g.id() + " should not drop cards");
                check(g.line() != Line.HADES || !cards.isEmpty(), g.id() + " drops no card");
                for (Tarot t : cards) check(dropped.add(t), t.id() + " dropped twice");
            }
            check(dropped.size() == 22, "cards dropped: " + dropped.size());
            check(Tarot.dropsFor(Gods.byId("hades")).equals(List.of(Tarot.DEATH)), "Hades holds Death");

            ServerPlayer hero = fakePlayer(server, level, "RocSeer");
            for (Tarot card : Tarot.values()) {
                hero.setPos(0.5, ay, 0.5);
                hero.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.COBBLESTONE));
                LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                zombie.setPos(3.5, ay, 3.5);
                level.addFreshEntity(zombie);
                advance(server, 41);
                check(card.cast(level, hero), card.id() + " cast");
                zombie.discard();
                hero.removeAllEffects();
                hero.setDeltaMovement(Vec3.ZERO);
            }
            // tidy up what those casts left behind
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class, new AABB(-40, ay - 10, -40, 40, ay + 20, 40), e -> true)) e.discard();
            advance(server, 20 * 400);
            TarotEffects.service(hero, server.getTickCount());
        });

        step("tarot: each card does what the index says", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocReader");
            hero.setPos(0.5, ay, 0.5);

            // The Fool: iron from head to foot (the test world has no village or mineshaft to travel to)
            Tarot.FOOL.cast(level, hero);
            check(hero.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET)
                    && hero.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(net.minecraft.world.item.Items.IRON_BOOTS), "Fool: iron armour worn");
            check(count(hero, net.minecraft.world.item.Items.IRON_SWORD) == 1, "Fool: iron sword");

            // The Magician: strength II or III and twenty logs
            Tarot.MAGICIAN.cast(level, hero);
            check(count(hero, net.minecraft.world.item.Items.OAK_LOG) == 20, "Magician: 20 logs");
            var strength = hero.getEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
            check(strength != null && strength.getAmplifier() >= 1 && strength.getAmplifier() <= 2, "Magician: strength II-III");

            // The High Priestess: a bubble that pushes foes out and lets no harm in
            Tarot.HIGH_PRIESTESS.cast(level, hero);
            var resistance = hero.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
            check(resistance != null && resistance.getAmplifier() >= 4, "Priestess: bubble");
            float hpBefore = hero.getHealth();
            hero.hurtServer(level, level.damageSources().magic(), 6.0F);
            check(hero.getHealth() == hpBefore, "Priestess: no harm enters the bubble");
            hero.removeAllEffects();

            // The Empress: crops grow, and luck
            BlockPos soil = new BlockPos(3, (int) ay - 1, -4);
            BlockState before = level.getBlockState(soil);
            level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 2);
            Tarot.EMPRESS.cast(level, hero);
            check(!level.getBlockState(soil.above()).equals(Blocks.WHEAT.defaultBlockState()), "Empress: the wheat grew");
            check(hero.hasEffect(net.minecraft.world.effect.MobEffects.LUCK), "Empress: luck");
            level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(soil, before, 2);
            hero.removeAllEffects();

            // The Emperor: no knockback and more armour, for a while
            double armour = hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            Tarot.EMPEROR.cast(level, hero);
            check(hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "Emperor: no knockback");
            check(hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) >= armour + 8, "Emperor: armour");
            advance(server, 20 * 46);
            TarotEffects.service(hero, server.getTickCount());
            check(hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) == armour, "Emperor: wears off");

            Tarot.HIEROPHANT.cast(level, hero);
            check(hero.hasEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE), "Hierophant: hero of the village");

            // The Chariot: speed and step height, for a while
            double step = hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
            Tarot.CHARIOT.cast(level, hero);
            check(hero.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "Chariot: speed");
            check(hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) >= step + 1.0, "Chariot: step height");
            advance(server, 20 * 31);
            TarotEffects.service(hero, server.getTickCount());
            check(hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) == step, "Chariot: wears off");
            hero.removeAllEffects();

            Tarot.STRENGTH.cast(level, hero);
            check(hero.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH) && hero.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE), "Strength");
            hero.removeAllEffects();

            Tarot.WHEEL_OF_FORTUNE.cast(level, hero);
            check(hero.getEffect(net.minecraft.world.effect.MobEffects.LUCK).getAmplifier() >= 9, "Wheel: luck at its maximum");
            hero.removeAllEffects();

            // Justice reveals; The Hanged Man makes monsters lose interest
            net.minecraft.world.entity.Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            zombie.setPos(4.5, ay, 4.5);
            level.addFreshEntity(zombie);
            Tarot.JUSTICE.cast(level, hero);
            check(zombie.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Justice: revealed");
            zombie.setTarget(hero);
            check(zombie.getTarget() == hero, "test setup: zombie targets the player");
            Tarot.HANGED_MAN.cast(level, hero);
            check(zombie.getTarget() == null && TarotEffects.isUntargetable(hero), "Hanged Man: the zombie lets go");
            zombie.setTarget(hero);
            advance(server, 10);
            TarotEffects.service(hero, (server.getTickCount() / 10) * 10L);
            check(zombie.getTarget() == null, "Hanged Man: and stays away");
            advance(server, 20 * 31);
            TarotEffects.service(hero, server.getTickCount());
            check(!TarotEffects.isUntargetable(hero), "Hanged Man: wears off");
            zombie.discard();

            Tarot.DEATH.cast(level, hero);
            check(count(hero, net.minecraft.world.item.Items.TOTEM_OF_UNDYING) == 1, "Death: a totem of undying");

            hero.setHealth(3.0F);
            hero.getFoodData().setFoodLevel(4);
            Tarot.TEMPERANCE.cast(level, hero);
            check(hero.getHealth() == hero.getMaxHealth() && hero.getFoodData().getFoodLevel() == 20, "Temperance: full health and hunger");

            int xp = hero.totalExperience;
            Tarot.STAR.cast(level, hero);
            check(hero.totalExperience > xp && hero.hasEffect(net.minecraft.world.effect.MobEffects.LUCK), "Star: experience and luck");
            hero.removeAllEffects();

            // The Moon: five harmless illusions; slaying them all pays out
            int xpBeforeMoon = hero.totalExperience;
            Tarot.MOON.cast(level, hero);
            List<net.minecraft.world.entity.monster.Vex> illusions = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class,
                    new AABB(-12, ay - 4, -12, 12, ay + 10, 12), e -> e.entityTags().contains(TarotEffects.ILLUSION_TAG));
            check(illusions.size() == Tarot.ILLUSIONS && TarotEffects.illusionsLeft(hero) == Tarot.ILLUSIONS, "Moon: illusions summoned: " + illusions.size());
            check(illusions.get(0).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 0.0, "Moon: illusions cannot hurt");
            for (var vex : illusions) {
                GodWeaponItem.onKill(vex, level.damageSources().playerAttack(hero));
                vex.discard();
            }
            check(TarotEffects.illusionsLeft(hero) == 0 && hero.totalExperience > xpBeforeMoon, "Moon: reward for slaying them all");
            hero.removeAllEffects();

            // The Devil: refuses without an offering; with one, burns the inventory and turns pickups into it
            hero.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            int logs = count(hero, net.minecraft.world.item.Items.OAK_LOG);
            check(!Tarot.DEVIL.cast(level, hero) && count(hero, net.minecraft.world.item.Items.OAK_LOG) == logs, "Devil: refuses with an empty off hand");
            ItemStack cards = new ItemStack(ModContent.tarotItem(Tarot.DEVIL), 2);
            hero.setItemInHand(InteractionHand.MAIN_HAND, cards);
            cards.getItem().use(level, hero, InteractionHand.MAIN_HAND);
            check(hero.getMainHandItem().getCount() == 2, "Devil: a refused card is not spent");
            hero.getInventory().add(new ItemStack(ModContent.TAROT_DECK.get()));
            hero.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
            check(Tarot.DEVIL.cast(level, hero), "Devil: cast with an offering");
            check(count(hero, net.minecraft.world.item.Items.OAK_LOG) == 0 && count(hero, net.minecraft.world.item.Items.TOTEM_OF_UNDYING) == 0, "Devil: inventory burned");
            check(count(hero, ModContent.TAROT_DECK.get()) == 1, "Devil: the Deck of Fate is spared");
            check(hero.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET), "Devil: worn armour is spared");
            check(hero.getOffhandItem().is(net.minecraft.world.item.Items.DIAMOND), "Devil: the offering is kept");
            hero.getInventory().add(new ItemStack(Blocks.COBBLESTONE, 7));
            TarotEffects.service(hero, server.getTickCount());
            check(count(hero, net.minecraft.world.item.Items.DIAMOND) == 8 && count(hero, net.minecraft.world.item.Items.COBBLESTONE) == 0, "Devil: pickups become the chosen item");
            advance(server, 20 * 31);
            TarotEffects.service(hero, server.getTickCount());
            hero.getInventory().add(new ItemStack(Blocks.COBBLESTONE, 3));
            TarotEffects.service(hero, server.getTickCount());
            check(count(hero, net.minecraft.world.item.Items.COBBLESTONE) == 3 && !TarotEffects.devilActive(hero), "Devil: the bargain ends");

            // The Hermit: islands are private and furnished (the test server has no hermitage dimension to travel to)
            check(data.hermitIndex(hero.getUUID()) == data.hermitIndex(hero.getUUID()), "Hermit: a player keeps one island");
            int mine = data.hermitIndex(hero.getUUID());
            int other = data.hermitIndex(UUID.randomUUID());
            check(mine != other && Hermitage.cell(mine).distManhattan(Hermitage.cell(other)) >= Hermitage.SPACING, "Hermit: islands are far apart");
            int shift = 40;                                    // build one island somewhere empty in the test world
            Hermitage.buildCell(level, shift);
            BlockPos o = Hermitage.cell(shift);
            check(level.getBlockState(o.offset(0, 1, 5)).is(ModContent.GATE_LEAVE.get()), "Hermit: way home");
            check(level.getBlockState(o.offset(-5, 1, 0)).is(Blocks.CRAFTING_TABLE) && level.getBlockState(o.offset(5, 1, 0)).is(Blocks.CHEST), "Hermit: furnished");
            check(level.getBlockState(o.above()).isAir() && level.getBlockState(o.above(2)).isAir() && solid(level, o), "Hermit: arrival spot is free");
            check(solid(level, o.offset(7, 2, 0)) && level.getBlockState(o.offset(0, 6, 0)).is(Blocks.BARRIER), "Hermit: walled and roofed");
            Tarot.HERMIT.cast(level, hero);
        });

        step("tarot: The Tower picks another tower - the one whose relics you hold fewest of", () -> {
            // the tags: every relic is in its own tower's tag, in no other, and in the tag of all relics
            var all = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, RealmsOfChaos.id("relics"));
            for (God g : Gods.ALL) {
                ItemStack relic = new ItemStack(ModContent.weaponItem(g));
                for (Line line : Line.values()) {
                    check(relic.is(line.relicTag()) == (line == g.line()), g.id() + " weapon and the tag of " + line);
                }
                check(relic.is(all), g.id() + " weapon is tagged as a relic");
            }
            check(!new ItemStack(ModContent.TAROT_DECK.get()).is(all), "only relic weapons are tagged");

            ServerPlayer hero = fakePlayer(server, level, "RocPilgrim");
            hero.setPos(0.5, ay, 0.5);
            Realm.testAlias(Level.OVERWORLD, null);             // standing outside the realms: every tower is a candidate
            try {
                check(Realm.of(hero.level().dimension()) == null, "test setup: outside the realms");
                Set<Line> seen = EnumSet.noneOf(Line.class);
                for (int i = 0; i < 200; i++) seen.add(Tarot.towerFor(hero));
                check(seen.size() == 3, "with no relics, any tower: " + seen);
                hero.getInventory().add(new ItemStack(ModContent.weaponItem(Gods.byId("iris"))));
                hero.getInventory().add(new ItemStack(ModContent.weaponItem(Gods.byId("nike"))));
                hero.getInventory().add(new ItemStack(ModContent.weaponItem(Gods.byId("galatea"))));
                check(Tarot.relicsHeld(hero, Line.ZEUS) == 2 && Tarot.relicsHeld(hero, Line.POSEIDON) == 1 && Tarot.relicsHeld(hero, Line.HADES) == 0, "relics counted by tag");
                for (int i = 0; i < 50; i++) check(Tarot.towerFor(hero) == Line.HADES, "fewest relics: Hades");
                // the off hand counts too
                hero.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModContent.weaponItem(Gods.byId("charon"))));
                hero.getInventory().add(new ItemStack(ModContent.weaponItem(Gods.byId("oneiroi"))));
                check(Tarot.relicsHeld(hero, Line.HADES) == 2, "off hand counted");
                for (int i = 0; i < 50; i++) check(Tarot.towerFor(hero) == Line.POSEIDON, "fewest relics: Poseidon");

                // inside a tower, that tower is never chosen - even when it is the one with the fewest
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
                seen.clear();
                for (int i = 0; i < 200; i++) seen.add(Tarot.towerFor(hero));
                check(seen.equals(EnumSet.of(Line.ZEUS, Line.HADES)), "in Poseidon's tower with a Zeus/Hades tie: " + seen);
                hero.getInventory().add(new ItemStack(ModContent.weaponItem(Gods.byId("pan"))));
                for (int i = 0; i < 50; i++) check(Tarot.towerFor(hero) == Line.HADES, "in Poseidon's tower: Hades has fewer than Zeus");
                // (the journey itself needs a second tower's dimension, which the test server does not have)
            } finally {
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            }
        });

        step("tarot: single cards are spent; the Deck of Fate is unlimited and never deals The Devil blind", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocDealer");
            hero.setPos(0.5, ay, 0.5);
            ItemStack cards = new ItemStack(ModContent.tarotItem(Tarot.EMPRESS), 3);
            hero.setItemInHand(InteractionHand.MAIN_HAND, cards);
            cards.getItem().use(level, hero, InteractionHand.MAIN_HAND);
            check(hero.getMainHandItem().getCount() == 2, "a single card is spent, left " + hero.getMainHandItem().getCount());
            check(cards.getMaxStackSize() == 16, "cards stack");

            ItemStack deck = new ItemStack(ModContent.TAROT_DECK.get());
            hero.setItemInHand(InteractionHand.MAIN_HAND, deck);
            hero.getInventory().add(new ItemStack(Blocks.COBBLESTONE, 5));
            for (int i = 0; i < 60; i++) {
                deck.getItem().use(level, hero, InteractionHand.MAIN_HAND);
                check(hero.getMainHandItem().getCount() == 1 && hero.getCooldowns().isOnCooldown(deck), "the deck is not used up and rests");
                hero.getCooldowns().removeCooldown(RealmsOfChaos.id("tarot_deck"));
                advance(server, 41);
            }
            check(count(hero, net.minecraft.world.item.Items.COBBLESTONE) >= 5 && !TarotEffects.devilActive(hero), "sixty blind draws never dealt The Devil");
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class, new AABB(-40, ay - 10, -40, 40, ay + 20, 40), e -> true)) e.discard();
            hero.setPos(0.5, ay, 0.5);
            hero.removeAllEffects();

            DeckMenu.open(hero, InteractionHand.MAIN_HAND);
            check(hero.containerMenu instanceof DeckMenu, "deck menu opens");
            DeckMenu menu = (DeckMenu) hero.containerMenu;
            Set<Integer> slots = new HashSet<>();
            for (Tarot card : Tarot.values()) {
                check(slots.add(DeckMenu.slotOf(card)) && menu.getSlot(DeckMenu.slotOf(card)).getItem().is(ModContent.tarotItem(card)), "deck shows " + card.id());
            }
            hero.setHealth(4.0F);
            menu.clicked(DeckMenu.slotOf(Tarot.TEMPERANCE), 0, ContainerInput.PICKUP, hero);
            check(hero.getHealth() == hero.getMaxHealth() && !(hero.containerMenu instanceof DeckMenu), "choosing a card plays it and closes the deck");
            check(hero.getMainHandItem().getCount() == 1 && hero.getCooldowns().isOnCooldown(deck), "still one deck, resting");
        });

        step("voodoo dolls: bind to a creature, stab from afar, each pin does its harm, the bond breaks on death", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocWitch");
            hero.setPos(0.5, ay, 0.5);
            for (VoodooDollItem.Pin pin : VoodooDollItem.Pin.values()) {
                LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                zombie.setPos(12.5, ay, -9.5);                     // well out of reach, behind nothing in particular
                level.addFreshEntity(zombie);
                ItemStack doll = new ItemStack(ModContent.DOLLS.get(pin).get());
                hero.setItemInHand(InteractionHand.MAIN_HAND, doll);
                check(VoodooDollItem.boundTo(doll) == null, pin + ": new doll is unbound");
                doll.getItem().interactLivingEntity(doll, hero, zombie, InteractionHand.MAIN_HAND);
                ItemStack held = hero.getMainHandItem();
                check(zombie.getUUID().equals(VoodooDollItem.boundTo(held)), pin + ": bound");
                float hp = zombie.getHealth();
                held.getItem().use(level, hero, InteractionHand.MAIN_HAND);
                check(zombie.getHealth() < hp, pin + ": the stab is felt (" + hp + " -> " + zombie.getHealth() + ")");
                if (pin == VoodooDollItem.Pin.BINDING) check(zombie.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "binding slows");
                if (pin == VoodooDollItem.Pin.ROT) check(zombie.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "rot withers");
                if (pin == VoodooDollItem.Pin.EMBERS) check(zombie.getRemainingFireTicks() > 0, "embers burn");
                check(hero.getCooldowns().isOnCooldown(held), pin + ": cooldown");
                hero.getCooldowns().removeCooldown(RealmsOfChaos.id("voodoo_doll_" + pin.name().toLowerCase()));
                zombie.discard();
                held.getItem().use(level, hero, InteractionHand.MAIN_HAND);
                check(VoodooDollItem.boundTo(hero.getMainHandItem()) == null, pin + ": bond breaks when the creature is gone");
            }
            // a god shrugs off half of it
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.byId("ares"));
            b.setPos(6.5, ay, 6.5);
            level.addFreshEntity(b);
            float hp = b.getHealth();
            ((VoodooDollItem) ModContent.DOLLS.get(VoodooDollItem.Pin.PAIN).get()).stab(level, hero, b);
            float lost = hp - b.getHealth();
            check(lost > 0 && lost <= 4.01F, "god takes half: " + lost);
            b.discard();
            for (VoodooDollItem.Pin pin : VoodooDollItem.Pin.values()) {
                var key = ResourceKey.create(Registries.RECIPE, RealmsOfChaos.id("voodoo_doll_" + pin.name().toLowerCase()));
                check(server.getRecipeManager().byKey(key).isPresent(), "recipe for the doll of " + pin);
            }
        });

        step("sentinels: sixty-three kinds, twenty-one to a tower - every one sets up, fights, and does what its role says", () -> {
            Set<String> names = new HashSet<>();
            check(Sentinel.Kind.values().length == 63, "kinds: " + Sentinel.Kind.values().length);
            for (Line line : Line.values()) {
                for (Sentinel.Role role : Sentinel.Role.values()) check(Sentinel.Kind.of(line, role).line == line && Sentinel.Kind.of(line, role).role == role, "a " + role + " for " + line);
            }
            check(Sentinel.threat(1, 0) == 0.0 && Sentinel.threat(2, 0) == 1.0 && Sentinel.threat(1, 1) == 0.75 && Sentinel.threat(14, 2) == 14.5, "threat grows with the floor and with each level in");
            double high = Sentinel.threat(14, 2);
            ServerPlayer target = fakePlayer(server, level, "RocIntruder");
            AABB near = new AABB(-24, ay - 6, -24, 24, ay + 12, 24);
            level.addNewPlayer(target);
            try {
                for (Sentinel.Kind kind : Sentinel.Kind.values()) {
                    check(names.add(kind.id()), kind + ": id");
                    Sentinel weak = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED), strong = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                    weak.setUp(kind, 1);
                    strong.setUp(kind, 3, high, false);
                    check(weak.kind() == kind && weak.tier() == 1 && weak.threat() == 0.0 && strong.tier() == 3 && strong.threat() == high, kind + ": set up");
                    check(Math.abs(weak.getMaxHealth() - Sentinel.health(kind, 0)) < 0.01 && weak.getHealth() == weak.getMaxHealth(), kind + ": health");
                    check(strong.getMaxHealth() > weak.getMaxHealth() * 1.9 && strong.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                            > weak.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 1.6, kind + ": far stronger at the top of the tower");
                    check(!weak.getMainHandItem().isEmpty(), kind + ": carries something");
                    boolean ranged = kind.role == Sentinel.Role.CASTER || kind.role == Sentinel.Role.MYSTIC;
                    check(weak.fightsAtRange() == ranged, kind + ": casters and mystics keep their distance, the rest close in");
                    check((weak.move() != null) == (kind.role == Sentinel.Role.KEEPER || kind == Sentinel.Kind.SHADE || kind.trait == Sentinel.Trait.CHARGE), kind + ": only Keepers, Shades and chargers have a move");
                    check(!weak.isElite() && !weak.hasGlowingTag(), kind + ": ordinary by default");
                    // they are a danger to players, never to each other or to the gods
                    check(Power.canHurt(weak, target) && !Power.canHurt(weak, strong), kind + ": sentinels do not hurt each other");
                    if (kind.role == Sentinel.Role.SKIRMISHER) {
                        check(weak.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.34, kind + ": skirmishers are quick");
                    }

                    double sy = ground(level, arena, 0.5, 0.5);
                    strong.setPos(0.5, sy, 0.5);
                    level.addFreshEntity(strong);
                    target.setPos(3.5, ground(level, arena, 3.5, 3.5), 3.5);
                    target.setHealth(target.getMaxHealth());
                    target.removeAllEffects();
                    target.clearFire();
                    target.damageCooldownTime = 0;
                    target.setDeltaMovement(Vec3.ZERO);
                    strong.setTarget(target);
                    float before = target.getHealth();
                    if (ranged) {
                        strong.performRangedAttack(target, 1.0F);
                        if (kind.trait == Sentinel.Trait.SNIPE) {
                            // a sniper aims along a line of light first
                            check(level.getEntitiesOfClass(Projectile.class, near, e -> true).isEmpty(), kind + ": aims before it shoots");
                            advance(server, Sentinel.AIM_TICKS + 1);
                            Scheduler.tick(server);
                        }
                        List<Projectile> shots = level.getEntitiesOfClass(Projectile.class, near, e -> true);
                        check(shots.size() == 1 && shots.get(0).getOwner() == strong, kind + ": one shot");
                        check((shots.get(0) instanceof Arrow) == (kind == Sentinel.Kind.SKY_ARCHER), kind + ": archers loose arrows, the others bolts");
                        if (shots.get(0) instanceof RelicProjectile bolt) {
                            bolt.impact(level, strong, target);
                            check(target.getHealth() < before && !target.getActiveEffects().isEmpty(), kind + ": its bolt wounds and lays a curse");
                        }
                        shots.forEach(Projectile::discard);
                    } else {
                        check(strong.doHurtTarget(level, target) && target.getHealth() < before && target.isAlive(), kind + ": its blow lands, and one blow does not kill");
                        if (kind.role == Sentinel.Role.BRUTE) check(target.getDeltaMovement().horizontalDistance() > 0.8, kind + ": a brute's blow throws you back");
                        if (kind == Sentinel.Kind.FURY) check(target.getRemainingFireTicks() > 0, "a Fury's blow sets you alight");
                    }
                    if (weak.move() != null) {
                        target.setHealth(target.getMaxHealth());
                        target.damageCooldownTime = 0;
                        if (kind == Sentinel.Kind.SHADE) target.setPos(8.5, ground(level, arena, 8.5, 0.5), 0.5);
                        before = target.getHealth();
                        Vec3 from = strong.position();
                        check(strong.castMove(level), kind + ": makes its move");
                        advance(server, 45);
                        Scheduler.tick(server);
                        check(target.getHealth() < before && target.isAlive(), kind + ": the move reaches the player without killing them");
                        if (kind == Sentinel.Kind.SHADE) check(strong.position().distanceTo(from) > 3.0 && strong.position().distanceTo(target.position()) < 2.5, "a Shade steps behind you");
                        for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, near, e -> true)) bolt.discard();
                    }
                    // a mystic's rite
                    if (kind.role == Sentinel.Role.MYSTIC) {
                        if (kind != Sentinel.Kind.NECROMANCER) check(!strong.rite(level), kind + ": nothing to do with nobody near");
                        Sentinel ally = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                        ally.setUp(Sentinel.Kind.of(kind.line, Sentinel.Role.SOLDIER), 3, high, false);
                        ally.setPos(2.5, ground(level, arena, 2.5, 0.5), 0.5);
                        level.addFreshEntity(ally);
                        ally.setHealth(5.0F);
                        strong.addTag("realms_of_chaos_guardian/9/9");
                        ally.addTag("realms_of_chaos_guardian/9/9");
                        // (a reviver has nothing to do until someone has fallen)
                        if (kind.trait == Sentinel.Trait.REVIVE) ally.hurtServer(level, level.damageSources().playerAttack(target), 100000.0F);
                        check(strong.rite(level), kind + ": performs its rite");
                        if (kind.trait == Sentinel.Trait.BANNER) {
                            check(ally.hasEffect(kind.line == Line.ZEUS ? net.minecraft.world.effect.MobEffects.SPEED : kind.line == Line.POSEIDON ? net.minecraft.world.effect.MobEffects.RESISTANCE
                                    : net.minecraft.world.effect.MobEffects.STRENGTH), kind + ": its standard gives its tower's strength");
                            strong.hurtServer(level, level.damageSources().playerAttack(target), 100000.0F);
                            check(ally.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), kind + ": when the standard falls, those under it falter");
                        }
                        if (kind.trait == Sentinel.Trait.HORN) check(ally.getTarget() == target && ally.hasEffect(net.minecraft.world.effect.MobEffects.SPEED) && !strong.rite(level), kind + ": the alarm sets its allies on you, and then rests");
                        if (kind.trait == Sentinel.Trait.REVIVE) {
                            List<Sentinel> back = level.getEntitiesOfClass(Sentinel.class, near, e -> e.isAlive() && e.entityTags().contains(Sentinel.RAISED_TAG));
                            check(back.size() == 1 && back.get(0).kind() == ally.kind() && back.get(0).getHealth() < back.get(0).getMaxHealth() * 0.5F
                                    && back.get(0).entityTags().contains("realms_of_chaos_guardian/9/9"), kind + ": stands a fallen ally back up, hurt, in the same room: " + back.size());
                            check(!strong.rite(level), kind + ": once");
                            back.forEach(Sentinel::discard);
                            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, near, e -> true)) drop.discard();
                            for (var orb : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, near, e -> true)) orb.discard();
                        }
                        if (kind == Sentinel.Kind.STORM_AUGUR) check(ally.hasEffect(net.minecraft.world.effect.MobEffects.SPEED) && ally.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH), "the Storm Augur quickens its allies");
                        if (kind == Sentinel.Kind.TIDE_PRIEST) check(ally.getHealth() > 5.0F, "the Tide Priest mends its allies");
                        if (kind == Sentinel.Kind.NECROMANCER) {
                            strong.rite(level);
                            check(!strong.rite(level), "the Necromancer stops at two");
                            List<Sentinel> raised = level.getEntitiesOfClass(Sentinel.class, near, e -> e.entityTags().contains(Sentinel.RAISED_TAG));
                            check(raised.size() == 2 && raised.stream().allMatch(r -> r.kind() == Sentinel.Kind.SHADE && r.entityTags().contains("realms_of_chaos_guardian/9/9")),
                                    "the Necromancer raises Shades that belong to its room, two at most: " + raised.size());
                            check(raised.get(0).getMaxHealth() < Sentinel.health(Sentinel.Role.SOLDIER, high), "what it raises is weaker than the real thing");
                            raised.forEach(Sentinel::discard);
                        }
                        ally.discard();
                    }
                    // an elite: half as tough again, glowing, named for it
                    Sentinel elite = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                    elite.setUp(kind, 3, high, true);
                    check(elite.isElite() && elite.hasGlowingTag() && Math.abs(elite.getMaxHealth() - strong.getMaxHealth() * 1.5) < 0.01, kind + ": an elite is half as tough again and glows");
                    elite.discard();
                    // a Keeper always gives up a piece of the tier's forge material to the player who fells it
                    if (kind.role == Sentinel.Role.KEEPER) {
                        strong.hurtServer(level, level.damageSources().playerAttack(target), 100000.0F);
                        check(!strong.isAlive() && level.getEntitiesOfClass(ItemEntity.class, near, e -> e.getItem().is(ModContent.materialItem(kind.line, 3))).size() == 1,
                                kind + ": drops this tier's forge material");
                        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, near, e -> true)) drop.discard();
                        for (var orb : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, near, e -> true)) orb.discard();
                    }
                    strong.discard();
                    weak.discard();
                }
            } finally {
                target.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                for (var chunk : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true)) chunk.discard();
            }
            // who a room holds: more kinds the more dangerous the level, an elite on the last, more for a group
            Passages.Plan gentle = Passages.plan(level, Line.ZEUS, 1, 0), deep = Passages.plan(level, Line.HADES, 12, Passages.stages(level, Line.HADES, 12) - 1), mid = Passages.plan(level, Line.HADES, 12, 0);
            check(Passages.roles(gentle).equals(List.of(Sentinel.Role.SOLDIER, Sentinel.Role.CASTER)), "the first passage has only soldiers and casters");
            check(Passages.roles(Passages.plan(level, Line.ZEUS, 3, 0)).contains(Sentinel.Role.SKIRMISHER) && !Passages.roles(Passages.plan(level, Line.ZEUS, 3, 0)).contains(Sentinel.Role.BRUTE), "skirmishers come first");
            check(Passages.roles(deep).size() == 5, "everything comes at the top of the tower");
            Passages.Cell chamber = mid.seals.get(0);
            check(Passages.garrison(mid, chamber, 1).size() == Passages.guardianCount(mid) && Passages.guardianCount(mid) == 5, "a seal's garrison for one player");
            check(Passages.garrison(mid, chamber, 2).size() == Passages.guardianCount(mid) + 1 && Passages.garrison(mid, chamber, 9).size() == Passages.guardianCount(mid) + 2,
                    "more wake for a group, up to two extra");
            check(Passages.garrison(mid, chamber, 1).stream().filter(k -> k.kind().role == Sentinel.Role.KEEPER).count() == 1, "one Keeper");
            check(Passages.garrison(mid, chamber, 1).stream().noneMatch(Passages.Post::elite), "no elite before the last level");
            check(Passages.guardianCount(deep) == 8 && Passages.garrison(deep, deep.seals.get(0), 1).stream().filter(Passages.Post::elite).count() == 1, "the last level: a bigger guard with an elite in it");
            check(Passages.garrison(deep, deep.seals.get(0), 1).stream().filter(k -> k.kind().role == Sentinel.Role.MYSTIC).count() <= 1, "one mystic to a room at most");
            check(Passages.garrison(gentle, gentle.seals.get(0), 1).stream().noneMatch(k -> k.kind().role == Sentinel.Role.BRUTE), "no brutes on the lowest floors");
        });

        step("custom screens: the Codex, the handbook, the chronicle, the altar and the party are real screens, and their buttons work", () -> {
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
            ServerPlayer hero = fakePlayer(server, level, "RocScreenReader");
            data.reset(hero.getUUID());
            hero.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
            Access.attune(hero);
            List<net.minecraft.server.dialog.Dialog> screens = new ArrayList<>();
            screens.add(Screens.home(hero));
            screens.add(Screens.handbook(hero));
            for (HandbookData.Topic topic : HandbookData.ALL) screens.add(Screens.topic(hero, topic));
            screens.add(Screens.chronicle(hero));
            for (Story.Chapter chapter : Story.Chapter.values()) screens.add(Screens.chapter(hero, chapter));
            screens.add(Screens.altar(hero, level, Line.ZEUS, Tower.altarPos(1)));
            screens.add(Screens.party(hero));
            for (net.minecraft.server.dialog.Dialog screen : screens) {
                // the game's own codec is what the client reads a screen with: if it encodes and reads back, it is a valid screen
                var json = net.minecraft.server.dialog.Dialog.DIRECT_CODEC.encodeStart(ops, screen);
                check(json.result().isPresent(), "a screen does not encode: " + json.error().map(e -> e.message()).orElse("?"));
                var back = net.minecraft.server.dialog.Dialog.DIRECT_CODEC.parse(ops, json.result().get());
                check(back.result().isPresent(), "a screen does not read back: " + back.error().map(e -> e.message()).orElse("?"));
                check(Screens.bodyLines(screen) >= 1, "a screen with nothing on it");
                for (String command : Screens.commands(screen)) check(command.startsWith("/roc"), "a button that does not run a /roc command: " + command);
            }
            check(HandbookData.ALL.size() == 19, "handbook topics: " + HandbookData.ALL.size());

            // the Codex item opens its home screen; every one of its buttons does something
            ModContent.CHAOS_CODEX.get().use(level, hero, InteractionHand.MAIN_HAND);
            net.minecraft.server.dialog.Dialog home = Screens.last(hero);
            check(home != null && Screens.commands(home).containsAll(List.of("/roc ui towers", "/roc ui chronicle", "/roc ui handbook", "/roc ui party", "/roc forge", "/roc hub", "/roc leave")),
                    "the Codex opens its home screen: " + (home == null ? "nothing" : Screens.commands(home)));
            var commands = server.getCommands();
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui handbook");
            check(Screens.commands(Screens.last(hero)).contains("/roc ui topic start") && Screens.commands(Screens.last(hero)).size() == HandbookData.ALL.size() + 1, "the handbook lists its topics");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui topic passages");
            check(Screens.bodyLines(Screens.last(hero)) == Screens.topicById("passages").lines() && Screens.commands(Screens.last(hero)).contains("/roc ui topic sentinels"),
                    "a topic shows its text and leads on to the next");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui chronicle");
            check(Screens.commands(Screens.last(hero)).contains("/roc ui chapter prologue") && !Screens.commands(Screens.last(hero)).contains("/roc ui chapter zeus_fall"),
                    "the chronicle offers the chapters written so far and no others");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui chapter sundering");
            check(Screens.bodyLines(Screens.last(hero)) == Story.Chapter.SUNDERING.pages(), "a chapter is read page by page");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui take sundering");
            check(Story.holds(hero, Story.Chapter.SUNDERING), "and can be taken away as a book");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui chapter zeus_fall");
            check(!Story.holds(hero, Story.Chapter.ZEUS_FALL) && Screens.commands(Screens.last(hero)).contains("/roc ui chapter prologue"), "an unwritten chapter cannot be opened: back to the list");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui take zeus_fall");
            check(!Story.holds(hero, Story.Chapter.ZEUS_FALL), "nor taken");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui towers");
            check(hero.containerMenu instanceof CodexMenu, "the towers button opens the floor plans");
            hero.closeContainer();
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc");
            check(Screens.commands(Screens.last(hero)).contains("/roc ui handbook"), "/roc opens the Codex");

            // the Trial Altar: right-clicking it opens its screen, and the screen's button begins the trial
            clearBosses(level);
            BlockPos altar = Tower.altarPos(1);
            TowerBuilder.ensureFloor(level, Line.ZEUS, 1);
            level.setBlock(altar, ModContent.ALTAR_ZEUS.get().defaultBlockState(), 2);       // (the test's three towers share one place: make this Zeus's altar)
            level.getBlockState(altar).useWithoutItem(level, hero, new BlockHitResult(Vec3.atCenterOf(altar), net.minecraft.core.Direction.UP, altar, false));
            String begin = "/roc ui altar " + altar.getX() + " " + altar.getY() + " " + altar.getZ() + " begin";
            check(Screens.commands(Screens.last(hero)).equals(List.of(begin)), "the altar's screen offers to begin: " + Screens.commands(Screens.last(hero)));
            check(Screens.bodyLines(Screens.last(hero)) == 8, "who waits, where, its move, its drops, the rules, what it makes of your arms, its phases, and the state: " + Screens.bodyLines(Screens.last(hero)));
            int y0 = Tower.floorY(1);
            AABB room = new AABB(-24, y0 - 3, -24, 24, y0 + 17, 24);
            hero.setPos(500, 100, 500);
            commands.performPrefixedCommand(hero.createCommandSourceStack(), begin);
            check(level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive).isEmpty(), "the button does nothing for a player who is not at the altar");
            hero.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui altar 3 " + altar.getY() + " 3 begin");
            check(level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive).isEmpty(), "nor for a place that is not an altar");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), begin);
            List<GodBoss> risen = level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive);
            check(risen.size() == 1 && risen.get(0).god() == Tower.god(Line.ZEUS, 1), "the button begins the trial: " + risen.size() + " rose, altar state "
                    + AltarBlock.state(level, hero, altar, Line.ZEUS) + ", block " + level.getBlockState(altar).getBlock() + ", hero at " + hero.position() + ", command " + begin);
            check(Screens.commands(Screens.altar(hero, level, Line.ZEUS, altar)).isEmpty(), "while a trial is underway the screen has nothing to press");
            slay(server, level, risen.get(0), hero);
            List<String> after = Screens.commands(Screens.altar(hero, level, Line.ZEUS, altar));
            check(after.size() == 2 && after.get(0).endsWith(" ascend") && after.get(1).endsWith(" rematch"), "once the god has fallen: go up, or a rematch: " + after);
            commands.performPrefixedCommand(hero.createCommandSourceStack(), after.get(1));
            check(level.getEntitiesOfClass(GodBoss.class, room, GodBoss::isAlive).size() == 1, "the rematch button calls the god back");
            clearBosses(level);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
            for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, room, e -> true)) bolt.discard();

            // the party screen
            Parties.reset();
            ServerPlayer friend = fakePlayer(server, level, "RocScreenFriend");
            Parties.testPlayer(hero);
            Parties.testPlayer(friend);
            Parties.invite(friend, hero);
            check(Screens.commands(Screens.party(hero)).containsAll(List.of("/roc ui party accept", "/roc ui party decline")), "an invitation shows with accept and decline");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui party accept");
            check(Parties.inParty(hero.getUUID()) && Screens.commands(Screens.last(hero)).contains("/roc ui party leave") && Screens.bodyLines(Screens.last(hero)) == 1 + 2 + 4, "accepting joins; the screen lists both members");
            commands.performPrefixedCommand(hero.createCommandSourceStack(), "/roc ui party leave");
            check(!Parties.inParty(hero.getUUID()), "and leave leaves");
            Parties.reset();
            data.reset(hero.getUUID());
        });

        step("tutorial: the handbook opens for a new player, and each tip shows once", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocBeginner");
            Tutorial.reset(hero);
            for (Tutorial.Tip tip : Tutorial.Tip.values()) {
                check(!Tutorial.seen(hero, tip), tip + ": not seen yet");
                check(Tutorial.tip(hero, tip) && Tutorial.seen(hero, tip), tip + ": shown the first time");
                check(!Tutorial.tip(hero, tip), tip + ": and only once");
            }
            advance(server, Tutorial.DELAY + 5);
            Scheduler.tick(server);
            check(Scheduler.pending() == 0, "tips are delivered");
            // tips come from what the player does: arriving on a floor, opening the forge
            ServerPlayer walker = fakePlayer(server, level, "RocFirstSteps");
            Tutorial.reset(walker);
            advance(server, 41);
            Teleports.toFloor(walker, Line.ZEUS, 1);
            check(Tutorial.seen(walker, Tutorial.Tip.FLOOR) && !Tutorial.seen(walker, Tutorial.Tip.FORGE), "arriving on a floor for the first time brings its tip");
            ForgeMenu.open(walker);
            check(Tutorial.seen(walker, Tutorial.Tip.FORGE), "and so does the first look at the forge");
            walker.closeContainer();
            // the welcome: the handbook's first topic, a few seconds after the first join
            ServerPlayer fresh = fakePlayer(server, level, "RocJustJoined");
            Tutorial.welcome(fresh);
            check(Screens.last(fresh) == null, "not at once");
            advance(server, 100);
            Scheduler.tick(server);
            check(Screens.last(fresh) != null && Screens.bodyLines(Screens.last(fresh)) == HandbookData.ALL.get(0).lines()
                    && Screens.commands(Screens.last(fresh)).contains("/roc ui topic " + HandbookData.ALL.get(1).id()), "the handbook opens on its first topic and leads on");
        });

        step("impacts: the floor throws chunks of itself up, and nothing is left behind", () -> {
            Vec3 at = Tower.centre(level, arena);
            AABB near = new AABB(at.x - 12, at.y - 4, at.z - 12, at.x + 12, at.y + 14, at.z + 12);
            for (var chunk : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true)) chunk.discard();
            BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
            BlockState floorBlock = level.getBlockState(under);
            check(Fx.ground(level, BlockPos.containing(at)).equals(floorBlock), "an impact knows what the floor is made of");
            // every block round the spot, before
            java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
            for (BlockPos b : BlockPos.betweenClosed(under.offset(-3, -1, -3), under.offset(3, 4, 3))) before.put(b.immutable(), level.getBlockState(b));
            int thrown = Fx.impact(level, at, 8);
            List<net.minecraft.world.entity.item.FallingBlockEntity> chunks = level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true);
            check(thrown > 0 && thrown <= Fx.MAX_CHUNKS && chunks.size() == thrown, "chunks fly: " + thrown);
            for (var chunk : chunks) {
                check(chunk.getBlockState().equals(floorBlock) && chunk.getDeltaMovement().y > 0.3 && !chunk.dropItem, "a chunk of the floor, thrown upward, that will drop nothing");
            }
            for (var e : before.entrySet()) check(level.getBlockState(e.getKey()).equals(e.getValue()), "an impact changed the block at " + e.getKey());
            check(Fx.impact(level, at, 1) == 1 || Fx.impact(level, at, 1) == 0, "a light impact throws one chunk at most");
            // a landed chunk vanishes rather than becoming a block: that is what "disableDrop" promises, so check the flag made it into the save
            var saved = new net.minecraft.nbt.CompoundTag();
            chunks.get(0).saveWithoutId(net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess()));
            for (var chunk : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true)) chunk.discard();
        });

        step("gods: the floor erupts under you once a god is enraged, and at a fifth of its health it makes a last stand", () -> {
            ServerPlayer target = fakePlayer(server, level, "RocStandingStill");
            Vec3 home = Tower.centre(level, 5);
            AABB near = new AABB(-24, home.y - 4, -24, 24, home.y + 14, 24);
            for (var chunk : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true)) chunk.discard();
            level.addNewPlayer(target);
            try {
                GodBoss god = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                god.setGod(Gods.byId("ares"));
                god.setPos(home.x, home.y, home.z);
                god.setHome(home);
                level.addFreshEntity(god);
                god.setTarget(target);
                // the eruption: a warning first, then the ground goes up where the player was standing
                target.setPos(home.x + 6.0, ground(level, 5, home.x + 6.0, home.z), home.z);
                target.setHealth(target.getMaxHealth());
                target.damageCooldownTime = 0;
                target.setDeltaMovement(Vec3.ZERO);
                settle(level);
                float before = target.getHealth();
                check(god.upheaval(level) == 1, "the floor marks where each challenger stands");
                check(target.getHealth() == before && level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true).isEmpty(), "first only a warning");
                advance(server, GodBoss.UPHEAVAL_WARNING + 2);
                Scheduler.tick(server);
                check(target.getHealth() < before && target.getDeltaMovement().y > 0.8, "then it erupts: the player is hurt and thrown into the air");
                check(!level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true).isEmpty(), "and the floor flies up with them");
                // stepping away is all it takes
                Vec3 dodge = new Vec3(home.x - 6.0, ground(level, 5, home.x - 6.0, home.z), home.z);
                target.setHealth(target.getMaxHealth());
                target.damageCooldownTime = 0;
                target.setDeltaMovement(Vec3.ZERO);
                god.upheaval(level);
                target.setPos(dodge.x, dodge.y, dodge.z);
                before = target.getHealth();
                advance(server, GodBoss.UPHEAVAL_WARNING + 2);
                Scheduler.tick(server);
                check(target.getHealth() == before, "a player who moved off the mark is untouched");

                // the last stand
                check(!god.madeLastStand(), "not yet");
                god.lastStand(level);
                check(god.madeLastStand() && god.isInvulnerable(), "for a moment nothing can touch it");
                float hp = god.getHealth();
                check(!god.hurtServer(level, level.damageSources().playerAttack(target), 50.0F) && god.getHealth() == hp, "not even a blow");
                List<Sentinel> called = level.getEntitiesOfClass(Sentinel.class, near, e -> e.entityTags().contains("realms_of_chaos_minion"));
                check(called.size() == 2 && called.stream().allMatch(c -> c.kind().line == Line.ZEUS && level.noCollision(c)), "two of its tower's sentinels answer the call: " + called.size());
                target.setPos(home.x + 3.0, ground(level, 5, home.x + 3.0, home.z + 3.0), home.z + 3.0);
                target.setHealth(target.getMaxHealth());
                target.damageCooldownTime = 0;
                before = target.getHealth();
                advance(server, 90);
                Scheduler.tick(server);
                check(!god.isInvulnerable(), "then it can be hurt again");
                check(target.getHealth() < before && target.isAlive(), "and it has thrown everything it has: " + before + " -> " + target.getHealth());
                god.lastStand(level);
                check(level.getEntitiesOfClass(Sentinel.class, near, e -> e.entityTags().contains("realms_of_chaos_minion")).size() == 2, "a last stand is made once");
                // when the god falls, the sentinels it called go with it
                slay(server, level, god, target);
                check(!god.isAlive() && level.getEntitiesOfClass(Sentinel.class, near, e -> e.isAlive() && e.entityTags().contains("realms_of_chaos_minion")).isEmpty(), "its sentinels fall with it");
            } finally {
                target.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                clearBosses(level);
                for (var chunk : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, near, e -> true)) chunk.discard();
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, near, e -> true)) drop.discard();
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, near, e -> true)) bolt.discard();
                for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, near, e -> e.entityTags().contains("realms_of_chaos_minion"))) m.discard();
            }
        });

        step("the walls hold: inside the realms a survival player cannot break or place blocks", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocMiner");
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            check(hero.gameMode() == GameType.SURVIVAL && !Wards.warded(hero), "test setup: a survival player");
            Wards.service(hero);
            check(hero.gameMode() == GameType.ADVENTURE && Wards.warded(hero), "in a tower: adventure mode, so no digging through the walls");
            check(!hero.mayBuild(), "and no building");
            Wards.service(hero);
            check(hero.gameMode() == GameType.ADVENTURE, "it stays that way");
            Realm.testAlias(Level.OVERWORLD, null);
            try {
                Wards.service(hero);
                check(hero.gameMode() == GameType.SURVIVAL && !Wards.warded(hero) && hero.mayBuild(), "outside again: survival is given back");
                // a player who chose adventure mode themselves is left in it
                hero.setGameMode(GameType.ADVENTURE);
                Wards.service(hero);
                check(hero.gameMode() == GameType.ADVENTURE, "adventure mode chosen outside the realms is not touched");
                // creative builders are never restricted
                hero.setGameMode(GameType.CREATIVE);
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
                Wards.service(hero);
                check(hero.gameMode() == GameType.CREATIVE && !Wards.warded(hero), "creative players keep creative mode inside");
            } finally {
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            }
            // every journey sets the mode at once, without waiting for the once-a-second check
            ServerPlayer traveller = fakePlayer(server, level, "RocTraveller");
            advance(server, 41);
            Teleports.toFloor(traveller, Line.ZEUS, 1);
            check(traveller.gameMode() == GameType.ADVENTURE, "arriving on a floor wards the player at once");
        });

        step("parties: climb together on the highest member's floor, each with their own progress", () -> {
            Parties.reset();
            ServerPlayer low = fakePlayer(server, level, "RocLow"), high = fakePlayer(server, level, "RocHigh"), third = fakePlayer(server, level, "RocThird");
            for (ServerPlayer pl : new ServerPlayer[]{low, high, third}) {
                Parties.testPlayer(pl);
                data.reset(pl.getUUID());
            }
            try {
                // "high" has done three floors and their passages; "low" is new
                for (int f = 1; f <= 3; f++) {
                    data.markDefeated(high.getUUID(), Tower.god(Line.ZEUS, f));
                    data.markPassed(high.getUUID(), Line.ZEUS, f);
                }
                check(Tower.currentFloor(data, high.getUUID(), Line.ZEUS) == 4 && Tower.currentFloor(data, low.getUUID(), Line.ZEUS) == 1, "test setup: floors 4 and 1");
                check(Parties.floor(low, Line.ZEUS) == 1 && !Teleports.canTravel(low, Line.ZEUS, 4), "alone, you are on your own floor");
                check(AltarBlock.state(level, low, Tower.altarPos(4), Line.ZEUS) == AltarBlock.State.LOCKED, "and a higher floor's trial is closed to you");

                // inviting and accepting
                check(!Parties.accept(low), "nothing to accept without an invitation");
                check(!Parties.invite(high, high), "you cannot invite yourself");
                check(Parties.invite(high, low) && high.getUUID().equals(Parties.invitedBy(low.getUUID())) && !Parties.inParty(low.getUUID()), "an invitation waits to be accepted");
                check(Parties.accept(low) && Parties.inParty(low.getUUID()) && Parties.inParty(high.getUUID()), "accepting makes the party");
                check(Parties.members(low.getUUID()).equals(Parties.members(high.getUUID())) && Parties.members(low.getUUID()).size() == 2, "both see the same party");
                check(!Parties.invite(high, low), "nobody is invited twice");
                check(Parties.online(low).size() == 2 && Parties.online(low).get(0) == low, "members are found, yourself first");

                // the highest member's floor is the party's floor
                check(Parties.floor(low, Line.ZEUS) == 4 && Parties.floor(high, Line.ZEUS) == 4, "the party's floor is its highest member's");
                check(Parties.floor(low, Line.POSEIDON) == 1, "tower by tower");
                check(Teleports.canTravel(low, Line.ZEUS, 4) && !Teleports.canTravel(low, Line.ZEUS, 5), "the party can go as far as its highest member, no further");
                advance(server, 41);
                Teleports.enterTower(low, Line.ZEUS);
                check(Tower.floorAt(low.getY()) == 4 && low.position().distanceTo(Tower.arrival(4)) < 0.1, "entering as a party arrives on the party's floor");
                check(AltarBlock.state(level, low, Tower.altarPos(4), Line.ZEUS) == AltarBlock.State.READY, "where anyone in the party may begin the trial");

                // progress stays each player's own
                check(Tower.currentFloor(data, low.getUUID(), Line.ZEUS) == 1, "being carried up does not move your own floor");
                advance(server, 41);
                Teleports.ascend(low);
                check(Tower.floorAt(low.getY()) == 4 && Passages.locate(low) == null, "you cannot go up past a floor whose god you have not beaten");
                data.markDefeated(low.getUUID(), Tower.god(Line.ZEUS, 4));                 // beaten together
                check(!Tower.canStand(data, low.getUUID(), Line.ZEUS, 5), "beating the god is not enough: the passage is still to walk");
                data.markPassed(low.getUUID(), Line.ZEUS, 4);
                check(Tower.canStand(data, low.getUUID(), Line.ZEUS, 5), "a floor's god beaten and its passage walked: the next floor is yours");
                check(Tower.currentFloor(data, low.getUUID(), Line.ZEUS) == 1, "while the floors you skipped are still yours to do");

                // leaving: on your own again, but what you earned you keep
                check(Parties.leave(low) && !Parties.inParty(low.getUUID()) && !Parties.inParty(high.getUUID()), "a party of one is no party");
                check(!Teleports.canTravel(low, Line.ZEUS, 4) && Teleports.canTravel(low, Line.ZEUS, 5) && Teleports.canTravel(low, Line.ZEUS, 1), "alone: your own floor, and the one you earned");
                advance(server, 41);
                Teleports.toFloor(low, Line.ZEUS, 4);
                check(Tower.floorAt(low.getY()) == 1, "a floor you have no right to sends you to your own");
                check(!Parties.leave(low), "nothing to leave");

                // up to four, and the party outlives its founder
                Parties.invite(high, low);
                Parties.accept(low);
                Parties.invite(low, third);
                check(Parties.accept(third) && Parties.members(high.getUUID()).size() == 3, "a member can invite too");
                Parties.leave(high);
                check(Parties.inParty(low.getUUID()) && Parties.members(low.getUUID()).equals(Set.of(low.getUUID(), third.getUUID())), "the party goes on when its founder leaves");
                check(Parties.floor(low, Line.ZEUS) == 1, "and its floor is now the highest of those left");

                // the party screen, from the Codex
                CodexMenu.open(low);
                ((CodexMenu) low.containerMenu).clicked(CodexMenu.SLOT_PARTY, 0, ContainerInput.PICKUP, low);
                check(low.containerMenu instanceof com.thefarawaydev.realmsofchaos.ui.PartyMenu, "the Codex opens the party screen");
                var screen = (com.thefarawaydev.realmsofchaos.ui.PartyMenu) low.containerMenu;
                check(screen.getSlot(com.thefarawaydev.realmsofchaos.ui.PartyMenu.memberSlot(0)).getItem().is(net.minecraft.world.item.Items.PLAYER_HEAD)
                        && screen.getSlot(com.thefarawaydev.realmsofchaos.ui.PartyMenu.memberSlot(1)).getItem().is(net.minecraft.world.item.Items.PLAYER_HEAD), "members shown");
                screen.clicked(com.thefarawaydev.realmsofchaos.ui.PartyMenu.SLOT_LEAVE, 0, ContainerInput.PICKUP, low);
                check(!Parties.inParty(low.getUUID()), "leave from the screen");
                low.closeContainer();
            } finally {
                Parties.reset();
                for (ServerPlayer pl : new ServerPlayer[]{low, high, third}) data.reset(pl.getUUID());
            }
        });

        step("the tower adapts: harder for a group, gentler after defeats, and everyone gets their own loot", () -> {
            check(Adapt.healthScale(1) == 1.0 && Math.abs(Adapt.healthScale(3) - 2.2) < 1e-9 && Adapt.healthScale(9) == Adapt.healthScale(4), "health grows with the group, to four");
            check(Adapt.damageScale(1) == 1.0F && Adapt.damageScale(2) > 1.1F, "so does how hard the god hits");
            check(Adapt.mercyScale(0) == 1.0 && Math.abs(Adapt.mercyScale(2) - 0.84) < 1e-9 && Adapt.mercyScale(9) == Adapt.mercyScale(3), "three defeats is as gentle as it gets");

            ServerPlayer one = fakePlayer(server, level, "RocFirstOfTwo"), two = fakePlayer(server, level, "RocSecondOfTwo");
            data.reset(one.getUUID());
            data.reset(two.getUUID());
            God god = Tower.god(Line.ZEUS, 1);
            BlockPos altar = Tower.altarPos(1);
            int y0 = Tower.floorY(1);
            AABB room = new AABB(-24, y0 - 3, -24, 24, y0 + 17, 24);
            clearBosses(level);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
            level.addNewPlayer(one);
            level.addNewPlayer(two);
            try {
                one.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
                two.setPos(Tower.arrival(1).x + 1, Tower.arrival(1).y, Tower.arrival(1).z);
                settle(level);
                // two on the floor: a harder god
                GodBoss boss = AltarBlock.begin(level, one, altar, Line.ZEUS);
                check(boss != null && boss.challengers() == 2, "the trial counts its challengers: " + (boss == null ? "none" : boss.challengers()));
                check(Math.abs(boss.getMaxHealth() - base(level, god) * 1.6) < 0.01 && boss.getHealth() == boss.getMaxHealth(), "two challengers: 60% more health");
                check(Math.abs(boss.damageScale() - 1.15F) < 1e-4, "and 15% harder hits");
                // it fells one of them: that is remembered
                check(Adapt.losses(two.getUUID(), god) == 0, "no losses yet");
                boss.killedEntity(level, two, level.damageSources().mobAttack(boss));
                check(Adapt.losses(two.getUUID(), god) == 1 && Adapt.losses(one.getUUID(), god) == 0, "a defeat is remembered for the player who fell");

                // both win: both get credit, and each their own loot that only they can pick up
                slay(server, level, boss, one);
                check(!boss.isAlive() && data.isDefeated(one.getUUID(), god) && data.isDefeated(two.getUUID(), god), "everyone on the floor shares the victory");
                check(Adapt.losses(two.getUUID(), god) == 0, "a victory wipes the slate");
                List<ItemEntity> relics = level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.weaponItem(god)));
                check(relics.size() == 2, "a relic for each victor: " + relics.size());
                int taken = 0;
                for (ItemEntity relic : relics) {
                    relic.setNoPickUpDelay();
                    int before = count(one, ModContent.weaponItem(god));
                    relic.playerTouch(one);
                    if (count(one, ModContent.weaponItem(god)) > before) taken++;
                }
                check(taken == 1 && count(one, ModContent.weaponItem(god)) == 1, "a player can only pick up their own: took " + taken);
                for (ItemEntity relic : level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.weaponItem(god)))) relic.playerTouch(two);
                check(count(two, ModContent.weaponItem(god)) == 1, "and the other takes theirs");
                check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.materialItem(Line.ZEUS, 1))).size() == 2, "forge material for each as well");
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();

                // helping a friend through a floor you have already done: they get the relic, you get materials
                ServerPlayer helper = one;
                ServerPlayer learner = fakePlayer(server, level, "RocLearner");
                data.reset(learner.getUUID());
                level.addNewPlayer(learner);
                try {
                    learner.setPos(Tower.arrival(1).x - 1, Tower.arrival(1).y, Tower.arrival(1).z);
                    two.setPos(900, 300, 900);                              // the second player has gone
                    settle(level);
                    check(AltarBlock.state(level, learner, altar, Line.ZEUS) == AltarBlock.State.READY, "the friend's own trial is still theirs to begin");
                    GodBoss again = AltarBlock.begin(level, learner, altar, Line.ZEUS);
                    check(again != null && again.challengers() == 2, "helper and friend face it together");
                    slay(server, level, again, helper);
                    check(data.isDefeated(learner.getUUID(), god), "the friend is credited though the helper struck the last blow");
                    check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.weaponItem(god))).size() == 1, "one relic: the friend's first");
                    check(level.getEntitiesOfClass(ItemEntity.class, room, e -> e.getItem().is(ModContent.materialItem(Line.ZEUS, 1))).size() == 2, "materials for both");
                    for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
                } finally {
                    learner.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    data.reset(learner.getUUID());
                }

                // mercy: a god that has beaten you twice comes back weaker; alone, at its usual strength otherwise
                data.reset(one.getUUID());
                Adapt.recordLoss(one.getUUID(), god);
                Adapt.recordLoss(one.getUUID(), god);
                settle(level);
                GodBoss gentler = AltarBlock.begin(level, one, altar, Line.ZEUS);
                check(gentler != null && gentler.challengers() == 1 && gentler.mercy() == 2, "the tower relents after two defeats");
                check(Math.abs(gentler.getMaxHealth() - base(level, god) * 0.84) < 0.01, "16% less health");
                gentler.discard();
                Adapt.clearLosses(one.getUUID(), god);
            } finally {
                one.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                two.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                data.reset(one.getUUID());
                data.reset(two.getUUID());
                clearBosses(level);
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, room, e -> true)) bolt.discard();
            }
        });

        step("the way in: ruins lie across the Overworld at places fixed by the seed", () -> {
            long seed = level.getSeed();
            java.util.Random random = new java.util.Random(7);
            for (Ruins.Kind kind : Ruins.Kind.values()) {
                for (int i = 0; i < 40; i++) {
                    int rx = random.nextInt(41) - 20, rz = random.nextInt(41) - 20;
                    Ruins.Site a = Ruins.site(seed, kind, rx, rz);
                    check(a.equals(Ruins.site(seed, kind, rx, rz)), "a ruin stays where it is");
                    check(a.x() >= rx * kind.spacing + 96 && a.x() < (rx + 1) * kind.spacing - 96 && a.z() >= rz * kind.spacing + 96 && a.z() < (rz + 1) * kind.spacing - 96,
                            "a ruin lies inside its own square, clear of the edges");
                }
                // the nearest ruin really is the nearest, and never far
                for (int i = 0; i < 60; i++) {
                    double x = random.nextInt(40000) - 20000, z = random.nextInt(40000) - 20000;
                    Ruins.Site near = Ruins.nearest(seed, kind, x, z);
                    double best = Double.MAX_VALUE;
                    int rx = Math.floorDiv((int) x, kind.spacing), rz = Math.floorDiv((int) z, kind.spacing);
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            Ruins.Site o = Ruins.site(seed, kind, rx + dx, rz + dz);
                            best = Math.min(best, Math.hypot(o.x() - x, o.z() - z));
                        }
                    }
                    check(Math.abs(Math.hypot(near.x() - x, near.z() - z) - best) < 1e-6, kind + ": nearest is nearest");
                    check(best <= kind.spacing * 1.2, kind + ": one is always within reach, was " + (int) best);
                }
            }
            check(!Ruins.site(seed, Ruins.Kind.SKY, 0, 0).equals(Ruins.site(seed + 1, Ruins.Kind.SKY, 0, 0))
                    || !Ruins.site(seed, Ruins.Kind.SKY, 1, 0).equals(Ruins.site(seed + 1, Ruins.Kind.SKY, 1, 0)), "another world, other places");
            check(Ruins.directionKey(0, 10).endsWith("south") && Ruins.directionKey(0, -10).endsWith("north") && Ruins.directionKey(10, 0).endsWith(".east")
                    && Ruins.directionKey(-10, 0).endsWith(".west") && Ruins.directionKey(10, -10).endsWith("north_east"), "compass directions");
        });

        step("the way in: the realms are closed until a Ruined Gate is woken with three keystones and stepped through", () -> {
            Realm.testAlias(Level.OVERWORLD, null);              // here the overworld is the Overworld
            try {
                ServerPlayer hero = fakePlayer(server, level, "RocWanderer");
                hero.setPos(5000.5, 100, 5000.5);
                // closed: no gate knows this player
                check(!Access.attuned(hero) && !Access.check(hero), "a new player is not attuned");
                Teleports.toHub(hero);
                Teleports.enterTower(hero, Line.ZEUS);
                check(hero.position().distanceTo(new Vec3(5000.5, 100, 5000.5)) < 0.01, "the realms refuse them");
                // the compass leads to a Ruined Gate instead of travelling
                Ruins.Site gateSite = Ruins.nearest(level.getSeed(), Ruins.Kind.GATE, hero.getX(), hero.getZ());
                check(gateSite.equals(Ruins.point(hero, Ruins.Kind.GATE)), "the compass points at the nearest Ruined Gate");
                ModContent.CHAOS_COMPASS.get().use(level, hero, InteractionHand.MAIN_HAND);
                check(hero.position().distanceTo(new Vec3(5000.5, 100, 5000.5)) < 0.01, "the Compass of Chaos does not travel: it scans");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero), "right-click begins a scan");
                com.thefarawaydev.realmsofchaos.world.Scanner.reset();

                // the Ruined Gate is raised once, where the seed says
                check(Ruins.centre(level, gateSite) == null, "not raised until someone comes");
                BlockPos c = Ruins.ensure(level, gateSite);
                check(c.getX() == gateSite.x() && c.getZ() == gateSite.z() && c.equals(Ruins.ensure(level, gateSite)) && c.equals(Ruins.centre(level, gateSite)), "raised once, and remembered");
                BlockPos pedestal = Ruins.pedestalPos(c);
                check(level.getBlockState(pedestal).is(ModContent.GATE_PEDESTAL.get()), "pedestal");
                check(Ruins.hasArch(level, pedestal) && Ruins.keystonesSet(level, pedestal) == 0 && !Ruins.awake(level, pedestal), "a dead arch with three empty sockets");
                for (BlockPos o : Ruins.opening(pedestal)) check(level.getBlockState(o).isAir(), "the arch is empty");
                check(level.getBlockState(c.offset(3, 1, 2)).is(Blocks.CAMPFIRE), "a fire still smoking");
                check(solid(level, c) && level.getBlockState(c.above()).isAir() && level.getBlockState(c.above(2)).isAir(), "room to stand at the pedestal");
                var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(c.offset(-3, 1, 2));
                check(chest != null, "the ruin's chest");
                boolean tale = false;
                for (int i = 0; i < chest.getContainerSize(); i++) tale |= Story.Chapter.SUNDERING.isBook(chest.getItem(i));
                check(tale, "the chest holds the story of the Sundering");
                for (Line line : Line.values()) check(chest.countItem(ModContent.seekerItem(line)) == 1, "the chest holds the seeker for " + line);

                // each seeker points to its shrine, and each shrine keeps its keystone
                for (Line line : Line.values()) {
                    Ruins.Kind kind = Ruins.Kind.of(line);
                    Ruins.Site shrine = Ruins.nearest(level.getSeed(), kind, hero.getX(), hero.getZ());
                    check(shrine.equals(Ruins.point(hero, kind)), "the seeker of " + line + " points at its shrine");
                    BlockPos sc = Ruins.ensure(level, shrine);
                    BlockPos chestAt = line == Line.HADES ? sc.offset(1, -4, 0) : sc.above();
                    var keep = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestAt);
                    check(keep != null && keep.countItem(ModContent.keystoneItem(line)) == 1, "the " + kind + " shrine keeps its keystone");
                    Story.Chapter chapter = line == Line.ZEUS ? Story.Chapter.SKY : line == Line.POSEIDON ? Story.Chapter.SEA : Story.Chapter.SHADE;
                    boolean page = false;
                    for (int i = 0; i < keep.getContainerSize(); i++) page |= chapter.isBook(keep.getItem(i));
                    check(page, "and a chapter of the story");
                    if (line == Line.POSEIDON) {
                        check(level.getBlockState(sc.offset(2, 0, 2)).is(Blocks.WATER) && solid(level, sc.offset(2, -1, 2)) && solid(level, sc.offset(0, 0, 3)), "a pool with a floor, bridged to the middle");
                    }
                    if (line == Line.ZEUS) check(level.getBlockState(sc.offset(3, 6, 3)).is(Blocks.GOLD_BLOCK), "gold-capped columns");
                    if (line == Line.HADES) {
                        // the crypt can be walked down into, one step at a time, and back out
                        Set<BlockPos> seen = new HashSet<>();
                        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
                        BlockPos start = sc.offset(-4, 1, 2);          // on the rim, between two standing stones
                        check(solid(level, start.below()) && open(level, start) && open(level, start.above()), "the rim of the crypt");
                        seen.add(start);
                        queue.add(start);
                        while (!queue.isEmpty()) {
                            BlockPos here = queue.poll();
                            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                                for (int dy = -1; dy <= 1; dy++) {
                                    BlockPos next = here.relative(d).above(dy);
                                    if (Math.abs(next.getX() - sc.getX()) > 6 || Math.abs(next.getZ() - sc.getZ()) > 6 || seen.contains(next)) continue;
                                    if (!solid(level, next.below()) || !open(level, next) || !open(level, next.above())) continue;
                                    // stepping up needs headroom where you stand; stepping down needs it where you go
                                    if (dy == 1 && !open(level, here.above(2))) continue;
                                    if (dy == -1 && !open(level, next.above(2))) continue;
                                    seen.add(next);
                                    queue.add(next);
                                }
                            }
                        }
                        check(seen.contains(sc.offset(0, -4, 0)), "the steps lead down to the chest");
                    }
                }

                // the pedestal: it says what is missing, takes each keystone once, and wakes the gate at three
                hero.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                check(!Ruins.usePedestal(level, pedestal, hero, hero.getMainHandItem()) && Ruins.keystonesSet(level, pedestal) == 0, "an empty hand sets nothing");
                hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.keystoneItem(Line.ZEUS), 2));
                level.getBlockState(pedestal).useItemOn(hero.getMainHandItem(), level, hero, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pedestal), net.minecraft.core.Direction.UP, pedestal, false));
                check(Ruins.isSet(level, pedestal, Line.ZEUS) && hero.getMainHandItem().getCount() == 1, "right-clicking the pedestal sets the keystone held");
                check(level.getBlockState(Ruins.socket(pedestal, Line.ZEUS)).is(Blocks.GOLD_BLOCK), "its socket lights");
                check(!Ruins.usePedestal(level, pedestal, hero, hero.getMainHandItem()) && hero.getMainHandItem().getCount() == 1, "a keystone already set is not taken again");
                check(!Ruins.awake(level, pedestal), "one keystone does not wake it");
                ItemStack tides = new ItemStack(ModContent.keystoneItem(Line.POSEIDON)), shades = new ItemStack(ModContent.keystoneItem(Line.HADES));
                check(Ruins.usePedestal(level, pedestal, hero, tides) && tides.isEmpty() && !Ruins.awake(level, pedestal), "two");
                check(Ruins.usePedestal(level, pedestal, hero, shades) && Ruins.keystonesSet(level, pedestal) == 3 && Ruins.awake(level, pedestal), "three keystones wake the gate");
                for (BlockPos o : Ruins.opening(pedestal)) check(level.getBlockState(o).is(ModContent.GATE_HUB.get()), "the arch fills with the gate");
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, new AABB(c).inflate(16), e -> true)) bolt.discard();

                // stepping through attunes; from then on the realms are open from anywhere
                check(!Access.attuned(hero), "waking the gate is not enough: you have to go through");
                BlockPos gate = Ruins.opening(pedestal).get(0);
                level.getBlockState(gate).useWithoutItem(level, hero, new BlockHitResult(Vec3.atCenterOf(gate), net.minecraft.core.Direction.SOUTH, gate, false));
                check(Access.attuned(hero) && Access.check(hero), "stepping through the gate attunes the player");
                check(!Access.attune(hero), "attuning happens once");
                // players from before gates were needed, and creative players, are let in
                ServerPlayer veteran = fakePlayer(server, level, "RocVeteran");
                data.markDefeated(veteran.getUUID(), Gods.byId("iris"));
                check(Access.attuned(veteran), "a player who had already woken a god keeps their way in");
                data.reset(veteran.getUUID());
                ServerPlayer builder = fakePlayer(server, level, "RocBuilder");
                check(!Access.attuned(builder), "test setup: a fresh survival player");
                builder.setGameMode(GameType.CREATIVE);
                check(Access.attuned(builder), "creative mode is let in");
                // a pedestal with no arch says so and takes nothing
                BlockPos lone = c.offset(40, 2, 40);
                level.setBlock(lone, ModContent.GATE_PEDESTAL.get().defaultBlockState(), 2);
                ItemStack spare = new ItemStack(ModContent.keystoneItem(Line.ZEUS));
                check(!Ruins.usePedestal(level, lone, hero, spare) && spare.getCount() == 1, "a pedestal without an arch takes nothing");
                level.setBlock(lone, Blocks.AIR.defaultBlockState(), 2);
            } finally {
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            }
        });

        step("the story: nineteen chapters, each a book, written as the journey goes", () -> {
            Realm.testAlias(Level.OVERWORLD, null);
            try {
                check(Story.Chapter.values().length == 19, "chapters: " + Story.Chapter.values().length);
                Set<String> titles = new HashSet<>();
                Set<Integer> slots = new HashSet<>();
                for (Story.Chapter c : Story.Chapter.values()) {
                    check(c.pages() >= 2 && titles.add(c.englishTitle()) && c.englishTitle().length() <= 32, c + ": text");
                    ItemStack book = c.book();
                    var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
                    check(book.is(net.minecraft.world.item.Items.WRITTEN_BOOK) && content != null && content.pages().size() == c.pages()
                            && content.author().equals(Story.AUTHOR), c + ": the book");
                    check(c.isBook(book), c + ": knows its own book");
                    check(slots.add(StoryMenu.slotOf(c)), c + " shares a slot in the chronicle");
                }
                check(!Story.Chapter.SKY.isBook(Story.Chapter.SEA.book()) && !Story.Chapter.SKY.isBook(new ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK)), "books are told apart");

                // what a player has earned
                ServerPlayer hero = fakePlayer(server, level, "RocReaderOfTales");
                check(Story.unlockedCount(hero) == 1 && Story.Chapter.PROLOGUE.unlocked(hero), "at first only the Oracle's letter");
                check(StoryMenu.guidance(hero).size() == 2, "the guide sends a new player to a Ruined Gate");
                Access.attune(hero);
                check(Story.unlockedCount(hero) == 9, "a gate opens the road in and the foot of each tower: " + Story.unlockedCount(hero));
                check(StoryMenu.guidance(hero).size() == 3, "then the guide names the next god of each tower");
                for (int rank = Line.RANKS; rank > Line.RANKS - 5; rank--) {
                    check(!Story.Chapter.ZEUS_MID.unlocked(hero), "not before five");
                    data.markDefeated(hero.getUUID(), Gods.get(Line.ZEUS, rank));
                }
                check(Story.Chapter.ZEUS_MID.unlocked(hero) && !Story.Chapter.ZEUS_HIGH.unlocked(hero) && !Story.Chapter.POSEIDON_MID.unlocked(hero), "five gods of a tower write its second chapter");
                check(Story.writtenBy(hero, Gods.get(Line.ZEUS, Line.RANKS - 4)).equals(List.of(Story.Chapter.ZEUS_MID)), "the fifth victory writes it");
                data.markDefeated(hero.getUUID(), Gods.get(Line.ZEUS, Line.RANKS - 5));
                check(Story.writtenBy(hero, Gods.get(Line.ZEUS, Line.RANKS - 5)).isEmpty(), "most victories write nothing new");

                // the chronicle: opened from the Codex, earned chapters can be taken as books, once
                CodexMenu.open(hero);
                check(!hero.containerMenu.getSlot(CodexMenu.SLOT_STORY).getItem().isEmpty(), "the Codex has the chronicle");
                ((CodexMenu) hero.containerMenu).clicked(CodexMenu.SLOT_STORY, 0, ContainerInput.PICKUP, hero);
                check(hero.containerMenu instanceof StoryMenu, "the Codex opens the Oracle's Chronicle");
                StoryMenu menu = (StoryMenu) hero.containerMenu;
                check(menu.getSlot(StoryMenu.slotOf(Story.Chapter.ZEUS_MID)).getItem().is(net.minecraft.world.item.Items.WRITTEN_BOOK), "an earned chapter shows as a book");
                check(menu.getSlot(StoryMenu.slotOf(Story.Chapter.ZEUS_FALL)).getItem().is(net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.GRAY)), "an unwritten chapter is blank");
                menu.clicked(StoryMenu.slotOf(Story.Chapter.ZEUS_FALL), 0, ContainerInput.PICKUP, hero);
                check(!Story.holds(hero, Story.Chapter.ZEUS_FALL), "an unwritten chapter cannot be taken");
                menu.clicked(StoryMenu.slotOf(Story.Chapter.ZEUS_MID), 0, ContainerInput.PICKUP, hero);
                menu.clicked(StoryMenu.slotOf(Story.Chapter.ZEUS_MID), 0, ContainerInput.PICKUP, hero);
                check(count(hero, net.minecraft.world.item.Items.WRITTEN_BOOK) == 1 && Story.holds(hero, Story.Chapter.ZEUS_MID), "a chapter is handed over once");
                check(menu.getCarried().isEmpty(), "the chronicle moves no items");
                hero.closeContainer();

                // the Oracle's Flame opens the same chronicle and hands over the chapter about the hub
                BlockPos flame = new BlockPos(5030, 120, 5030);
                level.setBlock(flame, ModContent.ORACLE.get().defaultBlockState(), 2);
                level.getBlockState(flame).useWithoutItem(level, hero, new BlockHitResult(Vec3.atCenterOf(flame), net.minecraft.core.Direction.UP, flame, false));
                check(Screens.last(hero) != null && Screens.commands(Screens.last(hero)).contains("/roc ui chapter hub") && Story.holds(hero, Story.Chapter.HUB), "the Oracle speaks");
                level.setBlock(flame, Blocks.AIR.defaultBlockState(), 2);

                // a king's own words are handed over when he falls; the epilogue when all three have
                for (God g : Gods.ofLine(Line.ZEUS)) data.markDefeated(hero.getUUID(), g);
                check(Story.writtenBy(hero, Gods.byId("zeus")).equals(List.of(Story.Chapter.ZEUS_FALL)), "the king's fall writes his chapter");
                Story.onDefeated(hero, Gods.byId("zeus"));
                check(!Story.holds(hero, Story.Chapter.ZEUS_FALL), "it comes a moment after the victory");
                advance(server, 120);
                Scheduler.tick(server);
                check(Story.holds(hero, Story.Chapter.ZEUS_FALL), "Zeus's words are handed over");
                for (God g : Gods.ALL) data.markDefeated(hero.getUUID(), g);
                check(Story.Chapter.EPILOGUE.unlocked(hero) && Story.unlockedCount(hero) == 19, "all three towers write the epilogue");
                check(Story.writtenBy(hero, Gods.byId("hades")).contains(Story.Chapter.EPILOGUE), "the last king's fall writes it");
                check(StoryMenu.guidance(hero).size() == 4, "the guide knows the towers are done");
                data.reset(hero.getUUID());
            } finally {
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            }
        });

        step("travel rules: you can revisit lower floors but not skip ahead", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            data.reset(player.getUUID());
            data.markDefeated(player.getUUID(), Tower.god(Line.ZEUS, 1));
            data.markDefeated(player.getUUID(), Tower.god(Line.ZEUS, 2));
            data.markPassed(player.getUUID(), Line.ZEUS, 1);
            advance(server, 41);
            Teleports.toFloor(player, Line.ZEUS, 9);
            check(Tower.floorAt(player.getY()) == 2, "floor 3 is not reached before its passage is walked: on " + Tower.floorAt(player.getY()));
            data.markPassed(player.getUUID(), Line.ZEUS, 2);
            advance(server, 41);
            Teleports.toFloor(player, Line.ZEUS, 9);
            check(Tower.floorAt(player.getY()) == 3, "clamped to the current floor (3), is on " + Tower.floorAt(player.getY()));
            advance(server, 41);
            Teleports.toFloor(player, Line.ZEUS, 1);
            check(Tower.floorAt(player.getY()) == 1, "back on floor 1");
            RealmSafety.updateBar(player, Line.ZEUS, 1);
        });

        step("rescue puts a stuck or fallen player back on their floor's arrival pad", () -> {
            player.setPos(10.5, Tower.floorY(4) + 3, -4.5);
            RealmSafety.rescue(player, Realm.OLYMPUS);
            check(player.position().distanceTo(Tower.arrival(4)) < 0.1, "rescued to floor 4 pad, at " + player.position());
        });

        step("leaving the realms: falls back to the world spawn when no return point is known", () -> {
            advance(server, 41);
            Teleports.leave(player);
            RESULTS.add("        left to " + player.level().dimension().identifier() + " " + BlockPos.containing(player.position()));
        });

        step("leaving the realms: returns to the remembered place in another dimension", () -> {
            ServerLevel nether = server.getLevel(Level.NETHER);
            check(nether != null, "test server has a nether");
            Realm.testAlias(Level.OVERWORLD, Realm.HUB);
            data.setReturnPoint(player.getUUID(), new ProgressData.ReturnPoint(Level.NETHER.identifier().toString(), 8.5, 70, 8.5, 90));
            // (the cross-dimension move itself is vanilla's teleport; a fake player cannot perform one on Forge)
            Teleports.Exit exit = Teleports.exitFor(server, player);
            check(exit.level() == nether, "exit is the nether, got " + exit.level().dimension().identifier());
            check(exit.pos().distanceTo(new Vec3(8.5, 70, 8.5)) < 0.1 && exit.yaw() == 90, "at the remembered spot");
            // a return point that itself lies inside a realm is ignored
            data.setReturnPoint(player.getUUID(), new ProgressData.ReturnPoint(Level.OVERWORLD.identifier().toString(), 1, 65, 1, 0));
            check(Realm.of(Teleports.exitFor(server, player).level().dimension()) == null || Teleports.exitFor(server, player).level() == server.overworld(),
                    "never exits into a realm when another place is known");
        });

        step("anti-stuck: a boss outside its floor, or inside a block, is put back at the centre", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            int floor = 5;
            Vec3 home = Tower.centre(level, floor);
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.get(Line.POSEIDON, 11));
            b.setHome(home);
            b.setPos(120.5, home.y, 0.5);
            level.addFreshEntity(b);
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 0.1, "out-of-bounds boss returned, is at " + b.position());
            b.setPos(0.5, home.y - 30, 0.5);
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 0.1, "boss that dropped a floor returned");
            b.setPos(0.5, home.y + 20, 0.5);
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 0.1, "boss that rose a floor returned");
            int y0 = Tower.floorY(floor);
            for (int y = y0 + 1; y <= y0 + 5; y++) level.setBlock(new BlockPos(10, y, 0), Blocks.STONE.defaultBlockState(), 2);
            b.setPos(10.5, y0 + 1, 0.5);
            check(b.isInWall(), "test setup: boss is inside stone");
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 0.1 && !b.isInWall(), "buried boss returned");
            for (int y = y0 + 1; y <= y0 + 5; y++) level.setBlock(new BlockPos(10, y, 0), Blocks.AIR.defaultBlockState(), 2);
            b.discard();
        });

        step("anti-stuck: a boss that makes no progress steps in next to its target", () -> {
            ServerPlayer target = fakePlayer(server, level, "RocTarget");
            int floor = 5;
            Vec3 home = Tower.centre(level, floor);
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.get(Line.POSEIDON, 11));
            b.setHome(home);
            b.setPos(home.x, home.y, home.z);
            level.addFreshEntity(b);
            target.setPos(13.5, Tower.floorY(floor) + 1, 0.5);
            b.setTarget(target);
            check(b.getTarget() == target, "test setup: target accepted");
            for (int i = 0; i < 6; i++) b.keepUnstuck(level);
            check(b.distanceTo(target) < 4.0, "boss moved next to target, distance " + b.distanceTo(target));
            check(!b.isInWall() && level.noCollision(b), "and is standing in free space");
            b.discard();
        });

        step("signature moves: every god has one; each of the fourteen kinds does its work for a relic's wielder", () -> {
            Set<Signature> used = EnumSet.noneOf(Signature.class);
            for (God g : Gods.ALL) {
                check(g.signature() != null, g.id() + " has no signature move");
                used.add(g.signature());
            }
            check(used.size() == Signature.values().length, "kinds of move in use: " + used.size());
            ServerPlayer hero = fakePlayer(server, level, "RocAdept");
            AABB near = new AABB(-20, ay - 4, -20, 20, ay + 10, 20);
            for (Signature kind : Signature.values()) {
                God god = Gods.ALL.stream().filter(g -> g.signature() == kind).findFirst().orElseThrow();
                hero.setPos(0.5, ground(level, arena, 0.5, 0.5), 0.5);
                hero.setHealth(8.0F);
                hero.removeAllEffects();
                hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.weaponItem(god)));
                // one foe ahead, one behind
                LivingEntity ahead = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                ahead.setPos(3.5, ground(level, arena, 3.5, 3.5), 3.5);
                level.addFreshEntity(ahead);
                LivingEntity behind = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                behind.setPos(-2.5, ground(level, arena, -2.5, -2.5), -2.5);
                level.addFreshEntity(behind);
                hero.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, ahead.getEyePosition());
                check(Signature.aim(level, hero, 18) == ahead, kind + ": aims at the foe in view");
                float before = ahead.getHealth(), beforeBehind = behind.getHealth();
                Vec3 from = hero.position();
                check(kind.perform(level, hero, god, 6.0F), kind + ": performed");
                switch (kind) {
                    case CHARGE -> check(hero.position().distanceTo(from) > 1.0 && hero.position().distanceTo(ahead.position()) < 3.0,
                            "CHARGE: dashed to the foe, moved " + hero.position().distanceTo(from));
                    case BLINK -> check(hero.position().distanceTo(ahead.position()) < 2.2 && level.noCollision(hero), "BLINK: stands beside the foe in free space");
                    case VOLLEY -> check(level.getEntitiesOfClass(Projectile.class, near, e -> true).size() == 5, "VOLLEY: five shots");
                    case MEND -> check(hero.getHealth() > 8.0F && hero.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "MEND: healed");
                    case WARD -> check(hero.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION) && hero.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE), "WARD: shielded");
                    case GALE -> check(ahead.getDeltaMovement().y > 0.8, "GALE: thrown upward");
                    case NOVA -> check(ahead.getDeltaMovement().x > 0.5 && ahead.getDeltaMovement().z > 0.5, "NOVA: hurled away");
                    case VORTEX -> check(ahead.getDeltaMovement().x < -0.5 && ahead.getDeltaMovement().z < -0.5, "VORTEX: dragged in");
                    case FROST -> check(ahead.getTicksFrozen() >= 200, "FROST: frozen");
                    case SLUMBER -> check(ahead.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && ahead.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "SLUMBER: dazed");
                    case CURSE -> check(ahead.hasEffect(net.minecraft.world.effect.MobEffects.WITHER) && Scheduler.pending() > 0, "CURSE: hexed, burst pending");
                    case REAP -> check(behind.getHealth() == beforeBehind && hero.getHealth() > 8.0F, "REAP: spares what is behind, heals the reaper");
                    case ERUPTION -> check(ahead.getHealth() == before && Scheduler.pending() > 0, "ERUPTION: warns before it strikes");
                    case STORM -> check(behind.getHealth() < beforeBehind, "STORM: strikes all around");
                }
                // let the delayed halves happen (vortex burst, hex, eruption)
                advance(server, 45);
                Scheduler.tick(server);
                check(Scheduler.pending() == 0, kind + ": nothing left waiting");
                if (kind != Signature.VOLLEY) check(ahead.getHealth() < before, kind + ": the foe in front was hurt (" + before + " -> " + ahead.getHealth() + ")");
                for (Projectile shot : level.getEntitiesOfClass(Projectile.class, near, e -> true)) shot.discard();
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, near, e -> true)) bolt.discard();
                ahead.discard();
                behind.discard();
            }
            // a blink needs somebody to blink to
            Vec3 spot = new Vec3(0.5, ground(level, arena, 0.5, 0.5), 0.5);
            hero.setPos(spot.x, spot.y, spot.z);
            check(!Signature.BLINK.perform(level, hero, Gods.byId("hermes"), 6.0F) && hero.position().distanceTo(spot) < 0.01, "BLINK: nothing happens with no foe");
            // moves never turn on other players or the wielder's own pets
            ServerPlayer friend = fakePlayer(server, level, "RocAlly");
            level.addNewPlayer(friend);                         // really in the world, so the move could find them
            try {
                friend.setPos(2.5, ground(level, arena, 2.5, 0.5), 0.5);
                float hp = friend.getHealth();
                check(level.getEntitiesOfClass(ServerPlayer.class, near, e -> e == friend).size() == 1, "test setup: the other player is in the world");
                Signature.NOVA.perform(level, hero, Gods.byId("pan"), 6.0F);
                check(friend.getHealth() == hp && friend.getDeltaMovement().horizontalDistance() < 0.01, "moves spare other players");
            } finally {
                friend.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            net.minecraft.world.entity.animal.wolf.Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.TRIGGERED);
            wolf.setPos(2.5, ground(level, arena, 2.5, 2.5), 2.5);
            level.addFreshEntity(wolf);
            wolf.tame(hero);
            check(!Power.canHurt(hero, wolf), "moves spare the wielder's pets");
            wolf.discard();
        });

        step("relic weapons: right-click casts the god's move, it recharges, three stars empower it, five make it echo", () -> {
            ServerPlayer hero = fakePlayer(server, level, "RocChampion");
            advance(server, 100);
            Scheduler.tick(server);                             // nothing left over from an earlier step
            AABB near = new AABB(-20, ay - 4, -20, 20, ay + 10, 20);
            Vec3 start = new Vec3(0.5, ground(level, arena, 0.5, 0.5), 0.5);
            hero.setPos(start.x, start.y, start.z);
            // a blade: right-click is the move (Ares: Warpath, a charge)
            ItemStack blade = new ItemStack(ModContent.weaponItem(Gods.byId("ares")));
            GodWeaponItem ares = (GodWeaponItem) blade.getItem();
            hero.setItemInHand(InteractionHand.MAIN_HAND, blade);
            ares.resetMove(hero);
            LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            zombie.setPos(3.5, ground(level, arena, 3.5, 3.5), 3.5);
            level.addFreshEntity(zombie);
            hero.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
            float before = zombie.getHealth();
            check(blade.getItem().use(level, hero, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.SUCCESS, "right-click casts");
            check(zombie.getHealth() < before && hero.position().distanceTo(start) > 1.0, "Warpath charged through the foe");
            check(hero.getCooldowns().isOnCooldown(blade) && ares.moveWait(hero, server.getTickCount()) == GodWeaponItem.MOVE_COOLDOWN_TICKS, "the move recharges for twelve seconds");
            check(!ares.castMove(level, hero, blade), "not again while recharging");
            advance(server, GodWeaponItem.MOVE_COOLDOWN_TICKS + 1);
            check(ares.moveWait(hero, server.getTickCount()) == 0 && ares.castMove(level, hero, blade), "ready again after the recharge");
            zombie.discard();

            // forging: each star shortens the recharge, three stars empower the move
            ItemStack fresh = new ItemStack(ModContent.weaponItem(Gods.byId("ares"))), three = fresh.copy(), five = fresh.copy();
            WeaponProgress.setStars(three, 3);
            WeaponProgress.setStars(five, 5);
            check(Math.abs(ares.moveDamage(three) - ares.moveDamage(fresh) * 1.3F * 1.5F) < 0.01, "three stars: +30% and empowered");
            check(GodWeaponItem.moveCooldown(five) < GodWeaponItem.moveCooldown(three) && GodWeaponItem.moveCooldown(three) < GodWeaponItem.moveCooldown(fresh), "stars shorten the recharge");

            // a bow: right-click shoots, sneak + right-click is the move (Apollo: Sunburst Volley), and five stars make it echo
            hero.setPos(start.x, start.y, start.z);
            ItemStack bow = new ItemStack(ModContent.weaponItem(Gods.byId("apollo")));
            GodWeaponItem apollo = (GodWeaponItem) bow.getItem();
            WeaponProgress.setStars(bow, 5);
            hero.setItemInHand(InteractionHand.MAIN_HAND, bow);
            apollo.resetMove(hero);
            bow.getItem().use(level, hero, InteractionHand.MAIN_HAND);
            check(level.getEntitiesOfClass(Projectile.class, near, e -> true).size() == 1, "right-click looses one arrow");
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class, near, e -> true)) shot.discard();
            hero.setShiftKeyDown(true);
            bow.getItem().use(level, hero, InteractionHand.MAIN_HAND);
            check(level.getEntitiesOfClass(Projectile.class, near, e -> true).size() == 5, "sneak + right-click looses the volley");
            check(Scheduler.pending() == 1, "an awakened relic has an echo waiting");
            advance(server, 20);
            Scheduler.tick(server);
            check(level.getEntitiesOfClass(Projectile.class, near, e -> true).size() == 10, "the echo looses a second volley");
            hero.setShiftKeyDown(false);
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class, near, e -> true)) shot.discard();

            // a move that cannot be made costs nothing
            ItemStack caduceus = new ItemStack(ModContent.weaponItem(Gods.byId("phobos")));
            GodWeaponItem phobos = (GodWeaponItem) caduceus.getItem();
            hero.setItemInHand(InteractionHand.MAIN_HAND, caduceus);
            phobos.resetMove(hero);
            check(!phobos.castMove(level, hero, caduceus) && phobos.moveWait(hero, server.getTickCount()) == 0, "no foe: Terror Strike does not go on recharge");

            // flourish: the fourth hit in a row lands a bonus
            ItemStack gladius = new ItemStack(ModContent.weaponItem(Gods.byId("nike")));
            hero.setItemInHand(InteractionHand.MAIN_HAND, gladius);
            LivingEntity dummy = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            dummy.setPos(2.5, ground(level, arena, 2.5, 0.5), 0.5);
            level.addFreshEntity(dummy);
            advance(server, 100);
            // as in a real fight, the blow itself lands first and starts the victim's hit cooldown
            dummy.hurtServer(level, level.damageSources().playerAttack(hero), 4.0F);
            check(dummy.damageCooldownTime > 10, "test setup: the dummy is in its hit cooldown");
            float hp = dummy.getHealth();
            for (int i = 0; i < 3; i++) gladius.getItem().hurtEnemy(gladius, dummy, hero);
            check(dummy.getHealth() == hp, "three hits: no flourish yet");
            gladius.getItem().hurtEnemy(gladius, dummy, hero);
            float bonus = (float) (Gods.byId("nike").baseDamage() * WeaponKind.BLADE.meleeScale() * 0.5);
            // (a zombie's two points of armour take a little off)
            check(hp - dummy.getHealth() > bonus * 0.8F && hp - dummy.getHealth() <= bonus + 0.01F,
                    "the fourth hit in a row is a flourish worth " + bonus + ", took " + (hp - dummy.getHealth()));
            dummy.setHealth(dummy.getMaxHealth());
            hp = dummy.getHealth();
            advance(server, 100);
            for (int i = 0; i < 3; i++) gladius.getItem().hurtEnemy(gladius, dummy, hero);
            check(dummy.getHealth() == hp, "a pause breaks the chain");
            dummy.discard();
        });

        step("gods: all 45 cast their signature move at a player; it alternates with the line's ability; footwork", () -> {
            ServerPlayer target = fakePlayer(server, level, "RocQuarry");
            Vec3 home = Tower.centre(level, 5);
            AABB near = new AABB(-24, home.y - 4, -24, 24, home.y + 12, 24);
            level.addNewPlayer(target);                         // really in the world, so the gods can find them
            try {
            for (God god : Gods.ALL) {
                target.setPos(home.x + 3.0, ground(level, 5, home.x + 3.0, home.z + 3.0), home.z + 3.0);
                target.setHealth(target.getMaxHealth());
                target.removeAllEffects();
                target.damageCooldownTime = 0;
                target.setDeltaMovement(Vec3.ZERO);
                GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                b.setGod(god);
                b.setPos(home.x, home.y, home.z);
                b.setHome(home);
                level.addFreshEntity(b);
                b.setTarget(target);
                b.setHealth(b.getMaxHealth() * 0.5F);
                check(b.nextIsSignature(), god.id() + " opens with its signature move");
                float before = target.getHealth();
                check(b.castSignature(level), god.id() + " cast " + god.signature());
                advance(server, 45);
                Scheduler.tick(server);
                boolean shots = !level.getEntitiesOfClass(Projectile.class, near, e -> true).isEmpty();
                check(target.getHealth() < before || shots, god.id() + ": " + god.signature() + " reached the player");
                check(target.isAlive(), god.id() + ": one move does not kill a full-health player");
                check(god.signature() != Signature.MEND || b.getHealth() > b.getMaxHealth() * 0.5F, god.id() + " healed itself");
                check(level.noCollision(b) && !b.isInWall(), god.id() + " ended its move in free space");
                b.keepUnstuck(level);
                for (Projectile shot : level.getEntitiesOfClass(Projectile.class, near, e -> true)) shot.discard();
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, near, e -> true)) bolt.discard();
                b.castAbility(level, god);
                b.discard();
            }
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(-64, -64, -64, 64, 400, 64),
                    e -> e.entityTags().contains("realms_of_chaos_minion"))) m.discard();
            for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, near, e -> true)) bolt.discard();

            // footwork: a melee god lunges at a target that keeps away; a ranged god hops back when crowded
            target.setHealth(target.getMaxHealth());
            GodBoss ares = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            ares.setGod(Gods.byId("ares"));
            ares.setPos(home.x, home.y, home.z);
            ares.setHome(home);
            level.addFreshEntity(ares);
            ares.setOnGround(true);
            target.setPos(home.x + 8.0, ground(level, 5, home.x + 8.0, home.z), home.z);
            boolean lunged = false;
            for (int i = 0; i < 60 && !lunged; i++) lunged = ares.maneuver(level, target);
            check(lunged && ares.getDeltaMovement().x > 0.5 && ares.getDeltaMovement().y > 0.3, "a melee god lunges at a distant target");
            target.setPos(home.x + 2.0, ground(level, 5, home.x + 2.0, home.z), home.z);
            ares.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 60; i++) check(!ares.maneuver(level, target), "a melee god does not hop about at close quarters");
            ares.discard();
            GodBoss apollo = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            apollo.setGod(Gods.byId("apollo"));
            apollo.setPos(home.x, home.y, home.z);
            apollo.setHome(home);
            level.addFreshEntity(apollo);
            apollo.setOnGround(true);
            boolean hopped = false;
            for (int i = 0; i < 60 && !hopped; i++) hopped = apollo.maneuver(level, target);
            check(hopped && apollo.getDeltaMovement().x < -0.5, "a ranged god hops away when crowded");
            // with its back to the wall it hops inward instead of into the wall
            apollo.setPos(home.x + Tower.FIGHT_RADIUS - 1.0, ground(level, 5, home.x + Tower.FIGHT_RADIUS - 1.0, home.z), home.z);
            target.setPos(home.x + Tower.FIGHT_RADIUS - 3.0, ground(level, 5, home.x + Tower.FIGHT_RADIUS - 3.0, home.z), home.z);
            apollo.setDeltaMovement(Vec3.ZERO);
            hopped = false;
            for (int i = 0; i < 60 && !hopped; i++) hopped = apollo.maneuver(level, target);
            check(hopped && apollo.getDeltaMovement().x < -0.5, "a cornered ranged god hops back towards the middle");
            apollo.discard();
            } finally {
                target.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        });

        step("every god's weapon hit power runs without error", () -> {
            ServerPlayer wielder = fakePlayer(server, level, "RocWielder");
            wielder.setPos(0.5, Tower.floorY(5) + 1, 0.5);
            for (God god : Gods.ALL) {
                LivingEntity dummy = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                dummy.setPos(3.5, Tower.floorY(5) + 1, 3.5);
                level.addFreshEntity(dummy);
                god.power().hit(level, dummy, wielder, god);
                god.applyTargetEffect(dummy);
                god.applySelfEffect(wielder);
                dummy.discard();
            }
            for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, new AABB(-24, 0, -24, 24, 400, 24), e -> true)) bolt.discard();
        });

        step("a trial has three phases: no blow skips one, the aegis needs its pylons broken, the wrath takes the floor and gives it back", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            final int floor = 7;
            TowerBuilder.buildFloor(level, Line.ZEUS, floor);
            God god = Tower.god(Line.ZEUS, floor);
            Vec3 home = Tower.centre(level, floor);
            int y0 = Tower.floorY(floor);
            AABB room = new AABB(-24, y0 - 3, -24, 24, y0 + 17, 24);
            clearBosses(level);
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, room, e -> true)) e.discard();
            ServerPlayer hero = fakePlayer(server, level, "RocPhases");
            level.addNewPlayer(hero);
            Block pylonBlock = ModContent.SHIELD_PYLON.get();
            // what the floor looks like before the fight, to compare with afterwards
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (int x = -21; x <= 21; x++) {
                for (int z = -21; z <= 21; z++) {
                    for (int y = y0 - 2; y <= y0 + 3; y++) before.put(new BlockPos(x, y, z), level.getBlockState(new BlockPos(x, y, z)));
                }
            }
            try {
                hero.setPos(home.x + 5, ground(level, floor, home.x + 5, home.z), home.z);
                settle(level);
                GodBoss boss = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                boss.setGod(god);
                boss.setPos(home.x, home.y, home.z);
                boss.setHome(home);
                level.addFreshEntity(boss);
                boss.setTarget(hero);
                float max = boss.getMaxHealth();
                check(boss.phase() == 1 && !boss.aegisUp() && !boss.isInvulnerable(), "a trial begins in its first phase");

                // ---- a blow that would kill it outright only carries it to the threshold
                boss.hurtServer(level, level.damageSources().playerAttack(hero), 100000.0F);
                check(boss.isAlive() && Math.abs(boss.getHealth() - max * GodBoss.AEGIS_AT) < 0.01, "no blow carries a god past 65%: " + boss.getHealth() + " of " + max);
                check(boss.phase() == 2 && boss.aegisUp() && boss.isInvulnerable() && boss.isEnraged(), "there it raises its aegis");
                int expected = Adapt.pylons(level.getDifficulty(), 1, 0);
                check(boss.pylons().size() == expected, "as many pylons as the difficulty asks: " + boss.pylons().size() + " of " + expected);
                for (BlockPos pylon : boss.pylons()) {
                    check(level.getBlockState(pylon).is(pylonBlock) && solid(level, pylon.below()) && level.getBlockState(pylon.above()).isAir(), "a pylon stands on open ground at " + pylon);
                    check(!(Math.abs(pylon.getX()) <= 4 && Math.abs(pylon.getZ()) >= 8), "clear of the altar and the gates: " + pylon);
                    double r = Math.hypot(pylon.getX() + 0.5 - home.x, pylon.getZ() + 0.5 - home.z);
                    check(r > 5 && r < 15, "out on the floor, inside the fight ring: " + r);
                }
                boss.damageCooldownTime = 0;
                float held = boss.getHealth();
                check(!boss.hurtServer(level, level.damageSources().playerAttack(hero), 50.0F) && boss.getHealth() == held, "nothing hurts it behind its aegis");

                // ---- the pylons: three blows each
                List<BlockPos> stood = new ArrayList<>(boss.pylons());
                BlockPos first = stood.get(0);
                check(com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock.strike(level, first, hero) && boss.ownsPylon(first) && level.getBlockState(first).is(pylonBlock), "one blow only cracks it");
                com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock.strike(level, first, hero);
                com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock.strike(level, first, hero);
                check(boss.ownsPylon(first) && level.getBlockState(first).is(pylonBlock), "blows in one flurry glance off: a pylon has to be stood at");
                strikePylon(server, level, first, hero, 2);
                check(!boss.ownsPylon(first) && level.getBlockState(first).isAir() && boss.aegisUp(), "the third breaks it, and the aegis holds while others stand");
                // a pylon the shifting floor took is stood up again
                BlockPos second = stood.get(1);
                level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
                boss.reseatPylons(level);
                check(boss.pylons().size() == expected - 1, "no pylon is lost to the floor: " + boss.pylons().size());
                for (BlockPos pylon : boss.pylons()) check(level.getBlockState(pylon).is(pylonBlock), "every pylon it counts is really there: " + pylon);
                for (BlockPos pylon : new ArrayList<>(boss.pylons())) {
                    strikePylon(server, level, pylon, hero, GodBoss.PYLON_HITS);
                }
                check(!boss.aegisUp() && boss.pylons().isEmpty() && !boss.isInvulnerable() && boss.phase() == 2, "all pylons down: the aegis is gone");
                check(FloorShifts.isShifted(Line.ZEUS, floor), "and the floor begins to shift");
                BlockPos orphan = BlockPos.containing(home.x + 3, ground(level, floor, home.x + 3, home.z + 3), home.z + 3);
                level.setBlock(orphan, pylonBlock.defaultBlockState(), 3);
                check(!com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock.strike(level, orphan, hero) && level.getBlockState(orphan).isAir(), "a pylon no god owns crumbles at a touch");

                // ---- the god has been fought hand to hand so far, and says so
                check(boss.stance() == GodBoss.Stance.BRISTLE, "fought hand to hand, it braces: " + boss.stance());
                // ...then it is shot at for a whole phase
                boss.recordDamage(0.0F, 5000.0F);
                boss.damageCooldownTime = 0;
                boss.hurtServer(level, level.damageSources().playerAttack(hero), 100000.0F);
                check(boss.isAlive() && Math.abs(boss.getHealth() - max * GodBoss.WRATH_AT) < 0.01, "the next blow stops at 30%: " + boss.getHealth());
                check(boss.phase() == 3 && boss.madeLastStand() && boss.isInvulnerable(), "there its wrath begins, with a last stand");
                check(boss.stance() == GodBoss.Stance.DEFLECT, "shot at for a whole phase, it has learnt to turn shots aside: " + boss.stance());
                check(!FloorShifts.isShifted(Line.ZEUS, floor), "the floor turns back for the wrath");
                advance(server, 90);
                Scheduler.tick(server);                 // its last stand plays out
                check(!boss.isInvulnerable(), "then it can be hurt again");
                var snowball = EntityTypes.SNOWBALL.create(level, EntitySpawnReason.TRIGGERED);
                float hp = boss.getHealth();
                boss.damageCooldownTime = 0;
                boss.hurtServer(level, level.damageSources().thrown(snowball, hero), 12.0F);
                float byShot = hp - boss.getHealth();
                hp = boss.getHealth();
                boss.damageCooldownTime = 0;
                boss.hurtServer(level, level.damageSources().playerAttack(hero), 12.0F);
                float byBlade = hp - boss.getHealth();
                check(byShot > 0 && byBlade > byShot * 2.0F, "a shot now does less than half of what a blow does: " + byShot + " against " + byBlade);

                // ---- the wrath takes the floor, ring by ring, from the wall inward
                check(boss.safeRadius() == Tower.WALL_INNER, "the whole floor to begin with: " + boss.safeRadius());
                for (int i = 0; i < 3; i++) boss.collapse(level);
                check(boss.safeRadius() == Tower.WALL_INNER - 3, "three rings gone: " + boss.safeRadius());
                Block hazard = com.thefarawaydev.realmsofchaos.world.Arena.hazard(Line.ZEUS);
                int lost = 0;
                for (BlockPos pos : before.keySet()) {
                    if (!level.getBlockState(pos).is(hazard)) continue;
                    lost++;
                    check(Math.hypot(pos.getX(), pos.getZ()) >= boss.safeRadius() + 0.5 && level.getBlockState(pos.above()).isAir(), "only paving outside the safe ring is lost: " + pos);
                }
                check(lost > 60, "the outer rings have turned: " + lost + " blocks");
                check(level.getBlockState(Tower.altarPos(floor)).getBlock() instanceof AltarBlock && before.get(Tower.altarPos(floor).below()) == level.getBlockState(Tower.altarPos(floor).below()),
                        "the altar and its dais are left alone");
                check(data.isBuilt(com.thefarawaydev.realmsofchaos.world.Arena.scorchKey(Line.ZEUS, floor)), "and the floor is marked for repair, should the server stop now");
                // standing on the lost part hurts; the middle does not
                hero.setPos(19.5, ground(level, floor, 19.5, 4.5), 4.5);
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                float b4 = hero.getHealth();
                check(boss.burnOutside(level) == 1 && hero.getHealth() < b4, "standing on the lost floor hurts");
                hero.setPos(home.x + 4, ground(level, floor, home.x + 4, home.z), home.z);
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                check(boss.burnOutside(level) == 0 && hero.getHealth() == hero.getMaxHealth(), "the middle is safe");
                for (int i = 0; i < 40; i++) boss.collapse(level);
                check(boss.safeRadius() == com.thefarawaydev.realmsofchaos.world.Arena.MIN_SAFE, "the middle of the floor is never lost: " + boss.safeRadius());

                // ---- strikes from above: marked first, then they land
                int marks = boss.strikes(level);
                check(marks >= 2 && hero.getHealth() == hero.getMaxHealth(), "strikes are only marked at first: " + marks);
                advance(server, GodBoss.STRIKE_WARNING + 1);
                Scheduler.tick(server);
                check(hero.getHealth() < hero.getMaxHealth(), "then they land on whoever stayed in the ring");
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                boss.strikes(level);
                hero.setPos(home.x + 14, ground(level, floor, home.x + 14, home.z), home.z);      // out of every ring
                advance(server, GodBoss.STRIKE_WARNING + 1);
                Scheduler.tick(server);
                check(hero.getHealth() == hero.getMaxHealth(), "stepping out of the ring is all it takes");

                // ---- when the god falls the floor is given back exactly as it was
                slay(server, level, boss, hero);
                check(!boss.isAlive(), "the god falls");
                for (int i = 0; i < TowerBuilder.MORPH_STEPS + 2; i++) FloorShifts.stepAll(server, true);
                for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                    check(level.getBlockState(e.getKey()) == e.getValue(), "the floor is as it was at " + e.getKey() + ": " + level.getBlockState(e.getKey()) + " was " + e.getValue());
                }
                check(!data.isBuilt(com.thefarawaydev.realmsofchaos.world.Arena.scorchKey(Line.ZEUS, floor)), "with nothing left to repair");

                // ---- a floor left scorched by a server stop is rebuilt when someone next sets foot on it
                Map<BlockPos, BlockState> scratch = new HashMap<>();
                check(com.thefarawaydev.realmsofchaos.world.Arena.scorch(level, Line.ZEUS, floor, 15.5, 22.0, scratch) > 60, "test setup: scorch the floor");
                data.markBuilt(TowerBuilder.floorKey(Line.ZEUS, floor));
                TowerBuilder.ensureFloor(level, Line.ZEUS, floor);
                for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) check(level.getBlockState(e.getKey()) == e.getValue(), "rebuilt at " + e.getKey());
                check(!data.isBuilt(com.thefarawaydev.realmsofchaos.world.Arena.scorchKey(Line.ZEUS, floor)), "and no longer marked");
            } finally {
                hero.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                clearBosses(level);
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, room, e -> true)) e.discard();
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, room, e -> true)) drop.discard();
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, room, e -> true)) bolt.discard();
                for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, room, e -> e.entityTags().contains("realms_of_chaos_minion"))) m.discard();
                data.reset(hero.getUUID());
            }
        });

        step("gods rise to match what their challengers carry - weapon, enchantments, forging - and the difficulty sets health and pace", () -> {
            var enchantments = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            ItemStack iron = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), diamond = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            check(Math.abs(Adapt.offence(iron) - 6.0) < 0.01, "an iron sword hits for 6: " + Adapt.offence(iron));
            diamond.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
            check(Adapt.enchantLevels(diamond) == 5 && Math.abs(Adapt.offence(diamond) - 10.0) < 0.01, "a diamond sword with Sharpness V counts its enchantment: " + Adapt.offence(diamond));
            check(Adapt.offence(new ItemStack(net.minecraft.world.item.Items.BREAD)) == 0.0 && Adapt.offence(ItemStack.EMPTY) == 0.0, "bread is not a weapon");
            ItemStack bow = new ItemStack(net.minecraft.world.item.Items.BOW);
            check(Adapt.offence(bow) == 8.0 && Adapt.isRanged(bow) && !Adapt.isRanged(iron), "a bow counts, as a weapon that fights from a distance");
            ItemStack relic = new ItemStack(ModContent.weaponItem(Gods.byId("zeus")));
            double plain = Adapt.offence(relic);
            WeaponProgress.setLevel(relic, WeaponProgress.MAX_LEVEL);
            WeaponProgress.setStars(relic, WeaponProgress.MAX_STARS);
            check(plain > 6.0 && Adapt.offence(relic) > plain * 1.5, "a relic counts for what has been forged into it: " + plain + " -> " + Adapt.offence(relic));
            ItemStack plate = new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            plate.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);

            check(Adapt.gearHealth(0.0) == 1.0 && Adapt.gearHealth(1.0) == 1.0, "a god is never weaker for poor gear");
            check(Adapt.gearHealth(1.5) > 1.3 && Adapt.gearHealth(1.5) < Adapt.gearHealth(2.0) && Adapt.gearHealth(50.0) == Adapt.MAX_GEAR_HEALTH, "it rises with gear past what the floor expects, to a cap");
            check(Adapt.gearDamage(0.5) == 1.0F && Adapt.gearDamage(2.0) > 1.3F && Adapt.gearDamage(99.0) == (float) Adapt.MAX_GEAR_DAMAGE, "and strikes harder at armour past what the floor expects");
            check(Adapt.expectedOffence(15) > Adapt.expectedOffence(1) && Adapt.expectedDefence(15) > Adapt.expectedDefence(1), "the higher floors expect more");
            check(Adapt.difficultyHealth(net.minecraft.world.Difficulty.EASY) < 1.0 && Adapt.difficultyHealth(net.minecraft.world.Difficulty.NORMAL) == 1.0
                    && Adapt.difficultyHealth(net.minecraft.world.Difficulty.HARD) > 1.0, "health follows the difficulty");
            check(Adapt.difficultyPace(net.minecraft.world.Difficulty.EASY) > 1.0 && Adapt.difficultyPace(net.minecraft.world.Difficulty.HARD) < 1.0, "and so does how long it waits between moves");
            check(Adapt.pylons(net.minecraft.world.Difficulty.EASY, 1, 0) == 2 && Adapt.pylons(net.minecraft.world.Difficulty.NORMAL, 1, 0) == 3
                    && Adapt.pylons(net.minecraft.world.Difficulty.HARD, 1, 0) == 4, "two, three or four pylons");
            check(Adapt.pylons(net.minecraft.world.Difficulty.NORMAL, 3, 0) == 4 && Adapt.pylons(net.minecraft.world.Difficulty.NORMAL, 1, 1) == 4
                    && Adapt.pylons(net.minecraft.world.Difficulty.EASY, 1, -1) == 2 && Adapt.pylons(net.minecraft.world.Difficulty.HARD, 4, 1) == 6, "one more for a group or a quick phase, never fewer than two");

            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            ServerPlayer bare = fakePlayer(server, level, "RocBareHanded"), armed = fakePlayer(server, level, "RocOverArmed");
            data.reset(bare.getUUID());
            data.reset(armed.getUUID());
            God god = Tower.god(Line.ZEUS, 1);
            BlockPos altar = Tower.altarPos(1);
            clearBosses(level);
            try {
                level.addNewPlayer(bare);
                bare.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
                settle(level);
                Adapt.Measure none = AltarBlock.measure(level, bare, altar, Line.ZEUS, god);
                check(none.might() == 0.0 && Adapt.gearHealth(none.might()) == 1.0, "empty hands measure as nothing: " + none.might());
                GodBoss asItIs = AltarBlock.begin(level, bare, altar, Line.ZEUS);
                check(asItIs != null && Math.abs(asItIs.getMaxHealth() - base(level, god)) < 0.01 && asItIs.stance() == GodBoss.Stance.BALANCED, "against bare hands the god is as it is");
                float plainHits = asItIs.damageScale();
                int plainWait = asItIs.castInterval(200.0);
                asItIs.discard();
                bare.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

                level.addNewPlayer(armed);
                armed.setPos(Tower.arrival(1).x, Tower.arrival(1).y, Tower.arrival(1).z);
                armed.getInventory().add(diamond.copy());
                settle(level);
                Adapt.Measure some = AltarBlock.measure(level, armed, altar, Line.ZEUS, god);
                check(Math.abs(some.might() - 0.65 * 10.0 / Adapt.expectedOffence(1)) < 1e-6, "an enchanted diamond sword on floor 1: " + some.might());
                armed.getInventory().add(relic.copy());
                Adapt.Measure much = AltarBlock.measure(level, armed, altar, Line.ZEUS, god);
                check(much.might() > some.might() && Adapt.gearHealth(much.might()) > 1.5, "the best weapon in the pack is the one that counts: " + much.might());
                GodBoss risen = AltarBlock.begin(level, armed, altar, Line.ZEUS);
                check(risen != null && Math.abs(risen.getMaxHealth() - base(level, god) * Adapt.gearHealth(much.might())) < 0.01 && risen.getHealth() == risen.getMaxHealth(),
                        "the god has risen to match: " + (risen == null ? "none" : risen.getMaxHealth()) + " for might " + much.might());
                check(Math.abs(risen.trialScale() - Adapt.gearHealth(much.might()) * Adapt.difficultyHealth(level.getDifficulty())) < 0.001, "and can say by how much");
                check(risen.damageScale() == plainHits, "armour, not weapons, is what makes it hit harder");
                // the altar's screen says so before the trial is begun
                check(Screens.bodyLines(Screens.altar(armed, level, Line.ZEUS, altar)) >= 7, "the altar's screen tells what the god will make of your arms");
                // phases quicken it
                check(risen.castInterval(200.0) == plainWait, "in its first phase it keeps its own pace");
                risen.setHealth(risen.getMaxHealth() * 0.6F);
                risen.updatePhase(level);
                risen.endAegis(level, true);
                int enraged = risen.castInterval(200.0);
                risen.setHealth(risen.getMaxHealth() * 0.25F);
                risen.updatePhase(level);
                check(enraged < plainWait && risen.castInterval(200.0) < enraged, "each phase shortens the wait between its moves: " + plainWait + " > " + enraged + " > " + risen.castInterval(200.0));
                // a god keeps its trial through a save
                var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess());
                risen.saveWithoutId(saved);
                check(saved.buildResult().getIntOr("Phase", 0) == 3 && saved.buildResult().getStringOr("Stance", "").equals(risen.stance().name()), "its phase and stance are saved");
                risen.discard();
            } finally {
                bare.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                armed.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                clearBosses(level);
                AABB room = new AABB(-24, Tower.floorY(1) - 3, -24, 24, Tower.floorY(1) + 17, 24);
                for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, room, e -> e.entityTags().contains("realms_of_chaos_minion"))) m.discard();
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, room, e -> true)) bolt.discard();
                for (int i = 0; i < TowerBuilder.MORPH_STEPS + 2; i++) FloorShifts.stepAll(server, true);
            }
        });

        step("the compass scans and carries nobody: rolls, clean locks and static, steadied by relays and the hub, spoiled by a jammer", () -> {
            Realm.testAlias(Level.OVERWORLD, null);
            com.thefarawaydev.realmsofchaos.world.Scanner.reset();
            com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            data.unmarkPrefix("net|");
            ServerPlayer hero = fakePlayer(server, level, "RocScanner"), rival = fakePlayer(server, level, "RocRival");
            Vec3 spot = new Vec3(7000.5, 100, 7000.5);
            hero.setPos(spot.x, spot.y, spot.z);
            Block relayBlock = ModContent.RELAY_CORE.get(), jammerBlock = ModContent.SIGNAL_JAMMER.get();
            BlockPos[] relays = {new BlockPos(7010, 100, 7000), new BlockPos(7000, 100, 7020), new BlockPos(6990, 100, 7000), new BlockPos(7000, 100, 6980)};
            BlockPos jammer = new BlockPos(7030, 100, 7030), farRelay = new BlockPos(7400, 100, 7000);
            try {
                check(!Teleports.recall(hero) && hero.position().distanceTo(spot) < 0.01, "nothing carries a player to the hub from the world outside");
                ItemStack compass = new ItemStack(ModContent.CHAOS_COMPASS.get());
                hero.setItemInHand(InteractionHand.MAIN_HAND, compass);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.mode(compass) == com.thefarawaydev.realmsofchaos.world.Scanner.Mode.HUB, "a new compass looks for the way to the hub");

                // ---- signal
                double weather = level.isThundering() ? 0.2 : level.isRaining() ? 0.1 : 0.0, bare = 0.35 - weather;
                check(Math.abs(com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero) - bare) < 1e-9, "a bare compass reads a poor signal: " + com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero));
                level.setBlock(relays[0], relayBlock.defaultBlockState(), 3);
                relayBlock.setPlacedBy(level, relays[0], relayBlock.defaultBlockState(), hero, ItemStack.EMPTY);
                var node = com.thefarawaydev.realmsofchaos.world.Network.at(level, relays[0], com.thefarawaydev.realmsofchaos.world.Network.Kind.RELAY);
                check(node != null && node.owner().equals(hero.getUUID()), "a relay set down joins the network under its owner");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.relays(level, hero) == 1 && Math.abs(com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero) - (bare + 0.15)) < 1e-9, "one relay in range steadies it");
                for (int i = 1; i < relays.length; i++) {
                    level.setBlock(relays[i], relayBlock.defaultBlockState(), 3);
                    relayBlock.setPlacedBy(level, relays[i], relayBlock.defaultBlockState(), hero, ItemStack.EMPTY);
                }
                level.setBlock(farRelay, relayBlock.defaultBlockState(), 3);
                relayBlock.setPlacedBy(level, farRelay, relayBlock.defaultBlockState(), hero, ItemStack.EMPTY);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.relays(level, hero) == 3 && Math.abs(com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero) - (bare + 0.45)) < 1e-9,
                        "three count; a fourth adds nothing and one 400 blocks off is out of range");
                // a relay that is gone is forgotten the next time anything looks
                level.setBlock(relays[3], Blocks.AIR.defaultBlockState(), 3);
                check(com.thefarawaydev.realmsofchaos.world.Network.near(level, com.thefarawaydev.realmsofchaos.world.Network.Kind.RELAY, spot.x, spot.z, 96).size() == 3
                        && com.thefarawaydev.realmsofchaos.world.Network.at(level, relays[3], com.thefarawaydev.realmsofchaos.world.Network.Kind.RELAY) == null, "a relay that is gone is forgotten");
                // someone else's jammer
                level.setBlock(jammer, jammerBlock.defaultBlockState(), 3);
                jammerBlock.setPlacedBy(level, jammer, jammerBlock.defaultBlockState(), rival, ItemStack.EMPTY);
                rival.setPos(spot.x, spot.y, spot.z);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.jammed(level, hero) && !com.thefarawaydev.realmsofchaos.world.Scanner.jammed(level, rival), "a jammer spoils everyone's scans but its owner's");
                check(Math.abs(com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero) - Math.max(0.05, bare + 0.45 - 0.5)) < 1e-9, "by half the dial");
                jammerBlock.playerWillDestroy(level, jammer, jammerBlock.defaultBlockState(), rival);
                level.setBlock(jammer, Blocks.AIR.defaultBlockState(), 3);
                check(!com.thefarawaydev.realmsofchaos.world.Scanner.jammed(level, hero), "breaking it takes it out of the network");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 5);
                check(Math.abs(com.thefarawaydev.realmsofchaos.world.Scanner.quality(level, hero) - Math.min(1.0, bare + 0.45 + 0.2)) < 1e-9, "every hub tier steadies it a little more");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);

                // ---- what there is to find
                var hubTargets = com.thefarawaydev.realmsofchaos.world.Scanner.targets(level, hero, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.HUB);
                Ruins.Site gate = Ruins.nearest(level.getSeed(), Ruins.Kind.GATE, hero.getX(), hero.getZ());
                check(hubTargets.size() == 1 && hubTargets.get(0).x() == gate.x() + 0.5 && hubTargets.get(0).z() == gate.z() + 0.5, "set to the hub it looks for the nearest Ruined Gate");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.targets(level, hero, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.KEYSTONES).size() == 3, "set to keystones, for the three shrines");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.targets(level, hero, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.NETWORK).size() == 4, "set to the network, for every relay that stands");

                // ---- the needle
                check(com.thefarawaydev.realmsofchaos.world.Scanner.surgeChance(1.0) < com.thefarawaydev.realmsofchaos.world.Scanner.surgeChance(0.1), "a poor signal surges more");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.lockChance(1.0, 1.0) > com.thefarawaydev.realmsofchaos.world.Scanner.lockChance(1.0, 0.0)
                        && com.thefarawaydev.realmsofchaos.world.Scanner.lockChance(1.0, 0.0) > com.thefarawaydev.realmsofchaos.world.Scanner.lockChance(0.1, 0.0), "a good signal and a long scan lock more");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.scatterDegrees(1.0, 0.0) == 0.0
                        && com.thefarawaydev.realmsofchaos.world.Scanner.scatterDegrees(0.35, 1.0) < com.thefarawaydev.realmsofchaos.world.Scanner.scatterDegrees(0.35, 0.0), "the longer a scan runs the truer it reads");
                var rng = net.minecraft.util.RandomSource.create(42L);
                double tx = hubTargets.get(0).x() - hero.getX(), tz = hubTargets.get(0).z() - hero.getZ(), trueDist = Math.hypot(tx, tz);
                int locks = 0, surges = 0, rough = 0;
                double q = 0.5, maxTurn = com.thefarawaydev.realmsofchaos.world.Scanner.scatterDegrees(q, 0.0), maxStretch = com.thefarawaydev.realmsofchaos.world.Scanner.scatterShare(q, 0.0);
                for (int i = 0; i < 3000; i++) {
                    var reading = com.thefarawaydev.realmsofchaos.world.Scanner.roll(hubTargets, hero.getX(), hero.getZ(), q, 0.0, rng);
                    switch (reading.roll()) {
                        case CLEAN_LOCK -> {
                            locks++;
                            check(Math.abs(reading.dx() - tx) < 1e-9 && Math.abs(reading.dz() - tz) < 1e-9, "a clean lock is exact");
                        }
                        case STATIC_SURGE -> surges++;
                        default -> {
                            rough++;
                            double turn = Math.toDegrees(Math.abs(Math.atan2(reading.dx() * tz - reading.dz() * tx, reading.dx() * tx + reading.dz() * tz)));
                            check(turn <= maxTurn + 1e-6, "a rough reading is off by no more than the signal allows: " + turn + " of " + maxTurn);
                            check(Math.abs(reading.distance() / trueDist - 1.0) <= maxStretch + 1e-6, "in distance too: " + reading.distance() + " for " + trueDist);
                        }
                    }
                }
                check(locks > 50 && surges > 50 && rough > 2000, "mostly rough readings, with clean locks and static among them: " + rough + " / " + locks + " / " + surges);
                hero.setYRot(0.0F);         // facing south
                check(com.thefarawaydev.realmsofchaos.world.Scanner.arrow(hero, 0, 100).equals("↑") && com.thefarawaydev.realmsofchaos.world.Scanner.arrow(hero, 0, -100).equals("↓")
                        && com.thefarawaydev.realmsofchaos.world.Scanner.arrow(hero, 100, 0).equals("←") && com.thefarawaydev.realmsofchaos.world.Scanner.arrow(hero, -100, 100).equals("↗"),
                        "the arrow is given as the player faces: up is straight ahead");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.bars(0.0).equals("▯▯▯▯▯") && com.thefarawaydev.realmsofchaos.world.Scanner.bars(1.0).equals("▮▮▮▮▮"), "five bars of signal");

                // ---- a scan, start to finish, by right-click
                ModContent.CHAOS_COMPASS.get().use(level, hero, InteractionHand.MAIN_HAND);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero) && hero.position().distanceTo(spot) < 0.01, "right-click begins a scan and goes nowhere");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.last(hero) == null, "nothing is read until the needle first rolls");
                var read = com.thefarawaydev.realmsofchaos.world.Scanner.pulse(hero, level, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.HUB);
                check(read != null && com.thefarawaydev.realmsofchaos.world.Scanner.last(hero) == read, "each roll is the reading shown");
                check(com.thefarawaydev.realmsofchaos.world.Scanner.line(hero, level, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.HUB, read, 60) != null, "and has its line above the hotbar");
                advance(server, 25);
                com.thefarawaydev.realmsofchaos.world.Scanner.tick(server);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero), "a scan runs for a minute or more");
                com.thefarawaydev.realmsofchaos.world.Scanner.expire(hero);
                com.thefarawaydev.realmsofchaos.world.Scanner.tick(server);
                check(!com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero), "then it is over");
                check(!com.thefarawaydev.realmsofchaos.world.Scanner.start(hero, com.thefarawaydev.realmsofchaos.world.Scanner.Mode.HUB), "and the needle needs a moment to settle");
                advance(server, com.thefarawaydev.realmsofchaos.world.Scanner.COOLDOWN + 1);
                // sneaking turns the compass to its next setting instead
                hero.setShiftKeyDown(true);
                ModContent.CHAOS_COMPASS.get().use(level, hero, InteractionHand.MAIN_HAND);
                check(com.thefarawaydev.realmsofchaos.world.Scanner.mode(hero.getMainHandItem()) == com.thefarawaydev.realmsofchaos.world.Scanner.Mode.KEYSTONES && !com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero),
                        "sneak and right-click turns it to the keystone shrines");
                hero.setShiftKeyDown(false);
                check(com.thefarawaydev.realmsofchaos.item.ChaosCompassItem.scan(hero) && com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero), "/roc scan begins one too");
                com.thefarawaydev.realmsofchaos.world.Scanner.reset();
                // inside the realms there is nothing to scan for
                Realm.testAlias(Level.OVERWORLD, Realm.HUB);
                ModContent.CHAOS_COMPASS.get().use(level, hero, InteractionHand.MAIN_HAND);
                check(!com.thefarawaydev.realmsofchaos.world.Scanner.scanning(hero), "inside the realms the compass has nothing to scan for");
            } finally {
                Realm.testAlias(Level.OVERWORLD, null);
                for (BlockPos relay : relays) level.setBlock(relay, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(farRelay, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(jammer, Blocks.AIR.defaultBlockState(), 3);
                data.unmarkPrefix("net|");
                com.thefarawaydev.realmsofchaos.world.Scanner.reset();
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            }
        });

        step("waygates: touch one to learn it, stand still five seconds, a pearl a journey, and the hub only for the attuned", () -> {
            Realm.testAlias(Level.OVERWORLD, null);
            com.thefarawaydev.realmsofchaos.world.Waygates.reset();
            com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            data.unmarkPrefix("net|");
            data.unmarkPrefix("seen|");
            ServerPlayer hero = fakePlayer(server, level, "RocTraveller");
            data.reset(hero.getUUID());
            data.unmarkBuilt("attuned/" + hero.getUUID());
            Block obelisk = ModContent.WAYGATE_OBELISK.get();
            BlockPos a = new BlockPos(7100, 120, 7100), b = new BlockPos(7300, 120, 7100);
            var pearl = net.minecraft.world.item.Items.ENDER_PEARL;
            try {
                for (BlockPos at : new BlockPos[]{a, b}) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            level.setBlock(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                            for (int dy = 0; dy <= 2; dy++) level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    level.setBlock(at, obelisk.defaultBlockState(), 3);
                }
                Vec3 byA = new Vec3(a.getX() + 0.5, a.getY(), a.getZ() + 1.5);
                hero.setPos(byA.x, byA.y, byA.z);
                obelisk.setPlacedBy(level, a, obelisk.defaultBlockState(), hero, ItemStack.EMPTY);
                check(com.thefarawaydev.realmsofchaos.world.Network.discovered(hero).size() == 1, "whoever raises an obelisk knows it");
                check(com.thefarawaydev.realmsofchaos.world.Waygates.destinations(hero, a).isEmpty(), "an obelisk on its own leads nowhere");
                check(Screens.commands(Screens.waygate(hero, level, a)).isEmpty(), "and its screen offers nothing to someone no gate knows");
                // touching a second one makes it a place to go
                com.thefarawaydev.realmsofchaos.block.FieldBlock.inspect(level, b, hero, com.thefarawaydev.realmsofchaos.world.Network.Kind.WAYGATE);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.destinations(hero, a).size() == 1 && com.thefarawaydev.realmsofchaos.world.Waygates.destinations(hero, b).size() == 1, "touching an obelisk makes it a place to travel to");
                String code = com.thefarawaydev.realmsofchaos.world.Network.at(level, b, com.thefarawaydev.realmsofchaos.world.Network.Kind.WAYGATE).code();
                String command = "roc ui waygate " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + code;
                check(Screens.commands(Screens.waygate(hero, level, a)).contains("/" + command), "and it is on the first one's screen");

                check(!com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, a, code), "no pearl, no journey");
                hero.getInventory().add(new ItemStack(pearl, 2));
                hero.setPos(byA.x + 30, byA.y, byA.z);
                check(!com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, a, code), "the traveller has to stand by the obelisk");
                hero.setPos(byA.x, byA.y, byA.z);
                check(!com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, a, "1_2_3"), "and name a waygate they know");

                // moving breaks the channel, and costs nothing
                server.getCommands().performPrefixedCommand(hero.createCommandSourceStack(), command);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero), "the screen's button begins the channel");
                for (int i = 0; i < 10; i++) com.thefarawaydev.realmsofchaos.world.Waygates.tick(server);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero) && hero.position().distanceTo(byA) < 0.01, "nothing happens at once");
                hero.setPos(byA.x + 2, byA.y, byA.z);
                com.thefarawaydev.realmsofchaos.world.Waygates.tick(server);
                check(!com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero) && count(hero, pearl) == 2, "moving breaks the channel and costs nothing");
                // so does being hit
                hero.setPos(byA.x, byA.y, byA.z);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, a, code), "begun again");
                hero.setHealth(hero.getHealth() - 3.0F);
                com.thefarawaydev.realmsofchaos.world.Waygates.tick(server);
                check(!com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero) && hero.position().distanceTo(byA) < 0.01, "being hit breaks it too");
                // five seconds undisturbed
                hero.setHealth(hero.getMaxHealth());
                check(com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, a, code), "and again");
                int needed = com.thefarawaydev.realmsofchaos.world.HubTiers.channelTicks(server);
                check(needed == 100, "five seconds: " + needed);
                for (int i = 0; i < needed - 1; i++) com.thefarawaydev.realmsofchaos.world.Waygates.tick(server);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero) && hero.position().distanceTo(byA) < 0.01, "not a tick sooner");
                com.thefarawaydev.realmsofchaos.world.Waygates.tick(server);
                check(!com.thefarawaydev.realmsofchaos.world.Waygates.channelling(hero) && hero.position().distanceTo(Vec3.atBottomCenterOf(b)) < 2.0, "then the traveller stands by the other obelisk: " + hero.position());
                check(open(level, hero.blockPosition()) && open(level, hero.blockPosition().above()) && solid(level, hero.blockPosition().below()), "on free ground");
                check(count(hero, pearl) == 1, "one pearl paid on arrival");

                check(!com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, b, com.thefarawaydev.realmsofchaos.world.Waygates.HUB), "the hub is closed to someone no gate knows");
                Access.attune(hero);
                String toHub = "/roc ui waygate " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + com.thefarawaydev.realmsofchaos.world.Waygates.HUB;
                check(Screens.commands(Screens.waygate(hero, level, b)).contains(toHub), "once a gate knows them the obelisk offers the hub");
                check(com.thefarawaydev.realmsofchaos.world.Waygates.begin(hero, b, com.thefarawaydev.realmsofchaos.world.Waygates.HUB), "and begins the channel there");
                com.thefarawaydev.realmsofchaos.world.Waygates.reset();

                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 4);
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.channelTicks(server) == 60 && com.thefarawaydev.realmsofchaos.world.Waygates.price(server) == 1, "tier IV: three seconds");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 5);
                hero.getInventory().clearContent();
                check(com.thefarawaydev.realmsofchaos.world.Waygates.price(server) == 0 && com.thefarawaydev.realmsofchaos.world.Waygates.canPay(hero), "tier V: the waygates ask for nothing");
                // an obelisk that is broken is nowhere to go
                obelisk.playerWillDestroy(level, a, obelisk.defaultBlockState(), hero);
                level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
                check(com.thefarawaydev.realmsofchaos.world.Waygates.destinations(hero, b).isEmpty() && com.thefarawaydev.realmsofchaos.world.Network.discovered(hero).size() == 1, "a broken obelisk is forgotten by everyone");
            } finally {
                for (BlockPos at : new BlockPos[]{a, b}) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            for (int dy = -1; dy <= 2; dy++) level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                com.thefarawaydev.realmsofchaos.world.Waygates.reset();
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
                data.unmarkPrefix("net|");
                data.unmarkPrefix("seen|");
                data.unmarkBuilt("attuned/" + hero.getUUID());
            }
        });

        step("the Field Tower Bag: a starter kit, contents that stay in the bag, no bag in a bag, quick-deploy, six rows at tier III", () -> {
            Realm.testAlias(Level.OVERWORLD, null);
            com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            data.unmarkPrefix("net|");
            ServerPlayer hero = fakePlayer(server, level, "RocPacker");
            Item relayItem = ModContent.BLOCK_ITEMS.get("relay_core").get(), gateItem = ModContent.BLOCK_ITEMS.get("waygate_obelisk").get();
            BlockPos ground = new BlockPos(7400, 119, 7400);
            try {
                ItemStack bag = com.thefarawaydev.realmsofchaos.item.FieldBagItem.starter();
                var inside = com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag);
                check(com.thefarawaydev.realmsofchaos.item.FieldBagItem.used(bag) == 2 && inside.get(0).is(relayItem) && inside.get(0).getCount() == 2 && inside.get(1).is(gateItem),
                        "a new bag holds two relay cores and a waygate obelisk");
                hero.setItemInHand(InteractionHand.MAIN_HAND, bag);
                com.thefarawaydev.realmsofchaos.ui.BagMenu.open(hero, InteractionHand.MAIN_HAND);
                check(hero.containerMenu instanceof com.thefarawaydev.realmsofchaos.ui.BagMenu, "right-click opens the bag");
                var menu = (com.thefarawaydev.realmsofchaos.ui.BagMenu) hero.containerMenu;
                check(menu.rows() == 3 && menu.slots.size() == 27 + 36 && menu.getSlot(0).getItem().is(relayItem), "three rows, showing what the bag holds");
                menu.setCarried(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
                menu.clicked(5, 0, ContainerInput.PICKUP, hero);
                check(menu.getCarried().isEmpty() && com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag).get(5).is(net.minecraft.world.item.Items.DIAMOND)
                        && com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag).get(5).getCount() == 3, "what is put in a slot is written straight into the bag");
                // the open bag cannot be moved, by any kind of click
                int selected = hero.getInventory().getSelectedSlot(), bagSlot = -1;
                for (int i = 0; i < menu.slots.size(); i++) {
                    if (menu.slots.get(i).container == hero.getInventory() && menu.slots.get(i).getContainerSlot() == selected) bagSlot = i;
                }
                check(bagSlot >= 27, "test setup: the bag's own slot is on screen");
                check(menu.forbidden(bagSlot, 0, ContainerInput.PICKUP) && menu.forbidden(bagSlot, 0, ContainerInput.THROW) && menu.forbidden(bagSlot, 0, ContainerInput.QUICK_MOVE)
                        && menu.forbidden(3, selected, ContainerInput.SWAP), "the open bag cannot be picked up, thrown, shift-clicked or swapped away");
                menu.clicked(bagSlot, 0, ContainerInput.PICKUP, hero);
                check(menu.getCarried().isEmpty() && hero.getMainHandItem() == bag, "it stays in the hand");
                ItemStack other = new ItemStack(ModContent.FIELD_BAG.get());
                menu.setCarried(other);
                menu.clicked(7, 0, ContainerInput.PICKUP, hero);
                check(menu.getCarried() == other && com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag).get(7).isEmpty(), "no bag goes inside a bag");
                menu.setCarried(ItemStack.EMPTY);
                check(!menu.forbidden(2, 0, ContainerInput.PICKUP) && !menu.forbidden(30, 0, ContainerInput.QUICK_MOVE), "everything else moves as in any chest");
                hero.closeContainer();
                check(com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(hero.getMainHandItem()).get(5).is(net.minecraft.world.item.Items.DIAMOND), "closed, the bag still holds it");

                // quick-deploy: sneaking, the first network block in the bag is set down
                level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 1; dy <= 3; dy++) level.setBlock(ground.above(dy), Blocks.AIR.defaultBlockState(), 3);
                hero.setPos(7400.5, 120, 7403.5);
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false);
                var click = new net.minecraft.world.item.context.UseOnContext(level, hero, InteractionHand.MAIN_HAND, bag, hit);
                check(bag.getItem().useOn(click) == InteractionResult.PASS && level.getBlockState(ground.above()).isAir(), "without sneaking the bag leaves the block alone");
                hero.setShiftKeyDown(true);
                bag.getItem().useOn(click);
                hero.setShiftKeyDown(false);
                check(level.getBlockState(ground.above()).is(ModContent.RELAY_CORE.get()), "sneak and right-click sets down the first relay in the bag");
                check(com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag).get(0).getCount() == 1, "taken out of the bag");
                var set = com.thefarawaydev.realmsofchaos.world.Network.at(level, ground.above(), com.thefarawaydev.realmsofchaos.world.Network.Kind.RELAY);
                check(set != null && set.owner().equals(hero.getUUID()), "and it joins the network under its owner");

                // six rows from tier III; what is in the lower rows is kept if the hub should ever stand lower
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 3);
                com.thefarawaydev.realmsofchaos.ui.BagMenu.open(hero, InteractionHand.MAIN_HAND);
                menu = (com.thefarawaydev.realmsofchaos.ui.BagMenu) hero.containerMenu;
                check(menu.rows() == 6 && menu.slots.size() == 54 + 36, "six rows once the hub has reached its third tier");
                menu.setCarried(new ItemStack(net.minecraft.world.item.Items.EMERALD));
                menu.clicked(40, 0, ContainerInput.PICKUP, hero);
                hero.closeContainer();
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
                com.thefarawaydev.realmsofchaos.ui.BagMenu.open(hero, InteractionHand.MAIN_HAND);
                check(((com.thefarawaydev.realmsofchaos.ui.BagMenu) hero.containerMenu).rows() == 3, "test setup: back to three rows");
                hero.closeContainer();
                check(com.thefarawaydev.realmsofchaos.item.FieldBagItem.contents(bag).get(40).is(net.minecraft.world.item.Items.EMERALD), "the lower rows are kept, only out of sight");
            } finally {
                hero.closeContainer();
                for (int dy = 0; dy <= 3; dy++) level.setBlock(ground.above(dy), Blocks.AIR.defaultBlockState(), 3);
                data.unmarkPrefix("net|");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            }
        });

        step("the hub rises through five tiers on what players hand over, and the Bounty Board pays for work done", () -> {
            com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
            data.clearCounters("bounty/");
            data.unmarkPrefix("bountyclaim/");
            ServerPlayer giver = fakePlayer(server, level, "RocGiver");
            var iron = net.minecraft.world.item.Items.IRON_INGOT;
            try {
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.tier(server) == 1 && com.thefarawaydev.realmsofchaos.world.HubTiers.costs(1).size() == 2
                        && com.thefarawaydev.realmsofchaos.world.HubTiers.costs(5).isEmpty(), "the hub starts at its first tier, and the fifth asks for nothing more");
                check(!com.thefarawaydev.realmsofchaos.world.Bounties.open(server) && Screens.commands(Screens.bounties(giver)).isEmpty(), "the Bounty Board is shut until the second");
                giver.getInventory().add(new ItemStack(iron, 10));
                giver.getInventory().add(new ItemStack(ModContent.materialItem(Line.HADES, 1), 8));
                giver.getInventory().add(new ItemStack(net.minecraft.world.item.Items.DIRT, 5));
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.deposit(giver) == 18 && com.thefarawaydev.realmsofchaos.world.HubTiers.tier(server) == 1, "what is asked for is taken; ten ingots are not sixteen");
                check(count(giver, iron) == 0 && count(giver, net.minecraft.world.item.Items.DIRT) == 5, "and nothing else is");
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.deposited(server, com.thefarawaydev.realmsofchaos.world.HubTiers.costs(1).get(0)) == 10, "the pile is kept");
                giver.getInventory().add(new ItemStack(iron, 20));
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.deposit(giver) == 6 && count(giver, iron) == 14, "only what is still missing is taken");
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.tier(server) == 2, "the pile complete, the hub rises");
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.deposited(server, com.thefarawaydev.realmsofchaos.world.HubTiers.costs(2).get(0)) == 0, "and the pile is spent");
                check(Math.abs(com.thefarawaydev.realmsofchaos.world.HubTiers.calibration(server) - 0.05) < 1e-9 && com.thefarawaydev.realmsofchaos.world.HubTiers.bagRows(server) == 3
                        && com.thefarawaydev.realmsofchaos.world.Bounties.open(server), "tier II: a steadier compass, and the Bounty Board opens");
                List<String> buttons = Screens.commands(Screens.hubCommand(giver));
                check(buttons.contains("/roc ui hubcore deposit") && buttons.contains("/roc ui bounty"), "Hub Command has the button that hands things over: " + buttons);
                // the button only works at the heart, in the hub
                giver.getInventory().add(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 16));
                Realm.testAlias(Level.OVERWORLD, null);
                server.getCommands().performPrefixedCommand(giver.createCommandSourceStack(), "roc ui hubcore deposit");
                check(count(giver, net.minecraft.world.item.Items.GOLD_INGOT) == 16, "nothing is handed over from outside the hub");
                Realm.testAlias(Level.OVERWORLD, Realm.HUB);
                server.getCommands().performPrefixedCommand(giver.createCommandSourceStack(), "roc ui hubcore deposit");
                check(count(giver, net.minecraft.world.item.Items.GOLD_INGOT) == 0 && com.thefarawaydev.realmsofchaos.world.HubTiers.tier(server) == 2, "in the hub it is");

                // sanctuary
                giver.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                com.thefarawaydev.realmsofchaos.world.HubTiers.sanctuary(giver, level);
                check(giver.hasEffect(MobEffects.REGENERATION) && giver.hasEffect(MobEffects.POISON), "from tier II the hub mends whoever stands in it");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 3);
                com.thefarawaydev.realmsofchaos.world.HubTiers.sanctuary(giver, level);
                check(!giver.hasEffect(MobEffects.POISON) && com.thefarawaydev.realmsofchaos.world.HubTiers.bagRows(server) == 6, "from tier III it lifts every harm, and bags grow");
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 5);
                check(com.thefarawaydev.realmsofchaos.world.HubTiers.deposit(giver) == 0 && com.thefarawaydev.realmsofchaos.world.HubTiers.waygatesFree(server)
                        && com.thefarawaydev.realmsofchaos.world.HubTiers.rise(server) == 5, "the fifth tier is the last");

                // ---- bounties
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 2);
                var posted = com.thefarawaydev.realmsofchaos.world.Bounties.today(server);
                check(posted.size() == 3 && posted.equals(com.thefarawaydev.realmsofchaos.world.Bounties.today(server)), "three bounties, the same every time you look");
                check(posted.get(0).kind() != posted.get(1).kind() && posted.get(1).kind() != posted.get(2).kind() && posted.get(0).kind() != posted.get(2).kind(), "each of a different kind");
                boolean changes = false;
                for (long day = 1; day <= 6; day++) changes |= !com.thefarawaydev.realmsofchaos.world.Bounties.on(level.getSeed(), day).equals(com.thefarawaydev.realmsofchaos.world.Bounties.on(level.getSeed(), 0));
                check(changes, "and different ones on other days");
                var bounty = posted.get(0);
                check(com.thefarawaydev.realmsofchaos.world.Bounties.claim(giver, 0).isEmpty(), "nothing is paid before the work is done");
                com.thefarawaydev.realmsofchaos.world.Bounties.count(giver, bounty.kind(), bounty.goal() - 1);
                check(com.thefarawaydev.realmsofchaos.world.Bounties.progress(giver, bounty) == bounty.goal() - 1 && com.thefarawaydev.realmsofchaos.world.Bounties.claim(giver, 0).isEmpty(), "nor one short of it");
                com.thefarawaydev.realmsofchaos.world.Bounties.count(giver, bounty.kind(), 5);
                check(com.thefarawaydev.realmsofchaos.world.Bounties.progress(giver, bounty) == bounty.goal(), "work past the goal is not counted");
                check(Screens.commands(Screens.bounties(giver)).contains("/roc ui bounty claim 0"), "a finished bounty has its button on the board");
                int had = count(giver, bounty.payment().getItem());
                server.getCommands().performPrefixedCommand(giver.createCommandSourceStack(), "roc ui bounty claim 0");
                check(count(giver, bounty.payment().getItem()) == had + bounty.payment().getCount() && com.thefarawaydev.realmsofchaos.world.Bounties.claimed(giver, bounty), "the button pays it out");
                check(com.thefarawaydev.realmsofchaos.world.Bounties.claim(giver, 0).isEmpty() && com.thefarawaydev.realmsofchaos.world.Bounties.claim(giver, 7).isEmpty(), "once");
                ServerPlayer other = fakePlayer(server, level, "RocOtherHunter");
                check(com.thefarawaydev.realmsofchaos.world.Bounties.progress(other, bounty) == 0, "everyone earns their own");
                // real work counts: a sentinel felled by a player
                var sentinels = posted.stream().filter(p -> p.kind() == com.thefarawaydev.realmsofchaos.world.Bounties.Kind.SENTINELS).findFirst();
                if (sentinels.isPresent()) {
                    Sentinel guard = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                    guard.setUp(Sentinel.Kind.of(Line.ZEUS, Sentinel.Role.SOLDIER), 1);
                    guard.setPos(0.5, Tower.floorY(3) + 1, 0.5);
                    level.addFreshEntity(guard);
                    int done = com.thefarawaydev.realmsofchaos.world.Bounties.progress(other, sentinels.get());
                    guard.hurtServer(level, level.damageSources().playerAttack(other), 100000.0F);
                    check(com.thefarawaydev.realmsofchaos.world.Bounties.progress(other, sentinels.get()) == done + 1, "a sentinel felled counts towards the bounty");
                    for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(-4, Tower.floorY(3) - 2, -4, 4, Tower.floorY(3) + 4, 4), e -> true)) drop.discard();
                }
            } finally {
                giver.removeAllEffects();
                com.thefarawaydev.realmsofchaos.world.HubTiers.setTier(server, 1);
                data.clearCounters("bounty/");
                data.unmarkPrefix("bountyclaim/");
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("rune chambers: stepping in floods and shuts the room, the runes pressed in order drain it, and it lets go and repairs itself", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            com.thefarawaydev.realmsofchaos.world.Traps.forget();
            data.unmarkPrefix("trapdone/");
            Passages.Plan found = null;
            Passages.Cell chamber = null;
            for (int index = 1; index <= Passages.COUNT && chamber == null; index++) {
                for (int stage = 0; stage < Passages.stages(level, Line.ZEUS, index) && chamber == null; stage++) {
                    Passages.Plan candidate = Passages.plan(level, Line.ZEUS, index, stage);
                    for (Passages.Cell c : candidate.cells()) {
                        if (candidate.kind(c) == Passages.Kind.TRAP && chamber == null) {
                            found = candidate;
                            chamber = c;
                        }
                    }
                }
            }
            check(chamber != null, "test setup: a rune chamber somewhere in the tower of Zeus");
            final Passages.Plan plan = found;
            final Passages.Cell cell = chamber;
            // (in the test world the three towers' passages share one place: build this one afresh)
            Passages.build(level, plan);
            BlockPos m = plan.centre(cell);
            Vec3 middle = new Vec3(m.getX() + 0.5, m.getY() + 1, m.getZ() + 0.5);
            List<BlockPos> doors = com.thefarawaydev.realmsofchaos.world.Traps.doors(plan, cell);
            ServerPlayer hero = fakePlayer(server, level, "RocRuneReader"), friend = fakePlayer(server, level, "RocRuneFriend"), late = fakePlayer(server, level, "RocRuneLate");
            Item prizeItem = com.thefarawaydev.realmsofchaos.world.Traps.prize(plan).getItem();
            int prizeCount = com.thefarawaydev.realmsofchaos.world.Traps.prize(plan).getCount();
            java.util.function.BooleanSupplier dry = () -> {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        for (int y = 1; y <= Passages.HEADROOM + 1; y++) if (level.getBlockState(m.offset(dx, y, dz)).is(Blocks.WATER)) return false;
                    }
                }
                return true;
            };
            java.util.function.BooleanSupplier open = () -> {
                for (BlockPos door : doors) if (!level.getBlockState(door).isAir()) return false;
                return true;
            };
            level.addNewPlayer(hero);
            level.addNewPlayer(friend);
            level.addNewPlayer(late);
            try {
                friend.setPos(900, 300, 900);
                late.setPos(900, 300, 900);
                for (int n = 0; n < com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.RUNES; n++) {
                    BlockPos at = com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, n);
                    BlockState state = level.getBlockState(at);
                    check(state.is(ModContent.RUNE_STONE.get()) && state.getValue(com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.RUNE) == n
                            && !state.getValue(com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.LIT) && solid(level, at.below()), "rune stone " + n + " stands dark on its pillar");
                }
                long ways = plan.neighbours(cell).stream().filter(n -> plan.connected(cell, n)).count();
                check(ways >= 1 && doors.size() == ways * 9 && open.getAsBoolean() && dry.getAsBoolean(), "its " + ways + " doorways stand open and it is dry");

                // nothing happens at the edge of the room, and the runes sleep
                hero.setPos(middle.x + 3.5, middle.y, middle.z);
                settle(level);
                com.thefarawaydev.realmsofchaos.world.Traps.service(hero, level, plan, cell);
                check(com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null, "walking along the wall does not spring it");
                check(!com.thefarawaydev.realmsofchaos.world.Traps.press(level, com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, 0), hero), "the runes sleep until it does");

                // the middle springs it
                hero.setPos(middle.x, middle.y, middle.z);
                com.thefarawaydev.realmsofchaos.world.Traps.service(hero, level, plan, cell);
                var trap = com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell);
                check(trap != null && trap.order().length == com.thefarawaydev.realmsofchaos.world.Traps.length(plan), "stepping into the middle springs it");
                for (BlockPos door : doors) check(solid(level, door), "every doorway is shut: " + door);
                int[] order = trap.order();
                for (int i = 1; i < order.length; i++) check(order[i] != order[i - 1], "no rune twice running");
                int beat = com.thefarawaydev.realmsofchaos.world.Traps.GLOW + com.thefarawaydev.realmsofchaos.world.Traps.GAP, wait = com.thefarawaydev.realmsofchaos.world.Traps.SHOW_DELAY;
                check(com.thefarawaydev.realmsofchaos.world.Traps.shown(order, 0) == -1 && com.thefarawaydev.realmsofchaos.world.Traps.shown(order, wait) == order[0]
                        && com.thefarawaydev.realmsofchaos.world.Traps.shown(order, wait + com.thefarawaydev.realmsofchaos.world.Traps.GLOW) == -1
                        && com.thefarawaydev.realmsofchaos.world.Traps.shown(order, wait + beat) == order[1]
                        && com.thefarawaydev.realmsofchaos.world.Traps.shown(order, wait + com.thefarawaydev.realmsofchaos.world.Traps.SHOW_EVERY) == order[0], "the order is shown rune by rune, and again");
                for (int i = 0; i <= wait; i++) com.thefarawaydev.realmsofchaos.world.Traps.step(trap);
                check(level.getBlockState(com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, order[0])).getValue(com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.LIT), "the first rune of the order lights");
                for (int i = 0; i < com.thefarawaydev.realmsofchaos.world.Traps.fillEvery(plan) && trap.water() < 1; i++) com.thefarawaydev.realmsofchaos.world.Traps.step(trap);
                check(trap.water() == 1 && level.getBlockState(m.offset(2, 1, 0)).is(Blocks.WATER) && level.getBlockState(m.offset(2, 2, 0)).isAir(), "the water rises a layer at a time");

                // a wrong rune costs a layer and the progress made
                BlockPos wrong = com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, (order[0] + 1) % 4);
                check(!com.thefarawaydev.realmsofchaos.world.Traps.press(level, wrong, hero) && trap.water() == 2 && trap.pressed() == 0, "a wrong rune brings the water up a layer");
                check(com.thefarawaydev.realmsofchaos.world.Traps.press(level, com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, order[0]), hero) && trap.pressed() == 1, "the right one counts");
                com.thefarawaydev.realmsofchaos.world.Traps.press(level, com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, (order[1] + 1) % 4), hero);
                check(trap.pressed() == 0 && trap.water() == 3, "a mistake part-way and the order has to be begun again");

                // the order pressed right drains it, opens it and pays everyone inside
                friend.setPos(middle.x + 1, middle.y, middle.z);
                for (int n : order) check(com.thefarawaydev.realmsofchaos.world.Traps.press(level, com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, n), hero), "the order, rune by rune");
                check(com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null && dry.getAsBoolean() && open.getAsBoolean(), "pressed right, the room drains and opens");
                for (int n = 0; n < 4; n++) {
                    check(!level.getBlockState(com.thefarawaydev.realmsofchaos.world.Traps.rune(plan, cell, n)).getValue(com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.LIT), "and its runes go dark");
                }
                check(count(hero, prizeItem) == prizeCount && count(friend, prizeItem) == prizeCount, "everyone in the room is paid");
                check(com.thefarawaydev.realmsofchaos.world.Traps.solvedBy(plan, cell, hero) && com.thefarawaydev.realmsofchaos.world.Traps.solvedBy(plan, cell, friend), "and it is remembered");
                com.thefarawaydev.realmsofchaos.world.Traps.service(hero, level, plan, cell);
                check(com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null, "it never springs for someone who has solved it");

                // someone else: left alone it fills, holds, and lets go by itself
                hero.setPos(900, 300, 900);
                friend.setPos(900, 300, 900);
                late.setPos(middle.x, middle.y, middle.z);
                com.thefarawaydev.realmsofchaos.world.Traps.service(late, level, plan, cell);
                trap = com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell);
                check(trap != null, "it springs for the next who has not");
                for (int i = 0; i < 20 * 200 && com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == trap; i++) {
                    late.damageCooldownTime = 0;
                    com.thefarawaydev.realmsofchaos.world.Traps.step(trap);
                }
                check(com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null && trap.full() && trap.water() == com.thefarawaydev.realmsofchaos.world.Traps.LAYERS, "left alone it fills, holds, and lets go");
                check(late.isAlive() && late.getHealth() < late.getMaxHealth(), "having hurt its catch, not killed it: " + late.getHealth());
                check(dry.getAsBoolean() && open.getAsBoolean() && !com.thefarawaydev.realmsofchaos.world.Traps.solvedBy(plan, cell, late) && count(late, prizeItem) == 0, "drained and open, and nothing paid");

                // a server stop mid-flood: the room is put right the next time someone walks in
                late.setHealth(late.getMaxHealth());
                com.thefarawaydev.realmsofchaos.world.Traps.service(late, level, plan, cell);
                trap = com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell);
                check(trap != null, "it springs again for someone it let go");
                for (int i = 0; i < com.thefarawaydev.realmsofchaos.world.Traps.fillEvery(plan); i++) com.thefarawaydev.realmsofchaos.world.Traps.step(trap);
                com.thefarawaydev.realmsofchaos.world.Traps.forget();
                check(!dry.getAsBoolean() && !open.getAsBoolean(), "test setup: the server stopped with the room flooded and shut");
                late.setPos(middle.x + 3.5, middle.y, middle.z);
                com.thefarawaydev.realmsofchaos.world.Traps.service(late, level, plan, cell);
                check(dry.getAsBoolean() && open.getAsBoolean() && com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null, "a room left flooded is put right when someone walks in");

                // and it resets the moment nobody is left inside
                late.setPos(middle.x, middle.y, middle.z);
                com.thefarawaydev.realmsofchaos.world.Traps.service(late, level, plan, cell);
                trap = com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell);
                late.setPos(900, 300, 900);
                com.thefarawaydev.realmsofchaos.world.Traps.step(trap);
                check(com.thefarawaydev.realmsofchaos.world.Traps.active(plan, cell) == null && dry.getAsBoolean() && open.getAsBoolean(), "nobody left inside: it resets for whoever comes next");
            } finally {
                for (ServerPlayer pl : new ServerPlayer[]{hero, friend, late}) pl.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                com.thefarawaydev.realmsofchaos.world.Traps.forget();
                com.thefarawaydev.realmsofchaos.world.Traps.restore(level, plan, cell);
                data.unmarkPrefix("trapdone/");
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("the sidebar: off until asked for, each player's own, and it follows what is going on", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            ServerPlayer watcher = fakePlayer(server, level, "RocWatcher");
            watcher.setPos(0.5, Tower.floorY(4) + 1, 6.5);
            data.unmarkBuilt("sidebar/" + watcher.getUUID());
            clearBosses(level);
            try {
                check(!com.thefarawaydev.realmsofchaos.ui.Sidebar.enabled(watcher) && !com.thefarawaydev.realmsofchaos.ui.Sidebar.showing(watcher), "nobody has a sidebar they did not ask for");
                check(Screens.commands(Screens.home(watcher)).contains("/roc ui sidebar"), "the Codex has the switch");
                server.getCommands().performPrefixedCommand(watcher.createCommandSourceStack(), "roc sidebar");
                check(com.thefarawaydev.realmsofchaos.ui.Sidebar.enabled(watcher) && com.thefarawaydev.realmsofchaos.ui.Sidebar.showing(watcher), "/roc sidebar turns it on");
                int quiet = com.thefarawaydev.realmsofchaos.ui.Sidebar.lines(watcher).size();
                check(quiet >= 3 && com.thefarawaydev.realmsofchaos.ui.Sidebar.shownLines(watcher) == quiet, "it says where you are and how the hub stands: " + quiet + " lines");
                // a trial close by adds its phase
                GodBoss boss = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                boss.setGod(Tower.god(Line.ZEUS, 4));
                Vec3 home = Tower.centre(level, 4);
                boss.setPos(home.x, home.y, home.z);
                boss.setHome(home);
                level.addFreshEntity(boss);
                settle(level);
                com.thefarawaydev.realmsofchaos.ui.Sidebar.update(watcher);
                int fighting = com.thefarawaydev.realmsofchaos.ui.Sidebar.shownLines(watcher);
                check(fighting == quiet + 2, "a trial close by adds the god and its phase: " + fighting);
                boss.setHealth(boss.getMaxHealth() * 0.6F);
                boss.updatePhase(level);
                com.thefarawaydev.realmsofchaos.ui.Sidebar.update(watcher);
                check(com.thefarawaydev.realmsofchaos.ui.Sidebar.shownLines(watcher) == fighting + 1, "and, behind its aegis, how many pylons stand");
                boss.discard();
                check(com.thefarawaydev.realmsofchaos.ui.Sidebar.lines(watcher).size() <= com.thefarawaydev.realmsofchaos.ui.Sidebar.MAX_LINES, "never longer than the screen is tall");
                com.thefarawaydev.realmsofchaos.ui.Sidebar.toggle(watcher);
                check(!com.thefarawaydev.realmsofchaos.ui.Sidebar.enabled(watcher) && !com.thefarawaydev.realmsofchaos.ui.Sidebar.showing(watcher), "and off again");
            } finally {
                clearBosses(level);
                data.unmarkBuilt("sidebar/" + watcher.getUUID());
                for (var bolt : level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, new AABB(-24, Tower.floorY(4) - 3, -24, 24, Tower.floorY(4) + 17, 24), e -> true)) bolt.discard();
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("sentinels cover for each other and make trouble: every trait does its one thing", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            final int floor = 6;
            TowerBuilder.buildFloor(level, Line.ZEUS, floor);
            int y0 = Tower.floorY(floor);
            AABB near = new AABB(-24, y0 - 4, -24, 24, y0 + 14, 24);
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, near, e -> !(e instanceof ServerPlayer))) e.discard();
            ServerPlayer hero = fakePlayer(server, level, "RocTroubled");
            level.addNewPlayer(hero);
            double high = Sentinel.threat(14, 2);
            var damageAttr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
            java.util.function.BiFunction<Sentinel.Kind, double[], Sentinel> put = (kind, at) -> {
                Sentinel made = ModContent.SENTINEL.get().create(level, EntitySpawnReason.TRIGGERED);
                made.setUp(kind, 3, high, false);
                made.setPos(at[0], ground(level, floor, at[0], at[1]), at[1]);
                level.addFreshEntity(made);
                made.setTarget(hero);
                return made;
            };
            com.thefarawaydev.realmsofchaos.world.Hazards.clear();
            try {
                // ---- the roster
                for (Line line : Line.values()) {
                    List<Sentinel.Kind> kinds = Sentinel.Kind.of(line);
                    Set<Sentinel.Trait> traits = new HashSet<>();
                    int plain = 0;
                    for (Sentinel.Kind k : kinds) {
                        if (k.trait == Sentinel.Trait.NONE) plain++;
                        else check(traits.add(k.trait) && Sentinel.Kind.of(line, k.trait) == k, line + ": one kind to a trait: " + k);
                    }
                    check(kinds.size() == 21 && plain == 6 && traits.size() == 15, line + ": twenty-one kinds, fifteen of them with a trait of their own: " + kinds.size());
                    check(Sentinel.Kind.pool(line, Sentinel.Role.SOLDIER, 0.0).size() == 2 && Sentinel.Kind.pool(line, Sentinel.Role.SOLDIER, high).size() == 3
                            && Sentinel.Kind.pool(line, Sentinel.Role.KEEPER, high).size() == 1, line + ": the deeper the level, the more kinds it can field");
                    check(Sentinel.Kind.pool(line, Sentinel.Role.CASTER, high).get(0) == Sentinel.Kind.of(line, Sentinel.Role.CASTER), line + ": the plain kind is always among them");
                    Set<Sentinel.Kind> seen = new HashSet<>();
                    java.util.Random dice = new java.util.Random(5);
                    for (int i = 0; i < 300; i++) seen.add(Sentinel.Kind.pick(line, Sentinel.Role.CASTER, high, dice));
                    check(seen.size() == 6 && seen.equals(new HashSet<>(Sentinel.Kind.pool(line, Sentinel.Role.CASTER, high))), line + ": every caster turns up: " + seen);
                }
                hero.setPos(6.5, ground(level, floor, 6.5, 0.5), 0.5);
                settle(level);
                var blow = level.damageSources().playerAttack(hero);

                // ---- shield: blows from the front glance off, and those beside it are sheltered
                Sentinel shield = put.apply(Sentinel.Kind.AEGIS_BEARER, new double[]{0.5, 0.5});
                float whole = shield.getHealth();
                shield.yBodyRot = -90.0F;                               // facing the hero
                check(shield.facing(hero.position()), "test setup: the shield faces the hero");
                shield.hurtServer(level, blow, 20.0F);
                float front = whole - shield.getHealth();
                shield.setHealth(whole);
                shield.damageCooldownTime = 0;
                shield.yBodyRot = 90.0F;                                // its back to them
                shield.hurtServer(level, blow, 20.0F);
                float behind = whole - shield.getHealth();
                check(front > 0 && behind > front * 1.8F, "a shield-bearer shrugs off blows from its front: " + front + " against " + behind);
                Sentinel sheltered = put.apply(Sentinel.Kind.STORM_HOPLITE, new double[]{0.5, 2.5});
                float h0 = sheltered.getHealth();
                sheltered.hurtServer(level, blow, 20.0F);
                float covered = h0 - sheltered.getHealth();
                shield.discard();
                sheltered.setHealth(h0);
                sheltered.damageCooldownTime = 0;
                sheltered.hurtServer(level, blow, 20.0F);
                float bare = h0 - sheltered.getHealth();
                check(covered > 0 && covered < bare * 0.8F, "and whoever stands beside one is hurt less: " + covered + " against " + bare);

                // ---- link: a warden takes half of what lands on its allies
                Sentinel warden = put.apply(Sentinel.Kind.OATHKEEPER, new double[]{0.5, -3.5});
                float wardenWhole = warden.getHealth();
                sheltered.setHealth(h0);
                sheltered.damageCooldownTime = 0;
                sheltered.hurtServer(level, blow, 20.0F);
                check(h0 - sheltered.getHealth() < bare * 0.6F && warden.getHealth() < wardenWhole, "a warden takes half of every blow that lands on an ally");
                warden.discard();
                sheltered.discard();

                // ---- frenzy: roused when an ally falls, wild when nearly beaten
                Sentinel maenad = put.apply(Sentinel.Kind.MAENAD, new double[]{0.5, 0.5}), doomed = put.apply(Sentinel.Kind.STORM_HOPLITE, new double[]{2.5, 0.5});
                double calm = maenad.getAttributeValue(damageAttr);
                doomed.hurtServer(level, blow, 100000.0F);
                check(maenad.frenzy() == 1 && maenad.getAttributeValue(damageAttr) > calm * 1.1, "a berserker is roused when an ally falls");
                maenad.setHealth(maenad.getMaxHealth() * 0.45F);
                maenad.hurtServer(level, level.damageSources().magic(), 4.0F);
                check(maenad.isWild() && maenad.hasEffect(MobEffects.STRENGTH), "and goes wild when it is nearly beaten");
                maenad.discard();

                // ---- swap: trades places with a wounded ally, and mends it
                Sentinel dancer = put.apply(Sentinel.Kind.WIND_DANCER, new double[]{-4.5, 0.5}), wounded = put.apply(Sentinel.Kind.STORM_HOPLITE, new double[]{4.5, 0.5});
                check(!dancer.swap(level), "a dancer has nothing to do while nobody is hurt");
                wounded.setHealth(wounded.getMaxHealth() * 0.3F);
                Vec3 dancerAt = dancer.position(), woundedAt = wounded.position();
                check(dancer.swap(level) && dancer.position().distanceTo(woundedAt) < 0.1 && wounded.position().distanceTo(dancerAt) < 0.1 && wounded.hasEffect(MobEffects.REGENERATION),
                        "it trades places with a wounded ally, who starts to mend");
                dancer.discard();
                wounded.discard();

                // ---- bomb: bursts on everyone near
                Sentinel bomb = put.apply(Sentinel.Kind.SPARK_RUNNER, new double[]{5.0, 0.5});
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                hero.setDeltaMovement(Vec3.ZERO);
                check(bomb.fuse() < 0 && bomb.getMaxHealth() < Sentinel.health(Sentinel.Role.SKIRMISHER, high), "a bomber is frail, and quiet until it is on you");
                bomb.burst(level);
                check(bomb.isRemoved() && hero.getHealth() < hero.getMaxHealth() && hero.isAlive() && hero.getDeltaMovement().y > 0.5, "then it bursts: hurt, and thrown into the air");

                // ---- bind: rooted, hauled in or blinded, by tower
                for (Line line : Line.values()) {
                    Sentinel binder = put.apply(Sentinel.Kind.of(line, Sentinel.Trait.BIND), new double[]{0.5, 0.5});
                    hero.removeAllEffects();
                    hero.setDeltaMovement(Vec3.ZERO);
                    binder.rider(level, hero);
                    switch (line) {
                        case ZEUS -> check(hero.hasEffect(MobEffects.SLOWNESS) && hero.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 5, "a Chain Thrower roots you to the spot");
                        case POSEIDON -> check(hero.getDeltaMovement().x < -0.8, "a Net Caster hauls you in: " + hero.getDeltaMovement());
                        case HADES -> check(hero.hasEffect(MobEffects.DARKNESS), "a Shackler blinds you");
                    }
                    if (line == Line.ZEUS) {
                        // and it rides on the bolt
                        hero.removeAllEffects();
                        hero.setHealth(hero.getMaxHealth());
                        hero.damageCooldownTime = 0;
                        binder.performRangedAttack(hero, 1.0F);
                        RelicProjectile chain = (RelicProjectile) level.getEntitiesOfClass(Projectile.class, near, e -> true).get(0);
                        chain.impact(level, binder, hero);
                        check(hero.hasEffect(MobEffects.SLOWNESS) && hero.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 5, "the bind rides on its bolt");
                        chain.discard();
                    }
                    binder.discard();
                }

                // ---- silence: the relic's move will not answer
                Sentinel breaker = put.apply(Sentinel.Kind.ORACLE_BREAKER, new double[]{0.5, 0.5});
                hero.removeAllEffects();
                hero.addEffect(new MobEffectInstance(MobEffects.SPEED, 400));
                GodWeaponItem relic = (GodWeaponItem) ModContent.weaponItem(Gods.byId("ares"));
                check(relic.moveWait(hero, server.getTickCount()) == 0, "test setup: the move is ready");
                breaker.rider(level, hero);
                check(relic.moveWait(hero, server.getTickCount()) > 20 * 5 && !hero.hasEffect(MobEffects.SPEED), "an Oracle Breaker silences your relic and takes a blessing with it");
                advance(server, 20 * 6 + 1);
                check(relic.moveWait(hero, server.getTickCount()) == 0, "for six seconds");
                breaker.discard();

                // ---- disarm: the weapon ends up in the pack, never on the floor
                hero.getInventory().clearContent();
                hero.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                check(Sentinel.disarm(hero) && hero.getMainHandItem().isEmpty() && count(hero, net.minecraft.world.item.Items.IRON_SWORD) == 1, "a thief knocks your weapon into your pack - nothing is lost");
                check(!Sentinel.disarm(hero), "an empty hand has nothing to lose");
                hero.getInventory().clearContent();

                // ---- trap: its bolt leaves ground that hurts, whether or not it hit anyone
                Sentinel sower = put.apply(Sentinel.Kind.STORM_SOWER, new double[]{0.5, 0.5});
                sower.performRangedAttack(hero, 1.0F);
                RelicProjectile seed = (RelicProjectile) level.getEntitiesOfClass(Projectile.class, near, e -> true).get(0);
                Vec3 spot = hero.position();
                seed.land(level, spot);
                var patches = com.thefarawaydev.realmsofchaos.world.Hazards.near(level, spot, 1.0);
                check(patches.size() == 1 && patches.get(0).line() == Line.ZEUS, "a sower's bolt leaves a patch where it lands: " + patches.size());
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                hero.setDeltaMovement(Vec3.ZERO);
                check(com.thefarawaydev.realmsofchaos.world.Hazards.bite(patches.get(0)) == 1 && hero.getHealth() < hero.getMaxHealth() && hero.getDeltaMovement().y > 0.4, "standing in it hurts, and under Zeus throws you up");
                hero.setPos(-10.5, ground(level, floor, -10.5, 0.5), 0.5);
                check(com.thefarawaydev.realmsofchaos.world.Hazards.bite(patches.get(0)) == 0, "stepping out of it is enough");
                seed.discard();
                sower.discard();
                hero.setPos(6.5, ground(level, floor, 6.5, 0.5), 0.5);

                // ---- mirage: three where there was one, and two of them are nothing
                Sentinel mirage = put.apply(Sentinel.Kind.MIRAGE, new double[]{0.5, 0.5});
                mirage.addTag("realms_of_chaos_guardian/3/3");
                List<Sentinel> copies = mirage.split(level);
                check(copies.size() == 2 && mirage.split(level).isEmpty(), "a mirage splits into three, once");
                for (Sentinel copy : copies) {
                    check(copy.isIllusion() && copy.getMaxHealth() == 1.0F && copy.kind() == mirage.kind() && copy.entityTags().contains("realms_of_chaos_guardian/3/3"), "its copies look the same and belong to its room");
                    hero.setHealth(hero.getMaxHealth());
                    check(!copy.doHurtTarget(level, hero) && hero.getHealth() == hero.getMaxHealth(), "but they hurt nobody");
                }
                mirage.hurtServer(level, blow, 100000.0F);
                check(copies.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "when the real one falls its copies are gone");

                // ---- snipe: a heavy shot after a line of light
                Sentinel sniper = put.apply(Sentinel.Kind.BOLT_HURLER, new double[]{-8.5, 0.5});
                sniper.performRangedAttack(hero, 1.0F);
                check(level.getEntitiesOfClass(Projectile.class, near, e -> true).isEmpty(), "a sniper aims first");
                advance(server, Sentinel.AIM_TICKS + 1);
                Scheduler.tick(server);
                List<Projectile> shots = level.getEntitiesOfClass(Projectile.class, near, e -> true);
                check(shots.size() == 1 && ((RelicProjectile) shots.get(0)).damage() > sniper.getAttributeValue(damageAttr) * 2.0, "and then looses one heavy shot");
                shots.forEach(Projectile::discard);
                sniper.discard();

                // ---- horn: everyone in earshot comes
                Sentinel herald = put.apply(Sentinel.Kind.THUNDER_HERALD, new double[]{0.5, 0.5}), idle = put.apply(Sentinel.Kind.STORM_HOPLITE, new double[]{10.5, 5.5});
                idle.setTarget(null);
                check(herald.soundHorn(level) == 1 && idle.getTarget() == hero && idle.hasEffect(MobEffects.SPEED), "a herald's horn sets every sentinel in earshot on you");
            } finally {
                hero.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, near, e -> !(e instanceof ServerPlayer))) e.discard();
                com.thefarawaydev.realmsofchaos.world.Hazards.clear();
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("a levelled relic's bolt breaks the ground where it lands: craters grow with the level, close again and bury nobody", () -> {
            check(com.thefarawaydev.realmsofchaos.world.Craters.radius(1, 0) == 0 && com.thefarawaydev.realmsofchaos.world.Craters.radius(2, 5) == 0
                    && com.thefarawaydev.realmsofchaos.world.Craters.radius(3, 0) == 1 && com.thefarawaydev.realmsofchaos.world.Craters.radius(6, 0) == 2
                    && com.thefarawaydev.realmsofchaos.world.Craters.radius(9, 0) == 3 && com.thefarawaydev.realmsofchaos.world.Craters.radius(10, 5) == 4,
                    "no crater below level 3, then wider at 3, 6 and 9, and one more with three stars");
            Realm.testAlias(Level.OVERWORLD, null);
            com.thefarawaydev.realmsofchaos.world.Craters.closeAll();
            BlockPos c = new BlockPos(7600, 100, 7600);
            AABB field = new AABB(7585, 90, 7585, 7615, 112, 7615);
            ServerPlayer hero = fakePlayer(server, level, "RocCratered");
            level.addNewPlayer(hero);
            try {
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dz = -8; dz <= 8; dz++) {
                        for (int y = 95; y <= 106; y++) level.setBlock(new BlockPos(7600 + dx, y, 7600 + dz), (y <= 100 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                    }
                }
                BlockPos chest = c.offset(2, 0, 0), bedrock = c.offset(-2, 0, 0);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
                level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 2);
                Vec3 hit = new Vec3(7600.5, 101.0, 7600.5);
                int small = com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 1, null, 0.0F);
                check(small >= 1 && small <= 9 && level.getBlockState(c).isAir() && com.thefarawaydev.realmsofchaos.world.Craters.open() == 1, "a small crater: " + small + " blocks");
                com.thefarawaydev.realmsofchaos.world.Craters.closeAll();
                check(level.getBlockState(c).is(Blocks.STONE) && com.thefarawaydev.realmsofchaos.world.Craters.open() == 0, "it closes again");
                int big = com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 4, null, 0.0F);
                check(big > small * 4 && level.getBlockState(c.below()).isAir() && !level.getBlockState(c.below(4)).isAir(), "a big one is wider and deeper, with ground left under it: " + big);
                check(level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockState(bedrock).is(Blocks.BEDROCK), "a chest and what cannot be mined are never taken");
                // someone standing in it is lifted out when it closes
                hero.setPos(7600.5, 98.0, 7600.5);
                settle(level);
                for (int i = 0; i < com.thefarawaydev.realmsofchaos.world.Craters.RESTORE_TICKS; i++) com.thefarawaydev.realmsofchaos.world.Craters.tick(server);
                check(com.thefarawaydev.realmsofchaos.world.Craters.open() == 0, "ten seconds later it has closed");
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        BlockPos p = c.offset(dx, 0, dz);
                        check(p.equals(chest) || p.equals(bedrock) || level.getBlockState(p).is(Blocks.STONE), "the ground is whole again at " + p);
                    }
                }
                check(hero.getY() >= 100.99 && level.noCollision(hero), "and whoever stood in it was lifted out, not buried: " + hero.position());

                // a crater that was open when the server stopped is closed when it starts again
                com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 2, null, 0.0F);
                com.thefarawaydev.realmsofchaos.world.Craters.forget();
                check(level.getBlockState(c).isAir(), "test setup: the server stopped with a crater open");
                com.thefarawaydev.realmsofchaos.world.Craters.tick(server);
                check(level.getBlockState(c).is(Blocks.STONE) && data.keys("crater|").isEmpty(), "it is closed when the server starts again");

                // the blast shakes whoever is near - but not the one who made it; and out in the world a player's crater stays
                var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                zombie.setPos(7602.5, 101.0, 7600.5);
                level.addFreshEntity(zombie);
                hero.setPos(7599.5, 101.0, 7600.5);
                hero.setHealth(hero.getMaxHealth());
                settle(level);
                float zh = zombie.getHealth();
                int dug = com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 2, hero, 6.0F);
                check(zombie.getHealth() < zh && hero.getHealth() == hero.getMaxHealth(), "the blast hurts what is near, never the one who made it");
                check(dug > 0 && com.thefarawaydev.realmsofchaos.world.Craters.open() == 0 && level.getBlockState(c).isAir() && data.keys("crater|").isEmpty(), "a player's crater out in the world is not put back");
                for (int i = 0; i < com.thefarawaydev.realmsofchaos.world.Craters.RESTORE_TICKS + 5; i++) com.thefarawaydev.realmsofchaos.world.Craters.tick(server);
                check(level.getBlockState(c).isAir() && level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockState(bedrock).is(Blocks.BEDROCK), "it stays - though chests and bedrock are still never taken");
                zombie.discard();
                // someone who may not build there (adventure mode) leaves the ground alone for good: theirs closes again
                level.setBlock(c, Blocks.STONE.defaultBlockState(), 2);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = 97; y <= 100; y++) {
                    BlockPos fill = new BlockPos(7600 + dx, y, 7600 + dz);
                    if (level.getBlockState(fill).isAir()) level.setBlock(fill, Blocks.STONE.defaultBlockState(), 2);
                }
                hero.setGameMode(GameType.ADVENTURE);
                com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 2, hero, 0.0F);
                check(com.thefarawaydev.realmsofchaos.world.Craters.open() == 1, "a visitor in adventure mode cannot dig: their crater is only for show");
                com.thefarawaydev.realmsofchaos.world.Craters.closeAll();
                check(level.getBlockState(c).is(Blocks.STONE), "and closes again");
                hero.setGameMode(GameType.SURVIVAL);

                // the bolt carries it: a levelled relic's shot has a crater in it, and makes it wherever it comes down
                God caster = null;
                for (God g : Gods.ALL) if (caster == null && g.kind().ranged() && g.kind() != WeaponKind.BOW && g.kind() != WeaponKind.THROWN) caster = g;
                check(caster != null, "test setup: a god whose relic shoots bolts");
                ItemStack fresh = new ItemStack(ModContent.weaponItem(caster)), forged = new ItemStack(ModContent.weaponItem(caster));
                WeaponProgress.setLevel(forged, 9);
                hero.setPos(7600.5, 101.0, 7592.5);
                Projectile plain = com.thefarawaydev.realmsofchaos.god.Ranged.fire(level, hero, caster, fresh, 5.0F, null);
                Projectile heavy = com.thefarawaydev.realmsofchaos.god.Ranged.fire(level, hero, caster, forged, 5.0F, null);
                check(plain instanceof RelicProjectile a && a.crater() == 0 && heavy instanceof RelicProjectile b && b.crater() == 3, "a relic's level decides the crater its bolt carries");
                ((RelicProjectile) plain).land(level, hit);
                check(level.getBlockState(c).is(Blocks.STONE), "a new relic's bolt leaves the ground alone");
                ((RelicProjectile) heavy).land(level, hit);
                check(level.getBlockState(c).isAir(), "a forged one breaks it - with nobody there to hit");
                plain.discard();
                heavy.discard();
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int y = 96; y <= 100; y++) {
                    BlockPos fill = new BlockPos(7600 + dx, y, 7600 + dz);
                    if (level.getBlockState(fill).isAir()) level.setBlock(fill, Blocks.STONE.defaultBlockState(), 2);
                }

                // inside the realms a crater is only ever one block deep
                Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
                com.thefarawaydev.realmsofchaos.world.Craters.blast(level, hit, 4, null, 0.0F);
                check(level.getBlockState(c).isAir() && level.getBlockState(c.below()).is(Blocks.STONE), "in a tower a crater is one block deep, however wide");
                com.thefarawaydev.realmsofchaos.world.Craters.closeAll();
            } finally {
                com.thefarawaydev.realmsofchaos.world.Craters.closeAll();
                hero.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, field, e -> !(e instanceof ServerPlayer))) e.discard();
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dz = -8; dz <= 8; dz++) {
                        for (int y = 95; y <= 106; y++) level.setBlock(new BlockPos(7600 + dx, y, 7600 + dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("passages have one to ten levels, named by wing, and the next level is built ahead a little each tick", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.UNDERWORLD);
            Set<Integer> counts = new HashSet<>();
            int most = 0, total = 0;
            for (Line line : Line.values()) {
                for (int index = 1; index <= Passages.COUNT; index++) {
                    int n = Passages.stages(level.getSeed(), line, index);
                    check(n >= 1 && n <= Passages.MAX_STAGES && n == Passages.plan(level, line, index, 0).stages, line + " " + index + ": between one and ten levels: " + n);
                    counts.add(n);
                    most = Math.max(most, n);
                    total += n;
                }
            }
            check(counts.size() >= 5, "passages differ in length: " + counts);
            RESULTS.add("        levels in this world: " + total + " across 42 passages, the longest " + most + "; lengths seen " + new java.util.TreeSet<>(counts));
            Set<Long> lengths = new HashSet<>();
            for (long seed = 1; seed <= 200; seed++) lengths.add((long) Passages.stages(seed, Line.ZEUS, 13));
            check(lengths.contains(4L) && lengths.contains(10L), "near the top a passage has anything from four to ten levels: " + lengths);
            check(Passages.plan(7L, Line.ZEUS, 13, 0).label().equals("13-A1"), "the first level of passage 13 is 13-A1");
            for (long seed = 1; seed <= 40; seed++) {
                if (Passages.stages(seed, Line.ZEUS, 13) < 5) continue;
                Passages.Plan fifth = Passages.plan(seed, Line.ZEUS, 13, 4);
                check(fifth.label().equals("13-B2") && fifth.wing() == 1 && Passages.plan(seed, Line.ZEUS, 13, 2).label().equals("13-A3"), "wings of three: the fifth level is 13-B2");
                break;
            }
            // building ahead
            data.unmarkPrefix("passage/hades/9/");
            check(!Passages.isBuilt(level, Line.HADES, 9, 0), "test setup: a level that is not standing");
            check(Passages.prepare(level, Line.HADES, 9, 0) && Passages.pending() == 1 && !Passages.prepare(level, Line.HADES, 9, 0), "a level is queued once");
            check(!Passages.prepare(level, Line.HADES, 9, 99) && !Passages.prepare(level, Line.HADES, 0, 0), "only levels that exist");
            int ticks = 0;
            long worst = 0;
            while (Passages.pending() > 0 && ticks++ < 5000) {
                long began = System.nanoTime();
                Passages.tick(server);
                worst = Math.max(worst, System.nanoTime() - began);
            }
            check(Passages.pending() == 0 && Passages.isBuilt(level, Line.HADES, 9, 0), "it gets built, a little each tick");
            checkPassage(level, Passages.plan(level, Line.HADES, 9, 0), "built ahead: ");
            RESULTS.add("        a level built ahead took " + ticks + " ticks, the longest of them " + worst / 1_000_000 + " ms");
            // a player who gets there first does not wait for the queue: what is left is done at once
            data.unmarkPrefix("passage/hades/9/");
            Passages.prepare(level, Line.HADES, 9, 0);
            Passages.tick(server);
            Passages.ensure(level, Line.HADES, 9, 0);
            check(Passages.pending() == 0 && Passages.isBuilt(level, Line.HADES, 9, 0), "arriving early finishes the job on the spot");
            checkPassage(level, Passages.plan(level, Line.HADES, 9, 0), "finished early: ");
            Passages.build(level, Passages.plan(level, Line.HADES, 9, 0));
            RESULTS.add("        the same level built in one go took " + Passages.lastBuildMillis + " ms");
            Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
        });

        step("puzzle rooms keep their caches locked until they are solved: a vault, a chime room, a beam room and a trembling floor", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            com.thefarawaydev.realmsofchaos.world.Rooms.forget();
            ServerPlayer hero = fakePlayer(server, level, "RocSolver");
            level.addNewPlayer(hero);
            try {
                // ---- the vault: nine tiles, lights out
                Object[] found = room(level, Line.ZEUS, Passages.Kind.VAULT);
                Passages.Plan vault = (Passages.Plan) found[0];
                Passages.Cell vc = (Passages.Cell) found[1];
                Passages.build(level, vault);
                data.unmarkPrefix("puzzle/");
                data.unmarkPrefix("loot/");
                BlockPos vm = vault.centre(vc), cache = com.thefarawaydev.realmsofchaos.world.Rooms.cache(vault, vc);
                hero.setPos(vm.getX() + 0.5, vm.getY() + 1, vm.getZ() + 3.5);
                settle(level);
                check(level.getBlockState(cache).is(ModContent.PASSAGE_CACHE.get()) && !com.thefarawaydev.realmsofchaos.world.Rooms.allLit(level, vault, vc)
                        && !com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, vault, vc), "a vault starts locked, with some of its tiles dark");
                check(!Passages.loot(level, cache, hero), "its cache will not open");
                boolean[] lit = new boolean[9];
                for (int i = 0; i < 9; i++) lit[i] = level.getBlockState(com.thefarawaydev.realmsofchaos.world.Rooms.glyph(vault, vc, i % 3, i / 3)).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT);
                // pressing a tile turns it and the ones beside it
                com.thefarawaydev.realmsofchaos.world.Rooms.pressGlyph(level, com.thefarawaydev.realmsofchaos.world.Rooms.glyph(vault, vc, 0, 0), hero);
                for (int i = 0; i < 9; i++) {
                    boolean now = level.getBlockState(com.thefarawaydev.realmsofchaos.world.Rooms.glyph(vault, vc, i % 3, i / 3)).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT);
                    check((now != lit[i]) == (i == 0 || i == 1 || i == 3), "a corner tile turns itself and its two neighbours: tile " + i);
                    lit[i] = now;
                }
                // every vault can be solved: find the presses that light them all, and make them
                int answer = -1;
                for (int mask = 0; mask < 512 && answer < 0; mask++) {
                    boolean[] sim = lit.clone();
                    for (int t = 0; t < 9; t++) {
                        if ((mask >> t & 1) == 0) continue;
                        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int x = t % 3 + d[0], z = t / 3 + d[1];
                            if (x >= 0 && z >= 0 && x < 3 && z < 3) sim[x + z * 3] = !sim[x + z * 3];
                        }
                    }
                    boolean all = true;
                    for (boolean b : sim) all &= b;
                    if (all) answer = mask;
                }
                check(answer > 0, "the vault has a solution");
                for (int t = 0; t < 9; t++) if ((answer >> t & 1) == 1) com.thefarawaydev.realmsofchaos.world.Rooms.pressGlyph(level, com.thefarawaydev.realmsofchaos.world.Rooms.glyph(vault, vc, t % 3, t / 3), hero);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.allLit(level, vault, vc) && com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, vault, vc), "all nine lit: the vault is solved");
                check(Passages.loot(level, cache, hero) && !Passages.loot(level, cache, hero), "and its cache opens, once for each player");
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.pressGlyph(level, com.thefarawaydev.realmsofchaos.world.Rooms.glyph(vault, vc, 1, 1), hero) && com.thefarawaydev.realmsofchaos.world.Rooms.allLit(level, vault, vc), "a solved vault stays as it is");
                // re-sealing the passage locks and scrambles it again
                Passages.rearm(level, vault);
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, vault, vc) && !com.thefarawaydev.realmsofchaos.world.Rooms.allLit(level, vault, vc), "a passage that seals itself again locks its puzzles again");

                // ---- the chime room: four crystals against the clock
                found = room(level, Line.ZEUS, Passages.Kind.CHIMES);
                Passages.Plan chimes = (Passages.Plan) found[0];
                Passages.Cell cc = (Passages.Cell) found[1];
                Passages.build(level, chimes);
                data.unmarkPrefix("puzzle/");
                BlockPos[] crystal = new BlockPos[4];
                for (int n = 0; n < 4; n++) {
                    crystal[n] = com.thefarawaydev.realmsofchaos.world.Rooms.chime(chimes, cc, n);
                    check(level.getBlockState(crystal[n]).is(ModContent.CHIME_CRYSTAL.get()) && !level.getBlockState(crystal[n]).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT), "crystal " + n + " stands silent");
                }
                for (int n = 0; n < 3; n++) check(!com.thefarawaydev.realmsofchaos.world.Rooms.pressChime(level, crystal[n], hero), "three of four is not enough");
                check(level.getBlockState(crystal[1]).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT), "a rung crystal keeps ringing");
                advance(server, com.thefarawaydev.realmsofchaos.world.Rooms.chimeTicks(chimes) + 1);
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.pressChime(level, crystal[3], hero) && !level.getBlockState(crystal[0]).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT),
                        "too slow: the fourth, rung late, only begins again");
                for (int n = 0; n < 2; n++) com.thefarawaydev.realmsofchaos.world.Rooms.pressChime(level, crystal[n], hero);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.pressChime(level, crystal[2], hero) && com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, chimes, cc), "all four inside the time: solved");
                check(Passages.loot(level, com.thefarawaydev.realmsofchaos.world.Rooms.cache(chimes, cc), hero), "and the cache opens");

                // ---- the beam room: two mirrors
                found = room(level, Line.ZEUS, Passages.Kind.BEAM);
                Passages.Plan beams = (Passages.Plan) found[0];
                Passages.Cell bc = (Passages.Cell) found[1];
                Passages.build(level, beams);
                data.unmarkPrefix("puzzle/");
                var beam = com.thefarawaydev.realmsofchaos.world.Rooms.beam(beams, bc);
                check(level.getBlockState(beam.emitter()).is(ModContent.BEAM_EMITTER.get()) && level.getBlockState(beam.receiver()).is(ModContent.BEAM_RECEIVER.get())
                        && level.getBlockState(beam.first()).is(ModContent.BEAM_MIRROR.get()) && level.getBlockState(beam.second()).is(ModContent.BEAM_MIRROR.get()), "an emitter, two mirrors and a receiver");
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.trace(level, beams, bc).reached() && !com.thefarawaydev.realmsofchaos.world.Rooms.trace(level, beams, bc).path().isEmpty(), "to begin with the beam goes astray");
                boolean reached = false;
                BlockPos[] turns = {beam.first(), beam.second(), beam.first()};
                for (int i = 0; i < 3 && !reached; i++) reached = com.thefarawaydev.realmsofchaos.world.Rooms.turnMirror(level, turns[i], hero);
                check(reached && com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, beams, bc) && com.thefarawaydev.realmsofchaos.world.Rooms.trace(level, beams, bc).reached(), "turned the right way, the beam reaches the receiver");
                check(level.getBlockState(beam.first()).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT) == beam.firstTurned()
                        && level.getBlockState(beam.second()).getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT) == beam.secondTurned(), "by the one setting that does it");
                check(Passages.loot(level, com.thefarawaydev.realmsofchaos.world.Rooms.cache(beams, bc), hero), "and the cache opens");

                // ---- the trembling floor: one path holds
                found = room(level, Line.ZEUS, Passages.Kind.PLATES);
                Passages.Plan floor = (Passages.Plan) found[0];
                Passages.Cell fc = (Passages.Cell) found[1];
                Passages.build(level, floor);
                data.unmarkPrefix("puzzle/");
                BlockPos fm = floor.centre(fc);
                List<BlockPos> plates = com.thefarawaydev.realmsofchaos.world.Rooms.plates(floor, fc), path = com.thefarawaydev.realmsofchaos.world.Rooms.path(floor, fc);
                check(plates.size() == 24 && path.size() >= 2 && plates.containsAll(path) && new HashSet<>(path).size() == path.size(), "twenty-four plates, and a path among them: " + path.size());
                for (int i = 1; i < path.size(); i++) check(path.get(i).distManhattan(path.get(i - 1)) == 1, "the path goes plate to plate");
                check(Math.max(Math.abs(path.get(0).getX() - fm.getX()), Math.abs(path.get(0).getZ() - fm.getZ())) == 2
                        && Math.max(Math.abs(path.get(path.size() - 1).getX() - fm.getX()), Math.abs(path.get(path.size() - 1).getZ() - fm.getZ())) == 1, "from the outer ring in to beside the cache");
                for (BlockPos plate : plates) check(level.getBlockState(plate).is(ModContent.MEMORY_PLATE.get()), "a plate at " + plate);
                BlockPos wrong = null;
                for (BlockPos plate : plates) if (wrong == null && !path.contains(plate)) wrong = plate;
                hero.setPos(wrong.getX() + 0.5, wrong.getY() + 1, wrong.getZ() + 0.5);
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.stepPlate(level, wrong, hero) && hero.getHealth() < hero.getMaxHealth() && hero.getDeltaMovement().y > 0.3, "a plate off the path throws you off");
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                check(!com.thefarawaydev.realmsofchaos.world.Rooms.stepPlate(level, path.get(path.size() - 1), hero) && hero.getHealth() < hero.getMaxHealth(), "so does jumping to the end of it");
                hero.setHealth(hero.getMaxHealth());
                boolean walked = false;
                for (BlockPos plate : path) walked = com.thefarawaydev.realmsofchaos.world.Rooms.stepPlate(level, plate, hero);
                check(walked && hero.getHealth() == hero.getMaxHealth() && com.thefarawaydev.realmsofchaos.world.Rooms.solved(level, floor, fc), "walked in order from its first plate, it holds");
                check(Passages.loot(level, com.thefarawaydev.realmsofchaos.world.Rooms.cache(floor, fc), hero), "and the cache opens");
            } finally {
                hero.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                com.thefarawaydev.realmsofchaos.world.Rooms.forget();
                data.unmarkPrefix("puzzle/");
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("trap halls, urns and tablets: a crusher's marked row, darts from the walls, what an urn hides, what a tablet tells", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            com.thefarawaydev.realmsofchaos.world.Rooms.forget();
            ServerPlayer hero = fakePlayer(server, level, "RocCareful");
            level.addNewPlayer(hero);
            AABB wide = null;
            try {
                // ---- the crusher hall
                Object[] found = room(level, Line.ZEUS, Passages.Kind.CRUSHER);
                Passages.Plan hall = (Passages.Plan) found[0];
                Passages.Cell hc = (Passages.Cell) found[1];
                Passages.build(level, hall);
                wide = hall.box().inflate(4);
                BlockPos hm = hall.centre(hc);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.crusherRow(hall, hc) == -5, "nothing is marked until someone is there");
                int row = com.thefarawaydev.realmsofchaos.world.Rooms.markRow(level, hall, hc);
                check(row == -4, "the first row marked is the one by the north wall: " + row);
                hero.setPos(hm.getX() + 0.5, hm.getY() + 1, hm.getZ() + row + 0.5);
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                settle(level);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.slam(level, hall, hc) == 1 && hero.getHealth() < hero.getMaxHealth() && hero.isAlive(), "whoever stays on the marked row is crushed, not killed");
                hero.setHealth(hero.getMaxHealth());
                hero.damageCooldownTime = 0;
                check(com.thefarawaydev.realmsofchaos.world.Rooms.markRow(level, hall, hc) == -3, "then the next row is marked");
                check(com.thefarawaydev.realmsofchaos.world.Rooms.slam(level, hall, hc) == 0 && hero.getHealth() == hero.getMaxHealth(), "and the row that just fell is the safe place to stand");
                for (int i = 0; i < 12; i++) check(Math.abs(com.thefarawaydev.realmsofchaos.world.Rooms.markRow(level, hall, hc)) <= 4, "the mark sweeps the hall end to end and back");

                // ---- the dart hall
                found = room(level, Line.ZEUS, Passages.Kind.DARTS);
                Passages.Plan darts = (Passages.Plan) found[0];
                Passages.Cell dc = (Passages.Cell) found[1];
                Passages.build(level, darts);
                wide = wide.minmax(darts.box().inflate(4));
                BlockPos dm = darts.centre(dc), tile = null;
                int tiles = 0;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (!level.getBlockState(dm.offset(dx, 0, dz)).is(ModContent.DART_TILE.get())) continue;
                        tiles++;
                        if (tile == null) tile = dm.offset(dx, 0, dz);
                    }
                }
                check(tiles >= 6 && tiles <= 40, "a dart hall has marked tiles, and safe ones between them: " + tiles);
                hero.setPos(tile.getX() + 0.5, tile.getY() + 1, tile.getZ() + 0.5);
                settle(level);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.stepDart(level, tile, hero) == 2, "a marked tile looses two darts");
                List<Projectile> loosed = level.getEntitiesOfClass(Projectile.class, darts.cellBox(dc).inflate(1), e -> e instanceof Arrow);
                check(loosed.size() == 2 && loosed.stream().allMatch(a -> a.getDeltaMovement().horizontalDistance() > 0.5), "out of the walls, at whoever trod on it: " + loosed.size());
                check(com.thefarawaydev.realmsofchaos.world.Rooms.stepDart(level, tile, hero) == 0, "then the tile rests");
                advance(server, com.thefarawaydev.realmsofchaos.world.Rooms.DART_REST + 1);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.stepDart(level, tile, hero) == 2, "for two seconds");
                check(com.thefarawaydev.realmsofchaos.world.Rooms.stepDart(level, dm.offset(0, 0, 6), hero) == 0, "a tile that is not in a dart hall does nothing");

                // ---- urns: loot, nothing, or an ambush - fixed by where the urn stands
                Set<com.thefarawaydev.realmsofchaos.world.Rooms.Urn> seen = EnumSet.noneOf(com.thefarawaydev.realmsofchaos.world.Rooms.Urn.class);
                for (int i = 0; i < 400 && seen.size() < 3; i++) {
                    BlockPos at = dm.offset(i % 7 - 3, 1 + (i / 49) % 3, (i / 7) % 7 - 3);
                    var holds = com.thefarawaydev.realmsofchaos.world.Rooms.urn(level.getSeed(), at);
                    if (!level.getBlockState(at).isAir() || !seen.add(holds)) continue;
                    level.setBlock(at, ModContent.ANCIENT_URN.get().defaultBlockState(), 2);
                    int drops = level.getEntitiesOfClass(ItemEntity.class, darts.cellBox(dc).inflate(2), e -> true).size();
                    int hiding = level.getEntitiesOfClass(Sentinel.class, darts.cellBox(dc).inflate(2), e -> true).size();
                    check(com.thefarawaydev.realmsofchaos.world.Rooms.smashUrn(level, at, hero) == holds && level.getBlockState(at).isAir(), "an urn smashes, and holds what its place says");
                    int dropped = level.getEntitiesOfClass(ItemEntity.class, darts.cellBox(dc).inflate(2), e -> true).size() - drops;
                    int sprung = level.getEntitiesOfClass(Sentinel.class, darts.cellBox(dc).inflate(2), e -> true).size() - hiding;
                    switch (holds) {
                        case LOOT -> check(dropped >= 1 && sprung == 0, "a little loot");
                        case EMPTY -> check(dropped == 0 && sprung == 0, "or nothing");
                        case AMBUSH -> check(sprung == 1 && dropped == 0, "or the sentinel that was hiding in it");
                    }
                }
                check(seen.size() == 3, "all three turn up: " + seen);
                check(com.thefarawaydev.realmsofchaos.world.Rooms.smashUrn(level, dm.above(3), hero) == com.thefarawaydev.realmsofchaos.world.Rooms.Urn.EMPTY, "smashing air smashes nothing");

                // ---- a lore tablet
                BlockPos tablet = dm.offset(2, 1, 2);
                level.setBlock(tablet, ModContent.LORE_TABLET.get().defaultBlockState(), 2);
                int xp = hero.totalExperience;
                int line = com.thefarawaydev.realmsofchaos.world.Rooms.readTablet(level, tablet, hero);
                check(line >= 1 && line <= com.thefarawaydev.realmsofchaos.world.Rooms.LORE_LINES && hero.totalExperience > xp, "a tablet tells a line of the tower's story and teaches a little");
                xp = hero.totalExperience;
                check(com.thefarawaydev.realmsofchaos.world.Rooms.readTablet(level, tablet, hero) == line && hero.totalExperience == xp, "the same line every time, and the lesson only once");
                level.setBlock(tablet, Blocks.AIR.defaultBlockState(), 2);
            } finally {
                hero.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                if (wide != null) for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, wide, e -> !(e instanceof ServerPlayer))) e.discard();
                com.thefarawaydev.realmsofchaos.world.Rooms.forget();
                data.unmarkPrefix("lore/");
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("the Passage Chart is a map with fog of war, and the mastery tree spends what a climber has earned", () -> {
            Realm.testAlias(Level.OVERWORLD, Realm.OLYMPUS);
            ServerPlayer hero = fakePlayer(server, level, "RocCartographer");
            data.reset(hero.getUUID());
            data.clearCounters("skill/" + hero.getUUID() + "/");
            var maxHealth = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
            try {
                // ---- the chart
                check(com.thefarawaydev.realmsofchaos.world.Charts.give(hero) && !com.thefarawaydev.realmsofchaos.world.Charts.give(hero) && com.thefarawaydev.realmsofchaos.world.Charts.holds(hero), "a climber is handed one chart");
                check(com.thefarawaydev.realmsofchaos.world.Charts.id(hero).equals(com.thefarawaydev.realmsofchaos.world.Charts.id(hero)) && com.thefarawaydev.realmsofchaos.world.Charts.data(hero).locked, "always the same map, and nobody's but the mod's to draw on");
                Passages.Plan plan = Passages.plan(level, Line.ZEUS, 6, 0);
                Passages.visited(hero, plan).clear();
                hero.setPos(plan.arrival().x, plan.arrival().y, plan.arrival().z);
                check(com.thefarawaydev.realmsofchaos.world.Charts.draw(hero, level, plan) == 0, "a level nobody has walked is blank parchment");
                byte fog = com.thefarawaydev.realmsofchaos.world.Charts.fog();
                for (Passages.Cell c : plan.cells()) {
                    int[] px = com.thefarawaydev.realmsofchaos.world.Charts.pixel(plan, c);
                    check(com.thefarawaydev.realmsofchaos.world.Charts.at(hero, px[0], px[1]) == fog, "fog over " + c);
                }
                Passages.visited(hero, plan).add(plan.start);
                check(com.thefarawaydev.realmsofchaos.world.Charts.draw(hero, level, plan) == 1, "the first room is inked in when it is entered");
                int inked = 0;
                for (Passages.Cell c : plan.cells()) {
                    int[] px = com.thefarawaydev.realmsofchaos.world.Charts.pixel(plan, c);
                    boolean drawn = com.thefarawaydev.realmsofchaos.world.Charts.at(hero, px[0], px[1]) != fog;
                    check(drawn == c.equals(plan.start), "only what has been seen is drawn: " + c);
                    if (drawn) inked++;
                }
                check(inked == 1, "one room on the sheet");
                Passages.Cell next = null;
                for (Passages.Cell n : plan.neighbours(plan.start)) if (next == null && plan.connected(plan.start, n)) next = n;
                Passages.visited(hero, plan).add(next);
                check(com.thefarawaydev.realmsofchaos.world.Charts.draw(hero, level, plan) == 2, "and the next when that is");
                // dump the sheet, so the chart can be looked at outside the game
                StringBuilder sheet = new StringBuilder();
                for (Passages.Cell c : plan.cells()) Passages.visited(hero, plan).add(c);
                com.thefarawaydev.realmsofchaos.world.Charts.draw(hero, level, plan);
                for (int z = 0; z < 128; z++) {
                    for (int x = 0; x < 128; x++) sheet.append(String.format("%08x ", net.minecraft.world.level.material.MapColor.getColorFromPackedId(com.thefarawaydev.realmsofchaos.world.Charts.at(hero, x, z))));
                    sheet.append('\n');
                }
                java.nio.file.Files.writeString(java.nio.file.Path.of("roc-chart.txt"), sheet.toString());
                Passages.visited(hero, plan).clear();
                Passages.visited(hero, plan).add(plan.start);

                // ---- the mastery tree
                check(com.thefarawaydev.realmsofchaos.world.Skills.earned(hero) == 0 && !com.thefarawaydev.realmsofchaos.world.Skills.raise(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.MIGHT), "nothing to spend before anything is earned");
                for (int rank = Line.RANKS; rank > Line.RANKS - 4; rank--) data.markDefeated(hero.getUUID(), Gods.get(Line.ZEUS, rank));
                data.markPassed(hero.getUUID(), Line.ZEUS, 1);
                check(com.thefarawaydev.realmsofchaos.world.Skills.earned(hero) == 5 && com.thefarawaydev.realmsofchaos.world.Skills.available(hero) == 5, "a point for every god woken and every passage walked");
                check(Screens.commands(Screens.skills(hero)).contains("/roc ui skills raise vigor") && Screens.commands(Screens.home(hero)).contains("/roc ui skills"), "the tree is a screen, reached from the Codex");
                double before = hero.getAttributeValue(maxHealth);
                server.getCommands().performPrefixedCommand(hero.createCommandSourceStack(), "roc ui skills raise vigor");
                check(com.thefarawaydev.realmsofchaos.world.Skills.rank(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.VIGOR) == 1 && hero.getAttributeValue(maxHealth) == before + 2.0, "a rank of Vigor is a heart");
                for (int i = 0; i < 3; i++) com.thefarawaydev.realmsofchaos.world.Skills.raise(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.FOCUS);
                check(com.thefarawaydev.realmsofchaos.world.Skills.rank(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.FOCUS) == 3 && !com.thefarawaydev.realmsofchaos.world.Skills.raise(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.FOCUS), "three ranks to a skill");
                check(Math.abs(com.thefarawaydev.realmsofchaos.world.Skills.moveScale(hero) - 0.76) < 1e-9, "Focus brings a relic's move back sooner");
                check(com.thefarawaydev.realmsofchaos.world.Skills.raise(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.CARTOGRAPHER) && com.thefarawaydev.realmsofchaos.world.Skills.available(hero) == 0
                        && !com.thefarawaydev.realmsofchaos.world.Skills.raise(hero, com.thefarawaydev.realmsofchaos.world.Skills.Skill.MIGHT), "and then the points are spent");
                long doors = plan.neighbours(plan.start).stream().filter(n -> plan.connected(plan.start, n)).count();
                check(com.thefarawaydev.realmsofchaos.world.Charts.draw(hero, level, plan) == 1 + doors, "a Cartographer's chart sketches the rooms next door: " + doors);
                check(com.thefarawaydev.realmsofchaos.world.Skills.signal(hero) == 0.0 && com.thefarawaydev.realmsofchaos.world.Skills.channelScale(hero) == 1.0, "what has not been bought does nothing");
                server.getCommands().performPrefixedCommand(hero.createCommandSourceStack(), "roc ui skills reset");
                check(com.thefarawaydev.realmsofchaos.world.Skills.available(hero) == 5 && hero.getAttributeValue(maxHealth) == before && com.thefarawaydev.realmsofchaos.world.Skills.moveScale(hero) == 1.0, "a tree can be taken apart: every point comes back");
            } finally {
                data.reset(hero.getUUID());
                data.clearCounters("skill/" + hero.getUUID() + "/");
                Realm.testAlias(Level.OVERWORLD, Realm.ABYSS);
            }
        });

        step("the menus wear the mod's own artwork: a panel in the title, blank tiles for unused slots, gems for meters", () -> {
            ServerPlayer viewer = fakePlayer(server, level, "RocViewer");
            ClassLoader assets = RealmsOfChaos.class.getClassLoader();
            // every panel is in the font, with a picture of the right height for its menu
            String font;
            try (var in = assets.getResourceAsStream("assets/realms_of_chaos/font/ui.json")) {
                check(in != null, "the menu font");
                font = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            check(font.contains("space") && font.contains("bitmap"), "the font has pictures, and the spaces that pull a panel back to the corner");
            String[] names = {"codex", "forge", "passage", "bag6", "altar", "seal", "deck", "party", "story", "bag3"};
            com.thefarawaydev.realmsofchaos.ui.Skin.Panel[] panels = com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values();
            check(panels.length == names.length, "a picture for every panel");
            for (int i = 0; i < names.length; i++) {
                com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel = panels[i];
                net.minecraft.network.chat.Component title = com.thefarawaydev.realmsofchaos.ui.Skin.title(panel, net.minecraft.network.chat.Component.literal("Name"));
                check(com.thefarawaydev.realmsofchaos.ui.Skin.skinned(title, panel) && title.getString().endsWith("Name"), panel + ": the title is the panel, then the name");
                check(panel.glyph == (char) (0xE000 + i), panel + ": its letter");
                try (var in = assets.getResourceAsStream("assets/realms_of_chaos/textures/gui/panel_" + names[i] + ".png")) {
                    check(in != null, "panel artwork for " + names[i]);
                    var image = javax.imageio.ImageIO.read(in);
                    int rows = (image.getHeight() - 114) / 18;
                    check(image.getWidth() == 176 && rows == panel.rows && font.contains("panel_" + names[i] + ".png"),
                            names[i] + ": a panel the size of its menu: " + image.getWidth() + " x " + image.getHeight() + " for " + panel.rows + " rows");
                    // every slot is a hole in the picture, so an icon can never be hidden by it
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < 9; c++) check((image.getRGB(8 + c * 18 + 8, 18 + r * 18 + 8) >>> 24) == 0, names[i] + ": slot " + r + "," + c + " is open");
                    }
                    check((image.getRGB(16, 161 + (rows - 4) * 18 + 8) >>> 24) == 0 && (image.getRGB(88, 8) >>> 24) == 255, names[i] + ": the hotbar is open and the header is solid");
                }
            }
            for (net.minecraft.world.item.DyeColor dye : net.minecraft.world.item.DyeColor.values()) {
                check(assets.getResource("assets/realms_of_chaos/items/ui/gem_" + dye.getName() + ".json") != null
                        && assets.getResource("assets/realms_of_chaos/textures/item/ui/gem_" + dye.getName() + ".png") != null, "a gem for " + dye.getName());
            }
            check(assets.getResource("assets/realms_of_chaos/items/ui/tile.json") != null, "the blank tile");
            // and the menus use them
            CodexMenu.open(viewer);
            CodexMenu codex = (CodexMenu) viewer.containerMenu;
            int tiles = 0, gems = 0;
            for (int i = 0; i < 54; i++) {
                var model = codex.getSlot(i).getItem().get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
                if (model == null) continue;
                if (model.equals(RealmsOfChaos.id("ui/tile"))) tiles++;
                if (model.getPath().startsWith("ui/gem_")) gems++;
            }
            check(tiles >= 10 && gems >= 3, "the Codex is tiles and gems, not glass panes: " + tiles + " tiles, " + gems + " gems");
            check(codex.getSlot(CodexMenu.SLOT_HUB).getItem().is(ModContent.CHAOS_COMPASS.get()), "its buttons keep their own icons");
            viewer.closeContainer();
        });

        step("live ticking: bosses of every line are left in the world mid-ability for the server to tick", () -> {
            ServerPlayer target = fakePlayer(server, level, "RocTarget");
            target.setPos(4.5, Tower.floorY(5) + 1, 4.5);
            int i = 0;
            for (Line line : Line.values()) {
                for (int prime : new int[]{2, 26}) {
                    GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                    b.setGod(Gods.get(line, 1));
                    Vec3 home = Tower.centre(level, 5);
                    b.setPos(-7.5 + 3 * i++, home.y, -3.5);
                    b.setHome(home);
                    level.addFreshEntity(b);
                    b.setTarget(target);
                    b.primeAbility(prime);
                    if (prime == 26) b.setHealth(b.getMaxHealth() * 0.4F);
                }
            }
        });

        finish();
    }

    /** Travel is throttled by the server tick counter; the self-test runs inside one tick, so it winds the clock. */
    private static void advance(MinecraftServer server, int ticks) throws Exception {
        java.lang.reflect.Field f = MinecraftServer.class.getDeclaredField("tickCount");
        f.setAccessible(true);
        f.setInt(server, f.getInt(server) + ticks);
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    private static void finish() {
        StringBuilder out = new StringBuilder("Realms of Chaos self-test: " + (failures == 0 ? "ALL PASSED" : failures + " FAILED") + "\n");
        for (String line : RESULTS) out.append(line).append('\n');
        System.out.println("[ROC-SELFTEST]\n" + out);
        try {
            Files.writeString(Path.of("roc-selftest.txt"), out.toString());
        } catch (Exception ignored) {
        }
    }
}
