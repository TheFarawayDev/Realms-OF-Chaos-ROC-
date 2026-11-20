package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;

/**
 * The Codex of Chaos: an in-game menu built on the vanilla 9x3 chest screen. The server owns every slot, so it works
 * the same on Fabric and Forge with no custom packets. Icons are buttons; nothing can be taken out.
 *
 * Main page: the three god lines, travel to the hub and an about card. Line page: all 15 gods of that line with
 * defeated / next / locked status, plus travel to that line's zone.
 */
public class CodexMenu extends ChestMenu {
    public static final int SIZE = 27;
    public static final int SLOT_ABOUT = 4, SLOT_ZEUS = 10, SLOT_POSEIDON = 13, SLOT_HADES = 16, SLOT_HUB = 22;
    public static final int SLOT_BACK = 18, SLOT_TRAVEL = 22, SLOT_SUMMARY = 26;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".";

    private final SimpleContainer view;
    private final ServerPlayer viewer;
    private Line page;     // null = main page

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new CodexMenu(id, inventory, (ServerPlayer) p, new SimpleContainer(SIZE)),
                Component.translatable(NS + "codex")));
    }

    private CodexMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer view) {
        super(MenuType.GENERIC_9x3, id, inventory, view, 3);
        this.view = view;
        this.viewer = viewer;
        render();
    }

    public Line page() {
        return page;
    }

    // ---- input: every click is a button press, never an inventory move --------------------------------

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < SIZE && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
            press(slot);
        }
        if (viewer.containerMenu == this) {
            sendAllDataToRemote();      // undo whatever the client predicted
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private void press(int slot) {
        if (page == null) {
            switch (slot) {
                case SLOT_ZEUS -> show(Line.ZEUS);
                case SLOT_POSEIDON -> show(Line.POSEIDON);
                case SLOT_HADES -> show(Line.HADES);
                case SLOT_HUB -> travel(Realm.HUB);
                default -> { }
            }
        } else {
            switch (slot) {
                case SLOT_BACK -> show(null);
                case SLOT_TRAVEL -> travel(Realm.ofLine(page));
                default -> { }
            }
        }
    }

    private void show(Line line) {
        this.page = line;
        click();
        render();
    }

    private void travel(Realm realm) {
        click();
        viewer.closeContainer();
        Teleports.travel(viewer, realm);
    }

    private void click() {
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    // ---- rendering --------------------------------------------------------------------------------------

    private void render() {
        ProgressData data = ProgressData.get(viewer.level().getServer());
        ItemStack filler = filler(page == null ? DyeColor.PURPLE : switch (page) {
            case ZEUS -> DyeColor.YELLOW;
            case POSEIDON -> DyeColor.CYAN;
            case HADES -> DyeColor.PURPLE;
        });
        for (int i = 0; i < SIZE; i++) view.setItem(i, filler.copy());
        if (page == null) {
            renderMain(data);
        } else {
            renderLine(data, page);
        }
        broadcastChanges();
    }

    private void renderMain(ProgressData data) {
        view.setItem(SLOT_ABOUT, icon(ModContent.CHAOS_CODEX.get(), text(NS + "about", 0xA55AD6), false, List.of(
                text(NS + "about.lore1", 0xF5B82E),
                text(NS + "about.lore2", 0xAAAAAA),
                Component.translatable(NS + "about.total", data.total(viewer.getUUID())).withStyle(plain(0x55FFFF)))));
        view.setItem(SLOT_ZEUS, lineIcon(data, Line.ZEUS));
        view.setItem(SLOT_POSEIDON, lineIcon(data, Line.POSEIDON));
        view.setItem(SLOT_HADES, lineIcon(data, Line.HADES));
        view.setItem(SLOT_HUB, icon(ModContent.CHAOS_COMPASS.get(), text(NS + "hub", 0xFFFFFF), false,
                List.of(text(NS + "hub.lore", 0xAAAAAA))));
    }

    private ItemStack lineIcon(ProgressData data, Line line) {
        int count = data.count(viewer.getUUID(), line);
        int next = data.nextRank(viewer.getUUID(), line);
        List<Component> lore = new ArrayList<>();
        lore.add(line.zoneName().copy().withStyle(plain(0xAAAAAA)));
        lore.add(Component.translatable(NS + "line.progress", count).withStyle(plain(0x55FFFF)));
        lore.add(next == 0
                ? text(NS + "line.complete", 0x55FF55)
                : Component.translatable(NS + "line.next", Gods.get(line, next).displayName()).withStyle(plain(0xFFFF55)));
        lore.add(text(NS + "click_view", 0xAAAAAA));
        return icon(ModContent.weaponItem(Gods.get(line, 1)),
                Component.translatable(NS + "line", line.displayName()).withStyle(plain(line.color())), next == 0, lore);
    }

    private void renderLine(ProgressData data, Line line) {
        int next = data.nextRank(viewer.getUUID(), line);
        // top row: ranks 15..7, second row: ranks 6..1 (the final god last)
        for (int rank = Line.RANKS; rank >= 1; rank--) {
            int slot = rank >= 7 ? Line.RANKS - rank : 9 + (6 - rank) + 1;
            view.setItem(slot, godIcon(data, Gods.get(line, rank), next));
        }
        view.setItem(SLOT_BACK, icon(Items.ARROW, text(NS + "back", 0xFFFFFF), false, List.of()));
        view.setItem(SLOT_TRAVEL, icon(Items.ENDER_PEARL,
                Component.translatable(NS + "travel", line.zoneName()).withStyle(plain(line.color())), false,
                List.of(text(NS + "hub.lore", 0xAAAAAA))));
        view.setItem(SLOT_SUMMARY, icon(ModContent.CHAOS_CODEX.get(),
                Component.translatable(NS + "line", line.displayName()).withStyle(plain(line.color())), false,
                List.of(Component.translatable(NS + "line.progress", data.count(viewer.getUUID(), line)).withStyle(plain(0x55FFFF)))));
    }

    private ItemStack godIcon(ProgressData data, God god, int next) {
        boolean defeated = data.isDefeated(viewer.getUUID(), god);
        boolean isNext = god.rank() == next;
        List<Component> lore = new ArrayList<>();
        lore.add(god.displayTitle().copy().withStyle(plain(0xAAAAAA)));
        lore.add(Component.translatable(NS + "god.rank", god.rank()).withStyle(plain(0xAAAAAA)));
        if (defeated) {
            lore.add(text(NS + "god.defeated", 0x55FF55));
            lore.add(Component.translatable(NS + "god.relic", Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + god.id() + "_weapon"))
                    .withStyle(plain(0x55FFFF)));
            return icon(ModContent.weaponItem(god), god.displayName().copy().withStyle(plain(0x55FF55)), true, lore);
        }
        if (isNext) {
            lore.add(text(NS + "god.next", 0xFFFF55));
            return icon(ModContent.weaponItem(god), god.displayName().copy().withStyle(plain(0xFFFF55)), false, lore);
        }
        God blocker = Gods.get(god.line(), god.rank() + 1);
        lore.add(Component.translatable(NS + "god.locked", blocker.displayName()).withStyle(plain(0xFF5555)));
        return icon(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), god.displayName().copy().withStyle(plain(0x888888)), false, lore);
    }

    // ---- icon helpers -----------------------------------------------------------------------------------

    private static Style plain(int color) {
        return Style.EMPTY.withItalic(false).withColor(color);
    }

    private static MutableComponent text(String key, int color) {
        return Component.translatable(key).withStyle(plain(color));
    }

    private static ItemStack icon(ItemLike item, Component name, boolean glint, List<Component> lore) {
        ItemStack stack = new ItemStack(item == null ? Items.BARRIER : item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        // keep the tooltip to our own text: hide attack stats and the item's own description lines
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
        return stack;
    }

    private static ItemStack filler(DyeColor color) {
        ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.pick(color));
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        return stack;
    }
}
