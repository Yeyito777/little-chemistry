package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** One persistent generated result or rejection and process-data payload for an AI-defined workstation signature. */
public final class AiWorkstationRecipe {
	private final WorkstationRecipeSignature signature;
	private final String outputName;
	private final int outputCount;
	private final JsonObject recipeData;
	private final WorkstationRecipeRejection rejection;

	public AiWorkstationRecipe(WorkstationRecipeSignature signature, String outputName, int outputCount,
			JsonObject recipeData) {
		if (signature == null) throw new IllegalArgumentException("Workstation recipe signature is required");
		if (outputName == null || !outputName.matches("[a-z0-9_]{1,64}")) {
			throw new IllegalArgumentException("Invalid workstation output name");
		}
		if (outputCount < 1 || outputCount > 64) {
			throw new IllegalArgumentException("Workstation output count must be between 1 and 64");
		}
		this.signature = signature;
		this.outputName = outputName;
		this.outputCount = outputCount;
		this.recipeData = recipeData == null ? new JsonObject() : recipeData.deepCopy();
		this.rejection = null;
	}

	private AiWorkstationRecipe(WorkstationRecipeSignature signature, WorkstationRecipeRejection rejection) {
		if (signature == null) throw new IllegalArgumentException("Workstation recipe signature is required");
		if (rejection == null) throw new IllegalArgumentException("Workstation recipe rejection is required");
		this.signature = signature;
		this.outputName = null;
		this.outputCount = 1;
		this.recipeData = new JsonObject();
		this.rejection = rejection;
	}

	public static AiWorkstationRecipe rejected(WorkstationRecipeSignature signature,
			WorkstationRecipeRejection rejection) {
		return new AiWorkstationRecipe(signature, rejection);
	}

	public WorkstationRecipeSignature signature() {
		return signature;
	}

	public String outputName() {
		return outputName;
	}

	public int outputCount() {
		return outputCount;
	}

	public JsonObject recipeData() {
		return recipeData.deepCopy();
	}

	public boolean isRejected() {
		return rejection != null;
	}

	public WorkstationRecipeRejection rejection() {
		return rejection;
	}

	public boolean referencesOutput(Set<String> outputNames) {
		return outputName != null && outputNames.contains(outputName);
	}

	public boolean outputAvailable() {
		return isRejected() || DynamicContentCatalog.find(outputName) != null;
	}

	public ItemStack outputStack() {
		if (rejection != null) return rejection.displayStack();
		DynamicContentDefinition definition = DynamicContentCatalog.find(outputName);
		if (definition == null) return ItemStack.EMPTY;
		ItemStack stack = DynamicContentObjects.createStack(definition);
		if (outputCount > stack.getMaxStackSize()) return ItemStack.EMPTY;
		stack.setCount(outputCount);
		return stack;
	}
}
