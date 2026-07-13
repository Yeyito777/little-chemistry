package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.client.DynamicArmorAssets;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentAssetManager.class)
abstract class EquipmentAssetManagerMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$resolveDynamicArmor(ResourceKey<EquipmentAsset> assetKey,
			CallbackInfoReturnable<EquipmentClientInfo> callback) {
		EquipmentClientInfo info = DynamicArmorAssets.find(assetKey);
		if (info != null) callback.setReturnValue(info);
	}
}
