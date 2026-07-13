package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

		assertEquals(5, decoded.format());
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

	private static DynamicContentDefinition definition(String name, DynamicItemProperties item) {
		return new DynamicContentDefinition(
				DynamicContentType.ITEM, name, name, 0L, TEXTURE_HASH, null, null, item, null);
	}
}
