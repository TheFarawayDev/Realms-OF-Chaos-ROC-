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
        // staff bolts, thrown relics and tarot cards are drawn as the item they carry
        event.registerEntityRenderer(ModContent.RELIC_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
}
