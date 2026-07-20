package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Fixed registry infrastructure used by every logical generated entity. */
public final class DynamicEntityObjects {
	public static EntityType<DynamicCarrierEntity> GROUND_CREATURE;
	public static EntityType<DynamicGroundMonsterEntity> GROUND_MONSTER;
	public static EntityType<DynamicFlyingEntity> FLYING_CREATURE;
	public static EntityType<DynamicFlyingMonsterEntity> FLYING_MONSTER;

	private DynamicEntityObjects() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> groundCreatureKey = key("dynamic_ground_creature");
		GROUND_CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, groundCreatureKey,
				EntityType.Builder.of(DynamicCarrierEntity::new, MobCategory.CREATURE)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().build(groundCreatureKey));

		ResourceKey<EntityType<?>> groundMonsterKey = key("dynamic_ground_monster");
		GROUND_MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, groundMonsterKey,
				EntityType.Builder.of(DynamicGroundMonsterEntity::new, MobCategory.MONSTER)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().notInPeaceful().build(groundMonsterKey));

		ResourceKey<EntityType<?>> flyingCreatureKey = key("dynamic_flying_creature");
		FLYING_CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, flyingCreatureKey,
				EntityType.Builder.of(DynamicFlyingEntity::new, MobCategory.CREATURE)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().build(flyingCreatureKey));

		ResourceKey<EntityType<?>> flyingMonsterKey = key("dynamic_flying_monster");
		FLYING_MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, flyingMonsterKey,
				EntityType.Builder.of(DynamicFlyingMonsterEntity::new, MobCategory.MONSTER)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().notInPeaceful().build(flyingMonsterKey));

		FabricDefaultAttributeRegistry.register(GROUND_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GROUND_MONSTER, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FLYING_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FLYING_MONSTER, DynamicCarrierEntity.createAttributes());
	}

	public static EntityType<? extends DynamicCarrierEntity> carrier(DynamicEntityMovement movement,
			DynamicEntityDisposition disposition) {
		boolean hostile = disposition == DynamicEntityDisposition.HOSTILE;
		if (movement == DynamicEntityMovement.FLYING) return hostile ? FLYING_MONSTER : FLYING_CREATURE;
		return hostile ? GROUND_MONSTER : GROUND_CREATURE;
	}

	public static boolean matches(DynamicCarrierEntity carrier, DynamicEntityProperties properties) {
		boolean flying = carrier instanceof DynamicFlyingEntity;
		boolean hostile = carrier instanceof net.minecraft.world.entity.monster.Enemy;
		return flying == (properties.movement() == DynamicEntityMovement.FLYING)
				&& hostile == (properties.disposition() == DynamicEntityDisposition.HOSTILE);
	}

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, LittleChemistry.id(path));
	}
}
