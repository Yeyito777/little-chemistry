package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicContentJsonTest {
	private static final String TEXTURE_HASH = "0".repeat(64);

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void roundTripPreservesToolPoseOnOrdinaryItem() {
		DynamicItemProperties item = new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.TOOL, 16, Rarity.UNCOMMON, false, 0, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, null, null, DynamicCraftingUse.KEEP);
		DynamicContentDefinition definition = definition("staff", item);

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)));

		assertEquals(DynamicContentJson.CURRENT_FORMAT, decoded.format());
		assertEquals(DynamicItemType.ITEM, decoded.definitions().getFirst().item().itemType());
		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
		assertEquals(DynamicCraftingUse.KEEP, decoded.definitions().getFirst().item().craftingUse());
		DynamicBehaviorCompiler.compile(decoded.definitions().getFirst().behaviorSource());
	}

	@Test
	void roundTripPreservesDescriptionAndMythicalRarity() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.CRYSTAL, 4.0F, DynamicTool.PICKAXE, true, DynamicBlockShape.FULL_CUBE,
				Rarity.EPIC, 0, 0, 8, true, List.of());
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "moon_crystal", "Moon Crystal",
				"A crystal that holds a sliver of moonlight.", DynamicRarity.MYTHICAL,
				0L, TEXTURE_HASH, null,
				null, null, block, null, null, DynamicBehaviorSource.completeLegacySource(null), null);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)))
				.definitions().getFirst();

		assertEquals("A crystal that holds a sliver of moonlight.", decoded.description());
		assertEquals(DynamicRarity.MYTHICAL, decoded.rarityTier());
		assertEquals(Rarity.EPIC, decoded.rarity());
		assertEquals(Rarity.EPIC, decoded.block().rarity());
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
		JsonObject legacyItem = legacy.getAsJsonArray("definitions").get(0).getAsJsonObject()
				.getAsJsonObject("item");
		legacyItem.remove("heldType");
		legacyItem.remove("craftingUse");

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8));

		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
		assertEquals(DynamicCraftingUse.CONSUME, decoded.definitions().getFirst().item().craftingUse());
	}

	@Test
	void roundTripPreservesArmorSlotAndProtection() {
		DynamicArmorProperties armor = new DynamicArmorProperties(
				DynamicArmorSlot.LEGGINGS, Rarity.RARE, true, 18, 6.0, 2.0, 0.1, 495);
		DynamicArmorDisplayTextureSpec displayTexture = displayTexture();
		String displayTextureHash = "1".repeat(64);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ARMOR, "star_leggings", "Star Leggings", 0L, TEXTURE_HASH,
				null, displayTextureHash, displayTexture, null, null, armor,
				DynamicBehaviorSource.completeLegacySource(null));

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
				null, null, null, armor, DynamicBehaviorSource.completeLegacySource(null));
		byte[] current = DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 6);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertNull(decoded.armorDisplayTexture());
		assertEquals(TEXTURE_HASH, decoded.effectiveArmorDisplayTextureHash());
	}

	@Test
	void legacyDefinitionWithoutBehaviorGetsAnExplicitCompilableClass() {
		byte[] current = DynamicContentJson.encode(
				UUID.randomUUID(), 1, List.of(definition("legacy_dust", DynamicItemProperties.DEFAULT)));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 7);
		legacy.getAsJsonArray("definitions").get(0).getAsJsonObject().remove("behaviorSource");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(DynamicBehaviorSource.completeLegacySource(null), decoded.behaviorSource());
		DynamicBehaviorCompiler.compile(decoded.behaviorSource());
	}

	@Test
	void formatEightMonolithicBehaviorMigratesToSelectedCapabilities() {
		byte[] current = DynamicContentJson.encode(
				UUID.randomUUID(), 1, List.of(definition("legacy_wand", DynamicItemProperties.DEFAULT)));
		JsonObject legacy = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 8);
		legacy.getAsJsonArray("definitions").get(0).getAsJsonObject().addProperty("behaviorSource", """
				import com.yeyito.littlechemistry.behavior.*;
				import net.minecraft.world.InteractionResult;
				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    @Override public InteractionResult useAir(DynamicItemUseContext context) {
				        return InteractionResult.SUCCESS;
				    }
				    @Override public InteractionResult useOnBlock(DynamicBlockUseContext context) {
				        return InteractionResult.PASS;
				    }
				}
				""");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(java.util.Set.of(DynamicBehaviorCapability.USE_AIR),
				DynamicBehaviorSource.capabilities(decoded.behaviorSource()));
		DynamicBehaviorCompiler.compile(decoded.behaviorSource());
	}

	@Test
	void formatNineCapabilityBehaviorAndSingleTextureBlocksRemainLoadable() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 1.5F, DynamicTool.PICKAXE, false, DynamicBlockShape.SLAB,
				0, 0, 0, false, List.of());
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "legacy_slab", "Legacy Slab", 0L, TEXTURE_HASH,
				null, block, null, null, DynamicBehaviorSource.completeLegacySource(null));
		JsonObject legacy = JsonParser.parseString(new String(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)), StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 9);
		JsonObject legacyDefinition = legacy.getAsJsonArray("definitions").get(0).getAsJsonObject();
		legacyDefinition.remove("description");
		legacyDefinition.remove("blockModel");
		legacyDefinition.getAsJsonObject("block").remove("rarity");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(DynamicBlockShape.SLAB, decoded.block().shape());
		assertEquals(Rarity.COMMON, decoded.rarity());
		assertEquals("", decoded.description());
		assertNull(decoded.blockModel());
		assertEquals(DynamicBehaviorSource.completeLegacySource(null), decoded.behaviorSource());
	}

	@Test
	void newBlockMeshesHaveStableSerializedNames() {
		assertEquals("star", DynamicBlockShape.STAR.serializedName());
		assertEquals(DynamicBlockShape.STAR, DynamicBlockShape.parse("star"));
		assertEquals("fence", DynamicBlockShape.FENCE.serializedName());
		assertEquals(DynamicBlockShape.FENCE, DynamicBlockShape.parse("fence"));
		assertEquals("custom", DynamicBlockShape.CUSTOM.serializedName());
	}

	@Test
	void roundTripPreservesMultiTextureBlockModelAndCustomElements() {
		DynamicTextureSpec stone = rectangularTexture(8, 8, "203040FF", "90B0D0FF");
		DynamicTextureSpec glow = rectangularTexture(32, 16, "402000FF", "FFD080FF");
		java.util.EnumMap<Direction, DynamicBlockModelFace> defaults = new java.util.EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) defaults.put(direction, new DynamicBlockModelFace("stone", null));
		defaults.put(Direction.UP, new DynamicBlockModelFace("glow", new DynamicBlockUv(0, 0, 16, 16)));
		java.util.EnumMap<Direction, DynamicBlockModelFace> elementFaces = new java.util.EnumMap<>(defaults);
		DynamicBlockModel model = new DynamicBlockModel(
				List.of(new DynamicBlockTexture("stone", TEXTURE_HASH, stone),
						new DynamicBlockTexture("glow", "2".repeat(64), glow)),
				"stone", defaults,
				List.of(new DynamicBlockModelElement(2, 0, 2, 14, 12, 14, true, elementFaces)));
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 2.0F, DynamicTool.PICKAXE, false, DynamicBlockShape.CUSTOM,
				0, 0, 0, false, List.of());
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "model_block", "Model Block", 0L, TEXTURE_HASH, stone,
				null, null, block, null, null, DynamicBehaviorSource.completeLegacySource(null), model);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition))).definitions().getFirst();

		assertEquals(2, decoded.blockModel().textures().size());
		assertEquals(32, decoded.blockModel().texture("glow").texture().width());
		assertEquals("glow", decoded.blockModel().faces().get(Direction.UP).texture());
		assertEquals(1, decoded.blockModel().elements().size());
		assertEquals(14.0F, decoded.blockModel().elements().getFirst().toX());
	}

	@Test
	void roundTripPreservesAuthoredParticlesAndCustomEmitterReferences() throws Exception {
		DynamicTextureSpec firstFrame = rectangularTexture(8, 8, "00000000", "FF8020FF");
		DynamicTextureSpec secondFrame = rectangularTexture(8, 8, "00000000", "FFF0A080");
		DynamicParticleDefinition particle = new DynamicParticleDefinition(
				"embers",
				List.of(
						new DynamicParticleFrame(DynamicTextureAsset.sha256(firstFrame.renderPng()), firstFrame),
						new DynamicParticleFrame(DynamicTextureAsset.sha256(secondFrame.renderPng()), secondFrame)),
				2, true, 30, 0.2F, 0.05F, "FFFFFFFF", "FF804000",
				-0.1F, 0.96F, false, true, 0.04F);
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.CRYSTAL, 2.0F, DynamicTool.PICKAXE, false, DynamicBlockShape.FULL_CUBE,
				0, 0, 7, true,
				List.of(new DynamicParticleEmitter("custom:embers", 0.1, 2, 0.04, true)));
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "ember_crystal", "Ember Crystal", "A crystal shedding tiny embers.",
				DynamicRarity.COMMON, 0L, TEXTURE_HASH, null, null, null,
				block, null, null, DynamicBehaviorSource.completeLegacySource(null), null, List.of(particle));

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 4, List.of(definition))).definitions().getFirst();

		assertEquals(1, decoded.customParticles().size());
		assertEquals("embers", decoded.customParticles().getFirst().id());
		assertEquals(2, decoded.customParticles().getFirst().frames().size());
		assertEquals("FF804000", decoded.customParticles().getFirst().endColor());
		assertEquals("custom:embers", decoded.block().particles().getFirst().particle());
		assertEquals("embers", decoded.block().particles().getFirst().customParticleId());
	}

	@Test
	void formatThirteenVanillaParticleEmitterRemainsLoadable() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 1.0F, DynamicTool.NONE, false, DynamicBlockShape.FULL_CUBE,
				0, 0, 0, false,
				List.of(new DynamicParticleEmitter(DynamicParticleType.SMOKE, 0.05, 1, 0.02, false)));
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "smoky_stone", "Smoky Stone", 0L, TEXTURE_HASH,
				null, block, null, null, DynamicBehaviorSource.completeLegacySource(null));
		JsonObject legacy = JsonParser.parseString(new String(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)), StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 13);
		JsonObject legacyDefinition = legacy.getAsJsonArray("definitions").get(0).getAsJsonObject();
		legacyDefinition.remove("customParticles");
		JsonObject emitter = legacyDefinition.getAsJsonObject("block").getAsJsonArray("particles")
				.get(0).getAsJsonObject();
		emitter.addProperty("type", emitter.remove("particle").getAsString());

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(DynamicParticleType.SMOKE, decoded.block().particles().getFirst().type());
		assertEquals(List.of(), decoded.customParticles());
	}

	private static DynamicContentDefinition definition(String name, DynamicItemProperties item) {
		return new DynamicContentDefinition(
				DynamicContentType.ITEM, name, name, 0L, TEXTURE_HASH, null, null, item, null,
				DynamicBehaviorSource.completeLegacySource(null));
	}

	private static DynamicArmorDisplayTextureSpec displayTexture() {
		List<String> rows = new java.util.ArrayList<>();
		for (int y = 0; y < 32; y++) rows.add("1".repeat(32) + "0".repeat(32));
		return new DynamicArmorDisplayTextureSpec(List.of("00000000", "80A0C0FF"), rows);
	}

	private static DynamicTextureSpec rectangularTexture(int width, int height, String dark, String light) {
		List<String> rows = new java.util.ArrayList<>();
		for (int y = 0; y < height; y++) {
			StringBuilder row = new StringBuilder(width);
			for (int x = 0; x < width; x++) row.append((x + y) % 2);
			rows.add(row.toString());
		}
		return new DynamicTextureSpec(List.of(dark, light), rows);
	}
}
