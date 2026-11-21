package com.thefarawaydev.realmsofchaos.fabric;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.client.GodBossRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client-side Fabric entrypoint: registers the god boss renderer. */
public class RealmsOfChaosFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModContent.GOD_BOSS.get(), GodBossRenderer::new);
        EntityRendererRegistry.register(ModContent.SENTINEL.get(), com.thefarawaydev.realmsofchaos.client.SentinelRenderer::new);
        // staff bolts, thrown relics and tarot cards are drawn as the item they carry
        EntityRendererRegistry.register(ModContent.RELIC_PROJECTILE.get(), com.thefarawaydev.realmsofchaos.client.RelicProjectileRenderer::new);
    }
}
