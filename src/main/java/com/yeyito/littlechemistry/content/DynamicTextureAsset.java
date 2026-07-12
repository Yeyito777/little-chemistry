package com.yeyito.littlechemistry.content;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

public final class DynamicTextureAsset {
	public static final int WIDTH = 16;
	public static final int HEIGHT = 16;
	public static final int MAX_ENCODED_BYTES = 256 * 1024;

	private DynamicTextureAsset() {
	}

	public static byte[] generate(long seed) throws IOException {
		Random random = new Random(seed);
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				int red = random.nextInt(256);
				int green = random.nextInt(256);
				int blue = random.nextInt(256);
				image.setRGB(x, y, 0xFF000000 | red << 16 | green << 8 | blue);
			}
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "PNG", output)) {
			throw new IOException("The Java runtime has no PNG writer");
		}
		return output.toByteArray();
	}

	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}
}
