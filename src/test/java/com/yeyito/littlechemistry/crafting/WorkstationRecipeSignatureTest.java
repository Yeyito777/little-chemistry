package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

final class WorkstationRecipeSignatureTest {
	private static final String BEHAVIOR_SOURCE = """
			public final class GeneratedBehaviorImpl implements
			        com.yeyito.littlechemistry.behavior.DynamicBehavior,
			        com.yeyito.littlechemistry.behavior.WorkstationBehavior,
			        com.yeyito.littlechemistry.behavior.WorkstationTickBehavior {
			    public GeneratedBehaviorImpl() {}
			    public com.yeyito.littlechemistry.behavior.WorkstationRecipeRequest createWorkstationRecipe(
			            com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) { return null; }
			    public void workstationTick(
			            com.yeyito.littlechemistry.behavior.DynamicWorkstationContext context) {}
			}
			""";

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void selectorContextIncludesTheSinglePrimaryOutputIdAndCapacity() {
		DynamicContentDefinition definition = definition();

		JsonObject context = WorkstationRecipeSignature.workstationContext(definition);

		JsonObject primaryOutput = context.getAsJsonObject("primaryOutput");
		assertEquals("result", primaryOutput.get("id").getAsString());
		assertEquals(12, primaryOutput.get("capacity").getAsInt());
	}

	private static DynamicContentDefinition definition() {
		JsonObject schema = JsonParser.parseString("""
				{"type":"object","properties":{"duration_ticks":{"type":"integer","minimum":1}},
				 "required":["duration_ticks"],"additionalProperties":false}
				""").getAsJsonObject();
		DynamicWorkstationSpec workstation = new DynamicWorkstationSpec(
				List.of(
						new DynamicWorkstationSlot("input", DynamicWorkstationSlotRole.INPUT,
								20, 20, 64, true, true, null, null),
						new DynamicWorkstationSlot("result", DynamicWorkstationSlotRole.OUTPUT,
								138, 20, 12, false, true, null, null)),
				new DynamicWorkstationUi(176, 166, 7, 84, "202020FF", List.of(), List.of(),
						List.of(new DynamicWorkstationButton("make_recipe", DynamicWorkstationButtonRole.MAKE_RECIPE,
								"Make Recipe", 70, 48, 76, 20, null)), List.of()),
				"Presses one input into a compact result.",
				"Choose a coherent result and set duration_ticks in Minecraft ticks.",
				new DynamicWorkstationRecipeDataSchema(schema));
		return new DynamicContentDefinition(
				DynamicContentType.BLOCK, "test_press", "Test Press", "Presses material.",
				DynamicRarity.COMMON, 0L, "0".repeat(64), null, null, null,
				DynamicBlockProperties.DEFAULT, null, null, BEHAVIOR_SOURCE, null, List.of(), workstation);
	}

}
