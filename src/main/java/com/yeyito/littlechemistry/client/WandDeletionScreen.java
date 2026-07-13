package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.network.DeleteContentRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WandDeletionScreen extends Screen {
	private static final Component TITLE = Component.translatable("screen.little_chemistry.deletion.title");
	private final List<DynamicContentDefinition> definitions;
	private final Set<String> selected = new HashSet<>();
	private int page;
	private int rowsPerPage;
	private Button confirmButton;

	public WandDeletionScreen() {
		super(TITLE);
		this.definitions = DynamicContentCatalog.definitions().stream()
				.sorted(Comparator.comparing(DynamicContentDefinition::type)
						.thenComparing(DynamicContentDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	@Override
	protected void init() {
		this.rowsPerPage = Math.max(3, Math.min(10, (this.height - 116) / 22));
		this.page = Math.min(this.page, pageCount() - 1);
		buildWidgets();
	}

	private void buildWidgets() {
		int listWidth = Math.min(420, this.width - 32);
		int left = (this.width - listWidth) / 2;
		int start = this.page * this.rowsPerPage;
		int end = Math.min(this.definitions.size(), start + this.rowsPerPage);
		for (int index = start; index < end; index++) {
			DynamicContentDefinition definition = this.definitions.get(index);
			this.addRenderableWidget(Button.builder(label(definition), button -> {
				if (!this.selected.add(definition.name())) this.selected.remove(definition.name());
				button.setMessage(label(definition));
				updateConfirmButton();
			}).bounds(left, 42 + (index - start) * 22, listWidth, 20).build());
		}

		int controlsY = 46 + this.rowsPerPage * 22;
		Button previous = this.addRenderableWidget(Button.builder(Component.literal("< Previous"), button -> changePage(-1))
				.bounds(left, controlsY, 98, 20).build());
		Button next = this.addRenderableWidget(Button.builder(Component.literal("Next >"), button -> changePage(1))
				.bounds(left + listWidth - 98, controlsY, 98, 20).build());
		previous.active = this.page > 0;
		next.active = this.page + 1 < pageCount();

		int selectionY = controlsY + 24;
		this.addRenderableWidget(Button.builder(Component.literal("Select All"), button -> {
			this.definitions.forEach(definition -> this.selected.add(definition.name()));
			rebuildPage();
		}).bounds(left, selectionY, 98, 20).build()).active = !this.definitions.isEmpty();
		this.addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
			this.selected.clear();
			rebuildPage();
		}).bounds(left + 104, selectionY, 70, 20).build()).active = !this.selected.isEmpty();

		int footerY = selectionY + 28;
		this.confirmButton = this.addRenderableWidget(Button.builder(
				Component.translatable("screen.little_chemistry.deletion.confirm"), button -> confirm())
				.bounds(left, footerY, (listWidth - 8) / 2, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(left + (listWidth + 8) / 2, footerY, (listWidth - 8) / 2, 20).build());
		updateConfirmButton();
	}

	private Component label(DynamicContentDefinition definition) {
		String check = this.selected.contains(definition.name()) ? "[x] " : "[ ] ";
		String kind = switch (definition.type()) {
			case ITEM -> "Item: ";
			case BLOCK -> "Block: ";
			case ARMOR -> "Armor (" + definition.armor().slot().serializedName() + "): ";
		};
		return Component.literal(check + kind + definition.displayName());
	}

	private void changePage(int delta) {
		this.page = Math.max(0, Math.min(pageCount() - 1, this.page + delta));
		rebuildPage();
	}

	private void rebuildPage() {
		this.clearWidgets();
		buildWidgets();
	}

	private int pageCount() {
		return Math.max(1, (this.definitions.size() + this.rowsPerPage - 1) / this.rowsPerPage);
	}

	private void updateConfirmButton() {
		if (this.confirmButton != null) this.confirmButton.active = !this.selected.isEmpty();
	}

	private void confirm() {
		if (this.selected.isEmpty()) return;
		ClientPlayNetworking.send(new DeleteContentRequestPayload(this.selected.stream().sorted().toList()));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, -1);
		graphics.centeredText(this.font,
				Component.literal(this.selected.size() + " selected · Page " + (this.page + 1) + "/" + pageCount()),
				this.width / 2, 29, -6250336);
		if (this.definitions.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("screen.little_chemistry.deletion.empty"),
					this.width / 2, 66, -6250336);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
