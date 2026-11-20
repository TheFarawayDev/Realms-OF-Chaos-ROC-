package com.thefarawaydev.realmsofchaos.dev;

import com.mojang.authlib.GameProfile;
import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.ui.CodexMenu;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.RealmBuilder;
import com.thefarawaydev.realmsofchaos.world.RealmSafety;
import com.thefarawaydev.realmsofchaos.world.RocAdvancements;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import com.thefarawaydev.realmsofchaos.world.Welcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Developer self-test. It only runs when the JVM is started with {@code -Droc.selftest=true} (the loaders hook it to
 * "server started"), and exercises the mod against a real running server: data loading, realm building, the altar,
 * a boss fight, powers, the Codex menu, the first-join welcome and the anti-stuck safeguards. Results go to the log
 * and to {@code roc-selftest.txt}. It is inert in normal play.
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
            for (int i = 0; i < Math.min(6, st.length); i++) RESULTS.add("        at " + st[i]);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }

    private static ServerPlayer fakePlayer(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("roc-selftest".getBytes()), "RocSelfTest");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    public static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer[] holder = new ServerPlayer[1];
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);

        step("dimension types and dimensions are registered", () -> {
            var types = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            for (Realm r : Realm.values()) {
                check(types.get(ResourceKey.create(Registries.DIMENSION_TYPE, RealmsOfChaos.id(r.id()))).isPresent(), "dimension type " + r.id());
            }
        });
        step("realm dimensions exist as levels (skipped on test servers without datapack dimensions)", () -> {
            int present = 0;
            for (Realm r : Realm.values()) if (server.getLevel(r.key()) != null) present++;
            RESULTS.add("        realm levels present: " + present + " / 4");
        });
        step("advancements loaded: root + 45 gods", () -> {
            check(server.getAdvancements().get(RocAdvancements.ROOT) != null, "root advancement");
            for (God g : Gods.ALL) check(server.getAdvancements().get(RocAdvancements.idOf(g)) != null, "advancement " + g.id());
        });
        step("fake player", () -> {
            holder[0] = fakePlayer(server, level);
            holder[0].setPos(0.5, 65, 25.5);
        });
        ServerPlayer player = holder[0];
        if (player == null) { finish(server); return; }

        step("hub builds: gates in place, centre safe", () -> {
            RealmBuilder.build(level, Realm.HUB);
            check(level.getBlockState(new BlockPos(0, 66, -18)).is(ModContent.GATE_ZEUS.get()), "zeus gate");
            check(level.getBlockState(new BlockPos(18, 66, 0)).is(ModContent.GATE_POSEIDON.get()), "poseidon gate");
            check(level.getBlockState(new BlockPos(-18, 66, 0)).is(ModContent.GATE_HADES.get()), "hades gate");
            check(level.getBlockState(new BlockPos(0, 65, 0)).isAir() && level.getBlockState(new BlockPos(0, 66, 0)).isAir(), "hub spawn is free");
            check(!level.getBlockState(new BlockPos(0, 64, 0)).isAir(), "hub floor");
            for (Realm r : Realm.values()) {
                Vec3 a = Realm.hubArrival(r);
                check(level.getBlockState(BlockPos.containing(a)).isAir(), "hub arrival free for " + r.id());
                check(!level.getBlockState(BlockPos.containing(a).below()).isAir(), "hub arrival floor for " + r.id());
            }
        });
        for (Line line : Line.values()) {
            step("arena builds for " + line + ": altar, gate, clear fight ring, barrier", () -> {
                Realm realm = Realm.ofLine(line);
                RealmBuilder.build(level, realm);
                check(level.getBlockState(new BlockPos(0, 65, 21)).getBlock() instanceof com.thefarawaydev.realmsofchaos.block.AltarBlock, "altar");
                check(level.getBlockState(new BlockPos(0, 66, 29)).is(ModContent.GATE_HUB.get()), "exit gate");
                check(level.getBlockState(new BlockPos(31, 80, 0)).is(Blocks.BARRIER), "barrier above wall");
                Vec3 s = realm.spawn();
                check(level.getBlockState(BlockPos.containing(s)).isAir(), "arrival pad free");
                // nothing may stand inside the fight ring except the altar dais near the entrance
                for (int x = -RealmBuilder.FIGHT_RADIUS; x <= RealmBuilder.FIGHT_RADIUS; x++) {
                    for (int z = -RealmBuilder.FIGHT_RADIUS; z <= RealmBuilder.FIGHT_RADIUS; z++) {
                        if (x * x + z * z > (RealmBuilder.FIGHT_RADIUS - 1) * (RealmBuilder.FIGHT_RADIUS - 1)) continue;
                        if (x == 0 && z == 21) continue;
                        for (int y = 65; y <= 70; y++) {
                            check(level.getBlockState(new BlockPos(x, y, z)).isAir(), "obstacle in fight ring at " + x + "," + y + "," + z);
                        }
                        BlockPos floor = new BlockPos(x, 64, z);
                        check(!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty(), "hole in floor at " + x + "," + z);
                    }
                }
            });
        }

        step("first join: thank-you, codex + compass given, only once", () -> {
            Welcome.onJoin(player);
            check(player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModContent.CHAOS_CODEX.get())), "codex given");
            check(player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModContent.CHAOS_COMPASS.get())), "compass given");
            int before = count(player, ModContent.CHAOS_CODEX.get());
            Welcome.onJoin(player);
            check(count(player, ModContent.CHAOS_CODEX.get()) == before, "not given twice");
            var root = server.getAdvancements().get(RocAdvancements.ROOT);
            check(player.getAdvancements().getOrStartProgress(root).isDone(), "root advancement granted");
        });

        step("codex menu: opens, pages switch, buttons do not move items", () -> {
            CodexMenu.open(player);
            check(player.containerMenu instanceof CodexMenu, "menu open");
            CodexMenu menu = (CodexMenu) player.containerMenu;
            check(!menu.getSlot(CodexMenu.SLOT_ZEUS).getItem().isEmpty(), "zeus button");
            menu.clicked(CodexMenu.SLOT_ZEUS, 0, ContainerInput.PICKUP, player);
            check(menu.page() == Line.ZEUS, "zeus page");
            check(menu.getCarried().isEmpty(), "nothing picked up");
            for (int slot = 0; slot < 16; slot++) {
                if (slot == 9) continue;
                check(!menu.getSlot(slot).getItem().isEmpty(), "god icon in slot " + slot);
            }
            menu.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
            menu.clicked(30, 0, ContainerInput.PICKUP, player);
            check(menu.getCarried().isEmpty(), "still nothing picked up");
            menu.clicked(CodexMenu.SLOT_BACK, 0, ContainerInput.PICKUP, player);
            check(menu.page() == null, "back to main page");
            menu.clicked(CodexMenu.SLOT_POSEIDON, 0, ContainerInput.PICKUP, player);
            menu.clicked(CodexMenu.SLOT_BACK, 0, ContainerInput.PICKUP, player);
            menu.clicked(CodexMenu.SLOT_HADES, 0, ContainerInput.PICKUP, player);
            check(menu.page() == Line.HADES, "hades page");
            player.closeContainer();
        });

        step("altar: summons Iris at the arena centre, in free space", () -> {
            RealmBuilder.build(level, Realm.OLYMPUS);
            BlockPos altar = new BlockPos(0, 65, 21);
            level.getBlockState(altar).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(altar), Direction.UP, altar, false));
            List<GodBoss> bosses = level.getEntitiesOfClass(GodBoss.class, new AABB(altar).inflate(64), b -> true);
            check(bosses.size() == 1, "one boss, got " + bosses.size());
            GodBoss boss = bosses.get(0);
            check(boss.god() == Gods.get(Line.ZEUS, 15), "boss is Iris, got " + boss.god().id());
            check(Math.abs(boss.getMaxHealth() - 60.0F) < 0.01, "health " + boss.getMaxHealth());
            check(boss.getMainHandItem().is(ModContent.weaponItem(boss.god())), "holds its relic");
            check(!boss.isInWall() && level.noCollision(boss), "boss is not inside a block");
            // a second click must not summon another
            level.getBlockState(altar).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(altar), Direction.UP, altar, false));
            check(level.getEntitiesOfClass(GodBoss.class, new AABB(altar).inflate(64), b -> true).size() == 1, "no double summon");
        });

        step("boss abilities for every line run without error", () -> {
            for (Line line : Line.values()) {
                for (int rank : new int[]{1, 8, 15}) {
                    GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                    b.setGod(Gods.get(line, rank));
                    b.setPos(0.5, 65, 0.5);
                    b.setHome(new Vec3(0.5, 65, 0.5));
                    level.addFreshEntity(b);
                    b.setTarget(player);
                    b.castAbility(level, b.god());
                    b.discard();
                }
            }
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(BlockPos.ZERO).inflate(80),
                    e -> e.entityTags().contains("realms_of_chaos_minion"))) m.discard();
        });

        step("every god's weapon power and Divine Surge run without error", () -> {
            for (God god : Gods.ALL) {
                LivingEntity dummy = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                dummy.setPos(3.5, 65, 3.5);
                level.addFreshEntity(dummy);
                god.power().hit(level, dummy, player, god);
                god.applyTargetEffect(dummy);
                god.applySelfEffect(player);
                Power.surge(level, player, god);
                dummy.discard();
            }
            player.removeAllEffects();
        });

        step("defeating Iris: progress, relic drop, advancement, next trial unlocked", () -> {
            GodBoss boss = level.getEntitiesOfClass(GodBoss.class, new AABB(BlockPos.ZERO).inflate(80), b -> b.isAlive()).get(0);
            God god = boss.god();
            boss.hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
            check(!boss.isAlive(), "boss died");
            ProgressData data = ProgressData.get(server);
            check(data.isDefeated(player.getUUID(), god), "progress saved");
            check(data.nextRank(player.getUUID(), Line.ZEUS) == 14, "next rank is 14, got " + data.nextRank(player.getUUID(), Line.ZEUS));
            boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.ZERO).inflate(80),
                    e -> e.getItem().is(ModContent.weaponItem(god))).isEmpty();
            check(dropped, "relic dropped");
            check(player.getAdvancements().getOrStartProgress(server.getAdvancements().get(RocAdvancements.idOf(god))).isDone(), "advancement");
        });

        step("altar now summons the next god (Nike)", () -> {
            for (GodBoss b : level.getEntitiesOfClass(GodBoss.class, new AABB(BlockPos.ZERO).inflate(80), e -> true)) b.discard();
            BlockPos altar = new BlockPos(0, 65, 21);
            level.getBlockState(altar).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(altar), Direction.UP, altar, false));
            List<GodBoss> bosses = level.getEntitiesOfClass(GodBoss.class, new AABB(altar).inflate(64), b -> b.isAlive());
            check(bosses.size() == 1 && bosses.get(0).god() == Gods.get(Line.ZEUS, 14), "next boss is Nike");
            bosses.get(0).discard();
        });

        step("anti-stuck: a boss outside its arena, or inside a block, is put back at the centre", () -> {
            Vec3 home = new Vec3(0.5, 65, 0.5);
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.get(Line.POSEIDON, 1));
            b.setHome(home);
            b.setPos(120.5, 65, 0.5);
            level.addFreshEntity(b);
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 1.0, "out-of-bounds boss returned, is at " + b.position());
            b.setPos(0.5, 20, 0.5);
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 1.0, "fallen boss returned");
            for (int y = 65; y <= 68; y++) level.setBlock(new BlockPos(6, y, 6), Blocks.STONE.defaultBlockState(), 2);
            b.setPos(6.5, 65, 6.5);
            check(b.isInWall(), "test setup: boss is inside stone");
            b.keepUnstuck(level);
            check(b.position().distanceTo(home) < 1.0 && !b.isInWall(), "buried boss returned");
            for (int y = 65; y <= 68; y++) level.setBlock(new BlockPos(6, y, 6), Blocks.AIR.defaultBlockState(), 2);
            b.discard();
        });

        step("anti-stuck: a boss that makes no progress steps in next to its target", () -> {
            Vec3 home = new Vec3(0.5, 65, 0.5);
            GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
            b.setGod(Gods.get(Line.HADES, 1));
            b.setHome(home);
            b.setPos(home.x, home.y, home.z);
            level.addFreshEntity(b);
            player.setPos(14.5, 65, 0.5);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            b.setTarget(player);
            check(b.getTarget() == player, "test setup: target accepted (creative=" + player.isCreative()
                    + ", invulnerable=" + player.isInvulnerable() + ", difficulty=" + level.getDifficulty() + ")");
            for (int i = 0; i < 6; i++) b.keepUnstuck(level);
            check(b.distanceTo(player) < 4.0, "boss moved next to target, distance " + b.distanceTo(player));
            check(!b.isInWall() && level.noCollision(b), "and is standing in free space");
            b.discard();
            player.setPos(0.5, 65, 25.5);
        });

        step("live ticking: bosses of every line are left in the world mid-ability for the server to tick", () -> {
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.setChunkForced(cx, cz, true);
            int i = 0;
            for (Line line : Line.values()) {
                for (int prime : new int[]{2, 26}) {
                    GodBoss b = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
                    b.setGod(Gods.get(line, 1));
                    b.setPos(-8.5 + 3 * i++, 65, -6.5);
                    b.setHome(new Vec3(0.5, 65, 0.5));
                    level.addFreshEntity(b);
                    b.setTarget(player);
                    b.primeAbility(prime);
                }
            }
        });

        step("travel and rescue do not crash without realm dimensions", () -> {
            Teleports.travel(player, Realm.HUB);
            for (int i = 0; i < 40; i++) RealmSafety.tick(server);
        });

        finish(server);
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) n += player.getInventory().getItem(i).getCount();
        }
        return n;
    }

    private static void finish(MinecraftServer server) {
        StringBuilder out = new StringBuilder("Realms of Chaos self-test: " + (failures == 0 ? "ALL PASSED" : failures + " FAILED") + "\n");
        for (String line : RESULTS) out.append(line).append('\n');
        System.out.println("[ROC-SELFTEST]\n" + out);
        try {
            Files.writeString(Path.of("roc-selftest.txt"), out.toString());
        } catch (Exception ignored) {
        }
    }
}
