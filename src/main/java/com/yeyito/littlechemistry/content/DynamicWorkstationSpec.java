package com.yeyito.littlechemistry.content;

import java.util.HashSet;
import java.util.List;

/**
 * Optional composable machine capability attached to a generated block definition.
 *
 * <p>{@code recipePolicy} is concise declarative guidance about the feel, balance, and gameplay properties of
 * resulting content. It is appended to workstation recipe user requests and is never used as API system
 * instructions. Input eligibility, consumption, processing, and timing belong in generated behavior and
 * {@code processDescription}; structured per-recipe metadata belongs in {@code recipeDataSchema}.</p>
 */
public record DynamicWorkstationSpec(
		List<DynamicWorkstationSlot> slots,
		DynamicWorkstationUi ui,
		String processDescription,
		String recipePolicy,
		DynamicWorkstationRecipeDataSchema recipeDataSchema
) {
	public static final int MAX_SLOTS = 54;
	public static final int MAX_PROCESS_DESCRIPTION_LENGTH = 1_024;
	public static final int MAX_RECIPE_POLICY_LENGTH = 16_384;
	/** Legacy source-compatibility alias; workstation policy is never an API system prompt. */
	@Deprecated
	public static final int MAX_RECIPE_SYSTEM_PROMPT_LENGTH = MAX_RECIPE_POLICY_LENGTH;

	public DynamicWorkstationSpec {
		if (slots == null) throw new IllegalArgumentException("Workstation slot list is required");
		slots = List.copyOf(slots);
		if (slots.size() < 2 || slots.size() > MAX_SLOTS) {
			throw new IllegalArgumentException("Workstations require 2-" + MAX_SLOTS + " slots");
		}
		if (ui == null) throw new IllegalArgumentException("Workstation UI is required");
		processDescription = DynamicWorkstationValidation.text(processDescription,
				"Workstation process description", MAX_PROCESS_DESCRIPTION_LENGTH, true);
		recipePolicy = DynamicWorkstationValidation.text(recipePolicy,
				"Workstation recipe policy", MAX_RECIPE_POLICY_LENGTH, true);
		if (recipeDataSchema == null) {
			throw new IllegalArgumentException("Workstation recipeData schema is required");
		}

		HashSet<String> ids = new HashSet<>();
		boolean hasInput = false;
		int primaryOutputs = 0;
		for (DynamicWorkstationSlot slot : slots) {
			if (!ids.add(slot.id())) throw new IllegalArgumentException("Duplicate workstation slot ID: " + slot.id());
			if (slot.role().capturesRecipeInput()) hasInput = true;
			if (slot.role() == DynamicWorkstationSlotRole.OUTPUT) primaryOutputs++;
			DynamicWorkstationValidation.rectangle("Workstation slot " + slot.id(),
					slot.x(), slot.y(), 18, 18, ui.width(), ui.height());
		}
		if (!hasInput) {
			throw new IllegalArgumentException("Workstations require a recipe input slot");
		}
		if (primaryOutputs != 1) {
			throw new IllegalArgumentException("Workstations require exactly one primary OUTPUT slot");
		}
		DynamicWorkstationJson.validateSerializedSize(
				slots, ui, processDescription, recipePolicy, recipeDataSchema);
	}

	/**
	 * Legacy accessor retained for generated source and integrations compiled against older worlds.
	 * The value is declarative user-level output guidance, not system instructions.
	 */
	@Deprecated
	public String recipeSystemPrompt() {
		return recipePolicy;
	}

	public DynamicWorkstationSlot slot(String id) {
		for (DynamicWorkstationSlot slot : slots) {
			if (slot.id().equals(id)) return slot;
		}
		return null;
	}

	/** The single slot with the primary {@link DynamicWorkstationSlotRole#OUTPUT} role. */
	public DynamicWorkstationSlot primaryOutputSlot() {
		for (DynamicWorkstationSlot slot : slots) {
			if (slot.role() == DynamicWorkstationSlotRole.OUTPUT) return slot;
		}
		throw new IllegalStateException("Validated workstation has no primary OUTPUT slot");
	}
}
