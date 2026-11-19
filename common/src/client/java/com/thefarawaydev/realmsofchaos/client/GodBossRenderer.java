package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.Gods;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** Draws every god boss as a humanoid with its own generated skin. */
public class GodBossRenderer extends HumanoidMobRenderer<GodBoss, GodBossRenderState, HumanoidModel<GodBossRenderState>> {

    public GodBossRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.6F);
    }

    @Override
    public GodBossRenderState createRenderState() {
        return new GodBossRenderState();
    }

    @Override
    public void extractRenderState(GodBoss entity, GodBossRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.godIndex = entity.godIndex();
    }

    @Override
    public Identifier getTextureLocation(GodBossRenderState state) {
        return RealmsOfChaos.id("textures/entity/god/" + Gods.byIndex(state.godIndex).id() + ".png");
    }
}
