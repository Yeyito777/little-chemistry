package com.yeyito.littlechemistry.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DynamicWorkstationScreenTest {
	@Test
	void screenUsesTheNativeSlotSpriteRatherThanRepaintingAnApproximation() {
		assertEquals(Identifier.withDefaultNamespace("container/slot"), DynamicWorkstationScreen.SLOT_SPRITE);
	}

	@Test
	void nativeBackAndFrontHighlightLayersProduceTheVanillaHighlightedBackground() throws Exception {
		int color = 0xFF8B8B8B;
		for (String path : new String[]{
				"/assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
				"/assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png"}) {
			try (var input = DynamicWorkstationScreenTest.class.getResourceAsStream(path)) {
				assertNotNull(input);
				var layer = ImageIO.read(input);
				color = sourceOver(color, layer.getRGB(layer.getWidth() / 2, layer.getHeight() / 2));
			}
		}
		assertEquals(0xFFC0C0C0, color);
	}

	private static int sourceOver(int background, int foreground) {
		int alpha = foreground >>> 24;
		int inverse = 255 - alpha;
		int red = (((foreground >>> 16) & 0xFF) * alpha + ((background >>> 16) & 0xFF) * inverse + 127) / 255;
		int green = (((foreground >>> 8) & 0xFF) * alpha + ((background >>> 8) & 0xFF) * inverse + 127) / 255;
		int blue = ((foreground & 0xFF) * alpha + (background & 0xFF) * inverse + 127) / 255;
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
