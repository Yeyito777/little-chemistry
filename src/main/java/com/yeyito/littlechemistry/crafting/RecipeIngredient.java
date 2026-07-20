package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.generation.DynamicContentAiDescription;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/** Exact-stack recipe ingredient helpers shared by crafting and smelting. */
final class RecipeIngredient {
	private RecipeIngredient() {
	}

	static ItemStack normalize(ItemStack stack) {
		if (stack.isEmpty()) return ItemStack.EMPTY;
		ItemStack normalized = stack.copyWithCount(1);
		if (normalized.has(DataComponents.DAMAGE)) normalized.setDamageValue(0);
		if (normalized.getItem() == LittleChemistry.CRAFTING_TABLE_ON_A_STICK) {
			normalized.remove(PortableCraftingComponents.TABLE_ID);
			normalized.remove(PortableCraftingComponents.STATE);
			normalized.remove(DataComponents.CONTAINER);
		}
		return normalized;
	}

	static boolean matches(ItemStack expected, ItemStack candidate) {
		return ItemStack.isSameItemSameComponents(normalize(expected), normalize(candidate));
	}

	static boolean referencesDynamicContent(ItemStack stack, Set<String> names) {
		var contentId = stack.get(DynamicContentObjects.CONTENT_ID);
		return contentId != null && LittleChemistry.MOD_ID.equals(contentId.getNamespace())
				&& names.contains(contentId.getPath());
	}

	static boolean referencesUnavailableDynamicContent(ItemStack stack) {
		var contentId = stack.get(DynamicContentObjects.CONTENT_ID);
		return contentId != null && LittleChemistry.MOD_ID.equals(contentId.getNamespace())
				&& DynamicContentCatalog.find(contentId.getPath()) == null;
	}

	static void describe(ItemStack stack, JsonObject destination, DynamicContentManager contentManager,
			Map<String, DynamicContentDefinition> dynamicIngredients) {
		if (stack.isEmpty()) {
			destination.addProperty("empty", true);
			return;
		}
		destination.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		destination.addProperty("displayName", stack.getHoverName().getString());
		var dynamicId = stack.get(DynamicContentObjects.CONTENT_ID);
		if (dynamicId == null) return;
		String id = dynamicId.toString();
		destination.addProperty("dynamicContentId", id);
		DynamicContentDefinition definition = contentManager == null ? null : contentManager.findDefinition(dynamicId);
		if (definition != null) dynamicIngredients.putIfAbsent(id, definition);
		else destination.addProperty("dynamicDefinitionUnavailable", true);
	}

	static JsonArray describeDynamicIngredients(Map<String, DynamicContentDefinition> dynamicIngredients) {
		JsonArray definitions = new JsonArray();
		dynamicIngredients.values().stream()
				.map(DynamicContentAiDescription::describe)
				.forEach(definitions::add);
		return definitions;
	}
}
