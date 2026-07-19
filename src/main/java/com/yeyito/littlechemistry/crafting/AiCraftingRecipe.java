package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.Level;

final class AiCraftingRecipe implements CraftingRecipe {
	private final RecipeSignature signature;
	private final String outputName;
	private final int outputCount;

	AiCraftingRecipe(RecipeSignature signature, String outputName, int outputCount) {
		if (outputCount < 1 || outputCount > 64) {
			throw new IllegalArgumentException("AI recipe output count must be between 1 and 64");
		}
		this.signature = signature;
		this.outputName = outputName;
		this.outputCount = outputCount;
	}

	RecipeSignature signature() {
		return signature;
	}

	String outputName() {
		return outputName;
	}

	int outputCount() {
		return outputCount;
	}

	boolean outputAvailable() {
		return !outputStack().isEmpty();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return outputAvailable() && signature.matches(input);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return outputStack();
	}

	private ItemStack outputStack() {
		DynamicContentDefinition definition = DynamicContentCatalog.find(outputName);
		if (definition == null) return ItemStack.EMPTY;
		ItemStack stack = DynamicContentObjects.createStack(definition);
		if (outputCount > stack.getMaxStackSize()) return ItemStack.EMPTY;
		stack.setCount(outputCount);
		return stack;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
		// Runtime recipes are never serialized through Minecraft's recipe sync.
		// A native serializer is returned only to satisfy the Recipe contract.
		return ShapedRecipe.SERIALIZER;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.MISC;
	}
}
