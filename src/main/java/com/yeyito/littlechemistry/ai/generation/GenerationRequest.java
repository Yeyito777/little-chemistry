package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;

/** Immutable request metadata written into each isolated world-source job. */
record GenerationRequest(
		DynamicContentType fixedType,
		DynamicArmorSlot fixedArmorSlot,
		String fixedDisplayName,
		int fixedOutputCount,
		JsonObject recipeContext,
		String workstationPolicy,
		JsonObject recipeDataSchema
) {
	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String TEXTURE_DIRECTION = """
			Always base the visual design on existing vanilla or modded textures. Before creating any texture, use the
			available tools to inspect relevant reference textures and study their palettes, pixel arrangements, silhouettes,
			shading, and UV or layout conventions. Then create an original texture grounded in those references.
			""";

	GenerationRequest {
		if (fixedType == null && fixedDisplayName != null || fixedType != null && fixedDisplayName == null) {
			throw new IllegalArgumentException("Fixed content type and name must be supplied together");
		}
		if (fixedType != DynamicContentType.ARMOR && fixedArmorSlot != null) {
			throw new IllegalArgumentException("Only fixed armor requests may select an armor slot");
		}
		if (fixedType == null && recipeContext == null) {
			throw new IllegalArgumentException("Flexible generation requires recipe context");
		}
		if ((workstationPolicy == null) != (recipeDataSchema == null)) {
			throw new IllegalArgumentException("Workstation policy and recipeData schema must be supplied together");
		}
		if (fixedOutputCount < 1 || fixedOutputCount > 64) {
			throw new IllegalArgumentException("Output count must be between 1 and 64");
		}
		recipeContext = recipeContext == null ? null : recipeContext.deepCopy();
		recipeDataSchema = recipeDataSchema == null ? null : recipeDataSchema.deepCopy();
	}

	static GenerationRequest fixed(DynamicContentType type, DynamicArmorSlot slot, String name, int count,
			JsonObject recipeContext) {
		return new GenerationRequest(type, slot, name, count, recipeContext, null, null);
	}

	static GenerationRequest recipe(JsonObject context, String workstationPolicy, JsonObject recipeDataSchema) {
		return new GenerationRequest(null, null, null, 1, context, workstationPolicy, recipeDataSchema);
	}

	boolean flexible() {
		return fixedType == null;
	}

	String userPrompt() {
		StringBuilder prompt = new StringBuilder();
		if (flexible()) {
			prompt.append("Please generate and code the most natural piece of Minecraft content for the following recipe. ")
					.append("Infer whether the result should be an item, block, armor piece, entity, or workstation from the ")
					.append("ingredients, their arrangement, and the crafting process.\n\nRecipe:\n")
					.append(PRETTY_GSON.toJson(recipeContext))
					.append("\n\n");
			if (workstationPolicy != null) {
				prompt.append("The workstation's recipe policy is:\n")
						.append(workstationPolicy)
						.append("\n\nThe required recipe-data schema is:\n")
						.append(PRETTY_GSON.toJson(recipeDataSchema))
						.append("\n\n");
			}
		} else {
			String kind = switch (fixedType) {
				case ITEM -> "item";
				case BLOCK -> "block";
				case ARMOR -> fixedArmorSlot == null ? "armor piece"
						: fixedArmorSlot.serializedName() + " armor piece";
				case ENTITY -> "entity";
			};
			prompt.append("Please generate and code a Minecraft ")
					.append(kind)
					.append(" named \"")
					.append(fixedDisplayName)
					.append("\" for Little Chemistry.\n\n");
			if (recipeContext != null) {
				prompt.append("Base it on this recipe:\n")
						.append(PRETTY_GSON.toJson(recipeContext))
						.append("\n\n");
			}
		}
		prompt.append(TEXTURE_DIRECTION)
				.append("Implement the complete gameplay properties and behavior that naturally follow from the concept and recipe. ")
				.append("Build and verify the finished content, repairing any diagnostics before finishing.");
		return prompt.toString();
	}

	@Override public JsonObject recipeContext() {
		return recipeContext == null ? null : recipeContext.deepCopy();
	}

	@Override public JsonObject recipeDataSchema() {
		return recipeDataSchema == null ? null : recipeDataSchema.deepCopy();
	}
}
