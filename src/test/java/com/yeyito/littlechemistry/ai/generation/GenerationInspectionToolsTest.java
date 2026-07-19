package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GenerationInspectionToolsTest {
	@Test
	void exposesTheSharedDynamicAndJavaInspectionToolSet() {
		JsonArray tools = new JsonArray();

		GenerationInspectionTools.addTo(tools);

		Set<String> names = new HashSet<>();
		tools.forEach(tool -> names.add(tool.getAsJsonObject().get("name").getAsString()));
		assertEquals(Set.of(
				"search_dynamic_content",
				"inspect_dynamic_content",
				"search_java_classes",
				"inspect_java_class",
				"inspect_java_source"
		), names);
	}
}
