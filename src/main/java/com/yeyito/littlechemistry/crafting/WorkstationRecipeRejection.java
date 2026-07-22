package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A persistent, non-craftable answer explaining why an AI-defined workstation rejected one recipe signature. */
public record WorkstationRecipeRejection(Category category, String description) {
	public static final int MAX_DESCRIPTION_LENGTH = 240;
	private static final int LORE_LINE_LENGTH = 42;
	private static final String DISPLAY_NAME = "Rejection";

	public WorkstationRecipeRejection {
		if (category == null) throw new IllegalArgumentException("Workstation rejection category is required");
		if (description == null) throw new IllegalArgumentException("Workstation rejection description is required");
		description = description.strip();
		if (description.length() < 8 || description.length() > MAX_DESCRIPTION_LENGTH
				|| description.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Workstation rejection description must be 8-"
					+ MAX_DESCRIPTION_LENGTH + " printable characters");
		}
		if (!description.matches(".*[.!?]$")) {
			throw new IllegalArgumentException("Workstation rejection description must be a complete sentence");
		}
		int sentences = sentenceCount(description);
		if (sentences < 1 || sentences > 2) {
			throw new IllegalArgumentException("Workstation rejection description must contain one or two sentences");
		}
	}

	/** Builds the client-visible barrier preview; the marker also lets both menu sides refuse pickup. */
	public ItemStack displayStack() {
		ItemStack stack = new ItemStack(Items.BARRIER);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(DISPLAY_NAME).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(category.displayName()).withStyle(ChatFormatting.DARK_RED));
		for (String line : wrap(description, LORE_LINE_LENGTH)) {
			lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
		return stack;
	}

	public JsonObject toJson() {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("category", category.serializedName());
		encoded.addProperty("description", description);
		return encoded;
	}

	public static WorkstationRecipeRejection fromJson(JsonObject encoded) {
		if (encoded == null || !encoded.keySet().equals(Set.of("category", "description"))) {
			throw new IllegalArgumentException("Rejection requires exactly category and description");
		}
		try {
			return new WorkstationRecipeRejection(
					Category.fromSerializedName(encoded.get("category").getAsString()),
					encoded.get("description").getAsString());
		} catch (IllegalArgumentException invalid) {
			throw invalid;
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("Invalid workstation rejection", invalid);
		}
	}

	public static boolean isDisplayStack(ItemStack stack) {
		return stack != null && stack.is(Items.BARRIER) && stack.has(DataComponents.CREATIVE_SLOT_LOCK)
				&& DISPLAY_NAME.equals(stack.getHoverName().getString());
	}

	private static int sentenceCount(String value) {
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
		iterator.setText(value);
		int sentences = 0;
		for (int start = iterator.first(), end = iterator.next(); end != BreakIterator.DONE;
				start = end, end = iterator.next()) {
			if (!value.substring(start, end).isBlank()) sentences++;
		}
		return sentences;
	}

	private static List<String> wrap(String value, int maximum) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : value.split(" +")) {
			if (!line.isEmpty() && line.length() + 1 + word.length() > maximum) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) line.append(' ');
			line.append(word);
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return List.copyOf(lines);
	}

	public enum Category {
		WORKSTATION_TOO_WEAK("workstation_too_weak", "Workstation too weak");

		private final String serializedName;
		private final String displayName;

		Category(String serializedName, String displayName) {
			this.serializedName = serializedName;
			this.displayName = displayName;
		}

		public String serializedName() {
			return serializedName;
		}

		public String displayName() {
			return displayName;
		}

		public static Category fromSerializedName(String value) {
			for (Category category : values()) {
				if (category.serializedName.equals(value)) return category;
			}
			throw new IllegalArgumentException("Unknown workstation rejection category: " + value);
		}
	}
}
