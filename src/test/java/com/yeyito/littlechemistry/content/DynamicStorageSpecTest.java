package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DynamicStorageSpecTest {
	@Test
	void validatesNativeChestRowBounds() {
		assertEquals(27, new DynamicStorageSpec(3).slots());
		assertThrows(IllegalArgumentException.class, () -> new DynamicStorageSpec(0));
		assertThrows(IllegalArgumentException.class, () -> new DynamicStorageSpec(7));
	}

	@Test
	void currentCatalogPersistsStorageWhileFormat22RemainsCompatible() {
		DynamicTextureSpec texture = texture();
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "field_satchel", "Field Satchel", "Carries supplies.",
				DynamicRarity.COMMON, 0L, "0".repeat(64), texture, null, null,
				null, DynamicItemProperties.ordinary(1, Rarity.COMMON, false, 0, 0), null,
				DynamicBehaviorSource.completeLegacySource(null), null, List.of(), null, null, null,
				DynamicItemVisuals.NONE, new DynamicStorageSpec(2));

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition));
		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(encoded);

		assertEquals(23, decoded.format());
		assertEquals(new DynamicStorageSpec(2), decoded.definitions().getFirst().storage());

		var legacyJson = com.google.gson.JsonParser.parseString(
				new String(encoded, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		legacyJson.addProperty("format", 22);
		legacyJson.getAsJsonArray("definitions").get(0).getAsJsonObject().remove("storage");
		String legacy = legacyJson.toString();
		DynamicContentDefinition legacyDefinition = DynamicContentJson.decode(
				legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8)).definitions().getFirst();
		assertNull(legacyDefinition.storage());
	}

	@Test
	void portableStorageMustUseARegularMaxStackOneItem() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
				new GeneratedContentSpec(texture(), null, DynamicItemProperties.DEFAULT, null, null,
						DynamicBehaviorSource.completeLegacySource(null), null, DynamicRarity.COMMON,
						"Storage.", List.of(), null, null, null, DynamicItemVisuals.NONE,
						new DynamicStorageSpec(1)));
		assertNotNull(failure.getMessage());
	}

	private static DynamicTextureSpec texture() {
		return new DynamicTextureSpec(List.of("00000000", "805020FF", "D0A060FF"), List.of(
				"0000000000000000", "0000000110000000", "0000011221100000", "0000112222110000",
				"0001122222211000", "0011221111221100", "0011211111121100", "0011212222121100",
				"0011212222121100", "0011211111121100", "0011222222221100", "0001122222211000",
				"0000111111110000", "0000011111100000", "0000000000000000", "0000000000000000"));
	}
}
