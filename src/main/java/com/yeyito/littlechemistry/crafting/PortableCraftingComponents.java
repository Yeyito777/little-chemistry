package com.yeyito.littlechemistry.crafting;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.UUID;

/** Persistent item components that identify and render one portable crafting grid. */
public final class PortableCraftingComponents {
	public static DataComponentType<UUID> TABLE_ID;
	public static DataComponentType<PortableCraftingState> STATE;

	private PortableCraftingComponents() {
	}

	public static void register() {
		TABLE_ID = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				LittleChemistry.id("portable_crafting_table_id"),
				DataComponentType.<UUID>builder()
						.persistent(UUIDUtil.STRING_CODEC)
						.networkSynchronized(UUIDUtil.STREAM_CODEC)
						.build()
		);
		STATE = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				LittleChemistry.id("portable_crafting_state"),
				DataComponentType.<PortableCraftingState>builder()
						.persistent(PortableCraftingState.CODEC)
						.networkSynchronized(PortableCraftingState.STREAM_CODEC)
						.build()
		);
	}
}
