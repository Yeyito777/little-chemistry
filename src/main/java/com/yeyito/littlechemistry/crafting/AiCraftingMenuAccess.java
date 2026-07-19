package com.yeyito.littlechemistry.crafting;

import org.jspecify.annotations.Nullable;

public interface AiCraftingMenuAccess extends AiRecipeMenuAccess {
	@Nullable SharedCraftingContainer littleChemistry$getSharedTable();
}
