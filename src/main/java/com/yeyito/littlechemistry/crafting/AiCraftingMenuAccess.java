package com.yeyito.littlechemistry.crafting;

import org.jspecify.annotations.Nullable;

public interface AiCraftingMenuAccess {
	int EMPTY_OR_VALID = 0;
	int MAKE_RECIPE_AVAILABLE = 1;
	int GENERATING = 2;
	int MAKE_RECIPE_BUTTON_ID = 0x4C43;

	int littleChemistry$getRecipeState();

	@Nullable SharedCraftingContainer littleChemistry$getSharedTable();
}
