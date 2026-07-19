package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The exact, trimmed shape of an invented recipe. Stack counts are deliberately
 * ignored, while data components are retained so different dynamic carrier
 * items never collapse into the same recipe ingredient.
 */
public final class RecipeSignature {
	private final int width;
	private final int height;
	private final List<ItemStack> ingredients;
	private final int hashCode;

	public RecipeSignature(int width, int height, List<ItemStack> ingredients) {
		if (width < 1 || width > 3 || height < 1 || height > 3 || ingredients.size() != width * height) {
			throw new IllegalArgumentException("Invalid crafting recipe dimensions");
		}
		this.width = width;
		this.height = height;
		List<ItemStack> normalized = new ArrayList<>(ingredients.size());
		for (ItemStack ingredient : ingredients) {
			normalized.add(RecipeIngredient.normalize(Objects.requireNonNull(ingredient, "ingredient")));
		}
		this.ingredients = List.copyOf(normalized);
		this.hashCode = 31 * (31 * width + height) + ItemStack.hashStackList(this.ingredients);
	}

	public static RecipeSignature capture(CraftingContainer container) {
		CraftingInput input = container.asCraftInput();
		return input.isEmpty() ? null : fromInput(input);
	}

	public static RecipeSignature fromInput(CraftingInput input) {
		CraftingInput normalized = CraftingInput.ofPositioned(input.width(), input.height(), input.items()).input();
		if (normalized.isEmpty()) return null;
		return new RecipeSignature(normalized.width(), normalized.height(), normalized.items());
	}

	public boolean matches(CraftingInput input) {
		RecipeSignature candidate = fromInput(input);
		return equals(candidate) || equals(candidate == null ? null : candidate.mirrored());
	}

	static boolean matchesIngredient(ItemStack expected, ItemStack candidate) {
		return RecipeIngredient.matches(expected, candidate);
	}

	boolean referencesDynamicContent(Set<String> names) {
		return ingredients.stream().anyMatch(ingredient -> RecipeIngredient.referencesDynamicContent(ingredient, names));
	}

	boolean referencesUnavailableDynamicContent() {
		return ingredients.stream().anyMatch(RecipeIngredient::referencesUnavailableDynamicContent);
	}

	public RecipeSignature mirrored() {
		List<ItemStack> mirrored = new ArrayList<>(ingredients.size());
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				mirrored.add(ingredients.get(width - x - 1 + y * width));
			}
		}
		return new RecipeSignature(width, height, mirrored);
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public List<ItemStack> ingredients() {
		return ingredients.stream().map(ItemStack::copy).toList();
	}

	public JsonObject toAiContext() {
		JsonObject context = new JsonObject();
		context.addProperty("recipeType", "crafting");
		context.addProperty("process", "minecraft:crafting");
		context.addProperty("width", width);
		context.addProperty("height", height);
		JsonArray grid = new JsonArray();
		Map<String, DynamicContentDefinition> dynamicIngredients = new LinkedHashMap<>();
		DynamicContentManager contentManager = DynamicContentManager.active();
		for (int slot = 0; slot < ingredients.size(); slot++) {
			ItemStack stack = ingredients.get(slot);
			JsonObject cell = new JsonObject();
			cell.addProperty("slot", slot);
			cell.addProperty("x", slot % width);
			cell.addProperty("y", slot / width);
			RecipeIngredient.describe(stack, cell, contentManager, dynamicIngredients);
			grid.add(cell);
		}
		context.add("grid", grid);
		context.add("dynamicIngredients", RecipeIngredient.describeDynamicIngredients(dynamicIngredients));
		return context;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof RecipeSignature signature)
				|| width != signature.width || height != signature.height
				|| ingredients.size() != signature.ingredients.size()) {
			return false;
		}
		for (int i = 0; i < ingredients.size(); i++) {
			if (!ItemStack.isSameItemSameComponents(ingredients.get(i), signature.ingredients.get(i))) return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}
}
