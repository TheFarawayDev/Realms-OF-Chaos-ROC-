package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.Gods;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/** Draws every god boss as a humanoid with its own generated skin - one for each phase of its trial. */
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
        state.phase = entity.phase();
    }

    /** While a god winds up its ability both arms are thrown up, as if calling down its power. */
    @Override
    protected HumanoidModel.ArmPose getArmPose(GodBoss mob, HumanoidArm arm) {
        return mob.isCasting() ? HumanoidModel.ArmPose.THROW_TRIDENT : super.getArmPose(mob, arm);
    }

    @Override
    public Identifier getTextureLocation(GodBossRenderState state) {
        // a god looks different in each phase of its trial: its aegis lights it up, its wrath cracks it open
        String phase = state.phase >= 3 ? "_p3" : state.phase == 2 ? "_p2" : "";
        return RealmsOfChaos.id("textures/entity/god/" + Gods.byIndex(state.godIndex).id() + phase + ".png");
    }
}
