package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;

/** Native consumption presentation and sound profile. */
public enum DynamicConsumeStyle {
	EAT("eat"),
	DRINK("drink");

	private final String serializedName;

	DynamicConsumeStyle(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public Consumable.Builder builder() {
		return this == DRINK ? Consumables.defaultDrink() : Consumables.defaultFood();
	}

	public static DynamicConsumeStyle parse(String value) {
		for (DynamicConsumeStyle style : values()) if (style.serializedName.equals(value)) return style;
		throw new IllegalArgumentException("Unknown consume style: " + value);
	}
}
