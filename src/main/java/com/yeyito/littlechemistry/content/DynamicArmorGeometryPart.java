package com.yeyito.littlechemistry.content;

/**
 * One UV-wrapped cuboid attached to an animated humanoid body part. Positions and sizes use Minecraft model pixels;
 * rotations are degrees, and textureU/textureV identify the upper-left corner of the conventional cuboid UV net.
 */
public record DynamicArmorGeometryPart(
		String id,
		DynamicArmorAnchor anchor,
		int textureU,
		int textureV,
		float x,
		float y,
		float z,
		float width,
		float height,
		float depth,
		float pivotX,
		float pivotY,
		float pivotZ,
		float pitch,
		float yaw,
		float roll,
		float dilation,
		boolean mirror
) {
	public DynamicArmorGeometryPart {
		if (id == null || !id.matches("[a-z0-9_]{1,32}")) {
			throw new IllegalArgumentException("Armor geometry part ID must use 1-32 lowercase letters, numbers, or underscores");
		}
		if (anchor == null) throw new IllegalArgumentException("Armor geometry part requires a humanoid anchor");
		if (textureU < 0 || textureV < 0) {
			throw new IllegalArgumentException("Armor geometry texture offsets cannot be negative");
		}
		if (!finite(x, y, z, width, height, depth, pivotX, pivotY, pivotZ, pitch, yaw, roll, dilation)
				|| width <= 0 || height <= 0 || depth <= 0 || width > 32 || height > 32 || depth > 32
				|| Math.abs(x) > 32 || Math.abs(y) > 32 || Math.abs(z) > 32
				|| Math.abs(pivotX) > 32 || Math.abs(pivotY) > 32 || Math.abs(pivotZ) > 32
				|| Math.abs(pitch) > 180 || Math.abs(yaw) > 180 || Math.abs(roll) > 180
				|| dilation < -4 || dilation > 4
				|| width + 2 * dilation <= 0 || height + 2 * dilation <= 0 || depth + 2 * dilation <= 0) {
			throw new IllegalArgumentException("Armor geometry coordinates, dimensions, rotation, or dilation are out of bounds");
		}
		// Minecraft's cuboid unwrap occupies 2*(depth+width) by depth+height pixels from the declared offset.
		if (textureU + 2 * (depth + width) > DynamicArmorDisplayTextureSpec.WIDTH
				|| textureV + depth + height > DynamicArmorDisplayTextureSpec.HEIGHT) {
			throw new IllegalArgumentException("Armor geometry cuboid UV net exceeds the 64x32 equipped texture");
		}
	}

	private static boolean finite(float... values) {
		for (float value : values) if (!Float.isFinite(value)) return false;
		return true;
	}
}
