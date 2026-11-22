package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.Boons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * A broken seal's gift: three boons laid out, one to take. Under each is a row of gems for how many of it the
 * player already carries. Closing the screen without choosing takes the first, so a seal never gives nothing.
 */
public class BoonMenu extends ButtonMenu {
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".boon";
    public static final int[] SLOTS = {slot(1, 2), slot(1, 4), slot(1, 6)};
    public static final int SLOT_INFO = slot(0, 4);

    private final List<Boons.Boon> offered;
    private boolean taken;

    public static void open(ServerPlayer player, BlockPos seal) {
        List<Boons.Boon> offered = Boons.offer(player, seal);
        if (offered.isEmpty()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BoonMenu(id, inventory, (ServerPlayer) p, offered),
                Skin.title(Skin.Panel.BOON, Component.translatable(NS))));
    }

    private BoonMenu(int id, Inventory inventory, ServerPlayer viewer, List<Boons.Boon> offered) {
        super(id, inventory, viewer, 3);
        this.offered = offered;
        render();
    }

    public List<Boons.Boon> offered() {
        return offered;
    }

    public static ItemLike icon(Boons.Boon boon) {
        return switch (boon) {
            case VIGOUR -> Items.GLISTERING_MELON_SLICE;
            case EDGE -> Items.FLINT;
            case SWIFT -> Items.FEATHER;
            case WARD -> Items.IRON_CHESTPLATE;
            case KEEN -> Items.SPYGLASS;
            case THIRST -> Items.GHAST_TEAR;
            case FORTUNE -> Items.GOLD_NUGGET;
        };
    }

    private void take(Boons.Boon boon) {
        if (taken) return;
        taken = true;
        Boons.grant(viewer, boon);
        viewer.level().playSound(null, viewer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
        Cards.show(viewer, HudArt.Card.BOON, Words.of(boon.displayName()) + " " + com.thefarawaydev.realmsofchaos.world.Insight.numeral(Boons.count(viewer, boon)),
                Words.of(boon.description()), true);
    }

    @Override
    protected void press(int slot) {
        for (int i = 0; i < offered.size(); i++) {
            if (slot == SLOTS[i] || slot == SLOTS[i] + 9) {
                clickSound();
                take(offered.get(i));
                viewer.closeContainer();
                return;
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!taken && !offered.isEmpty()) take(offered.get(0));         // a seal never gives nothing
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        view.setItem(SLOT_INFO, icon(Items.NETHER_STAR, text(NS + ".title", 0x78E68C), true, List.of(text(NS + ".lore", 0xAAAAAA), text(NS + ".lore2", 0xAAAAAA))));
        for (int i = 0; i < offered.size(); i++) {
            Boons.Boon boon = offered.get(i);
            int have = Boons.count(viewer, boon);
            List<Component> lore = new ArrayList<>();
            lore.add(boon.description().copy().withStyle(plain(0xAAAAAA)));
            lore.add(text(NS + ".have", 0x55FFFF, have, Boons.MAX_STACK));
            lore.add(text(NS + ".take", 0x55FF55));
            view.setItem(SLOTS[i], icon(icon(boon), boon.displayName().copy().withStyle(plain(0x78E68C)), true, lore));
            view.setItem(SLOTS[i] + 9, pane(have > 0 ? DyeColor.LIME : DyeColor.GRAY, text(NS + ".have", have > 0 ? 0x78E68C : 0x888888, have, Boons.MAX_STACK), List.of()));
        }
        broadcastChanges();
    }
}
