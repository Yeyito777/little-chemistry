package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaCodeInspectorTest {
	@Test
	void inspectsGeneratedBehaviorCapabilityWithoutInitializingTarget() {
		JsonObject input = new JsonObject();
		input.addProperty("className", "com.yeyito.littlechemistry.behavior.UseAirBehavior");
		input.addProperty("memberQuery", "use");
		input.addProperty("includeInherited", false);
		ContentGenerationDraft.ToolExecution result = JavaCodeInspector.inspect(input);
		assertTrue(result.output().get("ok").getAsBoolean());
		assertEquals("interface", result.output().get("kind").getAsString());
		assertFalse(result.output().getAsJsonArray("methods").isEmpty());
		assertTrue(result.output().getAsJsonArray("methods").toString().contains("useAir"));
	}

	@Test
	void reportsMissingClassesAsToolFeedback() {
		JsonObject input = new JsonObject();
		input.addProperty("className", "not.a.real.MinecraftClass");
		ContentGenerationDraft.ToolExecution result = JavaCodeInspector.inspect(input);
		assertFalse(result.output().get("ok").getAsBoolean());
		assertEquals("JAVA_CLASS_NOT_FOUND", result.output().get("code").getAsString());
	}
}
