package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class AiCraftingRecipe implements CraftingRecipe {
	private final RecipeSignature signature;
	private final String outputName;
	private final int outputCount;
	private final List<CraftingIngredientUse> ingredientUses;
	private final PlacementInfo placementInfo;

	AiCraftingRecipe(RecipeSignature signature, String outputName, int outputCount) {
		this(signature, outputName, outputCount, List.of());
	}

	AiCraftingRecipe(RecipeSignature signature, String outputName, int outputCount,
			List<CraftingIngredientUse> ingredientUses) {
		if (outputCount < 1 || outputCount > 64) {
			throw new IllegalArgumentException("AI recipe output count must be between 1 and 64");
		}
		this.signature = signature;
		this.outputName = outputName;
		this.outputCount = outputCount;
		List<ItemStack> signatureIngredients = signature.ingredients();
		if (ingredientUses == null || ingredientUses.isEmpty()) {
			this.ingredientUses = CraftingIngredientUse.resolvedDefaults(signatureIngredients);
		} else {
			if (ingredientUses.size() != signatureIngredients.size()) {
				throw new IllegalArgumentException("Crafting ingredient uses must match the recipe grid");
			}
			List<CraftingIngredientUse> resolved = new ArrayList<>(ingredientUses.size());
			for (int slot = 0; slot < ingredientUses.size(); slot++) {
				CraftingIngredientUse use = ingredientUses.get(slot);
				if (use == null) throw new IllegalArgumentException("Crafting ingredient use is required");
				ItemStack ingredient = signatureIngredients.get(slot);
				if (ingredient.isEmpty() && use != CraftingIngredientUse.DEFAULT) {
					throw new IllegalArgumentException("Empty crafting slots cannot declare an ingredient use");
				}
				if (use == CraftingIngredientUse.DAMAGE && !ingredient.isDamageableItem()) {
					throw new IllegalArgumentException("Only damageable crafting ingredients may lose durability");
				}
				resolved.add(use == CraftingIngredientUse.DEFAULT
						? CraftingIngredientUse.defaultFor(ingredient) : use);
			}
			this.ingredientUses = List.copyOf(resolved);
		}
		List<Optional<Ingredient>> ingredients = new ArrayList<>(signature.ingredients().size());
		for (ItemStack ingredient : signature.ingredients()) {
			ingredients.add(ingredient.isEmpty() ? Optional.empty() : Optional.of(exactIngredient(ingredient)));
		}
		this.placementInfo = PlacementInfo.createFromOptionals(ingredients);
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

	List<CraftingIngredientUse> ingredientUses() {
		return ingredientUses;
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

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(input);
		if (input.isEmpty()) return remainders;
		List<CraftingIngredientUse> uses = signature.matchesDirect(input)
				? ingredientUses : mirroredUses();
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack ingredient = input.getItem(slot);
			CraftingIngredientUse use = uses.get(slot);
			if (use != CraftingIngredientUse.DEFAULT) {
				remainders.set(slot, craftingRemainder(ingredient, use));
			}
		}
		return remainders;
	}

	static ItemStack craftingRemainder(ItemStack ingredient, CraftingIngredientUse craftingUse) {
		return switch (craftingUse) {
			case DEFAULT -> ingredient.getItem().getCraftingRemainder() == null ? ItemStack.EMPTY
					: ingredient.getItem().getCraftingRemainder().create();
			case CONSUME -> ItemStack.EMPTY;
			case KEEP -> ingredient.copyWithCount(1);
			case DAMAGE -> {
				ItemStack remainder = ingredient.copyWithCount(1);
				if (!remainder.isDamageableItem() || remainder.nextDamageWillBreak()) yield ItemStack.EMPTY;
				remainder.setDamageValue(remainder.getDamageValue() + 1);
				yield remainder;
			}
		};
	}

	private List<CraftingIngredientUse> mirroredUses() {
		List<CraftingIngredientUse> mirrored = new ArrayList<>(ingredientUses.size());
		for (int y = 0; y < signature.height(); y++) {
			for (int x = 0; x < signature.width(); x++) {
				mirrored.add(ingredientUses.get(signature.width() - x - 1 + y * signature.width()));
			}
		}
		return mirrored;
	}

	ItemStack outputStack() {
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
		return placementInfo;
	}

	@Override
	public List<RecipeDisplay> display() {
		ItemStack result = outputStack();
		if (result.isEmpty()) return List.of();
		List<SlotDisplay> ingredients = signature.ingredients().stream()
				.map(stack -> stack.isEmpty() ? SlotDisplay.Empty.INSTANCE
						: new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack)))
				.map(SlotDisplay.class::cast)
				.toList();
		return List.of(new ShapedCraftingRecipeDisplay(
				signature.width(),
				signature.height(),
				ingredients,
				new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(result)),
				new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
		));
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.MISC;
	}

	private static Ingredient exactIngredient(ItemStack stack) {
		return stack.getComponentsPatch().isEmpty()
				? Ingredient.of(stack.getItem())
				: DefaultCustomIngredients.components(stack);
	}
}
