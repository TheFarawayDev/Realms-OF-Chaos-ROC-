package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * The look of the mod's menus. A chest screen opened by a server cannot be given a different background - but its
 * title can be written in a font, and the mod ships a font whose letters are whole panels. A menu's title is that
 * one letter, pulled back to the screen's corner so that it lies exactly over the chest, followed by the title's
 * words. Every slot is a hole in the picture; unused slots are filled with the mod's own blank tile, and meters
 * and labels are gems rather than glass panes. Nothing here needs anything on the client but the mod's own
 * resources, so it is the same on Fabric and Forge.
 */
public final class Skin {
    public static final Identifier FONT = RealmsOfChaos.id("ui");
    /** In the font: eight pixels back, and back again over the panel to where a title starts. */
    private static final String BACK_TO_CORNER = "\uF800", BACK_TO_TITLE = "\uF801";
    public static final int INK = 0xF5E6C8;

    /** One panel for each kind of menu. The letter is its picture in the font. */
    public enum Panel {
        CODEX('\uE000', 6), FORGE('\uE001', 6), PASSAGE('\uE002', 6), BAG_LARGE('\uE003', 6),
        ALTAR('\uE004', 3), SEAL('\uE005', 3), DECK('\uE006', 6), PARTY('\uE007', 6), STORY('\uE008', 6), BAG('\uE009', 3),
        SETTINGS('\uE00A', 3), INSIGHT('\uE00B', 6), BOON('\uE00C', 3), BESTIARY('\uE00D', 6);

        public final char glyph;
        public final int rows;

        Panel(char glyph, int rows) {
            this.glyph = glyph;
            this.rows = rows;
        }
    }

    private Skin() {}

    /** True while a menu is being drawn for a player who has turned the drawn interface off: tiles and gems stay glass. */
    private static boolean plain;

    /** Says whose menu is about to be drawn. */
    public static void drawFor(net.minecraft.server.level.ServerPlayer viewer) {
        plain = viewer != null && !Options.on(viewer, Options.Option.ART);
    }

    /**
     * A menu's title for one player: the panel and the name - or just the name, for a player who has turned the
     * drawn interface off, or whose game has not got this build's artwork.
     */
    public static Component title(net.minecraft.server.level.ServerPlayer player, Panel panel, Component name) {
        return HudArt.present(player, title(panel, name), name);
    }

    /** A menu title: the panel's artwork laid over the chest screen, then the name written on its header. */
    public static Component title(Panel panel, Component name) {
        Component art = Component.literal(BACK_TO_CORNER + panel.glyph + BACK_TO_TITLE)
                .withStyle(Style.EMPTY.withFont(new FontDescription.Resource(FONT)).withColor(0xFFFFFF));
        return Component.empty().append(art).append(name.copy().withStyle(Style.EMPTY.withColor(INK)));
    }

    /** True if a title carries a panel. */
    public static boolean skinned(Component title, Panel panel) {
        return title.getString().indexOf(panel.glyph) >= 0;
    }

    private static ItemStack model(ItemStack stack, String name) {
        if (plain) return stack;
        stack.set(DataComponents.ITEM_MODEL, RealmsOfChaos.id("ui/" + name));
        return stack;
    }

    /** Makes a stack look like the blank tile that fills an unused slot. */
    public static ItemStack tile(ItemStack stack) {
        return model(stack, "tile");
    }

    /** Makes a stack look like a gem of a colour: a meter's segment, a label. */
    public static ItemStack gem(ItemStack stack, DyeColor color) {
        return model(stack, "gem_" + color.getName());
    }
}
