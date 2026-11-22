package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.item.Tarot;
import com.thefarawaydev.realmsofchaos.item.TarotDeckItem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** The Deck of Fate laid out: all 22 cards. Click one to play it from the deck; the deck then rests for a while. */
public class DeckMenu extends ButtonMenu {
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".deck";
    private final InteractionHand hand;

    public static void open(ServerPlayer player, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DeckMenu(id, inventory, (ServerPlayer) p, hand),
                Skin.title(player, Skin.Panel.DECK, Component.translatable(NS))));
    }

    private DeckMenu(int id, Inventory inventory, ServerPlayer viewer, InteractionHand hand) {
        super(id, inventory, viewer, 6);
        this.hand = hand;
        render();
    }

    /** Cards sit in rows 1-4, columns 1-7 (the first 22 of those 28 places). */
    public static int slotOf(Tarot card) {
        return slot(1 + card.ordinal() / 7, 1 + card.ordinal() % 7);
    }

    @Override
    protected void press(int slot) {
        ItemStack deck = viewer.getItemInHand(hand);
        if (!(deck.getItem() instanceof TarotDeckItem) || viewer.getCooldowns().isOnCooldown(deck)) return;
        for (Tarot card : Tarot.values()) {
            if (slotOf(card) == slot) {
                clickSound();
                viewer.closeContainer();
                TarotDeckItem.play((ServerLevel) viewer.level(), viewer, deck, card, card.rechargeSeconds() * 20);
                return;
            }
        }
    }

    @Override
    protected void render() {
        frame(DyeColor.PURPLE, DyeColor.BLACK);
        for (Tarot card : Tarot.values()) {
            view.setItem(slotOf(card), icon(ModContent.tarotItem(card), card.displayName().copy().withStyle(plain(0xF5B82E)), false, List.of(
                    card.description().copy().withStyle(plain(0x55FFFF)),
                    text(NS + ".rest", 0xAAAAAA, card.rechargeSeconds()))));
        }
        broadcastChanges();
    }
}
