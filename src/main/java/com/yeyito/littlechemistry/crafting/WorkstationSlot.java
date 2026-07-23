package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.behavior.WorkstationSlotAction;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlot;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlotIcon;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Slot implementing the declarative workstation envelope plus guarded generated rules on the server. */
final class WorkstationSlot extends Slot {
	private final DynamicWorkstationSlot specification;
	private final Player owner;

	WorkstationSlot(Container container, int index, DynamicWorkstationSlot specification, Player owner) {
		super(container, index, specification.x(), specification.y());
		this.specification = specification;
		this.owner = owner;
	}

	@Override
	public ItemStack getItem() {
		if (container instanceof DynamicBlockEntity workstation) {
			ItemStack rejection = workstation.rejectionDisplayStack(index);
			if (!rejection.isEmpty()) return rejection;
		}
		return super.getItem();
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		if (WorkstationRecipeRejection.isDisplayStack(getItem())) return false;
		if (container instanceof DynamicBlockEntity workstation) {
			return owner instanceof ServerPlayer serverPlayer
					&& workstation.mayUseSlot(index, stack, WorkstationSlotAction.INSERT, serverPlayer, null);
		}
		return specification.allowPlayerInsert();
	}

	@Override
	public ItemStack safeClone(Player player) {
		return WorkstationRecipeRejection.isDisplayStack(getItem()) ? ItemStack.EMPTY : super.safeClone(player);
	}

	@Override
	public boolean mayPickup(Player player) {
		if (WorkstationRecipeRejection.isDisplayStack(getItem())) return false;
		if (container instanceof DynamicBlockEntity workstation) {
			return player instanceof ServerPlayer serverPlayer
					&& workstation.mayUseSlot(index, getItem(), WorkstationSlotAction.EXTRACT, serverPlayer, null);
		}
		return specification.allowPlayerExtract();
	}

	@Override
	public int getMaxStackSize() {
		return specification.maxStack();
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return Math.min(specification.maxStack(), stack.getMaxStackSize());
	}

	@Override
	public Identifier getNoItemIcon() {
		return DynamicWorkstationSlotIcon.resolve(specification.emptySlotIcon());
	}
}
