package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Headless contact-sheet renderer showing how a generated 64x32 sheet maps onto vanilla humanoid armor cuboids. */
final class EquippedArmorPreviewRenderer {
	static final int WIDTH = 512;
	static final int HEIGHT = 300;
	private static final int FIGURE_SCALE = 3;

	private EquippedArmorPreviewRenderer() {
	}

	static byte[] render(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot) throws IOException {
		BufferedImage source = sourceImage(texture);
		BufferedImage output = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = output.createGraphics();
		try {
			graphics.setColor(new Color(22, 24, 30));
			graphics.fillRect(0, 0, WIDTH, HEIGHT);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			graphics.setColor(Color.WHITE);
			graphics.drawString("Equipped armor preview — " + slot.serializedName(), 14, 21);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
			graphics.setColor(new Color(185, 190, 202));
			graphics.drawString("Raw 64×32 UV sheet", 14, 41);
			drawChecker(graphics, 14, 48, 256, 128, 8);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(source, 14, 48, 256, 128, null);

			drawFigure(graphics, source, slot, View.FRONT, 280, 55);
			drawFigure(graphics, source, slot, View.BACK, 336, 55);
			drawFigure(graphics, source, slot, View.LEFT_SIDE, 392, 55);
			drawFigure(graphics, source, slot, View.RIGHT_SIDE, 448, 55);
			graphics.setColor(new Color(185, 190, 202));
			graphics.drawString("front", 288, 171);
			graphics.drawString("back", 346, 171);
			graphics.drawString("left", 404, 171);
			graphics.drawString("right", 457, 171);

			graphics.setColor(new Color(38, 42, 52));
			graphics.fillRoundRect(14, 218, WIDTH - 28, 64, 8, 8);
			graphics.setColor(new Color(220, 223, 232));
			graphics.drawString("The figures use Minecraft's vanilla armor UV cuboids. Transparent or misplaced pixels expose", 25, 240);
			graphics.drawString("the gray mannequin. Review front, back, and side before final verify; texture-only armor cannot", 25, 257);
			graphics.drawString("create a true hat brim or a backpack with depth beyond the humanoid shell.", 25, 274);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		if (!ImageIO.write(output, "PNG", bytes)) throw new IOException("The Java runtime has no PNG writer");
		return bytes.toByteArray();
	}

	private static BufferedImage sourceImage(DynamicArmorDisplayTextureSpec texture) {
		BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) {
			String row = texture.rows().get(y);
			for (int x = 0; x < 64; x++) {
				String rgba = texture.palette().get(Character.digit(row.charAt(x), 16));
				int red = Integer.parseInt(rgba.substring(0, 2), 16);
				int green = Integer.parseInt(rgba.substring(2, 4), 16);
				int blue = Integer.parseInt(rgba.substring(4, 6), 16);
				int alpha = Integer.parseInt(rgba.substring(6, 8), 16);
				source.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		return source;
	}

	private static void drawChecker(Graphics2D graphics, int x, int y, int width, int height, int cell) {
		for (int row = 0; row < height; row += cell) {
			for (int column = 0; column < width; column += cell) {
				graphics.setColor(((row / cell + column / cell) & 1) == 0
						? new Color(65, 68, 78) : new Color(48, 51, 61));
				graphics.fillRect(x + column, y + row, cell, cell);
			}
		}
	}

	private static void drawFigure(Graphics2D graphics, BufferedImage texture, DynamicArmorSlot slot,
			View view, int x, int y) {
		graphics.setColor(new Color(92, 96, 108));
		graphics.fillRect(x + 4 * FIGURE_SCALE, y, 8 * FIGURE_SCALE, 8 * FIGURE_SCALE);
		graphics.fillRect(x + 4 * FIGURE_SCALE, y + 8 * FIGURE_SCALE, 8 * FIGURE_SCALE, 12 * FIGURE_SCALE);
		graphics.fillRect(x, y + 8 * FIGURE_SCALE, 4 * FIGURE_SCALE, 12 * FIGURE_SCALE);
		graphics.fillRect(x + 12 * FIGURE_SCALE, y + 8 * FIGURE_SCALE, 4 * FIGURE_SCALE, 12 * FIGURE_SCALE);
		graphics.fillRect(x + 4 * FIGURE_SCALE, y + 20 * FIGURE_SCALE, 4 * FIGURE_SCALE, 12 * FIGURE_SCALE);
		graphics.fillRect(x + 8 * FIGURE_SCALE, y + 20 * FIGURE_SCALE, 4 * FIGURE_SCALE, 12 * FIGURE_SCALE);

		if (slot == DynamicArmorSlot.HEAD) {
			drawCuboidFace(graphics, texture, 0, 0, 8, 8, 8, view, x + 4 * FIGURE_SCALE, y, false);
			drawCuboidFace(graphics, texture, 32, 0, 8, 8, 8, view, x + 4 * FIGURE_SCALE, y, false);
		}
		if (slot == DynamicArmorSlot.CHEST || slot == DynamicArmorSlot.LEGGINGS) {
			drawCuboidFace(graphics, texture, 16, 16, 8, 12, 4, view,
					x + 4 * FIGURE_SCALE, y + 8 * FIGURE_SCALE, false);
		}
		if (slot == DynamicArmorSlot.CHEST) {
			drawCuboidFace(graphics, texture, 40, 16, 4, 12, 4, view, x, y + 8 * FIGURE_SCALE, false);
			drawCuboidFace(graphics, texture, 40, 16, 4, 12, 4, view,
					x + 12 * FIGURE_SCALE, y + 8 * FIGURE_SCALE, true);
		}
		if (slot == DynamicArmorSlot.LEGGINGS || slot == DynamicArmorSlot.BOOTS) {
			drawCuboidFace(graphics, texture, 0, 16, 4, 12, 4, view,
					x + 4 * FIGURE_SCALE, y + 20 * FIGURE_SCALE, false);
			drawCuboidFace(graphics, texture, 0, 16, 4, 12, 4, view,
					x + 8 * FIGURE_SCALE, y + 20 * FIGURE_SCALE, true);
		}
	}

	private static void drawCuboidFace(Graphics2D graphics, BufferedImage texture,
			int u, int v, int width, int height, int depth, View view,
			int destinationX, int destinationY, boolean mirrored) {
		int sourceX;
		int sourceWidth;
		switch (view) {
			case FRONT -> { sourceX = u + depth; sourceWidth = width; }
			case BACK -> { sourceX = u + depth * 2 + width; sourceWidth = width; }
			case LEFT_SIDE -> { sourceX = mirrored ? u : u + depth + width; sourceWidth = depth; }
			case RIGHT_SIDE -> { sourceX = mirrored ? u + depth + width : u; sourceWidth = depth; }
			default -> throw new AssertionError(view);
		}
		int destinationWidth = sourceWidth * FIGURE_SCALE;
		int centeredX = destinationX + (width - sourceWidth) * FIGURE_SCALE / 2;
		int sourceStart = mirrored ? sourceX + sourceWidth : sourceX;
		int sourceEnd = mirrored ? sourceX : sourceX + sourceWidth;
		graphics.drawImage(texture, centeredX, destinationY,
				centeredX + destinationWidth, destinationY + height * FIGURE_SCALE,
				sourceStart, v + depth, sourceEnd, v + depth + height, null);
	}

	private enum View { FRONT, BACK, LEFT_SIDE, RIGHT_SIDE }
}
