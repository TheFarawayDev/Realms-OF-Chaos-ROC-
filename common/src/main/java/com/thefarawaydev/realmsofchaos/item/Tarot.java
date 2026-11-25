package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.Hermitage;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The 22 Major Arcana. Each card does one thing, following the card index. Single cards are used up; the Deck of
 * Fate, dropped by Hades, is a stack that never runs out. The gods of Hades' tower drop the cards, and Hades
 * himself throws them.
 */
public enum Tarot {
    FOOL("fool", 120), MAGICIAN("magician", 60), HIGH_PRIESTESS("high_priestess", 60), EMPRESS("empress", 60),
    EMPEROR("emperor", 60), HIEROPHANT("hierophant", 120), LOVERS("lovers", 60), CHARIOT("chariot", 45),
    STRENGTH("strength", 60), HERMIT("hermit", 10), WHEEL_OF_FORTUNE("wheel_of_fortune", 120), JUSTICE("justice", 45),
    HANGED_MAN("hanged_man", 90), DEATH("death", 300), TEMPERANCE("temperance", 90), DEVIL("devil", 120),
    TOWER("tower", 30), STAR("star", 120), MOON("moon", 90), SUN("sun", 45), JUDGEMENT("judgement", 180), WORLD("world", 120);

    public static final int ILLUSIONS = 5;

    /** How many relics of this line's tower the player carries (inventory, armour and off hand), by item tag. */
    public static int relicsHeld(ServerPlayer player, Line line) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(line.relicTag())) n += stack.getCount();
        }
        return n;
    }

    /**
     * Where The Tower sends a player: always a different tower from the one they stand in, and of the others the
     * one whose relics they hold fewest of - the tower they have the most left to win from. Ties fall at random.
     */
    public static Line towerFor(ServerPlayer player) {
        com.thefarawaydev.realmsofchaos.world.Realm here = com.thefarawaydev.realmsofchaos.world.Realm.of(player.level().dimension());
        Line current = here == null ? null : here.line();
        java.util.List<Line> fewest = new java.util.ArrayList<>();
        int least = Integer.MAX_VALUE;
        for (Line line : Line.values()) {
            if (line == current) continue;
            int held = relicsHeld(player, line);
            if (held < least) {
                least = held;
                fewest.clear();
            }
            if (held == least) fewest.add(line);
        }
        return fewest.get(player.getRandom().nextInt(fewest.size()));
    }
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".";

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

    private static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** Wears the piece if that slot is free, otherwise puts it in the pack. */
    private static void equip(ServerPlayer player, EquipmentSlot slot, Item item) {
        if (player.getItemBySlot(slot).isEmpty()) player.setItemSlot(slot, new ItemStack(item));
        else hand(player, new ItemStack(item));
    }

    /**
     * Whether the card can be played right now. Only The Devil can refuse: it needs an item in the off hand to
     * know what everything should become, and without one it does nothing rather than wiping the inventory.
     */
    public boolean canCast(ServerPlayer player) {
        return this != DEVIL || !player.getOffhandItem().isEmpty();
    }

    /** Casts the card for {@code player}. @return false if the card refused and should not be spent. */
    public boolean cast(ServerLevel level, ServerPlayer player) {
        if (!canCast(player)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "devil.need"), 0xFF5555));
            return false;
        }
        switch (this) {
            case FOOL -> fool(level, player);
            case MAGICIAN -> {
                give(player, MobEffects.STRENGTH, 45, 1 + level.getRandom().nextInt(2));      // Strength II or III
                hand(player, new ItemStack(Items.OAK_LOG, 20));
            }
            case HIGH_PRIESTESS -> TarotEffects.bubble(player, 10);
            case EMPRESS -> {
                grow(level, player.blockPosition(), 6);
                give(player, MobEffects.LUCK, 120, 1);
            }
            case EMPEROR -> TarotEffects.emperor(player, 45);
            case HIEROPHANT -> give(player, MobEffects.HERO_OF_THE_VILLAGE, 300, 0);
            case LOVERS -> {
                for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10), Player::isAlive)) {
                    give(ally, MobEffects.REGENERATION, 15, 1);
                    give(ally, MobEffects.STRENGTH, 30, 0);
                    give(ally, MobEffects.RESISTANCE, 30, 0);
                }
            }
            case CHARIOT -> {
                give(player, MobEffects.SPEED, 30, 2);
                TarotEffects.chariot(player, 30);
            }
            case STRENGTH -> {
                give(player, MobEffects.STRENGTH, 45, 1);
                give(player, MobEffects.RESISTANCE, 45, 0);
            }
            case HERMIT -> Hermitage.toggle(player);
            case WHEEL_OF_FORTUNE -> give(player, MobEffects.LUCK, 120, 9);
            case JUSTICE -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32), e -> !(e instanceof Player))) {
                    give(e, MobEffects.GLOWING, 30, 0);
                }
            }
            case HANGED_MAN -> TarotEffects.hangedMan(player, 30);
            case DEATH -> hand(player, new ItemStack(Items.TOTEM_OF_UNDYING));
            case TEMPERANCE -> {
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
            }
            case DEVIL -> TarotEffects.devil(player, player.getOffhandItem().getItem(), 30);
            case TOWER -> Teleports.enterTower(player, towerFor(player));
            case STAR -> {
                player.giveExperiencePoints(150);
                give(player, MobEffects.LUCK, 120, 0);
            }
            case MOON -> moon(level, player);
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
        if (player.level() instanceof ServerLevel now) {
            now.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.6, 0.5, 0.4);
            now.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
            now.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
        }
        return true;
    }

    /** Iron from head to foot, then off to the nearest village or - failing that - mineshaft, in the overworld. */
    private static void fool(ServerLevel level, ServerPlayer player) {
        equip(player, EquipmentSlot.HEAD, Items.IRON_HELMET);
        equip(player, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
        equip(player, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        equip(player, EquipmentSlot.FEET, Items.IRON_BOOTS);
        hand(player, new ItemStack(Items.IRON_SWORD));

        ServerLevel overworld = level.getServer().overworld();
        BlockPos origin = level == overworld ? player.blockPosition() : overworld.getRespawnData().globalPos().pos();
        boolean villageFirst = level.getRandom().nextBoolean();
        BlockPos found = locate(overworld, origin, villageFirst);
        if (found == null) found = locate(overworld, origin, !villageFirst);
        if (found == null) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "fool.none"), 0xAAAAAA));
            return;
        }
        Teleports.rememberReturn(player, level.getServer());
        overworld.getChunk(found);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(overworld, found.getX() + 0.5, y, found.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "fool.found"), 0x55FF55));
    }

    private static BlockPos locate(ServerLevel overworld, BlockPos origin, boolean village) {
        try {
            return overworld.findNearestMapStructure(village ? StructureTags.VILLAGE : StructureTags.MINESHAFT, origin, 64, false);
        } catch (RuntimeException e) {
            return null;        // worlds without structures (superflat presets, test worlds) simply have nowhere to go
        }
    }

    /** Bonemeals crops, saplings and the like around a point. Ground cover that only spreads is left alone. */
    public static int grow(ServerLevel level, BlockPos centre, int radius) {
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock plant) || state.getBlock() instanceof GrassBlock || state.getBlock() instanceof NyliumBlock) continue;
            for (int i = 0; i < 4; i++) {
                BlockState current = level.getBlockState(pos);
                if (current.getBlock() != plant || !plant.isValidBonemealTarget(level, pos, current, net.minecraft.world.level.block.BonemealSource.INTERACTION)) break;
                plant.performBonemeal(level, level.getRandom(), pos.immutable(), current, net.minecraft.world.level.block.BonemealSource.INTERACTION);
                grown++;
            }
        }
        return grown;
    }

    /** Illusions: spectral vexes that cannot hurt you and fade on their own. Slay them all for the Moon's reward. */
    private static void moon(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < ILLUSIONS; i++) {
            Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.TRIGGERED);
            if (vex == null) continue;
            double a = Math.PI * 2 * i / ILLUSIONS;
            vex.setPos(player.getX() + Math.cos(a) * 4, player.getY() + 1.5, player.getZ() + Math.sin(a) * 4);
            var damage = vex.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(0.0);
            vex.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            vex.setLimitedLife(20 * 45);
            vex.setBoundOrigin(player.blockPosition().above());
            vex.addTag(TarotEffects.ILLUSION_TAG);
            vex.setCustomName(Component.translatable("entity." + RealmsOfChaos.MOD_ID + ".illusion"));
            vex.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 45, 0));
            vex.setTarget(player);
            level.addFreshEntity(vex);
            TarotEffects.addIllusion(player, vex);
        }
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "moon.start", ILLUSIONS), 0xAAAAFF));
    }
}
