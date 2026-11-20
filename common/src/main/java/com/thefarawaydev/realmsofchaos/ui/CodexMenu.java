package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.BuildInfo;
import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Codex of Chaos. Main page: the three towers, travel to the hub, leave the realms and an about card.
 * Tower page: all 15 gods of that line, floor by floor, with defeated / next / locked status; click a floor you
 * have reached to travel there.
 */
public class CodexMenu extends ButtonMenu {
    public static final int SLOT_ABOUT = 4, SLOT_ZEUS = 10, SLOT_POSEIDON = 13, SLOT_HADES = 16, SLOT_HUB = 21, SLOT_LEAVE = 23;
    public static final int SLOT_BACK = 18, SLOT_TRAVEL = 22, SLOT_SUMMARY = 26;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".";

    private Line page;     // null = main page

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new CodexMenu(id, inventory, (ServerPlayer) p, new SimpleContainer(SIZE)),
                Component.translatable(NS + "codex")));
    }

    private CodexMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer view) {
        super(id, inventory, viewer, view);
        render();
    }

    public Line page() {
        return page;
    }

    /** The slot showing the god of {@code rank} on a tower page: top row ranks 15..7, second row ranks 6..1. */
    public static int slotOfRank(int rank) {
        return rank >= 7 ? Line.RANKS - rank : 9 + (6 - rank) + 1;
    }

    @Override
    protected void press(int slot) {
        if (page == null) {
            switch (slot) {
                case SLOT_ZEUS -> show(Line.ZEUS);
                case SLOT_POSEIDON -> show(Line.POSEIDON);
                case SLOT_HADES -> show(Line.HADES);
                case SLOT_HUB -> { close(); Teleports.toHub(viewer); }
                case SLOT_LEAVE -> { close(); Teleports.leave(viewer); }
                default -> { }
            }
            return;
        }
        if (slot == SLOT_BACK) {
            show(null);
        } else if (slot == SLOT_TRAVEL) {
            Line line = page;
            close();
            Teleports.enterTower(viewer, line);
        } else {
            // a god icon: travel to that floor if the player has reached it
            ProgressData data = ProgressData.get(viewer.level().getServer());
            for (int rank = 1; rank <= Line.RANKS; rank++) {
                if (slotOfRank(rank) == slot) {
                    int floor = Tower.FLOORS + 1 - rank;
                    if (floor <= Tower.currentFloor(data, viewer.getUUID(), page)) {
                        Line line = page;
                        close();
                        Teleports.toFloor(viewer, line, floor);
                    }
                    return;
                }
            }
        }
    }

    private void show(Line line) {
        this.page = line;
        clickSound();
        render();
    }

    private void close() {
        clickSound();
        viewer.closeContainer();
    }

    @Override
    protected void render() {
        ProgressData data = ProgressData.get(viewer.level().getServer());
        fill(page == null ? DyeColor.PURPLE : switch (page) {
            case ZEUS -> DyeColor.YELLOW;
            case POSEIDON -> DyeColor.CYAN;
            case HADES -> DyeColor.PURPLE;
        });
        if (page == null) {
            renderMain(data);
        } else {
            renderTower(data, page);
        }
        broadcastChanges();
    }

    private void renderMain(ProgressData data) {
        view.setItem(SLOT_ABOUT, icon(ModContent.CHAOS_CODEX.get(), text(NS + "about", 0xA55AD6), false, List.of(
                text(NS + "about.lore1", 0xF5B82E),
                text(NS + "about.lore2", 0xAAAAAA),
                text(NS + "about.version", 0xAAAAAA, BuildInfo.DISPLAY),
                text(NS + "about.total", 0x55FFFF, data.total(viewer.getUUID())))));
        view.setItem(SLOT_ZEUS, towerIcon(data, Line.ZEUS));
        view.setItem(SLOT_POSEIDON, towerIcon(data, Line.POSEIDON));
        view.setItem(SLOT_HADES, towerIcon(data, Line.HADES));
        view.setItem(SLOT_HUB, icon(ModContent.CHAOS_COMPASS.get(), text(NS + "hub", 0xFFFFFF), false,
                List.of(text(NS + "click", 0xAAAAAA))));
        view.setItem(SLOT_LEAVE, icon(Items.OAK_DOOR, text(NS + "leave", 0x55FF55), false,
                List.of(text(NS + "leave.lore", 0xAAAAAA), text(NS + "click", 0xAAAAAA))));
    }

    private ItemStack towerIcon(ProgressData data, Line line) {
        int count = data.count(viewer.getUUID(), line);
        int next = data.nextRank(viewer.getUUID(), line);
        int floor = Tower.currentFloor(data, viewer.getUUID(), line);
        List<Component> lore = new ArrayList<>();
        lore.add(text(NS + "line.progress", 0x55FFFF, count));
        if (next == 0) {
            lore.add(text(NS + "line.complete", 0x55FF55));
        } else {
            lore.add(text(NS + "line.floor", 0xFFFFFF, floor));
            lore.add(Tower.tierName(line, floor).copy().withStyle(plain(0xAAAAAA)));
            lore.add(text(NS + "line.next", 0xFFFF55, Gods.get(line, next).displayName()));
        }
        lore.add(text(NS + "click_view", 0xAAAAAA));
        return icon(ModContent.weaponItem(Gods.get(line, 1)), Tower.towerName(line).copy().withStyle(plain(line.color())), next == 0, lore);
    }

    private void renderTower(ProgressData data, Line line) {
        int next = data.nextRank(viewer.getUUID(), line);
        for (int rank = Line.RANKS; rank >= 1; rank--) {
            view.setItem(slotOfRank(rank), godIcon(data, Gods.get(line, rank), next));
        }
        view.setItem(SLOT_BACK, icon(Items.ARROW, text(NS + "back", 0xFFFFFF), false, List.of()));
        view.setItem(SLOT_TRAVEL, icon(Items.ENDER_PEARL, text(NS + "travel", line.color(), Tower.towerName(line)), false,
                List.of(text(NS + "travel.lore", 0xAAAAAA), text(NS + "click", 0xAAAAAA))));
        view.setItem(SLOT_SUMMARY, icon(ModContent.CHAOS_CODEX.get(), Tower.towerName(line).copy().withStyle(plain(line.color())), false,
                List.of(text(NS + "line.progress", 0x55FFFF, data.count(viewer.getUUID(), line)))));
    }

    private ItemStack godIcon(ProgressData data, God god, int next) {
        boolean defeated = data.isDefeated(viewer.getUUID(), god);
        boolean isNext = god.rank() == next;
        int floor = Tower.floorOf(god);
        List<Component> lore = new ArrayList<>();
        lore.add(god.displayTitle().copy().withStyle(plain(0xAAAAAA)));
        lore.add(text(NS + "god.floor", 0xAAAAAA, floor, Tower.tierName(god.line(), floor)));
        lore.add(text(NS + "god.rank", 0xAAAAAA, god.rank()));
        if (defeated) {
            lore.add(text(NS + "god.defeated", 0x55FF55));
            lore.add(text(NS + "god.relic", 0x55FFFF, Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + god.id() + "_weapon")));
            lore.add(text(NS + "god.travel", 0xAAAAAA));
            return icon(ModContent.weaponItem(god), god.displayName().copy().withStyle(plain(0x55FF55)), true, lore);
        }
        if (isNext) {
            lore.add(text(NS + "god.next", 0xFFFF55));
            lore.add(text(NS + "god.travel", 0xAAAAAA));
            return icon(ModContent.weaponItem(god), god.displayName().copy().withStyle(plain(0xFFFF55)), false, lore);
        }
        God blocker = Gods.get(god.line(), god.rank() + 1);
        lore.add(text(NS + "god.locked", 0xFF5555, blocker.displayName()));
        return icon(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), god.displayName().copy().withStyle(plain(0x888888)), false, lore);
    }
}
