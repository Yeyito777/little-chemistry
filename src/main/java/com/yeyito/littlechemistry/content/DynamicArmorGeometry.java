package com.yeyito.littlechemistry.content;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Optional authored equipment silhouette; null keeps Minecraft's normal humanoid armor wrapping. */
public record DynamicArmorGeometry(List<DynamicArmorGeometryPart> parts) {
	public static final int MAX_PARTS = 32;

	public DynamicArmorGeometry {
		parts = List.copyOf(parts);
		if (parts.isEmpty() || parts.size() > MAX_PARTS) {
			throw new IllegalArgumentException("Authored armor geometry requires 1-32 cuboid parts");
		}
		Set<String> ids = new HashSet<>();
		for (DynamicArmorGeometryPart part : parts) {
			if (!ids.add(part.id())) throw new IllegalArgumentException("Duplicate armor geometry part ID: " + part.id());
		}
	}

	public void validateFor(DynamicArmorSlot slot) {
		for (DynamicArmorGeometryPart part : parts) {
			if (!part.anchor().isAvailableTo(slot)) {
				throw new IllegalArgumentException("Armor geometry part '" + part.id() + "' uses anchor "
						+ part.anchor().serializedName() + " outside the " + slot.serializedName() + " equipment slot");
			}
		}
	}
}
