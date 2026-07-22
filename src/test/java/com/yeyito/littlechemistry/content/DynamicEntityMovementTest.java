package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicEntityMovementTest {
	@Test
	void everyRegisteredCarrierFamilyRoundTripsItsSerializedName() {
		for (DynamicEntityMovement movement : DynamicEntityMovement.values()) {
			assertEquals(movement, DynamicEntityMovement.parse(movement.serializedName()));
		}
	}

	@Test
	void waterNativeFamiliesExplicitlyOverrideUnderwaterBreathing() throws Exception {
		assertEquals(DynamicAquaticEntity.class,
				DynamicAquaticEntity.class.getMethod("canBreatheUnderwater").getDeclaringClass());
		assertEquals(DynamicAmphibiousEntity.class,
				DynamicAmphibiousEntity.class.getMethod("canBreatheUnderwater").getDeclaringClass());
		assertEquals(DynamicAquaticEntity.class,
				DynamicAquaticMonsterEntity.class.getMethod("canBreatheUnderwater").getDeclaringClass());
		assertEquals(DynamicAmphibiousEntity.class,
				DynamicAmphibiousMonsterEntity.class.getMethod("canBreatheUnderwater").getDeclaringClass());
	}
}
