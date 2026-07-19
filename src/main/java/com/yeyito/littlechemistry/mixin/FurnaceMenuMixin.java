package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiFurnaceMenuAccess;
import com.yeyito.littlechemistry.crafting.LockedFurnaceInputSlot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceMenu.class)
public abstract class FurnaceMenuMixin extends AbstractFurnaceMenu implements AiFurnaceMenuAccess {
	@Unique private Container littleChemistry$furnace;
	@Unique private int littleChemistry$clientRecipeState;

	protected FurnaceMenuMixin(MenuType<?> menuType, ResourceKey<RecipePropertySet> acceptedInputs,
			RecipeBookType recipeBookType, int containerId, Inventory inventory, Container container,
			ContainerData data) {
		super(menuType, acceptedInputs, recipeBookType, containerId, inventory, container, data);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;)V", at = @At("TAIL"))
	private void littleChemistry$initializeClientMenu(int containerId, Inventory inventory, CallbackInfo callback) {
		littleChemistry$initialize();
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
			at = @At("TAIL"))
	private void littleChemistry$initializeServerMenu(int containerId, Inventory inventory, Container container,
			ContainerData data, CallbackInfo callback) {
		littleChemistry$initialize();
	}

	@Unique
	private void littleChemistry$initialize() {
		littleChemistry$furnace = this.slots.getFirst().container;
		LockedFurnaceInputSlot inputSlot = new LockedFurnaceInputSlot(
				littleChemistry$furnace, 0, 56, 17,
				() -> littleChemistry$getRecipeState() == GENERATING);
		inputSlot.index = 0;
		this.slots.set(0, inputSlot);
		this.addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return littleChemistry$recipeState();
			}

			@Override
			public void set(int value) {
				littleChemistry$clientRecipeState = value;
			}
		});
	}

	@Override
	public int littleChemistry$getRecipeState() {
		return littleChemistry$recipeState();
	}

	@Override
	public boolean littleChemistry$requestRecipe(ServerPlayer player) {
		AiCraftingManager manager = AiCraftingManager.active();
		return manager != null && manager.requestSmeltingRecipe(player, littleChemistry$furnace);
	}

	@Override
	public boolean littleChemistry$isLockedRecipeSlot(int slotIndex) {
		return slotIndex == 0;
	}

	@Override
	public Container littleChemistry$getFurnaceContainer() {
		return littleChemistry$furnace;
	}

	@Unique
	private int littleChemistry$recipeState() {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager == null || !manager.belongsTo(this.level)) return littleChemistry$clientRecipeState;
		if (manager.isSmeltingLocked(littleChemistry$furnace)) return GENERATING;
		ItemStack ingredient = littleChemistry$furnace.getItem(0);
		if (ingredient.isEmpty()) return EMPTY_OR_VALID;
		return manager.hasAnySmeltingRecipe(ingredient, this.level) ? EMPTY_OR_VALID : MAKE_RECIPE_AVAILABLE;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex == 0 && littleChemistry$getRecipeState() == GENERATING) {
			return ItemStack.EMPTY;
		}
		return super.quickMoveStack(player, slotIndex);
	}

	@Override
	protected boolean canSmelt(ItemStack stack) {
		if (super.canSmelt(stack)) return true;
		AiCraftingManager manager = AiCraftingManager.active();
		return manager != null && manager.belongsTo(this.level) && manager.hasSmeltingRecipe(stack, this.level);
	}
}
