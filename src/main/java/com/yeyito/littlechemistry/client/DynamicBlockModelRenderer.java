package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockModelElement;
import com.yeyito.littlechemistry.content.DynamicBlockModelFace;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBlockUv;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;

import java.util.Map;

/** Draws preset and AI-authored cuboid block models while filtering faces by named texture. */
final class DynamicBlockModelRenderer {
	private DynamicBlockModelRenderer() {
	}

	static void render(DynamicBlockShape shape, DynamicBlockModel model, String textureFilter,
			PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int overlay,
			boolean north, boolean east, boolean south, boolean west) {
		render(shape, model, textureFilter, pose, vertices, faceLight, 0, overlay, north, east, south, west);
	}

	static void render(DynamicBlockShape shape, DynamicBlockModel model, String textureFilter,
			PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay,
			boolean north, boolean east, boolean south, boolean west) {
		render(shape, model, textureFilter, pose, vertices, null, light, overlay, north, east, south, west);
	}

	private static void render(DynamicBlockShape shape, DynamicBlockModel model, String textureFilter,
			PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int uniformLight, int overlay,
			boolean north, boolean east, boolean south, boolean west) {
		switch (shape) {
			case SLAB -> cuboid(pose, vertices, faceLight, uniformLight, overlay,
					0, 0, 0, 16, 8, 16, model.faces(), textureFilter);
			case FENCE -> fence(model, textureFilter, pose, vertices, faceLight, uniformLight, overlay,
					north, east, south, west);
			case CROSS -> cross(model, textureFilter, pose, vertices,
					faceLight == null ? uniformLight : maximumLight(faceLight), overlay);
			case STAR -> star(model, textureFilter, pose, vertices,
					faceLight == null ? uniformLight : maximumLight(faceLight), overlay);
			case TORCH -> cuboid(pose, vertices, faceLight, uniformLight, overlay,
					7, 0, 7, 9, 10, 9, model.faces(), textureFilter);
			case CUSTOM -> {
				for (DynamicBlockModelElement element : model.elements()) {
					cuboid(pose, vertices, faceLight, uniformLight, overlay,
							element.fromX(), element.fromY(), element.fromZ(),
							element.toX(), element.toY(), element.toZ(), element.faces(), textureFilter);
				}
			}
			default -> cuboid(pose, vertices, faceLight, uniformLight, overlay,
					0, 0, 0, 16, 16, 16, model.faces(), textureFilter);
		}
	}

	private static void fence(DynamicBlockModel model, String textureFilter, PoseStack.Pose pose,
			VertexConsumer vertices, int[] faceLight, int uniformLight, int overlay,
			boolean north, boolean east, boolean south, boolean west) {
		cuboid(pose, vertices, faceLight, uniformLight, overlay,
				6, 0, 6, 10, 16, 10, model.faces(), textureFilter);
		if (north) fenceRail(model, textureFilter, pose, vertices, faceLight, uniformLight, overlay, 7, 0, 9, 8);
		if (south) fenceRail(model, textureFilter, pose, vertices, faceLight, uniformLight, overlay, 7, 8, 9, 16);
		if (west) fenceRail(model, textureFilter, pose, vertices, faceLight, uniformLight, overlay, 0, 7, 8, 9);
		if (east) fenceRail(model, textureFilter, pose, vertices, faceLight, uniformLight, overlay, 8, 7, 16, 9);
	}

	private static void fenceRail(DynamicBlockModel model, String textureFilter, PoseStack.Pose pose,
			VertexConsumer vertices, int[] faceLight, int uniformLight, int overlay,
			float minX, float minZ, float maxX, float maxZ) {
		cuboid(pose, vertices, faceLight, uniformLight, overlay,
				minX, 6, minZ, maxX, 9, maxZ, model.faces(), textureFilter);
		cuboid(pose, vertices, faceLight, uniformLight, overlay,
				minX, 12, minZ, maxX, 15, maxZ, model.faces(), textureFilter);
	}

	private static void cross(DynamicBlockModel model, String textureFilter, PoseStack.Pose pose,
			VertexConsumer vertices, int light, int overlay) {
		float inset = 1.28F;
		float opposite = 16 - inset;
		plane(model.faces().get(Direction.SOUTH), textureFilter, pose, vertices, light, overlay,
				inset, 0.032F, inset, opposite, 0.032F, opposite,
				opposite, 16, opposite, inset, 16, inset, 0.7071F, 0, -0.7071F);
		plane(model.faces().get(Direction.EAST), textureFilter, pose, vertices, light, overlay,
				inset, 0.032F, opposite, opposite, 0.032F, inset,
				opposite, 16, inset, inset, 16, opposite, 0.7071F, 0, 0.7071F);
	}

	private static void star(DynamicBlockModel model, String textureFilter, PoseStack.Pose pose,
			VertexConsumer vertices, int light, int overlay) {
		float inset = 0.64F;
		float opposite = 16 - inset;
		float middle = 8;
		plane(model.faces().get(Direction.SOUTH), textureFilter, pose, vertices, light, overlay,
				inset, 0.032F, middle, opposite, 0.032F, middle,
				opposite, 16, middle, inset, 16, middle, 0, 0, 1);
		plane(model.faces().get(Direction.EAST), textureFilter, pose, vertices, light, overlay,
				middle, 0.032F, opposite, middle, 0.032F, inset,
				middle, 16, inset, middle, 16, opposite, 1, 0, 0);
		plane(model.faces().get(Direction.NORTH), textureFilter, pose, vertices, light, overlay,
				inset, 0.032F, inset, opposite, 0.032F, opposite,
				opposite, 16, opposite, inset, 16, inset, 0.7071F, 0, -0.7071F);
		plane(model.faces().get(Direction.WEST), textureFilter, pose, vertices, light, overlay,
				inset, 0.032F, opposite, opposite, 0.032F, inset,
				opposite, 16, inset, inset, 16, opposite, 0.7071F, 0, 0.7071F);
	}

	private static void plane(DynamicBlockModelFace face, String textureFilter, PoseStack.Pose pose,
			VertexConsumer vertices, int light, int overlay,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float normalX, float normalY, float normalZ) {
		if (!matches(face, textureFilter)) return;
		DynamicBlockUv uv = face.uv() == null ? new DynamicBlockUv(0, 0, 16, 16) : face.uv();
		quad(pose, vertices, light, overlay,
				x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16,
				x2 / 16, y2 / 16, z2 / 16, x3 / 16, y3 / 16, z3 / 16,
				uv, normalX, normalY, normalZ);
	}

	private static void cuboid(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int uniformLight,
			int overlay, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			Map<Direction, DynamicBlockModelFace> faces, String textureFilter) {
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.SOUTH, faces.get(Direction.SOUTH), textureFilter,
				minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
				minX, 16 - maxY, maxX, 16 - minY);
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.NORTH, faces.get(Direction.NORTH), textureFilter,
				maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
				16 - maxX, 16 - maxY, 16 - minX, 16 - minY);
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.EAST, faces.get(Direction.EAST), textureFilter,
				maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
				16 - maxZ, 16 - maxY, 16 - minZ, 16 - minY);
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.WEST, faces.get(Direction.WEST), textureFilter,
				minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
				minZ, 16 - maxY, maxZ, 16 - minY);
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.UP, faces.get(Direction.UP), textureFilter,
				minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
				minX, minZ, maxX, maxZ);
		face(pose, vertices, faceLight, uniformLight, overlay, Direction.DOWN, faces.get(Direction.DOWN), textureFilter,
				minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
				minX, 16 - maxZ, maxX, 16 - minZ);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer vertices, int[] faceLight, int uniformLight,
			int overlay, Direction direction, DynamicBlockModelFace face, String textureFilter,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float autoU0, float autoV0, float autoU1, float autoV1) {
		if (!matches(face, textureFilter)) return;
		DynamicBlockUv uv = face.uv() == null
				? new DynamicBlockUv(autoU0, autoV0, autoU1, autoV1) : face.uv();
		quad(pose, vertices, faceLight == null ? uniformLight : faceLight[direction.ordinal()], overlay,
				x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16,
				x2 / 16, y2 / 16, z2 / 16, x3 / 16, y3 / 16, z3 / 16,
				uv, direction.getStepX(), direction.getStepY(), direction.getStepZ());
	}

	private static boolean matches(DynamicBlockModelFace face, String textureFilter) {
		return face != null && (textureFilter == null || textureFilter.equals(face.texture()));
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			DynamicBlockUv uv, float normalX, float normalY, float normalZ) {
		vertex(pose, vertices, x0, y0, z0, uv.u0() / 16, uv.v1() / 16, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x1, y1, z1, uv.u1() / 16, uv.v1() / 16, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x2, y2, z2, uv.u1() / 16, uv.v0() / 16, light, overlay, normalX, normalY, normalZ);
		vertex(pose, vertices, x3, y3, z3, uv.u0() / 16, uv.v0() / 16, light, overlay, normalX, normalY, normalZ);
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

	private static int maximumLight(int[] faceLight) {
		int light = 0;
		for (int value : faceLight) light = LightCoordsUtil.max(light, value);
		return light;
	}
}
