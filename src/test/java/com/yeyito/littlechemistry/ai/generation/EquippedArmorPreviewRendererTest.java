package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EquippedArmorPreviewRendererTest {
	@Test
	void mapsBaseAndOuterHeadFrontOntoTheEquippedFigure() throws Exception {
		List<String> rows = new ArrayList<>();
		for (int y = 0; y < 32; y++) rows.add("0".repeat(64));
		for (int y = 8; y < 16; y++) {
			String row = rows.get(y);
			row = row.substring(0, 8) + "1".repeat(8) + row.substring(16);
			row = row.substring(0, 40) + "2".repeat(8) + row.substring(48);
			rows.set(y, row);
		}
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "FF0000FF", "0060FFFF"), rows);

		byte[] png = EquippedArmorPreviewRenderer.render(texture, DynamicArmorSlot.HEAD);
		var image = ImageIO.read(new ByteArrayInputStream(png));

		assertEquals(EquippedArmorPreviewRenderer.WIDTH, image.getWidth());
		assertEquals(EquippedArmorPreviewRenderer.HEIGHT, image.getHeight());
		// The opaque outer-head front is drawn over the red base-head front on the first mannequin.
		assertEquals(0xFF0060FF, image.getRGB(294, 57));
	}

	@Test
	void mirrorsTheSecondLimbAndKeepsTorsoSideAtDepthWidth() throws Exception {
		List<String> rows = new ArrayList<>();
		for (int y = 0; y < 32; y++) rows.add("0".repeat(64));
		for (int y = 20; y < 32; y++) {
			char[] row = rows.get(y).toCharArray();
			row[44] = '1';
			row[47] = '2';
			row[28] = '3';
			row[40] = '4';
			row[48] = '3';
			rows.set(y, new String(row));
		}
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "FF0000FF", "00FF00FF", "0060FFFF", "FFFF00FF"), rows);
		var image = ImageIO.read(new ByteArrayInputStream(
				EquippedArmorPreviewRenderer.render(texture, DynamicArmorSlot.CHEST)));

		assertEquals(0xFFFF0000, image.getRGB(281, 80));
		assertEquals(0xFF00FF00, image.getRGB(317, 80));
		assertEquals(0xFF0060FF, image.getRGB(411, 80));
		assertEquals(0xFF5C606C, image.getRGB(405, 80));
		assertEquals(0xFF0060FF, image.getRGB(393, 80));
		assertEquals(0xFFFFFF00, image.getRGB(439, 80));
		assertEquals(0xFFFFFF00, image.getRGB(449, 80));
		assertEquals(0xFF0060FF, image.getRGB(495, 80));
	}
}
