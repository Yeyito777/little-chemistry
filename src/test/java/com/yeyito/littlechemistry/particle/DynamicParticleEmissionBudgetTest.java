package com.yeyito.littlechemistry.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicParticleEmissionBudgetTest {
	@Test
	void capsPerDefinitionLiveParticlesUntilTheirLifetimeExpires() {
		DynamicParticleEmissionBudget budget = new DynamicParticleEmissionBudget();

		assertEquals(32, count(budget.reserve("embers", 10, 20, 128)));
		assertEquals(0, count(budget.reserve("embers", 10, 20, 1)));
		assertEquals(32, count(budget.reserve("embers", 11, 20, 128)));
		assertEquals(0, count(budget.reserve("embers", 12, 20, 1)));
		assertEquals(32, count(budget.reserve("embers", 30, 20, 128)));
	}

	@Test
	void capsCallsEvenWhenEachCallEmitsOneParticle() {
		DynamicParticleEmissionBudget budget = new DynamicParticleEmissionBudget();

		for (int index = 0; index < DynamicParticleEmissionBudget.MAX_CALLS_PER_TICK_PER_DEFINITION; index++) {
			assertEquals(1, count(budget.reserve("spark", 4, 1, 1)));
		}
		assertEquals(0, count(budget.reserve("spark", 4, 1, 1)));
		assertEquals(1, count(budget.reserve("spark", 5, 1, 1)));
	}

	@Test
	void appliesASeparateWholeLevelBudget() {
		DynamicParticleEmissionBudget budget = new DynamicParticleEmissionBudget();
		int emitted = 0;
		for (int definition = 0; definition < 16; definition++) {
			emitted += count(budget.reserve("particle_" + definition, 1, 100, 32));
		}

		assertEquals(DynamicParticleEmissionBudget.MAX_PARTICLES_PER_TICK_PER_LEVEL, emitted);
		assertEquals(0, count(budget.reserve("another", 1, 100, 1)));
	}

	@Test
	void refundReleasesLiveCapacityButRetainsAttemptRateLimits() {
		DynamicParticleEmissionBudget budget = new DynamicParticleEmissionBudget();
		DynamicParticleEmissionBudget.Reservation failed = budget.reserve("spark", 7, 100, 32);
		budget.refund(failed);

		assertEquals(0, count(budget.reserve("spark", 7, 100, 1)));
		assertEquals(32, count(budget.reserve("spark", 8, 100, 32)));
	}

	private static int count(DynamicParticleEmissionBudget.Reservation reservation) {
		return reservation == null ? 0 : reservation.count();
	}
}
