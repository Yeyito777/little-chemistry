package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds a searchable, lazily materialized indexed-pixel mirror of every installed mod's artwork. */
final class MinecraftReferenceExporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private MinecraftReferenceExporter() {
	}

	static void writeIndex(Path vanillaRoot) throws IOException {
		List<String> entries = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				Path assets = root.resolve("assets");
				if (!Files.isDirectory(assets)) continue;
				try (var namespaces = Files.list(assets)) {
					for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
						String namespaceName = namespace.getFileName().toString();
						if (!validNamespace(namespaceName)) continue;
						Path textures = namespace.resolve("textures");
						if (!Files.isDirectory(textures)) continue;
						try (var paths = Files.walk(textures)) {
							paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".png"))
									.map(textures::relativize)
									.map(Path::toString)
									.map(path -> namespaceName + "/"
											+ path.substring(0, path.length() - 4).replace('\\', '/') + ".json")
									.forEach(entries::add);
						}
					}
				}
			}
		}
		entries = entries.stream().distinct().sorted().toList();
		Files.createDirectories(vanillaRoot);
		write(vanillaRoot.resolve("TEXTURES.txt"), String.join("\n", entries) + "\n");
		write(vanillaRoot.resolve("README.md"), """
				# Installed artwork mirror

				Search TEXTURES.txt, then read the matching `<namespace>/<texture-path>.json` virtual path under this
				directory. The index includes Minecraft and every installed mod namespace. Item and block PNGs are
				normalized to an indexed 16x16 first frame. Entity/equipment artwork keeps its installed dimensions when they
				fit Little Chemistry's 1-64 pixel representation. The materialized JSON contains an RRGGBBAA palette and rows
				of hexadecimal palette indices, ready to adapt in Java texture helpers. Preserve UV island positions when
				reusing an animated entity profile or humanoid equipment sheet.
				""");
	}

	static String materialize(String virtualPath) throws IOException {
		TextureReference reference = parse(virtualPath);
		BufferedImage source = read(reference);
		boolean sampled = reference.path().startsWith("item/") || reference.path().startsWith("block/");
		BufferedImage image = sampled ? sample16(source) : source;
		if (image.getWidth() < 1 || image.getWidth() > 64 || image.getHeight() < 1 || image.getHeight() > 64) {
			throw new IOException("Installed texture is outside the supported 1-64 pixel dimensions: "
					+ image.getWidth() + "x" + image.getHeight());
		}
		DynamicTextureSpec indexed = encode(image);
		JsonObject output = new JsonObject();
		output.addProperty("source", reference.namespace() + ":" + reference.path());
		output.addProperty("sourceWidth", source.getWidth());
		output.addProperty("sourceHeight", source.getHeight());
		output.addProperty("width", indexed.width());
		output.addProperty("height", indexed.height());
		output.addProperty("sampledRegion", sampled ? "first square frame normalized to 16x16" : "complete texture");
		JsonArray palette = new JsonArray();
		indexed.palette().forEach(palette::add);
		output.add("palette", palette);
		JsonArray rows = new JsonArray();
		indexed.rows().forEach(rows::add);
		output.add("rows", rows);
		return GSON.toJson(output);
	}

	/** Returns the same first-frame image represented by {@link #materialize(String)}, for multimodal inspection. */
	static byte[] previewPng(String virtualPath) throws IOException {
		TextureReference reference = parse(virtualPath);
		BufferedImage source = read(reference);
		BufferedImage preview = reference.path().startsWith("item/") || reference.path().startsWith("block/")
				? sample16(source) : source;
		if (preview.getWidth() < 1 || preview.getWidth() > 256 || preview.getHeight() < 1 || preview.getHeight() > 256) {
			throw new IOException("Installed texture is outside the previewable 1-256 pixel dimensions: "
					+ preview.getWidth() + "x" + preview.getHeight());
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(preview, "PNG", output)) throw new IOException("The Java runtime has no PNG writer");
		return output.toByteArray();
	}

	private static BufferedImage read(TextureReference reference) throws IOException {
		String relative = "assets/" + reference.namespace() + "/textures/" + reference.path() + ".png";
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				Path texture = root.resolve(relative);
				if (!Files.isRegularFile(texture)) continue;
				try (InputStream input = Files.newInputStream(texture)) {
					BufferedImage image = ImageIO.read(input);
					if (image != null) return image;
				}
			}
		}
		throw new IOException("Installed texture does not exist: " + reference.namespace() + ":" + reference.path());
	}

	private static TextureReference parse(String virtualPath) throws IOException {
		String normalized = virtualPath.replace('\\', '/');
		if (normalized.startsWith("/") || normalized.contains("../") || !normalized.endsWith(".json")) {
			throw new IOException("Invalid installed texture reference path");
		}
		String withoutExtension = normalized.substring(0, normalized.length() - 5);
		int separator = withoutExtension.indexOf('/');
		String namespace;
		String path;
		if (separator > 0 && isInstalledNamespace(withoutExtension.substring(0, separator))) {
			namespace = withoutExtension.substring(0, separator);
			path = withoutExtension.substring(separator + 1);
		} else {
			// Compatibility for world workspaces created before installed mod namespaces were indexed.
			namespace = "minecraft";
			path = withoutExtension;
		}
		if (!validNamespace(namespace) || !path.matches("[a-z0-9_./-]+") || path.startsWith("/")
				|| path.endsWith("/") || path.contains("//")) {
			throw new IOException("Invalid installed texture reference path");
		}
		return new TextureReference(namespace, path);
	}

	private static boolean validNamespace(String value) {
		return value != null && value.matches("[a-z0-9_.-]+");
	}

	private static boolean isInstalledNamespace(String candidate) {
		if (!validNamespace(candidate)) return false;
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				if (Files.isDirectory(root.resolve("assets").resolve(candidate))) return true;
			}
		}
		return false;
	}

	private static BufferedImage sample16(BufferedImage source) {
		int frame = Math.min(source.getWidth(), source.getHeight());
		BufferedImage sampled = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int sourceX = Math.min(source.getWidth() - 1, x * frame / 16);
				int sourceY = Math.min(source.getHeight() - 1, y * frame / 16);
				sampled.setRGB(x, y, source.getRGB(sourceX, sourceY));
			}
		}
		return sampled;
	}

	private static DynamicTextureSpec encode(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = new int[width * height];
		Map<Integer, Integer> counts = new HashMap<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = normalizeAlpha(image.getRGB(x, y));
				pixels[y * width + x] = argb;
				counts.merge(argb, 1, Integer::sum);
			}
		}
		List<Integer> palette = counts.entrySet().stream()
				.sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
						.thenComparing(entry -> Integer.toUnsignedLong(entry.getKey())))
				.map(Map.Entry::getKey).limit(16).toList();
		List<String> encodedPalette = palette.stream().map(MinecraftReferenceExporter::rgba).toList();
		String keys = "0123456789ABCDEF";
		List<String> rows = new ArrayList<>(height);
		for (int y = 0; y < height; y++) {
			StringBuilder row = new StringBuilder(width);
			for (int x = 0; x < width; x++) {
				row.append(keys.charAt(nearestPaletteIndex(pixels[y * width + x], palette)));
			}
			rows.add(row.toString());
		}
		return new DynamicTextureSpec(encodedPalette, rows);
	}

	private static int normalizeAlpha(int argb) {
		return ((argb >>> 24) & 0xFF) < 16 ? 0 : argb;
	}

	private static int nearestPaletteIndex(int argb, List<Integer> palette) {
		int exact = palette.indexOf(argb);
		if (exact >= 0) return exact;
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int index = 0; index < palette.size(); index++) {
			int candidate = palette.get(index);
			int alpha = (argb >>> 24) - (candidate >>> 24);
			int red = (argb >> 16 & 255) - (candidate >> 16 & 255);
			int green = (argb >> 8 & 255) - (candidate >> 8 & 255);
			int blue = (argb & 255) - (candidate & 255);
			long distance = 2L * alpha * alpha + (long) red * red + (long) green * green + (long) blue * blue;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = index;
			}
		}
		return best;
	}

	private static String rgba(int argb) {
		return String.format(Locale.ROOT, "%02X%02X%02X%02X", (argb >>> 16) & 255,
				(argb >>> 8) & 255, argb & 255, (argb >>> 24) & 255);
	}

	private static void write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporary, content, StandardCharsets.UTF_8);
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
	}

	private record TextureReference(String namespace, String path) {
	}
}
