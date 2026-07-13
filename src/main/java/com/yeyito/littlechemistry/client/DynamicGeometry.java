package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;

final class DynamicGeometry {
	private static final float ITEM_BACK_Z = 7.5F / 16.0F;
	private static final float ITEM_FRONT_Z = 8.5F / 16.0F;

	private DynamicGeometry() {
	}

	static void item(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, boolean[] opaquePixels) {
		quad(pose, vertices, light, overlay,
				0, 0, ITEM_FRONT_Z, 1, 0, ITEM_FRONT_Z, 1, 1, ITEM_FRONT_Z, 0, 1, ITEM_FRONT_Z,
				0, 0, 1);
		backQuad(pose, vertices, light, overlay, ITEM_BACK_Z);
		if (opaquePixels == null || opaquePixels.length != 256) return;

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (!opaque(opaquePixels, x, y)) continue;
				float left = x / 16.0F;
				float right = (x + 1) / 16.0F;
				float top = 1.0F - y / 16.0F;
				float bottom = 1.0F - (y + 1) / 16.0F;
				float sampleU = (x + 0.5F) / 16.0F;
				float sampleV = (y + 0.5F) / 16.0F;

				if (!opaque(opaquePixels, x - 1, y)) {
					edgeQuad(pose, vertices, light, overlay, sampleU, sampleV, -1, 0, 0,
							left, bottom, ITEM_BACK_Z, left, bottom, ITEM_FRONT_Z,
							left, top, ITEM_FRONT_Z, left, top, ITEM_BACK_Z);
				}
				if (!opaque(opaquePixels, x + 1, y)) {
					edgeQuad(pose, vertices, light, overlay, sampleU, sampleV, 1, 0, 0,
							right, bottom, ITEM_FRONT_Z, right, bottom, ITEM_BACK_Z,
							right, top, ITEM_BACK_Z, right, top, ITEM_FRONT_Z);
				}
				if (!opaque(opaquePixels, x, y - 1)) {
					edgeQuad(pose, vertices, light, overlay, sampleU, sampleV, 0, 1, 0,
							left, top, ITEM_FRONT_Z, right, top, ITEM_FRONT_Z,
							right, top, ITEM_BACK_Z, left, top, ITEM_BACK_Z);
				}
				if (!opaque(opaquePixels, x, y + 1)) {
					edgeQuad(pose, vertices, light, overlay, sampleU, sampleV, 0, -1, 0,
							left, bottom, ITEM_BACK_Z, right, bottom, ITEM_BACK_Z,
							right, bottom, ITEM_FRONT_Z, left, bottom, ITEM_FRONT_Z);
				}
			}
		}
	}

	private static void backQuad(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, float z) {
		// Reverse the winding for the back face while preserving the front face's
		// position-to-UV mapping. The previous generic quad call inverted V, so the
		// no-cull item render displayed a second copy mirrored across the X axis.
		vertex(pose, vertices, 0, 1, z, 0, 0, light, overlay, 0, 0, -1);
		vertex(pose, vertices, 1, 1, z, 1, 0, light, overlay, 0, 0, -1);
		vertex(pose, vertices, 1, 0, z, 1, 1, light, overlay, 0, 0, -1);
		vertex(pose, vertices, 0, 0, z, 0, 1, light, overlay, 0, 0, -1);
	}

	private static boolean opaque(boolean[] pixels, int x, int y) {
		return x >= 0 && x < 16 && y >= 0 && y < 16 && pixels[y * 16 + x];
	}

	private static void edgeQuad(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay,
			float u, float v, float normalX, float normalY, float normalZ,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3) {
		vertex(pose, vertices, x0, y0, z0, u, v, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x1, y1, z1, u, v, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x2, y2, z2, u, v, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x3, y3, z3, u, v, light, overlay, normalX, normalY, normalZ);
	}

	static void cube(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay) {
		quad(pose, vertices, light, overlay, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1);
		quad(pose, vertices, light, overlay, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, -1);
		quad(pose, vertices, light, overlay, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0);
		quad(pose, vertices, light, overlay, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
		quad(pose, vertices, light, overlay, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 0);
		quad(pose, vertices, light, overlay, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
	}

	static void slab(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay) {
		box(pose, vertices, new int[] {light, light, light, light, light, light}, overlay, 0.5F);
	}

	static void cube(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay) {
		box(pose, vertices, faceLight, overlay, 1.0F);
	}

	static void slab(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay) {
		box(pose, vertices, faceLight, overlay, 0.5F);
	}

	static void cross(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay) {
		int light = maximumLight(faceLight);
		float inset = 0.08F;
		float bottom = 0.002F;
		// entityCutout is already a no-cull pipeline, so each crossed plane must be
		// submitted only once. Duplicating reversed coplanar quads causes z-fighting.
		quad(pose, vertices, light, overlay,
				inset, bottom, inset, 1 - inset, bottom, 1 - inset,
				1 - inset, 1, 1 - inset, inset, 1, inset,
				0.7071F, 0, -0.7071F);
		quad(pose, vertices, light, overlay,
				inset, bottom, 1 - inset, 1 - inset, bottom, inset,
				1 - inset, 1, inset, inset, 1, 1 - inset,
				0.7071F, 0, 0.7071F);
	}

	static void torch(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay) {
		float min = 7.0F / 16.0F;
		float max = 9.0F / 16.0F;
		float height = 10.0F / 16.0F;
		float u0 = 7.0F / 16.0F;
		float u1 = 9.0F / 16.0F;
		float v0 = 6.0F / 16.0F;
		float v1 = 1.0F;

		texturedQuad(pose, vertices, faceLight[Direction.SOUTH.ordinal()], overlay,
				min, 0, max, max, 0, max, max, height, max, min, height, max,
				u0, v1, u1, v1, u1, v0, u0, v0, 0, 0, 1);
		texturedQuad(pose, vertices, faceLight[Direction.NORTH.ordinal()], overlay,
				max, 0, min, min, 0, min, min, height, min, max, height, min,
				u0, v1, u1, v1, u1, v0, u0, v0, 0, 0, -1);
		texturedQuad(pose, vertices, faceLight[Direction.EAST.ordinal()], overlay,
				max, 0, max, max, 0, min, max, height, min, max, height, max,
				u0, v1, u1, v1, u1, v0, u0, v0, 1, 0, 0);
		texturedQuad(pose, vertices, faceLight[Direction.WEST.ordinal()], overlay,
				min, 0, min, min, 0, max, min, height, max, min, height, min,
				u0, v1, u1, v1, u1, v0, u0, v0, -1, 0, 0);
		texturedQuad(pose, vertices, faceLight[Direction.UP.ordinal()], overlay,
				min, height, max, max, height, max, max, height, min, min, height, min,
				u0, v0, u1, v0, u1, v0 + 2.0F / 16.0F, u0, v0 + 2.0F / 16.0F, 0, 1, 0);
	}

	private static void box(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay, float height) {
		quad(pose, vertices, faceLight[Direction.SOUTH.ordinal()], overlay, 0, 0, 1, 1, 0, 1, 1, height, 1, 0, height, 1, 0, 0, 1);
		quad(pose, vertices, faceLight[Direction.NORTH.ordinal()], overlay, 1, 0, 0, 0, 0, 0, 0, height, 0, 1, height, 0, 0, 0, -1);
		quad(pose, vertices, faceLight[Direction.EAST.ordinal()], overlay, 1, 0, 1, 1, 0, 0, 1, height, 0, 1, height, 1, 1, 0, 0);
		quad(pose, vertices, faceLight[Direction.WEST.ordinal()], overlay, 0, 0, 0, 0, 0, 1, 0, height, 1, 0, height, 0, -1, 0, 0);
		quad(pose, vertices, faceLight[Direction.UP.ordinal()], overlay, 0, height, 1, 1, height, 1, 1, height, 0, 0, height, 0, 0, 1, 0);
		quad(pose, vertices, faceLight[Direction.DOWN.ordinal()], overlay, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
	}

	private static int maximumLight(int[] faceLight) {
		int light = 0;
		for (int value : faceLight) light = LightCoordsUtil.max(light, value);
		return light;
	}

	private static void texturedQuad(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
			float normalX, float normalY, float normalZ) {
		vertex(pose, vertices, x0, y0, z0, u0, v0, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x1, y1, z1, u1, v1, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x2, y2, z2, u2, v2, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x3, y3, z3, u3, v3, light, overlay, normalX, normalY, normalZ);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float normalX, float normalY, float normalZ) {
		vertex(pose, vertices, x0, y0, z0, 0, 1, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x1, y1, z1, 1, 1, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x2, y2, z2, 1, 0, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x3, y3, z3, 0, 0, light, overlay, normalX, normalY, normalZ);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
			float u, float v, int light, int overlay, float normalX, float normalY, float normalZ) {
		vertices.addVertex(pose, x, y, z)
				.setColor(255, 255, 255, 255)
				.setUv(u, v)
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}
}
