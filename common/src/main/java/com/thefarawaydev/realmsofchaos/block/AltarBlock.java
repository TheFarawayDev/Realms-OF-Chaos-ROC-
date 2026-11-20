package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.RealmBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The trial altar of a god line. Each player climbs the line from rank 15 (the starter god) to rank 1 (the final god);
 * the next trial is only available once the previous god of that line has been defeated.
 */
public class AltarBlock extends Block {
    private final Line line;

    public AltarBlock(Line line, Properties properties) {
        super(properties);
        this.line = line;
    }

    /** The first free spot around the altar where the boss fits, so it never appears inside a block. */
    private static Vec3 findSpawn(ServerLevel level, GodBoss boss, BlockPos altar) {
        int[][] offsets = {{0, -4}, {4, 0}, {-4, 0}, {0, 4}, {3, -3}, {-3, -3}, {3, 3}, {-3, 3}, {0, -2}, {2, 0}, {-2, 0}, {0, 2}};
        for (int dy = 0; dy <= 3; dy++) {
            for (int[] o : offsets) {
                Vec3 p = new Vec3(altar.getX() + 0.5 + o[0], altar.getY() + dy, altar.getZ() + 0.5 + o[1]);
                boss.setPos(p.x, p.y, p.z);
                if (level.noCollision(boss)) return p;
            }
        }
        return new Vec3(altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5);
    }

    /** Client-side ambience: runes rise from the altar. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6, pos.getY() + 1.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6, 0, -0.6, 0);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (serverLevel.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            sp.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".altar.peaceful"));
            return InteractionResult.SUCCESS;
        }
        ProgressData data = ProgressData.get(serverLevel.getServer());
        int next = data.nextRank(sp.getUUID(), line);
        if (next == 0) {
            sp.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".altar.done", line.displayName()));
            return InteractionResult.SUCCESS;
        }

        List<GodBoss> active = serverLevel.getEntitiesOfClass(GodBoss.class, new AABB(pos).inflate(96), b -> b.isAlive());
        if (!active.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".altar.active"));
            return InteractionResult.SUCCESS;
        }

        God god = Gods.get(line, next);
        GodBoss boss = ModContent.GOD_BOSS.get().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (boss == null) {
            return InteractionResult.FAIL;
        }
        // In a realm the god appears at the arena centre; an altar placed elsewhere summons a few blocks away.
        Realm realm = Realm.of(serverLevel.dimension());
        Vec3 spawn = realm != null ? realm.arenaCentre() : findSpawn(serverLevel, boss, pos);
        RealmBuilder.makeSafeIfRealm(serverLevel, spawn);
        boss.setGod(god);
        boss.setPos(spawn.x, spawn.y, spawn.z);
        boss.setHome(spawn);
        serverLevel.addFreshEntity(boss);
        boss.setTarget(sp);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(spawn.x, spawn.y, spawn.z);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }
        serverLevel.sendParticles(ParticleTypes.POOF, spawn.x, spawn.y + 1, spawn.z, 60, 0.6, 1.0, 0.6, 0.08);
        serverLevel.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.3F);
        sp.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".altar.summon", god.displayName(), god.rank()));
        return InteractionResult.SUCCESS;
    }
}
