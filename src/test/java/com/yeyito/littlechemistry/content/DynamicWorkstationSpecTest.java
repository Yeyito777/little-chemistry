package com.yeyito.littlechemistry.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicWorkstationSpecTest {
	@Test
	void recipeDataSchemaValidatesRequiredClosedAndBoundedValues() {
		DynamicWorkstationRecipeDataSchema schema = recipeDataSchema();
		JsonObject valid = JsonParser.parseString("""
				{
				  "duration_ticks": 200,
				  "mode": "separate",
				  "stages": ["spin", "settle"]
				}
				""").getAsJsonObject();

		schema.validateValue(valid);

		JsonObject missing = valid.deepCopy();
		missing.remove("duration_ticks");
		assertThrows(IllegalArgumentException.class, () -> schema.validateValue(missing));
		JsonObject unknown = valid.deepCopy();
		unknown.addProperty("unbounded_payload", "no");
		assertThrows(IllegalArgumentException.class, () -> schema.validateValue(unknown));
		JsonObject outsideRange = valid.deepCopy();
		outsideRange.addProperty("duration_ticks", 20_001);
		assertThrows(IllegalArgumentException.class, () -> schema.validateValue(outsideRange));
		JsonObject tooManyStages = valid.deepCopy();
		tooManyStages.add("stages", JsonParser.parseString("[\"a\",\"b\",\"c\",\"d\",\"e\"]"));
		assertThrows(IllegalArgumentException.class, () -> schema.validateValue(tooManyStages));
	}

	@Test
	void schemaRejectsUnsupportedOrUnboundedStructures() {
		JsonObject unknownKeyword = JsonParser.parseString("""
				{"type":"object","properties":{},"patternProperties":{}}
				""").getAsJsonObject();
		assertThrows(IllegalArgumentException.class,
				() -> new DynamicWorkstationRecipeDataSchema(unknownKeyword));

		JsonObject openObject = JsonParser.parseString("""
				{"type":"object","properties":{},"additionalProperties":true}
				""").getAsJsonObject();
		assertThrows(IllegalArgumentException.class,
				() -> new DynamicWorkstationRecipeDataSchema(openObject));

		JsonObject oversizedArray = JsonParser.parseString("""
				{
				  "type":"object",
				  "properties":{"values":{"type":"array","items":{"type":"integer"},"maxItems":65}}
				}
				""").getAsJsonObject();
		assertThrows(IllegalArgumentException.class,
				() -> new DynamicWorkstationRecipeDataSchema(oversizedArray));
	}

	@Test
	void modelIsImmutableAndJsonCodecRoundTripsEveryComponent() {
		JsonObject sourceSchema = schemaJson();
		DynamicWorkstationRecipeDataSchema schema = new DynamicWorkstationRecipeDataSchema(sourceSchema);
		DynamicWorkstationSpec workstation = workstation(schema);
		JsonObject encoded = DynamicWorkstationJson.encode(workstation);

		sourceSchema.getAsJsonObject("properties").remove("duration_ticks");
		schema.schema().getAsJsonObject("properties").remove("mode");
		DynamicWorkstationSpec decoded = DynamicWorkstationJson.decode(encoded);

		assertEquals(workstation, decoded);
		assertEquals(3, decoded.slots().size());
		assertEquals(DynamicWorkstationSlotRole.CATALYST, decoded.slot("catalyst").role());
		assertEquals(10_000, decoded.ui().stateChannel("progress").maximum());
		assertEquals("minecraft:item/empty_slot_amethyst_shard", decoded.slot("catalyst").emptySlotIcon());
		assertTrue(decoded.recipeDataSchema().schema().getAsJsonObject("properties").has("duration_ticks"));
		assertTrue(decoded.recipeDataSchema().schema().getAsJsonObject("properties").has("mode"));
		assertThrows(UnsupportedOperationException.class,
				() -> decoded.slots().add(decoded.slots().getFirst()));
	}

	@Test
	void aggregateValidationRejectsAmbiguousLayoutsAndBrokenUiReferences() {
		DynamicWorkstationSpec valid = workstation(recipeDataSchema());
		List<DynamicWorkstationSlot> duplicateSlots = List.of(valid.slots().getFirst(), valid.slots().getFirst(),
				valid.slots().getLast());
		assertThrows(IllegalArgumentException.class, () -> new DynamicWorkstationSpec(
				duplicateSlots, valid.ui(), "Separate materials.", "Create separation recipes.", recipeDataSchema()));

		List<DynamicWorkstationSlot> noPrimaryOutput = List.of(valid.slots().getFirst(), valid.slots().get(1));
		assertThrows(IllegalArgumentException.class, () -> new DynamicWorkstationSpec(
				noPrimaryOutput, valid.ui(), "Separate materials.", "Create separation recipes.", recipeDataSchema()));
		List<DynamicWorkstationSlot> multiplePrimaryOutputs = List.of(
				valid.slots().getFirst(), valid.slots().getLast(),
				new DynamicWorkstationSlot("second_result", DynamicWorkstationSlotRole.OUTPUT,
						120, 50, 64, false, true, null, null));
		IllegalArgumentException primaryOutputError = assertThrows(IllegalArgumentException.class,
				() -> new DynamicWorkstationSpec(multiplePrimaryOutputs, valid.ui(),
						"Separate materials.", "Create separation recipes.", recipeDataSchema()));
		assertTrue(primaryOutputError.getMessage().contains("exactly one primary OUTPUT"));

		assertThrows(IllegalArgumentException.class, () -> new DynamicWorkstationUi(
				176, 166, 7, 84, "20242AFF", List.of(),
				List.of(new DynamicWorkstationGauge("heat_gauge", "missing", 70, 20, 12, 40,
						DynamicWorkstationGaugeDirection.BOTTOM_TO_TOP, "FF6020FF", "101010FF", null)),
				List.of(new DynamicWorkstationButton("make_recipe", DynamicWorkstationButtonRole.MAKE_RECIPE,
						"Make Recipe", 90, 20, 70, 20, null)), List.of()));

		assertFalse(valid.slots().getLast().allowPlayerInsert());
	}

	@Test
	void aggregateSerializedSpecificationIsBoundedByCharactersAndUtf8Bytes() {
		DynamicWorkstationSpec valid = workstation(recipeDataSchema());
		List<DynamicWorkstationLabel> labels = new ArrayList<>();
		for (int index = 0; index < DynamicWorkstationUi.MAX_LABELS; index++) {
			labels.add(new DynamicWorkstationLabel("label_" + index, "界".repeat(128), 0, 0,
					"FFFFFFFF", false, "界".repeat(256)));
		}
		DynamicWorkstationUi largeUi = new DynamicWorkstationUi(
				valid.ui().width(), valid.ui().height(), valid.ui().playerInventoryX(), valid.ui().playerInventoryY(),
				valid.ui().titleX(), valid.ui().titleY(), valid.ui().playerInventoryLabelX(),
				valid.ui().playerInventoryLabelY(), valid.ui().backgroundColor(), labels,
				valid.ui().gauges(), valid.ui().buttons(), valid.ui().stateChannels());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> new DynamicWorkstationSpec(valid.slots(), largeUi, "界".repeat(1_024),
						"界".repeat(DynamicWorkstationSpec.MAX_RECIPE_SYSTEM_PROMPT_LENGTH), recipeDataSchema()));

		assertTrue(error.getMessage().contains("UTF-8 byte opening-data limit"), error.getMessage());
	}

	@Test
	@SuppressWarnings("deprecation")
	void legacySystemPromptNamesRemainSourceAndPersistenceCompatible() {
		DynamicWorkstationSpec workstation = workstation();
		JsonObject encoded = DynamicWorkstationJson.encode(workstation);

		assertEquals(workstation.recipePolicy(), workstation.recipeSystemPrompt());
		assertEquals(workstation.recipePolicy(), encoded.get("recipeSystemPrompt").getAsString());
		assertFalse(encoded.has("recipePolicy"));
		assertEquals(workstation, DynamicWorkstationJson.decode(encoded));
		JsonObject modelFacing = encoded.deepCopy();
		modelFacing.add("recipePolicy", modelFacing.remove("recipeSystemPrompt"));
		assertEquals(workstation, DynamicWorkstationJson.decode(modelFacing));
		modelFacing.addProperty("recipeSystemPrompt", workstation.recipePolicy());
		assertThrows(IllegalArgumentException.class, () -> DynamicWorkstationJson.decode(modelFacing));
	}

	@Test
	void legacyImperativePolicyRemainsLoadableAsUntrustedCompatibilityData() {
		JsonObject encoded = DynamicWorkstationJson.encode(workstation());
		String legacy = "Generate coherent separation recipes. Catalysts are retained and duration_ticks controls spin time.";
		encoded.addProperty("recipeSystemPrompt", legacy);

		DynamicWorkstationSpec decoded = DynamicWorkstationJson.decode(encoded);

		assertEquals(legacy, decoded.recipePolicy());
	}

	static DynamicWorkstationSpec workstation() {
		return workstation(recipeDataSchema());
	}

	static DynamicWorkstationSpec workstation(DynamicWorkstationRecipeDataSchema schema) {
		List<DynamicWorkstationSlot> slots = new ArrayList<>();
		slots.add(new DynamicWorkstationSlot("input", DynamicWorkstationSlotRole.INPUT,
				20, 24, 64, true, true, null, "Material to separate"));
		slots.add(new DynamicWorkstationSlot("catalyst", DynamicWorkstationSlotRole.CATALYST,
				44, 24, 1, true, true, "minecraft:item/empty_slot_amethyst_shard", "Optional focusing crystal"));
		slots.add(new DynamicWorkstationSlot("result", DynamicWorkstationSlotRole.OUTPUT,
				142, 24, 64, false, true, null, "Separated result"));
		DynamicWorkstationUi ui = new DynamicWorkstationUi(
				176, 166, 7, 84, "20242AFF",
				List.of(new DynamicWorkstationLabel("title", "Centrifuge", 8, 6, "FFFFFFFF", true, null)),
				List.of(new DynamicWorkstationGauge("progress_gauge", "progress", 72, 24, 52, 12,
						DynamicWorkstationGaugeDirection.LEFT_TO_RIGHT, "40C0FFFF", "101820FF", "Separation progress")),
				List.of(
						new DynamicWorkstationButton("make_recipe", DynamicWorkstationButtonRole.MAKE_RECIPE,
								"Make Recipe", 92, 52, 72, 20, "Generate this workstation recipe"),
						new DynamicWorkstationButton("vent", DynamicWorkstationButtonRole.CUSTOM,
								"Vent", 8, 52, 48, 20, null)),
				List.of(new DynamicWorkstationStateChannel("progress", 0, 10_000, 0)));
		return new DynamicWorkstationSpec(slots, ui,
				"Separates mixed materials through controlled rotational force over 200 Minecraft ticks.",
				"Results are coherent separated or clarified forms grounded in the supplied mixture and catalyst.",
				schema);
	}

	static DynamicWorkstationRecipeDataSchema recipeDataSchema() {
		return new DynamicWorkstationRecipeDataSchema(schemaJson());
	}

	private static JsonObject schemaJson() {
		return JsonParser.parseString("""
				{
				  "type": "object",
				  "properties": {
				    "duration_ticks": {"type":"integer","minimum":1,"maximum":20000},
				    "targetRpm": {"type":"number","minimum":0,"maximum":10000},
				    "mode": {"type":"string","enum":["separate","clarify"]},
				    "stages": {
				      "type":"array",
				      "items":{"type":"string","maxLength":32},
				      "maxItems":4
				    }
				  },
				  "required": ["duration_ticks", "mode"],
				  "additionalProperties": false
				}
				""").getAsJsonObject();
	}
}
