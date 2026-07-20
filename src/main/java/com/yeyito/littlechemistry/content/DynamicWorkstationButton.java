package com.yeyito.littlechemistry.content;

/** One engine or generated-code action in a declarative workstation screen. */
public record DynamicWorkstationButton(
		String id,
		DynamicWorkstationButtonRole role,
		String label,
		int x,
		int y,
		int width,
		int height,
		String tooltip,
		String visibleChannel,
		String enabledChannel
) {
	public DynamicWorkstationButton(String id, DynamicWorkstationButtonRole role, String label,
			int x, int y, int width, int height, String tooltip) {
		this(id, role, label, x, y, width, height, tooltip, null, null);
	}

	public DynamicWorkstationButton {
		id = DynamicWorkstationValidation.id(id, "Workstation button");
		if (role == null) throw new IllegalArgumentException("Workstation button role is required");
		label = DynamicWorkstationValidation.text(label, "Workstation button label", 64, false);
		if (x < 0 || y < 0 || width < 20 || width > 256 || height < 12 || height > 40) {
			throw new IllegalArgumentException("Workstation button dimensions are invalid");
		}
		tooltip = DynamicWorkstationValidation.optionalText(
				tooltip, "Workstation button tooltip", 256, true);
		if (visibleChannel != null) {
			visibleChannel = DynamicWorkstationValidation.id(visibleChannel, "Workstation button visible channel");
		}
		if (enabledChannel != null) {
			enabledChannel = DynamicWorkstationValidation.id(enabledChannel, "Workstation button enabled channel");
		}
	}
}
