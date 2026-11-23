package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Words;
import com.thefarawaydev.realmsofchaos.world.Gauntlet.Hazard;
import com.thefarawaydev.realmsofchaos.world.Gauntlet.Pattern;
import com.thefarawaydev.realmsofchaos.world.Gauntlet.Variant;
import com.thefarawaydev.realmsofchaos.world.Passages.Cell;
import com.thefarawaydev.realmsofchaos.world.Passages.Plan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The trap halls, in the world. {@link Gauntlet} says what a hall's floor is doing at any moment; this builds the
 * room, shows the little there is to see of it, and hurts whoever is standing on a live tile.
 *
 * <p>A trap hall does not say what it is. Its floor is the floor of any other room; the only signs are a few
 * stained tiles, the bones of whoever came before, and - for a moment before a tile goes off - a wisp over it.
 * What a hall is called, and how it is beaten, a climber is told the first time one of its kind bites them.
 */
public final class Gauntlets {
    /** Nobody is hurt by a hall twice within this many ticks. */
    public static final int MERCY_TICKS = 12;

    private record Mark(int dx, int dz, long at) {}

    private static final Map<String, Long> TENDED = new HashMap<>();
    private static final Map<String, List<Mark>> MARKS = new HashMap<>();
    private static final Map<UUID, Long> BITTEN = new HashMap<>();

    private Gauntlets() {}

    private static String key(Plan plan, Cell c) {
        return plan.line.id() + "/" + plan.index + "/" + plan.stage + "/" + c.x() + "/" + c.z();
    }

    /** Which of a room's four sides have a doorway: east, south, west, north. */
    public static boolean[] doors(Plan plan, Cell c) {
        boolean[] doors = new boolean[4];
        for (int side = 0; side < 4; side++) {
            Cell n = new Cell(c.x() + Gauntlet.SIDES[side][0], c.z() + Gauntlet.SIDES[side][1]);
            doors[side] = n.x() >= 0 && n.z() >= 0 && n.x() < plan.size && n.z() < plan.size && plan.connected(c, n);
        }
        return doors;
    }

    /** The seed a hall's own patterns run on. */
    public static long seed(Plan plan, Cell c) {
        return plan.seed * 31L + c.x() * 7919L + c.z() * 104729L;
    }

    public static Component name(Variant v) {
        return Component.translatable("trap." + RealmsOfChaos.MOD_ID + "." + v.id());
    }

    /** How a pattern is beaten: what a climber is told the first time a hall of its kind bites. */
    public static Component lesson(Pattern pattern) {
        return Component.translatable("trap." + RealmsOfChaos.MOD_ID + ".pattern." + pattern.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String knownKey(ServerPlayer player, Variant v) {
        return "trapknown/" + player.getUUID() + "/" + v.id();
    }

    /** Has this hall's kind bitten this player before? */
    public static boolean known(ServerPlayer player, Variant v) {
        MinecraftServer server = player.level().getServer();
        return server != null && ProgressData.get(server).isBuilt(knownKey(player, v));
    }

    /** What a tile of a hall is doing now: {@link Gauntlet#SAFE}, {@link Gauntlet#WARN} or {@link Gauntlet#LIVE}. */
    public static int state(Plan plan, Cell c, int dx, int dz, long now) {
        Variant v = plan.variant(c);
        if (v == null) return Gauntlet.SAFE;
        int warn = v.warn(plan.threat);
        if (v.pattern != Pattern.CHASE) return Gauntlet.state(v, warn, doors(plan, c), seed(plan, c), dx, dz, now);
        int best = Gauntlet.SAFE;
        for (Mark mark : MARKS.getOrDefault(key(plan, c), List.of())) {
            if (mark.dx != dx || mark.dz != dz) continue;
            long rel = now - mark.at;
            if (rel >= warn && rel < warn + v.hold) return Gauntlet.LIVE;
            if (rel >= 0 && rel < warn) best = Gauntlet.WARN;
        }
        return best;
    }

    /** The tile a hazard leaves a stain of itself on. */
    public static Block stain(Hazard hazard) {
        return switch (hazard) {
            case SPIKE -> Blocks.COBBLED_DEEPSLATE;
            case FLAME -> Blocks.SMOOTH_BASALT;
            case SOULFIRE -> Blocks.SOUL_SOIL;
            case FROST -> Blocks.PACKED_ICE;
            case VENOM -> Blocks.MOSS_BLOCK;
            case SHOCK -> Blocks.RAW_COPPER_BLOCK;
            case WITHER -> Blocks.BLACKSTONE;
            case GEYSER -> Blocks.PRISMARINE;
            case GALE -> Blocks.CALCITE;
            case LEVITATE -> Blocks.PURPUR_BLOCK;
            case BLIGHT -> Blocks.SCULK;
        };
    }

    /**
     * Furnishes a trap hall - with very little. A handful of stained tiles, bones in one corner, and an urn or two
     * in the others for whoever dares to fetch them.
     */
    public static void build(ServerLevel level, Plan plan, Cell c, Random random) {
        Variant v = plan.variant(c);
        if (v == null) return;
        BlockPos m = plan.centre(c);
        int r = plan.half(c);
        boolean[] doors = doors(plan, c);
        for (int n = 0, placed = 0; n < 40 && placed < 2 + r; n++) {
            int dx = random.nextInt(2 * r + 1) - r, dz = random.nextInt(2 * r + 1) - r;
            if (Gauntlet.doormat(r, doors, dx, dz)) continue;
            level.setBlock(m.offset(dx, 0, dz), stain(v.hazard).defaultBlockState(), 2);
            placed++;
        }
        int bones = random.nextInt(4), urns = 1 + random.nextInt(2);
        for (int k = 0; k < 4; k++) {
            int corner = (bones + k) % 4;
            BlockPos at = m.offset(corner < 2 ? r : -r, 1, (corner & 1) == 0 ? r : -r);
            if (!level.getBlockState(at).isAir()) continue;
            if (k == 0) level.setBlock(at, Blocks.SKELETON_SKULL.defaultBlockState(), 2);
            else if (urns-- > 0) level.setBlock(at, ModContent.ANCIENT_URN.get().defaultBlockState(), 2);
        }
    }

    private static List<Player> inside(ServerLevel level, Plan plan, Cell cell) {
        return level.getEntitiesOfClass(Player.class, plan.cellBox(cell), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    /** One tick of one hall, as the clock stands at {@code now}. A hall is only tended once in any tick. */
    public static void tend(ServerLevel level, Plan plan, Cell cell, long now) {
        Variant v = plan.variant(cell);
        if (v == null) return;
        String key = key(plan, cell);
        Long last = TENDED.put(key, now);
        if (last != null && last == now) return;
        BlockPos m = plan.centre(cell);
        int r = plan.half(cell), warn = v.warn(plan.threat);
        boolean[] doors = doors(plan, cell);
        long seed = seed(plan, cell);
        List<Player> players = inside(level, plan, cell);

        if (v.pattern == Pattern.CHASE) {
            // whoever is standing on the floor when the hall looks is marked where they stand: keep moving
            List<Mark> marks = MARKS.computeIfAbsent(key, k -> new ArrayList<>());
            marks.removeIf(mark -> now - mark.at >= warn + v.hold || now < mark.at);
            if (Math.floorMod(now, (long) v.turn) == 0) {
                for (Player p : players) {
                    int dx = p.getBlockX() - m.getX(), dz = p.getBlockZ() - m.getZ();
                    if (Math.abs(dx) > r || Math.abs(dz) > r || Gauntlet.doormat(r, doors, dx, dz)) continue;
                    marks.add(new Mark(dx, dz, now));
                    // (deeper in, the tiles beside it too)
                    if (plan.threat >= 8.0) for (int[] d : Gauntlet.SIDES) marks.add(new Mark(dx + d[0], dz + d[1], now));
                }
            }
        }

        // what there is to see: a wisp over a tile about to go, and the thing itself when it does
        if (now % 4 == 0) {
            boolean path = v.pattern == Pattern.PATH;
            long shimmer = Math.floorMod(now + Math.floorMod(seed, 1000L), (long) v.turn);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double x = m.getX() + dx + 0.5, y = m.getY() + 1.05, z = m.getZ() + dz + 0.5;
                    if (path) {
                        // (a path hall shows only its path, and only while the rest is being warned of)
                        if (shimmer < warn && shimmer % 8 == 0 && Gauntlet.onPath(r, doors, seed, dx, dz)) level.sendParticles(ParticleTypes.END_ROD, x, y + 0.1, z, 1, 0.1, 0.0, 0.1, 0.0);
                    }
                    int state = state(plan, cell, dx, dz, now);
                    if (state == Gauntlet.WARN && !path && ((dx + dz + (now >> 2)) & 1) == 0) level.sendParticles(wisp(v.hazard), x, y, z, 1, 0.15, 0.02, 0.15, 0.0);
                    if (state == Gauntlet.LIVE && ((dx + dz + (now >> 2)) & 1) == 0) level.sendParticles(burst(v.hazard), x, y + 0.2, z, 2, 0.25, 0.35, 0.25, 0.01);
                }
            }
        }
        // and to hear: a click as a turn begins, the hazard as it comes - neither more than once a second
        if (v.pattern != Pattern.CHASE) {
            long rel = Math.floorMod(now + Math.floorMod(seed, 1000L), (long) v.turn), turnNo = Math.floorDiv(now + Math.floorMod(seed, 1000L), (long) v.turn);
            int every = Math.max(1, (int) Math.ceil(24.0 / v.turn));
            if (turnNo % every == 0) {
                if (rel == 0) level.playSound(null, m, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.35F, 0.7F);
                if (rel == warn) level.playSound(null, m, sound(v.hazard), SoundSource.BLOCKS, 0.5F, pitch(v.hazard));
            }
        }

        for (Player p : players) {
            int dx = p.getBlockX() - m.getX(), dz = p.getBlockZ() - m.getZ();
            if (p.getY() > m.getY() + 2.6 || state(plan, cell, dx, dz, now) != Gauntlet.LIVE) continue;
            Long bitten = BITTEN.get(p.getUUID());
            if (bitten != null && now >= bitten && now - bitten < MERCY_TICKS) continue;
            BITTEN.put(p.getUUID(), now);
            strike(level, plan, v, p, seed);
            if (p instanceof ServerPlayer sp && !known(sp, v)) {
                // bitten by this kind of hall for the first time: now it has a name, and a way through
                ProgressData.get(level.getServer()).markBuilt(knownKey(sp, v));
                Cards.notice(sp, Words.of(name(v)), Words.of(lesson(v.pattern)));
            }
        }
        if (now % 200 == 0) {
            TENDED.values().removeIf(t -> now - t > 200);
            BITTEN.values().removeIf(t -> now - t > 200);
            MARKS.values().removeIf(List::isEmpty);
        }
    }

    /** What a hazard does to whoever it catches. */
    public static void strike(ServerLevel level, Plan plan, Variant v, Player p, long seed) {
        float harm = Snares.damage(plan);
        switch (v.hazard) {
            case SPIKE -> {
                p.hurtServer(level, level.damageSources().stalagmite(), harm);
                p.setDeltaMovement(p.getDeltaMovement().x, 0.42, p.getDeltaMovement().z);
            }
            case FLAME -> {
                p.hurtServer(level, level.damageSources().inFire(), harm * 0.7F);
                p.setRemainingFireTicks(60);
            }
            case SOULFIRE -> {
                p.hurtServer(level, level.damageSources().inFire(), harm);
                p.setRemainingFireTicks(100);
            }
            case FROST -> {
                p.hurtServer(level, level.damageSources().freeze(), harm * 0.6F);
                p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 60, p.getTicksFrozen() + 120));
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            }
            case VENOM -> {
                p.hurtServer(level, level.damageSources().magic(), harm * 0.4F);
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            }
            case SHOCK -> p.hurtServer(level, level.damageSources().lightningBolt(), harm);
            case WITHER -> {
                p.hurtServer(level, level.damageSources().magic(), harm * 0.5F);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            }
            case GEYSER -> {
                // thrown at the ceiling, and dropped
                p.hurtServer(level, level.damageSources().magic(), harm * 0.4F);
                p.setDeltaMovement(p.getDeltaMovement().x, 1.0, p.getDeltaMovement().z);
            }
            case GALE -> {
                // shoved across the floor, one way in any one hall
                int[] way = Gauntlet.SIDES[(int) Math.floorMod(seed, 4L)];
                p.hurtServer(level, level.damageSources().magic(), harm * 0.3F);
                p.setDeltaMovement(way[0] * 1.3, 0.35, way[1] * 1.3);
            }
            case LEVITATE -> {
                p.hurtServer(level, level.damageSources().magic(), harm * 0.3F);
                p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 22, 5));
            }
            case BLIGHT -> {
                p.hurtServer(level, level.damageSources().magic(), harm * 0.6F);
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0));
            }
        }
        p.needsSync = true;
    }

    private static ParticleOptions wisp(Hazard hazard) {
        return switch (hazard) {
            case SPIKE -> ParticleTypes.CRIT;
            case FLAME, SOULFIRE -> ParticleTypes.SMOKE;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case VENOM -> ParticleTypes.ITEM_SLIME;
            case SHOCK -> ParticleTypes.ELECTRIC_SPARK;
            case WITHER -> ParticleTypes.ASH;
            case GEYSER -> ParticleTypes.BUBBLE_POP;
            case GALE -> ParticleTypes.WHITE_ASH;
            case LEVITATE -> ParticleTypes.WITCH;
            case BLIGHT -> ParticleTypes.SQUID_INK;
        };
    }

    private static ParticleOptions burst(Hazard hazard) {
        return switch (hazard) {
            case SPIKE -> ParticleTypes.ENCHANTED_HIT;
            case FLAME -> ParticleTypes.FLAME;
            case SOULFIRE -> ParticleTypes.SOUL_FIRE_FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case VENOM -> ParticleTypes.SNEEZE;
            case SHOCK -> ParticleTypes.ELECTRIC_SPARK;
            case WITHER -> ParticleTypes.SOUL;
            case GEYSER -> ParticleTypes.SPLASH;
            case GALE -> ParticleTypes.CLOUD;
            case LEVITATE -> ParticleTypes.END_ROD;
            case BLIGHT -> ParticleTypes.LARGE_SMOKE;
        };
    }

    private static SoundEvent sound(Hazard hazard) {
        return switch (hazard) {
            case SPIKE -> SoundEvents.POINTED_DRIPSTONE_LAND;
            case FLAME, SOULFIRE -> SoundEvents.BLAZE_SHOOT;
            case FROST -> SoundEvents.GLASS_BREAK;
            case SHOCK -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case WITHER, LEVITATE -> SoundEvents.BEACON_DEACTIVATE;
            default -> SoundEvents.FIRE_EXTINGUISH;
        };
    }

    private static float pitch(Hazard hazard) {
        return switch (hazard) {
            case SOULFIRE, WITHER, BLIGHT -> 0.6F;
            case GEYSER, LEVITATE, SHOCK -> 1.5F;
            default -> 1.0F;
        };
    }

    /** True if a block is one any room of this level is floored with: for tests. */
    public static boolean ordinaryFloor(Plan plan, net.minecraft.world.level.block.state.BlockState floor) {
        TowerBuilder.Palette p = plan.palette();
        return floor.is(p.top()) || floor.is(p.topAlt()) || floor.is(p.inlay());
    }

    /** Marks a tile of a chasing hall as its own tick would: for tests. */
    public static void mark(Plan plan, Cell c, int dx, int dz, long now) {
        MARKS.computeIfAbsent(key(plan, c), k -> new ArrayList<>()).add(new Mark(dx, dz, now));
    }

    /** Forgets every mark, and who was bitten when: between tests. */
    public static void forget() {
        TENDED.clear();
        MARKS.clear();
        BITTEN.clear();
    }
}
