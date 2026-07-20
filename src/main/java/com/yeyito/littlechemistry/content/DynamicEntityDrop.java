package com.yeyito.littlechemistry.content;

import net.minecraft.resources.Identifier;

/** A bounded vanilla-item drop rolled independently when a generated entity dies. */
public record DynamicEntityDrop(Identifier item, int minimum, int maximum, double chance) {
	public DynamicEntityDrop {
		if (item == null) throw new IllegalArgumentException("Entity drop item is required");
		if (item.getNamespace().equals("little_chemistry")) {
			throw new IllegalArgumentException("Entity drops cannot refer to an unbound Little Chemistry carrier item");
		}
		if (minimum < 0 || maximum < minimum || maximum > 64) {
			throw new IllegalArgumentException("Entity drop counts must satisfy 0 <= minimum <= maximum <= 64");
		}
		if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
			throw new IllegalArgumentException("Entity drop chance must be between 0 and 1");
		}
	}
}
