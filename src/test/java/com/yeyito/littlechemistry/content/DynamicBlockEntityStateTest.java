package com.yeyito.littlechemistry.content;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DynamicBlockEntityStateTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void generatedStateIsIncludedInInitialAndIncrementalUpdateTagShape() {
		CompoundTag update = new CompoundTag();
		DynamicBlockEntity.appendGeneratedStateUpdate(update, Map.of("visual", "lit", "mode", "charging"));

		CompoundTag state = update.getCompound("generated_state").orElseThrow();
		assertEquals("lit", state.getString("visual").orElseThrow());
		assertEquals("charging", state.getString("mode").orElseThrow());
	}

	@Test
	void emptyGeneratedStateDoesNotAddUnnecessaryPayload() {
		CompoundTag update = new CompoundTag();
		DynamicBlockEntity.appendGeneratedStateUpdate(update, Map.of());

		assertFalse(update.contains("generated_state"));
	}
}
