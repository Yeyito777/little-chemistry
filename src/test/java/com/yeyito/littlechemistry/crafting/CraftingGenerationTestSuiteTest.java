package com.yeyito.littlechemistry.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftingGenerationTestSuiteTest {
	@Test
	void suiteOneCapturesTheV4ManualGridsAndBackpackRegression() throws Exception {
		CraftingGenerationTestSuite suite = CraftingGenerationTestSuite.load(1);

		assertEquals("V4 manual crafting run", suite.name());
		assertEquals(11, suite.recipes().size());
		assertEquals("Phantom boat", suite.recipes().get(0).label());
		assertEquals("minecraft:oak_boat", suite.recipes().get(0).ingredients().get(4).toString());
		assertEquals(3, suite.recipes().get(1).width());
		assertEquals(2, suite.recipes().get(1).height());
		assertNull(suite.recipes().get(1).ingredients().get(0));
		assertEquals("minecraft:flint_and_steel", suite.recipes().get(4).ingredients().get(3).toString());
		assertEquals(1, suite.recipes().get(8).width());
		assertEquals(2, suite.recipes().get(8).height());
		assertEquals("minecraft:golden_nautilus_armor",
				suite.recipes().get(9).ingredients().get(3).toString());
		assertEquals("Leather backpack", suite.recipes().get(10).label());
		assertEquals("minecraft:string", suite.recipes().get(10).ingredients().get(1).toString());
		assertEquals("minecraft:chest", suite.recipes().get(10).ingredients().get(4).toString());
		assertEquals(7, suite.recipes().get(10).ingredients().stream()
				.filter(id -> id != null && id.toString().equals("minecraft:leather")).count());
		assertTrue(suite.recipes().stream().allMatch(recipe -> recipe.ingredients().size()
				== recipe.width() * recipe.height()));
	}

	@Test
	void placementBuildsAUniqueCenteredRowPerpendicularToThePlayer() {
		BlockPos player = new BlockPos(100, 64, 100);
		var positions = CraftingGenerationTestHarness.linePositions(player, Direction.NORTH, 11);

		assertEquals(11, positions.size());
		assertEquals(11, new HashSet<>(positions).size());
		assertTrue(positions.stream().allMatch(position -> position.getY() == 64 && position.getZ() == 96));
		assertEquals(95, positions.getFirst().getX());
		assertEquals(105, positions.getLast().getX());
	}
}
