package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicContentJsonTest {
	private static final String TEXTURE_HASH = "0".repeat(64);

	@Test
	void roundTripPreservesToolPoseOnOrdinaryItem() {
		DynamicItemProperties item = new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.TOOL, 16, Rarity.UNCOMMON, false, 0, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, null, null);
		DynamicContentDefinition definition = definition("staff", item);

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)));

		assertEquals(7, decoded.format());
		assertEquals(DynamicItemType.ITEM, decoded.definitions().getFirst().item().itemType());
		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
	}

	@Test
	void legacyCatalogInfersHeldTypeFromGameplayType() {
		DynamicItemProperties tool = new DynamicItemProperties(
				DynamicItemType.TOOL, DynamicHeldType.REGULAR, 1, Rarity.COMMON, false, 0, 0.0,
				DynamicTool.SWORD, DynamicBreakingPower.IRON, 6.0F, 5.0, 1.6,
				250, 1, 1, null, null);
		byte[] current = DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition("blade", tool)));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 4);
		legacy.getAsJsonArray("definitions").get(0).getAsJsonObject()
				.getAsJsonObject("item").remove("heldType");

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8));

		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
	}

	@Test
	void roundTripPreservesArmorSlotAndProtection() {
		DynamicArmorProperties armor = new DynamicArmorProperties(
				DynamicArmorSlot.LEGGINGS, Rarity.RARE, true, 18, 6.0, 2.0, 0.1, 495);
		DynamicArmorDisplayTextureSpec displayTexture = displayTexture();
		String displayTextureHash = "1".repeat(64);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ARMOR, "star_leggings", "Star Leggings", 0L, TEXTURE_HASH,
				null, displayTextureHash, displayTexture, null, null, armor, null);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)))
				.definitions().getFirst();

		assertEquals(DynamicContentType.ARMOR, decoded.type());
		assertEquals(DynamicArmorSlot.LEGGINGS, decoded.armor().slot());
		assertEquals(6.0, decoded.armor().defense());
		assertEquals(495, decoded.armor().durability());
		assertEquals(displayTextureHash, decoded.armorDisplayTextureHash());
		assertEquals(displayTexture, decoded.armorDisplayTexture());
	}

	@Test
	void formatSixArmorRemainsLoadableWithoutSeparateDisplayTexture() {
		DynamicArmorProperties armor = DynamicArmorProperties.defaults(DynamicArmorSlot.HEAD);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ARMOR, "legacy_helmet", "Legacy Helmet", 0L, TEXTURE_HASH,
				null, null, null, armor, null);
		byte[] current = DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 6);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertNull(decoded.armorDisplayTexture());
		assertEquals(TEXTURE_HASH, decoded.effectiveArmorDisplayTextureHash());
	}

	@Test
	void newBlockMeshesHaveStableSerializedNames() {
		assertEquals("star", DynamicBlockShape.STAR.serializedName());
		assertEquals(DynamicBlockShape.STAR, DynamicBlockShape.parse("star"));
		assertEquals("fence", DynamicBlockShape.FENCE.serializedName());
		assertEquals(DynamicBlockShape.FENCE, DynamicBlockShape.parse("fence"));
	}

	private static DynamicContentDefinition definition(String name, DynamicItemProperties item) {
		return new DynamicContentDefinition(
				DynamicContentType.ITEM, name, name, 0L, TEXTURE_HASH, null, null, item, null, null);
	}

	private static DynamicArmorDisplayTextureSpec displayTexture() {
		List<String> rows = new java.util.ArrayList<>();
		for (int y = 0; y < 32; y++) rows.add("1".repeat(32) + "0".repeat(32));
		return new DynamicArmorDisplayTextureSpec(List.of("00000000", "80A0C0FF"), rows);
	}
}
