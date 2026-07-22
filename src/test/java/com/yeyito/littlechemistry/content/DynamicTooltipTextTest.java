package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTooltipTextTest {
	@Test
	void wrapsNaturalProseAtWordBoundaries() {
		List<String> lines = DynamicTooltipText.wrap(
				"A membrane-winged oak skiff. Steer with your gaze, sneak to descend, and repair it with phantom membrane.",
				40);

		assertEquals(List.of(
				"A membrane-winged oak skiff. Steer with",
				"your gaze, sneak to descend, and repair",
				"it with phantom membrane."), lines);
		assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= 40));
	}

	@Test
	void preservesExplicitBreaksAndSplitsLongTokens() {
		assertEquals(List.of("first", "line", "second", "line"),
				DynamicTooltipText.wrap("first line\nsecond line", 6));
		assertEquals(List.of("abc", "def", "g"), DynamicTooltipText.wrap("abcdefg", 3));
	}

	@Test
	void rejectsInvalidLineLength() {
		assertThrows(IllegalArgumentException.class, () -> DynamicTooltipText.wrap("text", 0));
	}
}
