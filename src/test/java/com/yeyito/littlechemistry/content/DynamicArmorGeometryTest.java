package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DynamicArmorGeometryTest {
	@Test
	void validatesUvNetsAndEquipmentSlotAnchorsWithoutConceptSpecificRules() {
		DynamicArmorGeometry headGeometry = new DynamicArmorGeometry(List.of(part(
				"upper_ring", DynamicArmorAnchor.HEAD, 0, 0, -5, -9, -5, 10, 1, 10)));
		assertDoesNotThrow(() -> headGeometry.validateFor(DynamicArmorSlot.HEAD));
		assertThrows(IllegalArgumentException.class, () -> headGeometry.validateFor(DynamicArmorSlot.CHEST));

		assertThrows(IllegalArgumentException.class, () -> new DynamicArmorGeometry(List.of(part(
				"oversized_uv", DynamicArmorAnchor.HEAD, 50, 20, -5, -9, -5, 10, 1, 10))));
	}

	private static DynamicArmorGeometryPart part(String id, DynamicArmorAnchor anchor, int u, int v,
			float x, float y, float z, float width, float height, float depth) {
		return new DynamicArmorGeometryPart(id, anchor, u, v, x, y, z, width, height, depth,
				0, 0, 0, 0, 0, 0, 0, false);
	}
}
