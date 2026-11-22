package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * The bestiary: every kind of sentinel a player has slain, a tower to a page. A kind not yet met is a blank; one
 * that has fallen shows what it is, what it does to help the others or make trouble, and how many have fallen.
 */
public class BestiaryMenu extends ButtonMenu {
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".bestiary";
    public static final int SLOT_BACK = slot(0, 0), SLOT_TOTAL = slot(0, 4);
    public static final int[] TABS = {slot(0, 6), slot(0, 7), slot(0, 8)};

    private Line page;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BestiaryMenu(id, inventory, (ServerPlayer) p),
                Skin.title(player, Skin.Panel.BESTIARY, Component.translatable(NS))));
    }

    private BestiaryMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 6);
        this.page = Line.ZEUS;
        render();
    }

    private static String key(ServerPlayer player, Sentinel.Kind kind) {
        return "bestiary/" + player.getUUID() + "/" + kind.name().toLowerCase(Locale.ROOT);
    }

    /** A sentinel of this kind has fallen to this player. */
    public static void record(ServerPlayer player, Sentinel.Kind kind) {
        MinecraftServer server = player.level().getServer();
        if (server != null) ProgressData.get(server).addCounter(key(player, kind), 1);
    }

    public static int slain(ServerPlayer player, Sentinel.Kind kind) {
        MinecraftServer server = player.level().getServer();
        return server == null ? 0 : ProgressData.get(server).counter(key(player, kind));
    }

    public static int known(ServerPlayer player) {
        int n = 0;
        for (Sentinel.Kind kind : Sentinel.Kind.values()) if (slain(player, kind) > 0) n++;
        return n;
    }

    /** The kinds of one tower, in the order they are laid out. */
    public static List<Sentinel.Kind> kinds(Line line) {
        List<Sentinel.Kind> out = new ArrayList<>();
        for (Sentinel.Kind kind : Sentinel.Kind.values()) if (kind.line == line) out.add(kind);
        return out;
    }

    /** Rows 2 to 4, seven to a row. */
    public static int slotOf(int index) {
        return slot(2 + index / 7, 1 + index % 7);
    }

    private static ItemLike icon(Sentinel.Kind kind) {
        return switch (kind.role) {
            case SOLDIER -> Items.IRON_SWORD;
            case CASTER -> Items.BLAZE_ROD;
            case SKIRMISHER -> Items.RABBIT_FOOT;
            case BRUTE -> Items.MACE;
            case MYSTIC -> Items.TOTEM_OF_UNDYING;
            case KEEPER -> Items.NETHERITE_HELMET;
        };
    }

    public Line page() {
        return page;
    }

    @Override
    protected void press(int slot) {
        if (slot == SLOT_BACK) {
            clickSound();
            CodexMenu.open(viewer);
            return;
        }
        for (Line line : Line.values()) {
            if (slot == TABS[line.ordinal()] && page != line) {
                clickSound();
                page = line;
                render();
                return;
            }
        }
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        view.setItem(SLOT_BACK, icon(Items.ARROW, text("menu." + RealmsOfChaos.MOD_ID + ".back", 0xFFFFFF), false, List.of()));
        view.setItem(SLOT_TOTAL, icon(Items.WRITABLE_BOOK, text(NS + ".title", 0xDC5050), true, List.of(
                text(NS + ".known", 0xAAAAAA, known(viewer), Sentinel.Kind.values().length), text(NS + ".lore", 0xAAAAAA))));
        for (Line line : Line.values()) {
            DyeColor colour = line == Line.ZEUS ? DyeColor.YELLOW : line == Line.POSEIDON ? DyeColor.CYAN : DyeColor.PURPLE;
            view.setItem(TABS[line.ordinal()], pane(page == line ? colour : DyeColor.GRAY, com.thefarawaydev.realmsofchaos.world.Tower.towerName(line).copy().withStyle(plain(line.color())), List.of()));
        }
        List<Sentinel.Kind> kinds = kinds(page);
        for (int i = 0; i < kinds.size() && i < 21; i++) {
            Sentinel.Kind kind = kinds.get(i);
            int slain = slain(viewer, kind);
            if (slain == 0) {
                view.setItem(slotOf(i), pane(DyeColor.GRAY, text(NS + ".unknown", 0x888888), List.of(text(NS + ".unknown.lore", 0x666666))));
                continue;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + ".role." + kind.role.name().toLowerCase(Locale.ROOT), 0xAAAAAA));
            if (kind.trait != Sentinel.Trait.NONE) lore.add(text(NS + ".trait." + kind.trait.name().toLowerCase(Locale.ROOT), 0xFFAA3C));
            lore.add(text(NS + ".slain", 0x55FFFF, slain));
            view.setItem(slotOf(i), icon(icon(kind), kind.displayName().copy().withStyle(plain(page.color())), slain >= 10, lore));
        }
        broadcastChanges();
    }
}
