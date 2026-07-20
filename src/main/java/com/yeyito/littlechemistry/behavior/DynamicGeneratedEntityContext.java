package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicCarrierEntity;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;

/** Authoritative server context shared by generated-entity callbacks. */
public record DynamicGeneratedEntityContext(
		ServerLevel level,
		DynamicCarrierEntity entity,
		DynamicContentDefinition definition,
		DynamicEntityState state
) {
}
