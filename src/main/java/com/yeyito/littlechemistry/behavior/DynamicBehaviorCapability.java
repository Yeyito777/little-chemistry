package com.yeyito.littlechemistry.behavior;

/** The callback interfaces a generated behavior class may explicitly opt into. */
public enum DynamicBehaviorCapability {
	USE_AIR("useAir", UseAirBehavior.class),
	USE_ON_BLOCK("useOnBlock", UseOnBlockBehavior.class),
	INTERACT_LIVING_ENTITY("interactLivingEntity", InteractLivingEntityBehavior.class),
	INVENTORY_TICK("inventoryTick", InventoryTickBehavior.class),
	POST_HURT_ENEMY("postHurtEnemy", PostHurtEnemyBehavior.class),
	MINE_BLOCK("mineBlock", MineBlockBehavior.class),
	FINISH_USING("finishUsing", FinishUsingBehavior.class),
	CRAFTED("crafted", CraftedBehavior.class),
	USE_PLACED_BLOCK("usePlacedBlock", UsePlacedBlockBehavior.class),
	ATTACK_PLACED_BLOCK("attackPlacedBlock", AttackPlacedBlockBehavior.class),
	PLACED_BLOCK("placedBlock", PlacedBlockBehavior.class),
	BROKEN_BLOCK("brokenBlock", BrokenBlockBehavior.class),
	STEP_ON_BLOCK("stepOnBlock", StepOnBlockBehavior.class),
	FALL_ON_BLOCK("fallOnBlock", FallOnBlockBehavior.class),
	ENTITY_INSIDE_BLOCK("entityInsideBlock", EntityInsideBlockBehavior.class),
	RANDOM_TICK_BLOCK("randomTickBlock", RandomTickBlockBehavior.class),
	SCHEDULED_TICK_BLOCK("scheduledTickBlock", ScheduledTickBlockBehavior.class),
	NEIGHBOR_CHANGED_BLOCK("neighborChangedBlock", NeighborChangedBlockBehavior.class),
	PROJECTILE_HIT_BLOCK("projectileHitBlock", ProjectileHitBlockBehavior.class);

	private static final String PACKAGE_PREFIX = "com.yeyito.littlechemistry.behavior.";
	private final String callbackName;
	private final Class<? extends DynamicBehavior> interfaceClass;

	DynamicBehaviorCapability(String callbackName, Class<? extends DynamicBehavior> interfaceClass) {
		this.callbackName = callbackName;
		this.interfaceClass = interfaceClass;
	}

	public String callbackName() {
		return callbackName;
	}

	public String interfaceName() {
		return interfaceClass.getSimpleName();
	}

	public String qualifiedInterfaceName() {
		return PACKAGE_PREFIX + interfaceName();
	}

	public Class<? extends DynamicBehavior> interfaceClass() {
		return interfaceClass;
	}
}
