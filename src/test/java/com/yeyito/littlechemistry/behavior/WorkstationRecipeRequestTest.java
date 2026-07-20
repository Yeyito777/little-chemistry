package com.yeyito.littlechemistry.behavior;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WorkstationRecipeRequestTest {
	@Test
	void builderCapturesIngredientUseAndDefensivelyCopiesAiContext() {
		JsonObject nested = new JsonObject();
		nested.addProperty("temperature", 900);
		JsonObject context = new JsonObject();
		context.add("environment", nested);

		WorkstationRecipeRequest.Builder builder = WorkstationRecipeRequest.builder("alloying")
				.consume("metal", 3)
				.keep("mold", 1)
				.damage("hammer", 1)
				.cacheDiscriminator("moon_phase=full")
				.aiContext(context);
		WorkstationRecipeRequest request = builder.build();

		nested.addProperty("temperature", 1);
		context.addProperty("injected", true);
		JsonObject returned = request.aiContext();
		returned.addProperty("injected", true);
		builder.putAiContext("later", true);

		assertEquals("alloying", request.processId());
		assertEquals("moon_phase=full", request.cacheDiscriminator());
		assertEquals(3, request.ingredients().size());
		assertEquals(new WorkstationRecipeRequest.Ingredient(
				"metal", 3, WorkstationRecipeRequest.IngredientUse.CONSUME), request.ingredients().getFirst());
		assertEquals(900, request.aiContext().getAsJsonObject("environment").get("temperature").getAsInt());
		assertEquals(null, request.aiContext().get("injected"));
		assertEquals(null, request.aiContext().get("later"));
		assertThrows(UnsupportedOperationException.class,
				() -> request.ingredients().add(request.ingredients().getFirst()));

		WorkstationRecipeRequest changed = builder.build();
		assertNotEquals(request, changed);
	}

	@Test
	void rejectsMissingDuplicateOrInvalidIngredients() {
		assertThrows(IllegalStateException.class, () -> WorkstationRecipeRequest.builder().build());
		assertThrows(IllegalArgumentException.class,
				() -> WorkstationRecipeRequest.builder().consume("Bad Slot", 1));
		assertThrows(IllegalArgumentException.class,
				() -> WorkstationRecipeRequest.builder().consume("input", 0));
		assertThrows(IllegalArgumentException.class,
				() -> WorkstationRecipeRequest.builder().consume("input", 65));
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRequest.builder()
				.consume("input", 1).keep("input", 1));
	}

	@Test
	void enforcesIngredientAndAiContextBudgets() {
		WorkstationRecipeRequest.Builder tooManyIngredients = WorkstationRecipeRequest.builder();
		for (int index = 0; index < WorkstationRecipeRequest.MAX_INGREDIENTS; index++) {
			tooManyIngredients.consume("slot_" + index, 1);
		}
		assertThrows(IllegalArgumentException.class,
				() -> tooManyIngredients.consume("one_too_many", 1));

		JsonArray tooManyValues = new JsonArray();
		for (int index = 0; index < WorkstationRecipeRequest.MAX_AI_CONTEXT_VALUES; index++) {
			tooManyValues.add(index);
		}
		JsonObject excessiveContext = new JsonObject();
		excessiveContext.add("values", tooManyValues);
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRequest.builder()
				.consume("input", 1).aiContext(excessiveContext));

		String excessiveString = String.join("", Collections.nCopies(
				WorkstationRecipeRequest.MAX_AI_STRING_CHARACTERS + 1, "x"));
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRequest.builder()
				.consume("input", 1).putAiContext("description", excessiveString));
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRequest.builder()
				.consume("input", 1).putAiContext("invalid", Double.NaN));
	}
}
