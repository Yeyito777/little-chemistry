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

	AiCraftingRecipe(RecipeSignature signature, String outputName) {
		this.signature = signature;
		this.outputName = outputName;
	}

	RecipeSignature signature() {
		return signature;
	}

	String outputName() {
		return outputName;
	}

	boolean outputAvailable() {
		return DynamicContentCatalog.find(outputName) != null;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return outputAvailable() && signature.matches(input);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		DynamicContentDefinition definition = DynamicContentCatalog.find(outputName);
		return definition == null ? ItemStack.EMPTY : DynamicContentObjects.createStack(definition);
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
