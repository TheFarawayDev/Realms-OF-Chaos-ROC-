package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.block.CornucopiaBlock;
import com.thefarawaydev.realmsofchaos.block.OrbBlockEntity;
import com.thefarawaydev.realmsofchaos.ui.OrbMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Orb of Plenty's screen: the core and the ichor on the left, the pattern in the middle inside a ring of lights
 * that fills as the next one comes, what it has made on the right, and under them a line or two saying how the
 * shrine stands. Everything shown is in the menu's slots and numbers; nothing floats over the block any more.
 */
public class OrbScreen extends AbstractContainerScreen<OrbMenu> {
    private static final Identifier TEXTURE = RealmsOfChaos.id("textures/gui/screen_orb.png");
    private static final String KEY = "screen." + RealmsOfChaos.MOD_ID + ".orb.";
    private static final int RING_X = 88, RING_Y = 45, RING_R = 19, LIGHTS = 32;
    private static final int INK = 0xFFF5E6C8, GOOD = 0xFF9FF0C8, WAIT = 0xFFF0D070, BAD = 0xFFFF8A6A, DIM = 0xFF5A5288;

    public OrbScreen(OrbMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
        this.inventoryLabelY = OrbMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);
        // the ring of lights round the pattern: lit as far as the next one has come, a glint running round while it works
        CornucopiaBlock.Core core = CornucopiaBlock.Core.of(menu.getSlot(OrbBlockEntity.CORE).getItem());
        int colour = switch (core) {
            case STORM -> 0xFFF2D25C;
            case TIDE -> 0xFF4FD8E8;
            case SHADE -> 0xFFB07CFF;
            case NONE -> 0xFFD6B25A;
        };
        int mode = menu.data(OrbMenu.MODE);
        boolean whole = (menu.data(OrbMenu.SHRINE) & OrbMenu.WHOLE) != 0;
        int lit = whole && mode != OrbMenu.IDLE ? Math.round(menu.data(OrbMenu.PROGRESS) / 1000.0F * LIGHTS) : 0;
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        int glint = (int) ((time / 2) % LIGHTS);
        for (int i = 0; i < LIGHTS; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / LIGHTS;
            int x = leftPos + RING_X + (int) Math.round(Math.cos(a) * RING_R) - 1, y = topPos + RING_Y + (int) Math.round(Math.sin(a) * RING_R) - 1;
            int c = i < lit ? colour : DIM;
            if (whole && mode == OrbMenu.MAKING || whole && mode == OrbMenu.BRINGING) {
                if (i == glint) c = 0xFFFFFFFF;
            }
            graphics.fill(x, y, x + 2, y + 2, c);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, INK, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF4A3B28, false);
        List<Component> lines = status();
        int colour = colour();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (font.width(line) > 164) line = Component.literal(font.plainSubstrByWidth(line.getString(), 158) + "...");
            graphics.centeredText(font, line, imageWidth / 2, 74 + i * 9, i == 0 ? colour : 0xFFB8B0D8);
        }
    }

    private int colour() {
        int shrine = menu.data(OrbMenu.SHRINE), mode = menu.data(OrbMenu.MODE);
        if (menu.data(OrbMenu.REFUSED) > 0 || (shrine & OrbMenu.WHOLE) == 0) return BAD;
        if (mode == OrbMenu.IDLE || mode == OrbMenu.WAITING) return WAIT;
        return GOOD;
    }

    /** What the orb says about itself, from the numbers the server sends. */
    private List<Component> status() {
        int shrine = menu.data(OrbMenu.SHRINE), mode = menu.data(OrbMenu.MODE);
        Component refused = menu.data(OrbMenu.REFUSED) > 0 ? Component.translatable(KEY + "refused") : null;
        if ((shrine & OrbMenu.WHOLE) == 0) {
            Component why = (shrine & OrbMenu.PLINTH) == 0 ? Component.translatable(KEY + "needs_plinth")
                    : Component.translatable(KEY + "needs_columns", shrine >> OrbMenu.COLUMNS_SHIFT);
            return List.of(refused != null ? refused : Component.translatable(KEY + "incomplete"), why);
        }
        if (mode == OrbMenu.IDLE) return List.of(refused != null ? refused : Component.translatable(KEY + "no_core"), Component.translatable(KEY + "no_core.hint"));
        ItemStack pattern = menu.getSlot(OrbBlockEntity.PATTERN).getItem();
        int seconds = menu.data(OrbMenu.SECONDS);
        Component time = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        boolean home = (shrine & OrbMenu.HOME) != 0;
        Component next = Component.translatable(home ? KEY + "next.home" : KEY + "next", time);
        return switch (mode) {
            case OrbMenu.BRINGING -> {
                CornucopiaBlock.Core core = CornucopiaBlock.Core.of(menu.getSlot(OrbBlockEntity.CORE).getItem());
                Component what = core.yield() == null ? Component.empty() : new ItemStack(core.yield()).getHoverName();
                yield List.of(refused != null ? refused : Component.translatable(KEY + "bringing", what), next);
            }
            case OrbMenu.WAITING -> List.of(refused != null ? refused : Component.translatable(KEY + "making", pattern.getHoverName()),
                    Component.translatable(KEY + "needs_ichor", menu.data(OrbMenu.COST)));
            default -> List.of(refused != null ? refused : Component.translatable(KEY + "making", pattern.getHoverName()),
                    Component.translatable(KEY + "cost", menu.data(OrbMenu.COST)).append("  ").append(next));
        };
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // an empty slot says what it is for
        if (hoveredSlot != null && hoveredSlot.index < OrbBlockEntity.SIZE && menu.getCarried().isEmpty()
                && (!hoveredSlot.hasItem() || hoveredSlot.index == OrbBlockEntity.PATTERN)) {
            String which = switch (hoveredSlot.index) {
                case OrbBlockEntity.CORE -> "core";
                case OrbBlockEntity.ICHOR -> "ichor";
                case OrbBlockEntity.PATTERN -> hoveredSlot.hasItem() ? "pattern.set" : "pattern";
                default -> "made";
            };
            List<Component> lines = new java.util.ArrayList<>();
            if (hoveredSlot.hasItem()) lines.add(hoveredSlot.getItem().getHoverName());
            lines.add(Component.translatable(KEY + "slot." + which));
            lines.add(Component.translatable(KEY + "slot." + which + ".hint").withStyle(s -> s.withColor(0xA8A0C8)));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
