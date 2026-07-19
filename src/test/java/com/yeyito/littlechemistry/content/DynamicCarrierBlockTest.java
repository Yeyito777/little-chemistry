package com.yeyito.littlechemistry.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicCarrierBlockTest {
	private static final String TEXTURE_HASH = "0".repeat(64);

	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void modelDirectionsRotateFromTheirNorthAuthoredBasis() {
		assertEquals(Direction.NORTH, DynamicCarrierBlock.orientFromNorth(Direction.NORTH, Direction.NORTH));
		assertEquals(Direction.EAST, DynamicCarrierBlock.orientFromNorth(Direction.NORTH, Direction.EAST));
		assertEquals(Direction.SOUTH, DynamicCarrierBlock.orientFromNorth(Direction.NORTH, Direction.SOUTH));
		assertEquals(Direction.WEST, DynamicCarrierBlock.orientFromNorth(Direction.NORTH, Direction.WEST));
		assertEquals(Direction.NORTH, DynamicCarrierBlock.orientFromNorth(Direction.WEST, Direction.EAST));
		assertEquals(Direction.UP, DynamicCarrierBlock.orientFromNorth(Direction.UP, Direction.WEST));
	}

	@Test
	void asymmetricCustomCollisionMatchesEveryFacing() {
		DynamicBlockModel model = asymmetricModel();

		assertBounds(shape(model, Direction.NORTH), 0.0, 0.25, 0.0, 0.5);
		assertBounds(shape(model, Direction.EAST), 0.5, 1.0, 0.0, 0.25);
		assertBounds(shape(model, Direction.SOUTH), 0.75, 1.0, 0.5, 1.0);
		assertBounds(shape(model, Direction.WEST), 0.0, 0.5, 0.75, 1.0);
	}

	private static AABB shape(DynamicBlockModel model, Direction facing) {
		return DynamicCarrierBlock.customShape(model, true, facing).bounds();
	}

	private static void assertBounds(AABB bounds, double minX, double maxX, double minZ, double maxZ) {
		assertEquals(minX, bounds.minX, 0.000001);
		assertEquals(maxX, bounds.maxX, 0.000001);
		assertEquals(minZ, bounds.minZ, 0.000001);
		assertEquals(maxZ, bounds.maxZ, 0.000001);
	}

	private static DynamicBlockModel asymmetricModel() {
		DynamicTextureSpec texture = new DynamicTextureSpec(List.of("FFFFFFFF"), List.of("0"));
		DynamicBlockTexture asset = new DynamicBlockTexture("all", TEXTURE_HASH, texture);
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) {
			faces.put(direction, new DynamicBlockModelFace("all", null));
		}
		return new DynamicBlockModel(
				List.of(asset), "all", faces,
				List.of(new DynamicBlockModelElement(0, 0, 0, 4, 16, 8, true, faces)));
	}
}
