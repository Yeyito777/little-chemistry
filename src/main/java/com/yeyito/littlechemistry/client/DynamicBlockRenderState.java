package com.yeyito.littlechemistry.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public final class DynamicBlockRenderState extends BlockEntityRenderState {
	String textureHash;
	final int[] faceLightCoords = new int[Direction.values().length];
}
