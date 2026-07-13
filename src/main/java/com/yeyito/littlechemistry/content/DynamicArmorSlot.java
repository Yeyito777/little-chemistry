package com.yeyito.littlechemistry.content;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;

public enum DynamicArmorSlot {
	HEAD("head", EquipmentSlot.HEAD),
	CHEST("chest", EquipmentSlot.CHEST),
	LEGGINGS("leggings", EquipmentSlot.LEGS),
	BOOTS("boots", EquipmentSlot.FEET);

	private final String serializedName;
	private final EquipmentSlot equipmentSlot;

	DynamicArmorSlot(String serializedName, EquipmentSlot equipmentSlot) {
		this.serializedName = serializedName;
		this.equipmentSlot = equipmentSlot;
	}

	public String serializedName() {
		return serializedName;
	}

	public EquipmentSlot equipmentSlot() {
		return equipmentSlot;
	}

	public EquipmentSlotGroup slotGroup() {
		return EquipmentSlotGroup.bySlot(equipmentSlot);
	}

	public static DynamicArmorSlot parse(String value) {
		for (DynamicArmorSlot slot : values()) {
			if (slot.serializedName.equals(value)) return slot;
		}
		throw new IllegalArgumentException("Unknown dynamic armor slot: " + value);
	}
}
