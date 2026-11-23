package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Titan;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/** Draws a fallen Titan: half as big again as a man, in its own skin for each phase of its fight, arms raised as it calls a move. */
public class TitanRenderer extends HumanoidMobRenderer<Titan, TitanRenderer.State, HumanoidModel<TitanRenderer.State>> {
    public static class State extends HumanoidRenderState {
        public int kind;
        public int phase = 1;
    }

    public TitanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.9F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Titan entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind().ordinal();
        state.phase = entity.phase();
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Titan mob, HumanoidArm arm) {
        return mob.isCasting() ? HumanoidModel.ArmPose.THROW_TRIDENT : super.getArmPose(mob, arm);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        String phase = state.phase >= 3 ? "_p3" : state.phase == 2 ? "_p2" : "";
        return RealmsOfChaos.id("textures/entity/titan/" + Titan.Kind.values()[Math.max(0, Math.min(2, state.kind))].id() + phase + ".png");
    }
}
