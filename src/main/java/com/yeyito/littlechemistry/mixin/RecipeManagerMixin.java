package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(net.minecraft.world.item.crafting.RecipeManager.class)
public abstract class RecipeManagerMixin {
	@Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
			at = @At("RETURN"), cancellable = true)
	private <I extends RecipeInput, T extends Recipe<I>> void littleChemistry$findAiRecipe(
			RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Optional<RecipeHolder<T>>> result) {
		if (result.getReturnValue().isPresent()) return;
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager == null) return;
		if (type == RecipeType.CRAFTING && input instanceof CraftingInput craftingInput) {
			Optional<RecipeHolder<CraftingRecipe>> invented = manager.findRecipe(craftingInput, level);
			if (invented.isPresent()) {
				result.setReturnValue((Optional<RecipeHolder<T>>)(Optional<?>)invented);
			}
		} else if (type == RecipeType.SMELTING && input instanceof SingleRecipeInput smeltingInput) {
			Optional<RecipeHolder<SmeltingRecipe>> invented = manager.findSmeltingRecipe(smeltingInput, level);
			if (invented.isPresent()) {
				result.setReturnValue((Optional<RecipeHolder<T>>)(Optional<?>)invented);
			}
		}
	}

	@Inject(method = "byKey", at = @At("RETURN"), cancellable = true)
	private void littleChemistry$findAiRecipeByKey(ResourceKey<Recipe<?>> key,
			CallbackInfoReturnable<Optional<RecipeHolder<?>>> result) {
		if (result.getReturnValue().isPresent()) return;
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && manager.ownsRecipeManager((RecipeManager)(Object)this)) {
			manager.findRecipeByKey(key).ifPresent(recipe -> result.setReturnValue(Optional.of(recipe)));
		}
	}
}
