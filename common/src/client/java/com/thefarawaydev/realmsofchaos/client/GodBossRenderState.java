package com.thefarawaydev.realmsofchaos.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state carrying which god is being drawn and which phase of its trial it is in (together they select the skin). */
public class GodBossRenderState extends HumanoidRenderState {
    public int godIndex;
    public int phase = 1;
}
