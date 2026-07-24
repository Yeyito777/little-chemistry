package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.ai.generation.RecipeVisualReferences;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The exact item-and-components identity consumed by an invented furnace recipe. */
public final class SmeltingRecipeSignature {
	private final ItemStack ingredient;
	private final String visualReferenceDigest;
	private final int hashCode;

	public SmeltingRecipeSignature(ItemStack ingredient) {
		this(ingredient, null);
	}

	public SmeltingRecipeSignature(ItemStack ingredient, String visualReferenceDigest) {
		this.ingredient = RecipeIngredient.normalize(Objects.requireNonNull(ingredient, "ingredient"));
		if (this.ingredient.isEmpty()) throw new IllegalArgumentException("A smelting ingredient cannot be empty");
		this.visualReferenceDigest = visualReferenceDigest == null
				? RecipeVisualReferences.digestForStacks(java.util.List.of(this.ingredient))
				: requireDigest(visualReferenceDigest);
		this.hashCode = Objects.hash(ItemStack.hashItemAndComponents(this.ingredient), this.visualReferenceDigest);
	}

	public static SmeltingRecipeSignature fromInput(SingleRecipeInput input) {
		return input.item().isEmpty() ? null : new SmeltingRecipeSignature(input.item());
	}

	public static SmeltingRecipeSignature fromStack(ItemStack stack) {
		return stack.isEmpty() ? null : new SmeltingRecipeSignature(stack);
	}

	public boolean matches(SingleRecipeInput input) {
		return RecipeIngredient.matches(ingredient, input.item());
	}

	public ItemStack ingredient() {
		return ingredient.copy();
	}

	public String visualReferenceDigest() {
		return visualReferenceDigest;
	}

	boolean hasCurrentVisualReferences() {
		return visualReferenceDigest.equals(RecipeVisualReferences.digestForStacks(java.util.List.of(ingredient)));
	}

	boolean referencesDynamicContent(Set<String> names) {
		return RecipeIngredient.referencesDynamicContent(ingredient, names);
	}

	boolean referencesUnavailableDynamicContent() {
		return RecipeIngredient.referencesUnavailableDynamicContent(ingredient);
	}

	public JsonObject toAiContext() {
		JsonObject context = new JsonObject();
		context.addProperty("recipeType", "smelting");
		context.addProperty("process", "minecraft:furnace_smelting");
		context.addProperty("visualReferenceDigest", visualReferenceDigest);
		JsonObject encodedIngredient = new JsonObject();
		Map<String, DynamicContentDefinition> dynamicIngredients = new LinkedHashMap<>();
		RecipeIngredient.describe(ingredient, encodedIngredient, DynamicContentManager.active(), dynamicIngredients);
		context.add("ingredient", encodedIngredient);
		context.add("dynamicIngredients", RecipeIngredient.describeDynamicIngredients(dynamicIngredients));
		return context;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof SmeltingRecipeSignature signature
				&& visualReferenceDigest.equals(signature.visualReferenceDigest)
				&& ItemStack.isSameItemSameComponents(ingredient, signature.ingredient);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	private static String requireDigest(String digest) {
		if (digest == null || !digest.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("Invalid smelting visual-reference digest");
		}
		return digest;
	}
}
