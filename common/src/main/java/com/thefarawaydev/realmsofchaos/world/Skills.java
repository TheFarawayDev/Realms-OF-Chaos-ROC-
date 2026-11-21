package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The mastery tree. A climber earns a point for every god they wake and every passage they walk for the first
 * time, and spends them here: three branches - combat, survival, exploration - of four skills each, every skill
 * with three ranks. Points are never lost; a tree can be taken apart and spent again whenever its owner likes.
 *
 * <p>Most skills are a standing bonus (more damage, more health, more armour...) kept on the player as an
 * attribute modifier; the rest are asked for by the code that needs them - how long a relic's move takes to come
 * back, how good a compass's signal is, how long a waygate takes.
 */
public final class Skills {
    public static final int MAX_RANK = 3;

    public enum Branch { COMBAT, SURVIVAL, EXPLORATION }

    public enum Skill {
        MIGHT(Branch.COMBAT), SWIFT(Branch.COMBAT), FOCUS(Branch.COMBAT), REACH(Branch.COMBAT),
        VIGOR(Branch.SURVIVAL), GUARD(Branch.SURVIVAL), STEADY(Branch.SURVIVAL), SECOND_WIND(Branch.SURVIVAL),
        CARTOGRAPHER(Branch.EXPLORATION), SIGNAL(Branch.EXPLORATION), SCAVENGER(Branch.EXPLORATION), PATHFINDER(Branch.EXPLORATION);

        public final Branch branch;

        Skill(Branch branch) {
            this.branch = branch;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("skill." + RealmsOfChaos.MOD_ID + "." + id());
        }

        public Component description() {
            return Component.translatable("skill." + RealmsOfChaos.MOD_ID + "." + id() + ".desc");
        }

        public static Skill byId(String id) {
            for (Skill s : values()) if (s.id().equals(id)) return s;
            return null;
        }
    }

    private Skills() {}

    private static String key(ServerPlayer player, Skill skill) {
        return "skill/" + player.getUUID() + "/" + skill.id();
    }

    public static int rank(ServerPlayer player, Skill skill) {
        MinecraftServer server = player.level().getServer();
        return server == null ? 0 : Math.max(0, Math.min(MAX_RANK, ProgressData.get(server).counter(key(player, skill))));
    }

    /** Every point this player has earned: one for each god woken and one for each passage walked. */
    public static int earned(ServerPlayer player) {
        ProgressData data = ProgressData.get(player.level().getServer());
        int passages = 0;
        for (Line line : Line.values()) {
            for (int index = 1; index <= Passages.COUNT; index++) if (data.hasPassed(player.getUUID(), line, index)) passages++;
        }
        return data.total(player.getUUID()) + passages;
    }

    public static int spent(ServerPlayer player) {
        int spent = 0;
        for (Skill skill : Skill.values()) spent += rank(player, skill);
        return spent;
    }

    public static int available(ServerPlayer player) {
        return Math.max(0, earned(player) - spent(player));
    }

    /** Spends a point on a skill. @return false if there is none to spend or the skill is already at its last rank. */
    public static boolean raise(ServerPlayer player, Skill skill) {
        if (available(player) <= 0 || rank(player, skill) >= MAX_RANK) return false;
        ProgressData.get(player.level().getServer()).addCounter(key(player, skill), 1);
        apply(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    /** Takes the whole tree apart: every point comes back. */
    public static void reset(ServerPlayer player) {
        ProgressData.get(player.level().getServer()).clearCounters("skill/" + player.getUUID() + "/");
        apply(player);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        var id = RealmsOfChaos.id("skill_" + name);
        if (amount == 0.0) instance.removeModifier(id);
        else instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** Puts a player's standing bonuses on them. Safe to call as often as wanted: it only ever sets what is so. */
    public static void apply(ServerPlayer player) {
        if (player.level().getServer() == null) return;
        modifier(player, Attributes.ATTACK_DAMAGE, "might", 0.06 * rank(player, Skill.MIGHT), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.MOVEMENT_SPEED, "swift", 0.04 * rank(player, Skill.SWIFT), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.ENTITY_INTERACTION_RANGE, "reach", 0.25 * rank(player, Skill.REACH), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.MAX_HEALTH, "vigor", 2.0 * rank(player, Skill.VIGOR), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.ARMOR, "guard", 1.5 * rank(player, Skill.GUARD), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.KNOCKBACK_RESISTANCE, "steady", 0.15 * rank(player, Skill.STEADY), AttributeModifier.Operation.ADD_VALUE);
    }

    /** Once a second for every player: the bonuses are kept on (a respawn makes a new body), and Second Wind breathes. */
    public static void service(ServerPlayer player, long tick) {
        if (tick % 100 == 0) apply(player);
        int wind = rank(player, Skill.SECOND_WIND);
        if (wind > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth() * 0.5F && tick % (20L * (12 - 3 * wind)) == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, true, false, true));
        }
    }

    // ---- what the other code asks -------------------------------------------------------------------------------

    /** A relic's move comes back this much sooner: 8% a rank. */
    public static double moveScale(ServerPlayer player) {
        return 1.0 - 0.08 * rank(player, Skill.FOCUS);
    }

    /** Added to a compass's signal: 0.05 a rank. */
    public static double signal(ServerPlayer player) {
        return 0.05 * rank(player, Skill.SIGNAL);
    }

    /** A waygate's channel is this much shorter: 15% a rank. */
    public static double channelScale(ServerPlayer player) {
        return 1.0 - 0.15 * rank(player, Skill.PATHFINDER);
    }
}
