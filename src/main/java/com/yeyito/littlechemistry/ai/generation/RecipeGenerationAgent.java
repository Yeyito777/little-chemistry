package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;

import java.io.IOException;

/** Lets the AI choose and author an output for a supplied crafting or cooking recipe. */
public final class RecipeGenerationAgent {
	private static final String CHOICE_PROMPT = """
				You invent sensible, surprising Minecraft recipe results. Inspect recipeType, process, and the exact supplied ingredients,
				then choose one coherent output whose identity and quantity follow from both the ingredients and process. Furnace smelting
				must feel like a plausible transformation by sustained heat rather than shaped assembly. Choose an item, block, or one armor piece,
				a short printable display name, and an output count from 1 to 64 that fits one resulting stack. Armor and other
				non-stackable results must use count 1. An ingredient's dynamicContentId references its authoritative entry in
				dynamicIngredients; use that entry's gameplayProperties and behavior when reasoning about generated Little Chemistry
			ingredients rather than treating the shared carrier itemId as their identity. Do not choose existing vanilla content merely
			to duplicate a normal recipe. Call choose_output.
			""";

	private final OpenAiClient openAi;

	public RecipeGenerationAgent(OpenAiClient openAi) {
		this.openAi = openAi;
	}

	public GeneratedRecipe generate(JsonObject recipeContext) throws IOException, InterruptedException {
		Choice choice = choose(recipeContext);
		GeneratedContentSpec content = new ContentGenerationAgent(openAi).generateForRecipe(
				choice.type(), choice.armorSlot(), choice.displayName(), choice.outputCount(), recipeContext);
		validateOutputCount(choice, content);
		return new GeneratedRecipe(choice.type(), choice.armorSlot(), choice.displayName(), choice.outputCount(), content);
	}

	private Choice choose(JsonObject recipeContext) throws IOException, InterruptedException {
		JsonArray history = new JsonArray();
		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", "Recipe request data:\n" + recipeContext);
		content.add(text);
		message.add("content", content);
		history.add(message);

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		JsonObject properties = new JsonObject();
		JsonObject kind = new JsonObject();
		kind.addProperty("type", "string");
		JsonArray kinds = new JsonArray();
		for (String value : new String[]{"item", "block", "helmet", "chestplate", "leggings", "boots"}) kinds.add(value);
		kind.add("enum", kinds);
		properties.add("kind", kind);
		JsonObject name = new JsonObject();
		name.addProperty("type", "string");
		name.addProperty("minLength", 1);
		name.addProperty("maxLength", 64);
		properties.add("name", name);
		JsonObject count = new JsonObject();
		count.addProperty("type", "integer");
		count.addProperty("minimum", 1);
		count.addProperty("maximum", 64);
		properties.add("count", count);
		schema.add("properties", properties);
		JsonArray required = new JsonArray();
		required.add("kind");
		required.add("name");
		required.add("count");
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);

		JsonObject tool = new JsonObject();
		tool.addProperty("type", "function");
		tool.addProperty("name", "choose_output");
		tool.addProperty("description", "Choose the new dynamic content and stack count produced by this exact recipe.");
		tool.add("parameters", schema);
		tool.addProperty("strict", false);
		JsonArray tools = new JsonArray();
		tools.add(tool);

		OpenAiClient.ToolRound round = openAi.runToolRound(CHOICE_PROMPT, tools, history);
		OpenAiClient.ToolCall call = round.calls().stream()
				.filter(candidate -> candidate.name().equals("choose_output"))
				.findFirst()
				.orElseThrow(() -> new IOException(openAi.model() + " did not choose a recipe output"));
		return parseChoice(call.arguments(), openAi.model());
	}

	static Choice parseChoice(JsonObject arguments, String model) throws IOException {
		String chosenKind;
		String chosenName;
		double rawCount;
		try {
			chosenKind = arguments.get("kind").getAsString();
			chosenName = arguments.get("name").getAsString().strip();
			rawCount = arguments.get("count").getAsDouble();
		} catch (Exception invalid) {
			throw new IOException(model + " returned an invalid recipe output choice", invalid);
		}
		if (chosenName.isEmpty() || chosenName.length() > 64 || chosenName.chars().anyMatch(Character::isISOControl)) {
			throw new IOException(model + " returned an invalid recipe output name");
		}
		if (!Double.isFinite(rawCount) || rawCount != Math.rint(rawCount) || rawCount < 1 || rawCount > 64) {
			throw new IOException(model + " returned an invalid recipe output count");
		}
		int outputCount = (int) rawCount;
		Choice choice = switch (chosenKind) {
			case "item" -> new Choice(DynamicContentType.ITEM, null, chosenName, outputCount);
			case "block" -> new Choice(DynamicContentType.BLOCK, null, chosenName, outputCount);
			case "helmet" -> new Choice(DynamicContentType.ARMOR, DynamicArmorSlot.HEAD, chosenName, outputCount);
			case "chestplate" -> new Choice(DynamicContentType.ARMOR, DynamicArmorSlot.CHEST, chosenName, outputCount);
			case "leggings" -> new Choice(DynamicContentType.ARMOR, DynamicArmorSlot.LEGGINGS, chosenName, outputCount);
			case "boots" -> new Choice(DynamicContentType.ARMOR, DynamicArmorSlot.BOOTS, chosenName, outputCount);
			default -> throw new IOException(model + " returned an unknown recipe output kind");
		};
		if (choice.type() == DynamicContentType.ARMOR && outputCount != 1) {
			throw new IOException(model + " returned multiple non-stackable armor pieces");
		}
		return choice;
	}

	private static void validateOutputCount(Choice choice, GeneratedContentSpec content) throws IOException {
		int maximum = switch (choice.type()) {
			case ITEM -> content.item().maxStack();
			case BLOCK -> 64;
			case ARMOR -> 1;
		};
		if (choice.outputCount() > maximum) {
			throw new IOException("Recipe output count " + choice.outputCount()
					+ " exceeds the generated content's stack limit of " + maximum);
		}
	}

	record Choice(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName, int outputCount) {
	}

	public record GeneratedRecipe(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName, int outputCount,
			GeneratedContentSpec content) {
	}
}
