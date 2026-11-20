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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Codex of Chaos, on a double-chest screen.
 *
 * Main page: the three towers side by side, each with a three-segment climb meter beneath it (upper floors on
 * top), plus travel to the hub, leaving the realms and an about card.
 *
 * Tower page: the tower drawn as it stands - floors 11-15 on the top row, 6-10 in the middle, 1-5 at the bottom,
 * each row labelled with its tier. Defeated gods glint, the next trial is highlighted, the rest are locked.
 * Click any floor you have reached to travel there.
 */
public class CodexMenu extends ButtonMenu {
    public static final int SLOT_ABOUT = slot(0, 4);
    public static final int SLOT_ZEUS = slot(1, 2), SLOT_POSEIDON = slot(1, 4), SLOT_HADES = slot(1, 6);
    public static final int SLOT_HUB = slot(5, 2), SLOT_FORGE = slot(5, 4), SLOT_LEAVE = slot(5, 6), SLOT_STORY = slot(5, 8), SLOT_PARTY = slot(5, 0);
    public static final int SLOT_BACK = slot(5, 0), SLOT_TRAVEL = slot(5, 4), SLOT_SUMMARY = slot(0, 4);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".";

    private Line page;     // null = main page

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new CodexMenu(id, inventory, (ServerPlayer) p),
                Component.translatable(NS + "codex")));
    }

    private CodexMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 6);
        render();
    }

    public Line page() {
        return page;
    }

    /** Where a floor sits on a tower page: row 1 = floors 11-15, row 2 = 6-10, row 3 = 1-5, columns 2-6. */
    public static int slotOfFloor(int floor) {
        int tierRow = (floor - 1) / 5;                  // 0 = bottom group
        return slot(3 - tierRow, 2 + (floor - 1) % 5);
    }

    private static int column(Line line) {
        return 2 + line.ordinal() * 2;
    }

    @Override
    protected void press(int slot) {
        if (page == null) {
            if (slot == SLOT_HUB) { close(); Teleports.toHub(viewer); return; }
            if (slot == SLOT_LEAVE) { close(); Teleports.leave(viewer); return; }
            if (slot == SLOT_FORGE) { clickSound(); ForgeMenu.open(viewer); return; }
            if (slot == SLOT_STORY) { clickSound(); StoryMenu.open(viewer); return; }
            if (slot == SLOT_PARTY) { clickSound(); PartyMenu.open(viewer); return; }
            // a tower icon or any segment of its meter opens that tower
            int row = slot / 9, col = slot % 9;
            for (Line line : Line.values()) {
                if (col == column(line) && row >= 1 && row <= 4) { show(line); return; }
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
            ProgressData data = ProgressData.get(viewer.level().getServer());
            for (int floor = 1; floor <= Tower.FLOORS; floor++) {
                if (slotOfFloor(floor) == slot && Teleports.canTravel(viewer, page, floor)) {
                    Line line = page;
                    close();
                    Teleports.toFloor(viewer, line, floor);
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

    private static DyeColor dye(Line line) {
        return switch (line) {
            case ZEUS -> DyeColor.YELLOW;
            case POSEIDON -> DyeColor.CYAN;
            case HADES -> DyeColor.PURPLE;
        };
    }

    @Override
    protected void render() {
        ProgressData data = ProgressData.get(viewer.level().getServer());
        if (page == null) {
            frame(DyeColor.PURPLE, DyeColor.BLACK);
            renderMain(data);
        } else {
            frame(dye(page), DyeColor.BLACK);
            renderTower(data, page);
        }
        broadcastChanges();
    }

    // ---- main page ------------------------------------------------------------------------------------------

    private void renderMain(ProgressData data) {
        view.setItem(SLOT_ABOUT, icon(ModContent.CHAOS_CODEX.get(), text(NS + "about", 0xA55AD6), true, List.of(
                text(NS + "about.lore1", 0xF5B82E),
                text(NS + "about.lore2", 0xAAAAAA),
                text(NS + "about.version", 0xAAAAAA, BuildInfo.DISPLAY),
                text(NS + "about.total", 0x55FFFF, data.total(viewer.getUUID())))));
        for (Line line : Line.values()) {
            int col = column(line);
            view.setItem(slot(1, col), towerIcon(data, line));
            // climb meter: three segments, upper floors on top
            int current = Tower.currentFloor(data, viewer.getUUID(), line);
            boolean complete = data.nextRank(viewer.getUUID(), line) == 0;
            for (int seg = 0; seg < 3; seg++) {
                int from = seg * 5 + 1, to = seg * 5 + 5;
                int done = 0;
                for (int f = from; f <= to; f++) if (data.isDefeated(viewer.getUUID(), Tower.god(line, f))) done++;
                DyeColor color = done == 5 ? DyeColor.LIME : (!complete && current >= from && current <= to) ? dye(line) : DyeColor.GRAY;
                List<Component> lore = new ArrayList<>();
                lore.add(text(NS + "tier.floors", 0xAAAAAA, from, to));
                lore.add(text(NS + "tier.progress", done == 5 ? 0x55FF55 : 0x55FFFF, done, 5));
                if (seg == 2) lore.add(text(NS + "tier.summit", 0xAAAAAA, Tower.tierName(line, Tower.FLOORS)));
                view.setItem(slot(4 - seg, col), pane(color, Tower.tierName(line, from).copy().withStyle(plain(line.color())), lore));
            }
        }
        view.setItem(SLOT_HUB, icon(ModContent.CHAOS_COMPASS.get(), text(NS + "hub", 0xFFFFFF), false,
                List.of(text(NS + "click", 0xAAAAAA))));
        view.setItem(SLOT_FORGE, icon(ModContent.BLOCK_ITEMS.get("chaos_forge").get(), text(NS + "forge.open", 0xFFAA00), false,
                List.of(text(NS + "forge.open.lore", 0xAAAAAA))));
        List<Component> story = new ArrayList<>();
        story.add(text(NS + "story.open.lore", 0xAAAAAA));
        story.add(text(NS + "story.count", 0xF5B82E, com.thefarawaydev.realmsofchaos.story.Story.unlockedCount(viewer),
                com.thefarawaydev.realmsofchaos.story.Story.Chapter.values().length));
        story.addAll(StoryMenu.guidance(viewer));
        view.setItem(SLOT_STORY, icon(Items.WRITABLE_BOOK, text(NS + "story.open", 0xA55AD6), true, story));
        boolean inParty = com.thefarawaydev.realmsofchaos.world.Parties.inParty(viewer.getUUID());
        view.setItem(SLOT_PARTY, icon(Items.PLAYER_HEAD, text(NS + "party.open", 0x55FFFF), inParty, List.of(
                text(NS + "party.open.lore", 0xAAAAAA),
                inParty ? text(NS + "party.size", 0x55FF55, com.thefarawaydev.realmsofchaos.world.Parties.members(viewer.getUUID()).size()) : text(NS + "party.solo", 0xAAAAAA))));
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

    // ---- tower page -----------------------------------------------------------------------------------------

    private void renderTower(ProgressData data, Line line) {
        int next = data.nextRank(viewer.getUUID(), line);
        for (int floor = 1; floor <= Tower.FLOORS; floor++) {
            view.setItem(slotOfFloor(floor), godIcon(data, Tower.god(line, floor), next));
        }
        // tier labels down the left edge, the summit marked on the right of the top row
        for (int seg = 0; seg < 3; seg++) {
            int from = seg * 5 + 1;
            view.setItem(slot(3 - seg, 1), pane(dye(line), Tower.tierName(line, from).copy().withStyle(plain(line.color())),
                    List.of(text(NS + "tier.floors", 0xAAAAAA, from, seg == 2 ? 14 : from + 4))));
        }
        view.setItem(slot(1, 7), pane(DyeColor.WHITE, Tower.tierName(line, Tower.FLOORS).copy().withStyle(plain(0xFFFFFF)),
                List.of(text(NS + "tier.floors", 0xAAAAAA, Tower.FLOORS, Tower.FLOORS))));

        view.setItem(SLOT_SUMMARY, icon(ModContent.CHAOS_CODEX.get(), Tower.towerName(line).copy().withStyle(plain(line.color())), true,
                List.of(text(NS + "line.progress", 0x55FFFF, data.count(viewer.getUUID(), line)),
                        next == 0 ? text(NS + "line.complete", 0x55FF55)
                                : text(NS + "line.floor", 0xFFFFFF, Tower.currentFloor(data, viewer.getUUID(), line)))));
        view.setItem(SLOT_BACK, icon(Items.ARROW, text(NS + "back", 0xFFFFFF), false, List.of()));
        view.setItem(SLOT_TRAVEL, icon(Items.ENDER_PEARL, text(NS + "travel", line.color(), Tower.towerName(line)), true,
                List.of(text(NS + "travel.lore", 0xAAAAAA), text(NS + "click", 0xAAAAAA))));
    }

    private ItemStack godIcon(ProgressData data, God god, int next) {
        boolean defeated = data.isDefeated(viewer.getUUID(), god);
        boolean isNext = god.rank() == next;
        int floor = Tower.floorOf(god);
        List<Component> lore = new ArrayList<>();
        lore.add(god.displayTitle().copy().withStyle(plain(0xAAAAAA)));
        lore.add(text(NS + "god.floor", 0xAAAAAA, floor, Tower.tierName(god.line(), floor)));
        lore.add(text(NS + "god.rank", 0xAAAAAA, god.rank()));
        lore.add(text(NS + "god.move", 0xF5B82E, god.moveName()));
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
        return pane(DyeColor.GRAY, god.displayName().copy().withStyle(plain(0x888888)), lore);
    }
}
