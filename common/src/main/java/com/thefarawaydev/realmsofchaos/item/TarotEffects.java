package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The tarot effects that last: things vanilla status effects cannot express, kept per player with an end time and
 * serviced once a tick. When an effect ends, whatever it changed is put back.
 */
public final class TarotEffects {
    public static final String ILLUSION_TAG = "realms_of_chaos_illusion";
    private static final Identifier EMPEROR_KNOCKBACK = RealmsOfChaos.id("tarot_emperor_knockback");
    private static final Identifier EMPEROR_ARMOR = RealmsOfChaos.id("tarot_emperor_armor");
    private static final Identifier CHARIOT_STEP = RealmsOfChaos.id("tarot_chariot_step");
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".";

    private static final class State {
        long emperorUntil, chariotUntil, bubbleUntil, hangedUntil, devilUntil;
        Item devilItem;
        int illusionsLeft, illusionsSlain, illusionsTotal;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Which player each living illusion belongs to. */
    private static final Map<UUID, UUID> ILLUSION_OWNER = new HashMap<>();

    private TarotEffects() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static long now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    private static void modifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                 Identifier id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    // ---- starting effects ------------------------------------------------------------------------------------

    /** The Emperor: nothing knocks you back and your armour hardens. */
    public static void emperor(ServerPlayer player, int seconds) {
        state(player).emperorUntil = now(player) + seconds * 20L;
        modifier(player, Attributes.KNOCKBACK_RESISTANCE, EMPEROR_KNOCKBACK, 1.0);
        modifier(player, Attributes.ARMOR, EMPEROR_ARMOR, 8.0);
    }

    /** The Chariot: you step up full blocks without jumping. */
    public static void chariot(ServerPlayer player, int seconds) {
        state(player).chariotUntil = now(player) + seconds * 20L;
        modifier(player, Attributes.STEP_HEIGHT, CHARIOT_STEP, 1.0);
    }

    /** The High Priestess: a bubble no harm can enter. Foes are pushed out of it for as long as it stands. */
    public static void bubble(ServerPlayer player, int seconds) {
        state(player).bubbleUntil = now(player) + seconds * 20L;
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, seconds * 20, 4));
    }

    /** The Hanged Man: monsters lose interest in you (gods are not fooled). */
    public static void hangedMan(ServerPlayer player, int seconds) {
        state(player).hangedUntil = now(player) + seconds * 20L;
        calm(player);
    }

    public static boolean isUntargetable(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && s.hangedUntil > now(player);
    }

    /**
     * The Devil: the inventory burns - all but worn armour, the Deck of Fate and the chosen item - and until the
     * bargain ends everything picked up turns into the chosen item.
     */
    public static void devil(ServerPlayer player, Item chosen, int seconds) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !stack.is(chosen) && !(stack.getItem() instanceof TarotDeckItem)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        State s = state(player);
        s.devilItem = chosen;
        s.devilUntil = now(player) + seconds * 20L;
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "devil.start", new ItemStack(chosen).getHoverName()), 0xFF5555));
    }

    public static boolean devilActive(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && s.devilItem != null && s.devilUntil > now(player);
    }

    /** The Moon: registers one summoned illusion for this player. */
    public static void addIllusion(ServerPlayer player, LivingEntity illusion) {
        State s = state(player);
        s.illusionsLeft++;
        s.illusionsTotal++;
        ILLUSION_OWNER.put(illusion.getUUID(), player.getUUID());
    }

    public static int illusionsLeft(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0 : s.illusionsLeft;
    }

    /** Called when anything dies. Illusions count towards their owner's tally; slaying them all earns the reward. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        UUID ownerId = ILLUSION_OWNER.remove(victim.getUUID());
        if (ownerId == null || victim.level().getServer() == null) return;
        State s = STATES.get(ownerId);
        if (s == null) return;
        ServerPlayer owner = victim.level().getServer().getPlayerList().getPlayer(ownerId);
        if (source.getEntity() instanceof ServerPlayer killer && killer.getUUID().equals(ownerId)) {
            s.illusionsSlain++;
            owner = killer;
        }
        s.illusionsLeft = Math.max(0, s.illusionsLeft - 1);
        if (s.illusionsLeft > 0) {
            if (owner != null) Hud.actionBar(owner, Hud.colored(Component.translatable(HUD + "moon.left", s.illusionsLeft), 0xAAAAFF));
            return;
        }
        boolean all = s.illusionsSlain >= s.illusionsTotal;
        s.illusionsSlain = 0;
        s.illusionsTotal = 0;
        if (owner == null) return;
        if (all) {
            owner.giveExperiencePoints(120);
            owner.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 60, 0));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
            Hud.actionBar(owner, Hud.colored(Component.translatable(HUD + "moon.done"), 0x55FF55));
        } else {
            Hud.actionBar(owner, Hud.colored(Component.translatable(HUD + "moon.failed"), 0xAAAAAA));
        }
    }

    // ---- servicing -------------------------------------------------------------------------------------------

    private static void calm(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> !(m instanceof GodBoss))) {
            if (mob.getTarget() == player) mob.setTarget(null);
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> entry = it.next();
            State s = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                if (s.illusionsLeft == 0) it.remove();      // transient attribute changes do not survive a logout
                continue;
            }
            service(player, s, now);
            if (s.emperorUntil == 0 && s.chariotUntil == 0 && s.bubbleUntil == 0 && s.hangedUntil == 0 && s.devilUntil == 0 && s.illusionsLeft == 0) {
                it.remove();
            }
        }
    }

    /** One tick of one player's lasting effects. */
    public static void service(ServerPlayer player, long now) {
        State s = STATES.get(player.getUUID());
        if (s != null) service(player, s, now);
    }

    private static void service(ServerPlayer player, State s, long now) {
        if (s.emperorUntil != 0 && now >= s.emperorUntil) {
            s.emperorUntil = 0;
            modifier(player, Attributes.KNOCKBACK_RESISTANCE, EMPEROR_KNOCKBACK, 0);
            modifier(player, Attributes.ARMOR, EMPEROR_ARMOR, 0);
        }
        if (s.chariotUntil != 0 && now >= s.chariotUntil) {
            s.chariotUntil = 0;
            modifier(player, Attributes.STEP_HEIGHT, CHARIOT_STEP, 0);
        }
        if (s.bubbleUntil != 0) {
            if (now >= s.bubbleUntil) {
                s.bubbleUntil = 0;
            } else if (player.level() instanceof ServerLevel level && now % 5 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.0), x -> x instanceof Enemy)) {
                    Vec3 away = e.position().subtract(player.position());
                    away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
                    e.setDeltaMovement(away.x * 0.9, 0.25, away.z * 0.9);
                    e.needsSync = true;
                }
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), player.getX() + Math.cos(a) * 3, player.getY() + 1, player.getZ() + Math.sin(a) * 3, 1, 0, 0.3, 0, 0);
                }
            }
        }
        if (s.hangedUntil != 0) {
            if (now >= s.hangedUntil) s.hangedUntil = 0;
            else if (now % 10 == 0) calm(player);
        }
        if (s.devilUntil != 0) {
            if (now >= s.devilUntil || s.devilItem == null) {
                s.devilUntil = 0;
                s.devilItem = null;
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "devil.end"), 0xAAAAAA));
            } else {
                Inventory inventory = player.getInventory();
                for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || stack.is(s.devilItem) || stack.getItem() instanceof TarotDeckItem) continue;
                    ItemStack turned = new ItemStack(s.devilItem, Math.min(stack.getCount(), new ItemStack(s.devilItem).getMaxStackSize()));
                    inventory.setItem(slot, turned);
                }
            }
        }
    }
}
