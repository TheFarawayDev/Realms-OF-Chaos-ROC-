package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws the passages' sentinels as humanoids, each kind with its own generated skin. The model is the player's, so a
 * skin's outer layer - jacket, sleeves, trouser legs and hat - is drawn over it: armour, cloaks and sashes stand proud.
 */
public class SentinelRenderer extends HumanoidMobRenderer<Sentinel, SentinelRenderState, HumanoidModel<SentinelRenderState>> {

    public SentinelRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public SentinelRenderState createRenderState() {
        return new SentinelRenderState();
    }

    @Override
    public void extractRenderState(Sentinel entity, SentinelRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind().id();
    }

    @Override
    public Identifier getTextureLocation(SentinelRenderState state) {
        return RealmsOfChaos.id("textures/entity/sentinel/" + state.kind + ".png");
    }
}
