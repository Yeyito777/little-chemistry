package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeVisualReferencesTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		if (!Items.OAK_BOAT.builtInRegistryHolder().areComponentsBound()) {
			Items.OAK_BOAT.builtInRegistryHolder().bindComponents(
					DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
		}
	}

	@Test
	void exactReferenceBytesProduceAStableValidatedRequestDigest() {
		var leather = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:leather"}]}
				""").getAsJsonObject();
		var string = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:string"}]}
				""").getAsJsonObject();

		RecipeVisualReferences.Bundle leatherReferences = RecipeVisualReferences.forRequest(leather);
		String leatherDigest = leatherReferences.digest();
		String stringDigest = RecipeVisualReferences.forRequest(string).digest();
		assertFalse(leatherReferences.promptSection().contains("minecraft:item/leather_boots"));
		assertTrue(leatherReferences.entityReferenceSection().isEmpty());
		assertNotEquals(leatherDigest, stringDigest);

		leather.addProperty("visualReferenceDigest", leatherDigest);
		assertDoesNotThrow(() -> RecipeVisualReferences.forRequest(leather));
		leather.addProperty("visualReferenceDigest", stringDigest);
		assertThrows(IllegalArgumentException.class, () -> RecipeVisualReferences.forRequest(leather));
	}

	@Test
	void anExplicitEquippableAssetSuppliesEveryAssociatedTextualLayer() {
		var recipe = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:golden_nautilus_armor",
				          "equipmentAssetId":"minecraft:gold"}]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();

		assertTrue(section.contains("minecraft:item/golden_nautilus_armor"));
		assertTrue(section.contains("minecraft:entity/equipment/humanoid/gold"));
		assertTrue(section.contains("minecraft:entity/equipment/nautilus_body/gold"));
		assertTrue(section.contains("\"tiles\""));
	}

	@Test
	void nativeBoatIngredientsSupplyTheirWorldRenderSheetTextually() {
		var recipe = JsonParser.parseString("""
				{"grid":[{"itemId":"minecraft:oak_boat"}]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();
		RecipeVisualReferences.Bundle bundle = RecipeVisualReferences.forRequest(recipe);

		assertTrue(section.contains("minecraft:item/oak_boat"));
		assertTrue(section.contains("minecraft:entity/boat/oak"));
		assertTrue(section.contains("\"tiles\""));
		assertFalse(section.contains("nativeModel"), "entity geometry belongs only in focused post-selection context");
		assertTrue(bundle.entityReferenceSection().contains("net.minecraft.client.model.object.boat.BoatModel"));
		assertTrue(bundle.entityReferenceSection().contains("createBoatModel"));
		assertTrue(bundle.entityReferenceSection().contains("pixelUv"));
		assertTrue(bundle.entityReferenceSection().contains("suggestedGeneratedBounds"));
	}

	@Test
	void chestBoatAndChestRaftIngredientsUseTheirActualWorldRenderSheets() {
		var recipe = JsonParser.parseString("""
				{"grid":[
				  {"itemId":"minecraft:oak_chest_boat"},
				  {"itemId":"minecraft:bamboo_chest_raft"}
				]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();

		assertTrue(section.contains("minecraft:entity/chest_boat/oak"));
		assertTrue(section.contains("minecraft:entity/chest_boat/bamboo"));
		assertFalse(section.contains("minecraft:entity/boat/oak"));
	}

	@Test
	void nativeCarrierProfilesPreserveInstalledModNamespaces() {
		var profile = MinecraftReferenceExporter.nativeEntityProfiles(
				Identifier.fromNamespaceAndPath("example_mod", "cedar_chest_boat")).getFirst();

		assertTrue(profile.texturePath().equals("example_mod/entity/chest_boat/cedar.json"));
		assertTrue(profile.modelClass().endsWith("BoatModel"));
		assertTrue(profile.factoryMethod().equals("createChestBoatModel"));
	}

	@Test
	void recipesRecognizeAndRewriteThePreWorldCarrierVisualDigest() {
		List<ItemStack> ingredients = List.of(new ItemStack(Items.OAK_BOAT));
		String legacy = RecipeVisualReferences.legacyDigestWithoutWorldCarriersForStacks(ingredients);
		String previous = RecipeVisualReferences.legacyDigestWithoutEntityProfilesForStacks(ingredients);
		String current = RecipeVisualReferences.digestForStacks(ingredients);

		String migrated = RecipeVisualReferences.migrateCompatibleDigestForStacks(ingredients, legacy);
		String migratedPrevious = RecipeVisualReferences.migrateCompatibleDigestForStacks(ingredients, previous);

		assertNotEquals(legacy, current);
		assertTrue(migrated.equals(current));
		assertNotEquals(previous, current);
		assertTrue(migratedPrevious.equals(current));
	}

	@Test
	void minecraftItemDefinitionGraphSuppliesBlockAndAnimatedStateTextures() {
		var recipe = JsonParser.parseString("""
				{"grid":[
				  {"itemId":"minecraft:furnace"},
				  {"itemId":"minecraft:fishing_rod"},
				  {"itemId":"minecraft:bundle"},
				  {"itemId":"minecraft:stick","itemModelId":"minecraft:brush"}
				]}
				""").getAsJsonObject();

		String section = RecipeVisualReferences.forRequest(recipe).promptSection();

		assertTrue(section.contains("minecraft:block/furnace_front"));
		assertTrue(section.contains("minecraft:item/fishing_rod_cast"));
		assertTrue(section.contains("minecraft:item/bundle_open_front"));
		assertTrue(section.contains("minecraft:item/bundle_open_back"));
		assertTrue(section.contains("\"itemModelId\": \"minecraft:brush\""));
		assertTrue(section.contains("minecraft:item/brush"));
	}
}
