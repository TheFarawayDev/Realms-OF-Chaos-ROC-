package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.Insight;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * What the gods know of this player: one column for each habit they watch for, with three gems that light as they
 * come to know it. Each says what earns it and what the gods do about it - and that a habit dropped is forgotten.
 */
public class InsightMenu extends ButtonMenu {
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".insight";
    public static final int SLOT_ABOUT = slot(0, 4), SLOT_BACK = slot(0, 0);

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new InsightMenu(id, inventory, (ServerPlayer) p),
                Skin.title(player, Skin.Panel.INSIGHT, Component.translatable(NS))));
    }

    private InsightMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 6);
        render();
    }

    /** The column a habit's icon and gems stand in. */
    public static int column(Insight.Habit habit) {
        return 0 + habit.ordinal() * 2;
    }

    public static int slotOf(Insight.Habit habit) {
        return slot(1, column(habit));
    }

    /** The gem for a level of a habit: level 1 at the bottom, 3 at the top. */
    public static int gemSlot(Insight.Habit habit, int level) {
        return slot(5 - level, column(habit));
    }

    private static ItemLike icon(Insight.Habit habit) {
        return switch (habit) {
            case ARCHER -> Items.BOW;
            case DUELLIST -> Items.IRON_SWORD;
            case GUARD -> Items.SHIELD;
            case MENDER -> Items.GOLDEN_APPLE;
            case RUNNER -> Items.FEATHER;
        };
    }

    @Override
    protected void press(int slot) {
        if (slot == SLOT_BACK) {
            clickSound();
            CodexMenu.open(viewer);
        }
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        view.setItem(SLOT_ABOUT, icon(Items.ENDER_EYE, text(NS + ".about", 0xFFAA3C), true, List.of(
                text(NS + ".about.1", 0xAAAAAA), text(NS + ".about.2", 0xAAAAAA), text(NS + ".about.3", 0x55FFFF))));
        view.setItem(SLOT_BACK, icon(Items.ARROW, text("menu." + RealmsOfChaos.MOD_ID + ".codex.back", 0xFFFFFF), false, List.of()));
        for (Insight.Habit habit : Insight.Habit.values()) {
            int known = Insight.level(viewer, habit);
            List<Component> lore = new ArrayList<>();
            lore.add(text(known == 0 ? NS + ".unknown" : NS + ".known", known == 0 ? 0x888888 : 0xFFAA3C, Insight.numeral(known)));
            lore.add(habit.cause().copy().withStyle(plain(0xAAAAAA)));
            lore.add(habit.answer().copy().withStyle(plain(known == 0 ? 0x888888 : 0xFF5555)));
            view.setItem(slotOf(habit), icon(icon(habit), habit.displayName().copy().withStyle(plain(0xF5E6C8)), known > 0, lore));
            for (int level = 1; level <= Insight.MAX_LEVEL; level++) {
                boolean lit = known >= level;
                view.setItem(gemSlot(habit, level), pane(lit ? (level == 3 ? DyeColor.RED : DyeColor.ORANGE) : DyeColor.GRAY,
                        text(NS + ".level", lit ? 0xFFAA3C : 0x888888, Insight.numeral(level)), List.of()));
            }
        }
        broadcastChanges();
    }
}
