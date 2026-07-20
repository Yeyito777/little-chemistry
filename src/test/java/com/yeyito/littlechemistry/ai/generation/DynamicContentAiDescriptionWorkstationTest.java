package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicBlockProperties;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicWorkstationButton;
import com.yeyito.littlechemistry.content.DynamicWorkstationButtonRole;
import com.yeyito.littlechemistry.content.DynamicWorkstationRecipeDataSchema;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlot;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlotRole;
import com.yeyito.littlechemistry.content.DynamicWorkstationSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationUi;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicContentAiDescriptionWorkstationTest {
	private static final String WORKSTATION_BEHAVIOR = """
			public final class GeneratedBehaviorImpl implements
					com.yeyito.littlechemistry.behavior.DynamicBehavior,
					com.yeyito.littlechemistry.behavior.WorkstationBehavior,
					com.yeyito.littlechemistry.behavior.WorkstationTickBehavior {
				public GeneratedBehaviorImpl() {}
				public com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest createWorkstationRecipe(
						com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
					return com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest.builder()
							.consume("input", 1).build();
				}
				public void workstationTick(com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {
					if (context.recipeStatus() == com.yeyito.littlechemistry.behavior.WorkstationRecipeStatus.READY)
						context.tryCompleteRecipe();
				}
			}
			""";
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void summaryIsCompactWhileFullDescriptionExposesWorkstationPolicyAndSchema() {
		JsonObject schema = JsonParser.parseString("""
				{
				  "type":"object",
				  "properties":{"duration_ticks":{"type":"integer","minimum":1,"maximum":1200}},
				  "required":["duration_ticks"],
				  "additionalProperties":false
				}
				""").getAsJsonObject();
		DynamicWorkstationSpec workstation = new DynamicWorkstationSpec(
				List.of(
						new DynamicWorkstationSlot("input", DynamicWorkstationSlotRole.INPUT,
								20, 20, 64, true, true, null, null),
						new DynamicWorkstationSlot("result", DynamicWorkstationSlotRole.OUTPUT,
								138, 20, 64, false, true, null, null)),
				new DynamicWorkstationUi(176, 166, 7, 84, "202020FF", List.of(), List.of(),
						List.of(new DynamicWorkstationButton("make_recipe", DynamicWorkstationButtonRole.MAKE_RECIPE,
								"Make Recipe", 70, 48, 76, 20, null)), List.of()),
				"Condenses one input into a stable crystal.",
				"Choose outputs consistent with pressure crystallization and conserve material.",
				new DynamicWorkstationRecipeDataSchema(schema));
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "crystal_press", "Crystal Press", "Presses crystals.",
				DynamicRarity.COMMON, 0L, "0".repeat(64), null, null, null,
				DynamicBlockProperties.DEFAULT, null, null, WORKSTATION_BEHAVIOR,
				null, List.of(), workstation);

		JsonObject summary = DynamicContentAiDescription.summarize(definition);
		JsonObject full = DynamicContentAiDescription.describe(definition);

		assertTrue(summary.get("isWorkstation").getAsBoolean());
		assertEquals(2, summary.getAsJsonObject("workstation").get("slotCount").getAsInt());
		assertEquals("output", summary.getAsJsonObject("workstation").getAsJsonArray("slots")
				.get(1).getAsJsonObject().get("role").getAsString());
		assertFalse(summary.getAsJsonObject("workstation").has("recipeSystemPrompt"));
		assertEquals(workstation.recipeSystemPrompt(),
				full.getAsJsonObject("workstation").get("recipeSystemPrompt").getAsString());
		assertEquals("object", full.getAsJsonObject("workstation").getAsJsonObject("recipeDataSchema")
				.get("type").getAsString());
		assertEquals("make_recipe", full.getAsJsonObject("workstation").getAsJsonObject("ui")
				.getAsJsonArray("buttons").get(0).getAsJsonObject().get("role").getAsString());
	}
}
