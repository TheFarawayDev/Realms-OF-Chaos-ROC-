package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Signature;
import com.thefarawaydev.realmsofchaos.item.GodWeaponItem;
import com.thefarawaydev.realmsofchaos.item.WeaponProgress;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Forge of Chaos screen, for the relic weapon in the player's main hand.
 *
 * <pre>
 *   row 0   . . . . W . . . X      the weapon; step back
 *   row 1   . . * * * * * . .      its five stars: forged, next, still to come
 *   row 2   . L . M . P . G .      level - signature move and perks - what the next star gives - materials guide
 *   row 3   . I . c F c . . .      infuse experience - the price either side of the forge button
 *   row 4   . = = = = = = = .      experience towards the next level
 *   row 5   - - - m M m - - -      while forging: the strike bar - a hammer runs along it, click when it is on the mark
 * </pre>
 *
 * Forging is a quick-time event. Pressing the forge button pays the price and starts three strikes; each is judged
 * by where the hammer is when the button is pressed again. The star is forged whatever happens - the strikes decide
 * the finish: a masterwork (no misses, two perfect) tempers the star for 2% more damage, fine work earns the relic
 * experience. A star that was not tempered can be struck again later with the Temper button.
 */
public class ForgeMenu extends ButtonMenu {
    public static final int SLOT_WEAPON = slot(0, 4), SLOT_CLOSE = slot(0, 8), STAR_ROW = 1, STAR_COLUMN = 2,
            SLOT_LEVEL = slot(2, 1), SLOT_MOVE = slot(2, 3), SLOT_PREVIEW = slot(2, 5), SLOT_GUIDE = slot(2, 7),
            SLOT_INFUSE = slot(3, 1), SLOT_COST = slot(3, 3), SLOT_FORGE = slot(3, 4), SLOT_COST_2 = slot(3, 5), SLOT_TEMPER = slot(3, 7),
            XP_ROW = 4, BAR_ROW = 5;
    public static final int STRIKES = 3, TEMPER_COST = 2, MASTERWORK_XP = 60, FINE_XP = 30;

    /** How a round of strikes came out. */
    public enum Finish { MASTERWORK, FINE, ROUGH }
    /** Weapon experience one of the player's own levels is worth when infused. */
    public static final int INFUSE_XP = 30;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".forge.";
    private static final String TIP = "tooltip." + RealmsOfChaos.MOD_ID + ".";

    /** The forge block this screen was opened at, or null when it was opened from the Codex or a command. */
    private final BlockPos forge;
    // the quick-time round in progress, if any
    private StrikeBar bar;
    private boolean tempering;
    private final List<StrikeBar.Hit> hits = new ArrayList<>();
    private int drawnMarker = -1;
    private Finish lastFinish;

    public static void open(ServerPlayer player) {
        open(player, null);
    }

    public static void open(ServerPlayer player, BlockPos forge) {
        Tutorial.tip(player, Tutorial.Tip.FORGE);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ForgeMenu(id, inventory, (ServerPlayer) p, forge),
                Skin.title(player, Skin.Panel.FORGE, Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".forge"))));
    }

    private ForgeMenu(int id, Inventory inventory, ServerPlayer viewer, BlockPos forge) {
        super(id, inventory, viewer, 6);
        this.forge = forge;
        render();
    }

    private ItemStack weapon() {
        ItemStack held = viewer.getMainHandItem();
        return held.getItem() instanceof GodWeaponItem ? held : ItemStack.EMPTY;
    }

    public static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void take(ServerPlayer player, Item item, int amount) {
        for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                int n = Math.min(amount, s.getCount());
                s.shrink(n);
                amount -= n;
            }
        }
    }

    /** Why the next star cannot be forged right now, or null if it can. */
    public String blocker() {
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) return "none";
        int next = WeaponProgress.stars(weapon) + 1;
        if (next > WeaponProgress.MAX_STARS) return "max";
        if (WeaponProgress.level(weapon) < WeaponProgress.starLevel(next)) return "level";
        God god = ((GodWeaponItem) weapon.getItem()).god();
        if (!viewer.hasInfiniteMaterials()) {
            for (WeaponProgress.Cost cost : WeaponProgress.starCost(next)) {
                if (count(viewer, ModContent.materialItem(god.line(), cost.tier())) < cost.count()) return "cost";
            }
        }
        return null;
    }

    private long now() {
        return viewer.level().getServer().getTickCount();
    }

    /** The strike bar of the round in progress, or null. */
    public StrikeBar strikeBar() {
        return bar;
    }

    /** How the last finished round came out, or null if none has been struck on this screen. */
    public Finish lastFinish() {
        return lastFinish;
    }

    /** Why the weapon cannot be tempered right now, or null if it can. */
    public String temperBlocker() {
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) return "none";
        if (WeaponProgress.temper(weapon) >= WeaponProgress.stars(weapon)) return "full";
        God god = ((GodWeaponItem) weapon.getItem()).god();
        if (!viewer.hasInfiniteMaterials() && count(viewer, ModContent.materialItem(god.line(), 1)) < TEMPER_COST) return "cost";
        return null;
    }

    @Override
    protected void press(int slot) {
        if (bar != null) {
            // a round is being struck: the forge button and the bar itself are the hammer, nothing else answers
            if (slot == SLOT_FORGE || slot / 9 == BAR_ROW) strike();
            return;
        }
        if (slot == SLOT_CLOSE) {
            clickSound();
            viewer.closeContainer();
        } else if (slot == SLOT_INFUSE) {
            infuse(shiftClick ? 5 : 1);
        } else if (slot == SLOT_FORGE) {
            forgeStar();
        } else if (slot == SLOT_TEMPER) {
            startTemper();
        }
    }

    private void beginRound(boolean temper) {
        tempering = temper;
        hits.clear();
        newBar();
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6F, 1.2F);
        render();
    }

    private void newBar() {
        ItemStack weapon = weapon();
        // the later stars are struck faster
        int speed = tempering || WeaponProgress.stars(weapon) >= 2 ? 2 : 3;
        bar = new StrikeBar(now(), speed, 2 + viewer.getRandom().nextInt(5));
        drawnMarker = -1;
    }

    private void strike() {
        StrikeBar.Hit hit = bar.hit(now());
        hits.add(hit);
        float pitch = hit == StrikeBar.Hit.PERFECT ? 1.6F : hit == StrikeBar.Hit.GOOD ? 1.2F : 0.6F;
        viewer.level().playSound(null, viewer.blockPosition(), hit == StrikeBar.Hit.MISS ? SoundEvents.ANVIL_LAND : SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, pitch);
        if (hit == StrikeBar.Hit.PERFECT) viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.4F);
        effects(hit == StrikeBar.Hit.MISS ? com.thefarawaydev.realmsofchaos.fx.RocParticles.SMOKE.type() : com.thefarawaydev.realmsofchaos.fx.RocParticles.EMBER.type(), hit == StrikeBar.Hit.PERFECT ? 10 : 4);
        Hud.actionBar(viewer, Hud.colored(Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".strike." + hit.name().toLowerCase() + ".hit"),
                hit == StrikeBar.Hit.PERFECT ? 0xFFFF55 : hit == StrikeBar.Hit.GOOD ? 0x55FF55 : 0xFF5555));
        if (hits.size() >= STRIKES) {
            finishRound();
        } else {
            newBar();
            render();
        }
    }

    public static Finish judge(List<StrikeBar.Hit> hits) {
        int perfect = 0, miss = 0;
        for (StrikeBar.Hit hit : hits) {
            if (hit == StrikeBar.Hit.PERFECT) perfect++;
            if (hit == StrikeBar.Hit.MISS) miss++;
        }
        return miss == 0 && perfect >= 2 ? Finish.MASTERWORK : miss <= 1 ? Finish.FINE : Finish.ROUGH;
    }

    /** The round is over: the star is forged (or the temper taken), with the finish the strikes earned. */
    private void finishRound() {
        while (hits.size() < STRIKES) hits.add(StrikeBar.Hit.MISS);        // a round walked away from is finished roughly
        Finish finish = judge(hits);
        lastFinish = finish;
        bar = null;
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) return;
        God god = ((GodWeaponItem) weapon.getItem()).god();
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".";
        if (tempering) {
            if (finish == Finish.MASTERWORK) {
                WeaponProgress.setTemper(weapon, WeaponProgress.temper(weapon) + 1);
                viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
                effects(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), 30);
                Hud.title(viewer, Hud.colored(Component.translatable(hud + "finish.masterwork"), 0x55FFFF),
                        Hud.colored(Component.translatable(hud + "finish.tempered", WeaponProgress.temper(weapon) * 2), 0xFFFFFF), 5, 40, 15);
            } else {
                viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
                Hud.actionBar(viewer, Hud.colored(Component.translatable(hud + "finish.temper.failed"), 0xFF5555));
            }
            render();
            return;
        }
        int next = WeaponProgress.stars(weapon) + 1;
        WeaponProgress.setStars(weapon, next);
        if (finish == Finish.MASTERWORK) WeaponProgress.setTemper(weapon, WeaponProgress.temper(weapon) + 1);
        if (finish != Finish.ROUGH) WeaponProgress.addXp(weapon, finish == Finish.MASTERWORK ? MASTERWORK_XP : FINE_XP);

        boolean milestone = next == GodWeaponItem.EMPOWERED_STARS || next == GodWeaponItem.AWAKENED_STARS;
        viewer.level().playSound(null, viewer.blockPosition(), milestone ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.7F, milestone ? 1.2F : 0.8F);
        effects(com.thefarawaydev.realmsofchaos.fx.RocParticles.FLAME_STORM.type(), 30);
        effects(Signature.spark(god.line()), 20 * next);
        if (milestone || finish == Finish.MASTERWORK) effects(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), 40);
        Component what = next == GodWeaponItem.AWAKENED_STARS ? Component.translatable(hud + "forged.awakened", god.moveName())
                : next == GodWeaponItem.EMPOWERED_STARS ? Component.translatable(hud + "forged.empowered", god.moveName())
                : Component.translatable(hud + "finish." + finish.name().toLowerCase() + ".sub");
        Hud.title(viewer, GodWeaponItem.starRow(next), Hud.colored(what, milestone ? 0xF5B82E : 0xFFFFFF), 5, 40, 15);
        Hud.actionBar(viewer, Hud.colored(Component.translatable(hud + "finish." + finish.name().toLowerCase()),
                finish == Finish.MASTERWORK ? 0x55FFFF : finish == Finish.FINE ? 0x55FF55 : 0xAAAAAA));
        render();
    }

    /** Closing the screen mid-round does not lose the price paid: the round ends as it stands. */
    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        if (bar != null) finishRound();
        super.removed(player);
    }

    private void drawBar() {
        long now = now();
        ItemStack[] row = bar.row(now);
        for (int i = 0; i < StrikeBar.SLOTS; i++) view.setItem(slot(BAR_ROW, i), row[i]);
        drawnMarker = bar.marker(now);
    }

    /** Called every server tick while the screen is open: during a round the hammer moves on. */
    @Override
    public void broadcastChanges() {
        if (bar != null && bar.marker(now()) != drawnMarker) drawBar();
        super.broadcastChanges();
    }

    private void startTemper() {
        String blocker = temperBlocker();
        if (blocker != null) {
            if ("cost".equals(blocker)) {
                Hud.actionBar(viewer, Hud.colored(Component.translatable(NS + "blocked.cost"), 0xFF5555));
                viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            render();
            return;
        }
        ItemStack weapon = weapon();
        God god = ((GodWeaponItem) weapon.getItem()).god();
        if (!viewer.hasInfiniteMaterials()) take(viewer, ModContent.materialItem(god.line(), 1), TEMPER_COST);
        beginRound(true);
    }

    /** Pours the player's own experience levels into the weapon. */
    private void infuse(int levels) {
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) return;
        if (WeaponProgress.level(weapon) >= WeaponProgress.MAX_LEVEL) {
            render();
            return;
        }
        boolean free = viewer.hasInfiniteMaterials();
        int given = free ? levels : Math.min(levels, viewer.experienceLevel);
        if (given <= 0) {
            Hud.actionBar(viewer, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".infuse.none"), 0xFF5555));
            viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
            return;
        }
        if (!free) viewer.giveExperienceLevels(-given);
        int before = WeaponProgress.level(weapon);
        WeaponProgress.addXp(weapon, given * INFUSE_XP);
        boolean levelled = WeaponProgress.level(weapon) > before;
        viewer.level().playSound(null, viewer.blockPosition(), levelled ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.7F, levelled ? 1.2F : 0.8F);
        effects(com.thefarawaydev.realmsofchaos.fx.RocParticles.RUNE.type(), 30);
        Hud.actionBar(viewer, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".infused", weapon.getHoverName(), given * INFUSE_XP), 0x55FF55));
        render();
    }

    private void forgeStar() {
        String blocker = blocker();
        if (blocker != null) {
            ItemStack weapon = weapon();
            if (!weapon.isEmpty() && ("level".equals(blocker) || "cost".equals(blocker))) {
                Component why = "level".equals(blocker)
                        ? Component.translatable(NS + "blocked.level", WeaponProgress.starLevel(WeaponProgress.stars(weapon) + 1))
                        : Component.translatable(NS + "blocked.cost");
                Hud.actionBar(viewer, Hud.colored(why, 0xFF5555));
                viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            render();
            return;
        }
        ItemStack weapon = weapon();
        God god = ((GodWeaponItem) weapon.getItem()).god();
        int next = WeaponProgress.stars(weapon) + 1;
        if (!viewer.hasInfiniteMaterials()) {
            for (WeaponProgress.Cost cost : WeaponProgress.starCost(next)) take(viewer, ModContent.materialItem(god.line(), cost.tier()), cost.count());
        }
        // the price is paid: now the star has to be struck
        beginRound(false);
    }

    /** Sparks at the forge block if there is one, otherwise around the player. */
    private void effects(net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (!(viewer.level() instanceof ServerLevel level)) return;
        Vec3 at = forge != null ? new Vec3(forge.getX() + 0.5, forge.getY() + 1.2, forge.getZ() + 0.5) : viewer.position().add(0, 1.0, 0);
        level.sendParticles(particle, at.x, at.y, at.z, count, 0.35, 0.35, 0.35, 0.05);
    }

    @Override
    protected void render() {
        frame(DyeColor.ORANGE, DyeColor.BLACK);
        view.setItem(SLOT_CLOSE, icon(Items.ARROW, text(NS + "close", 0xFFFFFF), false, List.of()));
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) {
            view.setItem(slot(2, 4), icon(Items.BARRIER, text(NS + "none", 0xFF5555), false, List.of(text(NS + "none.lore", 0xAAAAAA))));
            broadcastChanges();
            return;
        }
        GodWeaponItem item = (GodWeaponItem) weapon.getItem();
        God god = item.god();
        int level = WeaponProgress.level(weapon), xp = WeaponProgress.xp(weapon), stars = WeaponProgress.stars(weapon);
        int need = WeaponProgress.xpNeeded(level);
        boolean maxLevel = level >= WeaponProgress.MAX_LEVEL;
        String blocker = blocker();
        int next = stars + 1;

        view.setItem(SLOT_WEAPON, weapon.copy());

        // the five stars: forged ones shine, the next one is marked, the rest wait
        for (int i = 1; i <= WeaponProgress.MAX_STARS; i++) {
            List<Component> lore = new ArrayList<>();
            if (i == GodWeaponItem.EMPOWERED_STARS) lore.add(text(TIP + "perk.empowered", 0xF5B82E));
            if (i == GodWeaponItem.AWAKENED_STARS) lore.add(text(TIP + "perk.awakened", 0xF5B82E));
            ItemStack star;
            if (i <= stars) {
                lore.add(0, text(NS + "star.owned", 0x55FF55));
                star = icon(Items.NETHER_STAR, text(NS + "star", 0xF5B82E, i), true, lore);
            } else {
                lore.add(0, text(NS + (i == next ? "star.next" : "star.empty"), i == next ? 0xFFFF55 : 0xAAAAAA));
                lore.add(text(NS + "needlevel", level >= WeaponProgress.starLevel(i) ? 0x55FF55 : 0xFF5555, WeaponProgress.starLevel(i)));
                for (WeaponProgress.Cost cost : WeaponProgress.starCost(i)) {
                    lore.add(text(NS + "guide.cost", 0xAAAAAA, cost.count(), new ItemStack(ModContent.materialItem(god.line(), cost.tier())).getHoverName()));
                }
                star = pane(i == next ? DyeColor.YELLOW : DyeColor.GRAY, text(NS + "star", i == next ? 0xFFFF55 : 0x888888, i), lore);
            }
            view.setItem(slot(STAR_ROW, STAR_COLUMN + i - 1), star);
        }

        int bonus = (int) Math.round((WeaponProgress.multiplier(weapon) - 1.0) * 100);
        view.setItem(SLOT_LEVEL, icon(Items.EXPERIENCE_BOTTLE, text(NS + "level", 0x55FF55, level), maxLevel,
                List.of(maxLevel ? text(TIP + "level.max", 0x55FF55) : text(NS + "xp", 0xAAAAAA, xp, need),
                        text(TIP + "bonus", 0xF5B82E, bonus))));

        // the signature move and what forging does to it
        List<Component> moveLore = new ArrayList<>();
        moveLore.add(god.signature().description().copy().withStyle(plain(0xAAAAAA)));
        moveLore.add(text(NS + "move.damage", 0xAAAAAA, String.format("%.1f", item.moveDamage(weapon)),
                String.format("%.1f", GodWeaponItem.moveCooldown(weapon) / 20.0)));
        GodWeaponItem.perkLines(weapon, moveLore::add);
        view.setItem(SLOT_MOVE, icon(Items.BLAZE_POWDER, text(NS + "move", god.line().color(), god.moveName()), stars >= GodWeaponItem.EMPOWERED_STARS, moveLore));

        // what the next star changes, in numbers
        List<Component> preview = new ArrayList<>();
        if ("max".equals(blocker)) {
            preview.add(text(NS + "preview.max", 0x55FF55));
        } else {
            ItemStack after = weapon.copy();
            WeaponProgress.setStars(after, next);
            preview.add(text(NS + "preview.damage", 0xFFFFFF, bonus, (int) Math.round((WeaponProgress.multiplier(after) - 1.0) * 100)));
            preview.add(text(NS + "preview.recharge", 0xFFFFFF, String.format("%.1f", GodWeaponItem.moveCooldown(weapon) / 20.0),
                    String.format("%.1f", GodWeaponItem.moveCooldown(after) / 20.0)));
            if (next == GodWeaponItem.EMPOWERED_STARS) preview.add(text(TIP + "perk.empowered", 0xF5B82E));
            if (next == GodWeaponItem.AWAKENED_STARS) preview.add(text(TIP + "perk.awakened", 0xF5B82E));
        }
        view.setItem(SLOT_PREVIEW, icon(Items.SPYGLASS, text(NS + "preview", 0x55FFFF), false, preview));

        // where each material of this weapon's tower comes from, and how many the player holds
        List<Component> guide = new ArrayList<>();
        String[] floors = {"1-5", "6-10", "11-14", "15"};
        for (int tier = 1; tier <= 4; tier++) {
            Item material = ModContent.materialItem(god.line(), tier);
            int have = count(viewer, material);
            guide.add(text(NS + "guide.line", have > 0 ? 0x55FF55 : 0xAAAAAA, have, new ItemStack(material).getHoverName(),
                    Tower.tierName(god.line(), tier == 1 ? 1 : tier == 2 ? 6 : tier == 3 ? 11 : 15), floors[tier - 1]));
        }
        guide.add(text(NS + "guide.rematch", 0x55FFFF));
        view.setItem(SLOT_GUIDE, icon(ModContent.CHAOS_CODEX.get(), text(NS + "guide", god.line().color()), false, guide));

        // infusing: the player's own levels become weapon experience
        view.setItem(SLOT_INFUSE, maxLevel
                ? icon(Items.GLASS_BOTTLE, text(NS + "infuse", 0x888888), false, List.of(text(NS + "infuse.max", 0xAAAAAA)))
                : icon(Items.ENCHANTED_BOOK, text(NS + "infuse", 0x55FF55), false, List.of(
                        text(NS + "infuse.lore", 0xAAAAAA, INFUSE_XP), text(NS + "infuse.lore2", 0xAAAAAA),
                        text(NS + "infuse.have", viewer.experienceLevel > 0 ? 0x55FF55 : 0xFF5555, viewer.experienceLevel))));

        // tempering: a second go at the finish of a star that was not a masterwork
        int temper = WeaponProgress.temper(weapon);
        if (stars > 0) {
            String tb = temperBlocker();
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + "temper.have", 0x55FFFF, temper, stars, temper * 2));
            if ("full".equals(tb)) {
                lore.add(text(NS + "temper.full", 0x55FF55));
            } else {
                Item material = ModContent.materialItem(god.line(), 1);
                lore.add(text(NS + "temper.lore", 0xAAAAAA));
                lore.add(text(NS + "cost", tb == null ? 0x55FF55 : 0xFF5555, TEMPER_COST, new ItemStack(material).getHoverName(), count(viewer, material)));
            }
            view.setItem(SLOT_TEMPER, icon(Items.GRINDSTONE, text(NS + "temper", tb == null ? 0x55FFFF : 0x888888), tb == null, lore));
        }

        if (bar != null) {
            // a round is being struck
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + "strike.lore", 0xAAAAAA));
            lore.add(text(NS + "strike.left", 0xFFFF55, STRIKES - hits.size()));
            for (StrikeBar.Hit hit : hits) {
                lore.add(text("menu." + RealmsOfChaos.MOD_ID + ".strike." + hit.name().toLowerCase() + ".hit",
                        hit == StrikeBar.Hit.PERFECT ? 0xFFFF55 : hit == StrikeBar.Hit.GOOD ? 0x55FF55 : 0xFF5555));
            }
            view.setItem(SLOT_FORGE, icon(Items.MACE, text(NS + "strike", 0xFFFF55), true, lore));
            drawBar();
        } else if ("max".equals(blocker)) {
            view.setItem(SLOT_FORGE, icon(Items.NETHER_STAR, text(NS + "max", 0x55FF55), true, List.of(text(NS + "awakened", 0xF5B82E))));
        } else {
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + "effect", 0xAAAAAA));
            if (next == GodWeaponItem.EMPOWERED_STARS) lore.add(text(TIP + "perk.empowered", 0xF5B82E));
            if (next == GodWeaponItem.AWAKENED_STARS) lore.add(text(TIP + "perk.awakened", 0xF5B82E));
            lore.add(text(NS + "needlevel", "level".equals(blocker) ? 0xFF5555 : 0x55FF55, WeaponProgress.starLevel(next)));
            int priceSlot = SLOT_COST;
            for (WeaponProgress.Cost cost : WeaponProgress.starCost(next)) {
                Item material = ModContent.materialItem(god.line(), cost.tier());
                int have = count(viewer, material);
                boolean enough = have >= cost.count() || viewer.hasInfiniteMaterials();
                Component line = text(NS + "cost", enough ? 0x55FF55 : 0xFF5555, cost.count(), new ItemStack(material).getHoverName(), have);
                ItemStack price = icon(material, line, enough, List.of(text(NS + "from", 0xAAAAAA,
                        Tower.tierName(god.line(), cost.tier() == 1 ? 1 : cost.tier() == 2 ? 6 : cost.tier() == 3 ? 11 : 15))));
                price.setCount(Math.max(1, Math.min(64, cost.count())));
                view.setItem(priceSlot, price);
                priceSlot = SLOT_COST_2;
                lore.add(line);
            }
            lore.add(text(NS + "strike.hint", 0x55FFFF));
            view.setItem(SLOT_FORGE, icon(Items.ANVIL, text(NS + "upgrade", blocker == null ? 0x55FF55 : 0xFF5555, next), blocker == null, lore));
        }

        // experience towards the next level
        int filled = maxLevel ? 7 : Math.min(7, (int) Math.floor(7.0 * xp / need));
        for (int i = 0; i < 7; i++) {
            view.setItem(slot(XP_ROW, 1 + i), pane(i < filled ? DyeColor.LIME : DyeColor.GRAY, text(NS + "level", 0x55FF55, level),
                    List.of(maxLevel ? text(TIP + "level.max", 0x55FF55) : text(NS + "xp", 0xAAAAAA, xp, need))));
        }
        broadcastChanges();
    }
}
