package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentGenerationDraftTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void ordinaryItemCanRequestToolHeldType() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "magic staff");
		JsonObject arguments = new JsonObject();
		arguments.addProperty("itemType", "item");
		arguments.addProperty("heldType", "tool");
		arguments.addProperty("maxStack", 16);
		arguments.addProperty("foil", false);
		arguments.addProperty("enchantability", 0);
		arguments.addProperty("reach", 0.0);
		arguments.addProperty("placeable", false);
		arguments.addProperty("craftingUse", "keep");
		arguments.addProperty("fuelBurnTicks", 1600);

		ContentGenerationDraft.ToolExecution result = draft.execute("set_item_properties", arguments);

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertEquals(1600, draft.execute("inspect_draft", new JsonObject()).output()
				.get("fuelBurnTicks").getAsInt());
	}

	@Test
	void furnaceFuelDurationIsBoundedAndZeroDisablesFuel() {
		ContentGenerationDraft fuelDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "compressed embers");
		JsonObject fuel = ordinaryItemArguments();
		fuel.addProperty("fuelBurnTicks", 3200);
		ContentGenerationDraft.ToolExecution fuelResult = fuelDraft.execute("set_item_properties", fuel);

		ContentGenerationDraft noFuelDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "wet dust");
		ContentGenerationDraft.ToolExecution noFuelResult = noFuelDraft.execute(
				"set_item_properties", ordinaryItemArguments());

		ContentGenerationDraft invalidDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "endless ember");
		JsonObject invalid = ordinaryItemArguments();
		invalid.addProperty("fuelBurnTicks", com.yeyito.littlechemistry.content.DynamicItemProperties.MAX_FUEL_BURN_TICKS + 1);
		ContentGenerationDraft.ToolExecution invalidResult = invalidDraft.execute("set_item_properties", invalid);

		assertTrue(fuelResult.output().get("ok").getAsBoolean(), fuelResult.output().toString());
		assertEquals(3200, fuelDraft.execute("inspect_draft", new JsonObject()).output()
				.get("fuelBurnTicks").getAsInt());
		assertTrue(noFuelResult.output().get("ok").getAsBoolean(), noFuelResult.output().toString());
		assertEquals(0, noFuelDraft.execute("inspect_draft", new JsonObject()).output()
				.get("fuelBurnTicks").getAsInt());
		assertFalse(invalidResult.output().get("ok").getAsBoolean(), invalidResult.output().toString());
	}

	@Test
	void craftingUseSupportsReusableItemsAndDamageableTools() {
		ContentGenerationDraft reusableDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "casting mold");
		JsonObject reusable = ordinaryItemArguments();
		reusable.addProperty("craftingUse", "keep");
		ContentGenerationDraft.ToolExecution reusableResult = reusableDraft.execute("set_item_properties", reusable);

		ContentGenerationDraft invalidDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "fragile catalyst");
		JsonObject invalid = ordinaryItemArguments();
		invalid.addProperty("craftingUse", "damage");
		ContentGenerationDraft.ToolExecution invalidResult = invalidDraft.execute("set_item_properties", invalid);

		ContentGenerationDraft cuttersDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "wire cutters");
		JsonObject cutters = ordinaryItemArguments();
		cutters.addProperty("itemType", "tool");
		cutters.addProperty("heldType", "tool");
		cutters.addProperty("maxStack", 1);
		cutters.addProperty("craftingUse", "damage");
		ContentGenerationDraft.ToolExecution cuttersResult = cuttersDraft.execute("set_item_properties", cutters);

		assertTrue(reusableResult.output().get("ok").getAsBoolean(), reusableResult.output().toString());
		assertFalse(invalidResult.output().get("ok").getAsBoolean(), invalidResult.output().toString());
		assertTrue(cuttersResult.output().get("ok").getAsBoolean(), cuttersResult.output().toString());
	}

	@Test
	void everyPublishedHeldPoseIsAcceptedIndependentlyOfGameplayType() {
		for (DynamicHeldType heldType : DynamicHeldType.values()) {
			ContentGenerationDraft draft = new ContentGenerationDraft(
					DynamicContentType.ITEM, heldType.serializedName() + " trinket");
			JsonObject arguments = ordinaryItemArguments();
			arguments.addProperty("heldType", heldType.serializedName());

			ContentGenerationDraft.ToolExecution result = draft.execute("set_item_properties", arguments);

			assertTrue(result.output().get("ok").getAsBoolean(), heldType + ": " + result.output());
		}
	}

	@Test
	void recipeOutputCountMustFitTheGeneratedItemStack() {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				DynamicContentType.ITEM, "copper bolts", null, 8);
		JsonObject tooSmall = ordinaryItemArguments();
		tooSmall.addProperty("maxStack", 4);

		ContentGenerationDraft.ToolExecution rejected = draft.execute("set_item_properties", tooSmall);
		ContentGenerationDraft.ToolExecution afterRejected = draft.execute("inspect_draft", new JsonObject());
		JsonObject largeEnough = ordinaryItemArguments();
		largeEnough.addProperty("maxStack", 16);
		ContentGenerationDraft.ToolExecution accepted = draft.execute("set_item_properties", largeEnough);

		assertFalse(rejected.output().get("ok").getAsBoolean(), rejected.output().toString());
		assertTrue(rejected.output().get("message").getAsString().contains("output count of 8"));
		assertTrue(afterRejected.output().getAsJsonArray("missing").toString().contains("itemProperties"));
		assertTrue(accepted.output().get("ok").getAsBoolean(), accepted.output().toString());
	}

	@Test
	void armorPropertiesMustUseRequestedSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				DynamicContentType.ARMOR, "lunar boots", DynamicArmorSlot.BOOTS);
		JsonObject arguments = armorArguments("head");

		ContentGenerationDraft.ToolExecution result = draft.execute("set_armor_properties", arguments);

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("boots"));
	}

	@Test
	void armorPropertiesAcceptRequestedSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(
				DynamicContentType.ARMOR, "lunar boots", DynamicArmorSlot.BOOTS);

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_properties", armorArguments("boots"));

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}

	@Test
	void armorPropertiesInferSlotWhenNoneWasRequested() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar boots");

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_properties", armorArguments("boots"));

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}

	@Test
	void armorDraftRequiresAndAcceptsSeparateDisplayTexture() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar boots");
		JsonObject empty = new JsonObject();

		ContentGenerationDraft.ToolExecution before = draft.execute("inspect_draft", empty);
		ContentGenerationDraft.ToolExecution set = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());
		ContentGenerationDraft.ToolExecution after = draft.execute("inspect_draft", empty);

		assertTrue(before.output().getAsJsonArray("missing").toString().contains("armorDisplayTexture"));
		assertTrue(set.output().get("ok").getAsBoolean(), set.output().toString());
		assertFalse(after.output().getAsJsonArray("missing").toString().contains("armorDisplayTexture"));
	}

	@Test
	void nonArmorCannotSetArmorDisplayTexture() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "lunar shard");

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("not creating armor"));
	}

	@Test
	void itemTextureCannotBeFullyTransparent() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "invisible shard");
		JsonObject arguments = itemTextureArguments();
		arguments.getAsJsonArray("palette").set(1, new com.google.gson.JsonPrimitive("20202000"));
		arguments.getAsJsonArray("palette").set(2, new com.google.gson.JsonPrimitive("E0F0FF00"));

		ContentGenerationDraft.ToolExecution result = draft.execute("set_texture", arguments);

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("invisible"));
	}

	@Test
	void starBlockAcceptsTransparentTextureInItsModel() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "ember spray");
		draft.execute("set_block_role", ordinaryBlockRole());
		draft.execute("set_metadata", metadataArguments("uncommon", "A warm spray of suspended embers."));
		draft.execute("set_block_properties", blockPropertiesArguments("star"));

		ContentGenerationDraft.ToolExecution texture = draft.execute("set_block_model", starBlockModelArguments());

		assertTrue(texture.output().get("ok").getAsBoolean(), texture.output().toString());
		assertDoesNotThrow(() -> ContentGenerationDraft.validateBlockTextureAlpha(
				starTextureSpec(), DynamicBlockShape.STAR));
	}

	@Test
	void blockModelSupportsDifferentFaceTexturesAndDimensions() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "layered masonry");
		draft.execute("set_block_properties", blockPropertiesArguments("full_cube"));

		ContentGenerationDraft.ToolExecution model = draft.execute("set_block_model", multiTextureBlockModelArguments());

		assertTrue(model.output().get("ok").getAsBoolean(), model.output().toString());
		assertEquals(2, model.output().get("textureCount").getAsInt());
		assertEquals(32 * 16 + 8 * 8, model.output().get("totalTexturePixels").getAsInt());
	}

	@Test
	void blockPropertiesStoreTheAiDirectionalChoice() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "facing machine");
		JsonObject properties = blockPropertiesArguments("full_cube");
		properties.addProperty("directional", true);

		ContentGenerationDraft.ToolExecution set = draft.execute("set_block_properties", properties);
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertTrue(set.output().get("ok").getAsBoolean(), set.output().toString());
		assertTrue(inspected.output().get("directional").getAsBoolean(), inspected.output().toString());
	}

	@Test
	void customBlockModelAcceptsAiAuthoredCuboids() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "crystal pedestal");
		draft.execute("set_block_properties", blockPropertiesArguments("custom"));
		JsonObject arguments = opaqueBlockModelArguments(16, 16);
		com.google.gson.JsonArray elements = arguments.getAsJsonArray("elements");
		JsonObject base = new JsonObject();
		base.add("from", coordinates(2, 0, 2));
		base.add("to", coordinates(14, 4, 14));
		base.addProperty("collision", true);
		base.add("faces", new JsonObject());
		elements.add(base);
		JsonObject pillar = new JsonObject();
		pillar.add("from", coordinates(6, 4, 6));
		pillar.add("to", coordinates(10, 16, 10));
		pillar.addProperty("collision", false);
		pillar.add("faces", new JsonObject());
		elements.add(pillar);

		ContentGenerationDraft.ToolExecution model = draft.execute("set_block_model", arguments);

		assertTrue(model.output().get("ok").getAsBoolean(), model.output().toString());
		assertEquals(2, model.output().get("elementCount").getAsInt());
	}

	@Test
	void completeTransparentBlockModelSubmitsWithCompiledBehavior() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "ember spray");
		draft.execute("set_block_role", ordinaryBlockRole());
		draft.execute("set_metadata", metadataArguments("uncommon", "A warm spray of suspended embers."));
		JsonObject blockProperties = blockPropertiesArguments("star");
		blockProperties.addProperty("directional", true);
		draft.execute("set_block_properties", blockProperties);
		draft.execute("set_block_model", starBlockModelArguments());
		JsonObject redstone = new JsonObject();
		redstone.addProperty("redstonePower", 0);
		redstone.addProperty("comparatorPower", 0);
		draft.execute("set_block_redstone", redstone);
		JsonObject light = new JsonObject();
		light.addProperty("level", 8);
		light.addProperty("visuallyEmissive", true);
		draft.execute("set_block_light", light);
		JsonObject particles = new JsonObject();
		particles.add("emitters", new com.google.gson.JsonArray());
		draft.execute("set_block_particles", particles);
		draft.execute("set_block_drops", selfDropArguments());
		draft.execute("set_behavior_source", behaviorSourceArguments());
		draft.execute("compile_behavior", new JsonObject());

		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());

		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertEquals(DynamicBlockShape.STAR, submitted.submitted().block().shape());
		assertTrue(submitted.submitted().block().directional());
		assertEquals(net.minecraft.world.item.Rarity.UNCOMMON, submitted.submitted().block().rarity());
		assertEquals(1, submitted.submitted().blockModel().textures().size());
	}

	@Test
	void completeBlockCanAuthorAnimatedParticleAndUseItForAmbience() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "ember crystal");
		draft.execute("set_block_role", ordinaryBlockRole());
		draft.execute("set_metadata", metadataArguments("rare", "A crystal that releases its own animated embers."));
		draft.execute("set_block_properties", blockPropertiesArguments("full_cube"));
		draft.execute("set_block_model", opaqueBlockModelArguments(16, 16));
		JsonObject redstone = new JsonObject();
		redstone.addProperty("redstonePower", 0);
		redstone.addProperty("comparatorPower", 0);
		draft.execute("set_block_redstone", redstone);
		JsonObject light = new JsonObject();
		light.addProperty("level", 7);
		light.addProperty("visuallyEmissive", true);
		draft.execute("set_block_light", light);

		ContentGenerationDraft.ToolExecution custom = draft.execute(
				"set_custom_particles", customParticleArguments());
		JsonObject particles = new JsonObject();
		com.google.gson.JsonArray emitters = new com.google.gson.JsonArray();
		JsonObject emitter = new JsonObject();
		emitter.addProperty("particle", "custom:embers");
		emitter.addProperty("chancePerTick", 0.1);
		emitter.addProperty("count", 2);
		emitter.addProperty("velocity", 0.04);
		emitter.addProperty("region", "top");
		emitters.add(emitter);
		particles.add("emitters", emitters);
		ContentGenerationDraft.ToolExecution ambience = draft.execute("set_block_particles", particles);
		draft.execute("set_block_drops", selfDropArguments());
		draft.execute("set_behavior_source", particleBehaviorSourceArguments("embers"));
		draft.execute("compile_behavior", new JsonObject());

		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());
		draft.execute("set_behavior_source", particleBehaviorSourceArguments("missing"));
		draft.execute("compile_behavior", new JsonObject());
		ContentGenerationDraft.ToolExecution rejected = draft.execute("submit", new JsonObject());

		assertTrue(custom.output().get("ok").getAsBoolean(), custom.output().toString());
		assertTrue(ambience.output().get("ok").getAsBoolean(), ambience.output().toString());
		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertEquals("embers", submitted.submitted().customParticles().getFirst().id());
		assertEquals(2, submitted.submitted().customParticles().getFirst().frames().size());
		assertEquals("custom:embers", submitted.submitted().block().particles().getFirst().particle());
		assertFalse(rejected.output().get("ok").getAsBoolean(), rejected.output().toString());
		assertTrue(rejected.output().get("message").getAsString().contains("undefined custom particle"));
	}

	@Test
	void blockDropsAreRequiredAndAcceptBoundedNativeRules() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "luminous ore");

		ContentGenerationDraft.ToolExecution before = draft.execute("inspect_draft", new JsonObject());
		JsonObject drops = selfDropArguments();
		JsonObject primary = drops.getAsJsonArray("entries").get(0).getAsJsonObject();
		primary.addProperty("targetKind", "registered_item");
		primary.addProperty("target", "minecraft:amethyst_shard");
		primary.addProperty("minCount", 2);
		primary.addProperty("maxCount", 4);
		primary.addProperty("fortune", "ore_like");
		drops.addProperty("silkTouchDropsSelf", true);
		drops.addProperty("explosionDecay", true);

		ContentGenerationDraft.ToolExecution accepted = draft.execute("set_block_drops", drops);
		ContentGenerationDraft.ToolExecution after = draft.execute("inspect_draft", new JsonObject());

		assertTrue(before.output().getAsJsonArray("missing").toString().contains("blockDrops"));
		assertTrue(accepted.output().get("ok").getAsBoolean(), accepted.output().toString());
		assertEquals(1, after.output().get("blockDropEntryCount").getAsInt());
		assertTrue(after.output().get("silkTouchDropsSelf").getAsBoolean());
	}

	@Test
	void blockDropsRejectMultiplyingTheOwningBlock() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "duplicating block");
		JsonObject drops = selfDropArguments();
		JsonObject primary = drops.getAsJsonArray("entries").get(0).getAsJsonObject();
		primary.addProperty("maxCount", 2);

		ContentGenerationDraft.ToolExecution rejected = draft.execute("set_block_drops", drops);

		assertFalse(rejected.output().get("ok").getAsBoolean(), rejected.output().toString());
		assertTrue(rejected.output().get("message").getAsString().contains("exactly one"));
	}

	@Test
	void completeBlockCanExplicitlyDefineACompiledWorkstation() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "crystal press");
		JsonObject role = new JsonObject();
		role.addProperty("workstation", true);
		assertTrue(draft.execute("set_block_role", role).output().get("ok").getAsBoolean());

		JsonObject policy = new JsonObject();
		policy.addProperty("processDescription", "Compresses one material through controlled pressure.");
		policy.addProperty("recipeSystemPrompt", "Choose pressure-formed outputs that conserve the captured material. Express durations in Minecraft ticks; 20 ticks equal one second.");
		JsonObject dataSchema = new JsonObject();
		dataSchema.addProperty("type", "object");
		dataSchema.add("properties", new JsonObject());
		dataSchema.add("required", new com.google.gson.JsonArray());
		dataSchema.addProperty("additionalProperties", false);
		policy.add("recipeDataSchema", dataSchema);
		assertTrue(draft.execute("set_workstation_policy", policy).output().get("ok").getAsBoolean());

		JsonObject layout = new JsonObject();
		com.google.gson.JsonArray slots = new com.google.gson.JsonArray();
		slots.add(workstationSlot("input", "input", 28, 24, true, true));
		slots.add(workstationSlot("result", "output", 132, 24, false, true));
		layout.add("slots", slots);
		JsonObject ui = new JsonObject();
		ui.addProperty("width", 176); ui.addProperty("height", 166);
		ui.addProperty("playerInventoryX", 7); ui.addProperty("playerInventoryY", 84);
		ui.addProperty("titleX", 8); ui.addProperty("titleY", 6);
		ui.addProperty("playerInventoryLabelX", 7); ui.addProperty("playerInventoryLabelY", 72);
		ui.addProperty("backgroundColor", "202530FF");
		ui.add("labels", new com.google.gson.JsonArray());
		ui.add("gauges", new com.google.gson.JsonArray());
		ui.add("stateChannels", new com.google.gson.JsonArray());
		com.google.gson.JsonArray buttons = new com.google.gson.JsonArray();
		JsonObject button = new JsonObject();
		button.addProperty("id", "make_recipe"); button.addProperty("role", "make_recipe");
		button.addProperty("label", "Make Recipe"); button.addProperty("x", 70); button.addProperty("y", 50);
		button.addProperty("width", 76); button.addProperty("height", 20);
		buttons.add(button); ui.add("buttons", buttons); layout.add("ui", ui);
		assertTrue(draft.execute("set_workstation_layout", layout).output().get("ok").getAsBoolean());

		JsonObject invalidReplacement = policy.deepCopy();
		invalidReplacement.addProperty("processDescription", "This rejected replacement must not leak into the draft.");
		invalidReplacement.addProperty("recipeSystemPrompt", "ticks "
				+ "x".repeat(DynamicWorkstationSpec.MAX_RECIPE_SYSTEM_PROMPT_LENGTH));
		ContentGenerationDraft.ToolExecution replacement = draft.execute("set_workstation_policy", invalidReplacement);
		assertFalse(replacement.output().get("ok").getAsBoolean(), replacement.output().toString());

		draft.execute("set_metadata", metadataArguments("rare", "A programmable pressure-forming workstation."));
		draft.execute("set_block_properties", blockPropertiesArguments("full_cube"));
		draft.execute("set_block_model", opaqueBlockModelArguments(16, 16));
		JsonObject redstone = new JsonObject(); redstone.addProperty("redstonePower", 0); redstone.addProperty("comparatorPower", 0);
		draft.execute("set_block_redstone", redstone);
		JsonObject light = new JsonObject(); light.addProperty("level", 0); light.addProperty("visuallyEmissive", false);
		draft.execute("set_block_light", light);
		JsonObject particles = new JsonObject(); particles.add("emitters", new com.google.gson.JsonArray());
		draft.execute("set_block_particles", particles);
		draft.execute("set_block_drops", selfDropArguments());
		JsonObject source = new JsonObject(); source.addProperty("source", workstationBehaviorSource());
		draft.execute("set_behavior_source", source);
		assertTrue(draft.execute("compile_behavior", new JsonObject()).output().get("ok").getAsBoolean());

		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());
		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertEquals("Compresses one material through controlled pressure.",
				submitted.submitted().workstation().processDescription());
		assertEquals(policy.get("recipeSystemPrompt").getAsString(),
				submitted.submitted().workstation().recipeSystemPrompt());
	}

	@Test
	void workstationPolicyMustDescribeTimingInTicks() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "untimed press");
		JsonObject role = new JsonObject();
		role.addProperty("workstation", true);
		draft.execute("set_block_role", role);
		JsonObject policy = new JsonObject();
		policy.addProperty("processDescription", "Presses an input.");
		policy.addProperty("recipeSystemPrompt", "Choose a pressure-formed output after a brief duration.");
		policy.add("recipeDataSchema", JsonParser.parseString(
				"{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}")
				.getAsJsonObject());

		ContentGenerationDraft.ToolExecution rejected = draft.execute("set_workstation_policy", policy);

		assertFalse(rejected.output().get("ok").getAsBoolean(), rejected.output().toString());
		assertTrue(rejected.output().get("message").getAsString().contains("Minecraft ticks"));
	}

	@Test
	void ordinaryBlockInspectionReportsWorkstationBehaviorMismatch() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "misclassified press");
		draft.execute("set_block_role", ordinaryBlockRole());
		JsonObject source = new JsonObject();
		source.addProperty("source", workstationBehaviorSource());
		draft.execute("set_behavior_source", source);

		ContentGenerationDraft.ToolExecution inspection = draft.execute("inspect_draft", new JsonObject());

		assertTrue(inspection.output().getAsJsonArray("missing").toString()
				.contains("ordinaryBlockWorkstationBehaviorMismatch"), inspection.output().toString());
	}

	@Test
	void changingToAnIncompatibleShapeClearsTheOldModelForReplacement() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "changing block");
		draft.execute("set_block_properties", blockPropertiesArguments("custom"));
		JsonObject custom = opaqueBlockModelArguments(16, 16);
		JsonObject element = new JsonObject();
		element.add("from", coordinates(2, 0, 2));
		element.add("to", coordinates(14, 16, 14));
		element.addProperty("collision", true);
		element.add("faces", new JsonObject());
		custom.getAsJsonArray("elements").add(element);
		draft.execute("set_block_model", custom);

		ContentGenerationDraft.ToolExecution changed = draft.execute(
				"set_block_properties", blockPropertiesArguments("slab"));
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertTrue(changed.output().get("ok").getAsBoolean(), changed.output().toString());
		assertTrue(changed.output().get("modelCleared").getAsBoolean(), changed.output().toString());
		assertTrue(inspected.output().getAsJsonArray("missing").toString().contains("blockModel"));
	}

	@Test
	void blockModelRejectsFullyInvisibleTexture() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.BLOCK, "invisible block");
		draft.execute("set_block_properties", blockPropertiesArguments("custom"));
		JsonObject model = opaqueBlockModelArguments(1, 1);
		JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		texture.getAsJsonArray("palette").set(0, new com.google.gson.JsonPrimitive("20304000"));
		texture.getAsJsonArray("palette").set(1, new com.google.gson.JsonPrimitive("7090B000"));
		texture.getAsJsonArray("palette").set(2, new com.google.gson.JsonPrimitive("E0F0FF00"));
		JsonObject element = new JsonObject();
		element.add("from", coordinates(0, 0, 0));
		element.add("to", coordinates(16, 16, 16));
		element.addProperty("collision", false);
		element.add("faces", new JsonObject());
		model.getAsJsonArray("elements").add(element);

		ContentGenerationDraft.ToolExecution result = draft.execute("set_block_model", model);

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("visible"));
	}

	@Test
	void solidBlockRejectsTransparentStarTexture() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ContentGenerationDraft.validateBlockTextureAlpha(
						starTextureSpec(), DynamicBlockShape.FULL_CUBE));

		assertTrue(error.getMessage().contains("opaque"));
	}

	@Test
	void armorDisplayTextureMustMatchPropertiesSlot() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ARMOR, "lunar helmet");
		draft.execute("set_armor_properties", armorArguments("head"));

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_armor_display_texture", armorDisplayTextureArguments());

		assertFalse(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("message").getAsString().contains("head"));
	}

	@Test
	void completedItemRequiresAndSubmitsWithCompiledBehaviorClass() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		draft.execute("set_metadata", metadataArguments("rare", "A dense cobalt bar with an icy sheen."));
		draft.execute("set_texture", itemTextureArguments());
		JsonObject itemProperties = ordinaryItemArguments();
		itemProperties.addProperty("fuelBurnTicks", 1600);
		draft.execute("set_item_properties", itemProperties);
		draft.execute("set_behavior_source", behaviorSourceArguments());
		draft.execute("compile_behavior", new JsonObject());

		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());
		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());

		assertTrue(inspected.output().get("behaviorRequired").getAsBoolean(), inspected.output().toString());
		assertTrue(inspected.output().get("complete").getAsBoolean(), inspected.output().toString());
		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertTrue(submitted.submitted().behaviorSource().contains("GeneratedBehaviorImpl"));
		assertEquals("A dense cobalt bar with\nan icy sheen.", submitted.submitted().description());
		assertEquals(net.minecraft.world.item.Rarity.RARE, submitted.submitted().item().rarity());
		assertEquals(1600, submitted.submitted().item().fuelBurnTicks());
	}

	@Test
	void completeEntitySubmitsNativePropertiesModelParticlesAndCompiledBehavior() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ENTITY, "crystal wisp");
		draft.execute("set_metadata", metadataArguments("rare", "A sharp-tempered crystal spirit."));
		draft.execute("set_texture", itemTextureArguments());
		draft.execute("set_custom_particles", customParticleArguments());

		JsonObject properties = entityPropertiesArguments();
		ContentGenerationDraft.ToolExecution acceptedProperties = draft.execute("set_entity_properties", properties);

		JsonObject model = opaqueBlockModelArguments(16, 16);
		JsonObject body = new JsonObject();
		body.add("from", coordinates(2, 1, 2));
		body.add("to", coordinates(14, 15, 14));
		body.add("faces", new JsonObject());
		model.getAsJsonArray("elements").add(body);
		ContentGenerationDraft.ToolExecution acceptedModel = draft.execute("set_entity_model", model);
		draft.execute("set_behavior_source", entityBehaviorSourceArguments());
		draft.execute("compile_behavior", new JsonObject());

		ContentGenerationDraft.ToolExecution submitted = draft.execute("submit", new JsonObject());

		assertTrue(acceptedProperties.output().get("ok").getAsBoolean(), acceptedProperties.output().toString());
		assertTrue(acceptedModel.output().get("ok").getAsBoolean(), acceptedModel.output().toString());
		assertTrue(submitted.output().get("ok").getAsBoolean(), submitted.output().toString());
		assertEquals(com.yeyito.littlechemistry.content.DynamicEntityMovement.FLYING,
				submitted.submitted().entity().movement());
		assertEquals(1, submitted.submitted().entityModel().elements().size());
		assertEquals("embers", submitted.submitted().customParticles().getFirst().id());
		assertTrue(submitted.submitted().behaviorSource().contains("EntityTickBehavior"));
	}

	@Test
	void entityCanUseAnimatedVanillaModelWithCompatibleAuthoredSkin() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ENTITY, "amber cow");
		JsonObject visual = new JsonObject();
		visual.addProperty("profile", "cow");
		visual.addProperty("width", 64);
		visual.addProperty("height", 64);
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("6B3E20FF");
		palette.add("E0B070FF");
		visual.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 64; y++) rows.add((y % 2 == 0 ? "12" : "21").repeat(32));
		visual.add("rows", rows);

		ContentGenerationDraft.ToolExecution accepted = draft.execute("set_entity_vanilla_model", visual);
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertTrue(accepted.output().get("ok").getAsBoolean(), accepted.output().toString());
		assertEquals("cow", inspected.output().get("entityModelProfile").getAsString());
		assertTrue(inspected.output().get("entityModelAnimated").getAsBoolean());
		assertEquals(0, inspected.output().get("entityModelElementCount").getAsInt());
	}

	@Test
	void metadataRequiresRarityAndShortDescription() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");

		ContentGenerationDraft.ToolExecution accepted = draft.execute(
				"set_metadata", metadataArguments("mythical", "A compact bar of refined cobalt."));
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());
		ContentGenerationDraft.ToolExecution rejected = draft.execute(
				"set_metadata", metadataArguments("rare", "x".repeat(121)));

		assertTrue(accepted.output().get("ok").getAsBoolean(), accepted.output().toString());
		assertTrue(inspected.output().get("metadataSet").getAsBoolean(), inspected.output().toString());
		assertEquals("mythical", inspected.output().get("rarity").getAsString());
		assertFalse(rejected.output().get("ok").getAsBoolean(), rejected.output().toString());
	}

	@Test
	void metadataWrapsTooltipDescriptionAfterEveryFiveWords() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");

		ContentGenerationDraft.ToolExecution result = draft.execute(
				"set_metadata", metadataArguments("rare", "One two three four five six seven eight nine ten eleven."));

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertEquals("One two three four five\nsix seven eight nine ten\neleven.",
				result.output().get("description").getAsString());
	}

	@Test
	void passiveMarkerOnlyBehaviorCompilesWithoutFakeCallbacks() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		JsonObject source = new JsonObject();
		source.addProperty("source", "public final class GeneratedBehaviorImpl implements com.yeyito.littlechemistry.behavior.DynamicBehavior { public GeneratedBehaviorImpl() {} }");
		draft.execute("set_behavior_source", source);

		ContentGenerationDraft.ToolExecution withSource = draft.execute("inspect_draft", new JsonObject());
		ContentGenerationDraft.ToolExecution compiled = draft.execute("compile_behavior", new JsonObject());

		assertTrue(withSource.output().getAsJsonArray("missing").toString().contains("behaviorCompilation"));
		assertTrue(compiled.output().get("ok").getAsBoolean(), compiled.output().toString());
	}

	@Test
	void submitRequiresBehaviorSource() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");
		draft.execute("set_texture", itemTextureArguments());
		draft.execute("set_item_properties", ordinaryItemArguments());

		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_draft", new JsonObject());

		assertFalse(inspected.output().get("complete").getAsBoolean(), inspected.output().toString());
		assertTrue(inspected.output().getAsJsonArray("missing").toString().contains("behaviorSource"));
		assertTrue(inspected.output().get("behaviorRequired").getAsBoolean());
	}

	@Test
	void behaviorSourceIsAcceptedWithoutAPlan() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "cobalt ingot");

		ContentGenerationDraft.ToolExecution result = draft.execute("set_behavior_source", behaviorSourceArguments());

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
	}

	@Test
	void behaviorApiPublishesOptInCapabilitiesWithoutDefaults() {
		ContentGenerationDraft draft = new ContentGenerationDraft(DynamicContentType.ITEM, "fairy wand");
		ContentGenerationDraft.ToolExecution inspected = draft.execute("inspect_behavior_api", new JsonObject());

		assertTrue(inspected.output().get("required").getAsBoolean(), inspected.output().toString());
		assertTrue(inspected.output().get("implementationScope").getAsString().contains("empty marker"));
		assertTrue(inspected.output().getAsJsonArray("capabilities").toString().contains("UseAirBehavior"));
		assertTrue(inspected.output().getAsJsonArray("capabilities").toString().contains("useAir"));
		assertFalse(inspected.output().getAsJsonArray("capabilities").toString().contains("EntityTickBehavior"));
		assertFalse(inspected.output().has("workstationCore"));
		ContentGenerationDraft blockDraft = new ContentGenerationDraft(DynamicContentType.BLOCK, "fairy press");
		ContentGenerationDraft.ToolExecution blockApi = blockDraft.execute("inspect_behavior_api", new JsonObject());
		assertTrue(blockApi.output().get("workstationCore").getAsString()
				.contains("excluded from recipe-cache identity"));
		assertTrue(blockApi.output().get("workstationCore").getAsString()
				.contains("canonical cacheDiscriminator"));
		ContentGenerationDraft entityDraft = new ContentGenerationDraft(DynamicContentType.ENTITY, "fairy");
		ContentGenerationDraft.ToolExecution entityApi = entityDraft.execute("inspect_behavior_api", new JsonObject());
		assertTrue(entityApi.output().getAsJsonArray("capabilities").toString().contains("EntityTickBehavior"));
		assertFalse(entityApi.output().getAsJsonArray("capabilities").toString().contains("UseAirBehavior"));
		assertFalse(entityApi.output().has("workstationCore"));
		assertFalse(inspected.output().has("example"));
	}

	@Test
	void behaviorCompilationRejectsCallbacksFromAnotherContentSubsystem() {
		ContentGenerationDraft itemDraft = new ContentGenerationDraft(DynamicContentType.ITEM, "bad item");
		JsonObject source = new JsonObject();
		source.addProperty("source", """
				public final class GeneratedBehaviorImpl implements
						com.yeyito.littlechemistry.behavior.DynamicBehavior,
						com.yeyito.littlechemistry.behavior.EntityTickBehavior {
					public GeneratedBehaviorImpl() {}
					public void entityTick(com.yeyito.littlechemistry.behavior.DynamicGeneratedEntityContext context) {}
				}
				""");
		itemDraft.execute("set_behavior_source", source);

		ContentGenerationDraft.ToolExecution compiled = itemDraft.execute("compile_behavior", new JsonObject());

		assertFalse(compiled.output().get("ok").getAsBoolean(), compiled.output().toString());
		assertTrue(compiled.output().get("message").getAsString().contains("do not apply"),
				compiled.output().toString());
	}

	@Test
	void overworldVegetationProfileAddsGrassCompatibleSupport() {
		java.util.List<String> supports = ContentGenerationDraft.resolvePlacementSupports(
				"overworld_vegetation", java.util.List.of("minecraft:sand"));

		assertEquals(java.util.List.of("#minecraft:supports_vegetation", "minecraft:sand"), supports);
	}

	private static JsonObject armorArguments(String slot) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("slot", slot);
		arguments.addProperty("foil", true);
		arguments.addProperty("enchantability", 15);
		arguments.addProperty("defense", 3.0);
		arguments.addProperty("toughness", 2.0);
		arguments.addProperty("knockbackResistance", 0.1);
		arguments.addProperty("durability", 400);
		return arguments;
	}

	private static JsonObject armorDisplayTextureArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("slot", "boots");
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("305080FF");
		palette.add("D0F0FFFF");
		arguments.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 32; y++) {
			rows.add(y < 16 ? "1".repeat(32) + "0".repeat(32) : "12".repeat(16) + "0".repeat(32));
		}
		arguments.add("rows", rows);
		return arguments;
	}

	private static JsonObject ordinaryItemArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("itemType", "item");
		arguments.addProperty("heldType", "regular");
		arguments.addProperty("maxStack", 64);
		arguments.addProperty("foil", false);
		arguments.addProperty("enchantability", 0);
		arguments.addProperty("reach", 0.0);
		arguments.addProperty("placeable", false);
		arguments.addProperty("craftingUse", "consume");
		arguments.addProperty("fuelBurnTicks", 0);
		return arguments;
	}

	private static JsonObject entityPropertiesArguments() {
		JsonObject properties = new JsonObject();
		properties.addProperty("movement", "flying");
		properties.addProperty("disposition", "hostile");
		properties.addProperty("width", 0.8);
		properties.addProperty("height", 1.2);
		properties.addProperty("eyeHeight", 0.9);
		properties.addProperty("maxHealth", 24.0);
		properties.addProperty("movementSpeed", 0.3);
		properties.addProperty("attackDamage", 5.0);
		properties.addProperty("armor", 2.0);
		properties.addProperty("knockbackResistance", 0.1);
		properties.addProperty("followRange", 32.0);
		properties.addProperty("experienceReward", 8);
		properties.addProperty("fireImmune", false);
		properties.addProperty("ambientSound", "minecraft:entity.zombie.ambient");
		properties.addProperty("hurtSound", "minecraft:entity.zombie.hurt");
		properties.addProperty("deathSound", "minecraft:entity.zombie.death");
		com.google.gson.JsonArray drops = new com.google.gson.JsonArray();
		JsonObject drop = new JsonObject();
		drop.addProperty("item", "minecraft:amethyst_shard");
		drop.addProperty("minimum", 1);
		drop.addProperty("maximum", 2);
		drop.addProperty("chance", 0.5);
		drops.add(drop);
		properties.add("drops", drops);
		return properties;
	}

	private static JsonObject metadataArguments(String rarity, String description) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("rarity", rarity);
		arguments.addProperty("description", description);
		return arguments;
	}

	private static JsonObject starTextureArguments() {
		JsonObject arguments = new JsonObject();
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("401000FF");
		palette.add("D06010FF");
		palette.add("FFF080FF");
		arguments.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 16; y++) {
			StringBuilder row = new StringBuilder(16);
			for (int x = 0; x < 16; x++) {
				int pattern = Math.floorMod(x * 3 + y * 5 + x * y, 11);
				row.append(pattern < 5 ? "0" : Integer.toString(1 + pattern % 3));
			}
			rows.add(row.toString());
		}
		arguments.add("rows", rows);
		return arguments;
	}

	private static JsonObject blockPropertiesArguments(String shape) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("material", "stone");
		arguments.addProperty("hardness", 2.0);
		arguments.addProperty("preferredTool", "pickaxe");
		arguments.addProperty("requiresCorrectTool", false);
		arguments.addProperty("shape", shape);
		arguments.addProperty("directional", false);
		return arguments;
	}

	private static JsonObject selfDropArguments() {
		JsonObject entry = new JsonObject();
		entry.addProperty("targetKind", "self");
		entry.addProperty("target", "self");
		entry.addProperty("minCount", 1);
		entry.addProperty("maxCount", 1);
		entry.addProperty("chance", 1.0);
		entry.addProperty("fortune", "none");
		com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
		entries.add(entry);
		JsonObject arguments = new JsonObject();
		arguments.add("entries", entries);
		arguments.addProperty("silkTouchDropsSelf", false);
		arguments.addProperty("explosionDecay", false);
		return arguments;
	}

	private static JsonObject starBlockModelArguments() {
		JsonObject arguments = new JsonObject();
		JsonObject texture = starTextureArguments();
		texture.addProperty("id", "all");
		texture.addProperty("width", 16);
		texture.addProperty("height", 16);
		com.google.gson.JsonArray textures = new com.google.gson.JsonArray();
		textures.add(texture);
		arguments.add("textures", textures);
		arguments.addProperty("particleTexture", "all");
		arguments.add("faces", allFaces("all"));
		arguments.add("elements", new com.google.gson.JsonArray());
		return arguments;
	}

	private static JsonObject opaqueBlockModelArguments(int width, int height) {
		JsonObject arguments = new JsonObject();
		JsonObject texture = indexedTexture("all", width, height);
		com.google.gson.JsonArray textures = new com.google.gson.JsonArray();
		textures.add(texture);
		arguments.add("textures", textures);
		arguments.addProperty("particleTexture", "all");
		arguments.add("faces", allFaces("all"));
		arguments.add("elements", new com.google.gson.JsonArray());
		return arguments;
	}

	private static JsonObject multiTextureBlockModelArguments() {
		JsonObject arguments = new JsonObject();
		com.google.gson.JsonArray textures = new com.google.gson.JsonArray();
		textures.add(indexedTexture("sides", 8, 8));
		textures.add(indexedTexture("caps", 32, 16));
		arguments.add("textures", textures);
		arguments.addProperty("particleTexture", "sides");
		JsonObject faces = allFaces("sides");
		faces.add("up", face("caps"));
		faces.add("down", face("caps"));
		arguments.add("faces", faces);
		arguments.add("elements", new com.google.gson.JsonArray());
		return arguments;
	}

	private static JsonObject indexedTexture(String id, int width, int height) {
		JsonObject texture = new JsonObject();
		texture.addProperty("id", id);
		texture.addProperty("width", width);
		texture.addProperty("height", height);
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("203040FF");
		palette.add("7090B0FF");
		palette.add("E0F0FFFF");
		texture.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < height; y++) {
			StringBuilder row = new StringBuilder(width);
			for (int x = 0; x < width; x++) row.append((x + y * 2 + x * y) % 3);
			rows.add(row.toString());
		}
		texture.add("rows", rows);
		return texture;
	}

	private static JsonObject allFaces(String texture) {
		JsonObject faces = new JsonObject();
		for (String direction : new String[] {"down", "up", "north", "south", "west", "east"}) {
			faces.add(direction, face(texture));
		}
		return faces;
	}

	private static JsonObject face(String texture) {
		JsonObject face = new JsonObject();
		face.addProperty("texture", texture);
		return face;
	}

	private static com.google.gson.JsonArray coordinates(int... values) {
		com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
		for (int value : values) coordinates.add(value);
		return coordinates;
	}

	private static DynamicTextureSpec starTextureSpec() {
		JsonObject arguments = starTextureArguments();
		java.util.List<String> palette = new java.util.ArrayList<>();
		arguments.getAsJsonArray("palette").forEach(value -> palette.add(value.getAsString()));
		java.util.List<String> rows = new java.util.ArrayList<>();
		arguments.getAsJsonArray("rows").forEach(value -> rows.add(value.getAsString()));
		return new DynamicTextureSpec(palette, rows);
	}

	private static JsonObject itemTextureArguments() {
		JsonObject arguments = new JsonObject();
		com.google.gson.JsonArray palette = new com.google.gson.JsonArray();
		palette.add("00000000");
		palette.add("202020FF");
		palette.add("E0F0FFFF");
		arguments.add("palette", palette);
		com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
		for (int y = 0; y < 16; y++) rows.add("0120120120120120");
		arguments.add("rows", rows);
		return arguments;
	}

	private static JsonObject customParticleArguments() {
		JsonObject arguments = new JsonObject();
		com.google.gson.JsonArray particles = new com.google.gson.JsonArray();
		JsonObject particle = new JsonObject();
		particle.addProperty("id", "embers");
		com.google.gson.JsonArray frames = new com.google.gson.JsonArray();
		for (int frameIndex = 0; frameIndex < 2; frameIndex++) {
			JsonObject frame = indexedTexture("unused", 8, 8);
			frame.remove("id");
			frame.remove("width");
			frame.remove("height");
			frame.getAsJsonArray("palette").set(0, new com.google.gson.JsonPrimitive("00000000"));
			frames.add(frame);
		}
		particle.add("frames", frames);
		particle.addProperty("frameTicks", 2);
		particle.addProperty("loop", true);
		particle.addProperty("lifetimeTicks", 30);
		particle.addProperty("startSize", 0.2);
		particle.addProperty("endSize", 0.05);
		particle.addProperty("startColor", "FFFFFFFF");
		particle.addProperty("endColor", "FF804000");
		particle.addProperty("gravity", -0.1);
		particle.addProperty("friction", 0.96);
		particle.addProperty("collision", false);
		particle.addProperty("emissive", true);
		particle.addProperty("spin", 0.04);
		particles.add(particle);
		arguments.add("particles", particles);
		return arguments;
	}

	private static JsonObject ordinaryBlockRole() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("workstation", false);
		return arguments;
	}

	private static JsonObject workstationSlot(String id, String role, int x, int y,
			boolean insert, boolean extract) {
		JsonObject slot = new JsonObject();
		slot.addProperty("id", id); slot.addProperty("role", role);
		slot.addProperty("x", x); slot.addProperty("y", y); slot.addProperty("maxStack", 64);
		slot.addProperty("allowPlayerInsert", insert); slot.addProperty("allowPlayerExtract", extract);
		return slot;
	}

	private static String workstationBehaviorSource() {
		return """
				public final class GeneratedBehaviorImpl implements
						com.yeyito.littlechemistry.behavior.DynamicBehavior,
						com.yeyito.littlechemistry.behavior.WorkstationBehavior,
						com.yeyito.littlechemistry.behavior.WorkstationTickBehavior {
					public GeneratedBehaviorImpl() {}
					public com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest createWorkstationRecipe(
							com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
						if (context.stack("input").isEmpty()) return null;
						return com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest.builder("compression")
								.consume("input", 1).putAiContext("pressure", "high").build();
					}
					public void workstationTick(com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
						if (context.recipeStatus() == com.yeyito.littlechemistry.behavior.WorkstationRecipeStatus.READY)
							context.tryCompleteRecipe();
					}
				}
				""";
	}

	private static JsonObject behaviorSourceArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("source", DynamicBehaviorSource.completeLegacySource(null));
		return arguments;
	}

	private static JsonObject entityBehaviorSourceArguments() {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("source", """
				import com.yeyito.littlechemistry.behavior.*;
				import com.yeyito.littlechemistry.particle.DynamicParticles;
				public final class GeneratedBehaviorImpl implements DynamicBehavior,
						EntitySpawnedBehavior, EntityTickBehavior {
					public GeneratedBehaviorImpl() {}
					public void entitySpawned(DynamicGeneratedEntityContext context) {
						context.state().setInt("ticks", 0);
					}
					public void entityTick(DynamicGeneratedEntityContext context) {
						context.state().setInt("ticks", context.state().getInt("ticks", 0) + 1);
						DynamicParticles.spawn(context.level(), context.definition(), "embers",
								context.entity().getX(), context.entity().getY() + 0.5, context.entity().getZ(),
								0.0, 0.01, 0.0);
					}
				}
				""");
		return arguments;
	}

	private static JsonObject particleBehaviorSourceArguments(String particleId) {
		JsonObject arguments = new JsonObject();
		arguments.addProperty("source", """
				import com.yeyito.littlechemistry.behavior.*;
				import com.yeyito.littlechemistry.particle.DynamicParticles;
				import net.minecraft.world.InteractionResult;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, UseAirBehavior {
				    public GeneratedBehaviorImpl() {}
				    public InteractionResult useAir(DynamicItemUseContext context) {
				        DynamicParticles.spawn(context.level(), context.definition(), "%s",
				                context.player().getX(), context.player().getY() + 1.0, context.player().getZ(),
				                0.0, 0.02, 0.0);
				        return InteractionResult.SUCCESS;
				    }
				}
				""".formatted(particleId));
		return arguments;
	}
}
