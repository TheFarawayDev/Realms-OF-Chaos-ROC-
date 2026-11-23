package com.thefarawaydev.realmsofchaos.forge;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.client.GodBossRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration: the god boss renderer. */
@Mod.EventBusSubscriber(modid = RealmsOfChaos.MOD_ID, value = Dist.CLIENT)
public final class RealmsOfChaosForgeClient {
    private RealmsOfChaosForgeClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.GOD_BOSS.get(), GodBossRenderer::new);
        event.registerEntityRenderer(ModContent.SENTINEL.get(), com.thefarawaydev.realmsofchaos.client.SentinelRenderer::new);
        event.registerEntityRenderer(ModContent.TITAN.get(), com.thefarawaydev.realmsofchaos.client.TitanRenderer::new);
        // staff bolts, thrown relics and tarot cards are drawn as the item they carry
        event.registerEntityRenderer(ModContent.RELIC_PROJECTILE.get(), com.thefarawaydev.realmsofchaos.client.RelicProjectileRenderer::new);
        // each of the mod's menus has a screen of its own
        for (com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel : com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values()) {
            net.minecraft.client.gui.screens.MenuScreens.register(ModContent.MENUS.get(panel.id()).get(),
                    (net.minecraft.world.inventory.ChestMenu menu, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.network.chat.Component title) ->
                            new com.thefarawaydev.realmsofchaos.client.PanelScreen(menu, inventory, title, panel));
        }
        // and the written screens - the Codex, the handbook, the chronicle, the altar - are pages of the mod's own
        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.PAGE.get(), com.thefarawaydev.realmsofchaos.client.PageScreen::new);
    }

    /** The HUD is drawn by the mod: the boss panel, the floor plaque, cards, the combat meter. */
    @SubscribeEvent
    public static void addHud(net.minecraftforge.client.event.AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(RealmsOfChaos.id("hud"), (graphics, delta) -> com.thefarawaydev.realmsofchaos.client.RocHud.draw(graphics));
    }
}
