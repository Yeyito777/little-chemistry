package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicTooltipTextTest {
	@Test
	void wrapsPhantomSkiffProseAtWordBoundariesWithoutControlGlyphs() {
		String prose = "A membrane-winged oak skiff. Steer with your gaze, sneak to descend, "
				+ "and repair it with phantom membrane.";
		List<String> lines = DynamicTooltipText.wrap(prose, 40);

		assertEquals(List.of(
				"A membrane-winged oak skiff. Steer with",
				"your gaze, sneak to descend, and repair",
				"it with phantom membrane."), lines);
		assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= 40));
		assertTrue(lines.stream().noneMatch(line -> line.contains("\n") || line.contains("\r")));
	}

	@Test
	void canonicalDescriptionStoresProseRatherThanPresentationBreaks() {
		String normalized = DynamicContentDefinition.normalizeDescription(
				"A membrane-bound oak skiff that\r\nglides wherever its rider looks.");

		assertEquals("A membrane-bound oak skiff that glides wherever its rider looks.", normalized);
		assertFalse(normalized.contains("\n"));
		assertFalse(normalized.contains("\r"));
	}

	@Test
	void preservesExplicitBreaksForDisplayAndSplitsLongUnicodeTokensSafely() {
		assertEquals(List.of("first", "line", "second", "line"),
				DynamicTooltipText.wrap("first line\nsecond line", 6));
		assertEquals(List.of("😀😀", "😀"), DynamicTooltipText.wrap("😀😀😀", 2));
	}

	@Test
	void rejectsInvalidLineLength() {
		assertThrows(IllegalArgumentException.class, () -> DynamicTooltipText.wrap("text", 0));
	}
}
