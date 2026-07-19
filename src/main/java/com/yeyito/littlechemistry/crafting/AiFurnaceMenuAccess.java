package com.yeyito.littlechemistry.crafting;

import net.minecraft.world.Container;

/** Exposes the physical furnace inventory used by a server-side furnace menu. */
public interface AiFurnaceMenuAccess extends AiRecipeMenuAccess {
	Container littleChemistry$getFurnaceContainer();
}
