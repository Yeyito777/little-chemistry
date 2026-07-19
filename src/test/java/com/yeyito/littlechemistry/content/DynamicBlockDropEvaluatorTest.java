package com.yeyito.littlechemistry.content;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicBlockDropEvaluatorTest {
	@Test
	void fortuneIsOreLikeButBoundedAndNeverAffectsNone() {
		RandomSource random = RandomSource.create(42L);
		assertEquals(3, DynamicBlockDropEvaluator.applyFortune(
				3, DynamicFortuneMode.NONE, 100, random));
		for (int index = 0; index < 100; index++) {
			int count = DynamicBlockDropEvaluator.applyFortune(
					3, DynamicFortuneMode.ORE_LIKE, 3, random);
			assertTrue(count >= 3 && count <= 12);
		}
		assertEquals(64, DynamicBlockDropEvaluator.applyFortune(
				64, DynamicFortuneMode.ORE_LIKE, 100, random));
	}

	@Test
	void explosionDecayUsesMinecraftStylePerItemSurvivalAndSafeBounds() {
		RandomSource random = RandomSource.create(42L);
		assertEquals(20, DynamicBlockDropEvaluator.applyExplosionDecay(20, false, 100.0F, random));
		assertEquals(20, DynamicBlockDropEvaluator.applyExplosionDecay(20, true, null, random));
		assertEquals(20, DynamicBlockDropEvaluator.applyExplosionDecay(20, true, 0.5F, random));
		assertEquals(0, DynamicBlockDropEvaluator.applyExplosionDecay(
				20, true, Float.POSITIVE_INFINITY, random));
	}

	@Test
	void rangedCountsStayInclusive() {
		DynamicDropEntry entry = new DynamicDropEntry(
				DynamicDropTargetKind.REGISTERED_ITEM, "minecraft:amethyst_shard",
				2, 4, 1.0, DynamicFortuneMode.NONE);
		RandomSource random = RandomSource.create(42L);
		boolean sawMinimum = false;
		boolean sawMaximum = false;
		for (int index = 0; index < 100; index++) {
			int count = DynamicBlockDropEvaluator.randomCount(entry, random);
			assertTrue(count >= 2 && count <= 4);
			sawMinimum |= count == 2;
			sawMaximum |= count == 4;
		}
		assertTrue(sawMinimum && sawMaximum);
	}
}
