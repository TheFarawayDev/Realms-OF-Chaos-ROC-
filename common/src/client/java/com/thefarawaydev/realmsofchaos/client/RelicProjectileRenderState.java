package com.thefarawaydev.realmsofchaos.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** What the bolt renderer needs: the item being flown, which way it is heading, and how far it has turned about its own length. */
public class RelicProjectileRenderState extends EntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float yRot, xRot, roll;
}
