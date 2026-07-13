package com.yeyito.littlechemistry.content;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public record DynamicTextureSpec(List<String> palette, List<String> rows) {
	private static final String KEYS = "0123456789ABCDEF";

	public DynamicTextureSpec {
		palette = List.copyOf(palette);
		rows = List.copyOf(rows);
		if (palette.isEmpty() || palette.size() > 16) {
			throw new IllegalArgumentException("Texture palette must contain 1-16 colors");
		}
		for (String color : palette) {
			if (color == null || !color.matches("[0-9A-Fa-f]{8}")) {
				throw new IllegalArgumentException("Texture colors must use RRGGBBAA hexadecimal notation");
			}
		}
		if (rows.size() != DynamicTextureAsset.HEIGHT) {
			throw new IllegalArgumentException("Texture must contain exactly 16 rows");
		}
		for (String row : rows) {
			if (row == null || row.length() != DynamicTextureAsset.WIDTH) {
				throw new IllegalArgumentException("Every texture row must contain exactly 16 palette keys");
			}
			for (int index = 0; index < row.length(); index++) {
				int paletteIndex = KEYS.indexOf(Character.toUpperCase(row.charAt(index)));
				if (paletteIndex < 0 || paletteIndex >= palette.size()) {
					throw new IllegalArgumentException("Texture row refers to an undefined palette key");
				}
			}
		}
	}

	public void requireOpaque() {
		for (String color : palette) {
			if (!color.regionMatches(true, 6, "FF", 0, 2)) {
				throw new IllegalArgumentException("Block textures must be fully opaque");
			}
		}
	}

	public void requireBinaryAlpha() {
		for (String color : palette) {
			String alpha = color.substring(6, 8);
			if (!alpha.equalsIgnoreCase("00") && !alpha.equalsIgnoreCase("FF")) {
				throw new IllegalArgumentException("Item and armor textures may use only fully transparent or fully opaque pixels");
			}
		}
	}

	public byte[] renderPng() throws IOException {
		BufferedImage image = new BufferedImage(DynamicTextureAsset.WIDTH, DynamicTextureAsset.HEIGHT, BufferedImage.TYPE_INT_ARGB);
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
