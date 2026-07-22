package com.yeyito.littlechemistry.ai.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MinecraftReferenceExporterTest {
	@Test
	void referencePixelsAreNormalizedToBinaryAlphaForGeneratedArtworkStudy() {
		assertEquals(0x00000000, MinecraftReferenceExporter.normalizeAlpha(0x0F336699));
		assertEquals(0xFF336699, MinecraftReferenceExporter.normalizeAlpha(0x10336699));
		assertEquals(0xFF336699, MinecraftReferenceExporter.normalizeAlpha(0x80336699));
		assertEquals(0xFF336699, MinecraftReferenceExporter.normalizeAlpha(0xFF336699));
	}
}
