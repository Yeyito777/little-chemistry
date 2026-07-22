package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicEntityVisualProfile;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render-thread snapshot for a logical generated carrier entity. */
public final class DynamicEntityRenderState extends HumanoidRenderState {
	public String contentName = "";
	public String modelTextureHash = "";
	public String visualTextureHash = "";
	public boolean visualTextureTranslucent;
	public DynamicEntityVisualProfile profile = DynamicEntityVisualProfile.CUSTOM;
	public boolean flying;
	public float logicalWidth = 1.0F;
	public float logicalHeight = 1.0F;
	public java.util.Map<String, String> synchronizedState = java.util.Map.of();
}
