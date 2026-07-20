package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicWorkstationButton;
import com.yeyito.littlechemistry.content.DynamicWorkstationButtonRole;
import com.yeyito.littlechemistry.content.DynamicWorkstationSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationStateChannel;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One fixed menu implementation for every synchronized AI-defined workstation layout. */
public final class DynamicWorkstationMenu extends AbstractContainerMenu implements AiRecipeMenuAccess {
	public static MenuType<DynamicWorkstationMenu> TYPE;

	private final DynamicWorkstationSpec specification;
	private final Container workstation;
	private final DynamicBlockEntity serverWorkstation;
	private final int machineSlots;
	private int clientRecipeState;
	private final int[] clientUiState;
	private final int[] clientLockWords = new int[4];

	public static void register() {
		TYPE = Registry.register(BuiltInRegistries.MENU, LittleChemistry.id("dynamic_workstation"),
				new ExtendedMenuType<>(DynamicWorkstationMenu::new, WorkstationOpenData.STREAM_CODEC));
	}

	/** Client constructor used by ExtendedMenuType. */
	public DynamicWorkstationMenu(int containerId, Inventory inventory, WorkstationOpenData openingData) {
		this(containerId, inventory, new SimpleContainer(openingData.specification().slots().size()),
				openingData.specification(), null);
	}

	/** Authoritative server constructor used by DynamicBlockEntity. */
	public DynamicWorkstationMenu(int containerId, Inventory inventory, DynamicBlockEntity workstation,
			DynamicWorkstationSpec specification) {
		this(containerId, inventory, workstation, specification, workstation);
	}

	private DynamicWorkstationMenu(int containerId, Inventory inventory, Container workstation,
			DynamicWorkstationSpec specification, DynamicBlockEntity serverWorkstation) {
		super(TYPE, containerId);
		this.specification = specification;
		this.workstation = workstation;
		this.serverWorkstation = serverWorkstation;
		this.machineSlots = specification.slots().size();
		this.clientUiState = new int[specification.ui().stateChannels().size()];
		for (int index = 0; index < machineSlots; index++) {
			this.addSlot(new WorkstationSlot(workstation, index, specification.slots().get(index), inventory.player));
		}
		this.addStandardInventorySlots(inventory,
				specification.ui().playerInventoryX(), specification.ui().playerInventoryY());
		this.addDataSlot(new DataSlot() {
			@Override public int get() {
				return DynamicWorkstationMenu.this.serverWorkstation == null
						? clientRecipeState : DynamicWorkstationMenu.this.serverWorkstation.recipeMenuState();
			}
			@Override public void set(int value) { clientRecipeState = value; }
		});
		for (int index = 0; index < specification.ui().stateChannels().size(); index++) {
			int channelIndex = index;
			DynamicWorkstationStateChannel channel = specification.ui().stateChannels().get(index);
			this.addDataSlot(new DataSlot() {
				@Override public int get() {
					return DynamicWorkstationMenu.this.serverWorkstation == null
							? clientUiState[channelIndex]
							: DynamicWorkstationMenu.this.serverWorkstation.uiState(channel.id());
				}
				@Override public void set(int value) { clientUiState[channelIndex] = value; }
			});
		}
		for (int word = 0; word < clientLockWords.length; word++) {
			int wordIndex = word;
			this.addDataSlot(new DataSlot() {
				@Override public int get() {
					return DynamicWorkstationMenu.this.serverWorkstation == null
							? clientLockWords[wordIndex]
							: (int) (DynamicWorkstationMenu.this.serverWorkstation.lockedSlotMask()
							>>> (wordIndex * 16)) & 0xFFFF;
				}
				@Override public void set(int value) { clientLockWords[wordIndex] = value & 0xFFFF; }
			});
		}
		workstation.startOpen(inventory.player);
	}

	public DynamicWorkstationSpec specification() {
		return specification;
	}

	public int uiState(String channelId) {
		for (int index = 0; index < specification.ui().stateChannels().size(); index++) {
			if (specification.ui().stateChannels().get(index).id().equals(channelId)) {
				return serverWorkstation == null ? clientUiState[index] : serverWorkstation.uiState(channelId);
			}
		}
		return 0;
	}

	@Override
	public int littleChemistry$getRecipeState() {
		return serverWorkstation == null ? clientRecipeState : serverWorkstation.recipeMenuState();
	}

	@Override
	public boolean littleChemistry$requestRecipe(ServerPlayer player) {
		AiCraftingManager manager = AiCraftingManager.active();
		DynamicWorkstationButton makeRecipe = specification.ui().buttons().stream()
				.filter(button -> button.role() == DynamicWorkstationButtonRole.MAKE_RECIPE)
				.findFirst().orElse(null);
		return manager != null && serverWorkstation != null && buttonAvailable(makeRecipe)
				&& manager.requestWorkstationRecipe(player, serverWorkstation);
	}

	@Override
	public boolean littleChemistry$isLockedRecipeSlot(int slotIndex) {
		if (slotIndex < 0 || slotIndex >= machineSlots) return false;
		if (serverWorkstation != null) return serverWorkstation.isSlotLocked(slotIndex);
		return (clientLockWords[slotIndex / 16] & (1 << (slotIndex % 16))) != 0;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId >= 0 && buttonId < specification.ui().buttons().size()
				&& player instanceof ServerPlayer serverPlayer && serverWorkstation != null) {
			DynamicWorkstationButton button = specification.ui().buttons().get(buttonId);
			if (button.role() == DynamicWorkstationButtonRole.CUSTOM && !serverWorkstation.isWorkstationLocked()
					&& buttonAvailable(button)) {
				return serverWorkstation.customButton(serverPlayer, button.id());
			}
		}
		return super.clickMenuButton(player, buttonId);
	}

	private boolean buttonAvailable(DynamicWorkstationButton button) {
		if (button == null) return false;
		return (button.visibleChannel() == null || uiState(button.visibleChannel()) != 0)
				&& (button.enabledChannel() == null || uiState(button.enabledChannel()) != 0);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
		Slot sourceSlot = slots.get(slotIndex);
		if (!sourceSlot.mayPickup(player)) return ItemStack.EMPTY;
		ItemStack source = sourceSlot.getItem();
		if (source.isEmpty()) return ItemStack.EMPTY;
		ItemStack original = source.copy();
		if (slotIndex < machineSlots) {
			if (!moveItemStackTo(source, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveIntoWorkstation(source)) {
			return ItemStack.EMPTY;
		}
		if (source.isEmpty()) sourceSlot.setByPlayer(ItemStack.EMPTY);
		else sourceSlot.setChanged();
		if (source.getCount() == original.getCount()) return ItemStack.EMPTY;
		sourceSlot.onTake(player, source);
		broadcastChanges();
		return original;
	}

	/** AbstractContainerMenu's merge pass does not call Slot.mayPlace, so heterogeneous machine slots need this. */
	private boolean moveIntoWorkstation(ItemStack source) {
		boolean moved = false;
		for (int index = 0; index < machineSlots && !source.isEmpty(); index++) {
			Slot target = slots.get(index);
			if (!target.hasItem()) continue;
			ItemStack existing = target.getItem();
			if (!ItemStack.isSameItemSameComponents(existing, source)) continue;
			int capacity = target.getMaxStackSize(source);
			int amount = Math.min(source.getCount(), capacity - existing.getCount());
			if (amount <= 0 || !target.mayPlace(source.copyWithCount(amount))) continue;
			existing.grow(amount);
			source.shrink(amount);
			target.setChanged();
			moved = true;
		}
		for (int index = 0; index < machineSlots && !source.isEmpty(); index++) {
			Slot target = slots.get(index);
			if (target.hasItem()) continue;
			int amount = Math.min(source.getCount(), target.getMaxStackSize(source));
			if (amount <= 0 || !target.mayPlace(source.copyWithCount(amount))) continue;
			target.setByPlayer(source.split(amount));
			target.setChanged();
			moved = true;
		}
		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return serverWorkstation == null || serverWorkstation.isValidWorkstation(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		workstation.stopOpen(player);
	}
}
