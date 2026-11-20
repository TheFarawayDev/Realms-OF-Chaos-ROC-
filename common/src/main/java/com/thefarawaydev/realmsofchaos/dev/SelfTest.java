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
        int treasure = 0, shrines = 0;
        for (Passages.Cell c : plan.cells()) {
            Passages.Kind kind = plan.kind(c);
            check(kind != null, at + "room without a kind");
            if (kind == Passages.Kind.TREASURE) {
                treasure++;
                check(level.getBlockEntity(plan.featurePos(c)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                        && chest.countItem(ModContent.materialItem(plan.line, plan.tier)) > 0, at + "treasure chest holds this tier's forge material");
            }
            if (kind == Passages.Kind.SHRINE) {
                shrines++;
                check(level.getBlockState(plan.featurePos(c)).is(ModContent.PASSAGE_SHRINE.get()), at + "shrine");
            }
            if (kind == Passages.Kind.SEAL) check(level.getBlockState(plan.sealPos(c)).is(ModContent.PASSAGE_SEAL.get()), at + "seal in place");
        }
        check(treasure == (plan.tier == 1 ? 1 : 2) && shrines == 1, at + "one shrine and the treasure rooms: " + shrines + ", " + treasure);
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
            check(solid(level, here.below()), at + "no floor under " + here);
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
            check(Story.holds(player, Story.Chapter.PROLOGUE), "the Oracle's letter given");
            Welcome.onJoin(player);
            check(count(player, ModContent.CHAOS_CODEX.get()) == 1, "not given twice");
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
                    check(Math.abs(boss.getMaxHealth() - god.bossHealth()) < 0.01, at + "health");
                    check(boss.getMainHandItem().is(ModContent.weaponItem(god)), at + "holds its relic");
                    check(!boss.isInWall() && level.noCollision(boss), at + "boss spawned in free space at " + boss.position());
                    check(boss.position().distanceTo(Tower.centre(level, floor)) < 0.1, at + "boss stands on the floor centre");
                    check(AltarBlock.state(level, player, altar, line) == AltarBlock.State.UNDERWAY, at + "underway");
                    check(AltarBlock.begin(level, player, altar, line) == null, at + "no double summon");
                    boss.keepUnstuck(level);
                    check(boss.position().distanceTo(Tower.centre(level, floor)) < 0.1, at + "boss at home is left alone");

                    if (floor == 2 || floor == 8 || floor == 15) {
                        // living floor: wounding the god makes the terrain shift, then shift back
                        boss.setHealth(boss.getMaxHealth() * 0.6F);
                        boss.updateFloorShift(level);
                        check(FloorShifts.isShifted(line, floor), at + "floor shifts when the god is wounded");
                        for (int i = 0; i < TowerBuilder.MORPH_STEPS; i++) {
                            FloorShifts.stepAll(server, false);
                            check(level.noCollision(boss) && !boss.isInWall(), at + "boss buried by the shift");
                        }
                        boss.keepUnstuck(level);
                        // (nothing ticks inside the self-test, so a boss on lowered ground has not fallen yet: compare x/z)
                        Vec3 c = Tower.centre(level, floor);
                        check(Math.hypot(boss.getX() - c.x, boss.getZ() - c.z) < 1.0 && boss.getY() >= c.y - 0.01, at + "boss still above the centre after the shift");
                        boss.setHealth(boss.getMaxHealth() * 0.3F);
                        boss.updateFloorShift(level);
                        check(!FloorShifts.isShifted(line, floor), at + "floor shifts back when the god is nearly beaten");
                        for (int i = 0; i < TowerBuilder.MORPH_STEPS + 1; i++) FloorShifts.stepAll(server, false);
                        check(level.noCollision(boss), at + "boss buried by the return shift");
                    }
                    boss.hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
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
                        risen.get(0).hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
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
                        Passages.Spot spot = Passages.locate(player);
                        check(spot != null && spot.line() == line && spot.index() == floor, at + "the gate leads into passage " + floor + ", got " + spot);
                        Passages.Plan plan = Passages.plan(level, line, floor);
                        String in = at + "passage: ";
                        check(player.position().distanceTo(plan.arrival()) < 0.1, in + "arrived at its start");
                        checkPassage(level, plan, in);
                        passageRooms[0] += plan.size * plan.size;

                        // its gate is sealed
                        advance(server, 41);
                        check(!Passages.tryExit(player, spot) && Passages.locate(player) != null, in + "the gate refuses while seals hold");
                        boolean thorough = floor == 1 || floor == 6 || floor == 11;
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
                            check(guards.size() == Passages.guardianCount(plan.tier), in + "guardians woke: " + guards.size() + " of " + Passages.guardianCount(plan.tier) + " - tagged mobs nearby: "
                                    + level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, plan.box().inflate(40), e -> e.entityTags().contains(Passages.GUARDIAN_TAG))
                                    .stream().map(e -> e.getType().toShortString() + (e.isAlive() ? "" : "(dead)") + "@" + e.blockPosition().toShortString() + e.entityTags()).toList());
                            for (net.minecraft.world.entity.Mob g : guards) check(level.noCollision(g) && plan.cellAt(g.blockPosition()).equals(c), in + "guardian stands free in its chamber");
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
                            // the map: opened with the Codex, it shows the rooms walked into and hints at the doors leading on
                            Passages.service(player, level, spot);
                            ModContent.CHAOS_CODEX.get().use(level, player, InteractionHand.MAIN_HAND);
                            check(player.containerMenu instanceof com.thefarawaydev.realmsofchaos.ui.PassageMenu, in + "the Codex opens the passage map here");
                            var map = (com.thefarawaydev.realmsofchaos.ui.PassageMenu) player.containerMenu;
                            Set<Passages.Cell> walked = Passages.visited(player, plan);
                            check(walked.contains(plan.start) && walked.containsAll(plan.seals) && walked.size() == plan.seals.size() + 1, in + "rooms walked into are remembered: " + walked);
                            for (Passages.Cell c : plan.cells()) {
                                boolean shown = !map.getSlot(com.thefarawaydev.realmsofchaos.ui.PassageMenu.slotOf(plan, c)).getItem().is(
                                        net.minecraft.world.item.Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.BLACK));
                                boolean hinted = plan.neighbours(c).stream().anyMatch(n -> walked.contains(n) && plan.connected(c, n));
                                check(shown == (walked.contains(c) || hinted), in + "map shows " + c + " only if explored or a door leads there");
                            }
                            map.clicked(com.thefarawaydev.realmsofchaos.ui.PassageMenu.SLOT_CODEX, 0, ContainerInput.PICKUP, player);
                            check(player.containerMenu instanceof CodexMenu, in + "the map leads on to the Codex");
                            player.closeContainer();
                            // the compass points at the gate once the seals are gone; the shrine blesses once
                            check(Passages.point(player, level, spot).equals(plan.centre(plan.exit).above()), in + "the compass points to the gate");
                            Passages.Cell shrine = plan.cells().stream().filter(c -> plan.kind(c) == Passages.Kind.SHRINE).findFirst().orElseThrow();
                            player.setHealth(4.0F);
                            check(ShrineBlock.bless(level, plan.featurePos(shrine), player) && player.getHealth() == player.getMaxHealth()
                                    && !player.getActiveEffects().isEmpty(), in + "the shrine heals and blesses");
                            check(!ShrineBlock.bless(level, plan.featurePos(shrine), player), in + "the shrine rests afterwards");
                            player.removeAllEffects();
                        }
                        // and the passage's gate leads on to the next floor
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
                RESULTS.add("        " + line + ": 14 passages built and walked, " + passageRooms[0] + " rooms in all");
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
                for (Passages.Cell cell : here.cells()) {
                    kinds.add(here.kind(cell));
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
                    Passages.Plan plan = Passages.plan(seed, line, n);
                    check(plan.distances().size() == plan.size * plan.size && !plan.start.equals(plan.exit) && plan.seals.size() == plan.tier + 1,
                            "seed " + seed + " " + line + " " + n + ": unsound plan");
                    check(plan.cells().stream().filter(c -> plan.kind(c) == Passages.Kind.SHRINE).count() == 1
                            && plan.cells().stream().noneMatch(c -> plan.kind(c) == null), "seed " + seed + ": rooms");
                }
            }
            RESULTS.add("        passage 6 of the tower of Zeus in this world (S in, X gate, * seal, $ treasure, + shrine, ! trap, o corridors, # room):");
            for (String row : Passages.plan(level.getSeed(), Line.ZEUS, 6).ascii()) RESULTS.add("            " + row);
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
                check(hero.position().distanceTo(new Vec3(5000.5, 100, 5000.5)) < 0.01, "the Compass of Chaos does not travel yet");

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
                check(hero.containerMenu instanceof StoryMenu && Story.holds(hero, Story.Chapter.HUB), "the Oracle speaks");
                hero.closeContainer();
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
