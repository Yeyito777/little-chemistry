package com.yeyito.littlechemistry.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Canonical JSON codec for the optional dynamic-workstation data model. */
public final class DynamicWorkstationJson {
	/** Bound shared by persistence and the byte-limited menu-opening codec. */
	public static final int MAX_SERIALIZED_CHARACTERS = 60_000;
	public static final int MAX_SERIALIZED_UTF8_BYTES = 60_000;

	private DynamicWorkstationJson() {
	}

	public static JsonObject encode(DynamicWorkstationSpec workstation) {
		if (workstation == null) throw new IllegalArgumentException("Workstation specification is required");
		return encode(workstation.slots(), workstation.ui(), workstation.processDescription(),
				workstation.recipePolicy(), workstation.recipeDataSchema());
	}

	static void validateSerializedSize(List<DynamicWorkstationSlot> slots, DynamicWorkstationUi ui,
			String processDescription, String recipePolicy,
			DynamicWorkstationRecipeDataSchema recipeDataSchema) {
		JsonObject encoded = encode(slots, ui, processDescription, recipePolicy, recipeDataSchema);
		String json = encoded.toString();
		if (json.length() > MAX_SERIALIZED_CHARACTERS
				|| json.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_UTF8_BYTES) {
			throw new IllegalArgumentException("Serialized workstation specification exceeds the "
					+ MAX_SERIALIZED_UTF8_BYTES + " character/UTF-8 byte opening-data limit");
		}
	}

	private static JsonObject encode(List<DynamicWorkstationSlot> slots, DynamicWorkstationUi ui,
			String processDescription, String recipePolicy,
			DynamicWorkstationRecipeDataSchema recipeDataSchema) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("processDescription", processDescription);
		// Preserve the legacy key so existing definition digests, pending journals, saves, and clients remain compatible.
		encoded.addProperty("recipeSystemPrompt", recipePolicy);
		encoded.add("recipeDataSchema", recipeDataSchema.schema());
		JsonArray encodedSlots = new JsonArray();
		for (DynamicWorkstationSlot slot : slots) encodedSlots.add(encodeSlot(slot));
		encoded.add("slots", encodedSlots);
		encoded.add("ui", encodeUi(ui));
		return encoded;
	}

	public static DynamicWorkstationSpec decode(JsonObject encoded) {
		requireOnly(encoded, "processDescription", "recipePolicy", "recipeSystemPrompt", "recipeDataSchema", "slots", "ui");
		if (encoded.has("recipePolicy") && encoded.has("recipeSystemPrompt")) {
			throw new IllegalArgumentException("Workstation JSON cannot contain both recipePolicy and recipeSystemPrompt");
		}
		JsonArray encodedSlots = requiredArray(encoded, "slots");
		if (encodedSlots.size() > DynamicWorkstationSpec.MAX_SLOTS) {
			throw new IllegalArgumentException("Workstation JSON contains too many slots");
		}
		List<DynamicWorkstationSlot> slots = new ArrayList<>();
		for (JsonElement element : encodedSlots) slots.add(decodeSlot(requiredObject(element, "workstation slot")));
		return new DynamicWorkstationSpec(
				slots,
				decodeUi(requiredObject(encoded, "ui")),
				requiredString(encoded, "processDescription"),
				requiredString(encoded, encoded.has("recipePolicy") ? "recipePolicy" : "recipeSystemPrompt"),
				new DynamicWorkstationRecipeDataSchema(requiredObject(encoded, "recipeDataSchema"))
		);
	}

	private static JsonObject encodeSlot(DynamicWorkstationSlot slot) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("id", slot.id());
		encoded.addProperty("role", slot.role().serializedName());
		encoded.addProperty("x", slot.x());
		encoded.addProperty("y", slot.y());
		encoded.addProperty("maxStack", slot.maxStack());
		encoded.addProperty("allowPlayerInsert", slot.allowPlayerInsert());
		encoded.addProperty("allowPlayerExtract", slot.allowPlayerExtract());
		if (slot.emptySlotIcon() != null) encoded.addProperty("emptySlotIcon", slot.emptySlotIcon());
		if (slot.tooltip() != null) encoded.addProperty("tooltip", slot.tooltip());
		return encoded;
	}

	private static DynamicWorkstationSlot decodeSlot(JsonObject encoded) {
		requireOnly(encoded, "id", "role", "x", "y", "maxStack", "allowPlayerInsert",
				"allowPlayerExtract", "emptySlotIcon", "tooltip");
		return new DynamicWorkstationSlot(
				requiredString(encoded, "id"),
				DynamicWorkstationSlotRole.parse(requiredString(encoded, "role")),
				requiredInt(encoded, "x"),
				requiredInt(encoded, "y"),
				requiredInt(encoded, "maxStack"),
				requiredBoolean(encoded, "allowPlayerInsert"),
				requiredBoolean(encoded, "allowPlayerExtract"),
				optionalString(encoded, "emptySlotIcon"),
				optionalString(encoded, "tooltip")
		);
	}

	private static JsonObject encodeUi(DynamicWorkstationUi ui) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("width", ui.width());
		encoded.addProperty("height", ui.height());
		encoded.addProperty("playerInventoryX", ui.playerInventoryX());
		encoded.addProperty("playerInventoryY", ui.playerInventoryY());
		encoded.addProperty("titleX", ui.titleX());
		encoded.addProperty("titleY", ui.titleY());
		encoded.addProperty("playerInventoryLabelX", ui.playerInventoryLabelX());
		encoded.addProperty("playerInventoryLabelY", ui.playerInventoryLabelY());
		encoded.addProperty("backgroundColor", ui.backgroundColor());

		JsonArray labels = new JsonArray();
		for (DynamicWorkstationLabel label : ui.labels()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", label.id());
			value.addProperty("text", label.text());
			value.addProperty("x", label.x());
			value.addProperty("y", label.y());
			value.addProperty("color", label.color());
			value.addProperty("shadow", label.shadow());
			if (label.tooltip() != null) value.addProperty("tooltip", label.tooltip());
			labels.add(value);
		}
		encoded.add("labels", labels);

		JsonArray gauges = new JsonArray();
		for (DynamicWorkstationGauge gauge : ui.gauges()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", gauge.id());
			value.addProperty("channel", gauge.channel());
			value.addProperty("x", gauge.x());
			value.addProperty("y", gauge.y());
			value.addProperty("width", gauge.width());
			value.addProperty("height", gauge.height());
			value.addProperty("direction", gauge.direction().serializedName());
			value.addProperty("fillColor", gauge.fillColor());
			value.addProperty("backgroundColor", gauge.backgroundColor());
			if (gauge.tooltip() != null) value.addProperty("tooltip", gauge.tooltip());
			gauges.add(value);
		}
		encoded.add("gauges", gauges);

		JsonArray buttons = new JsonArray();
		for (DynamicWorkstationButton button : ui.buttons()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", button.id());
			value.addProperty("role", button.role().serializedName());
			value.addProperty("label", button.label());
			value.addProperty("x", button.x());
			value.addProperty("y", button.y());
			value.addProperty("width", button.width());
			value.addProperty("height", button.height());
			if (button.tooltip() != null) value.addProperty("tooltip", button.tooltip());
			if (button.visibleChannel() != null) value.addProperty("visibleChannel", button.visibleChannel());
			if (button.enabledChannel() != null) value.addProperty("enabledChannel", button.enabledChannel());
			buttons.add(value);
		}
		encoded.add("buttons", buttons);

		JsonArray stateChannels = new JsonArray();
		for (DynamicWorkstationStateChannel channel : ui.stateChannels()) {
			JsonObject value = new JsonObject();
			value.addProperty("id", channel.id());
			value.addProperty("minimum", channel.minimum());
			value.addProperty("maximum", channel.maximum());
			value.addProperty("initialValue", channel.initialValue());
			stateChannels.add(value);
		}
		encoded.add("stateChannels", stateChannels);
		return encoded;
	}

	private static DynamicWorkstationUi decodeUi(JsonObject encoded) {
		requireOnly(encoded, "width", "height", "playerInventoryX", "playerInventoryY", "titleX", "titleY",
				"playerInventoryLabelX", "playerInventoryLabelY", "backgroundColor",
				"labels", "gauges", "buttons", "stateChannels");
		List<DynamicWorkstationLabel> labels = new ArrayList<>();
		JsonArray encodedLabels = requiredArray(encoded, "labels");
		if (encodedLabels.size() > DynamicWorkstationUi.MAX_LABELS) {
			throw new IllegalArgumentException("Workstation JSON contains too many labels");
		}
		for (JsonElement element : encodedLabels) {
			JsonObject value = requiredObject(element, "workstation label");
			requireOnly(value, "id", "text", "x", "y", "color", "shadow", "tooltip");
			labels.add(new DynamicWorkstationLabel(
					requiredString(value, "id"), requiredString(value, "text"),
					requiredInt(value, "x"), requiredInt(value, "y"), requiredString(value, "color"),
					requiredBoolean(value, "shadow"), optionalString(value, "tooltip")));
		}

		List<DynamicWorkstationGauge> gauges = new ArrayList<>();
		JsonArray encodedGauges = requiredArray(encoded, "gauges");
		if (encodedGauges.size() > DynamicWorkstationUi.MAX_GAUGES) {
			throw new IllegalArgumentException("Workstation JSON contains too many gauges");
		}
		for (JsonElement element : encodedGauges) {
			JsonObject value = requiredObject(element, "workstation gauge");
			requireOnly(value, "id", "channel", "x", "y", "width", "height", "direction",
					"fillColor", "backgroundColor", "tooltip");
			gauges.add(new DynamicWorkstationGauge(
					requiredString(value, "id"), requiredString(value, "channel"),
					requiredInt(value, "x"), requiredInt(value, "y"),
					requiredInt(value, "width"), requiredInt(value, "height"),
					DynamicWorkstationGaugeDirection.parse(requiredString(value, "direction")),
					requiredString(value, "fillColor"), requiredString(value, "backgroundColor"),
					optionalString(value, "tooltip")));
		}

		List<DynamicWorkstationButton> buttons = new ArrayList<>();
		JsonArray encodedButtons = requiredArray(encoded, "buttons");
		if (encodedButtons.size() > DynamicWorkstationUi.MAX_BUTTONS) {
			throw new IllegalArgumentException("Workstation JSON contains too many buttons");
		}
		for (JsonElement element : encodedButtons) {
			JsonObject value = requiredObject(element, "workstation button");
			requireOnly(value, "id", "role", "label", "x", "y", "width", "height", "tooltip",
					"visibleChannel", "enabledChannel");
			buttons.add(new DynamicWorkstationButton(
					requiredString(value, "id"),
					DynamicWorkstationButtonRole.parse(requiredString(value, "role")),
					requiredString(value, "label"), requiredInt(value, "x"), requiredInt(value, "y"),
					requiredInt(value, "width"), requiredInt(value, "height"), optionalString(value, "tooltip"),
					optionalString(value, "visibleChannel"), optionalString(value, "enabledChannel")));
		}

		List<DynamicWorkstationStateChannel> channels = new ArrayList<>();
		JsonArray encodedChannels = requiredArray(encoded, "stateChannels");
		if (encodedChannels.size() > DynamicWorkstationUi.MAX_STATE_CHANNELS) {
			throw new IllegalArgumentException("Workstation JSON contains too many state channels");
		}
		for (JsonElement element : encodedChannels) {
			JsonObject value = requiredObject(element, "workstation state channel");
			requireOnly(value, "id", "minimum", "maximum", "initialValue");
			channels.add(new DynamicWorkstationStateChannel(
					requiredString(value, "id"), requiredInt(value, "minimum"),
					requiredInt(value, "maximum"), requiredInt(value, "initialValue")));
		}

		return new DynamicWorkstationUi(
				requiredInt(encoded, "width"), requiredInt(encoded, "height"),
				requiredInt(encoded, "playerInventoryX"), requiredInt(encoded, "playerInventoryY"),
				requiredInt(encoded, "titleX"), requiredInt(encoded, "titleY"),
				requiredInt(encoded, "playerInventoryLabelX"), requiredInt(encoded, "playerInventoryLabelY"),
				requiredString(encoded, "backgroundColor"), labels, gauges, buttons, channels);
	}

	private static void requireOnly(JsonObject object, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String key : object.keySet()) {
			if (!names.contains(key)) throw new IllegalArgumentException("Unknown workstation JSON field: " + key);
		}
	}

	private static JsonObject requiredObject(JsonObject object, String key) {
		if (!(object.get(key) instanceof JsonObject value)) {
			throw new IllegalArgumentException("Missing workstation JSON object: " + key);
		}
		return value;
	}

	private static JsonObject requiredObject(JsonElement element, String description) {
		if (!(element instanceof JsonObject value)) {
			throw new IllegalArgumentException("Expected " + description + " JSON object");
		}
		return value;
	}

	private static JsonArray requiredArray(JsonObject object, String key) {
		if (!(object.get(key) instanceof JsonArray value)) {
			throw new IllegalArgumentException("Missing workstation JSON array: " + key);
		}
		return value;
	}

	private static String requiredString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			throw new IllegalArgumentException("Missing workstation JSON string: " + key);
		}
		return primitive.getAsString();
	}

	private static String optionalString(JsonObject object, String key) {
		return object.has(key) ? requiredString(object, key) : null;
	}

	private static int requiredInt(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			throw new IllegalArgumentException("Missing workstation JSON integer: " + key);
		}
		double raw = primitive.getAsDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Invalid workstation JSON integer: " + key);
		}
		return (int) raw;
	}

	private static boolean requiredBoolean(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
			throw new IllegalArgumentException("Missing workstation JSON boolean: " + key);
		}
		return primitive.getAsBoolean();
	}
}
