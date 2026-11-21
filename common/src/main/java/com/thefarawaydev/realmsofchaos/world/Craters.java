package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.god.Power;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Craters. A bolt from a relic that has been levelled breaks the ground where it comes down: a shallow bowl, wider
 * the higher the relic's level, with the stone that was in it thrown into the air and everything near the rim
 * shaken. The bolt does not have to find anyone - the ground is enough.
 *
 * <p>Out in the world a crater stays: the stone is gone, as after any blast - but only where the one who made it
 * could have dug it out themselves (not in adventure mode, not inside spawn protection). Inside the realms the
 * towers mend themselves: there a crater closes again after ten seconds, lifting anyone who stood in it, and one
 * that was open when the server stopped is closed when it starts again. Either way only plain, breakable blocks
 * are taken - never a chest, an altar or anything that cannot be mined - and never the last layer of a floor.
 */
public final class Craters {
    public static final int RESTORE_TICKS = 20 * 10, MAX_RADIUS = 4;
    private static final String KEY = "crater|";

    private static final class Crater {
        final ServerLevel level;
        final Map<BlockPos, BlockState> taken;
        int ticks = RESTORE_TICKS;

        Crater(ServerLevel level, Map<BlockPos, BlockState> taken) {
            this.level = level;
            this.taken = taken;
        }
    }

    private static final List<Crater> OPEN = new ArrayList<>();
    private static boolean recovered;

    private Craters() {}

    /**
     * How wide a crater a relic's bolt leaves: none below level 3, then one block, two from level 6, three from
     * level 9 - and one more for a relic with three stars or more.
     */
    public static int radius(int level, int stars) {
        int r = level >= 9 ? 3 : level >= 6 ? 2 : level >= 3 ? 1 : 0;
        return r == 0 ? 0 : Math.min(MAX_RADIUS, r + (stars >= 3 ? 1 : 0));
    }

    private static boolean takeable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F
                && state.isCollisionShapeFullBlock(level, pos);
    }

    private static String key(ServerLevel level, BlockPos pos, BlockState state) {
        return KEY + Network.dimension(level) + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ() + "|" + BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    /**
     * Breaks a crater open. @param radius 1 to {@link #MAX_RADIUS} @param by who is to blame, or null
     * @param splash what the blast does to those caught in it (nothing if 0) @return how many blocks it took.
     */
    public static int blast(ServerLevel level, Vec3 at, int radius, LivingEntity by, float splash) {
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        // inside the realms the ground is only a few blocks thick: a crater there is one block deep, whatever its width
        boolean thin = Realm.of(level.dimension()) != null;
        // out in the world a player's crater stays; anything else - and anything in the realms - closes again
        boolean lasting = !thin && by instanceof net.minecraft.server.level.ServerPlayer digger && digger.mayBuild();
        Map<BlockPos, BlockState> taken = new LinkedHashMap<>();
        BlockPos centre = BlockPos.containing(at.x, at.y + 0.2, at.z);
        ProgressData data = ProgressData.get(level.getServer());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.3) continue;
                // the top of the ground in this column, looking a little above and below where the bolt came down
                BlockPos top = null;
                for (int dy = 1; dy >= -2 && top == null; dy--) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (takeable(level, p, level.getBlockState(p)) && !level.getBlockState(p.above()).isCollisionShapeFullBlock(level, p.above())) top = p;
                }
                if (top == null) continue;
                int depth = thin ? 1 : Math.max(1, (int) Math.round((radius - dist) * 0.7 + 0.4));
                for (int d = 0; d < depth; d++) {
                    BlockPos p = top.below(d);
                    BlockState state = level.getBlockState(p);
                    // never the last layer: there is always ground left under a crater
                    if (!takeable(level, p, state) || !level.getBlockState(p.below()).isCollisionShapeFullBlock(level, p.below())) break;
                    if (lasting && !level.mayInteract(by, p)) break;
                    // (floor a god's wrath has already taken is the fight's to give back, not a crater's)
                    if (thin && (state.is(Blocks.OCHRE_FROGLIGHT) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.MAGMA_BLOCK))) break;
                    taken.put(p, state);
                }
            }
        }
        int thrown = 0;
        for (Map.Entry<BlockPos, BlockState> e : taken.entrySet()) {
            if (!lasting) data.markBuilt(key(level, e.getKey(), e.getValue()));
            level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), lasting ? 3 : 2);
            if (thrown < 4 + radius * 2 && level.getRandom().nextInt(3) == 0) {
                // some of what was there goes up - for show: it vanishes when it lands
                FallingBlockEntity chunk = FallingBlockEntity.fall(level, e.getKey(), e.getValue());
                chunk.dropItem = false;
                chunk.disableDrop();
                chunk.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.6, 0.45 + level.getRandom().nextDouble() * 0.35, (level.getRandom().nextDouble() - 0.5) * 0.6);
                chunk.needsSync = true;
                thrown++;
            }
        }
        if (!taken.isEmpty() && !lasting) OPEN.add(new Crater(level, taken));
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1 + radius, radius * 0.4, 0.2, radius * 0.4, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Fx.ground(level, centre)), at.x, at.y + 0.3, at.z, 20 * radius, radius * 0.5, 0.3, radius * 0.5, 0.2);
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F + 0.2F * radius, 1.3F - 0.15F * radius);
        if (splash > 0.0F) {
            double reach = radius + 1.0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at.x - reach, at.y - 2, at.z - reach, at.x + reach, at.y + 3, at.z + reach), LivingEntity::isAlive)) {
                if (e == by || (by != null && !Power.canHurt(by, e)) || e.position().distanceTo(at) > reach + 0.5) continue;
                e.hurtServer(level, by == null ? level.damageSources().magic() : level.damageSources().indirectMagic(by, by), splash);
            }
        }
        return taken.size();
    }

    private static void close(Crater crater) {
        ProgressData data = ProgressData.get(crater.level.getServer());
        AABB bounds = null;
        for (Map.Entry<BlockPos, BlockState> e : crater.taken.entrySet()) {
            BlockPos p = e.getKey();
            // (only into open space: anything built there in the meantime stays)
            boolean free = crater.level.getBlockState(p).isAir() || !crater.level.getBlockState(p).getFluidState().isEmpty();
            // (and never onto nothing: if a shifting floor has moved the ground away from under it, it is left out)
            boolean held = crater.taken.containsKey(p.below()) || !crater.level.getBlockState(p.below()).isAir();
            if (free && held) crater.level.setBlock(p, e.getValue(), 2);
            data.unmarkBuilt(key(crater.level, p, e.getValue()));
            AABB box = new AABB(p);
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        if (bounds == null) return;
        // nobody is buried: whatever the ground closed on is lifted on top of it
        for (Entity e : crater.level.getEntitiesOfClass(Entity.class, bounds.inflate(0.5, 1.0, 0.5), en -> !en.isSpectator() && !(en instanceof FallingBlockEntity))) {
            Fx.unbury(crater.level, e, MAX_RADIUS + 1);
        }
        crater.level.playSound(null, BlockPos.containing(bounds.getCenter()), SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    /** How many craters are open right now. */
    public static int open() {
        return OPEN.size();
    }

    /** Closes every open crater at once. */
    public static void closeAll() {
        for (Crater crater : OPEN) close(crater);
        OPEN.clear();
    }

    /** Test seam: forgets the open craters without closing them, as a server stop would. */
    public static void forget() {
        OPEN.clear();
        recovered = false;
    }

    /** Closes whatever craters were open when the server last stopped. @return how many blocks were put back. */
    public static int recover(MinecraftServer server) {
        ProgressData data = ProgressData.get(server);
        int restored = 0;
        for (String key : data.keys(KEY)) {
            String[] f = key.split("\\|");
            data.unmarkBuilt(key);
            if (f.length != 6) continue;
            try {
                Identifier dim = Identifier.tryParse(f[1]), block = Identifier.tryParse(f[5]);
                if (dim == null || block == null) continue;
                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
                BlockPos p = new BlockPos(Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
                if (level == null || !level.getBlockState(p).isAir()) continue;
                level.setBlock(p, BuiltInRegistries.BLOCK.getValue(block).defaultBlockState(), 2);
                restored++;
            } catch (NumberFormatException ignored) {
                // a key that cannot be read is simply dropped
            }
        }
        return restored;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (!recovered) {
            recovered = true;
            if (OPEN.isEmpty()) recover(server);
        }
        if (OPEN.isEmpty()) return;
        for (Iterator<Crater> it = OPEN.iterator(); it.hasNext(); ) {
            Crater crater = it.next();
            if (--crater.ticks > 0) continue;
            close(crater);
            it.remove();
        }
    }
}
