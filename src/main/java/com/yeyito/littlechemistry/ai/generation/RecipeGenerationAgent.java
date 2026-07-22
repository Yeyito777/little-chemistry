package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;

import java.io.IOException;

/** Generates recipe choice, process data, content source, visuals, and behavior in one generalist workspace pass. */
public final class RecipeGenerationAgent {
	private final OpenAiClient openAi;

	public RecipeGenerationAgent(OpenAiClient openAi) {
		this.openAi = openAi;
	}

	public GeneratedRecipe generate(JsonObject recipeContext) throws IOException, InterruptedException {
		return generate(recipeContext, null, null);
	}

	/** Generates a complete recipe governed by an optional AI-authored workstation policy and closed data schema. */
	public GeneratedRecipe generate(JsonObject recipeContext, String workstationPolicy, JsonObject recipeDataSchema)
			throws IOException, InterruptedException {
		return generate(recipeContext, workstationPolicy, recipeDataSchema, null);
	}

	/** Generates a complete recipe and optionally exports its terminal native conversation for a test-suite run. */
	public GeneratedRecipe generate(JsonObject recipeContext, String workstationPolicy, JsonObject recipeDataSchema,
			ExocortexConversationExporter conversationExporter) throws IOException, InterruptedException {
		if (recipeContext == null) throw new IllegalArgumentException("Recipe context is required");
		if ((workstationPolicy == null) != (recipeDataSchema == null)) {
			throw new IllegalArgumentException("Workstation policy and recipe-data schema must be supplied together");
		}
		try {
			WorkspaceGenerationVerifier.VerifiedGeneration generated = new ContentGenerationAgent(openAi)
					.generateRecipe(recipeContext, workstationPolicy, recipeDataSchema, conversationExporter);
			return new GeneratedRecipe(generated.type(), generated.armorSlot(), generated.displayName(),
					generated.outputCount(), generated.recipeData(), generated.content());
		} catch (RecipeRejectedException rejected) {
			return GeneratedRecipe.rejected(rejected.rejection());
		}
	}

	public record GeneratedRecipe(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName,
			int outputCount, JsonObject recipeData, GeneratedContentSpec content,
			WorkstationRecipeRejection rejection) {
		public GeneratedRecipe(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName,
				int outputCount, JsonObject recipeData, GeneratedContentSpec content) {
			this(type, armorSlot, displayName, outputCount, recipeData, content, null);
		}

		public GeneratedRecipe {
			recipeData = recipeData == null ? new JsonObject() : recipeData.deepCopy();
			if (rejection == null) {
				if (type == null || displayName == null || content == null || outputCount < 1) {
					throw new IllegalArgumentException("Successful generated recipe is incomplete");
				}
			} else if (type != null || armorSlot != null || displayName != null || content != null || outputCount != 0
					|| !recipeData.isEmpty()) {
				throw new IllegalArgumentException("Rejected recipe cannot also contain generated content");
			}
		}

		static GeneratedRecipe rejected(WorkstationRecipeRejection rejection) {
			return new GeneratedRecipe(null, null, null, 0, new JsonObject(), null, rejection);
		}

		public boolean isRejected() {
			return rejection != null;
		}

		@Override
		public JsonObject recipeData() {
			return recipeData.deepCopy();
		}
	}
}
