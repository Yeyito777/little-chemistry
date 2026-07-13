package com.yeyito.littlechemistry.content;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * An indexed Minecraft humanoid equipment texture. Unlike an armor item's 16x16 inventory icon,
 * this 64x32 UV sheet is wrapped around the equipped armor model.
 */
public record DynamicArmorDisplayTextureSpec(List<String> palette, List<String> rows) {
	public static final int WIDTH = 64;
	public static final int HEIGHT = 32;
	private static final String KEYS = "0123456789ABCDEF";

	public DynamicArmorDisplayTextureSpec {
		palette = List.copyOf(palette);
		rows = List.copyOf(rows);
		if (palette.isEmpty() || palette.size() > 16) {
			throw new IllegalArgumentException("Armor display texture palette must contain 1-16 colors");
		}
		for (String color : palette) {
			if (color == null || !color.matches("[0-9A-Fa-f]{8}")) {
				throw new IllegalArgumentException("Armor display texture colors must use RRGGBBAA hexadecimal notation");
			}
			String alpha = color.substring(6, 8);
			if (!alpha.equalsIgnoreCase("00") && !alpha.equalsIgnoreCase("FF")) {
				throw new IllegalArgumentException("Armor display textures may use only fully transparent or fully opaque pixels");
			}
		}
		if (rows.size() != HEIGHT) {
			throw new IllegalArgumentException("Armor display texture must contain exactly 32 rows");
		}
		for (String row : rows) {
			if (row == null || row.length() != WIDTH) {
				throw new IllegalArgumentException("Every armor display texture row must contain exactly 64 palette keys");
			}
			for (int index = 0; index < row.length(); index++) {
				int paletteIndex = KEYS.indexOf(Character.toUpperCase(row.charAt(index)));
				if (paletteIndex < 0 || paletteIndex >= palette.size()) {
					throw new IllegalArgumentException("Armor display texture row refers to an undefined palette key");
				}
			}
		}
	}

	public byte[] renderPng() throws IOException {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < rows.size(); y++) {
			String row = rows.get(y);
			for (int x = 0; x < row.length(); x++) {
				int paletteIndex = KEYS.indexOf(Character.toUpperCase(row.charAt(x)));
				String rgba = palette.get(paletteIndex);
				int red = Integer.parseInt(rgba.substring(0, 2), 16);
				int green = Integer.parseInt(rgba.substring(2, 4), 16);
				int blue = Integer.parseInt(rgba.substring(4, 6), 16);
				int alpha = Integer.parseInt(rgba.substring(6, 8), 16);
				image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "PNG", output)) {
			throw new IOException("The Java runtime has no PNG writer");
		}
		return output.toByteArray();
	}
}
