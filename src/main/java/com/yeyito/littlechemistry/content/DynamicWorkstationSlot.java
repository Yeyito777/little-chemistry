package com.yeyito.littlechemistry.content;

/** One stable, named slot in a generated workstation's declarative inventory layout. */
public record DynamicWorkstationSlot(
		String id,
		DynamicWorkstationSlotRole role,
		int x,
		int y,
		int maxStack,
		boolean allowPlayerInsert,
		boolean allowPlayerExtract,
		String emptySlotIcon,
		String tooltip
) {
	public DynamicWorkstationSlot {
		id = DynamicWorkstationValidation.id(id, "Workstation slot");
		if (role == null) throw new IllegalArgumentException("Workstation slot role is required");
		if (x < 0 || y < 0) throw new IllegalArgumentException("Workstation slot coordinates cannot be negative");
		if (maxStack < 1 || maxStack > 64) {
			throw new IllegalArgumentException("Workstation slot maxStack must be between 1 and 64");
		}
		if (role.isOutput() && allowPlayerInsert) {
			throw new IllegalArgumentException("Workstation output slots cannot allow player insertion");
		}
		emptySlotIcon = DynamicWorkstationValidation.optionalIdentifier(
				emptySlotIcon, "Workstation empty-slot icon");
		tooltip = DynamicWorkstationValidation.optionalText(
				tooltip, "Workstation slot tooltip", 256, true);
	}
}
