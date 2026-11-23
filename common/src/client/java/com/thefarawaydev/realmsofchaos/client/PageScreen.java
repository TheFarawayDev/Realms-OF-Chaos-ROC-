package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.ui.PageMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The screen a page is read on: the Codex, the handbook, the chronicle, the altar. A tablet of dark stone with a
 * header in the page's own colour, its text set out in paragraphs that scroll, pictures of the items it speaks of,
 * and buttons drawn to match - a screen of the mod's own, not the game's stock dialog.
 *
 * <p>Everything on it comes from the page's title (see {@link PageMenu#pack}); a button pressed is sent back as the
 * menu's button number.
 */
public class PageScreen extends AbstractContainerScreen<PageMenu> {
    private static final int WIDTH = 300, HEADER = 24, FOOTER = 30, PAD = 12, BUTTON_H = 18, GAP = 4, PARAGRAPH_GAP = 5;
    private static final int STONE = 0xFF14122A, STONE_EDGE = 0xFF05040B, FIELD = 0xFF1C1A38, GOLD = 0xFFD6B25A, GOLD_DIM = 0xFF7A6532, INK = 0xFFF5E6C8;

    private record Line(FormattedCharSequence text, int indent) {}

    /** {@code icon} is the hidden slot its picture rides in, or -1: the item itself arrives a moment after the page does. */
    private record Paragraph(List<Line> lines, int icon, int height) {}

    private record Button(int index, Component label, Component tip, boolean exit) {}

    private Component heading = Component.empty();
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private Button exit;
    /** The buttons along the foot of the page, always in view: the way out, and - when a page has only one or two things to do - those too. */
    private final List<Button> footer = new ArrayList<>();
    private int columns = 1, contentHeight, scroll, accent = GOLD;
    private boolean laidOut;

    public PageScreen(PageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, 220);
    }

    private int panelHeight() {
        return Math.max(120, Math.min(236, this.height - 16));
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    private int panelLeft() {
        return (this.width - WIDTH) / 2;
    }

    private int viewHeight() {
        return panelHeight() - HEADER - FOOTER - 8;
    }

    /** Reads the page out of its title and sets its text out for the width there is. */
    private void layOut() {
        laidOut = true;
        paragraphs.clear();
        buttons.clear();
        exit = null;
        List<Component> parts = this.title.getSiblings();
        if (parts.size() < 3) {
            heading = this.title;
            return;
        }
        heading = parts.get(0);
        TextColor colour = heading.getSiblings().isEmpty() ? heading.getStyle().getColor() : heading.getSiblings().get(0).getStyle().getColor();
        accent = colour == null ? GOLD : 0xFF000000 | colour.getValue();
        int textWidth = WIDTH - PAD * 2 - 10;
        for (Component part : parts.get(1).getSiblings()) {
            String mark = part.getStyle().getInsertion();
            int icon = mark != null && mark.startsWith(PageMenu.ICON) ? mark.charAt(PageMenu.ICON.length()) - '0' : -1;
            int indent = mark != null && mark.startsWith(PageMenu.ICON) ? 22 : 0;
            List<Line> lines = new ArrayList<>();
            for (FormattedCharSequence row : this.font.split(part, textWidth - indent)) lines.add(new Line(row, indent));
            paragraphs.add(new Paragraph(lines, icon, Math.max(indent > 0 ? 18 : 0, lines.size() * 10)));
        }
        Component set = parts.get(2);
        try {
            columns = Math.max(1, Math.min(3, Integer.parseInt(String.valueOf(set.getStyle().getInsertion()))));
        } catch (NumberFormatException ignored) {
            columns = 1;
        }
        int index = 0;
        for (Component label : set.getSiblings()) {
            Component tip = label.getStyle().getHoverEvent() instanceof HoverEvent.ShowText shown ? shown.value() : null;
            Button button = new Button(index++, label, tip, PageMenu.EXIT.equals(label.getStyle().getInsertion()));
            if (button.exit) exit = button;
            else buttons.add(button);
        }
        // a page with one or two things to do - begin a trial, take a book - keeps them at its foot, where they
        // are seen without scrolling to the end of what is written; a page that is a list of choices keeps its grid
        footer.clear();
        if (buttons.size() <= 2) {
            footer.addAll(buttons);
            buttons.clear();
        }
        if (exit != null) footer.add(exit);
        // (a name too long for its column gets a wider one: fewer buttons across, none cut short)
        for (boolean fits = false; columns > 1 && !fits; ) {
            fits = true;
            for (Button button : buttons) fits &= this.font.width(button.label) <= buttonWidth() - 8;
            if (!fits) columns--;
        }
        contentHeight = 0;
        for (Paragraph paragraph : paragraphs) contentHeight += paragraph.height + PARAGRAPH_GAP;
        if (!buttons.isEmpty()) contentHeight += 6 + ((buttons.size() + columns - 1) / columns) * (BUTTON_H + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight())));
    }

    @Override
    protected void init() {
        super.init();
        layOut();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight());
    }

    private int buttonWidth() {
        return (WIDTH - PAD * 2 - 10 - (columns - 1) * GAP) / columns;
    }

    /** Where a button of the grid stands on the screen (its top may be scrolled out of view). */
    private int[] buttonAt(int n) {
        int y = panelTop() + HEADER + 4 - scroll + 6;
        for (Paragraph paragraph : paragraphs) y += paragraph.height + PARAGRAPH_GAP;
        return new int[]{panelLeft() + PAD + (n % columns) * (buttonWidth() + GAP), y + (n / columns) * (BUTTON_H + GAP)};
    }

    /** Where a button of the foot stands, and how wide it is: they share the row, the way out last. */
    private int[] footerAt(int n) {
        int count = footer.size(), w = Math.min(130, (WIDTH - PAD * 2 - (count - 1) * GAP) / Math.max(1, count)), all = count * w + (count - 1) * GAP;
        return new int[]{panelLeft() + (WIDTH - all) / 2 + n * (w + GAP), panelTop() + panelHeight() - FOOTER + 6, w};
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, Component label, boolean hover) {
        g.fill(x, y, x + w, y + BUTTON_H, STONE_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + BUTTON_H - 1, hover ? 0xFF3A3470 : 0xFF2A2752);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hover ? GOLD : 0xFF4A4690);
        g.fill(x + 1, y + BUTTON_H - 2, x + w - 1, y + BUTTON_H - 1, 0xFF15132C);
        if (hover) {
            g.fill(x, y, x + 1, y + BUTTON_H, GOLD);
            g.fill(x + w - 1, y, x + w, y + BUTTON_H, GOLD);
        }
        List<FormattedCharSequence> rows = this.font.split(label, w - 8);
        if (!rows.isEmpty()) g.centeredText(this.font, rows.get(0), x + w / 2, y + 5, hover ? 0xFFFFFFFF : INK);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        if (!laidOut) layOut();
        int x = panelLeft(), y = panelTop(), h = panelHeight();
        // the tablet: an edge, a gold line inside it, the header band in the page's colour, the field the text lies on
        g.fill(x - 2, y - 2, x + WIDTH + 2, y + h + 2, STONE_EDGE);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + h + 1, GOLD_DIM);
        g.fill(x, y, x + WIDTH, y + h, STONE);
        g.fill(x, y, x + WIDTH, y + HEADER, 0xFF221E44);
        g.fill(x, y + HEADER - 2, x + WIDTH, y + HEADER - 1, accent);
        g.fill(x, y + HEADER - 1, x + WIDTH, y + HEADER, STONE_EDGE);
        g.fill(x + 4, y + 4, x + 7, y + HEADER - 6, accent);
        g.fill(x + WIDTH - 7, y + 4, x + WIDTH - 4, y + HEADER - 6, accent);
        for (int k = 0; k < 4; k++) {
            // (a stud in each corner)
            int sx = k % 2 == 0 ? x + 2 : x + WIDTH - 4, sy = k < 2 ? y + 2 : y + h - 4;
            g.fill(sx, sy, sx + 2, sy + 2, GOLD);
        }
        g.fill(x + 6, y + HEADER + 2, x + WIDTH - 6, y + h - FOOTER, FIELD);
        g.fill(x + 6, y + h - FOOTER, x + WIDTH - 6, y + h - FOOTER + 1, GOLD_DIM);
        g.centeredText(this.font, heading, x + WIDTH / 2, y + 8, 0xFFFFFFFF);

        // what is written on it, cut off at the edges of the field
        int top = y + HEADER + 4, view = viewHeight();
        g.enableScissor(x + 6, top, x + WIDTH - 6, top + view);
        int at = top - scroll;
        for (Paragraph paragraph : paragraphs) {
            if (at + paragraph.height >= top && at <= top + view) {
                ItemStack icon = paragraph.icon < 0 ? ItemStack.EMPTY : this.menu.icon(paragraph.icon);
                if (!icon.isEmpty()) g.item(icon, x + PAD, at);
                int row = at + (paragraph.icon < 0 ? 0 : Math.max(0, (18 - paragraph.lines.size() * 10) / 2));
                for (Line line : paragraph.lines) {
                    g.text(this.font, line.text, x + PAD + line.indent, row, INK, false);
                    row += 10;
                }
            }
            at += paragraph.height + PARAGRAPH_GAP;
        }
        Button over = null;
        for (int n = 0; n < buttons.size(); n++) {
            int[] spot = buttonAt(n);
            boolean hover = mouseY >= top && mouseY < top + view && isHovering(spot[0], spot[1], buttonWidth(), BUTTON_H, mouseX, mouseY);
            if (spot[1] + BUTTON_H >= top && spot[1] <= top + view) drawButton(g, spot[0], spot[1], buttonWidth(), buttons.get(n).label, hover);
            if (hover) over = buttons.get(n);
        }
        g.disableScissor();

        // a bar at the side when there is more than fits
        if (maxScroll() > 0) {
            int track = view - 2, grip = Math.max(14, track * view / contentHeight), gy = top + 1 + (track - grip) * scroll / maxScroll();
            g.fill(x + WIDTH - 11, top, x + WIDTH - 8, top + view, STONE_EDGE);
            g.fill(x + WIDTH - 11, gy, x + WIDTH - 8, gy + grip, accent);
        }
        for (int n = 0; n < footer.size(); n++) {
            int[] spot = footerAt(n);
            boolean hover = isHovering(spot[0], spot[1], spot[2], BUTTON_H, mouseX, mouseY);
            drawButton(g, spot[0], spot[1], spot[2], footer.get(n).label, hover);
            // (what the page is for is marked out from the way out of it)
            if (!footer.get(n).exit) g.fill(spot[0] + 1, spot[1] + 1, spot[0] + spot[2] - 1, spot[1] + 2, accent);
            if (hover) over = footer.get(n);
        }
        if (over != null && over.tip != null) g.setTooltipForNextFrame(this.font, this.font.split(over.tip, 200), mouseX, mouseY);
    }

    /** (Positions here are the screen's own: the stock check is against the corner of a chest that is not there.) */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // (the heading is drawn with the tablet: there is no chest title or inventory label on a page)
    }

    private void press(Button button) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button.index);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            int top = panelTop() + HEADER + 4;
            for (int n = 0; n < footer.size(); n++) {
                int[] spot = footerAt(n);
                if (isHovering(spot[0], spot[1], spot[2], BUTTON_H, event.x(), event.y())) {
                    press(footer.get(n));
                    return true;
                }
            }
            if (event.y() >= top && event.y() < top + viewHeight()) {
                for (int n = 0; n < buttons.size(); n++) {
                    int[] spot = buttonAt(n);
                    if (isHovering(spot[0], spot[1], buttonWidth(), BUTTON_H, event.x(), event.y())) {
                        press(buttons.get(n));
                        return true;
                    }
                }
            }
        }
        // (nothing on a page is picked up or put down: a click anywhere else does nothing)
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(vertical * 20)));
        return true;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return false;
    }
}
