package com.yeyito.littlechemistry.content;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared validated spawn lifecycle used by spawner items and operator commands. */
public final class DynamicEntitySpawner {
	private DynamicEntitySpawner() {
	}

	public static @Nullable DynamicCarrierEntity spawn(ServerLevel level, DynamicContentDefinition definition,
			Vec3 position, float yRot, @Nullable LivingEntity source, EntitySpawnReason reason) {
		if (definition == null || definition.type() != DynamicContentType.ENTITY) return null;
		if (definition.entity().disposition() == DynamicEntityDisposition.HOSTILE
				&& level.getDifficulty() == Difficulty.PEACEFUL) return null;
		DynamicCarrierEntity entity = DynamicEntityObjects.carrier(
				definition.entity().movement(), definition.entity().disposition()).create(level, reason);
		if (entity == null) return null;
		entity.setDefinition(definition);
		entity.snapTo(position.x(), position.y(), position.z(), yRot, 0.0F);
		if (!level.getWorldBorder().isWithinBounds(entity.getBoundingBox()) || !level.noCollision(entity)) return null;
		entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), reason, null);
		if (!level.addFreshEntity(entity)) return null;
		entity.initializeBehavior();
		level.gameEvent(GameEvent.ENTITY_PLACE, position, GameEvent.Context.of(source == null ? entity : source));
		return entity;
	}
}
