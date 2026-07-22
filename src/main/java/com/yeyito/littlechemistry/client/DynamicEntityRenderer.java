package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yeyito.littlechemistry.content.DynamicCarrierEntity;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicEntityMovement;
import com.yeyito.littlechemistry.content.DynamicEntityVisualProfile;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.fish.CodModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Renders authored cuboids or a generated skin on one of the fixed animated vanilla model profiles. */
public final class DynamicEntityRenderer extends MobRenderer<DynamicCarrierEntity, DynamicEntityRenderState,
		EntityModel<? super DynamicEntityRenderState>> {
	private final EntityModel<DynamicEntityRenderState> emptyModel;
	private final Map<DynamicEntityVisualProfile, EntityModel<? super DynamicEntityRenderState>> vanillaModels;

	public DynamicEntityRenderer(EntityRendererProvider.Context context) {
		this(context, emptyModel());
	}

	private DynamicEntityRenderer(EntityRendererProvider.Context context,
			EntityModel<DynamicEntityRenderState> emptyModel) {
		super(context, emptyModel, 0.5F);
		this.emptyModel = emptyModel;
		EnumMap<DynamicEntityVisualProfile, EntityModel<? super DynamicEntityRenderState>> models =
				new EnumMap<>(DynamicEntityVisualProfile.class);
		models.put(DynamicEntityVisualProfile.ZOMBIE,
				new HumanoidModel<DynamicEntityRenderState>(context.bakeLayer(ModelLayers.ZOMBIE)));
		models.put(DynamicEntityVisualProfile.SKELETON,
				new HumanoidModel<DynamicEntityRenderState>(context.bakeLayer(ModelLayers.SKELETON)));
		models.put(DynamicEntityVisualProfile.ENDERMAN,
				new HumanoidModel<DynamicEntityRenderState>(context.bakeLayer(ModelLayers.ENDERMAN)));
		models.put(DynamicEntityVisualProfile.COW, new CowModel(context.bakeLayer(ModelLayers.COW)));
		models.put(DynamicEntityVisualProfile.PIG, new PigModel(context.bakeLayer(ModelLayers.PIG)));
		models.put(DynamicEntityVisualProfile.SPIDER, new SpiderModel(context.bakeLayer(ModelLayers.SPIDER)));
		models.put(DynamicEntityVisualProfile.CREEPER,
				new CreeperProfileModel(context.bakeLayer(ModelLayers.CREEPER)));
		models.put(DynamicEntityVisualProfile.BLAZE, new BlazeModel(context.bakeLayer(ModelLayers.BLAZE)));
		models.put(DynamicEntityVisualProfile.COD, new CodModel(context.bakeLayer(ModelLayers.COD)));
		this.vanillaModels = Map.copyOf(models);
	}

	@Override
	public void submit(DynamicEntityRenderState state, PoseStack poseStack, SubmitNodeCollector nodes,
			CameraRenderState camera) {
		var definition = DynamicContentCatalog.find(state.contentName);
		boolean ready = definition != null && definition.type() == DynamicContentType.ENTITY
				&& definition.entityModel() != null
				&& RuntimeTextureStore.areTexturesReady(definition.entityModel().textureHashes());
		EntityModel<? super DynamicEntityRenderState> selected = ready && state.profile.usesVanillaModel()
				? vanillaModels.get(state.profile) : emptyModel;
		this.model = selected == null ? emptyModel : selected;
		super.submit(state, poseStack, nodes, camera);
		if (!ready || state.profile.usesVanillaModel() || (state.isInvisible && !state.appearsGlowing())) return;

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
		if (state.flying) {
			poseStack.translate(0.0F, Mth.sin(state.ageInTicks * 0.15F) * 0.05F, 0.0F);
		}
		poseStack.scale(state.logicalWidth, state.logicalHeight, state.logicalWidth);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		int order = 0;
			for (var texture : definition.entityModel().textures()) {
				String textureId = texture.id();
				var textureLocation = RuntimeTextureStore.texture(texture.hash());
				if (!state.isInvisible) {
					nodes.order(order++).submitCustomGeometry(poseStack,
							RenderTypes.entityCutout(textureLocation),
							(pose, vertices) -> renderGeometry(definition.entityModel().geometry(), textureId,
									pose, vertices, state.lightCoords));
				} else if (!state.isInvisibleToPlayer) {
					nodes.order(order++).submitCustomGeometry(poseStack,
							RenderTypes.entityTranslucentCullItemTarget(textureLocation),
							(pose, vertices) -> renderGeometry(definition.entityModel().geometry(), textureId,
									pose, vertices, state.lightCoords));
				}
				if (state.appearsGlowing()) {
					nodes.order(order++).submitCustomGeometry(poseStack,
							RenderTypes.outline(textureLocation),
							(pose, vertices) -> renderGeometry(definition.entityModel().geometry(), textureId,
									pose, new OutlineVertexConsumer(vertices, state.outlineColor), state.lightCoords));
			}
		}
		poseStack.popPose();
	}

	@Override
	public Identifier getTextureLocation(DynamicEntityRenderState state) {
		return state.modelTextureHash.isEmpty()
				? MissingTextureAtlasSprite.getLocation()
				: RuntimeTextureStore.texture(state.modelTextureHash);
	}

	@Override
	public DynamicEntityRenderState createRenderState() {
		return new DynamicEntityRenderState();
	}

	@Override
	public void extractRenderState(DynamicCarrierEntity entity, DynamicEntityRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, this.itemModelResolver);
		state.contentName = entity.contentName();
		var definition = entity.definition();
		state.flying = definition != null && definition.entity().movement() == DynamicEntityMovement.FLYING;
		state.logicalWidth = definition == null ? 1.0F : definition.entity().width();
		state.logicalHeight = definition == null ? 1.0F : definition.entity().height();
		state.profile = definition == null || definition.entityModel() == null
				? DynamicEntityVisualProfile.CUSTOM : definition.entityModel().profile();
		state.modelTextureHash = definition == null || definition.entityModel() == null
				? "" : definition.entityModel().primaryTexture().hash();
	}

	@Override
	protected void scale(DynamicEntityRenderState state, PoseStack poseStack) {
		if (!state.profile.usesVanillaModel()) return;
		float horizontal = state.logicalWidth / state.profile.nativeWidth();
		float vertical = state.logicalHeight / state.profile.nativeHeight();
		poseStack.scale(horizontal, vertical, horizontal);
	}

	@Override
	protected float getShadowRadius(DynamicEntityRenderState state) {
		return Math.min(4.0F, state.logicalWidth * 0.5F);
	}

	private static EntityModel<DynamicEntityRenderState> emptyModel() {
		return new EntityModel<>(new ModelPart(List.of(), Map.of())) {
		};
	}

	private static void renderGeometry(com.yeyito.littlechemistry.content.DynamicBlockModel model,
			String textureId, PoseStack.Pose pose, VertexConsumer vertices, int light) {
		DynamicBlockModelRenderer.render(com.yeyito.littlechemistry.content.DynamicBlockShape.CUSTOM,
				model, textureId, pose, vertices, light, OverlayTexture.NO_OVERLAY, true, true, true, true);
	}

	/** Custom geometry nodes have no separate outline-color field, so carry the team's color in each vertex. */
	private record OutlineVertexConsumer(VertexConsumer delegate, int outlineColor) implements VertexConsumer {
		@Override public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(outlineColor);
			return this;
		}

		@Override public VertexConsumer setColor(int color) {
			delegate.setColor(outlineColor);
			return this;
		}

		@Override public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}

		@Override public VertexConsumer setLineWidth(float width) {
			delegate.setLineWidth(width);
			return this;
		}
	}

	/** The creeper layer has vanilla quadruped part names, so the shared gait applies directly. */
	private static final class CreeperProfileModel extends QuadrupedModel<DynamicEntityRenderState> {
		private CreeperProfileModel(ModelPart root) {
			super(root);
		}
	}
}
