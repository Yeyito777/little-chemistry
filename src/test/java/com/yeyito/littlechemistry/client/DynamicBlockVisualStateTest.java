package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockModelFace;
import com.yeyito.littlechemistry.content.DynamicBlockTexture;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DynamicBlockVisualStateTest {
	@Test
	void substitutesAuthoredVisualVariantAndFallsBackToBase() {
		DynamicBlockTexture base = new DynamicBlockTexture("front", "0".repeat(64), texture("1"));
		DynamicBlockTexture active = new DynamicBlockTexture("front_active", "1".repeat(64), texture("2"));
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) faces.put(direction, new DynamicBlockModelFace("front", null));
		DynamicBlockModel model = new DynamicBlockModel(List.of(base, active), "front", faces, List.of());

		assertEquals(active, DynamicBlockEntityRenderer.textureForState(model, "front", "active"));
		assertEquals(base, DynamicBlockEntityRenderer.textureForState(model, "front", "open"));
		assertEquals(java.util.Set.of("front"), model.referencedTextureIds());
	}

	private static DynamicTextureSpec texture(String key) {
		return new DynamicTextureSpec(List.of("101010FF", "808080FF", "F0F0F0FF"),
				java.util.Collections.nCopies(16, key.repeat(16)));
	}
}
