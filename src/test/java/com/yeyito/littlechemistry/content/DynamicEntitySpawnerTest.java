package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicEntitySpawnerTest {
	@Test
	void sideAndUndersidePlacementClearTheSupportingBlockForLargeCarriers() {
		BlockPos clicked = new BlockPos(10, 20, 30);

		var east = DynamicEntitySpawner.placementPosition(clicked, Direction.EAST, 1.65F, 1.0F);
		assertTrue(east.x() - 1.65 / 2.0 > clicked.getX() + 1.0,
				"the carrier's west edge must clear the clicked block's east face");
		assertEquals(20.0, east.y());

		var below = DynamicEntitySpawner.placementPosition(clicked, Direction.DOWN, 1.0F, 2.4F);
		assertTrue(below.y() + 2.4 < clicked.getY(),
				"the carrier's top must clear the clicked block's underside");
	}

	@Test
	void ordinaryTopPlacementKeepsMinecraftsAdjacentCellCenter() {
		var position = DynamicEntitySpawner.placementPosition(BlockPos.ZERO, Direction.UP, 0.6F, 1.8F);
		assertEquals(0.5, position.x());
		assertEquals(1.0, position.y());
		assertEquals(0.5, position.z());
	}
}
