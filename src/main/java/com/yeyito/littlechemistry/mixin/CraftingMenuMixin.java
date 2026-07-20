package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiCraftingMenuAccess;
import com.yeyito.littlechemistry.crafting.LockedCraftingSlot;
import com.yeyito.littlechemistry.crafting.PortableCraftingAccess;
import com.yeyito.littlechemistry.crafting.PortableCraftingComponents;
import com.yeyito.littlechemistry.crafting.SharedCraftingContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin extends AbstractCraftingMenu implements AiCraftingMenuAccess {
	@Unique private @Nullable SharedCraftingContainer littleChemistry$table;
	@Unique private @Nullable ServerLevel littleChemistry$level;
	@Unique private boolean littleChemistry$portable;
	@Unique private @Nullable UUID littleChemistry$portableId;
	@Unique private int littleChemistry$clientRecipeState;

	protected CraftingMenuMixin(MenuType<?> menuType, int containerId, int width, int height) {
		super(menuType, containerId, width, height);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void littleChemistry$useSharedTable(int containerId, Inventory inventory, ContainerLevelAccess access,
			CallbackInfo callback) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (access instanceof PortableCraftingAccess portableAccess) {
			littleChemistry$portable = true;
			littleChemistry$portableId = portableAccess.tableId();
			if (portableAccess.level() instanceof ServerLevel serverLevel && manager != null) {
				littleChemistry$level = serverLevel;
				littleChemistry$table = manager.portableTable(
						serverLevel, portableAccess.tableId(), portableAccess.carrier());
			}
		} else {
			access.execute((level, pos) -> {
				if (level instanceof ServerLevel serverLevel && manager != null) {
					littleChemistry$level = serverLevel;
					littleChemistry$table = manager.table(serverLevel, pos);
				}
			});
		}

		if (littleChemistry$table != null) {
			((AbstractCraftingMenuAccessor)this).littleChemistry$setCraftSlots(littleChemistry$table);
			ResultSlot resultSlot = new ResultSlot(inventory.player, littleChemistry$table, this.resultSlots, 0, 124, 35);
			resultSlot.index = 0;
			this.slots.set(0, resultSlot);
			for (int slot = 0; slot < 9; slot++) {
				int x = slot % 3;
				int y = slot / 3;
				LockedCraftingSlot craftingSlot = new LockedCraftingSlot(littleChemistry$table, slot,
						30 + x * 18, 17 + y * 18);
				craftingSlot.index = slot + 1;
				this.slots.set(slot + 1, craftingSlot);
			}
			littleChemistry$table.addViewer((CraftingMenu)(Object)this);
		}

		this.addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return littleChemistry$serverRecipeState();
			}

			@Override
			public void set(int value) {
				littleChemistry$clientRecipeState = value;
			}
		});

		if (littleChemistry$table != null && littleChemistry$level != null && inventory.player instanceof ServerPlayer serverPlayer) {
			littleChemistry$initializeResult(littleChemistry$level, serverPlayer);
		}
	}

	@Unique
	private void littleChemistry$initializeResult(ServerLevel level, ServerPlayer player) {
		ItemStack result = ItemStack.EMPTY;
		RecipeHolder<CraftingRecipe> recipe = level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, littleChemistry$table.asCraftInput(), level)
				.orElse(null);
		if (recipe != null && this.resultSlots.setRecipeUsed(player, recipe)) {
			ItemStack assembled = recipe.value().assemble(littleChemistry$table.asCraftInput());
			if (assembled.isItemEnabled(level.enabledFeatures())) result = assembled;
		}
		this.resultSlots.setItem(0, result);
	}

	@Inject(method = "removed", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$keepSharedContents(Player player, CallbackInfo callback) {
		if (littleChemistry$table == null) return;
		if (littleChemistry$portable) {
			ItemStack carrier = littleChemistry$findPortableCarrier(player);
			if (!carrier.isEmpty()) littleChemistry$table.attachPortableCarrier(carrier);
		}
		littleChemistry$table.removeViewer((CraftingMenu)(Object)this);
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack carried = this.getCarried();
			if (!carried.isEmpty()) {
				boolean removed = player.isRemoved()
						&& player.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION;
				if (removed || serverPlayer.hasDisconnected()) player.drop(carried, false);
				else player.getInventory().placeItemBackInInventory(carried);
				this.setCarried(ItemStack.EMPTY);
			}
		}
		callback.cancel();
	}

	@Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$keepPortableMenuValid(Player player,
			CallbackInfoReturnable<Boolean> result) {
		if (littleChemistry$portable) result.setReturnValue(!littleChemistry$findPortableCarrier(player).isEmpty());
	}

	@Override
	public int littleChemistry$getRecipeState() {
		return littleChemistry$serverRecipeState();
	}

	@Override
	public boolean littleChemistry$requestRecipe(ServerPlayer player) {
		AiCraftingManager manager = AiCraftingManager.active();
		return manager != null && littleChemistry$table != null && manager.requestRecipe(player, littleChemistry$table);
	}

	@Override
	public boolean littleChemistry$isLockedRecipeSlot(int slotIndex) {
		return slotIndex >= 0 && slotIndex <= 9;
	}

	@Override
	public boolean littleChemistry$isProtectedCarrierInteraction(
			int slotIndex, int buttonNum, ContainerInput input, Player player) {
		if (!littleChemistry$portable || littleChemistry$portableId == null) return false;
		if (littleChemistry$isPortableCarrier(this.getCarried())) return true;
		if (slotIndex >= 0 && slotIndex < this.slots.size()
				&& littleChemistry$isPortableCarrier(this.slots.get(slotIndex).getItem())) {
			return true;
		}
		return input == ContainerInput.SWAP
				&& ((buttonNum >= 0 && buttonNum < 9) || buttonNum == Inventory.SLOT_OFFHAND)
				&& littleChemistry$isPortableCarrier(player.getInventory().getItem(buttonNum));
	}

	@Override
	public @Nullable SharedCraftingContainer littleChemistry$getSharedTable() {
		return littleChemistry$table;
	}

	@Unique
	private int littleChemistry$serverRecipeState() {
		if (littleChemistry$table == null || littleChemistry$level == null) return littleChemistry$clientRecipeState;
		if (littleChemistry$table.isLocked()) return GENERATING;
		if (littleChemistry$table.isEmpty()) return EMPTY_OR_VALID;
		return littleChemistry$level.getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, littleChemistry$table.asCraftInput(), littleChemistry$level)
					.isPresent() ? EMPTY_OR_VALID : MAKE_RECIPE_AVAILABLE;
	}

	@Unique
	private ItemStack littleChemistry$findPortableCarrier(Player player) {
		ItemStack carried = this.getCarried();
		if (littleChemistry$isPortableCarrier(carried)) return carried;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (littleChemistry$isPortableCarrier(candidate)) return candidate;
		}
		return ItemStack.EMPTY;
	}

	@Unique
	private boolean littleChemistry$isPortableCarrier(ItemStack stack) {
		return !stack.isEmpty() && littleChemistry$portableId != null
				&& littleChemistry$portableId.equals(stack.get(PortableCraftingComponents.TABLE_ID));
	}
}
