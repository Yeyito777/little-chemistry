package com.yeyito.littlechemistry.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicActionInputTest {
	@Test
	void decodesEveryFixedActionChannelIndependently() {
		DynamicActionInput input = DynamicActionInput.fromMask(1 | 4 | 16);

		assertTrue(input.primary());
		assertFalse(input.secondary());
		assertTrue(input.ascend());
		assertFalse(input.descend());
		assertTrue(input.modeSwitch());
	}

	@Test
	void absentPlayerHasNoActionInput() {
		assertEquals(DynamicActionInput.NONE, DynamicActionInput.capture(null));
	}
}
