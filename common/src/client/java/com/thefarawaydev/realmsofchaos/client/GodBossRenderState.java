package com.thefarawaydev.realmsofchaos.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state carrying which god is being drawn (selects the skin). */
public class GodBossRenderState extends HumanoidRenderState {
    public int godIndex;
}
