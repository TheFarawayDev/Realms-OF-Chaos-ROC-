package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.story.Story;
import com.thefarawaydev.realmsofchaos.world.Access;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * The Oracle's Chronicle: every chapter of the story, the ones a player has earned as books to take, the rest as
 * blanks that say what will write them. The top row of chapters is the road in; then a row for each tower; the
 * epilogue at the bottom. "What now?" in the corner always names the next thing to do.
 */
public class StoryMenu extends ButtonMenu {
    public static final int SLOT_INFO = slot(0, 4), SLOT_BACK = slot(5, 0), SLOT_GUIDE = slot(5, 8);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".story.";

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new StoryMenu(id, inventory, (ServerPlayer) p),
                Skin.title(player, Skin.Panel.STORY, Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".story"))));
    }

    private StoryMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 6);
        render();
    }

    public static int slotOf(Story.Chapter chapter) {
        if (chapter == Story.Chapter.EPILOGUE) return slot(5, 4);
        if (chapter.line == null) return slot(1, 1 + chapter.ordinal());
        int stage = chapter.needed / 5;                     // foot, mid, high, fall
        return slot(2 + chapter.line.ordinal(), 3 + stage);
    }

    @Override
    protected void press(int slot) {
        if (slot == SLOT_BACK) {
            clickSound();
            CodexMenu.open(viewer);
            return;
        }
        for (Story.Chapter chapter : Story.Chapter.values()) {
            if (slotOf(chapter) != slot) continue;
            if (chapter.unlocked(viewer) && Story.give(viewer, chapter)) {
                Hud.actionBar(viewer, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".story.given", chapter.title()), 0xF5B82E));
            }
            render();
            return;
        }
    }

    /** What the player should do next, a line per thing. */
    public static List<Component> guidance(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        if (!Access.attuned(player)) {
            out.add(text(NS + "guide.gate", 0xFFFF55));
            out.add(text(NS + "guide.keystones", 0xAAAAAA));
            return out;
        }
        ProgressData data = ProgressData.get(player.level().getServer());
        boolean all = true;
        for (Line line : Line.values()) {
            int next = data.nextRank(player.getUUID(), line);
            if (next == 0) {
                out.add(text(NS + "guide.done", 0x55FF55, Tower.towerName(line)));
            } else {
                all = false;
                God god = Gods.get(line, next);
                out.add(text(NS + "guide.next", line.color(), Tower.towerName(line), god.displayName(), Tower.floorOf(god)));
            }
        }
        if (all) out.add(text(NS + "guide.finished", 0xF5B82E));
        return out;
    }

    @Override
    protected void render() {
        frame(DyeColor.PURPLE, DyeColor.BLACK);
        view.setItem(SLOT_INFO, icon(ModContent.ORACLE.get(), text("menu." + RealmsOfChaos.MOD_ID + ".story", 0xA55AD6), true, List.of(
                text(NS + "count", 0xF5B82E, Story.unlockedCount(viewer), Story.Chapter.values().length))));
        for (Line line : Line.values()) {
            view.setItem(slot(2 + line.ordinal(), 1), icon(ModContent.weaponItem(Gods.get(line, 1)), Tower.towerName(line).copy().withStyle(plain(line.color())), false, List.of()));
        }
        for (Story.Chapter chapter : Story.Chapter.values()) {
            if (chapter.unlocked(viewer)) {
                boolean held = Story.holds(viewer, chapter);
                view.setItem(slotOf(chapter), icon(held ? Items.BOOK : Items.WRITTEN_BOOK,
                        chapter.title().copy().withStyle(plain(chapter.line == null ? 0xF5B82E : chapter.line.color())), !held,
                        List.of(chapter.blurb().copy().withStyle(plain(0xAAAAAA)), text(NS + "read", 0x55FF55))));
            } else {
                Component how = chapter == Story.Chapter.EPILOGUE ? text(NS + "unlock.all", 0xAAAAAA)
                        : chapter.needed == 0 ? text(NS + "unlock.attuned", 0xAAAAAA)
                        : text(NS + "unlock.count", 0xAAAAAA, chapter.needed, Tower.towerName(chapter.line));
                view.setItem(slotOf(chapter), pane(DyeColor.GRAY, text(NS + "locked", 0x888888), List.of(how)));
            }
        }
        view.setItem(SLOT_GUIDE, icon(ModContent.CHAOS_COMPASS.get(), text(NS + "guide", 0x55FFFF), true, guidance(viewer)));
        view.setItem(SLOT_BACK, icon(ModContent.CHAOS_CODEX.get(), text("menu." + RealmsOfChaos.MOD_ID + ".passage.codex", 0xFFFFFF), false, List.of()));
        broadcastChanges();
    }
}
