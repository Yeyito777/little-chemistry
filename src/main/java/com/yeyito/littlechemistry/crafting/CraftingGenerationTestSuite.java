package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A data-driven batch of crafting grids used to replay a manual invention test run. */
public record CraftingGenerationTestSuite(int number, String name, List<RecipeCase> recipes) {
	private static final int FORMAT = 1;
	private static final int MAX_RECIPES = 32;

	public CraftingGenerationTestSuite {
		if (number < 1) throw new IllegalArgumentException("Test suite number must be positive");
		if (name == null || name.isBlank()) throw new IllegalArgumentException("Test suite name is required");
		recipes = List.copyOf(recipes);
		if (recipes.isEmpty() || recipes.size() > MAX_RECIPES) {
			throw new IllegalArgumentException("Test suites must contain 1-" + MAX_RECIPES + " recipes");
		}
		Set<String> labels = new HashSet<>();
		for (RecipeCase recipe : recipes) {
			if (!labels.add(recipe.label())) throw new IllegalArgumentException("Duplicate test recipe label: " + recipe.label());
		}
	}

	public static CraftingGenerationTestSuite load(int number) throws IOException {
		if (number < 1) throw new IllegalArgumentException("Test suite number must be positive");
		String path = "/data/little_chemistry/generation_test/" + number + ".json";
		try (InputStream stream = CraftingGenerationTestSuite.class.getResourceAsStream(path)) {
			if (stream == null) throw new IOException("No Little Chemistry generation test suite " + number + " exists");
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!(parsed instanceof JsonObject root)) throw new IOException("Generation test suite must be a JSON object");
				return decode(number, root);
			}
		} catch (IllegalArgumentException invalid) {
			throw new IOException("Invalid Little Chemistry generation test suite " + number + ": "
					+ invalid.getMessage(), invalid);
		}
	}

	static CraftingGenerationTestSuite decode(int number, JsonObject root) {
		int format = requiredInt(root, "format");
		if (format != FORMAT) throw new IllegalArgumentException("Unsupported format " + format);
		String name = requiredString(root, "name");
		JsonArray encodedRecipes = requiredArray(root, "recipes");
		List<RecipeCase> recipes = new ArrayList<>(encodedRecipes.size());
		for (int index = 0; index < encodedRecipes.size(); index++) {
			if (!(encodedRecipes.get(index) instanceof JsonObject encoded)) {
				throw new IllegalArgumentException("Recipe " + index + " must be an object");
			}
			String label = requiredString(encoded, "label");
			int width = requiredInt(encoded, "width");
			int height = requiredInt(encoded, "height");
			if (width < 1 || width > 3 || height < 1 || height > 3) {
				throw new IllegalArgumentException("Recipe " + label + " dimensions must be between 1 and 3");
			}
			JsonArray encodedIngredients = requiredArray(encoded, "ingredients");
			if (encodedIngredients.size() != width * height) {
				throw new IllegalArgumentException("Recipe " + label + " needs exactly " + width * height + " ingredients");
			}
			List<Identifier> ingredients = new ArrayList<>(encodedIngredients.size());
			for (int slot = 0; slot < encodedIngredients.size(); slot++) {
				JsonElement ingredient = encodedIngredients.get(slot);
				if (ingredient == null || ingredient.isJsonNull()) {
					ingredients.add(null);
					continue;
				}
				if (!ingredient.isJsonPrimitive() || !ingredient.getAsJsonPrimitive().isString()) {
					throw new IllegalArgumentException("Recipe " + label + " ingredient " + slot + " must be an item ID or null");
				}
				Identifier id = Identifier.tryParse(ingredient.getAsString());
				if (id == null) throw new IllegalArgumentException("Recipe " + label + " has an invalid item ID at " + slot);
				ingredients.add(id);
			}
			recipes.add(new RecipeCase(label, width, height, ingredients));
		}
		return new CraftingGenerationTestSuite(number, name, recipes);
	}

	private static String requiredString(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
				|| value.getAsString().isBlank()) {
			throw new IllegalArgumentException("Missing non-empty string '" + key + "'");
		}
		return value.getAsString();
	}

	private static int requiredInt(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("Missing integer '" + key + "'");
		}
		int result = value.getAsInt();
		if (value.getAsDouble() != result) throw new IllegalArgumentException("'" + key + "' must be an integer");
		return result;
	}

	private static JsonArray requiredArray(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (!(value instanceof JsonArray array)) throw new IllegalArgumentException("Missing array '" + key + "'");
		return array;
	}

	public record RecipeCase(String label, int width, int height, List<Identifier> ingredients) {
		public RecipeCase {
			if (label == null || label.isBlank()) throw new IllegalArgumentException("Recipe label is required");
			if (width < 1 || width > 3 || height < 1 || height > 3) {
				throw new IllegalArgumentException("Recipe dimensions must be between 1 and 3");
			}
			ingredients = java.util.Collections.unmodifiableList(new ArrayList<>(ingredients));
			if (ingredients.size() != width * height) {
				throw new IllegalArgumentException("Recipe ingredient count does not match its dimensions");
			}
		}
	}
}
