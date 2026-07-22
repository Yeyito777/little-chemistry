package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.entity.player.Input;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicRiderInputTest {
	@Test
	void convertsSynchronizedButtonsToSignedAxes() {
		DynamicRiderInput input = DynamicRiderInput.from(
				new Input(true, false, true, false, true, false, true), 35.0F, -20.0F);

		assertEquals(1.0F, input.forward());
		assertEquals(1.0F, input.strafe());
		assertTrue(input.jump());
		assertFalse(input.shift());
		assertTrue(input.sprint());
		assertEquals(35.0F, input.yaw());
		assertEquals(-20.0F, input.pitch());
	}

	@Test
	void opposingButtonsCancelAndReverseAxesRemainNegative() {
		DynamicRiderInput cancelled = DynamicRiderInput.from(
				new Input(true, true, true, true, false, true, false), 0.0F, 0.0F);
		assertEquals(0.0F, cancelled.forward());
		assertEquals(0.0F, cancelled.strafe());
		assertTrue(cancelled.shift());

		DynamicRiderInput reverse = DynamicRiderInput.from(
				new Input(false, true, false, true, false, false, false), 0.0F, 0.0F);
		assertEquals(-1.0F, reverse.forward());
		assertEquals(-1.0F, reverse.strafe());
	}
}
