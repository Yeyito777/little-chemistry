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
		DynamicContentDefinition definition = DynamicContentCatalog.find(contentId);
		state.textureHash = definition == null ? null : definition.textureHash();
		if (state.textureHash != null && blockEntity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
			DynamicParticleTextures.remember(clientLevel, blockEntity.getBlockPos(), state.textureHash);
		}
		var placement = definition == null || definition.item() == null ? null : definition.item().placement();
		state.placedShape = placement == null ? null : placement.shape();
		state.visuallyEmissive = definition != null && definition.block() != null && definition.block().visuallyEmissive()
				|| placement != null && placement.visuallyEmissive();
		state.shape = definition == null || definition.block() == null
				? com.yeyito.littlechemistry.content.DynamicBlockShape.FULL_CUBE : definition.block().shape();
		if (state.visuallyEmissive) {
			Arrays.fill(state.faceLightCoords, LightCoordsUtil.FULL_BRIGHT);
			return;
		}
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
						(pose, vertices) -> {
							if (state.placedShape == com.yeyito.littlechemistry.content.DynamicPlacedShape.CROSS) {
								DynamicGeometry.cross(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
							} else if (state.placedShape == com.yeyito.littlechemistry.content.DynamicPlacedShape.TORCH) {
								DynamicGeometry.torch(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
							} else if (state.shape == com.yeyito.littlechemistry.content.DynamicBlockShape.SLAB) {
								DynamicGeometry.slab(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
							} else {
								DynamicGeometry.cube(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
							}
						}
		);
	}
}
