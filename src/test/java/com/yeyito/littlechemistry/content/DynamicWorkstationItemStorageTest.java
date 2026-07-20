package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.WorkstationSlotAction;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicWorkstationItemStorageTest {
	@BeforeAll
	@SuppressWarnings("deprecation")
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Plain JUnit does not run the server data-component loading phase.
		if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
			Items.STONE.builtInRegistryHolder().bindComponents(
					DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
		}
	}

	@Test
	void exposesOnlyDeclaredSlotsAndNeverAcceptsPastSlotCapacity() {
		FakeWorkstation workstation = new FakeWorkstation(slots(2));
		DynamicWorkstationItemStorage storage = new DynamicWorkstationItemStorage(workstation, null);
		ItemVariant stone = new TestItemVariant(Items.STONE);

		try (Transaction transaction = Transaction.openOuter()) {
			assertEquals(2, storage.insert(stone, Long.MAX_VALUE, transaction));
			transaction.commit();
		}

		assertEquals(2, storage.getSlotCount());
		assertEquals(2, storage.getSlot(0).getCapacity());
		assertEquals(2, storage.getSlot(0).getAmount());
		assertTrue(storage.getSlot(1).isResourceBlank());

		workstation.stacks.set(0, new ItemStack(Items.STONE, 3));
		try (Transaction transaction = Transaction.openOuter()) {
			assertEquals(0, storage.getSlot(0).extract(stone, 1, transaction));
		}
		assertEquals(3, workstation.stacks.get(0).getCount());
	}

	@Test
	void appliesDeclarativeRulesLocksAndGeneratedRulesForNullAndSidedAccess() {
		FakeWorkstation workstation = new FakeWorkstation(slots(4));
		ItemVariant stone = new TestItemVariant(Items.STONE);

		workstation.sideRule = side -> side == null;
		try (Transaction transaction = Transaction.openOuter()) {
			DynamicWorkstationItemStorage nullSide = new DynamicWorkstationItemStorage(workstation, null);
			assertEquals(1, nullSide.insert(stone, 1, transaction));
			transaction.commit();
		}
		assertNull(workstation.lastSide);

		workstation.clear();
		try (Transaction transaction = Transaction.openOuter()) {
			DynamicWorkstationItemStorage north = new DynamicWorkstationItemStorage(workstation, Direction.NORTH);
			assertEquals(0, north.insert(stone, 1, transaction));
		}
		assertEquals(Direction.NORTH, workstation.lastSide);

		workstation.sideRule = side -> true;
		workstation.lockedSlots.add(0);
		try (Transaction transaction = Transaction.openOuter()) {
			DynamicWorkstationItemStorage locked = new DynamicWorkstationItemStorage(workstation, Direction.DOWN);
			assertEquals(0, locked.insert(stone, 1, transaction));
		}

		workstation.lockedSlots.clear();
		try (Transaction transaction = Transaction.openOuter()) {
			DynamicWorkstationItemStorage output = new DynamicWorkstationItemStorage(workstation, Direction.DOWN);
			assertEquals(0, output.getSlot(1).insert(stone, 1, transaction));
		}
	}

	@Test
	void nestedAndOuterRollbackRestoreIndependentStackSnapshots() {
		FakeWorkstation workstation = new FakeWorkstation(slots(4));
		workstation.stacks.set(0, new ItemStack(Items.STONE));
		DynamicWorkstationItemStorage storage = new DynamicWorkstationItemStorage(workstation, null);
		ItemVariant stone = new TestItemVariant(Items.STONE);

		try (Transaction outer = Transaction.openOuter()) {
			assertEquals(1, storage.getSlot(0).insert(stone, 1, outer));
			assertEquals(2, storage.getSlot(0).getAmount());

			try (Transaction nested = outer.openNested()) {
				assertEquals(2, storage.getSlot(0).extract(stone, 2, nested));
				assertTrue(storage.getSlot(0).isResourceBlank());
			}

			assertEquals(2, storage.getSlot(0).getAmount());
		}

		assertEquals(1, storage.getSlot(0).getAmount());
	}

	private static List<DynamicWorkstationSlot> slots(int inputMaxStack) {
		return List.of(
				new DynamicWorkstationSlot("input", DynamicWorkstationSlotRole.INPUT,
						0, 0, inputMaxStack, true, true, null, null),
				new DynamicWorkstationSlot("output", DynamicWorkstationSlotRole.OUTPUT,
						18, 0, 64, false, true, null, null)
		);
	}

	/** Avoids relying on Fabric's Item mixin in the plain (non-Fabric-Loader) JUnit worker. */
	private record TestItemVariant(Item item) implements ItemVariant {
		@Override
		public boolean isBlank() {
			return false;
		}

		@Override
		public Item getObject() {
			return item;
		}

		@Override
		public DataComponentPatch getComponentsPatch() {
			return DataComponentPatch.EMPTY;
		}

		@Override
		public DataComponentMap getComponents() {
			return item.components();
		}

		@Override
		public ItemVariant withComponents(DataComponentPatch components) {
			if (!components.isEmpty()) throw new UnsupportedOperationException("Test variant only supports default components");
			return this;
		}
	}

	private static final class FakeWorkstation implements DynamicWorkstationItemStorage.WorkstationAccess {
		private final List<DynamicWorkstationSlot> slots;
		private final List<ItemStack> stacks;
		private final Set<Integer> lockedSlots = new HashSet<>();
		private Predicate<Direction> sideRule = side -> true;
		private @Nullable Direction lastSide;

		private FakeWorkstation(List<DynamicWorkstationSlot> slots) {
			this.slots = List.copyOf(slots);
			this.stacks = new ArrayList<>();
			for (int ignored = 0; ignored < slots.size(); ignored++) stacks.add(ItemStack.EMPTY);
		}

		@Override
		public List<DynamicWorkstationSlot> slots() {
			return slots;
		}

		@Override
		public ItemStack stack(String slotId) {
			return stacks.get(index(slotId)).copy();
		}

		@Override
		public void setStack(String slotId, ItemStack stack) {
			stacks.set(index(slotId), stack.copy());
		}

		@Override
		public boolean isSlotLocked(int slot) {
			return lockedSlots.contains(slot);
		}

		@Override
		public boolean mayUseSlot(int slot, ItemStack stack, WorkstationSlotAction action,
				@Nullable Direction side) {
			lastSide = side;
			DynamicWorkstationSlot specification = slots.get(slot);
			boolean declarative = action == WorkstationSlotAction.INSERT
					? specification.allowPlayerInsert() : specification.allowPlayerExtract();
			return declarative && sideRule.test(side);
		}

		private int index(String slotId) {
			for (int index = 0; index < slots.size(); index++) {
				if (slots.get(index).id().equals(slotId)) return index;
			}
			throw new IllegalArgumentException("Unknown slot " + slotId);
		}

		private void clear() {
			for (int index = 0; index < stacks.size(); index++) stacks.set(index, ItemStack.EMPTY);
		}
	}
}
