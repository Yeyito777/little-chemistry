package com.yeyito.littlechemistry.behavior;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicEntityPreBehaviorTest {
	@Test
	void preDamageAndPreAttackCapabilitiesCompileAndAreDiscoverable() {
		String source = """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.EntityPreHurtBehavior,
				        com.yeyito.littlechemistry.behavior.EntityPreAttackBehavior {
				    public GeneratedBehaviorImpl() {}
				    @Override public float entityPreHurt(
				            com.yeyito.littlechemistry.behavior.DynamicGeneratedEntityContext context,
				            net.minecraft.world.damagesource.DamageSource source, float amount) {
				        return amount * 0.5F;
				    }
				    @Override public boolean entityPreAttack(
				            com.yeyito.littlechemistry.behavior.DynamicGeneratedEntityContext context,
				            net.minecraft.world.entity.Entity target) {
				        return true;
				    }
				}
				""";

		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(source).instantiate();

		assertTrue(behavior instanceof EntityPreHurtBehavior);
		assertTrue(behavior instanceof EntityPreAttackBehavior);
		assertEquals(Set.of(DynamicBehaviorCapability.ENTITY_PRE_HURT,
				DynamicBehaviorCapability.ENTITY_PRE_ATTACK), DynamicBehaviorSource.capabilities(source));
	}
}
