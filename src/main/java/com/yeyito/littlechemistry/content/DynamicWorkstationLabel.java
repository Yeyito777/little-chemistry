package com.yeyito.littlechemistry.content;

/** Static text rendered by the generic workstation screen. */
public record DynamicWorkstationLabel(
		String id,
		String text,
		int x,
		int y,
		String color,
		boolean shadow,
		String tooltip
) {
	public DynamicWorkstationLabel {
		id = DynamicWorkstationValidation.id(id, "Workstation label");
		text = DynamicWorkstationValidation.text(text, "Workstation label text", 128, false);
		if (x < 0 || y < 0) throw new IllegalArgumentException("Workstation label coordinates cannot be negative");
		color = DynamicWorkstationValidation.color(color, "Workstation label color");
		tooltip = DynamicWorkstationValidation.optionalText(
				tooltip, "Workstation label tooltip", 256, true);
	}
}
