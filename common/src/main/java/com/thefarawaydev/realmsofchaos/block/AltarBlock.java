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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
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
        boss.setGod(god);
        boss.setPos(0.5, pos.getY() + 0.0, 0.5);
        serverLevel.addFreshEntity(boss);
        boss.setTarget(sp);
        serverLevel.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.3F);
        sp.sendSystemMessage(Component.translatable("message." + RealmsOfChaos.MOD_ID + ".altar.summon", god.displayName(), god.rank()));
        return InteractionResult.SUCCESS;
    }
}
