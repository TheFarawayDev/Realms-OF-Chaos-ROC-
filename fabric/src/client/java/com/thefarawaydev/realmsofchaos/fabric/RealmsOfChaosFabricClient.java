package com.thefarawaydev.realmsofchaos.fabric;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.client.GodBossRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client-side Fabric entrypoint: registers the god boss renderer. */
public class RealmsOfChaosFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // the mod's own particles, each drawn from its own sprites
        for (com.thefarawaydev.realmsofchaos.fx.RocParticles kind : com.thefarawaydev.realmsofchaos.fx.RocParticles.values()) {
            net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(kind.type(),
                    (net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.PendingParticleProvider<net.minecraft.core.particles.SimpleParticleType>)
                            sprites -> com.thefarawaydev.realmsofchaos.client.RocQuadParticle.provider(kind, sprites));
        }
        EntityRendererRegistry.register(ModContent.GOD_BOSS.get(), GodBossRenderer::new);
        EntityRendererRegistry.register(ModContent.SENTINEL.get(), com.thefarawaydev.realmsofchaos.client.SentinelRenderer::new);
        EntityRendererRegistry.register(ModContent.TITAN.get(), com.thefarawaydev.realmsofchaos.client.TitanRenderer::new);
        // staff bolts, thrown relics and tarot cards are drawn as the item they carry
        EntityRendererRegistry.register(ModContent.RELIC_PROJECTILE.get(), com.thefarawaydev.realmsofchaos.client.RelicProjectileRenderer::new);
        // each of the mod's menus has a screen of its own
        for (com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel : com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values()) {
            net.minecraft.client.gui.screens.MenuScreens.register(ModContent.MENUS.get(panel.id()).get(),
                    (net.minecraft.world.inventory.ChestMenu menu, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.network.chat.Component title) ->
                            new com.thefarawaydev.realmsofchaos.client.PanelScreen(menu, inventory, title, panel));
        }
        // and the written screens - the Codex, the handbook, the chronicle, the altar - are pages of the mod's own
        net.minecraft.client.gui.screens.MenuScreens.register(ModContent.PAGE.get(), com.thefarawaydev.realmsofchaos.client.PageScreen::new);
        // and the HUD is drawn by the mod: the boss panel, the floor plaque, cards, the combat meter
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(com.thefarawaydev.realmsofchaos.RealmsOfChaos.id("hud"),
                (graphics, delta) -> com.thefarawaydev.realmsofchaos.client.RocHud.draw(graphics));
    }
}
