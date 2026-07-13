package com.yeyito.littlechemistry.ai.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationModelTest {
	@Test
	void usesConcreteGpt56TierIds() {
		assertEquals("gpt-5.6-luna", GenerationModel.LUNA.modelId());
		assertEquals("gpt-5.6-terra", GenerationModel.TERRA.modelId());
		assertEquals("gpt-5.6-sol", GenerationModel.SOL.modelId());
	}

	@Test
	void parsesOnlySupportedWandChoices() {
		for (GenerationModel model : GenerationModel.values()) {
			assertEquals(model, GenerationModel.parse(model.serializedName()));
		}
		assertThrows(IllegalArgumentException.class, () -> GenerationModel.parse("gpt-5.6"));
	}
}
