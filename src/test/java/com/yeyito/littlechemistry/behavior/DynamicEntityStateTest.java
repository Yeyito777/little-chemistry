package com.yeyito.littlechemistry.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicEntityStateTest {
	@Test
	void typedValuesSurviveEncoding() {
		DynamicEntityState original = new DynamicEntityState();
		original.setString("mode", "guard");
		original.setInt("charge", 42);
		original.setDouble("range", 12.5);
		original.setBoolean("awake", true);

		DynamicEntityState decoded = new DynamicEntityState();
		decoded.decode(original.encode());

		assertEquals("guard", decoded.getString("mode", ""));
		assertEquals(42, decoded.getInt("charge", 0));
		assertEquals(12.5, decoded.getDouble("range", 0));
		assertTrue(decoded.getBoolean("awake", false));
		assertFalse(decoded.getBoolean("missing", false));
	}

	@Test
	void rejectsUnsafeKeysAndOversizedValues() {
		DynamicEntityState state = new DynamicEntityState();
		assertThrows(IllegalArgumentException.class, () -> state.setString("bad key", "value"));
		assertThrows(IllegalArgumentException.class, () -> state.setString("key", "x".repeat(1_025)));
	}
}
