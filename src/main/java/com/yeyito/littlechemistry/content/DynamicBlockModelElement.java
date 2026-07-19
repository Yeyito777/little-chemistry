package com.yeyito.littlechemistry.content;

import net.minecraft.core.Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** One axis-aligned cuboid in an AI-authored custom block model. Coordinates use Minecraft's 0-16 model space. */
public record DynamicBlockModelElement(
		float fromX, float fromY, float fromZ,
		float toX, float toY, float toZ,
		boolean collision,
		Map<Direction, DynamicBlockModelFace> faces
) {
	public DynamicBlockModelElement {
		if (!coordinate(fromX) || !coordinate(fromY) || !coordinate(fromZ)
				|| !coordinate(toX) || !coordinate(toY) || !coordinate(toZ)
				|| fromX >= toX || fromY >= toY || fromZ >= toZ) {
			throw new IllegalArgumentException("Custom block element bounds must be ordered coordinates between 0 and 16");
		}
		if (faces == null || faces.isEmpty()) {
			throw new IllegalArgumentException("Custom block elements require at least one rendered face");
		}
		EnumMap<Direction, DynamicBlockModelFace> copied = new EnumMap<>(Direction.class);
		copied.putAll(faces);
		if (copied.containsValue(null)) throw new IllegalArgumentException("Custom block element faces cannot be null");
		faces = Collections.unmodifiableMap(copied);
	}

	private static boolean coordinate(float value) {
		return Float.isFinite(value) && value >= 0 && value <= 16;
	}
}
