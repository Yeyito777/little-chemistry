package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess;
import com.yeyito.littlechemistry.crafting.DynamicWorkstationMenu;
import com.yeyito.littlechemistry.content.DynamicWorkstationButton;
import com.yeyito.littlechemistry.content.DynamicWorkstationButtonRole;
import com.yeyito.littlechemistry.content.DynamicWorkstationGauge;
import com.yeyito.littlechemistry.content.DynamicWorkstationLabel;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlot;
import com.yeyito.littlechemistry.content.DynamicWorkstationStateChannel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Generic declarative screen used by every AI-defined workstation. */
public final class DynamicWorkstationScreen extends AbstractContainerScreen<DynamicWorkstationMenu> {
	private final List<ButtonBinding> generatedButtons = new ArrayList<>();

	public DynamicWorkstationScreen(DynamicWorkstationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.specification().ui().width(), menu.specification().ui().height());
		this.titleLabelX = menu.specification().ui().titleX();
		this.titleLabelY = menu.specification().ui().titleY();
		this.inventoryLabelX = menu.specification().ui().playerInventoryLabelX();
		this.inventoryLabelY = menu.specification().ui().playerInventoryLabelY();
	}

	@Override
	protected void init() {
		super.init();
		generatedButtons.clear();
		for (int index = 0; index < menu.specification().ui().buttons().size(); index++) {
			DynamicWorkstationButton specification = menu.specification().ui().buttons().get(index);
			int buttonId = index;
			Button button = Button.builder(Component.literal(specification.label()), clicked -> {
				if (minecraft == null || minecraft.gameMode == null) return;
				int sentId = specification.role() == DynamicWorkstationButtonRole.MAKE_RECIPE
						? AiRecipeMenuAccess.MAKE_RECIPE_BUTTON_ID : buttonId;
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, sentId);
			}).bounds(leftPos + specification.x(), topPos + specification.y(),
					specification.width(), specification.height()).build();
			if (specification.tooltip() != null) {
				button.setTooltip(Tooltip.create(Component.literal(specification.tooltip())));
			}
			this.addRenderableWidget(button);
			generatedButtons.add(new ButtonBinding(specification, button));
		}
		updateButtons();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		updateButtons();
		var ui = menu.specification().ui();
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, argb(ui.backgroundColor()));
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF101010);
		for (Slot slot : menu.slots) {
			if (!slot.isActive()) continue;
			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF202020);
			graphics.outline(x - 1, y - 1, 18, 18, 0xFFB8B8B8);
		}
		for (DynamicWorkstationGauge gauge : ui.gauges()) {
			drawGauge(graphics, gauge);
			if (gauge.tooltip() != null && isHovering(gauge.x(), gauge.y(), gauge.width(), gauge.height(),
					mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font, font.split(Component.literal(gauge.tooltip()), 170), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		for (DynamicWorkstationLabel label : menu.specification().ui().labels()) {
			int line = 0;
			for (String text : label.text().split("\\n", -1)) {
				graphics.text(font, text, label.x(), label.y() + line++ * 10, argb(label.color()), label.shadow());
			}
			if (label.tooltip() != null && isHovering(label.x(), label.y(),
					Math.max(1, font.width(label.text())), 10 * line, mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font, font.split(Component.literal(label.tooltip()), 170),
						mouseX, mouseY);
			}
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int menuSlotIndex = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
		if (hoveredSlot == null || hoveredSlot.hasItem() || menuSlotIndex < 0
				|| menuSlotIndex >= menu.specification().slots().size()) return;
		DynamicWorkstationSlot specification = menu.specification().slots().get(menuSlotIndex);
		if (specification.tooltip() != null) {
			graphics.setTooltipForNextFrame(font, font.split(Component.literal(specification.tooltip()), 170), mouseX, mouseY);
		}
	}

	private void drawGauge(GuiGraphicsExtractor graphics, DynamicWorkstationGauge gauge) {
		DynamicWorkstationStateChannel channel = menu.specification().ui().stateChannel(gauge.channel());
		if (channel == null) return;
		int raw = menu.uiState(gauge.channel());
		double fraction = channel.maximum() == channel.minimum() ? 1.0
				: Math.max(0.0, Math.min(1.0,
				(raw - channel.minimum()) / (double) (channel.maximum() - channel.minimum())));
		int x = leftPos + gauge.x();
		int y = topPos + gauge.y();
		graphics.fill(x, y, x + gauge.width(), y + gauge.height(), argb(gauge.backgroundColor()));
		int fillWidth = (int) Math.round(gauge.width() * fraction);
		int fillHeight = (int) Math.round(gauge.height() * fraction);
		switch (gauge.direction()) {
			case LEFT_TO_RIGHT -> graphics.fill(x, y, x + fillWidth, y + gauge.height(), argb(gauge.fillColor()));
			case RIGHT_TO_LEFT -> graphics.fill(x + gauge.width() - fillWidth, y,
					x + gauge.width(), y + gauge.height(), argb(gauge.fillColor()));
			case TOP_TO_BOTTOM -> graphics.fill(x, y, x + gauge.width(), y + fillHeight, argb(gauge.fillColor()));
			case BOTTOM_TO_TOP -> graphics.fill(x, y + gauge.height() - fillHeight,
					x + gauge.width(), y + gauge.height(), argb(gauge.fillColor()));
		}
		graphics.outline(x, y, gauge.width(), gauge.height(), 0xFF101010);
	}

	private void updateButtons() {
		int state = menu.littleChemistry$getRecipeState();
		for (ButtonBinding binding : generatedButtons) {
			boolean channelVisible = binding.specification.visibleChannel() == null
					|| menu.uiState(binding.specification.visibleChannel()) != 0;
			boolean channelEnabled = binding.specification.enabledChannel() == null
					|| menu.uiState(binding.specification.enabledChannel()) != 0;
			binding.button.visible = channelVisible;
			binding.button.active = channelEnabled;
				if (binding.specification.role() == DynamicWorkstationButtonRole.MAKE_RECIPE) {
				binding.button.visible = channelVisible && state != AiRecipeMenuAccess.EMPTY_OR_VALID;
				binding.button.active = channelEnabled && state == AiRecipeMenuAccess.MAKE_RECIPE_AVAILABLE;
				if (state == AiRecipeMenuAccess.GENERATING) {
					binding.button.setMessage(Component.translatable("button.little_chemistry.making_recipe"));
					} else {
						binding.button.setMessage(Component.literal(binding.specification.label()));
					}
				} else if (state == AiRecipeMenuAccess.GENERATING) {
					binding.button.active = false;
				}
		}
	}

	private static int argb(String rgba) {
		long value = Long.parseUnsignedLong(rgba, 16);
		int rgb = (int) (value >>> 8);
		int alpha = (int) (value & 0xFFL);
		return alpha << 24 | rgb;
	}

	private record ButtonBinding(DynamicWorkstationButton specification, Button button) {
	}
}
