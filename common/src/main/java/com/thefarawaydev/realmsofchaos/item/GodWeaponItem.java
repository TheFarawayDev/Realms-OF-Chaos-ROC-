package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.god.Ranged;
import com.thefarawaydev.realmsofchaos.god.WeaponKind;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A god's relic weapon. What it is - blade, dagger, polearm, hammer, scythe, staff, bow, thrown weapon or relic -
 * comes from the god's {@link WeaponKind}: that sets its melee stats and what right-click does. Every relic also
 * carries its god's hit power and casts its god's signature move, levels up with kills and can be forged with
 * stars (see {@link WeaponProgress}): three stars empower the move, five make it echo.
 */
public class GodWeaponItem extends Item {
    public static final int MOVE_COOLDOWN_TICKS = 20 * 12;
    public static final int EMPOWERED_STARS = 3, AWAKENED_STARS = 5, FLOURISH_HITS = 4;
    /** When each player's move with each relic is ready again, in server ticks. */
    private static final Map<String, Long> MOVE_READY = new ConcurrentHashMap<>();
    /** Hits in a row, per player: {count, tick of the last hit}. */
    private static final Map<UUID, long[]> COMBO = new ConcurrentHashMap<>();

    private final God god;

    public GodWeaponItem(God god, Properties properties) {
        super(properties);
        this.god = god;
    }

    public God god() {
        return god;
    }

    /** Builds the item properties: melee damage, attack speed and reach all follow the weapon kind and the rank. */
    public static Properties properties(God god, ResourceKey<Item> key) {
        WeaponKind kind = god.kind();
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        god.baseDamage() * kind.meleeScale(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        kind.attackSpeed(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (kind.reach() > 0) {
            attributes.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(RealmsOfChaos.id("relic_reach"),
                    kind.reach(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        Rarity rarity = god.rank() <= 3 ? Rarity.EPIC : god.rank() <= 9 ? Rarity.RARE : Rarity.UNCOMMON;
        return new Properties().setId(key).stacksTo(1).attributes(attributes.build()).rarity(rarity).fireResistant();
    }

    /** Damage of one ranged shot from this stack. */
    public float rangedDamage(ItemStack stack) {
        return (float) (god.baseDamage() * god.kind().rangedScale() * WeaponProgress.multiplier(stack));
    }

    // ---- melee -----------------------------------------------------------------------------------------------

    /** Level and stars add damage on top of the base attribute. */
    @Override
    public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            ItemStack held = attacker.getMainHandItem();
            if (held.getItem() == this) return (float) (damage * (WeaponProgress.multiplier(held) - 1.0));
        }
        return 0.0F;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (attacker.level() instanceof ServerLevel level) {
            god.power().hit(level, target, attacker, god);
            god.applyTargetEffect(target);
            god.applySelfEffect(attacker);
            // a hammer, an anchor or an oar comes down hard enough to break the ground
            if (god.kind() == WeaponKind.HAMMER) com.thefarawaydev.realmsofchaos.world.Fx.impact(level, target.position(), 2);
            if (god.kind() == WeaponKind.SCYTHE) {
                // a scythe's edge carries through everything beside the target
                for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                        e -> e != target && Power.canHurt(attacker, e))) {
                    other.hurtServer(level, Power.source(level, attacker), (float) (god.baseDamage() * 0.35 * WeaponProgress.multiplier(stack)));
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 3, 1.0, 0.1, 1.0, 0.0);
            }
            if (attacker instanceof Player player && flourish(level, player)) {
                // the fourth hit in a row lands half as hard again
                target.damageCooldownTime = 0;          // or the hit that just landed would swallow the bonus
                target.hurtServer(level, Power.source(level, attacker),
                        (float) (god.baseDamage() * god.kind().meleeScale() * WeaponProgress.multiplier(stack) * 0.5));
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 18, 0.3, 0.4, 0.3, 0.4);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
                level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
                com.thefarawaydev.realmsofchaos.world.Fx.impact(level, target.position(), 3);
                if (player instanceof ServerPlayer sp) {
                    Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".flourish"), god.line().color()));
                }
            }
        }
    }

    /** Counts a hit; hits no more than three seconds apart form a chain, and every fourth one is a flourish. */
    private static boolean flourish(ServerLevel level, Player player) {
        long now = level.getServer().getTickCount();
        long[] combo = COMBO.computeIfAbsent(player.getUUID(), id -> new long[2]);
        long gap = now - combo[1];
        combo[0] = gap >= 0 && gap <= 60 ? combo[0] + 1 : 1;
        combo[1] = now;
        if (combo[0] < FLOURISH_HITS) return false;
        combo[0] = 0;
        return true;
    }

    // ---- right-click -----------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        WeaponKind kind = god.kind();
        // melee kinds cast their move on right-click; ranged kinds shoot, and cast with sneak held
        boolean move = !kind.ranged() || player.isShiftKeyDown();
        if (!(level instanceof ServerLevel serverLevel)) {
            return (!move && player.getCooldowns().isOnCooldown(stack)) ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (move) {
            return castMove(serverLevel, player, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        if (kind == WeaponKind.RELIC) {
            invoke(serverLevel, player, stack);
        } else {
            Ranged.fire(serverLevel, player, god, stack, rangedDamage(stack), null);
        }
        player.getCooldowns().addCooldown(stack, Math.max(4, (int) (kind.useCooldown() * WeaponProgress.cooldownScale(stack))));
        return InteractionResult.SUCCESS;
    }

    /** A relic has no edge and no bolt: it releases its god's power on everything within five blocks. */
    private void invoke(ServerLevel level, Player player, ItemStack stack) {
        float damage = rangedDamage(stack);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0), x -> Power.canHurt(player, x))) {
            e.hurtServer(level, Power.source(level, player), damage);
            god.power().hit(level, e, player, god);
            god.applyTargetEffect(e);
        }
        god.applySelfEffect(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    /** Damage of this stack's signature move: level and stars count, and three stars empower it. */
    public float moveDamage(ItemStack stack) {
        double empowered = WeaponProgress.stars(stack) >= EMPOWERED_STARS ? 1.5 : 1.0;
        return (float) (god.baseDamage() * 0.9 * WeaponProgress.multiplier(stack) * empowered);
    }

    public static int moveCooldown(ItemStack stack) {
        return (int) (MOVE_COOLDOWN_TICKS * WeaponProgress.cooldownScale(stack));
    }

    /** Players a sentinel has silenced, and the server tick until which no relic of theirs will cast its move. */
    private static final Map<java.util.UUID, Long> SILENCED = new ConcurrentHashMap<>();

    /** Silences a player: for this many ticks none of their relics will cast its move. */
    public static void silence(Player player, int ticks) {
        if (player.level().getServer() == null) return;
        SILENCED.put(player.getUUID(), player.level().getServer().getTickCount() + (long) ticks);
        if (player instanceof ServerPlayer sp) {
            Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".sentinel.silenced", ticks / 20), 0xFF5555));
        }
    }

    public static int silencedFor(Player player, long now) {
        Long until = SILENCED.get(player.getUUID());
        // (a time far ahead is left over from another world)
        return until == null || now >= until || until - now > 20 * 60 ? 0 : (int) (until - now);
    }

    /** Ticks until this player's move with this relic is ready; 0 when it is. */
    public int moveWait(Player player, long now) {
        Long ready = MOVE_READY.get(player.getUUID() + "/" + god.id());
        // a time further ahead than any recharge is left over from another world: ignore it
        int wait = ready == null || now >= ready || ready - now > MOVE_COOLDOWN_TICKS ? 0 : (int) (ready - now);
        return Math.max(wait, silencedFor(player, now));
    }

    /** Makes the move ready at once. */
    public void resetMove(Player player) {
        MOVE_READY.remove(player.getUUID() + "/" + god.id());
        SILENCED.remove(player.getUUID());
        player.getCooldowns().removeCooldown(god.weaponId());
    }

    /**
     * Casts the god's signature move for the player. @return false if it is still recharging or could not be made.
     */
    public boolean castMove(ServerLevel level, Player player, ItemStack stack) {
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".move.";
        ServerPlayer shown = player instanceof ServerPlayer sp ? sp : null;
        long now = level.getServer().getTickCount();
        int wait = moveWait(player, now);
        if (wait > 0) {
            if (shown != null) Hud.actionBar(shown, Hud.colored(Component.translatable(hud + "wait", god.moveName(), (wait + 19) / 20), 0xAAAAAA));
            return false;
        }
        float damage = moveDamage(stack);
        if (!god.signature().perform(level, player, god, damage)) {
            if (shown != null) Hud.actionBar(shown, Hud.colored(Component.translatable(hud + "notarget", god.moveName()), 0xAAAAAA));
            return false;
        }
        int cooldown = moveCooldown(stack);
        // (the Focus skill brings it back sooner)
        MOVE_READY.put(player.getUUID() + "/" + god.id(), now + Math.round(cooldown * (player instanceof ServerPlayer skilled ? com.thefarawaydev.realmsofchaos.world.Skills.moveScale(skilled) : 1.0)));
        // melee kinds have no shots to time, so the recharge can show on the item itself
        if (!god.kind().ranged()) player.getCooldowns().addCooldown(stack, cooldown);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
        if (shown != null) Hud.actionBar(shown, Hud.colored(Component.translatable(hud + "cast", god.moveName()), god.line().color()));
        if (WeaponProgress.stars(stack) >= AWAKENED_STARS) {
            // an awakened relic answers itself: the move comes again, softer, a moment later
            Scheduler.after(level.getServer(), 16, () -> {
                if (!player.isAlive() || player.level() != level) return;
                if (god.signature().perform(level, player, god, damage * 0.6F) && shown != null) {
                    Hud.actionBar(shown, Hud.colored(Component.translatable(hud + "echo", god.moveName()), god.line().color()));
                }
            });
        }
        return true;
    }

    // ---- levelling -------------------------------------------------------------------------------------------

    /** Called by the loaders whenever a living thing dies: a kill with a relic in hand feeds that relic. */
    public static void onKill(LivingEntity victim, DamageSource source) {
        TarotEffects.onDeath(victim, source);
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof GodWeaponItem weapon)) return;
        int gained = WeaponProgress.addXp(held, WeaponProgress.xpFor(victim));
        if (gained > 0) {
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".levelup",
                    held.getHoverName(), WeaponProgress.level(held)), weapon.god().line().color()));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    // ---- presentation ----------------------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        int stars = WeaponProgress.stars(stack), level = WeaponProgress.level(stack);
        Component base = super.getName(stack);
        if (stars == 0 && level == 1) return base;
        StringBuilder suffix = new StringBuilder();
        if (level > 1) suffix.append(" +").append(level - 1);
        if (stars > 0) suffix.append(' ').append("★".repeat(stars));
        return base.copy().append(Component.literal(suffix.toString()));
    }

    private static MutableComponent line(String key, int color, Object... args) {
        return Component.translatable(key, args).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    /** Ten squares, as many lit as the fraction says. */
    public static Component meter(double fraction, int color) {
        int lit = (int) Math.max(0, Math.min(10, Math.floor(fraction * 10)));
        return Component.empty()
                .append(Component.literal("\u25A0".repeat(lit)).withStyle(Style.EMPTY.withColor(color).withItalic(false)))
                .append(Component.literal("\u25A0".repeat(10 - lit)).withStyle(Style.EMPTY.withColor(0x444444).withItalic(false)));
    }

    /** Five stars, the forged ones gold. */
    public static Component starRow(int stars) {
        return Component.empty()
                .append(Component.literal("\u2605".repeat(stars)).withStyle(Style.EMPTY.withColor(0xF5B82E).withItalic(false)))
                .append(Component.literal("\u2605".repeat(WeaponProgress.MAX_STARS - stars)).withStyle(Style.EMPTY.withColor(0x444444).withItalic(false)));
    }

    /** The two forging perks, each gold once earned and grey with what it takes until then. */
    public static void perkLines(ItemStack stack, Consumer<Component> out) {
        String ns = "tooltip." + RealmsOfChaos.MOD_ID + ".perk.";
        int stars = WeaponProgress.stars(stack);
        for (int[] perk : new int[][]{{EMPOWERED_STARS, 0}, {AWAKENED_STARS, 1}}) {
            boolean earned = stars >= perk[0];
            MutableComponent text = Component.literal("  ").append(line(ns + (perk[1] == 0 ? "empowered" : "awakened"), earned ? 0xF5B82E : 0x777777));
            if (!earned) text.append(Component.literal(" ")).append(line(ns + "locked", 0x777777, perk[0]));
            out.accept(text);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        String ns = "tooltip." + RealmsOfChaos.MOD_ID + ".";
        int level = WeaponProgress.level(stack), stars = WeaponProgress.stars(stack);
        builder.accept(god.displayTitle().copy().withStyle(Style.EMPTY.withColor(god.line().color()).withItalic(true)));
        builder.accept(Component.empty().append(god.kind().displayName()).append(Component.literal(" - "))
                .append(Component.translatable(ns + "line", god.line().displayName()))
                .append(Component.literal(" - ")).append(Component.translatable(ns + "rank", god.rank()))
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        builder.accept(god.kind().useHint().copy().withStyle(Style.EMPTY.withColor(0x55FFFF)));
        builder.accept(line(ns + "hit", 0x55FFFF, god.power().description()));
        builder.accept(line(ns + "flourish", 0xAAAAAA));
        builder.accept(Component.empty());
        // the signature move, and what forging does to it
        builder.accept(line(ns + (god.kind().ranged() ? "move.sneak" : "move"), 0xF5B82E, god.moveName()));
        builder.accept(Component.literal("  ").append(god.signature().description().copy().withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(false))));
        perkLines(stack, builder);
        builder.accept(Component.empty());
        builder.accept(level >= WeaponProgress.MAX_LEVEL
                ? line(ns + "level.max", 0x55FF55)
                : line(ns + "level", 0x55FF55, level, meter(WeaponProgress.xp(stack) / (double) WeaponProgress.xpNeeded(level), 0x55FF55),
                        WeaponProgress.xp(stack), WeaponProgress.xpNeeded(level)));
        builder.accept(line(ns + "stars", 0xF5B82E, starRow(stars)));
        int temper = WeaponProgress.temper(stack);
        if (temper > 0) builder.accept(line(ns + "temper", 0x55FFFF, temper, temper * 2));
        int bonus = (int) Math.round((WeaponProgress.multiplier(stack) - 1.0) * 100);
        if (bonus > 0) builder.accept(line(ns + "bonus", 0xF5B82E, bonus));
    }
}
