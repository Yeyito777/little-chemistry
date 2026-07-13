package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DynamicParticleTextures {
	private static final int MAX_CACHED_BLOCKS = 8192;
	private static final Map<Key, String> BLOCK_TEXTURES = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
			return size() > MAX_CACHED_BLOCKS;
		}
	};

	private DynamicParticleTextures() {
	}

	public static String textureFor(ClientLevel level, BlockPos position) {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity blockEntity) {
			DynamicContentDefinition definition = DynamicContentCatalog.find(blockEntity.contentId());
			if (definition != null) {
				remember(level, position, definition.textureHash());
				return definition.textureHash();
			}
		}
		return BLOCK_TEXTURES.get(new Key(level.dimension(), position.asLong()));
	}

	public static void remember(ClientLevel level, BlockPos position, String textureHash) {
		if (textureHash != null) BLOCK_TEXTURES.put(new Key(level.dimension(), position.asLong()), textureHash);
	}

	public static void clear() {
		BLOCK_TEXTURES.clear();
	}

	private record Key(ResourceKey<Level> dimension, long position) {
	}
}
