package com.yeyito.littlechemistry.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicPlacedShape;

public final class DynamicBlockRenderState extends BlockEntityRenderState {
	String textureHash;
	DynamicBlockModel model;
	boolean visuallyEmissive;
	DynamicBlockShape shape = DynamicBlockShape.FULL_CUBE;
	DynamicPlacedShape placedShape;
	Direction facing = Direction.NORTH;
	boolean fenceNorth;
	boolean fenceEast;
	boolean fenceSouth;
	boolean fenceWest;
	final int[] faceLightCoords = new int[Direction.values().length];
}
