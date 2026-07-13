package com.yeyito.littlechemistry.content;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicArmorDisplayTextureSpecTest {
	@Test
	void rendersNativeHumanoidEquipmentDimensions() throws Exception {
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "A0C0E0FF"),
				java.util.stream.IntStream.range(0, 32)
						.mapToObj(y -> "1".repeat(32) + "0".repeat(32)).toList());

		var image = ImageIO.read(new ByteArrayInputStream(texture.renderPng()));

		assertEquals(64, image.getWidth());
		assertEquals(32, image.getHeight());
	}

	@Test
	void rejectsPartialAlpha() {
		assertThrows(IllegalArgumentException.class, () -> new DynamicArmorDisplayTextureSpec(
				List.of("FFFFFF80"), List.of("0".repeat(64))));
	}
}
