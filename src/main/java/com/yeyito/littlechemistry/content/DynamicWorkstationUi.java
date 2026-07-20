package com.yeyito.littlechemistry.content;

import java.util.HashSet;
import java.util.List;

/** Bounded declarative UI rendered by the single generic workstation screen. */
public record DynamicWorkstationUi(
		int width,
		int height,
		int playerInventoryX,
		int playerInventoryY,
		int titleX,
		int titleY,
		int playerInventoryLabelX,
		int playerInventoryLabelY,
		String backgroundColor,
		List<DynamicWorkstationLabel> labels,
		List<DynamicWorkstationGauge> gauges,
		List<DynamicWorkstationButton> buttons,
		List<DynamicWorkstationStateChannel> stateChannels
) {
	public static final int MIN_WIDTH = 176;
	public static final int MAX_WIDTH = 320;
	public static final int MIN_HEIGHT = 120;
	public static final int MAX_HEIGHT = 256;
	public static final int MAX_LABELS = 32;
	public static final int MAX_GAUGES = 16;
	public static final int MAX_BUTTONS = 16;
	public static final int MAX_STATE_CHANNELS = 32;
	private static final int PLAYER_INVENTORY_WIDTH = 162;
	private static final int PLAYER_INVENTORY_HEIGHT = 76;

	/** Compatibility constructor for callers using the original generated-UI field set. */
	public DynamicWorkstationUi(int width, int height, int playerInventoryX, int playerInventoryY,
			String backgroundColor, List<DynamicWorkstationLabel> labels,
			List<DynamicWorkstationGauge> gauges, List<DynamicWorkstationButton> buttons,
			List<DynamicWorkstationStateChannel> stateChannels) {
		this(width, height, playerInventoryX, playerInventoryY, 8, 6,
				playerInventoryX, Math.max(0, playerInventoryY - 12), backgroundColor,
				labels, gauges, buttons, stateChannels);
	}

	public DynamicWorkstationUi {
		if (width < MIN_WIDTH || width > MAX_WIDTH || height < MIN_HEIGHT || height > MAX_HEIGHT) {
			throw new IllegalArgumentException("Workstation screen dimensions are outside the supported bounds");
		}
		DynamicWorkstationValidation.rectangle("Player inventory", playerInventoryX, playerInventoryY,
				PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT, width, height);
		DynamicWorkstationValidation.rectangle("Workstation title", titleX, titleY, 1, 9, width, height);
		DynamicWorkstationValidation.rectangle("Player inventory label", playerInventoryLabelX,
				playerInventoryLabelY, 1, 9, width, height);
		backgroundColor = DynamicWorkstationValidation.color(
				backgroundColor, "Workstation background color");
		if (labels == null || gauges == null || buttons == null || stateChannels == null) {
			throw new IllegalArgumentException("Workstation UI widget and state-channel lists are required");
		}
		labels = List.copyOf(labels);
		gauges = List.copyOf(gauges);
		buttons = List.copyOf(buttons);
		stateChannels = List.copyOf(stateChannels);
		if (labels.size() > MAX_LABELS || gauges.size() > MAX_GAUGES || buttons.size() > MAX_BUTTONS
				|| stateChannels.size() > MAX_STATE_CHANNELS) {
			throw new IllegalArgumentException("Workstation UI exceeds its widget or state-channel budget");
		}

		uniqueIds(labels.stream().map(DynamicWorkstationLabel::id).toList(), "label");
		uniqueIds(gauges.stream().map(DynamicWorkstationGauge::id).toList(), "gauge");
		uniqueIds(buttons.stream().map(DynamicWorkstationButton::id).toList(), "button");
		uniqueIds(stateChannels.stream().map(DynamicWorkstationStateChannel::id).toList(), "state channel");

		for (DynamicWorkstationLabel label : labels) {
			DynamicWorkstationValidation.rectangle("Workstation label " + label.id(),
					label.x(), label.y(), 1, 9, width, height);
		}
		HashSet<String> channelIds = new HashSet<>();
		for (DynamicWorkstationStateChannel channel : stateChannels) channelIds.add(channel.id());
		for (DynamicWorkstationGauge gauge : gauges) {
			DynamicWorkstationValidation.rectangle("Workstation gauge " + gauge.id(),
					gauge.x(), gauge.y(), gauge.width(), gauge.height(), width, height);
			if (!channelIds.contains(gauge.channel())) {
				throw new IllegalArgumentException("Workstation gauge refers to undefined state channel: "
						+ gauge.channel());
			}
		}
		int makeRecipeButtons = 0;
		for (DynamicWorkstationButton button : buttons) {
			DynamicWorkstationValidation.rectangle("Workstation button " + button.id(),
					button.x(), button.y(), button.width(), button.height(), width, height);
			if (button.role() == DynamicWorkstationButtonRole.MAKE_RECIPE) makeRecipeButtons++;
			if (button.visibleChannel() != null && !channelIds.contains(button.visibleChannel())) {
				throw new IllegalArgumentException("Workstation button refers to undefined visible channel: "
						+ button.visibleChannel());
			}
			if (button.enabledChannel() != null && !channelIds.contains(button.enabledChannel())) {
				throw new IllegalArgumentException("Workstation button refers to undefined enabled channel: "
						+ button.enabledChannel());
			}
		}
		if (makeRecipeButtons != 1) {
			throw new IllegalArgumentException("Workstation UI requires exactly one Make Recipe button");
		}
	}

	public DynamicWorkstationStateChannel stateChannel(String id) {
		for (DynamicWorkstationStateChannel channel : stateChannels) {
			if (channel.id().equals(id)) return channel;
		}
		return null;
	}

	private static void uniqueIds(List<String> ids, String kind) {
		HashSet<String> unique = new HashSet<>();
		for (String id : ids) {
			if (!unique.add(id)) throw new IllegalArgumentException("Duplicate workstation " + kind + " ID: " + id);
		}
	}
}
