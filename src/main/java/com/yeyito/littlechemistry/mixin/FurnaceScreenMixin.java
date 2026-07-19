package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.FurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(FurnaceScreen.class)
public abstract class FurnaceScreenMixin extends AbstractFurnaceScreen<FurnaceMenu> {
	@Unique private static final Component MAKE_RECIPE = Component.translatable("button.little_chemistry.make_smelting_recipe");
	@Unique private static final Component MAKING_RECIPE = Component.translatable("button.little_chemistry.making_smelting_recipe");
	@Unique private static final WidgetSprites MAKE_RECIPE_SPRITES = new WidgetSprites(
			LittleChemistry.id("make_recipe"), LittleChemistry.id("make_recipe_disabled"),
			LittleChemistry.id("make_recipe_highlighted"));
	@Unique private ImageButton littleChemistry$makeRecipeButton;

	protected FurnaceScreenMixin(FurnaceMenu menu, Inventory inventory, Component title, Component filterName,
			Identifier texture, Identifier litProgressSprite, Identifier burnProgressSprite,
			List<RecipeBookComponent.TabInfo> tabs) {
		super(menu, inventory, title, filterName, texture, litProgressSprite, burnProgressSprite, tabs);
	}

	@Override
	public void init() {
		super.init();
		littleChemistry$makeRecipeButton = new ImageButton(20, 20, MAKE_RECIPE_SPRITES, button -> {
			if (this.minecraft != null && this.minecraft.gameMode != null) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
						AiRecipeMenuAccess.MAKE_RECIPE_BUTTON_ID);
			}
		}, MAKE_RECIPE);
		littleChemistry$makeRecipeButton.setTooltip(Tooltip.create(MAKE_RECIPE));
		this.addRenderableWidget(littleChemistry$makeRecipeButton);
		littleChemistry$updateButton();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		littleChemistry$updateButton();
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	}

	@Unique
	private void littleChemistry$updateButton() {
		if (littleChemistry$makeRecipeButton == null) return;
		int state = ((AiRecipeMenuAccess)this.menu).littleChemistry$getRecipeState();
		littleChemistry$makeRecipeButton.visible = state != AiRecipeMenuAccess.EMPTY_OR_VALID;
		littleChemistry$makeRecipeButton.active = state == AiRecipeMenuAccess.MAKE_RECIPE_AVAILABLE;
		littleChemistry$makeRecipeButton.setPosition(this.leftPos + this.imageWidth + 4, this.topPos + 34);
		littleChemistry$makeRecipeButton.setTooltip(Tooltip.create(
				state == AiRecipeMenuAccess.GENERATING ? MAKING_RECIPE : MAKE_RECIPE));
	}
}
