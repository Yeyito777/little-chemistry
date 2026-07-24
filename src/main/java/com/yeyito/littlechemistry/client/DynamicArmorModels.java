package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicArmorAnchor;
import com.yeyito.littlechemistry.content.DynamicArmorGeometry;
import com.yeyito.littlechemistry.content.DynamicArmorGeometryPart;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Builds bounded AI-authored armor cuboids on Minecraft's normally animated humanoid anchors. */
public final class DynamicArmorModels {
	private static final Map<HumanoidModel<?>, Map<DynamicArmorGeometry, HumanoidModel<HumanoidRenderState>>> CACHE =
			new IdentityHashMap<>();

	private DynamicArmorModels() {
	}

	public static synchronized HumanoidModel<HumanoidRenderState> find(ItemStack stack, HumanoidModel<?> selectedModel) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		DynamicArmorGeometry geometry = definition == null ? null : definition.armorGeometry();
		if (geometry == null) return null;
		return CACHE.computeIfAbsent(selectedModel, ignored -> new HashMap<>())
				.computeIfAbsent(geometry, ignored -> build(geometry, selectedModel));
	}

	static HumanoidModel<HumanoidRenderState> build(DynamicArmorGeometry geometry, HumanoidModel<?> selectedModel) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		EnumMap<DynamicArmorAnchor, PartDefinition> anchors = new EnumMap<>(DynamicArmorAnchor.class);
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		anchors.put(DynamicArmorAnchor.HEAD, head);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		anchors.put(DynamicArmorAnchor.BODY, root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO));
		anchors.put(DynamicArmorAnchor.RIGHT_ARM, root.addOrReplaceChild(
				"right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F)));
		anchors.put(DynamicArmorAnchor.LEFT_ARM, root.addOrReplaceChild(
				"left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F)));
		anchors.put(DynamicArmorAnchor.RIGHT_LEG, root.addOrReplaceChild(
				"right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F)));
		anchors.put(DynamicArmorAnchor.LEFT_LEG, root.addOrReplaceChild(
				"left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F)));

		for (DynamicArmorGeometryPart part : geometry.parts()) {
			CubeListBuilder cube = CubeListBuilder.create().texOffs(part.textureU(), part.textureV())
					.mirror(part.mirror())
					.addBox(part.x(), part.y(), part.z(), part.width(), part.height(), part.depth(),
							new CubeDeformation(part.dilation()));
			anchors.get(part.anchor()).addOrReplaceChild("little_chemistry_" + part.id(), cube,
					PartPose.offsetAndRotation(part.pivotX(), part.pivotY(), part.pivotZ(),
							(float) Math.toRadians(part.pitch()), (float) Math.toRadians(part.yaw()),
							(float) Math.toRadians(part.roll())));
		}
		return new AnchoredArmorModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), selectedModel);
	}

	public static synchronized void clear() {
		CACHE.clear();
	}

	private static final class AnchoredArmorModel extends HumanoidModel<HumanoidRenderState> {
		private final HumanoidModel<HumanoidRenderState> selectedModel;

		@SuppressWarnings("unchecked")
		private AnchoredArmorModel(net.minecraft.client.model.geom.ModelPart root, HumanoidModel<?> selectedModel) {
			super(root);
			this.selectedModel = (HumanoidModel<HumanoidRenderState>) selectedModel;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			selectedModel.setupAnim(state);
			copyPose(selectedModel.root(), root());
			copyPose(selectedModel.head, head);
			copyPose(selectedModel.hat, hat);
			copyPose(selectedModel.body, body);
			copyPose(selectedModel.rightArm, rightArm);
			copyPose(selectedModel.leftArm, leftArm);
			copyPose(selectedModel.rightLeg, rightLeg);
			copyPose(selectedModel.leftLeg, leftLeg);
		}

		private static void copyPose(net.minecraft.client.model.geom.ModelPart source,
				net.minecraft.client.model.geom.ModelPart target) {
			target.x = source.x; target.y = source.y; target.z = source.z;
			target.xRot = source.xRot; target.yRot = source.yRot; target.zRot = source.zRot;
			target.xScale = source.xScale; target.yScale = source.yScale; target.zScale = source.zScale;
			target.visible = source.visible; target.skipDraw = source.skipDraw;
		}
	}
}
