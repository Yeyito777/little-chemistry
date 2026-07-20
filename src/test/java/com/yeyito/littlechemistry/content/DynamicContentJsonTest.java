package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				0, 0, 0, null, null, DynamicCraftingUse.KEEP, 1600);
		DynamicContentDefinition definition = definition("staff", item);

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)));

		assertEquals(19, DynamicContentJson.CURRENT_FORMAT);
		assertEquals(DynamicContentJson.CURRENT_FORMAT, decoded.format());
		assertEquals(DynamicItemType.ITEM, decoded.definitions().getFirst().item().itemType());
		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
		assertEquals(DynamicCraftingUse.KEEP, decoded.definitions().getFirst().item().craftingUse());
		assertEquals(1600, decoded.definitions().getFirst().item().fuelBurnTicks());
		DynamicBehaviorCompiler.compile(decoded.definitions().getFirst().behaviorSource());
	}

	@Test
	void roundTripPreservesDescriptionAndMythicalRarity() {
		DynamicBlockDrops drops = new DynamicBlockDrops(List.of(
				new DynamicDropEntry(DynamicDropTargetKind.REGISTERED_ITEM, "minecraft:amethyst_shard",
						2, 4, 1.0, DynamicFortuneMode.ORE_LIKE),
				new DynamicDropEntry(DynamicDropTargetKind.REGISTERED_ITEM, "minecraft:diamond",
						1, 1, 0.1, DynamicFortuneMode.NONE)
		), true, true);
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.CRYSTAL, 4.0F, DynamicTool.PICKAXE, true, DynamicBlockShape.FULL_CUBE,
				true, Rarity.EPIC, 0, 0, 8, true, List.of(), drops);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "moon_crystal", "Moon Crystal",
				"A crystal that holds a sliver of moonlight.", DynamicRarity.MYTHICAL,
				0L, TEXTURE_HASH, null,
				null, null, block, null, null, DynamicBehaviorSource.completeLegacySource(null), null);

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition));
		DynamicContentDefinition decoded = DynamicContentJson.decode(encoded).definitions().getFirst();
		JsonObject formatFourteen = JsonParser.parseString(
				new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		formatFourteen.addProperty("format", 14);
		JsonObject formatFourteenDefinition = formatFourteen.getAsJsonArray("definitions").get(0).getAsJsonObject();
		formatFourteenDefinition.remove("customParticles");
		formatFourteenDefinition.getAsJsonObject("block").remove("drops");
		DynamicContentDefinition formatFourteenDecoded = DynamicContentJson.decode(
				formatFourteen.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();
		JsonObject dropBranchFormatFifteen = JsonParser.parseString(
				new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		dropBranchFormatFifteen.addProperty("format", 15);
		dropBranchFormatFifteen.getAsJsonArray("definitions").get(0).getAsJsonObject()
				.remove("customParticles");
		DynamicContentDefinition dropBranchDecoded = DynamicContentJson.decode(
				dropBranchFormatFifteen.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals("A crystal that holds a\nsliver of moonlight.", decoded.description());
		assertEquals(DynamicRarity.MYTHICAL, decoded.rarityTier());
		assertEquals(Rarity.EPIC, decoded.rarity());
		assertEquals(Rarity.EPIC, decoded.block().rarity());
		assertTrue(decoded.block().directional());
		assertEquals(drops, decoded.block().drops());
		assertTrue(formatFourteenDecoded.block().directional());
		assertEquals(List.of(), formatFourteenDecoded.customParticles());
		assertEquals(DynamicBlockDrops.DEFAULT, formatFourteenDecoded.block().drops());
		assertEquals(List.of(), dropBranchDecoded.customParticles());
		assertEquals(drops, dropBranchDecoded.block().drops());
	}

	@Test
	void legacyBlockWithoutDropRulesKeepsTheExistingSelfDrop() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 2.0F, DynamicTool.PICKAXE, false, DynamicBlockShape.FULL_CUBE,
				0, 0, 0, false, List.of());
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "legacy_block", "Legacy Block", 0L, TEXTURE_HASH,
				null, block, null, null, DynamicBehaviorSource.completeLegacySource(null));
		JsonObject legacy = JsonParser.parseString(new String(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition)), StandardCharsets.UTF_8)).getAsJsonObject();
		legacy.addProperty("format", 13);
		legacy.getAsJsonArray("definitions").get(0).getAsJsonObject().getAsJsonObject("block").remove("drops");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(DynamicBlockDrops.DEFAULT, decoded.block().drops());
	}

	@Test
	void persistedBlockSurvivesARegisteredDropTargetModBeingRemoved() {
		DynamicBlockDrops drops = new DynamicBlockDrops(List.of(new DynamicDropEntry(
				DynamicDropTargetKind.REGISTERED_ITEM, "removed_mod:lost_crystal",
				1, 1, 1.0, DynamicFortuneMode.NONE)), false, false);
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 2.0F, DynamicTool.PICKAXE, false, DynamicBlockShape.FULL_CUBE,
				false, Rarity.COMMON, 0, 0, 0, false, List.of(), drops);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "orphaned_ore", "Orphaned Ore", 0L, TEXTURE_HASH,
				null, block, null, null, DynamicBehaviorSource.completeLegacySource(null));

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition))).definitions().getFirst();

		assertEquals("removed_mod:lost_crystal", decoded.block().drops().entries().getFirst().target());
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
		legacyItem.remove("fuelBurnTicks");

		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8));

		assertEquals(DynamicHeldType.TOOL, decoded.definitions().getFirst().item().heldType());
		assertEquals(DynamicCraftingUse.CONSUME, decoded.definitions().getFirst().item().craftingUse());
		assertEquals(0, decoded.definitions().getFirst().item().fuelBurnTicks());
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
		legacyDefinition.getAsJsonObject("block").remove("directional");

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				legacy.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(DynamicBlockShape.SLAB, decoded.block().shape());
		assertFalse(decoded.block().directional());
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

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 4, List.of(definition));
		DynamicContentDefinition decoded = DynamicContentJson.decode(encoded).definitions().getFirst();
		JsonObject particleBranchFormat = JsonParser.parseString(
				new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		particleBranchFormat.addProperty("format", 14);
		JsonObject particleBranchBlock = particleBranchFormat.getAsJsonArray("definitions").get(0)
				.getAsJsonObject().getAsJsonObject("block");
		particleBranchBlock.remove("directional");
		particleBranchBlock.remove("drops");
		DynamicContentDefinition previousFormatDecoded = DynamicContentJson.decode(
				particleBranchFormat.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();
		JsonObject combinedFormatFifteen = JsonParser.parseString(
				new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		combinedFormatFifteen.addProperty("format", 15);
		combinedFormatFifteen.getAsJsonArray("definitions").get(0).getAsJsonObject()
				.getAsJsonObject("block").remove("drops");
		DynamicContentDefinition combinedFormatDecoded = DynamicContentJson.decode(
				combinedFormatFifteen.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();

		assertEquals(1, decoded.customParticles().size());
		assertEquals("embers", decoded.customParticles().getFirst().id());
		assertEquals(2, decoded.customParticles().getFirst().frames().size());
		assertEquals("FF804000", decoded.customParticles().getFirst().endColor());
		assertEquals("custom:embers", decoded.block().particles().getFirst().particle());
		assertEquals("embers", decoded.block().particles().getFirst().customParticleId());
		assertEquals("embers", previousFormatDecoded.customParticles().getFirst().id());
		assertFalse(previousFormatDecoded.block().directional());
		assertEquals(DynamicBlockDrops.DEFAULT, previousFormatDecoded.block().drops());
		assertEquals("embers", combinedFormatDecoded.customParticles().getFirst().id());
		assertEquals(DynamicBlockDrops.DEFAULT, combinedFormatDecoded.block().drops());
	}

	@Test
	void roundTripPreservesGeneratedEntityPropertiesModelAndParticles() {
		DynamicContentDefinition definition = entityDefinition(false, true);

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 3, List.of(definition));
		DynamicContentDefinition decoded = DynamicContentJson.decode(encoded).definitions().getFirst();

		assertEquals(DynamicContentType.ENTITY, decoded.type());
		assertEquals(DynamicEntityMovement.FLYING, decoded.entity().movement());
		assertEquals(DynamicEntityDisposition.HOSTILE, decoded.entity().disposition());
		assertEquals(40.0, decoded.entity().maxHealth());
		assertEquals(Identifier.parse("minecraft:amethyst_shard"), decoded.entity().drops().getFirst().item());
		assertEquals(DynamicEntityVisualProfile.CUSTOM, decoded.entityModel().profile());
		assertEquals(1, decoded.entityModel().elements().size());
		assertEquals("skin", decoded.entityModel().particleTexture());
		assertEquals("sparks", decoded.customParticles().getFirst().id());

		JsonObject legacyRoot = JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		legacyRoot.addProperty("format", 14);
		JsonObject legacyEntry = legacyRoot.getAsJsonArray("definitions").get(0).getAsJsonObject();
		legacyEntry.remove("customParticles");
		legacyEntry.add("entityModel", legacyEntry.getAsJsonObject("entityModel").getAsJsonObject("geometry").deepCopy());
		legacyEntry.getAsJsonObject("entity").addProperty("maxHealth", 2048.0);
		legacyEntry.getAsJsonObject("entity").addProperty("armor", 100.0);
		DynamicContentDefinition migrated = DynamicContentJson.decode(
				legacyRoot.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();
		assertEquals(DynamicEntityVisualProfile.CUSTOM, migrated.entityModel().profile());
		assertEquals(1, migrated.entityModel().elements().size());
		assertEquals(List.of(), migrated.customParticles());
		assertEquals(1024.0, migrated.entity().maxHealth());
		assertEquals(30.0, migrated.entity().armor());
	}

	@Test
	void roundTripPreservesAnimatedVanillaEntityProfileAndSkin() {
		DynamicContentDefinition definition = entityDefinition(true, false);
		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 4, List.of(definition));

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				encoded).definitions().getFirst();

		assertEquals(DynamicEntityVisualProfile.COW, decoded.entityModel().profile());
		assertTrue(decoded.entityModel().usesVanillaModel());
		assertNull(decoded.entityModel().geometry());
		assertEquals(64, decoded.entityModel().primaryTexture().texture().width());
		assertEquals("3".repeat(64), decoded.entityModel().primaryTexture().hash());

		JsonObject entityBranchFormatFifteen = JsonParser.parseString(
				new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		entityBranchFormatFifteen.addProperty("format", 15);
		DynamicContentDefinition migrated = DynamicContentJson.decode(
				entityBranchFormatFifteen.toString().getBytes(StandardCharsets.UTF_8)).definitions().getFirst();
		assertEquals(DynamicEntityVisualProfile.COW, migrated.entityModel().profile());
	}

	@Test
	void currentCatalogRoundTripKeepsWorkstationsAndEntitiesTogether() {
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.STONE, 2.0F, DynamicTool.PICKAXE, false, DynamicBlockShape.FULL_CUBE,
				0, 0, 0, false, List.of());
		DynamicContentDefinition workstation = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "separator", "Separator", "Separates mixed materials.",
				DynamicRarity.COMMON, 0L, TEXTURE_HASH, null, null, null,
				block, null, null, workstationBehaviorSource(), null, List.of(),
				DynamicWorkstationSpecTest.workstation(), null, null);
		DynamicContentDefinition entity = entityDefinition(true, false);

		byte[] encoded = DynamicContentJson.encode(UUID.randomUUID(), 9, List.of(workstation, entity));
		DynamicContentJson.Decoded decoded = DynamicContentJson.decode(encoded);

		assertEquals(19, decoded.format());
		assertEquals(2, decoded.definitions().size());
		assertEquals("separator", decoded.definitions().get(0).name());
		assertTrue(decoded.definitions().get(0).workstation().recipeDataSchema()
				.schema().getAsJsonObject("properties").has("duration_ticks"));
		assertEquals(DynamicEntityVisualProfile.COW, decoded.definitions().get(1).entityModel().profile());

		JsonObject formatEighteen = JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
		formatEighteen.addProperty("format", 18);
		formatEighteen.getAsJsonArray("definitions").remove(1);
		DynamicContentJson.Decoded migrated = DynamicContentJson.decode(
				formatEighteen.toString().getBytes(StandardCharsets.UTF_8));
		assertEquals("separator", migrated.definitions().get(0).name());
		assertEquals(DynamicWorkstationSpecTest.workstation(), migrated.definitions().get(0).workstation());
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

	private static DynamicContentDefinition entityDefinition(boolean vanillaProfile, boolean withParticles) {
		DynamicTextureSpec icon = rectangularTexture(16, 16, "00000000", "80D0FFFF");
		DynamicEntityProperties entity = new DynamicEntityProperties(
				DynamicEntityMovement.FLYING, DynamicEntityDisposition.HOSTILE,
				1.4F, 2.0F, 1.6F, 40.0, 0.35, 7.0, 4.0, 0.25, 48.0, 12, true,
				Identifier.parse("minecraft:entity.allay.ambient_with_item"),
				Identifier.parse("minecraft:entity.allay.hurt"),
				Identifier.parse("minecraft:entity.allay.death"),
				List.of(new DynamicEntityDrop(Identifier.parse("minecraft:amethyst_shard"), 1, 3, 0.75)));
		DynamicEntityModel model;
		if (vanillaProfile) {
			DynamicTextureSpec skin = rectangularTexture(64, 64, "203010FF", "C09060FF");
			model = DynamicEntityModel.vanilla(DynamicEntityVisualProfile.COW,
					new DynamicBlockTexture("skin", "3".repeat(64), skin));
		} else {
			DynamicTextureSpec skin = rectangularTexture(16, 16, "204060FF", "A0E0FFFF");
			java.util.EnumMap<Direction, DynamicBlockModelFace> faces = new java.util.EnumMap<>(Direction.class);
			for (Direction direction : Direction.values()) faces.put(direction, new DynamicBlockModelFace("skin", null));
			DynamicBlockModel geometry = new DynamicBlockModel(
					List.of(new DynamicBlockTexture("skin", "2".repeat(64), skin)), "skin", faces,
					List.of(new DynamicBlockModelElement(2, 0, 2, 14, 16, 14, false, faces)));
			model = new DynamicEntityModel(geometry);
		}
		List<DynamicParticleDefinition> particles = withParticles
				? List.of(testParticle("sparks")) : List.of();
		return new DynamicContentDefinition(
				DynamicContentType.ENTITY, vanillaProfile ? "amber_cow" : "sky_crystal",
				vanillaProfile ? "Amber Cow" : "Sky Crystal", "A generated living crystal.",
				DynamicRarity.RARE, 0L, TEXTURE_HASH, icon, null, null,
				null, null, null, DynamicBehaviorSource.completeLegacySource(null), null,
				particles, null, entity, model);
	}

	private static DynamicParticleDefinition testParticle(String id) {
		DynamicTextureSpec frame = rectangularTexture(8, 8, "00000000", "A0E0FFFF");
		return new DynamicParticleDefinition(id,
				List.of(new DynamicParticleFrame("4".repeat(64), frame)),
				1, true, 20, 0.2F, 0.05F, "FFFFFFFF", "80D0FFFF",
				0.0F, 0.96F, false, true, 0.02F);
	}

	private static String workstationBehaviorSource() {
		return """
				public final class GeneratedBehaviorImpl implements
						com.yeyito.littlechemistry.behavior.DynamicBehavior,
						com.yeyito.littlechemistry.behavior.WorkstationBehavior,
						com.yeyito.littlechemistry.behavior.WorkstationTickBehavior {
					public GeneratedBehaviorImpl() {}
					public com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest createWorkstationRecipe(
							com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) { return null; }
					public void workstationTick(
							com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {}
				}
				""";
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
