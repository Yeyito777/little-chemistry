package com.yeyito.littlechemistry.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yeyito.littlechemistry.client.DynamicArmorModels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Substitutes authored geometry only for dynamic armor that explicitly opts into it; vanilla wrapping remains untouched. */
@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin {
	@Redirect(method = "renderArmorPiece", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers("
					+ "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
					+ "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
					+ "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
	private <S> void littleChemistry$useAuthoredArmorGeometry(EquipmentLayerRenderer renderer,
			EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> asset,
			Model<? super S> vanillaModel, S state, ItemStack stack, PoseStack poseStack,
			SubmitNodeCollector nodes, int light, int outlineColor) {
		if (state instanceof HumanoidRenderState humanoidState && !humanoidState.isBaby
				&& vanillaModel instanceof HumanoidModel<?> selectedModel) {
			var authored = DynamicArmorModels.find(stack, selectedModel);
			if (authored != null) {
				@SuppressWarnings("unchecked") Model<? super S> replacement = (Model<? super S>) authored;
				// Vanilla trim UVs describe the fixed armor shell and have no meaningful mapping onto arbitrary authored
				// cuboid nets. Preserve every other component and native render behavior, but omit that incompatible overlay.
				ItemStack renderedStack = stack;
				if (stack.has(DataComponents.TRIM)) {
					renderedStack = stack.copy();
					renderedStack.remove(DataComponents.TRIM);
				}
				renderer.renderLayers(layerType, asset, replacement, state, renderedStack,
						poseStack, nodes, light, outlineColor);
				return;
			}
		}
		renderer.renderLayers(layerType, asset, vanillaModel, state, stack, poseStack, nodes, light, outlineColor);
	}
}
