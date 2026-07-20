package com.yeyito.littlechemistry.content;

/** A bounded signed-16-bit value synchronized by name for workstation UI rendering. */
public record DynamicWorkstationStateChannel(
		String id,
		int minimum,
		int maximum,
		int initialValue
) {
	public static final int MIN_SYNCHRONIZED_VALUE = Short.MIN_VALUE;
	public static final int MAX_SYNCHRONIZED_VALUE = Short.MAX_VALUE;

	public DynamicWorkstationStateChannel {
		id = DynamicWorkstationValidation.id(id, "Workstation state channel");
		if (minimum < MIN_SYNCHRONIZED_VALUE || maximum > MAX_SYNCHRONIZED_VALUE || minimum > maximum) {
			throw new IllegalArgumentException("Workstation state channels must use an ordered signed-16-bit range");
		}
		if (initialValue < minimum || initialValue > maximum) {
			throw new IllegalArgumentException("Workstation state channel initial value is outside its range");
		}
	}
}
