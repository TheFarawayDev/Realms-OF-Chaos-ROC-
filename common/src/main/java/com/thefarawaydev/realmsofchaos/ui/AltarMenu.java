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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * The Trial Altar screen. It shows the god of this floor and one action button whose meaning follows the state:
 * begin the trial, ascend (once the god is defeated), or an explanation of why nothing can happen right now.
 */
public class AltarMenu extends ButtonMenu {
    public static final int SLOT_ACTION = 11, SLOT_GOD = 13, SLOT_CLOSE = 15, SLOT_INFO = 22;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".";

    private final Line line;
    private final BlockPos altar;

    public static void open(ServerPlayer player, Line line, BlockPos altar) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new AltarMenu(id, inventory, (ServerPlayer) p, new SimpleContainer(SIZE), line, altar),
                Component.translatable(NS + "altar")));
    }

    private AltarMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer view, Line line, BlockPos altar) {
        super(id, inventory, viewer, view);
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
        fill(switch (state) {
            case READY -> DyeColor.LIME;
            case DEFEATED, COMPLETE -> DyeColor.LIGHT_BLUE;
            case UNDERWAY -> DyeColor.ORANGE;
            default -> DyeColor.RED;
        });
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
        view.setItem(SLOT_INFO, icon(ModContent.CHAOS_CODEX.get(), Tower.towerName(line).copy().withStyle(plain(line.color())), false, List.of(
                text("hud." + RealmsOfChaos.MOD_ID + ".floor", 0xFFFFFF, Tower.tierName(line, floor), floor))));
        broadcastChanges();
    }
}
