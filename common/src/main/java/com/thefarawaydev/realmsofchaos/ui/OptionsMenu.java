package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * Settings: what the mod puts on this player's screen. A row of switches - the cast bar (off until asked for),
 * tips, what the gods learn, the side panel - each with a gem under it that is lit when it is on.
 */
public class OptionsMenu extends ButtonMenu {
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".options";
    public static final int SLOT_CASTS = slot(1, 1), SLOT_TIPS = slot(1, 3), SLOT_INSIGHT = slot(1, 5), SLOT_SIDEBAR = slot(1, 7);
    public static final int SLOT_AGAIN = slot(0, 8), SLOT_BACK = slot(0, 0);

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new OptionsMenu(id, inventory, (ServerPlayer) p),
                Skin.title(Skin.Panel.SETTINGS, Component.translatable(NS))));
    }

    private OptionsMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 3);
        render();
    }

    private static Options.Option optionAt(int slot) {
        return slot == SLOT_CASTS ? Options.Option.CASTS : slot == SLOT_TIPS ? Options.Option.TIPS : slot == SLOT_INSIGHT ? Options.Option.INSIGHT : null;
    }

    @Override
    protected void press(int slot) {
        int over = slot - 9;                                // the gem under a switch works it too
        Options.Option option = optionAt(slot) != null ? optionAt(slot) : optionAt(over);
        if (option != null) {
            clickSound();
            Options.toggle(viewer, option);
        } else if (slot == SLOT_SIDEBAR || over == SLOT_SIDEBAR) {
            clickSound();
            Sidebar.toggle(viewer);
        } else if (slot == SLOT_AGAIN) {
            clickSound();
            Tutorial.reset(viewer);
            Cards.tip(viewer, Words.of(NS + ".again.done"));
        } else if (slot == SLOT_BACK) {
            clickSound();
            CodexMenu.open(viewer);
            return;
        } else {
            return;
        }
        render();
    }

    private void toggle(int slot, net.minecraft.world.level.ItemLike item, String id, boolean on) {
        Component state = text(NS + (on ? ".on" : ".off"), on ? 0x55FF55 : 0xFF5555);
        view.setItem(slot, icon(item, text(NS + "." + id, 0xF5E6C8), on, List.of(text(NS + "." + id + ".lore", 0xAAAAAA), state, text(NS + ".click", 0x55FFFF))));
        view.setItem(slot + 9, pane(on ? DyeColor.LIME : DyeColor.GRAY, state, List.of()));
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        toggle(SLOT_CASTS, Items.CLOCK, "casts", Options.on(viewer, Options.Option.CASTS));
        toggle(SLOT_TIPS, Items.TORCH, "tips", Options.on(viewer, Options.Option.TIPS));
        toggle(SLOT_INSIGHT, Items.ENDER_EYE, "insight", Options.on(viewer, Options.Option.INSIGHT));
        toggle(SLOT_SIDEBAR, Items.OAK_SIGN, "sidebar", Sidebar.enabled(viewer));
        view.setItem(SLOT_AGAIN, icon(Items.WRITABLE_BOOK, text(NS + ".again", 0x55FFFF), false, List.of(text(NS + ".again.lore", 0xAAAAAA))));
        view.setItem(SLOT_BACK, icon(Items.ARROW, text("menu." + RealmsOfChaos.MOD_ID + ".codex.back", 0xFFFFFF), false, List.of()));
        broadcastChanges();
    }
}
