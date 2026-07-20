package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicWorkstationRuntimeAccess;
import com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-wide cache identity for one exact AI-defined workstation transformation.
 * Placement position and transient UI state are deliberately excluded.
 */
public final class WorkstationRecipeSignature {
	private final String workstationName;
	private final String processId;
	private final String discriminator;
	private final List<Ingredient> ingredients;
	private final int hashCode;

	public WorkstationRecipeSignature(String workstationName, String processId, String discriminator,
			List<Ingredient> ingredients) {
		if (workstationName == null || !workstationName.matches("[a-z0-9_]{1,64}")) {
			throw new IllegalArgumentException("Invalid workstation content name");
		}
		if (processId == null || !processId.matches("[a-z][a-z0-9_]{0,63}")) {
			throw new IllegalArgumentException("Invalid workstation process ID");
		}
		if (discriminator == null || discriminator.length() > WorkstationRecipeRequest.MAX_CACHE_DISCRIMINATOR_CHARACTERS
				|| discriminator.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Invalid workstation recipe discriminator");
		}
		if (ingredients == null || ingredients.isEmpty()
				|| ingredients.size() > WorkstationRecipeRequest.MAX_INGREDIENTS) {
			throw new IllegalArgumentException("Invalid workstation recipe ingredient list");
		}
		this.workstationName = workstationName;
		this.processId = processId;
		this.discriminator = discriminator;
		this.ingredients = List.copyOf(ingredients);
		this.hashCode = Objects.hash(workstationName, processId, discriminator, this.ingredients);
	}

	/** Validates generated capture instructions and snapshots the exact current stacks. */
	public static WorkstationRecipeSignature capture(DynamicContentDefinition definition,
			WorkstationRecipeRequest request, DynamicWorkstationRuntimeAccess runtime) {
		if (definition == null || definition.workstation() == null || request == null || runtime == null) return null;
		List<Ingredient> captured = new ArrayList<>();
		for (WorkstationRecipeRequest.Ingredient requested : request.ingredients()) {
			DynamicWorkstationSlot slot = definition.workstation().slot(requested.slotId());
			if (slot == null || !slot.role().capturesRecipeInput()) {
				throw new IllegalArgumentException("Recipe captured a non-input workstation slot: " + requested.slotId());
			}
			ItemStack stack = runtime.stack(requested.slotId());
			if (stack == null || stack.isEmpty() || stack.getCount() < requested.count()) {
				return null;
			}
			if (requested.use() == WorkstationRecipeRequest.IngredientUse.DAMAGE
					&& (!stack.isDamageableItem() || requested.count() != 1)) {
				throw new IllegalArgumentException("Damaged workstation ingredients must be one damageable item");
			}
			captured.add(new Ingredient(requested.slotId(), RecipeIngredient.normalize(stack),
					requested.count(), requested.use()));
		}
		return new WorkstationRecipeSignature(definition.name(), request.processId(),
				request.cacheDiscriminator(), captured);
	}

	public String workstationName() {
		return workstationName;
	}

	public String processId() {
		return processId;
	}

	public String discriminator() {
		return discriminator;
	}

	public List<Ingredient> ingredients() {
		return ingredients.stream().map(Ingredient::copy).toList();
	}

	public boolean matches(DynamicContentDefinition definition, WorkstationRecipeRequest request,
			DynamicWorkstationRuntimeAccess runtime) {
		return equals(capture(definition, request, runtime));
	}

	boolean referencesDynamicContent(Set<String> names) {
		return names.contains(workstationName) || ingredients.stream()
				.anyMatch(ingredient -> RecipeIngredient.referencesDynamicContent(ingredient.stack, names));
	}

	boolean referencesUnavailableDynamicContent() {
		return com.yeyito.littlechemistry.content.DynamicContentCatalog.find(workstationName) == null
				|| ingredients.stream().anyMatch(ingredient ->
				RecipeIngredient.referencesUnavailableDynamicContent(ingredient.stack));
	}

	public JsonObject toAiContext(DynamicContentDefinition definition, JsonObject generatedContext) {
		JsonObject context = new JsonObject();
		context.addProperty("recipeType", "workstation");
		context.addProperty("process", LittleChemistry.MOD_ID + ":workstation/" + workstationName + "/" + processId);
		context.addProperty("processId", processId);
		context.add("workstation", workstationContext(definition));
		JsonArray captured = new JsonArray();
		Map<String, DynamicContentDefinition> dynamicIngredients = new LinkedHashMap<>();
		DynamicContentManager contentManager = DynamicContentManager.active();
		for (Ingredient ingredient : ingredients) {
			JsonObject encoded = new JsonObject();
			encoded.addProperty("slot", ingredient.slotId);
			DynamicWorkstationSlot slot = definition.workstation().slot(ingredient.slotId);
			encoded.addProperty("slotRole", slot.role().serializedName());
			encoded.addProperty("requiredCount", ingredient.count);
			encoded.addProperty("use", ingredient.use.name().toLowerCase(java.util.Locale.ROOT));
			RecipeIngredient.describe(ingredient.stack, encoded, contentManager, dynamicIngredients);
			captured.add(encoded);
		}
		context.add("ingredients", captured);
		context.add("dynamicIngredients", RecipeIngredient.describeDynamicIngredients(dynamicIngredients));
		context.add("workstationContext", generatedContext == null ? new JsonObject() : generatedContext.deepCopy());
		if (!discriminator.isEmpty()) context.addProperty("cacheDiscriminator", discriminator);
		return context;
	}

	static JsonObject workstationContext(DynamicContentDefinition definition) {
		if (definition == null || definition.workstation() == null) {
			throw new IllegalArgumentException("A workstation definition is required");
		}
		JsonObject workstation = new JsonObject();
		workstation.addProperty("contentId", LittleChemistry.MOD_ID + ":" + definition.name());
		workstation.addProperty("displayName", definition.displayName());
		workstation.addProperty("processDescription", definition.workstation().processDescription());
		DynamicWorkstationSlot primaryOutputSlot = definition.workstation().primaryOutputSlot();
		JsonObject primaryOutput = new JsonObject();
		primaryOutput.addProperty("id", primaryOutputSlot.id());
		primaryOutput.addProperty("capacity", primaryOutputSlot.maxStack());
		workstation.add("primaryOutput", primaryOutput);
		return workstation;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof WorkstationRecipeSignature signature
				&& workstationName.equals(signature.workstationName)
				&& processId.equals(signature.processId)
				&& discriminator.equals(signature.discriminator)
				&& ingredients.equals(signature.ingredients);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	public record Ingredient(String slotId, ItemStack stack, int count,
			WorkstationRecipeRequest.IngredientUse use) {
		public Ingredient {
			if (slotId == null || !slotId.matches("[a-z][a-z0-9_]{0,63}")) {
				throw new IllegalArgumentException("Invalid workstation ingredient slot ID");
			}
			stack = RecipeIngredient.normalize(Objects.requireNonNull(stack, "stack"));
			if (stack.isEmpty() || count < 1 || count > 64
					|| use != WorkstationRecipeRequest.IngredientUse.CONSUME
					&& use != WorkstationRecipeRequest.IngredientUse.KEEP
					&& use != WorkstationRecipeRequest.IngredientUse.DAMAGE) {
				throw new IllegalArgumentException("Invalid workstation ingredient");
			}
		}

		private Ingredient copy() {
			return new Ingredient(slotId, stack.copy(), count, use);
		}

		@Override
		public ItemStack stack() {
			return stack.copy();
		}

		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof Ingredient ingredient
					&& slotId.equals(ingredient.slotId) && count == ingredient.count && use == ingredient.use
					&& ItemStack.isSameItemSameComponents(stack, ingredient.stack);
		}

		@Override
		public int hashCode() {
			return Objects.hash(slotId, count, use, ItemStack.hashItemAndComponents(stack));
		}
	}
}
