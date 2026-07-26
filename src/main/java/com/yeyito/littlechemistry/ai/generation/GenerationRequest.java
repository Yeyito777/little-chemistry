package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentManager;
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
	private static final String SELECTION_DIRECTION = """
			Choose from the result's primary player interaction: item for hand/inventory use, block for an ordinary placed object,
			workstation for a functional machine or crafting/processing bench, armor only when equipping for protection is primary,
			and entity for an independently placed world object, creature, vehicle, or mount. Storage is a capability of an item or
			block when opening an inventory is primary. Do not substitute a cosmetic or unrelated passive effect for the natural
			interaction. Record capability `ordinary` for ordinary items/blocks, `storage` for native containers,
			`projectile_weapon` for bows/crossbows, and the matching `workstation`, `armor`, or `entity` capability otherwise. Select
			first; the tool that observes `.littlechemistry/result.json` returns only that focused implementation contract. Follow it
			before authoring source instead of reading unrelated capability documentation.
			""";
	private static final String TEXTURE_DIRECTION = """
			The separate recipe-item texture section below contains exact textual artwork for every provided ingredient, including
			conventional state frames and associated equipped layers. Start from the closest supplied carrier, preserve its occupied
			silhouette, shading language, UV layout, and animation progression, then make deliberate ingredient-specific palette and
			motif edits. Do not redraw familiar Minecraft carriers from memory or infer pixels from filenames. Search
			`reference/vanilla/TEXTURES.txt` and call `read_texture` only for additional references relevant to the selected contract.
			All model-facing texture data uses the same indexed RRGGBBAA palette/rows representation generated Java must submit; large
			references are coordinate-labelled textual tiles, never raster images.
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

	/* Keep this query request-specific. Shared API details belong in reference/API.md, and only workstation queries may
	 * advertise rejection as a valid terminal result. */
	String userPrompt() {
		return userPrompt("");
	}

	String userPrompt(String visualReferenceSection) {
		StringBuilder prompt = new StringBuilder();
		if (flexible()) {
			JsonObject displayedRecipe = recipeContext.deepCopy();
			JsonObject workstation = workstationPolicy == null ? null : workstationIdentity(displayedRecipe);
			prompt.append("Please generate and code the most natural piece of Minecraft content for the following recipe. ")
					.append("Infer whether the result should be an item, block, armor piece, entity, or workstation from the ")
					.append("ingredients, their arrangement, and the crafting process.\n\nRecipe:\n")
						.append(PRETTY_GSON.toJson(displayedRecipe))
						.append("\n\n");
			if (workstationPolicy != null) {
				prompt.append("This recipe is being created in the following special workstation:\n")
						.append("Name: ").append(workstationName(workstation)).append('\n')
						.append("Description: ").append(workstationDescription(workstation)).append("\n\n")
						.append("The workstation's output policy is:\n")
						.append(workstationPolicy)
						.append("\n\nThe required recipe-data schema is:\n")
						.append(PRETTY_GSON.toJson(recipeDataSchema))
						.append("\n\nBefore creating source, choose one terminal result and write it to "
								+ "`.littlechemistry/result.json`. For an accepted result, use exactly:\n")
						.append("`{\"kind\":\"item|block|workstation|helmet|chestplate|leggings|boots|entity\","
								+ "\"capability\":\"ordinary|storage|projectile_weapon|workstation|armor|entity\","
								+ "\"displayName\":\"...\",\"outputCount\":<natural integer>,\"recipeData\":{...}}`\n")
						.append("`recipeData` is required and must match the closed schema above. You may instead reject "
								+ "this workstation recipe only if the workstation is too weak for the craft. For a rejection, "
								+ "write exactly:\n")
						.append("`{\"kind\":\"rejection\",\"category\":\"workstation_too_weak\","
								+ "\"description\":\"...\"}`\n")
						.append("The description must be one or two short, complete sentences that specifically explain "
								+ "the rejection. Do not create source for a rejection; call `verify` immediately after writing "
								+ "the rejection result.\n\n")
						.append("Use the workstation output policy to characterize an accepted result. `workstationContext` and "
								+ "behavior `aiContext` are descriptive prompt material and are excluded from cache identity. The engine-supplied "
								+ "`visualReferenceDigest` binds automatic ingredient artwork to cache identity. Every other contextual "
								+ "value that can change output identity, count, recipeData, visuals, properties, or behavior must already "
								+ "be represented in the deterministic canonical `cacheDiscriminator`. Never make a result depend on "
								+ "descriptive context absent from that discriminator.\n\n");
			} else {
				boolean ordinaryCrafting = displayedRecipe.has("recipeType")
						&& "crafting".equals(displayedRecipe.get("recipeType").getAsString());
				prompt.append("Before creating source, choose the result and write exactly this shape to "
						+ "`.littlechemistry/result.json`:\n")
						.append("`{\"kind\":\"item|block|workstation|helmet|chestplate|leggings|boots|entity\","
								+ "\"capability\":\"ordinary|storage|projectile_weapon|workstation|armor|entity\","
								+ "\"displayName\":\"...\",\"outputCount\":<natural integer>"
								+ (ordinaryCrafting ? ",\"ingredientUses\":{\"<slot>\":\"consume|keep|damage\"}" : "")
								+ "}`\n")
						.append("Do not include `recipeData` for ordinary crafting or smelting.\n\n");
				if (ordinaryCrafting) {
					prompt.append("For `ingredientUses`, include only occupied slots whose role intentionally differs from the "
							+ "shown `defaultIngredientUse`; use `{}` when the defaults fit. Use `damage` only when the ingredient "
							+ "serves as a reusable utility implement in this craft, such as wire cutters, shears, a file, hammer, wrench, "
							+ "or similar tool. Ingredients serving as material or as the object being transformed are consumed whole; "
							+ "use `keep` only for a genuine unchanged catalyst, mold, or template.\n\n");
				}
			}
			prompt.append("For an accepted result, choose the natural output count from 1 to 64 based on the recipe and "
					+ "result. Armor output count is always 1, and every count must fit both the destination and the "
					+ "generated stack. `displayName` is the player-facing title: write capitalized words separated by spaces, "
					+ "never a lowercase underscore identifier. Derive the separate lowercase underscore `<id>` from it and create "
					+ "`<category>/<id>/C_<id>_Content.java` with package `<category>.c_<id>`, plus the sibling "
					+ "`GeneratedBehaviorImpl.java`. The process and every ingredient must materially influence identity, "
					+ "pixels, native properties, and behavior.\n\n")
					.append(SELECTION_DIRECTION).append('\n');
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
			String identifier = DynamicContentManager.normalizeIdentifier(
						DynamicContentManager.normalizeDisplayName(fixedDisplayName));
			String category = GenerationWorkspace.category(fixedType);
			String factoryClass = GenerationWorkspace.className(identifier);
			prompt.append("Keep this exact generated-source identity: factory file `")
						.append(category).append('/').append(identifier).append('/').append(factoryClass)
						.append(".java`, package `").append(category).append('.')
						.append(GenerationWorkspace.javaPackageSegment(identifier)).append("`, public factory class `")
						.append(factoryClass).append("`, and sibling behavior file `")
						.append(category).append('/').append(identifier).append("/GeneratedBehaviorImpl.java`. ")
						.append("The requested output count is ").append(fixedOutputCount).append(".\n\n");
			if (fixedType == DynamicContentType.ITEM) {
				prompt.append("Before coding, classify the item's native capability by writing ")
						.append("`{\"kind\":\"item\",\"capability\":\"ordinary|storage|projectile_weapon\"}` to ")
						.append("`.littlechemistry/result.json`. The observing tool returns only that focused contract; follow it ")
						.append("before authoring source.\n\n");
			} else if (fixedType == DynamicContentType.BLOCK) {
				prompt.append("Before coding, classify the requested block by writing one of ")
						.append("`{\"kind\":\"block\",\"capability\":\"ordinary|storage\"}` or ")
						.append("`{\"kind\":\"workstation\",\"capability\":\"workstation\"}` to ")
						.append("`.littlechemistry/result.json`. Use workstation for a functional machine or crafting/processing ")
						.append("bench. The observing tool returns only that focused contract; follow it before authoring source.\n\n");
				} else {
					GenerationContracts.Contract contract = GenerationContracts.fixedContract(this);
					prompt.append("The already selected focused implementation contract follows; do not read unrelated contracts:\n\n")
							.append(contract.content()).append("\n\n")
							.append(GenerationContracts.OPTIONAL_PARTICLES_DIRECTION).append('\n');
				}
		}
		prompt.append(TEXTURE_DIRECTION);
		if (visualReferenceSection != null && !visualReferenceSection.isBlank()) {
			prompt.append(visualReferenceSection);
		}
		if (workstationPolicy != null) {
			prompt.append("If accepting the recipe, implement the complete gameplay properties and behavior that naturally follow "
					+ "from the concept and recipe. For either an accepted result or a rejection, call `verify` and repair any "
					+ "diagnostics before finishing.");
		} else {
			prompt.append("Implement the complete gameplay properties and behavior that naturally follow from the concept and recipe. ")
					.append("Build and verify the finished content, repairing any diagnostics before finishing.");
		}
		return prompt.toString().stripTrailing();
	}

	/** Extracts identity for the prose policy prefix and avoids repeating it in the rendered recipe JSON. */
	private static JsonObject workstationIdentity(JsonObject displayedRecipe) {
		if (!(displayedRecipe.get("workstation") instanceof JsonObject workstation)) {
			return null;
		}
		JsonObject identity = new JsonObject();
		if (workstation.has("displayName")) {
			identity.add("displayName", workstation.remove("displayName"));
		}
		if (workstation.has("description")) {
			identity.add("description", workstation.remove("description"));
		}
		return identity;
	}

	private static String workstationName(JsonObject identity) {
		return identity != null && identity.has("displayName")
				? identity.get("displayName").getAsString() : "Unnamed Workstation";
	}

	private static String workstationDescription(JsonObject identity) {
		return identity != null && identity.has("description")
				? identity.get("description").getAsString() : "No workstation description was supplied.";
	}

	@Override public JsonObject recipeContext() {
		return recipeContext == null ? null : recipeContext.deepCopy();
	}

	@Override public JsonObject recipeDataSchema() {
		return recipeDataSchema == null ? null : recipeDataSchema.deepCopy();
	}
}
