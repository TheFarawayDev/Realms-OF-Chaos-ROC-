package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.Realm;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A shrine found in the passages. Right-click it for a blessing: full health, harmful effects washed away, and
 * three minutes of the tower's own favour. Each shrine blesses a given player once every ten minutes.
 */
public class ShrineBlock extends Block {
    public static final int REST_TICKS = 20 * 60 * 10, BLESSING_TICKS = 20 * 60 * 3;
    private static final Map<String, Long> BLESSED = new HashMap<>();

    public ShrineBlock(Properties properties) {
        super(properties);
    }

    /** @return true if the player was blessed, false if this shrine is still resting for them. */
    public static boolean bless(ServerLevel level, BlockPos pos, ServerPlayer player) {
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".shrine.";
        long now = level.getServer().getTickCount();
        String key = player.getUUID() + "@" + level.dimension().identifier() + "/" + pos.asLong();
        Long last = BLESSED.get(key);
        if (last != null && now >= last && now - last < REST_TICKS) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "resting", (REST_TICKS - (now - last)) / 1200 + 1), 0xAAAAAA));
            return false;
        }
        BLESSED.put(key, now);
        Realm realm = Realm.of(level.dimension());
        Line line = realm == null || realm.line() == null ? Line.ZEUS : realm.line();
        player.setHealth(player.getMaxHealth());
        Signature.cleanse(player);
        switch (line) {
            case ZEUS -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, BLESSING_TICKS, 0));
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BLESSING_TICKS, 0));
            }
            case POSEIDON -> {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BLESSING_TICKS, 0));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 0));
            }
            case HADES -> {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, BLESSING_TICKS, 0));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BLESSING_TICKS, 1));
            }
        }
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.0, player.getZ(), 6, 0.4, 0.2, 0.4, 0.0);
        level.sendParticles(Signature.spark(line), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.5, 0.4, 0.05);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.5F);
        Hud.actionBar(player, Hud.colored(Component.translatable(hud + "blessed." + line.id()), line.color()));
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) bless(serverLevel, pos, sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
