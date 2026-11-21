package com.thefarawaydev.realmsofchaos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thefarawaydev.realmsofchaos.entity.RelicProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a bolt, a thrown relic or a thrown card as the item's own model lying along its flight - nose first, like
 * an arrow - and turning slowly about its length. (It used to be a flat picture that always faced the camera.)
 */
public class RelicProjectileRenderer extends EntityRenderer<RelicProjectile, RelicProjectileRenderState> {
    private final ItemModelResolver itemModelResolver;

    public RelicProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public RelicProjectileRenderState createRenderState() {
        return new RelicProjectileRenderState();
    }

    @Override
    public void extractRenderState(RelicProjectile entity, RelicProjectileRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.roll = (entity.tickCount + partialTicks) * 18.0F;
    }

    @Override
    public void submit(RelicProjectileRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // the same turn an arrow is given: the model's long axis (x) ends up along the line of flight
        pose.rotateDegrees(Axis.YP, state.yRot - 90.0F);
        pose.rotateDegrees(Axis.ZP, state.xRot);
        pose.rotateDegrees(Axis.XP, state.roll);
        pose.scale(0.9F, 0.9F, 0.9F);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
