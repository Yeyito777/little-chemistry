package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DynamicBlockAssemblyTest {
	private static final String HASH = "a".repeat(64);

	@BeforeAll
	static void bootstrapMinecraft() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	@Test
	void rotatesNorthAuthoredFootprintsWithoutMirroringTheirVerticalAxis() {
		BlockPos local = new BlockPos(1, 2, 3);
		assertEquals(new BlockPos(1, 2, 3), DynamicBlockAssemblyRuntime.rotateOffset(local, Direction.NORTH));
		assertEquals(new BlockPos(-3, 2, 1), DynamicBlockAssemblyRuntime.rotateOffset(local, Direction.EAST));
		assertEquals(new BlockPos(-1, 2, -3), DynamicBlockAssemblyRuntime.rotateOffset(local, Direction.SOUTH));
		assertEquals(new BlockPos(3, 2, -1), DynamicBlockAssemblyRuntime.rotateOffset(local, Direction.WEST));
		BlockPos root = new BlockPos(20, 70, -4);
		for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
			BlockPos member = DynamicBlockAssemblyRuntime.worldPosition(root, local, facing);
			assertEquals(root, DynamicBlockAssemblyRuntime.rootPosition(member, local, facing));
		}
	}

	@Test
	void variantsShareOneConnectedFootprintAndMayChangeCollisionGeometry() {
		DynamicBlockModelElement solid = element(true, "wood");
		DynamicBlockModelElement visualOnly = element(false, "wood");
		DynamicBlockAssembly assembly = new DynamicBlockAssembly(List.of(
				new DynamicBlockAssemblyVariant("closed", List.of(
						new DynamicBlockAssemblyCell(0, 0, 0, List.of(solid)),
						new DynamicBlockAssemblyCell(1, 0, 0, List.of(solid)))),
				new DynamicBlockAssemblyVariant("open", List.of(
						new DynamicBlockAssemblyCell(0, 0, 0, List.of(visualOnly)),
						new DynamicBlockAssemblyCell(1, 0, 0, List.of())))));

		assertEquals(1, assembly.variantIndex("open"));
		assertTrue(assembly.variant(0).cell(BlockPos.ZERO).elements().stream()
				.anyMatch(DynamicBlockModelElement::collision));
		assertFalse(assembly.variant(1).cell(BlockPos.ZERO).elements().stream()
				.anyMatch(DynamicBlockModelElement::collision));
		assertThrows(IllegalArgumentException.class, () -> new DynamicBlockAssembly(List.of(
				new DynamicBlockAssemblyVariant("bad", List.of(
						new DynamicBlockAssemblyCell(0, 0, 0, List.of(solid)),
						new DynamicBlockAssemblyCell(2, 0, 0, List.of(solid)))))));
	}

	@Test
	void catalogRoundTripPreservesCellsVariantsAndFaceUvs() {
		DynamicBlockModelElement rootElement = element(true, "wood");
		DynamicBlockModelElement openElement = element(false, "wood");
		DynamicBlockAssembly assembly = new DynamicBlockAssembly(List.of(
				new DynamicBlockAssemblyVariant("closed", List.of(
						new DynamicBlockAssemblyCell(0, 0, 0, List.of(rootElement)),
						new DynamicBlockAssemblyCell(1, 0, 0, List.of(rootElement)))),
				new DynamicBlockAssemblyVariant("open", List.of(
						new DynamicBlockAssemblyCell(0, 0, 0, List.of(openElement)),
						new DynamicBlockAssemblyCell(1, 0, 0, List.of(openElement))))));
		DynamicTextureSpec texture = texture();
		DynamicBlockModel model = new DynamicBlockModel(
				List.of(new DynamicBlockTexture("wood", HASH, texture)), "wood", faces("wood"), List.of(rootElement));
		DynamicBlockProperties block = new DynamicBlockProperties(
				DynamicMaterial.WOOD, 2.0F, DynamicTool.AXE, false, DynamicBlockShape.CUSTOM,
				true, Rarity.COMMON, 0, 0, 0, false, List.of(), DynamicBlockDrops.DEFAULT, assembly);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.BLOCK, "wide_gate", "Wide Gate", 1L, HASH, texture,
				null, null, block, null, null, DynamicBehaviorSource.completeLegacySource(null), model);

		DynamicContentDefinition decoded = DynamicContentJson.decode(
				DynamicContentJson.encode(UUID.randomUUID(), 1, List.of(definition))).definitions().getFirst();

		assertEquals(27, DynamicContentJson.CURRENT_FORMAT);
		assertNotNull(decoded.block().assembly());
		assertEquals(List.of("closed", "open"), decoded.block().assembly().variants().stream()
				.map(DynamicBlockAssemblyVariant::id).toList());
		assertEquals(new BlockPos(1, 0, 0), decoded.block().assembly().initialVariant().cells().get(1).offset());
		assertFalse(decoded.block().assembly().variant(1).cell(BlockPos.ZERO).elements().getFirst().collision());
		assertEquals(new DynamicBlockUv(0, 0, 16, 16), decoded.block().assembly().initialVariant()
				.cell(BlockPos.ZERO).elements().getFirst().faces().get(Direction.UP).uv());
	}

	private static DynamicBlockModelElement element(boolean collision, String texture) {
		return new DynamicBlockModelElement(0, 0, 0, 16, 16, 16, collision, faces(texture));
	}

	private static EnumMap<Direction, DynamicBlockModelFace> faces(String texture) {
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) {
			faces.put(direction, new DynamicBlockModelFace(texture,
					direction == Direction.UP ? new DynamicBlockUv(0, 0, 16, 16) : null));
		}
		return faces;
	}

	private static DynamicTextureSpec texture() {
		return new DynamicTextureSpec(List.of("00000000", "805020FF"),
				java.util.Collections.nCopies(16, "1111111111111111"));
	}
}
