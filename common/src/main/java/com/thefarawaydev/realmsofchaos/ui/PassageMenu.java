package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.Passages;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * The map of the passage a player is in, opened with the Codex of Chaos. It is drawn as the maze is: one slot per
 * room, north at the top. Rooms appear as the player walks into them; a room not yet entered shows only as a
 * door leading on, if a room already explored opens onto it.
 */
public class PassageMenu extends ButtonMenu {
    // the buttons stand in the side columns, which the maze never uses: the biggest mazes need all six rows
    public static final int SLOT_CODEX = slot(0, 0), SLOT_INFO = slot(2, 0), SLOT_CLOSE = slot(0, 8);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".passage.";

    private final Passages.Spot spot;

    public static void open(ServerPlayer player, Passages.Spot spot) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PassageMenu(id, inventory, (ServerPlayer) p, spot),
                Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".passage", spot.index() + 1)));
    }

    private PassageMenu(int id, Inventory inventory, ServerPlayer viewer, Passages.Spot spot) {
        super(id, inventory, viewer, 6);
        this.spot = spot;
        render();
    }

    /** The slot a room is drawn in: the maze sits in the middle columns, north at the top. */
    public static int slotOf(Passages.Plan plan, Passages.Cell cell) {
        return slot(cell.z(), (plan.size >= 6 ? 1 : 2) + cell.x());
    }

    @Override
    protected void press(int slot) {
        if (slot == SLOT_CODEX) {
            clickSound();
            viewer.closeContainer();
            Screens.show(viewer, Screens.home(viewer));
        } else if (slot == SLOT_CLOSE) {
            clickSound();
            viewer.closeContainer();
        }
    }

    private static List<Component> doors(Passages.Plan plan, Passages.Cell c) {
        List<String> names = new ArrayList<>();
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] dirs = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            Passages.Cell n = new Passages.Cell(c.x() + sides[i][0], c.z() + sides[i][1]);
            if (n.x() >= 0 && n.z() >= 0 && n.x() < plan.size && n.z() < plan.size && plan.connected(c, n)) names.add(dirs[i]);
        }
        List<Component> out = new ArrayList<>();
        for (String dir : names) {
            out.add(text(NS + "door", 0xAAAAAA, Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".passage.dir." + dir)));
        }
        return out;
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        if (!(viewer.level() instanceof ServerLevel level)) return;
        Passages.Plan plan = Passages.plan(level, spot);
        Set<Passages.Cell> seen = Passages.visited(viewer, plan);
        Passages.Cell here = plan.cellAt(viewer.blockPosition());
        for (Passages.Cell c : plan.cells()) {
            if (!seen.contains(c)) {
                // not entered yet: shown only if a door from an explored room leads to it
                boolean hinted = false;
                for (Passages.Cell n : plan.neighbours(c)) hinted |= seen.contains(n) && plan.connected(c, n);
                if (hinted) view.setItem(slotOf(plan, c), pane(DyeColor.LIGHT_GRAY, text(NS + "unexplored", 0xAAAAAA), List.of(text(NS + "unexplored.lore", 0x777777))));
                continue;
            }
            Passages.Kind kind = plan.kind(c);
            boolean sealed = kind == Passages.Kind.SEAL && level.getBlockState(plan.sealPos(c)).is(ModContent.PASSAGE_SEAL.get());
            ItemLike icon = switch (kind) {
                case START -> Items.OAK_DOOR;
                case EXIT -> ModContent.GATE_ASCEND.get();
                case SEAL -> sealed ? ModContent.PASSAGE_SEAL.get() : Items.GLASS;
                case TREASURE -> Items.CHEST;
                case SHRINE -> ModContent.PASSAGE_SHRINE.get();
                case TRAP -> Items.MAGMA_BLOCK;
                case HALL -> Items.STONE_BRICK_STAIRS;
                case ROOM -> Items.STONE_BRICKS;
                case GARDEN -> Items.FLOWERING_AZALEA;
                case LIBRARY -> Items.BOOKSHELF;
                case POOL -> Items.LILY_PAD;
                case ARENA -> Items.IRON_SWORD;
            };
            String name = kind == Passages.Kind.SEAL ? (sealed ? "seal" : "seal.broken") : kind.name().toLowerCase();
            List<Component> lore = new ArrayList<>();
            if (c.equals(here)) lore.add(text(NS + "here", 0xFFFF55));
            lore.addAll(doors(plan, c));
            int color = c.equals(here) ? 0xFFFF55 : kind == Passages.Kind.SEAL && sealed ? 0x55FFFF : 0xFFFFFF;
            ItemStack stack = icon(icon, text(NS + "kind." + name, color), c.equals(here), lore);
            view.setItem(slotOf(plan, c), stack);
        }
        int total = plan.seals.size(), broken = total - Passages.sealsLeft(level, plan);
        view.setItem(SLOT_INFO, icon(ModContent.CHAOS_COMPASS.get(), text(NS + "info", spot.line().color(), spot.index() + 1), false, List.of(
                text(NS + "info.level", 0xFFFFFF, plan.stage + 1, plan.stages),
                text(NS + "info.seals", broken == total ? 0x55FF55 : 0x55FFFF, broken, total),
                text(NS + "info.rooms", 0xAAAAAA, seen.size(), plan.size * plan.size),
                text(NS + "info.compass", 0xAAAAAA))));
        view.setItem(SLOT_CODEX, icon(ModContent.CHAOS_CODEX.get(), text(NS + "codex", 0xFFFFFF), false, List.of(text(NS + "codex.lore", 0xAAAAAA))));
        view.setItem(SLOT_CLOSE, icon(Items.ARROW, text("menu." + RealmsOfChaos.MOD_ID + ".forge.close", 0xFFFFFF), false, List.of()));
        broadcastChanges();
    }
}
