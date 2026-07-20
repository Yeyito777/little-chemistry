package com.yeyito.littlechemistry.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable instructions for snapshotting and caching one generated workstation recipe.
 *
 * <p>Ingredients identify slots by their stable data-defined IDs. The workstation runtime snapshots the exact stack
 * in each slot after the behavior callback returns and includes the required count and use in the recipe signature.
 * AI context is descriptive prompt input only; it is defensively copied and bounded so generated code cannot create
 * an unbounded prompt. It is deliberately excluded from the server-wide recipe-cache identity. Any value that is
 * allowed to affect the selected output or {@code recipeData} must therefore also be encoded deterministically in the
 * canonical {@link Builder#cacheDiscriminator(String) cache discriminator}.</p>
 */
public final class WorkstationRecipeRequest {
	public static final int MAX_INGREDIENTS = 54;
	public static final int MAX_AI_CONTEXT_CHARACTERS = 8_192;
	public static final int MAX_AI_CONTEXT_DEPTH = 8;
	public static final int MAX_AI_CONTEXT_VALUES = 128;
	public static final int MAX_AI_STRING_CHARACTERS = 1_024;
	public static final int MAX_CACHE_DISCRIMINATOR_CHARACTERS = 256;

	private final String processId;
	private final List<Ingredient> ingredients;
	private final String cacheDiscriminator;
	private final JsonObject aiContext;

	private WorkstationRecipeRequest(String processId, List<Ingredient> ingredients, String cacheDiscriminator,
			JsonObject aiContext) {
		this.processId = processId;
		this.ingredients = List.copyOf(ingredients);
		this.cacheDiscriminator = cacheDiscriminator;
		this.aiContext = aiContext.deepCopy();
	}

	public static Builder builder() {
		return builder("default");
	}

	public static Builder builder(String processId) {
		return new Builder(processId);
	}

	public String processId() {
		return processId;
	}

	public List<Ingredient> ingredients() {
		return ingredients;
	}

	/**
	 * Optional bounded canonical value included in the server-wide recipe cache key. Generated behavior must use a
	 * deterministic representation of every non-ingredient value that may affect the output or recipeData.
	 */
	public String cacheDiscriminator() {
		return cacheDiscriminator;
	}

	/**
	 * Returns descriptive prompt context as a deep copy. This value is not part of cache identity; output-affecting
	 * context must also enter {@link #cacheDiscriminator()} in canonical form.
	 */
	public JsonObject aiContext() {
		return aiContext.deepCopy();
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof WorkstationRecipeRequest request
				&& processId.equals(request.processId)
				&& ingredients.equals(request.ingredients)
				&& cacheDiscriminator.equals(request.cacheDiscriminator)
				&& aiContext.equals(request.aiContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(processId, ingredients, cacheDiscriminator, aiContext);
	}

	@Override
	public String toString() {
		return "WorkstationRecipeRequest[processId=" + processId + ", ingredients=" + ingredients
				+ ", cacheDiscriminator=" + cacheDiscriminator + ", aiContext=" + aiContext + "]";
	}

	/** How the engine treats a captured ingredient after a successful atomic completion. */
	public enum IngredientUse {
		/** Consume the captured required count. */
		CONSUME,
		/** Require the count but leave the stack unchanged. */
		KEEP,
		/** Require the count and apply one durability point to the selected stack. */
		DAMAGE
	}

	public record Ingredient(String slotId, int count, IngredientUse use) {
		public Ingredient {
			slotId = DynamicWorkstationContext.requireId(slotId, "ingredient slot ID");
			if (count < 1 || count > 64) {
				throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
			}
			Objects.requireNonNull(use, "use");
		}
	}

	public static final class Builder {
		private final String processId;
		private final Map<String, Ingredient> ingredients = new LinkedHashMap<>();
		private String cacheDiscriminator = "";
		private JsonObject aiContext = new JsonObject();

		private Builder(String processId) {
			this.processId = DynamicWorkstationContext.requireId(processId, "process ID");
		}

		public Builder ingredient(String slotId, int count, IngredientUse use) {
			Ingredient ingredient = new Ingredient(slotId, count, use);
			if (ingredients.containsKey(ingredient.slotId())) {
				throw new IllegalArgumentException("Ingredient slot is already captured: " + ingredient.slotId());
			}
			if (ingredients.size() >= MAX_INGREDIENTS) {
				throw new IllegalArgumentException("A workstation recipe may capture at most " + MAX_INGREDIENTS
						+ " ingredients");
			}
			ingredients.put(ingredient.slotId(), ingredient);
			return this;
		}

		public Builder consume(String slotId, int count) {
			return ingredient(slotId, count, IngredientUse.CONSUME);
		}

		public Builder keep(String slotId, int count) {
			return ingredient(slotId, count, IngredientUse.KEEP);
		}

		public Builder damage(String slotId, int count) {
			return ingredient(slotId, count, IngredientUse.DAMAGE);
		}

		/**
		 * Sets the canonical cache suffix. AI context is descriptive and excluded from cache identity, so every
		 * output-affecting contextual value must be represented deterministically here.
		 */
		public Builder cacheDiscriminator(String value) {
			Objects.requireNonNull(value, "cache discriminator");
			if (value.length() > MAX_CACHE_DISCRIMINATOR_CHARACTERS || value.indexOf('\0') >= 0) {
				throw new IllegalArgumentException("Cache discriminator must be valid text no longer than "
						+ MAX_CACHE_DISCRIMINATOR_CHARACTERS + " characters");
			}
			this.cacheDiscriminator = value;
			return this;
		}

		/** Adds bounded descriptive prompt context; this does not change recipe-cache identity. */
		public Builder aiContext(JsonObject context) {
			this.aiContext = validateAiContext(Objects.requireNonNull(context, "AI context"));
			return this;
		}

		public Builder putAiContext(String key, JsonElement value) {
			requireAiKey(key);
			JsonObject candidate = aiContext.deepCopy();
			candidate.add(key, value == null ? JsonNull.INSTANCE : value);
			aiContext = validateAiContext(candidate);
			return this;
		}

		public Builder putAiContext(String key, String value) {
			return putAiContext(key, new JsonPrimitive(Objects.requireNonNull(value, "AI context value")));
		}

		public Builder putAiContext(String key, boolean value) {
			return putAiContext(key, new JsonPrimitive(value));
		}

		public Builder putAiContext(String key, long value) {
			return putAiContext(key, new JsonPrimitive(value));
		}

		public Builder putAiContext(String key, double value) {
			if (!Double.isFinite(value)) throw new IllegalArgumentException("AI context numbers must be finite");
			return putAiContext(key, new JsonPrimitive(value));
		}

		public WorkstationRecipeRequest build() {
			if (ingredients.isEmpty()) {
				throw new IllegalStateException("A workstation recipe request must capture at least one ingredient");
			}
			JsonObject validatedContext = validateAiContext(aiContext);
			return new WorkstationRecipeRequest(processId, new ArrayList<>(ingredients.values()),
					cacheDiscriminator, validatedContext);
		}
	}

	private static JsonObject validateAiContext(JsonObject context) {
		ValidationBudget budget = new ValidationBudget();
		validateAiElement(context, 1, budget);
		if (context.toString().length() > MAX_AI_CONTEXT_CHARACTERS) {
			throw new IllegalArgumentException("AI context may contain at most " + MAX_AI_CONTEXT_CHARACTERS
					+ " serialized characters");
		}
		return context.deepCopy();
	}

	private static void validateAiElement(JsonElement element, int depth, ValidationBudget budget) {
		if (depth > MAX_AI_CONTEXT_DEPTH) {
			throw new IllegalArgumentException("AI context may be at most " + MAX_AI_CONTEXT_DEPTH + " levels deep");
		}
		if (++budget.values > MAX_AI_CONTEXT_VALUES) {
			throw new IllegalArgumentException("AI context may contain at most " + MAX_AI_CONTEXT_VALUES + " values");
		}
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				requireAiKey(entry.getKey());
				validateAiElement(entry.getValue(), depth + 1, budget);
			}
		} else if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (JsonElement value : array) validateAiElement(value, depth + 1, budget);
		} else if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isString() && (primitive.getAsString().length() > MAX_AI_STRING_CHARACTERS
					|| primitive.getAsString().indexOf('\0') >= 0)) {
				throw new IllegalArgumentException("AI context strings may contain at most "
						+ MAX_AI_STRING_CHARACTERS + " characters and no NUL characters");
			}
			if (primitive.isNumber()) {
				double number;
				try {
					number = primitive.getAsDouble();
				} catch (NumberFormatException error) {
					throw new IllegalArgumentException("AI context numbers must be valid", error);
				}
				if (!Double.isFinite(number)) throw new IllegalArgumentException("AI context numbers must be finite");
			}
		}
	}

	private static void requireAiKey(String key) {
		Objects.requireNonNull(key, "AI context key");
		if (key.isEmpty() || key.length() > 64 || key.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("AI context keys must contain 1 to 64 valid text characters");
		}
	}

	private static final class ValidationBudget {
		private int values;
	}
}
