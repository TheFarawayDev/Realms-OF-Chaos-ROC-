package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.RelicProjectile;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The 22 Major Arcana. Each card casts one effect. Single cards are used up; the Deck of Fate, dropped by Hades,
 * is a stack that never runs out. The gods of Hades' tower drop the cards, and Hades himself throws them.
 */
public enum Tarot {
    FOOL("fool", 30), MAGICIAN("magician", 30), HIGH_PRIESTESS("high_priestess", 45), EMPRESS("empress", 45),
    EMPEROR("emperor", 60), HIEROPHANT("hierophant", 45), LOVERS("lovers", 60), CHARIOT("chariot", 45),
    STRENGTH("strength", 60), HERMIT("hermit", 60), WHEEL_OF_FORTUNE("wheel_of_fortune", 30), JUSTICE("justice", 45),
    HANGED_MAN("hanged_man", 45), DEATH("death", 60), TEMPERANCE("temperance", 60), DEVIL("devil", 60),
    TOWER("tower", 45), STAR("star", 90), MOON("moon", 45), SUN("sun", 45), JUDGEMENT("judgement", 180), WORLD("world", 120);

    private final String id;
    private final int rechargeSeconds;

    Tarot(String id, int rechargeSeconds) {
        this.id = id;
        this.rechargeSeconds = rechargeSeconds;
    }

    public String id() { return id; }
    public String itemId() { return "tarot_" + id; }
    /** How long the Deck of Fate rests after this card is chosen from it. */
    public int rechargeSeconds() { return rechargeSeconds; }

    public Component displayName() {
        return Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + itemId());
    }

    public Component description() {
        return Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + itemId() + ".desc");
    }

    /** The cards a god of Hades' tower drops: one each on the lower floors, two higher up, Death from Hades. */
    public static List<Tarot> dropsFor(God god) {
        List<Tarot> out = new ArrayList<>();
        if (god.line() != Line.HADES) return out;
        if (god.rank() == 1) {
            out.add(DEATH);
            return out;
        }
        List<Tarot> pool = new ArrayList<>();
        for (Tarot t : values()) if (t != DEATH) pool.add(t);
        int index = 0;
        for (int rank = Line.RANKS; rank >= 2; rank--) {
            int n = rank >= 9 ? 1 : 2;
            if (rank == god.rank()) {
                for (int i = 0; i < n; i++) out.add(pool.get(index + i));
            }
            index += n;
        }
        return out;
    }

    private static List<LivingEntity> foes(ServerLevel level, ServerPlayer player, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && Power.canHurt(player, e));
    }

    private static void give(LivingEntity who, Holder<MobEffect> effect, int seconds, int amplifier) {
        who.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier));
    }

    /** Casts the card for {@code player}. */
    public void cast(ServerLevel level, ServerPlayer player) {
        var magic = level.damageSources().indirectMagic(player, player);
        switch (this) {
            case FOOL -> {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 1.7, 0.6 + Math.max(0, look.y) * 0.6, look.z * 1.7);
                player.needsSync = true;
                player.resetFallDistance();
                give(player, MobEffects.SLOW_FALLING, 6, 0);
            }
            case MAGICIAN -> {
                for (int i = -1; i <= 1; i++) {
                    RelicProjectile bolt = new RelicProjectile(level, player, new ItemStack(ModContent.orbItem(Power.KNOCKBACK)));
                    bolt.setDamage(7.0F);
                    bolt.shootFromRotation(player, player.getXRot(), player.getYRot() + i * 9.0F, 0.0F, 1.8F, 0.5F);
                    level.addFreshEntity(bolt);
                }
            }
            case HIGH_PRIESTESS -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24), e -> !(e instanceof Player))) {
                    give(e, MobEffects.GLOWING, 20, 0);
                }
                give(player, MobEffects.NIGHT_VISION, 30, 0);
            }
            case EMPRESS -> {
                player.heal(8.0F);
                give(player, MobEffects.REGENERATION, 10, 1);
            }
            case EMPEROR -> {
                give(player, MobEffects.RESISTANCE, 12, 1);
                give(player, MobEffects.STRENGTH, 12, 0);
            }
            case HIEROPHANT -> {
                for (MobEffectInstance active : List.copyOf(player.getActiveEffects())) {
                    if (!active.getEffect().value().isBeneficial()) player.removeEffect(active.getEffect());
                }
                give(player, MobEffects.ABSORPTION, 20, 0);
            }
            case LOVERS -> {
                for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10), Player::isAlive)) ally.heal(10.0F);
            }
            case CHARIOT -> {
                give(player, MobEffects.SPEED, 10, 2);
                for (LivingEntity e : foes(level, player, 4)) {
                    Vec3 away = e.position().subtract(player.position());
                    away = new Vec3(away.x, 0, away.z).normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.4, 0.4, away.z * 1.4));
                    e.needsSync = true;
                }
            }
            case STRENGTH -> give(player, MobEffects.STRENGTH, 15, 1);
            case HERMIT -> {
                give(player, MobEffects.INVISIBILITY, 15, 0);
                give(player, MobEffects.SPEED, 15, 0);
            }
            case WHEEL_OF_FORTUNE -> {
                Tarot other = this;
                while (other == this) other = values()[level.getRandom().nextInt(values().length)];
                other.cast(level, player);
            }
            case JUSTICE -> {
                for (LivingEntity e : foes(level, player, 7)) e.hurtServer(level, magic, 10.0F);
            }
            case HANGED_MAN -> {
                for (LivingEntity e : foes(level, player, 8)) give(e, MobEffects.LEVITATION, 4, 1);
                give(player, MobEffects.SLOW_FALLING, 8, 0);
            }
            case DEATH -> {
                LivingEntity target = null;
                Vec3 look = player.getLookAngle();
                double best = 0.6;                    // must be roughly in front of the player
                for (LivingEntity e : foes(level, player, 16)) {
                    double dot = e.position().subtract(player.position()).normalize().dot(look);
                    if (dot > best) { best = dot; target = e; }
                }
                if (target != null) {
                    target.hurtServer(level, magic, 14.0F);
                    give(target, MobEffects.WITHER, 8, 2);
                    level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                }
            }
            case TEMPERANCE -> {
                give(player, MobEffects.FIRE_RESISTANCE, 60, 0);
                give(player, MobEffects.WATER_BREATHING, 60, 0);
                give(player, MobEffects.REGENERATION, 20, 0);
            }
            case DEVIL -> {
                give(player, MobEffects.STRENGTH, 10, 2);
                if (player.getHealth() > 5.0F) player.hurtServer(level, level.damageSources().magic(), 4.0F);
            }
            case TOWER -> {
                for (LivingEntity e : foes(level, player, 10)) {
                    LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
                        bolt.setPos(e.getX(), e.getY(), e.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    e.hurtServer(level, magic, 8.0F);
                }
            }
            case STAR -> give(player, MobEffects.ABSORPTION, 60, 3);
            case MOON -> {
                for (LivingEntity e : foes(level, player, 10)) {
                    give(e, MobEffects.DARKNESS, 10, 0);
                    give(e, MobEffects.SLOWNESS, 10, 1);
                }
                give(player, MobEffects.NIGHT_VISION, 30, 0);
            }
            case SUN -> {
                for (LivingEntity e : foes(level, player, 8)) e.igniteForSeconds(6.0F);
                give(player, MobEffects.REGENERATION, 6, 1);
            }
            case JUDGEMENT -> {
                player.setHealth(player.getMaxHealth());
                give(player, MobEffects.RESISTANCE, 4, 3);
            }
            case WORLD -> {
                give(player, MobEffects.SPEED, 25, 0);
                give(player, MobEffects.STRENGTH, 25, 0);
                give(player, MobEffects.REGENERATION, 25, 0);
                give(player, MobEffects.RESISTANCE, 25, 0);
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.6, 0.5, 0.4);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
    }
}
