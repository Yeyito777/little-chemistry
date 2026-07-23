package com.yeyito.littlechemistry.content;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicWorkstationSlotIconTest {
	@Test
	void resolvesGuiSpriteIdsAndGeneralLegacyItemAliases() {
		Identifier lapis = Identifier.parse("minecraft:container/slot/lapis_lazuli");

		assertEquals(lapis, DynamicWorkstationSlotIcon.resolve("minecraft:container/slot/lapis_lazuli"));
		assertEquals(lapis, DynamicWorkstationSlotIcon.resolve("minecraft:item/lapis_lazuli"));
		assertEquals(lapis, DynamicWorkstationSlotIcon.resolve("minecraft:item/empty_slot_lapis_lazuli"));
		assertEquals(lapis, DynamicWorkstationSlotIcon.resolve(
				"minecraft:textures/gui/sprites/container/slot/lapis_lazuli.png"));
		assertNotNull(DynamicWorkstationSlotIcon.resolve("minecraft:container/slot/amethyst_shard"));
		assertNull(DynamicWorkstationSlotIcon.resolve("minecraft:container/slot/definitely_missing"));
	}
}
