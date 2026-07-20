package com.yeyito.littlechemistry.content;

/** A rectangular fill gauge driven by a named synchronized state channel. */
public record DynamicWorkstationGauge(
		String id,
		String channel,
		int x,
		int y,
		int width,
		int height,
		DynamicWorkstationGaugeDirection direction,
		String fillColor,
		String backgroundColor,
		String tooltip
) {
	public DynamicWorkstationGauge {
		id = DynamicWorkstationValidation.id(id, "Workstation gauge");
		channel = DynamicWorkstationValidation.id(channel, "Workstation gauge channel");
		if (x < 0 || y < 0 || width < 1 || height < 1 || width > 256 || height > 256) {
			throw new IllegalArgumentException("Workstation gauge dimensions are invalid");
		}
		if (direction == null) throw new IllegalArgumentException("Workstation gauge direction is required");
		fillColor = DynamicWorkstationValidation.color(fillColor, "Workstation gauge fill color");
		backgroundColor = DynamicWorkstationValidation.color(
				backgroundColor, "Workstation gauge background color");
		tooltip = DynamicWorkstationValidation.optionalText(
				tooltip, "Workstation gauge tooltip", 256, true);
	}
}
