package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.PedestalMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Keystone Pedestal's screen: the Ruined Gate's arch, its three sockets in the lintel, and under it a cradle for
 * each keystone. A keystone set lights its socket in the drawing as it lights the real one; when all three are set
 * the opening fills with the gate's light.
 */
public class PedestalScreen extends AbstractContainerScreen<PedestalMenu> {
    private static final Identifier TEXTURE = RealmsOfChaos.id("textures/gui/screen_pedestal.png");
    private static final String KEY = "screen." + RealmsOfChaos.MOD_ID + ".pedestal.";
    /** The arch's opening in the drawing, and the row of sockets in its lintel. */
    private static final int OPEN_X0 = 46, OPEN_X1 = 130, OPEN_Y0 = 30, OPEN_Y1 = 64, SOCKET_Y = 21;

    public PedestalScreen(PedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
        this.inventoryLabelY = PedestalMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        if (menu.data(PedestalMenu.AWAKE) != 0) {
            // the gate is open: the arch fills with slow bands of its light
            for (int y = OPEN_Y0; y < OPEN_Y1; y++) {
                double wave = Math.sin((y + time * 0.6) * 0.35) * 0.5 + 0.5;
                int r = (int) (90 + 80 * wave), g = (int) (40 + 30 * wave), b = (int) (150 + 90 * wave);
                graphics.fill(leftPos + OPEN_X0, topPos + y, leftPos + OPEN_X1, topPos + y + 1, 0xC0000000 | r << 16 | g << 8 | b);
            }
        }
        for (Line line : Line.values()) {
            // each socket in the lintel: dark until its keystone is set, then lit in its tower's colour
            int x = leftPos + PedestalMenu.cradleX(line) + 5, y = topPos + SOCKET_Y;
            boolean set = menu.isSet(line);
            int c = set ? 0xFF000000 | line.color() : 0xFF201A30;
            graphics.fill(x, y, x + 6, y + 6, c);
            if (set) {
                graphics.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFFFFFF);
                int pulse = (int) (60 + 50 * Math.sin(time * 0.15 + line.ordinal()));
                graphics.fill(x - 1, y - 1, x + 7, y, pulse << 24 | line.color());
                graphics.fill(x - 1, y + 6, x + 7, y + 7, pulse << 24 | line.color());
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF5E6C8, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF4A3B28, false);
        List<Component> lines = new ArrayList<>();
        int colour;
        if (menu.data(PedestalMenu.ARCH) == 0) {
            lines.add(Component.translatable(KEY + "no_gate"));
            colour = 0xFFFF8A6A;
        } else if (menu.data(PedestalMenu.AWAKE) != 0) {
            lines.add(Component.translatable(KEY + "awake"));
            lines.add(Component.translatable(KEY + "awake.hint"));
            colour = 0xFF9FF0C8;
        } else {
            int set = 0;
            MutableComponent missing = Component.empty();
            for (Line line : Line.values()) {
                if (menu.isSet(line)) {
                    set++;
                    continue;
                }
                if (!missing.getSiblings().isEmpty()) missing.append(", ");
                missing.append(Component.translatable(KEY + "tower." + line.id()));
            }
            lines.add(Component.translatable(KEY + "set", set));
            lines.add(Component.translatable(KEY + "missing", missing));
            colour = 0xFFF0D070;
        }
        for (int i = 0; i < lines.size(); i++) graphics.centeredText(font, lines.get(i), imageWidth / 2, 72 + i * 9, i == 0 ? colour : 0xFFB8B0D8);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.index < 3 && menu.getCarried().isEmpty()) {
            Line line = Line.values()[hoveredSlot.index];
            List<Component> lines = new ArrayList<>();
            lines.add(new ItemStack(ModContent.keystoneItem(line)).getHoverName());
            lines.add(Component.translatable(hoveredSlot.hasItem() ? KEY + "cradle.set" : KEY + "cradle.empty").withStyle(s -> s.withColor(0xA8A0C8)));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
