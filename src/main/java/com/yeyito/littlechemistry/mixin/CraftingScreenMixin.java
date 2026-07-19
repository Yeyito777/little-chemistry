package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin extends AbstractRecipeBookScreen<CraftingMenu> {
	@Unique private static final Component MAKE_RECIPE = Component.translatable("button.little_chemistry.make_recipe");
	@Unique private static final Component MAKING_RECIPE = Component.translatable("button.little_chemistry.making_recipe");
	@Unique private static final WidgetSprites MAKE_RECIPE_SPRITES = new WidgetSprites(
			LittleChemistry.id("make_recipe"), LittleChemistry.id("make_recipe_disabled"),
			LittleChemistry.id("make_recipe_highlighted"));
	@Unique private ImageButton littleChemistry$makeRecipeButton;

	protected CraftingScreenMixin(CraftingMenu menu, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
		super(menu, recipeBook, inventory, title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void littleChemistry$addMakeRecipeButton(CallbackInfo callback) {
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

	@Inject(method = "extractBackground", at = @At("HEAD"))
	private void littleChemistry$refreshMakeRecipeButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo callback) {
		littleChemistry$updateButton();
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
