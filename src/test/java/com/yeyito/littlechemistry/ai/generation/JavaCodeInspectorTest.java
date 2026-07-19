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

	@Test
	void decompilesCompleteLoadedMethodBodies() {
		JsonObject input = new JsonObject();
		input.addProperty("className", JavaSourceInspectionFixture.class.getName());

		ContentGenerationDraft.ToolExecution result = JavaCodeInspector.inspectSource(input);

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertEquals("vineflower_decompiled_classpath_bytecode", result.output().get("sourceKind").getAsString());
		assertFalse(result.output().get("truncated").getAsBoolean());
		assertTrue(result.output().get("javaSource").getAsString().contains("return value * 2"));
	}

	@Test
	void decompilesVanillaMinecraftMethodBodies() {
		JsonObject input = new JsonObject();
		input.addProperty("className", "net.minecraft.world.item.Item");

		ContentGenerationDraft.ToolExecution result = JavaCodeInspector.inspectSource(input);

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertTrue(result.output().get("classFileCount").getAsInt() > 1);
		String source = result.output().get("javaSource").getAsString();
		assertTrue(source.contains("package net.minecraft.world.item;"));
		assertTrue(source.contains("InteractionResult use("));
		assertTrue(source.contains("return InteractionResult.PASS;"));
	}

	@Test
	void includesTheCompleteClassFamilyForNestedMinecraftClasses() {
		JsonObject input = new JsonObject();
		input.addProperty("className", "net.minecraft.world.item.Item$Properties");

		ContentGenerationDraft.ToolExecution result = JavaCodeInspector.inspectSource(input);

		assertTrue(result.output().get("ok").getAsBoolean(), result.output().toString());
		assertEquals("net.minecraft.world.item.Item", result.output().get("decompiledClassName").getAsString());
		assertTrue(result.output().get("classFileCount").getAsInt() > 1);
		assertTrue(result.output().get("javaSource").getAsString().contains("class Properties"));
	}
}

final class JavaSourceInspectionFixture {
	int twice(int value) {
		return value * 2;
	}
}
