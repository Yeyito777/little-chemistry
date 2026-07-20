package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.WorkstationSlotAction;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Fabric item storage for the declarative slots of one dynamic workstation.
 *
 * <p>This deliberately does not wrap the workstation as a {@code Container}: its container has a fixed persistence
 * capacity larger than many workstation layouts, and the generic wrapper does not apply null-side extraction rules.
 * Every accepted mutation is bounded before it reaches {@link DynamicBlockEntity#setStack(String, ItemStack)}, and
 * every transaction depth receives a defensive {@link ItemStack} snapshot.</p>
 */
public final class DynamicWorkstationItemStorage implements SlottedStorage<ItemVariant> {
	private final List<WorkstationSlotStorage> slots;

	public DynamicWorkstationItemStorage(DynamicBlockEntity workstation, @Nullable Direction side) {
		this(blockEntityAccess(workstation), side);
	}

	DynamicWorkstationItemStorage(WorkstationAccess workstation, @Nullable Direction side) {
		Objects.requireNonNull(workstation, "workstation");
		List<DynamicWorkstationSlot> specifications = List.copyOf(workstation.slots());
		List<WorkstationSlotStorage> slots = new ArrayList<>(specifications.size());
		for (DynamicWorkstationSlot slot : specifications) {
			slots.add(new WorkstationSlotStorage(workstation, slot.id(), side));
		}
		this.slots = List.copyOf(slots);
	}

	@Override
	public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		Objects.requireNonNull(transaction, "transaction");
		long inserted = 0;
		for (WorkstationSlotStorage slot : slots) {
			inserted += slot.insert(resource, maxAmount - inserted, transaction);
			if (inserted == maxAmount) break;
		}
		return inserted;
	}

	@Override
	public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notBlankNotNegative(resource, maxAmount);
		Objects.requireNonNull(transaction, "transaction");
		long extracted = 0;
		for (WorkstationSlotStorage slot : slots) {
			extracted += slot.extract(resource, maxAmount - extracted, transaction);
			if (extracted == maxAmount) break;
		}
		return extracted;
	}

	@Override
	public int getSlotCount() {
		return slots.size();
	}

	@Override
	public SingleSlotStorage<ItemVariant> getSlot(int slot) {
		return slots.get(slot);
	}

	@Override
	public Iterator<StorageView<ItemVariant>> iterator() {
		return slots.stream().map(slot -> (StorageView<ItemVariant>) slot).iterator();
	}

	private static WorkstationAccess blockEntityAccess(DynamicBlockEntity workstation) {
		Objects.requireNonNull(workstation, "workstation");
		if (workstation.workstationDefinition() == null) {
			throw new IllegalArgumentException("Block entity is not a dynamic workstation");
		}
		return new BlockEntityAccess(workstation);
	}

	interface WorkstationAccess {
		List<DynamicWorkstationSlot> slots();

		ItemStack stack(String slotId);

		void setStack(String slotId, ItemStack stack);

		boolean isSlotLocked(int slot);

		boolean mayUseSlot(int slot, ItemStack stack, WorkstationSlotAction action, @Nullable Direction side);
	}

	private record BlockEntityAccess(DynamicBlockEntity workstation) implements WorkstationAccess {
		@Override
		public List<DynamicWorkstationSlot> slots() {
			DynamicContentDefinition definition = workstation.workstationDefinition();
			return definition == null ? List.of() : definition.workstation().slots();
		}

		@Override
		public ItemStack stack(String slotId) {
			return workstation.stack(slotId);
		}

		@Override
		public void setStack(String slotId, ItemStack stack) {
			workstation.setStack(slotId, stack);
		}

		@Override
		public boolean isSlotLocked(int slot) {
			return workstation.isSlotLocked(slot);
		}

		@Override
		public boolean mayUseSlot(int slot, ItemStack stack, WorkstationSlotAction action,
				@Nullable Direction side) {
			return workstation.mayUseSlot(slot, stack, action, null, side);
		}
	}

	private static final class WorkstationSlotStorage extends SnapshotParticipant<ItemStack>
			implements SingleSlotStorage<ItemVariant> {
		private final WorkstationAccess workstation;
		private final String slotId;
		private final @Nullable Direction side;

		private WorkstationSlotStorage(WorkstationAccess workstation, String slotId, @Nullable Direction side) {
			this.workstation = workstation;
			this.slotId = slotId;
			this.side = side;
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			Objects.requireNonNull(transaction, "transaction");
			if (maxAmount == 0) return 0;

			ResolvedSlot initial = resolve();
			if (initial == null) return 0;
			ItemStack current = stack(initial);
			long proposedAmount = insertionAmount(initial, current, resource, maxAmount);
			if (proposedAmount == 0 || workstation.isSlotLocked(initial.index())) return 0;
			ItemStack proposed = resource.toStack(Math.toIntExact(proposedAmount));
			if (!workstation.mayUseSlot(initial.index(), proposed, WorkstationSlotAction.INSERT, side)) return 0;

			ResolvedSlot approved = resolveUnchanged(initial);
			if (approved == null || workstation.isSlotLocked(approved.index())) return 0;
			current = stack(approved);
			long accepted = insertionAmount(approved, current, resource, maxAmount);
			if (accepted == 0) return 0;

			updateSnapshots(transaction);
			ResolvedSlot mutation = resolveUnchanged(approved);
			if (mutation == null || workstation.isSlotLocked(mutation.index())) return 0;
			current = stack(mutation);
			accepted = insertionAmount(mutation, current, resource, maxAmount);
			if (accepted == 0) return 0;

			ItemStack replacement;
			if (current.isEmpty()) {
				replacement = resource.toStack(Math.toIntExact(accepted));
			} else {
				replacement = current.copy();
				replacement.grow(Math.toIntExact(accepted));
			}
			setExact(mutation, replacement);
			return accepted;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			Objects.requireNonNull(transaction, "transaction");
			if (maxAmount == 0) return 0;

			ResolvedSlot initial = resolve();
			if (initial == null) return 0;
			ItemStack current = stack(initial);
			long proposedAmount = extractionAmount(initial, current, resource, maxAmount);
			if (proposedAmount == 0 || workstation.isSlotLocked(initial.index())) return 0;
			ItemStack proposed = current.copy();
			proposed.setCount(Math.toIntExact(proposedAmount));
			if (!workstation.mayUseSlot(initial.index(), proposed, WorkstationSlotAction.EXTRACT, side)) return 0;

			ResolvedSlot approved = resolveUnchanged(initial);
			if (approved == null || workstation.isSlotLocked(approved.index())) return 0;
			current = stack(approved);
			long accepted = extractionAmount(approved, current, resource, maxAmount);
			if (accepted == 0) return 0;

			updateSnapshots(transaction);
			ResolvedSlot mutation = resolveUnchanged(approved);
			if (mutation == null || workstation.isSlotLocked(mutation.index())) return 0;
			current = stack(mutation);
			accepted = extractionAmount(mutation, current, resource, maxAmount);
			if (accepted == 0) return 0;

			ItemStack replacement = current.copy();
			replacement.shrink(Math.toIntExact(accepted));
			setExact(mutation, replacement);
			return accepted;
		}

		@Override
		public boolean isResourceBlank() {
			ResolvedSlot slot = resolve();
			return slot == null || stack(slot).isEmpty();
		}

		@Override
		public ItemVariant getResource() {
			ResolvedSlot slot = resolve();
			return slot == null ? ItemVariant.blank() : ItemVariant.of(stack(slot));
		}

		@Override
		public long getAmount() {
			ResolvedSlot slot = resolve();
			return slot == null ? 0 : stack(slot).getCount();
		}

		@Override
		public long getCapacity() {
			ResolvedSlot slot = resolve();
			if (slot == null) return 0;
			ItemStack stack = stack(slot);
			return stack.isEmpty() ? slot.specification().maxStack()
					: Math.min(slot.specification().maxStack(), stack.getMaxStackSize());
		}

		@Override
		protected ItemStack createSnapshot() {
			ResolvedSlot slot = resolve();
			if (slot == null) throw new IllegalStateException("Workstation slot disappeared during a transaction: " + slotId);
			return stack(slot).copy();
		}

		@Override
		protected void readSnapshot(ItemStack snapshot) {
			ResolvedSlot slot = resolve();
			if (slot == null) throw new IllegalStateException("Workstation slot disappeared during rollback: " + slotId);
			setExact(slot, snapshot.copy());
		}

		private long insertionAmount(ResolvedSlot slot, ItemStack current, ItemVariant resource, long maxAmount) {
			if ((!current.isEmpty() && !resource.matches(current)) || !validStoredStack(slot, current)) return 0;
			return Math.min(maxAmount, (long) capacity(slot, resource) - current.getCount());
		}

		private long extractionAmount(ResolvedSlot slot, ItemStack current, ItemVariant resource, long maxAmount) {
			if (current.isEmpty() || !resource.matches(current) || !validStoredStack(slot, current)) return 0;
			return Math.min(maxAmount, current.getCount());
		}

		private boolean validStoredStack(ResolvedSlot slot, ItemStack stack) {
			return stack.isEmpty() || stack.getCount() <= Math.min(
					slot.specification().maxStack(), stack.getMaxStackSize());
		}

		private int capacity(ResolvedSlot slot, ItemVariant resource) {
			if (resource.isBlank()) return slot.specification().maxStack();
			return Math.min(slot.specification().maxStack(), resource.toStack().getMaxStackSize());
		}

		private ItemStack stack(ResolvedSlot slot) {
			return workstation.stack(slot.specification().id()).copy();
		}

		private void setExact(ResolvedSlot slot, ItemStack replacement) {
			if (!replacement.isEmpty()) {
				int capacity = Math.min(slot.specification().maxStack(), replacement.getMaxStackSize());
				if (replacement.getCount() > capacity) {
					throw new IllegalStateException("Refusing to truncate workstation slot " + slotId);
				}
			}
			workstation.setStack(slot.specification().id(), replacement.copy());
			ItemStack stored = workstation.stack(slot.specification().id());
			if (!sameStack(stored, replacement)) {
				throw new IllegalStateException("Workstation slot did not retain the complete transferred stack: " + slotId);
			}
		}

		private @Nullable ResolvedSlot resolve() {
			List<DynamicWorkstationSlot> specifications = workstation.slots();
			for (int index = 0; index < specifications.size(); index++) {
				DynamicWorkstationSlot specification = specifications.get(index);
				if (specification.id().equals(slotId)) return new ResolvedSlot(index, specification);
			}
			return null;
		}

		private @Nullable ResolvedSlot resolveUnchanged(ResolvedSlot expected) {
			ResolvedSlot current = resolve();
			return expected.equals(current) ? current : null;
		}

		private static boolean sameStack(ItemStack first, ItemStack second) {
			if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
			return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
		}

		private record ResolvedSlot(int index, DynamicWorkstationSlot specification) {
		}
	}
}
