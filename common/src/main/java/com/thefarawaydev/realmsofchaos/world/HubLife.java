package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * Who and what lives in the Hub of Realms. Beside every gate stand two echoes of its tower's gods - the first you
 * will meet and the king at the top - which do nothing but turn their heads to watch whoever passes. Pilgrims
 * wander the plaza, cats sit about, and allays drift over it. While anyone is there, fireflies rise over the
 * gardens, the canal bubbles and now and then a chime sounds.
 */
public final class HubLife {
    public static final String TAG = "realms_of_chaos_hub";
    public static final int PILGRIMS = 4, CATS = 3, ALLAYS = 2;
    /** The corners of the plaza where the gardens are. */
    public static final int GARDEN = 14;
    private static final int Y = RealmBuilder.Y;

    private HubLife() {}

    private static AABB bounds() {
        return new AABB(-40, Y - 8, -40, 40, Y + 40, 40);
    }

    public static List<Entity> residents(ServerLevel level) {
        return level.getEntitiesOfClass(Entity.class, bounds(), e -> e.entityTags().contains(TAG));
    }

    public static void clear(ServerLevel level) {
        for (Entity e : residents(level)) e.discard();
    }

    /** Where the two echoes of a tower stand: on the plinths either side of its gate, facing the plaza. */
    public static BlockPos[] plinths(Line line) {
        return switch (line) {
            case ZEUS -> new BlockPos[]{new BlockPos(-6, Y + 1, -16), new BlockPos(6, Y + 1, -16)};
            case POSEIDON -> new BlockPos[]{new BlockPos(16, Y + 1, -6), new BlockPos(16, Y + 1, 6)};
            case HADES -> new BlockPos[]{new BlockPos(-16, Y + 1, 6), new BlockPos(-16, Y + 1, -6)};
        };
    }

    private static float facing(Line line) {
        return switch (line) {
            case ZEUS -> 0.0F;          // south, into the plaza
            case POSEIDON -> 90.0F;     // west
            case HADES -> -90.0F;       // east
        };
    }

    private static void echo(ServerLevel level, God god, BlockPos plinth, float yaw) {
        GodBoss echo = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
        if (echo == null) return;
        echo.setGod(god);
        echo.setStatue(true);
        echo.setCustomName(Component.translatable("entity." + RealmsOfChaos.MOD_ID + ".echo", god.displayName()));
        echo.snapTo(plinth.getX() + 0.5, plinth.getY() + 1, plinth.getZ() + 0.5, yaw, 0.0F);
        echo.setYHeadRot(yaw);
        echo.setYBodyRot(yaw);
        echo.addTag(TAG);
        level.addFreshEntity(echo);
    }

    private static <T extends Mob> void resident(ServerLevel level, EntityType<T> type, double x, double y, double z, String name) {
        T mob = type.create(level, EntitySpawnReason.TRIGGERED);
        if (mob == null) return;
        BlockPos at = BlockPos.containing(x, y, z);
        mob.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
        mob.setPersistenceRequired();
        if (name != null) mob.setCustomName(Component.translatable("entity." + RealmsOfChaos.MOD_ID + "." + name));
        mob.addTag(TAG);
        level.addFreshEntity(mob);
    }

    /** Fills the hub with its residents, replacing any that were there. */
    public static void populate(ServerLevel level) {
        clear(level);
        for (Line line : Line.values()) {
            BlockPos[] plinths = plinths(line);
            echo(level, Gods.get(line, Line.RANKS), plinths[0], facing(line));      // the first god of the tower
            echo(level, Gods.get(line, 1), plinths[1], facing(line));               // and its king
        }
        double[][] pilgrims = {{6.5, -6.5}, {-6.5, -6.5}, {3.5, 8.5}, {-3.5, 8.5}};
        for (int i = 0; i < PILGRIMS; i++) resident(level, EntityTypes.VILLAGER, pilgrims[i][0], Y + 1, pilgrims[i][1], "pilgrim");
        double[][] cats = {{4.5, -9.5}, {-4.5, -9.5}, {9.5, 3.5}};
        for (int i = 0; i < CATS; i++) resident(level, EntityTypes.CAT, cats[i][0], Y + 1, cats[i][1], null);
        double[][] allays = {{0.5, 6.5}, {3.5, -3.5}};
        for (int i = 0; i < ALLAYS; i++) resident(level, EntityTypes.ALLAY, allays[i][0], Y + 4, allays[i][1], null);
    }

    private static int ticker;

    /** Ambience while somebody is in the hub. */
    public static void tick(MinecraftServer server) {
        if (++ticker % 10 != 0) return;
        ServerLevel level = Realm.HUB.level(server);
        if (level == null || Realm.of(level.dimension()) != Realm.HUB || level.players().isEmpty()) return;
        RandomSource random = level.getRandom();
        // fireflies over the four gardens
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                level.sendParticles(ParticleTypes.FIREFLY, sx * GARDEN + 0.5, Y + 3.0, sz * GARDEN + 0.5, 2, 2.5, 1.5, 2.5, 0.0);
            }
        }
        // the canal: a bubble here and there along its ring
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.BUBBLE_POP, Math.cos(a) * 12.0, Y + 1.05, Math.sin(a) * 12.0, 1, 0.3, 0.0, 0.3, 0.0);
        }
        // the Oracle's flame breathes
        BlockPos oracle = RealmBuilder.ORACLE;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, oracle.getX() + 0.5, oracle.getY() + 1.2, oracle.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.01);
        level.sendParticles(ParticleTypes.ENCHANT, oracle.getX() + 0.5, oracle.getY() + 2.2, oracle.getZ() + 0.5, 4, 0.8, 0.6, 0.8, 0.5);
        if (ticker % 400 == 0) {
            level.playSound(null, oracle, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.5F, 0.6F + random.nextFloat() * 0.6F);
        }
    }
}
