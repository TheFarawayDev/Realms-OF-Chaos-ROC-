package com.thefarawaydev.realmsofchaos.fx;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.god.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The mod's own sound effects, played by name: the relics, the moves of a fight, the blocks being used, and the idle
 * sounds of machines and fires (played now and then from the client's own animation tick, so they cost the server
 * nothing and only those near enough hear them).
 */
public final class RocSounds {
    private RocSounds() {}

    /** Every effect's id, as named in sounds.json. */
    public static final java.util.List<String> EFFECTS = java.util.List.of(
            "orb_make", "orb_core", "orb_pattern", "orb_ichor", "shrine_awaken", "keystone_set", "gate_awaken", "ui_click",
            "relic_cast_zeus", "relic_cast_poseidon", "relic_cast_hades", "relic_shot_zeus", "relic_shot_poseidon", "relic_shot_hades",
            "relic_ready", "relic_flourish", "relic_level", "parry", "riposte", "dodge",
            "sliding_door", "forge_strike", "forge_perfect", "forge_miss", "forge_done", "seal_strike", "seal_break", "altar_summon",
            "cache_open", "lock_pin", "lock_break", "lock_open", "waygate_charge", "waygate_travel", "oracle_speak", "war_drum",
            "dummy_hit", "puzzle_solved",
            "idle_orb", "idle_forge", "idle_gate", "idle_relay", "idle_jammer", "idle_waygate", "idle_fire", "idle_oracle",
            "idle_altar", "idle_pedestal");

    /** Heard by everyone near a place. */
    public static void at(Level level, BlockPos pos, String id, float volume, float pitch) {
        level.playSound(null, pos, ModContent.sound(id), SoundSource.BLOCKS, volume, pitch);
    }

    /** Heard by everyone near someone, as their own doing. */
    public static void by(Level level, Entity who, String id, float volume, float pitch) {
        level.playSound(null, who.blockPosition(), ModContent.sound(id), SoundSource.PLAYERS, volume, pitch);
    }

    /** Heard by one player alone, wherever they are. */
    public static void to(net.minecraft.server.level.ServerPlayer player, String id, float volume, float pitch) {
        if (player.connection == null) return;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModContent.sound(id)), SoundSource.PLAYERS,
                player.getX(), player.getEyeY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    /** A tower's version of an effect: zeus, poseidon or hades. */
    public static String of(String base, Line line) {
        return base + "_" + line.id();
    }

    /**
     * An idle sound, from a block's animation tick on the client: played with the given chance each time the block is
     * ticked, quietly, at a slightly varied pitch.
     */
    public static void idle(Level level, BlockPos pos, RandomSource random, String id, float chance, float volume) {
        if (random.nextFloat() >= chance) return;
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModContent.sound(id), SoundSource.BLOCKS,
                volume, 0.92F + random.nextFloat() * 0.16F, false);
    }
}
