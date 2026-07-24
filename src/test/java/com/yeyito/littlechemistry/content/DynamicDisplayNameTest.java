package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DynamicDisplayNameTest {
	@Test
	void separatesPlayerFacingTitlesFromIdentifierFormatting() {
		assertEquals("Phantom Skiff", DynamicDisplayName.normalize("phantom_skiff"));
		assertEquals("Dark Oak Cabinet", DynamicDisplayName.normalize("DARK-OAK cabinet"));
		assertEquals("Explorer's Backpack", DynamicDisplayName.normalize("explorer's backpack"));
		assertEquals("Sunwoven Crown", DynamicDisplayName.normalize("SunwovenCrown"));
	}

	@Test
	void stripsAccidentalOuterQuotesAndRejectsNamesWithoutWords() {
		assertEquals("Golden Nautilus Cuirass", DynamicDisplayName.normalize("  \"golden nautilus cuirass\"  "));
		assertThrows(IllegalArgumentException.class, () -> DynamicDisplayName.normalize("___"));
		assertThrows(IllegalArgumentException.class, () -> DynamicDisplayName.normalize("bad\nname"));
	}

	@Test
	void authoritativeDefinitionsStoreTheCanonicalTitleUsedByItemsAndMenus() {
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "phantom_skiff", "phantom_skiff", 0L, "0".repeat(64), null,
				null, DynamicItemProperties.DEFAULT, DynamicBehaviorSource.completeLegacySource(null));

		assertEquals("Phantom Skiff", definition.displayName());
		assertEquals("Phantom Skiff", DynamicContentJson.encodeDefinition(definition).get("displayName").getAsString());
	}
}
