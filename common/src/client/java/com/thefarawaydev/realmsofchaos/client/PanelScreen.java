package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Skin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * The screen of one of the mod's menus: a real screen of its own, like an inventory, with the menu's own panel
 * drawn as its background - a header in the menu's colour with its emblem, a dark field of slots, and a parchment
 * half for the player's inventory. Each kind of menu is registered with the game as a menu type of its own.
 */
public class PanelScreen extends AbstractContainerScreen<ChestMenu> {
    private final Identifier texture;
    private final int rows;

    public PanelScreen(ChestMenu menu, Inventory inventory, Component title, Skin.Panel panel) {
        super(menu, inventory, title, 176, 114 + panel.rows * 18);
        this.rows = panel.rows;
        this.texture = RealmsOfChaos.id("textures/gui/screen_" + panel.art + ".png");
        // (the parchment half starts a little lower than a chest's does)
        this.inventoryLabelY = this.imageHeight - 91;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - this.imageWidth) / 2, top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0.0F, 0.0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // the name on the header in the panel's own ink, the inventory's on the parchment in a dark one
        // (a name too long for the header stops short of the emblem in its corner)
        if (this.font.width(this.title) > 148) graphics.text(this.font, RocHud.fit(this.font, this.title.getString(), 148), this.titleLabelX, this.titleLabelY, 0xFFF5E6C8, false);
        else graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF5E6C8, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF4A3B28, false);
    }
}
