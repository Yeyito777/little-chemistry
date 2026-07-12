package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;

final class DynamicGeometry {
	private DynamicGeometry() {
	}

	static void flat(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay) {
		quad(pose, vertices, light, overlay, 0, 0, 0.5F, 1, 0, 0.5F, 1, 1, 0.5F, 0, 1, 0.5F, 0, 0, 1);
		quad(pose, vertices, light, overlay, 0, 1, 0.49F, 1, 1, 0.49F, 1, 0, 0.49F, 0, 0, 0.49F, 0, 0, -1);
	}

	static void cube(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay) {
		quad(pose, vertices, light, overlay, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1);
		quad(pose, vertices, light, overlay, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, -1);
		quad(pose, vertices, light, overlay, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0);
		quad(pose, vertices, light, overlay, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
		quad(pose, vertices, light, overlay, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 0);
		quad(pose, vertices, light, overlay, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
	}

	static void cube(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay) {
		quad(pose, vertices, faceLight[Direction.SOUTH.ordinal()], overlay, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1);
		quad(pose, vertices, faceLight[Direction.NORTH.ordinal()], overlay, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, -1);
		quad(pose, vertices, faceLight[Direction.EAST.ordinal()], overlay, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0);
		quad(pose, vertices, faceLight[Direction.WEST.ordinal()], overlay, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
		quad(pose, vertices, faceLight[Direction.UP.ordinal()], overlay, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 1, 0);
		quad(pose, vertices, faceLight[Direction.DOWN.ordinal()], overlay, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
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
