package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		var search = tools.asList().stream().map(element -> element.getAsJsonObject())
				.filter(tool -> tool.get("name").getAsString().equals("search_dynamic_content"))
				.findFirst().orElseThrow();
		assertTrue(search.getAsJsonObject("parameters").getAsJsonObject("properties")
				.getAsJsonObject("kind").getAsJsonArray("enum").toString().contains("entity"));
		var inspect = tools.asList().stream().map(element -> element.getAsJsonObject())
				.filter(tool -> tool.get("name").getAsString().equals("inspect_dynamic_content"))
				.findFirst().orElseThrow();
		assertTrue(inspect.get("description").getAsString().contains("entity"));
	}
}
