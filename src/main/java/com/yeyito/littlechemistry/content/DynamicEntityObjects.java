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
	public static EntityType<DynamicAquaticEntity> AQUATIC_CREATURE;
	public static EntityType<DynamicAquaticMonsterEntity> AQUATIC_MONSTER;
	public static EntityType<DynamicAmphibiousEntity> AMPHIBIOUS_CREATURE;
	public static EntityType<DynamicAmphibiousMonsterEntity> AMPHIBIOUS_MONSTER;
	public static EntityType<DynamicVehicleEntity> VEHICLE_CREATURE;
	public static EntityType<DynamicVehicleMonsterEntity> VEHICLE_MONSTER;

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

		ResourceKey<EntityType<?>> aquaticCreatureKey = key("dynamic_aquatic_creature");
		AQUATIC_CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, aquaticCreatureKey,
				EntityType.Builder.of(DynamicAquaticEntity::new, MobCategory.WATER_CREATURE)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().build(aquaticCreatureKey));

		ResourceKey<EntityType<?>> aquaticMonsterKey = key("dynamic_aquatic_monster");
		AQUATIC_MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, aquaticMonsterKey,
				EntityType.Builder.of(DynamicAquaticMonsterEntity::new, MobCategory.MONSTER)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().notInPeaceful().build(aquaticMonsterKey));

		ResourceKey<EntityType<?>> amphibiousCreatureKey = key("dynamic_amphibious_creature");
		AMPHIBIOUS_CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, amphibiousCreatureKey,
				EntityType.Builder.of(DynamicAmphibiousEntity::new, MobCategory.CREATURE)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().build(amphibiousCreatureKey));

		ResourceKey<EntityType<?>> amphibiousMonsterKey = key("dynamic_amphibious_monster");
		AMPHIBIOUS_MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, amphibiousMonsterKey,
				EntityType.Builder.of(DynamicAmphibiousMonsterEntity::new, MobCategory.MONSTER)
						.sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2)
						.noSummon().noLootTable().notInPeaceful().build(amphibiousMonsterKey));

		ResourceKey<EntityType<?>> vehicleCreatureKey = key("dynamic_vehicle_creature");
		VEHICLE_CREATURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, vehicleCreatureKey,
				EntityType.Builder.of(DynamicVehicleEntity::new, MobCategory.MISC)
						.sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(1)
						.noSummon().noLootTable().build(vehicleCreatureKey));

		ResourceKey<EntityType<?>> vehicleMonsterKey = key("dynamic_vehicle_monster");
		VEHICLE_MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, vehicleMonsterKey,
				EntityType.Builder.of(DynamicVehicleMonsterEntity::new, MobCategory.MONSTER)
						.sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(1)
						.noSummon().noLootTable().notInPeaceful().build(vehicleMonsterKey));

		FabricDefaultAttributeRegistry.register(GROUND_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GROUND_MONSTER, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FLYING_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FLYING_MONSTER, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(AQUATIC_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(AQUATIC_MONSTER, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(AMPHIBIOUS_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(AMPHIBIOUS_MONSTER, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(VEHICLE_CREATURE, DynamicCarrierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(VEHICLE_MONSTER, DynamicCarrierEntity.createAttributes());
	}

	public static EntityType<? extends DynamicCarrierEntity> carrier(DynamicEntityMovement movement,
			DynamicEntityDisposition disposition) {
		boolean hostile = disposition == DynamicEntityDisposition.HOSTILE;
		return switch (movement) {
			case GROUND -> hostile ? GROUND_MONSTER : GROUND_CREATURE;
			case FLYING -> hostile ? FLYING_MONSTER : FLYING_CREATURE;
			case AQUATIC -> hostile ? AQUATIC_MONSTER : AQUATIC_CREATURE;
			case AMPHIBIOUS -> hostile ? AMPHIBIOUS_MONSTER : AMPHIBIOUS_CREATURE;
			case VEHICLE -> hostile ? VEHICLE_MONSTER : VEHICLE_CREATURE;
		};
	}

	public static boolean matches(DynamicCarrierEntity carrier, DynamicEntityProperties properties) {
		boolean hostile = carrier instanceof net.minecraft.world.entity.monster.Enemy;
		boolean movementMatches = switch (properties.movement()) {
			case GROUND -> !(carrier instanceof DynamicFlyingEntity
					|| carrier instanceof DynamicAquaticEntity || carrier instanceof DynamicAmphibiousEntity
					|| carrier instanceof DynamicVehicleEntity);
			case FLYING -> carrier instanceof DynamicFlyingEntity;
			case AQUATIC -> carrier instanceof DynamicAquaticEntity;
			case AMPHIBIOUS -> carrier instanceof DynamicAmphibiousEntity;
			case VEHICLE -> carrier instanceof DynamicVehicleEntity;
		};
		return movementMatches && hostile == (properties.disposition() == DynamicEntityDisposition.HOSTILE);
	}

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, LittleChemistry.id(path));
	}
}
