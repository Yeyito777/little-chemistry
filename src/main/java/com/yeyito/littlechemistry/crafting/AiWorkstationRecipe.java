package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.world.item.ItemStack;

/** One persistent generated result and process-data payload for an AI-defined workstation signature. */
public final class AiWorkstationRecipe {
	private final WorkstationRecipeSignature signature;
	private final String outputName;
	private final int outputCount;
	private final JsonObject recipeData;

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

	public boolean outputAvailable() {
		return !outputStack().isEmpty();
	}

	public ItemStack outputStack() {
		DynamicContentDefinition definition = DynamicContentCatalog.find(outputName);
		if (definition == null) return ItemStack.EMPTY;
		ItemStack stack = DynamicContentObjects.createStack(definition);
		if (outputCount > stack.getMaxStackSize()) return ItemStack.EMPTY;
		stack.setCount(outputCount);
		return stack;
	}
}
