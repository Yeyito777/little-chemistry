package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public final class DynamicBlockEntityRenderer implements BlockEntityRenderer<DynamicBlockEntity, DynamicBlockRenderState> {
	@Override
	public DynamicBlockRenderState createRenderState() {
		return new DynamicBlockRenderState();
	}

	@Override
	public void extractRenderState(DynamicBlockEntity blockEntity, DynamicBlockRenderState state,
			float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
		Identifier contentId = blockEntity.contentId();
		DynamicContentDefinition definition = contentId == null ? null : DynamicContentCatalog.find(contentId.getPath());
		state.textureHash = definition == null ? null : definition.textureHash();
		if (blockEntity.getLevel() == null) {
			Arrays.fill(state.faceLightCoords, LightCoordsUtil.FULL_BRIGHT);
			return;
		}

		for (Direction direction : Direction.values()) {
			int neighborLight = LightCoordsUtil.getLightCoords(
					blockEntity.getLevel(),
					blockEntity.getBlockPos().relative(direction)
			);
			state.faceLightCoords[direction.ordinal()] = LightCoordsUtil.max(state.lightCoords, neighborLight);
		}
	}

	@Override
	public void submit(DynamicBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
			CameraRenderState cameraState) {
		if (state.textureHash == null) {
			return;
		}
		Identifier texture = RuntimeTextureStore.texture(state.textureHash);
		nodes.order(0).submitCustomGeometry(
					poseStack,
					RenderTypes.entityCutout(texture),
					(pose, vertices) -> DynamicGeometry.cube(
							pose,
							vertices,
							state.faceLightCoords,
							OverlayTexture.NO_OVERLAY
					)
		);
	}
}
