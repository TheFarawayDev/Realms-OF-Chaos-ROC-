package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Pictures on the screen, made of nothing but text. A boss bar's name can be written in a font, and the mod ships
 * fonts whose letters are artwork (see tools/hud_art.py): a frame that lies exactly over the game's own bar, pieces
 * of fill, block lettering, and cards for a few lines of writing. Spaces of exact width - some of them negative -
 * move the pen between the pieces. Every row made here is the same width however it is filled, because the game
 * centres a name on the screen and the pictures have to land where they expect to.
 */
public final class HudArt {
    private static final Style ART = Style.EMPTY.withFont(new FontDescription.Resource(RealmsOfChaos.id("hud"))).withColor(0xFFFFFF).withoutShadow();
    public static final int NAME_INK = 0xF5E6C8, SHIELDED = 0x7EEBFF, WRATH = 0xFF5A3C, CAST = 0xFFBE50;
    /** The most a card's line may be wide, and the most lines a card holds. */
    public static final int CARD_TEXT_WIDTH = 198, CARD_LINES = 3;
    /** Which of the five line fonts each line of a card uses, by how many lines it has. */
    private static final int[][] ROWS = {{2}, {1, 3}, {0, 2, 4}};

    public enum Card {
        TIP(0x55E6F5), INSIGHT(0xFFAA3C), NOTICE(0xECD096);

        public final int color;

        Card(int color) {
            this.color = color;
        }
    }

    private HudArt() {}

    /** Spaces that move the pen by exactly this many pixels, forward or back. */
    public static String space(int pixels) {
        StringBuilder out = new StringBuilder();
        char base = pixels < 0 ? HudGlyphs.BACK : HudGlyphs.SPACE;
        int left = Math.abs(pixels);
        for (int k = 8; k >= 0; k--) {
            while (left >= (1 << k)) {
                out.append((char) (base + k));
                left -= 1 << k;
            }
        }
        return out.toString();
    }

    /** How far a run of spaces made by {@link #space} moves the pen: the test's way of reading a row back. */
    public static int advance(String row) {
        int total = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c >= HudGlyphs.SPACE && c <= HudGlyphs.SPACE + 8) total += 1 << (c - HudGlyphs.SPACE);
            else if (c >= HudGlyphs.BACK && c <= HudGlyphs.BACK + 8) total -= 1 << (c - HudGlyphs.BACK);
            else if (c >= HudGlyphs.FILL && c <= HudGlyphs.FILL + 7) total += (1 << (c - HudGlyphs.FILL)) + 1;
            else if (c >= HudGlyphs.LETTER + 0x20 && c < HudGlyphs.LETTER + 0x60) total += HudGlyphs.LETTER_ADVANCE[c - HudGlyphs.LETTER - 0x20];
            else if (c == HudGlyphs.CAST_FRAME) total += HudGlyphs.CAST_FRAME_ADVANCE;
            else {
                for (int k = 0; k < 3; k++) {
                    if (c == HudGlyphs.FRAME[k]) total += HudGlyphs.FRAME_ADVANCE[k];
                    if (c == HudGlyphs.OVERLAY[k]) total += HudGlyphs.OVERLAY_ADVANCE[k];
                    if (c == HudGlyphs.CARD[k]) total += HudGlyphs.CARD_ADVANCE[k];
                }
            }
        }
        return total;
    }

    /** A name in block lettering: capitals only, and nothing the lettering has no shape for. */
    public static String lettering(String text, int maxWidth) {
        StringBuilder out = new StringBuilder();
        int width = 0;
        for (char raw : text.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if (raw < 0x20 || raw >= 0x60) continue;
            int adv = HudGlyphs.LETTER_ADVANCE[raw - 0x20];
            if (adv == 0) continue;
            if (width + adv > maxWidth) break;
            out.append((char) (HudGlyphs.LETTER + raw));
            width += adv;
        }
        return out.toString().stripTrailing();
    }

    public static int letteringWidth(String lettering) {
        return advance(lettering);
    }

    /** How many of the bar's 182 pixels are full. Anything still alive shows at least one. */
    public static int fillWidth(float progress) {
        if (progress <= 0.0F) return 0;
        return Math.max(1, Math.min(HudGlyphs.BAR_WIDTH, Math.round(progress * HudGlyphs.BAR_WIDTH)));
    }

    /** A fill of exactly this width, the pen left at its end. */
    private static String fill(int width) {
        StringBuilder out = new StringBuilder();
        for (int k = 7; k >= 0; k--) {
            if ((width & (1 << k)) != 0) out.append((char) (HudGlyphs.FILL + k)).append((char) HudGlyphs.BACK);
        }
        return out.toString();
    }

    private static MutableComponent art(String row) {
        return Component.literal(row).withStyle(ART);
    }

    private static MutableComponent bar(char frame, int frameAdvance, char overlay, int overlayAdvance, int width, int fillColor, String name, int nameWidth) {
        MutableComponent row = Component.empty();
        row.append(art(frame + space(HudGlyphs.BAR_X - frameAdvance)));
        row.append(Component.literal(fill(width)).withStyle(ART.withColor(fillColor)));
        String back = space(-(HudGlyphs.BAR_X + width));
        row.append(art(overlay == 0 ? back : back + overlay + space(-overlayAdvance)));
        String letters = lettering(name, nameWidth);
        int adv = letteringWidth(letters), pad = (HudGlyphs.FRAME_WIDTH - Math.max(0, adv - 1)) / 2;
        row.append(Component.literal(space(pad) + letters + space(HudGlyphs.FRAME_WIDTH - pad - adv)).withStyle(ART.withColor(NAME_INK)));
        return row;
    }

    /**
     * A god's bar: its tower's frame over the game's own bar, a fill as long as its health, the marks where its
     * aegis rises and its wrath begins, a gem lit for each phase reached, and its name.
     */
    public static Component bossBar(Line line, int phase, boolean shielded, float progress, String name) {
        int p = Math.max(1, Math.min(3, phase));
        int color = shielded ? SHIELDED : p >= 3 ? WRATH : line.color();
        return bar(HudGlyphs.FRAME[line.ordinal()], HudGlyphs.FRAME_ADVANCE[line.ordinal()], HudGlyphs.OVERLAY[p - 1], HudGlyphs.OVERLAY_ADVANCE[p - 1],
                fillWidth(progress), color, name, 88);
    }

    /** The cast bar: a move's name, filling as the god winds up. */
    public static Component castBar(float progress, String name) {
        return bar(HudGlyphs.CAST_FRAME, HudGlyphs.CAST_FRAME_ADVANCE, (char) 0, 0, fillWidth(progress), CAST, name, 124);
    }

    // ---- cards ----------------------------------------------------------------------------------------------

    /** Text a card can show: the letters of the game's own font from space to tilde, and nothing else. */
    public static String plain(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '‘', '’' -> out.append('\'');
                case '“', '”' -> out.append('"');
                case '–', '—' -> out.append('-');
                case '…' -> out.append("...");
                case '\n', '\t', ' ' -> out.append(' ');
                default -> {
                    if (c >= 0x20 && c < 0x7F) out.append(c);
                }
            }
        }
        return out.toString().strip();
    }

    /** How wide the game's own font writes this. */
    public static int textWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) width += c >= 0x20 && c < 0x7F ? HudGlyphs.ASCII_ADVANCE[c - 0x20] : 6;
        return width;
    }

    /** Breaks text into lines no wider than a card, at spaces where it can. */
    public static List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : plain(text).split(" +")) {
            if (word.isEmpty()) continue;
            while (textWidth(word) > maxWidth) {                // a word wider than the card is cut where it must be
                int cut = word.length();
                while (cut > 1 && textWidth(word.substring(0, cut)) > maxWidth) cut--;
                if (line.length() > 0) lines.add(line.toString());
                line.setLength(0);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String joined = line.length() == 0 ? word : line + " " + word;
            if (textWidth(joined) <= maxWidth) {
                line.setLength(0);
                line.append(joined);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /**
     * A card with up to three lines on it. A heading, if there is one, takes the first line in the card's colour.
     * Text that does not fit is cut short with an ellipsis rather than spilling off the card.
     */
    public static Component card(Card kind, String heading, String text) {
        return card(kind, lines(heading, text), heading != null && !heading.isBlank());
    }

    /** The lines {@link #card(Card, String, String)} would write. */
    public static List<String> lines(String heading, String text) {
        List<String> lines = new ArrayList<>();
        if (heading != null && !heading.isBlank()) {
            List<String> head = wrap(heading, CARD_TEXT_WIDTH);
            if (!head.isEmpty()) lines.add(head.get(0));
        }
        lines.addAll(wrap(text == null ? "" : text, CARD_TEXT_WIDTH));
        if (lines.size() > CARD_LINES) {
            lines = new ArrayList<>(lines.subList(0, CARD_LINES));
            String last = lines.get(CARD_LINES - 1);
            while (!last.isEmpty() && textWidth(last + "...") > CARD_TEXT_WIDTH) last = last.substring(0, last.length() - 1);
            lines.set(CARD_LINES - 1, last.stripTrailing() + "...");
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    public static Component card(Card kind, List<String> lines, boolean headed) {
        MutableComponent row = Component.empty();
        row.append(art(HudGlyphs.CARD[kind.ordinal()] + space(-HudGlyphs.CARD_ADVANCE[kind.ordinal()])));
        int[] rows = ROWS[Math.min(CARD_LINES, lines.size()) - 1];
        for (int i = 0; i < rows.length; i++) {
            String line = lines.get(i);
            int width = textWidth(line), pad = (HudGlyphs.CARD_WIDTH - width) / 2;
            row.append(art(space(pad)));
            row.append(Component.literal(line).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(RealmsOfChaos.id("tip" + rows[i])))
                    .withColor(headed && i == 0 ? kind.color : 0xFFFFFF)));
            row.append(art(space(-(pad + width))));
        }
        row.append(art(space(HudGlyphs.CARD_WIDTH)));
        return row;
    }
}
