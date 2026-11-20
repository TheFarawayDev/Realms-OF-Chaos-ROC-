package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.item.GodWeaponItem;
import com.thefarawaydev.realmsofchaos.item.WeaponProgress;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Forge of Chaos screen, for the relic weapon in the player's main hand. The top row shows its five stars, the
 * middle row the weapon, its level, the price of the next star and the forge button, and the bottom row is an
 * experience bar towards the next level.
 */
public class ForgeMenu extends ButtonMenu {
    public static final int SLOT_WEAPON = slot(1, 1), SLOT_LEVEL = slot(1, 2), SLOT_COST = slot(1, 4), SLOT_COST_2 = slot(1, 5), SLOT_FORGE = slot(1, 7);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".forge.";

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ForgeMenu(id, inventory, (ServerPlayer) p),
                Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".forge")));
    }

    private ForgeMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 3);
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

    @Override
    protected void press(int slot) {
        if (slot != SLOT_FORGE) return;
        if (blocker() != null) {
            render();
            return;
        }
        ItemStack weapon = weapon();
        God god = ((GodWeaponItem) weapon.getItem()).god();
        int next = WeaponProgress.stars(weapon) + 1;
        if (!viewer.hasInfiniteMaterials()) {
            for (WeaponProgress.Cost cost : WeaponProgress.starCost(next)) take(viewer, ModContent.materialItem(god.line(), cost.tier()), cost.count());
        }
        WeaponProgress.setStars(weapon, next);
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.1F);
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
        Hud.actionBar(viewer, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".forged", weapon.getHoverName(), next), 0xF5B82E));
        render();
    }

    @Override
    protected void render() {
        frame(DyeColor.ORANGE, DyeColor.BLACK);
        ItemStack weapon = weapon();
        if (weapon.isEmpty()) {
            view.setItem(slot(1, 4), icon(Items.BARRIER, text(NS + "none", 0xFF5555), false, List.of(text(NS + "none.lore", 0xAAAAAA))));
            broadcastChanges();
            return;
        }
        God god = ((GodWeaponItem) weapon.getItem()).god();
        int level = WeaponProgress.level(weapon), xp = WeaponProgress.xp(weapon), stars = WeaponProgress.stars(weapon);
        int need = WeaponProgress.xpNeeded(level);
        boolean maxLevel = level >= WeaponProgress.MAX_LEVEL;

        // top row: the five stars
        for (int i = 1; i <= WeaponProgress.MAX_STARS; i++) {
            boolean owned = i <= stars;
            view.setItem(slot(0, 1 + i), owned
                    ? icon(Items.NETHER_STAR, text(NS + "star", 0xF5B82E, i), true, List.of(text(NS + "star.owned", 0x55FF55)))
                    : pane(DyeColor.GRAY, text(NS + "star", 0x888888, i), List.of(text(NS + "star.empty", 0xAAAAAA),
                            text(NS + "needlevel", 0xAAAAAA, WeaponProgress.starLevel(i)))));
        }

        view.setItem(SLOT_WEAPON, weapon.copy());
        view.setItem(SLOT_LEVEL, icon(Items.EXPERIENCE_BOTTLE, text(NS + "level", 0x55FF55, level), maxLevel,
                List.of(maxLevel ? text("tooltip." + RealmsOfChaos.MOD_ID + ".level.max", 0x55FF55) : text(NS + "xp", 0xAAAAAA, xp, need),
                        text("tooltip." + RealmsOfChaos.MOD_ID + ".bonus", 0xF5B82E, (int) Math.round((WeaponProgress.multiplier(weapon) - 1.0) * 100)))));

        String blocker = blocker();
        int next = stars + 1;
        if ("max".equals(blocker)) {
            view.setItem(SLOT_FORGE, icon(Items.NETHER_STAR, text(NS + "max", 0x55FF55), true, List.of()));
        } else {
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + "effect", 0xAAAAAA));
            if (next == WeaponProgress.MAX_STARS) lore.add(text(NS + "awakened", 0xF5B82E));
            lore.add(text(NS + "needlevel", "level".equals(blocker) ? 0xFF5555 : 0x55FF55, WeaponProgress.starLevel(next)));
            int priceSlot = SLOT_COST;
            for (WeaponProgress.Cost cost : WeaponProgress.starCost(next)) {
                Item material = ModContent.materialItem(god.line(), cost.tier());
                int have = count(viewer, material);
                boolean enough = have >= cost.count() || viewer.hasInfiniteMaterials();
                Component line = text(NS + "cost", enough ? 0x55FF55 : 0xFF5555, cost.count(), new ItemStack(material).getHoverName(), have);
                ItemStack price = icon(material, line, false, List.of(text(NS + "from", 0xAAAAAA,
                        com.thefarawaydev.realmsofchaos.world.Tower.tierName(god.line(), cost.tier() == 1 ? 1 : cost.tier() == 2 ? 6 : cost.tier() == 3 ? 11 : 15))));
                price.setCount(Math.max(1, Math.min(64, cost.count())));
                view.setItem(priceSlot, price);
                priceSlot = SLOT_COST_2;
                lore.add(line);
            }
            view.setItem(SLOT_FORGE, icon(Items.ANVIL, text(NS + "upgrade", blocker == null ? 0x55FF55 : 0xFF5555, next), blocker == null, lore));
        }

        // bottom row: experience towards the next level
        int filled = maxLevel ? 9 : Math.min(9, (int) Math.floor(9.0 * xp / need));
        for (int i = 0; i < 9; i++) {
            view.setItem(slot(2, i), pane(i < filled ? DyeColor.LIME : DyeColor.GRAY, text(NS + "level", 0x55FF55, level),
                    List.of(maxLevel ? text("tooltip." + RealmsOfChaos.MOD_ID + ".level.max", 0x55FF55) : text(NS + "xp", 0xAAAAAA, xp, need))));
        }
        broadcastChanges();
    }
}
