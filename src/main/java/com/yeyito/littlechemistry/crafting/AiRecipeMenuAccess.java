package com.yeyito.littlechemistry.crafting;

import net.minecraft.server.level.ServerPlayer;

/** Common menu contract for asking the AI to invent a runtime recipe. */
public interface AiRecipeMenuAccess {
	int EMPTY_OR_VALID = 0;
	int MAKE_RECIPE_AVAILABLE = 1;
	int GENERATING = 2;
	int MAKE_RECIPE_BUTTON_ID = 0x4C43;

	int littleChemistry$getRecipeState();

	boolean littleChemistry$requestRecipe(ServerPlayer player);

	boolean littleChemistry$isLockedRecipeSlot(int slotIndex);
}
