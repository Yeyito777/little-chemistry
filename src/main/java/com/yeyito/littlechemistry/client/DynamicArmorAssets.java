package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicArmorAssets {
	private static final String ASSET_PREFIX = "dynamic/";
	private static final Map<Identifier, EquipmentClientInfo> INFO_BY_ASSET = new ConcurrentHashMap<>();

	private DynamicArmorAssets() {
	}

	public static EquipmentClientInfo find(ResourceKey<EquipmentAsset> assetKey) {
		Identifier id = assetKey.identifier();
		if (!LittleChemistry.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith(ASSET_PREFIX)) {
			return null;
		}
		String hash = id.getPath().substring(ASSET_PREFIX.length());
		if (!hash.matches("[a-f0-9]{64}")) return null;
		return INFO_BY_ASSET.computeIfAbsent(id, ignored -> EquipmentClientInfo.builder()
				.addHumanoidLayers(textureId(hash))
				.build());
	}

	public static Identifier textureId(String hash) {
		return LittleChemistry.id(ASSET_PREFIX + hash);
	}

	public static Identifier textureLocation(String hash, EquipmentClientInfo.LayerType layerType) {
		return new EquipmentClientInfo.Layer(textureId(hash)).getTextureLocation(layerType);
	}

	public static void clear() {
		INFO_BY_ASSET.clear();
	}
}
