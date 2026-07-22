package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicCarrierBlock;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicPlacedShape;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
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
		state.model = definition == null ? null : definition.blockModel();
		if (state.textureHash != null && blockEntity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
			DynamicParticleTextures.remember(clientLevel, blockEntity.getBlockPos(), state.textureHash);
		}
		var placement = definition == null || definition.item() == null ? null : definition.item().placement();
		state.placedShape = placement == null ? null : placement.shape();
		state.visuallyEmissive = definition != null && definition.block() != null && definition.block().visuallyEmissive()
				|| placement != null && placement.visuallyEmissive();
		state.shape = definition == null || definition.block() == null
				? DynamicBlockShape.FULL_CUBE : definition.block().shape();
		state.facing = definition != null && definition.block() != null && definition.block().directional()
				? blockEntity.getBlockState().getValue(DynamicCarrierBlock.FACING)
				: Direction.NORTH;
		state.fenceNorth = state.shape == DynamicBlockShape.FENCE
				&& blockEntity.getLevel() != null
				&& DynamicCarrierBlock.connectsFence(blockEntity.getLevel(), blockEntity.getBlockPos(),
						DynamicCarrierBlock.orientFromNorth(Direction.NORTH, state.facing));
		state.fenceEast = state.shape == DynamicBlockShape.FENCE
				&& blockEntity.getLevel() != null
				&& DynamicCarrierBlock.connectsFence(blockEntity.getLevel(), blockEntity.getBlockPos(),
						DynamicCarrierBlock.orientFromNorth(Direction.EAST, state.facing));
		state.fenceSouth = state.shape == DynamicBlockShape.FENCE
				&& blockEntity.getLevel() != null
				&& DynamicCarrierBlock.connectsFence(blockEntity.getLevel(), blockEntity.getBlockPos(),
						DynamicCarrierBlock.orientFromNorth(Direction.SOUTH, state.facing));
		state.fenceWest = state.shape == DynamicBlockShape.FENCE
				&& blockEntity.getLevel() != null
				&& DynamicCarrierBlock.connectsFence(blockEntity.getLevel(), blockEntity.getBlockPos(),
						DynamicCarrierBlock.orientFromNorth(Direction.WEST, state.facing));
		if (state.visuallyEmissive) {
			Arrays.fill(state.faceLightCoords, LightCoordsUtil.FULL_BRIGHT);
			return;
		}
		if (blockEntity.getLevel() == null) {
			Arrays.fill(state.faceLightCoords, LightCoordsUtil.FULL_BRIGHT);
			return;
		}

		for (Direction direction : Direction.values()) {
			Direction worldDirection = DynamicCarrierBlock.orientFromNorth(direction, state.facing);
			int neighborLight = LightCoordsUtil.getLightCoords(
					blockEntity.getLevel(),
					blockEntity.getBlockPos().relative(worldDirection)
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
		boolean rotated = state.facing != Direction.NORTH;
		if (rotated) poseStack.pushPose();
		try {
			if (rotated) {
				poseStack.rotateAround(
						Axis.YP.rotationDegrees(180.0F - state.facing.toYRot()), 0.5F, 0.5F, 0.5F);
			}
			if (state.model == null) {
				Identifier texture = RuntimeTextureStore.texture(state.textureHash);
				nodes.order(0).submitCustomGeometry(
						poseStack,
						RenderTypes.entityCutout(texture),
						(pose, vertices) -> renderLegacyGeometry(state, pose, vertices)
				);
			} else {
				int order = 0;
				for (var texture : state.model.textures()) {
					String textureId = texture.id();
					nodes.order(order++).submitCustomGeometry(
							poseStack,
							RenderTypes.entityCutout(RuntimeTextureStore.texture(texture.hash())),
							(pose, vertices) -> renderModelGeometry(state, textureId, pose, vertices)
					);
				}
			}
			if (state.breakProgress != null) {
				int progress = Math.clamp(state.breakProgress.progress(), 0, ModelBakery.DESTROY_TYPES.size() - 1);
				int breakOrder = state.model == null ? 1 : state.model.textures().size();
				nodes.order(breakOrder).submitCustomGeometry(
						poseStack,
						ModelBakery.DESTROY_TYPES.get(progress),
						(pose, vertices) -> {
							VertexConsumer decal = new SheetedDecalTextureGenerator(
									vertices, state.breakProgress.cameraPose(), 1.0F);
							if (state.model == null) renderLegacyGeometry(state, pose, decal);
							else renderModelGeometry(state, null, pose, decal);
						}
				);
			}
		} finally {
			if (rotated) poseStack.popPose();
		}
	}

	private static void renderLegacyGeometry(DynamicBlockRenderState state, PoseStack.Pose pose, VertexConsumer vertices) {
		if (state.placedShape == DynamicPlacedShape.CROSS) {
			DynamicGeometry.cross(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
		} else if (state.placedShape == DynamicPlacedShape.TORCH) {
			DynamicGeometry.torch(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
		} else switch (state.shape) {
			case SLAB -> DynamicGeometry.slab(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
			case STAR -> DynamicGeometry.star(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
			case FENCE -> DynamicGeometry.fence(pose, vertices, state.faceLightCoords,
					OverlayTexture.NO_OVERLAY, state.fenceNorth, state.fenceEast,
					state.fenceSouth, state.fenceWest);
			default -> DynamicGeometry.cube(pose, vertices, state.faceLightCoords, OverlayTexture.NO_OVERLAY);
		}
	}

	private static void renderModelGeometry(DynamicBlockRenderState state, String textureFilter,
			PoseStack.Pose pose, VertexConsumer vertices) {
		DynamicBlockModelRenderer.render(state.shape, state.model, textureFilter, pose, vertices,
				state.faceLightCoords, OverlayTexture.NO_OVERLAY, state.fenceNorth, state.fenceEast,
				state.fenceSouth, state.fenceWest);
	}
}
