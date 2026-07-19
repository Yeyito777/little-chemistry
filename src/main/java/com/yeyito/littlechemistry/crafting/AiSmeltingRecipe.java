package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

/** A runtime smelting recipe with exact data-component-aware input matching. */
final class AiSmeltingRecipe extends SmeltingRecipe {
	static final float DEFAULT_EXPERIENCE = 0.1F;
	static final int DEFAULT_COOKING_TIME = 200;

	private final SmeltingRecipeSignature signature;
	private final ResourceKey<Recipe<?>> recipeKey;
	private final String outputName;
	private final int outputCount;

	AiSmeltingRecipe(ResourceKey<Recipe<?>> recipeKey, SmeltingRecipeSignature signature, String outputName,
			int outputCount, float experience, int cookingTime) {
		super(new Recipe.CommonInfo(false),
				new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""),
				Ingredient.of(signature.ingredient().getItem()),
				new ItemStackTemplate(Items.BARRIER),
				experience, cookingTime);
		if (outputCount < 1 || outputCount > 64) {
			throw new IllegalArgumentException("AI recipe output count must be between 1 and 64");
		}
		if (!Float.isFinite(experience) || experience < 0.0F) {
			throw new IllegalArgumentException("AI smelting recipe experience must be finite and nonnegative");
		}
		if (cookingTime < 1 || cookingTime > Short.MAX_VALUE) {
			throw new IllegalArgumentException("AI smelting recipe cooking time must be between 1 and 32767 ticks");
		}
		this.recipeKey = recipeKey;
		this.signature = signature;
		this.outputName = outputName;
		this.outputCount = outputCount;
	}

	SmeltingRecipeSignature signature() {
		return signature;
	}

	ResourceKey<Recipe<?>> recipeKey() {
		return recipeKey;
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
	public boolean matches(SingleRecipeInput input, Level level) {
		return outputAvailable() && signature.matches(input);
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input) {
		return outputStack();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public List<RecipeDisplay> display() {
		ItemStack output = outputStack();
		if (output.isEmpty()) return List.of();
		return List.of(new FurnaceRecipeDisplay(
				new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(signature.ingredient())),
				SlotDisplay.AnyFuel.INSTANCE,
				new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(output)),
				new SlotDisplay.ItemSlotDisplay(Items.FURNACE),
				cookingTime(), experience()));
	}

	private ItemStack outputStack() {
		DynamicContentDefinition definition = DynamicContentCatalog.find(outputName);
		if (definition == null) return ItemStack.EMPTY;
		ItemStack stack = DynamicContentObjects.createStack(definition);
		if (outputCount > stack.getMaxStackSize()) return ItemStack.EMPTY;
		stack.setCount(outputCount);
		return stack;
	}
}
