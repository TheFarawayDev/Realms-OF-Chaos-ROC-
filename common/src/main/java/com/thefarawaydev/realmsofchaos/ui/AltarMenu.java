package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.AltarBlock;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Teleports;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Trial Altar screen. The middle row reads left to right: the action button, whose meaning follows the state
 * (begin the trial, ascend once the god is defeated, or why nothing can happen right now), then the god, its
 * signature move and what it drops, then a rematch button once the god has fallen.
 */
public class AltarMenu extends ButtonMenu {
    public static final int SLOT_INFO = slot(0, 4), SLOT_CLOSE = slot(0, 8), SLOT_ACTION = slot(1, 1), SLOT_GOD = slot(1, 3),
            SLOT_MOVE = slot(1, 4), SLOT_DROPS = slot(1, 5), SLOT_REMATCH = slot(1, 7);
    /** The bottom row is a strip of nine floors centred on this one. */
    public static final int STRIP_ROW = 2;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".";

    private final Line line;
    private final BlockPos altar;

    public static void open(ServerPlayer player, Line line, BlockPos altar) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new AltarMenu(id, inventory, (ServerPlayer) p, line, altar),
                Skin.title(player, Skin.Panel.ALTAR, Component.translatable(NS + "altar"))));
    }

    private AltarMenu(int id, Inventory inventory, ServerPlayer viewer, Line line, BlockPos altar) {
        super(id, inventory, viewer, 3);
        this.line = line;
        this.altar = altar;
        render();
    }

    private ServerLevel level() {
        return (ServerLevel) viewer.level();
    }

    public AltarBlock.State state() {
        return AltarBlock.state(level(), viewer, altar, line);
    }

    @Override
    protected void press(int slot) {
        if (slot == SLOT_CLOSE) {
            clickSound();
            viewer.closeContainer();
            return;
        }
        if (slot == SLOT_REMATCH) {
            if (AltarBlock.canRematch(level(), viewer, altar, line)) {
                clickSound();
                viewer.closeContainer();
                AltarBlock.rematch(level(), viewer, altar, line);
            } else {
                render();
            }
            return;
        }
        if (slot != SLOT_ACTION) return;
        switch (state()) {
            case READY -> {
                clickSound();
                viewer.closeContainer();
                AltarBlock.begin(level(), viewer, altar, line);
            }
            case DEFEATED -> {
                clickSound();
                viewer.closeContainer();
                Teleports.ascend(viewer);
            }
            default -> render();
        }
    }

    @Override
    protected void render() {
        AltarBlock.State state = state();
        frame(switch (state) {
            case READY -> DyeColor.LIME;
            case DEFEATED, COMPLETE -> DyeColor.LIGHT_BLUE;
            case UNDERWAY -> DyeColor.ORANGE;
            default -> DyeColor.RED;
        }, DyeColor.BLACK);
        God god = AltarBlock.godFor(level(), viewer, altar, line);
        boolean inTower = Realm.of(level().dimension()) != null;
        int floor = inTower ? Tower.floorAt(altar.getY()) : (god == null ? Tower.FLOORS : Tower.floorOf(god));

        if (god != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(god.displayTitle().copy().withStyle(plain(0xAAAAAA)));
            lore.add(text(NS + "god.rank", 0xAAAAAA, god.rank()));
            lore.add(text(NS + "god.relic", 0x55FFFF, Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + god.id() + "_weapon")));
            lore.add(god.power().description().copy().withStyle(plain(0xAAAAAA)));
            view.setItem(SLOT_GOD, icon(ModContent.weaponItem(god), god.displayName().copy().withStyle(plain(line.color())),
                    state == AltarBlock.State.DEFEATED, lore));

            // what to expect: the god's own move, named so it can be recognised when the warning comes
            view.setItem(SLOT_MOVE, icon(Items.BLAZE_POWDER, text(NS + "god.move", 0xF5B82E, god.moveName()), false,
                    List.of(god.signature().description().copy().withStyle(plain(0xAAAAAA)))));

            // what it gives up
            boolean won = state == AltarBlock.State.DEFEATED;
            var material = ModContent.materialItem(god.line(), Tower.tier(Tower.floorOf(god)));
            int amount = com.thefarawaydev.realmsofchaos.entity.GodBoss.materialDrop(god);
            ItemStack drops = icon(material, text(NS + "god.drops", 0x55FFFF), false, List.of(
                    text(NS + "god.drops.material", 0xFFFFFF, amount, new ItemStack(material).getHoverName()),
                    text(NS + "god.drops.relic", won ? 0x777777 : 0xFFFFFF, Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + god.id() + "_weapon"))));
            drops.setCount(amount);
            view.setItem(SLOT_DROPS, drops);

            if (won) {
                boolean can = AltarBlock.canRematch(level(), viewer, altar, line);
                view.setItem(SLOT_REMATCH, icon(Items.IRON_SWORD, text(NS + "altar.rematch", can ? 0xFFAA00 : 0x888888), can, List.of(
                        text(NS + "altar.rematch.lore", 0xAAAAAA, god.displayName()),
                        can ? text(NS + "god.drops.material", 0x55FF55, amount, new ItemStack(material).getHoverName())
                                : text(NS + (level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL ? "altar.peaceful.lore" : "altar.rematch.busy"), 0xFF5555))));
            }
        }

        view.setItem(SLOT_ACTION, switch (state) {
            case READY -> icon(Items.EMERALD, text(NS + "altar.begin", 0x55FF55), true,
                    List.of(text(NS + "altar.begin.lore", 0xAAAAAA, god.displayName())));
            case DEFEATED -> floor < Tower.FLOORS
                    ? icon(Items.ENDER_EYE, text(NS + "altar.ascend", 0x55FFFF, floor + 1), true, List.of(text(NS + "altar.ascend.lore", 0xAAAAAA)))
                    : icon(Items.NETHER_STAR, text(NS + "altar.complete", 0x55FF55), true, List.of(text(NS + "altar.complete.lore", 0xAAAAAA)));
            case COMPLETE -> icon(Items.NETHER_STAR, text(NS + "altar.complete", 0x55FF55), true, List.of(text(NS + "altar.complete.lore", 0xAAAAAA)));
            case UNDERWAY -> icon(Items.CLOCK, text(NS + "altar.underway", 0xFFAA00), false, List.of(text(NS + "altar.underway.lore", 0xAAAAAA)));
            case PEACEFUL -> icon(Items.BARRIER, text(NS + "altar.peaceful", 0xFF5555), false, List.of(text(NS + "altar.peaceful.lore", 0xAAAAAA)));
            case LOCKED -> icon(Items.BARRIER, text(NS + "altar.locked", 0xFF5555), false,
                    god == null || god.rank() >= Line.RANKS ? List.of()
                            : List.of(text(NS + "god.locked", 0xAAAAAA, com.thefarawaydev.realmsofchaos.god.Gods.get(line, god.rank() + 1).displayName())));
        });

        view.setItem(SLOT_CLOSE, icon(Items.ARROW, text(NS + "altar.close", 0xFFFFFF), false, List.of()));
        view.setItem(SLOT_INFO, icon(ModContent.CHAOS_CODEX.get(), Tower.towerName(line).copy().withStyle(plain(line.color())), true, List.of(
                text("hud." + RealmsOfChaos.MOD_ID + ".floor", 0xFFFFFF, Tower.tierName(line, floor), floor))));

        // the climb at a glance: four floors below, this floor, four floors above
        com.thefarawaydev.realmsofchaos.world.ProgressData data = com.thefarawaydev.realmsofchaos.world.ProgressData.get(level().getServer());
        for (int i = 0; i < 9; i++) {
            int f = floor - 4 + i;
            if (f < 1 || f > Tower.FLOORS) {
                view.setItem(slot(STRIP_ROW, i), filler(DyeColor.BLACK));
                continue;
            }
            God g = Tower.god(line, f);
            boolean won = data.isDefeated(viewer.getUUID(), g);
            List<Component> lore = new ArrayList<>();
            lore.add(Tower.tierName(line, f).copy().withStyle(plain(0xAAAAAA)));
            if (f == floor) lore.add(text(NS + "strip.here", 0xFFFF55));
            if (won) lore.add(text(NS + "god.defeated", 0x55FF55));
            view.setItem(slot(STRIP_ROW, i), pane(won ? DyeColor.LIME : f == floor ? DyeColor.YELLOW : DyeColor.GRAY,
                    text(NS + "strip.floor", won ? 0x55FF55 : f == floor ? 0xFFFF55 : 0x888888, f, g.displayName()), lore));
        }
        broadcastChanges();
    }
}
