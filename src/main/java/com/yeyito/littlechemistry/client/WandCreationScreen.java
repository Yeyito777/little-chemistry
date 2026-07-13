package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.ai.generation.GenerationModel;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.network.CreateContentRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class WandCreationScreen extends Screen {
	private static final Component TITLE = Component.translatable("screen.little_chemistry.creation.title");
	private static final Component NAME_LABEL = Component.translatable("screen.little_chemistry.creation.name");
	private static final Component TYPE_LABEL = Component.translatable("screen.little_chemistry.creation.type");
	private static final Component ITEM = Component.translatable("screen.little_chemistry.creation.item");
	private static final Component BLOCK = Component.translatable("screen.little_chemistry.creation.block");
	private static final Component ARMOR = Component.translatable("screen.little_chemistry.creation.armor");
	private static final Component MODEL_LABEL = Component.translatable("screen.little_chemistry.creation.model");

	private EditBox nameEdit;
	private CycleButton<DynamicContentType> typeButton;
	private CycleButton<GenerationModel> modelButton;
	private Button doneButton;
	private Button cancelButton;
	private int left;
	private int top;

	public WandCreationScreen() {
		super(TITLE);
	}

	@Override
	protected void init() {
		this.left = this.width / 2 - 152;
		this.top = Math.max(16, this.height / 2 - 97);

		this.nameEdit = this.addRenderableWidget(new EditBox(
				this.font,
				this.left,
				this.top + 32,
				304,
				20,
				NAME_LABEL
		));
		this.nameEdit.setMaxLength(CreateContentRequestPayload.MAX_NAME_LENGTH);
		this.nameEdit.setHint(Component.translatable("screen.little_chemistry.creation.name_hint"));
		this.nameEdit.setResponder(value -> this.updateDoneButton());

		this.typeButton = this.addRenderableWidget(
				CycleButton.<DynamicContentType>builder(
						type -> switch (type) {
							case ITEM -> ITEM;
							case BLOCK -> BLOCK;
							case ARMOR -> ARMOR;
						},
						DynamicContentType.ITEM
				)
						.withValues(DynamicContentType.ITEM, DynamicContentType.BLOCK, DynamicContentType.ARMOR)
						.displayOnlyValue()
						.create(this.left, this.top + 74, 304, 20, TYPE_LABEL)
		);

		this.modelButton = this.addRenderableWidget(
				CycleButton.<GenerationModel>builder(
						model -> Component.translatable("screen.little_chemistry.creation.model." + model.serializedName()),
						GenerationModel.SOL
				)
						.withValues(GenerationModel.values())
						.displayOnlyValue()
						.create(this.left, this.top + 116, 304, 20, MODEL_LABEL)
		);

		this.doneButton = this.addRenderableWidget(
				Button.builder(CommonComponents.GUI_DONE, button -> this.onDone())
						.bounds(this.left, this.top + 158, 148, 20)
						.build()
		);
		this.cancelButton = this.addRenderableWidget(
				Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
						.bounds(this.left + 156, this.top + 158, 148, 20)
						.build()
		);
		this.updateDoneButton();
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.nameEdit);
	}

	private void updateDoneButton() {
		if (this.doneButton != null) {
			this.doneButton.active = !this.nameEdit.getValue().trim().isEmpty();
		}
	}

	private void onDone() {
		String name = this.nameEdit.getValue().trim();
		if (name.isEmpty()) {
			return;
		}
		DynamicContentType type = this.typeButton.getValue();
		ClientPlayNetworking.send(new CreateContentRequestPayload(
				type,
				this.modelButton.getValue(),
				name));
		this.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (event.isConfirmation() && this.doneButton.active) {
			this.onDone();
			return true;
		}
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.top, -1);
		graphics.text(this.font, NAME_LABEL, this.left, this.top + 20, -6250336);
		graphics.text(this.font, TYPE_LABEL, this.left, this.top + 62, -6250336);
		graphics.text(this.font, MODEL_LABEL, this.left, this.top + 104, -6250336);
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
