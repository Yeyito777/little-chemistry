package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicContentJson;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the exact textual visual source material attached to a recipe generation request.
 *
 * <p>This boundary is intentionally palette/rows-only. Installed PNG files are decoded internally by
 * {@link MinecraftReferenceExporter}, but neither those bytes nor a raster/image input are ever sent to the model. The
 * automatically supplied references therefore use the same representation that generated Java must author.</p>
 */
public final class RecipeVisualReferences {
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DIGEST_FIELD = "visualReferenceDigest";
	private static final ConcurrentHashMap<StaticIngredient, JsonObject> STATIC_INGREDIENTS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Identifier, JsonArray> NATIVE_ENTITY_PROFILES = new ConcurrentHashMap<>();

	private RecipeVisualReferences() {
	}

	static Bundle forRequest(JsonObject recipeContext) {
		Set<IngredientReference> ingredients = new LinkedHashSet<>();
		collectIngredientReferences(recipeContext, ingredients);
		Bundle bundle = build(ingredients);
		if (recipeContext != null && recipeContext.has(DIGEST_FIELD)) {
			String advertised = recipeContext.get(DIGEST_FIELD).getAsString();
			if (!bundle.digest().equals(advertised)) {
				throw new IllegalArgumentException("Recipe visual-reference digest no longer matches installed ingredient artwork");
			}
		}
		return bundle;
	}

	/** Returns the cache identity of the exact textual references that the listed ingredient stacks will supply. */
	public static String digestForStacks(List<ItemStack> stacks) {
		return build(ingredientReferences(stacks), true, true).digest();
	}

	/** Digest produced by the immediately preceding exporter, before native world-carrier sheets were automatic. */
	public static String legacyDigestWithoutWorldCarriersForStacks(List<ItemStack> stacks) {
		return build(ingredientReferences(stacks), false, false).digest();
	}

	/** Digest produced after world-carrier textures were supplied but before their native geometry/UV profiles were staged. */
	static String legacyDigestWithoutEntityProfilesForStacks(List<ItemStack> stacks) {
		return build(ingredientReferences(stacks), true, false).digest();
	}

	/** Migrates only a recognized previous exporter digest; unrelated installed-artwork changes remain stale. */
	public static String migrateCompatibleDigestForStacks(List<ItemStack> stacks, String storedDigest) {
		String current = digestForStacks(stacks);
		if (current.equals(storedDigest)) return current;
		return legacyDigestWithoutWorldCarriersForStacks(stacks).equals(storedDigest)
				|| legacyDigestWithoutEntityProfilesForStacks(stacks).equals(storedDigest) ? current : storedDigest;
	}

	private static Set<IngredientReference> ingredientReferences(List<ItemStack> stacks) {
		Set<IngredientReference> ingredients = new LinkedHashSet<>();
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) continue;
			Identifier itemId = stack.getItem().builtInRegistryHolder().unwrapKey()
					.map(key -> key.identifier()).orElseGet(() -> Identifier.fromNamespaceAndPath(
							LittleChemistry.MOD_ID, "unregistered_" + Integer.toUnsignedString(
									System.identityHashCode(stack.getItem()), 16)));
			Identifier dynamicId = DynamicContentObjects.CONTENT_ID == null
					? null : stack.get(DynamicContentObjects.CONTENT_ID);
			var equippable = stack.get(DataComponents.EQUIPPABLE);
			Identifier equipmentAssetId = equippable != null && equippable.assetId().isPresent()
					? equippable.assetId().get().identifier() : null;
			Identifier itemModelId = stack.get(DataComponents.ITEM_MODEL);
			ingredients.add(new IngredientReference(itemId, dynamicId, equipmentAssetId, itemModelId));
		}
		return ingredients;
	}

	private static Bundle build(Set<IngredientReference> ingredientSet) {
		return build(ingredientSet, true, true);
	}

	private static Bundle build(Set<IngredientReference> ingredientSet, boolean includeWorldCarriers,
			boolean includeEntityProfiles) {
		List<IngredientReference> ingredients = ingredientSet.stream()
				.sorted(Comparator.comparing(IngredientReference::cacheIdentity)).toList();
		JsonObject root = new JsonObject();
		root.addProperty("representation", "text-only indexed textures: RRGGBBAA palette plus hexadecimal rows");
		JsonArray entries = new JsonArray();
		for (IngredientReference ingredient : ingredients) {
			entries.add(encodeIngredient(ingredient, includeWorldCarriers));
		}
		root.add("ingredients", entries);
		JsonArray entityProfileIdentities = includeEntityProfiles
				? nativeEntityProfileIdentities(ingredients) : new JsonArray();
		JsonArray entityProfiles = includeEntityProfiles ? nativeEntityProfiles(ingredients) : new JsonArray();
		JsonObject canonical = root.deepCopy();
		// Cache identity uses declarative profile coordinates, never optional client-class decompilation output. This keeps
		// integrated and dedicated servers bit-identical while still staging any materialized model bundle that is available.
		if (!entityProfileIdentities.isEmpty()) canonical.add("nativeEntityProfiles", entityProfileIdentities);
		String digest = digest(canonical.toString());
		String section = ingredients.isEmpty() ? "" : """

				## Automatically supplied recipe-item texture references
				The following visual source data was collected from every provided recipe item before this turn. It is already in the
				exact RRGGBBAA palette and hexadecimal-row format generated Java must submit. Start from the closest carrier rows and
				UV layout instead of redrawing its silhouette from memory; use the other ingredient textures for material, palette,
				and motif edits. You may search reference/vanilla/TEXTURES.txt and call read_texture for additional installed
				textures when the intended result needs a related entity sheet, animation family, block state, or other inspiration.

				""" + PRETTY_GSON.toJson(root) + "\n";
		String entitySection = entityProfiles.isEmpty() ? "" : """

				## Focused native entity texture/UV references
				These references pair exact text-only world texture pixels with the native cuboids, inherited pivots/rotations,
				and per-face UV rectangles that consume those pixels. Suggested generated bounds translate the native profile into
				Little Chemistry's 0-16 model space. Treat them as faithful source material, not a requirement to clone vanilla.

				""" + PRETTY_GSON.toJson(entityProfiles) + "\n";
		return new Bundle(section, digest, entitySection);
	}

	private static JsonArray nativeEntityProfileIdentities(List<IngredientReference> ingredients) {
		JsonArray result = new JsonArray();
		Set<String> seen = new java.util.TreeSet<>();
		for (IngredientReference ingredient : ingredients) {
			for (MinecraftReferenceExporter.NativeEntityProfile profile
					: MinecraftReferenceExporter.nativeEntityProfiles(ingredient.itemId())) {
				String key = profile.texturePath() + "|" + profile.modelClass() + "|" + profile.factoryMethod();
				if (!seen.add(key)) continue;
				JsonObject encoded = new JsonObject();
				encoded.addProperty("texturePath", profile.texturePath());
				encoded.addProperty("modelClass", profile.modelClass());
				encoded.addProperty("factoryMethod", profile.factoryMethod());
				result.add(encoded);
			}
		}
		return result;
	}

	private static JsonArray nativeEntityProfiles(List<IngredientReference> ingredients) {
		JsonArray result = new JsonArray();
		Set<String> seen = new java.util.HashSet<>();
		for (IngredientReference ingredient : ingredients) {
			JsonArray profiles = NATIVE_ENTITY_PROFILES.computeIfAbsent(
					ingredient.itemId(), RecipeVisualReferences::loadNativeEntityProfiles);
			for (JsonElement element : profiles) {
				JsonObject profile = element.getAsJsonObject();
				String key = profile.get("texturePath").getAsString() + "|"
						+ profile.getAsJsonObject("nativeModel").get("modelClass").getAsString() + "|"
						+ profile.getAsJsonObject("nativeModel").get("factoryMethod").getAsString();
				if (seen.add(key)) result.add(profile.deepCopy());
			}
		}
		return result;
	}

	private static JsonArray loadNativeEntityProfiles(Identifier itemId) {
		JsonArray result = new JsonArray();
		for (MinecraftReferenceExporter.NativeEntityProfile profile
				: MinecraftReferenceExporter.nativeEntityProfiles(itemId)) {
			try {
				result.add(MinecraftReferenceExporter.materializeNativeEntityProfile(profile));
			} catch (Exception | LinkageError ignored) {
				// Dedicated or unusual mod runtimes may omit a client model class. The texture remains available and the
				// model can pair another indexed entity texture/source explicitly with read_entity_reference.
			}
		}
		return result;
	}

	private static JsonObject encodeIngredient(IngredientReference ingredient, boolean includeWorldCarriers) {
		if (ingredient.dynamicId() == null) {
			StaticIngredient installed = new StaticIngredient(
					ingredient.itemId(), ingredient.equipmentAssetId(), ingredient.itemModelId());
			return (includeWorldCarriers
					? STATIC_INGREDIENTS.computeIfAbsent(installed, RecipeVisualReferences::encodeInstalledIngredient)
					: encodeInstalledIngredient(installed, false)).deepCopy();
		}
		JsonObject output = new JsonObject();
		output.addProperty("ingredient", ingredient.identity());
		if (ingredient.equipmentAssetId() != null) {
			output.addProperty("equipmentAssetId", ingredient.equipmentAssetId().toString());
		}
		if (ingredient.itemModelId() != null) output.addProperty("itemModelId", ingredient.itemModelId().toString());
		JsonArray references = new JsonArray();
		if (ingredient.dynamicId() != null && LittleChemistry.MOD_ID.equals(ingredient.dynamicId().getNamespace())) {
			DynamicContentDefinition definition = DynamicContentCatalog.find(ingredient.dynamicId().getPath());
			if (definition != null) appendDynamicReferences(references, definition);
		}
		// Dynamic identity supplies its authored definition, while exact per-stack ITEM_MODEL/EQUIPPABLE overrides may add
		// installed layers of their own. Supplying both keeps exact-component recipe visuals composable.
		appendInstalledReferences(references, ingredient.itemId(), ingredient.equipmentAssetId(), ingredient.itemModelId(),
				includeWorldCarriers);
		output.add("textures", references);
		if (references.isEmpty()) {
			output.addProperty("note", "No conventional installed texture resource was available for this ingredient.");
		}
		return output;
	}

	private static JsonObject encodeInstalledIngredient(StaticIngredient ingredient) {
		return encodeInstalledIngredient(ingredient, true);
	}

	private static JsonObject encodeInstalledIngredient(StaticIngredient ingredient, boolean includeWorldCarriers) {
		JsonObject output = new JsonObject();
		output.addProperty("ingredient", ingredient.itemId().toString());
		if (ingredient.equipmentAssetId() != null) {
			output.addProperty("equipmentAssetId", ingredient.equipmentAssetId().toString());
		}
		if (ingredient.itemModelId() != null) output.addProperty("itemModelId", ingredient.itemModelId().toString());
		JsonArray references = new JsonArray();
		appendInstalledReferences(references, ingredient.itemId(), ingredient.equipmentAssetId(), ingredient.itemModelId(),
				includeWorldCarriers);
		output.add("textures", references);
		if (references.isEmpty()) {
			output.addProperty("note", "No conventional installed texture resource was available for this ingredient.");
		}
		return output;
	}

	private static void appendInstalledReferences(JsonArray references, Identifier itemId,
			Identifier equipmentAssetId, Identifier itemModelId, boolean includeWorldCarriers) {
		List<String> paths = includeWorldCarriers
				? MinecraftReferenceExporter.referencesForItem(itemId, equipmentAssetId, itemModelId)
				: MinecraftReferenceExporter.referencesForItemWithoutWorldCarriers(
						itemId, equipmentAssetId, itemModelId);
		for (String path : paths) {
			try {
				JsonObject reference = JsonParser.parseString(MinecraftReferenceExporter.materialize(path)).getAsJsonObject();
				reference.addProperty("referencePath", "reference/vanilla/" + path);
				references.add(reference);
			} catch (Exception ignored) {
				// Missing/broken optional mod resources are omitted; a raster fallback is deliberately impossible.
			}
		}
	}

	private static void appendDynamicReferences(JsonArray references, DynamicContentDefinition definition) {
		addTexture(references, "generated base/inventory texture", definition.texture());
		definition.itemVisuals().states().forEach(state ->
				addTexture(references, "generated item state " + state.id(), state.texture()));
		if (definition.armorDisplayTexture() != null) {
			JsonObject equipped = addTexture(references, "generated equipped armor texture", definition.armorDisplayTexture());
			if (definition.armorGeometry() != null) {
				equipped.add("authoredGeometry", DynamicContentJson.encodeArmorGeometry(definition.armorGeometry()));
			}
		}
		if (definition.blockModel() != null) definition.blockModel().textures().forEach(texture ->
				addTexture(references, "generated block model texture " + texture.id(), texture.texture()));
		if (definition.entityModel() != null) definition.entityModel().textures().forEach(texture ->
				addTexture(references, "generated entity model texture " + texture.id(), texture.texture()));
	}

	private static JsonObject addTexture(JsonArray references, String role, DynamicTextureSpec texture) {
		return addTexture(references, role, texture.width(), texture.height(), texture.palette(), texture.rows());
	}

	private static JsonObject addTexture(JsonArray references, String role, DynamicArmorDisplayTextureSpec texture) {
		return addTexture(references, role, 64, 32, texture.palette(), texture.rows());
	}

	private static JsonObject addTexture(JsonArray references, String role, int width, int height,
			List<String> paletteValues, List<String> rowValues) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("role", role);
		encoded.addProperty("width", width);
		encoded.addProperty("height", height);
		JsonArray palette = new JsonArray();
		paletteValues.forEach(palette::add);
		encoded.add("palette", palette);
		JsonArray rows = new JsonArray();
		rowValues.forEach(rows::add);
		encoded.add("rows", rows);
		references.add(encoded);
		return encoded;
	}

	private static void collectIngredientReferences(JsonElement element, Set<IngredientReference> result) {
		if (element == null || element.isJsonNull()) return;
		if (element instanceof JsonArray array) {
			for (JsonElement child : array) collectIngredientReferences(child, result);
			return;
		}
		if (!(element instanceof JsonObject object)) return;
		if (object.has("itemId") && object.get("itemId").isJsonPrimitive()) {
			try {
				Identifier itemId = Identifier.parse(object.get("itemId").getAsString());
				Identifier dynamicId = object.has("dynamicContentId")
						? Identifier.parse(object.get("dynamicContentId").getAsString()) : null;
				Identifier equipmentAssetId = object.has("equipmentAssetId")
						? Identifier.parse(object.get("equipmentAssetId").getAsString()) : null;
				Identifier itemModelId = object.has("itemModelId")
						? Identifier.parse(object.get("itemModelId").getAsString()) : null;
				result.add(new IngredientReference(itemId, dynamicId, equipmentAssetId, itemModelId));
			} catch (RuntimeException ignored) {
				// Request validation owns malformed recipe values; visual collection only handles valid resource IDs.
			}
		}
		for (var entry : object.entrySet()) collectIngredientReferences(entry.getValue(), result);
	}

	private static String digest(String canonical) {
		try {
			return java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	static record Bundle(String promptSection, String digest, String entityReferenceSection) {
	}

	private record IngredientReference(Identifier itemId, Identifier dynamicId, Identifier equipmentAssetId,
			Identifier itemModelId) {
		private IngredientReference {
			if (itemId == null) throw new IllegalArgumentException("Ingredient item ID is required");
		}

		String identity() {
			return (dynamicId == null ? itemId : dynamicId).toString().toLowerCase(Locale.ROOT);
		}

		String cacheIdentity() {
			return identity() + (equipmentAssetId == null ? "" : "|equipment=" + equipmentAssetId)
					+ (itemModelId == null ? "" : "|model=" + itemModelId);
		}

		@Override public boolean equals(Object other) {
			return other instanceof IngredientReference reference && cacheIdentity().equals(reference.cacheIdentity());
		}

		@Override public int hashCode() {
			return cacheIdentity().hashCode();
		}
	}

	private record StaticIngredient(Identifier itemId, Identifier equipmentAssetId, Identifier itemModelId) {
	}
}
