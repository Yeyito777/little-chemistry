package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlotIcon;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a searchable, lazily materialized indexed-pixel mirror of installed artwork.
 *
 * PNG decoding is strictly an internal import step. The generation model must receive textures only through
 * {@link #materialize(String)} as the same RRGGBBAA palette and hexadecimal rows that generated source authors; never add
 * a PNG preview or model-facing raster method here.
 */
final class MinecraftReferenceExporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile List<TextureReference> installedTextureCache;
	private MinecraftReferenceExporter() {
	}

	static void writeIndex(Path vanillaRoot) throws IOException {
		List<String> entries = installedTextures().stream()
				.map(reference -> "reference/vanilla/" + reference.virtualPath() + ".json").toList();
		Files.createDirectories(vanillaRoot);
		write(vanillaRoot.resolve("TEXTURES.txt"), String.join("\n", entries) + "\n");
		List<String> guiSprites = DynamicWorkstationSlotIcon.availableIds().stream()
				.map(Identifier::toString).sorted().toList();
		write(vanillaRoot.resolve("GUI_SPRITES.txt"), String.join("\n", guiSprites) + "\n");
		write(vanillaRoot.resolve("README.md"), """
				# Installed artwork mirror

				Search TEXTURES.txt, then use read_texture on the matching virtual JSON path under this directory. Minecraft
				paths retain their short `reference/vanilla/<texture>.json` spelling; other installed namespaces use
				`reference/vanilla/<namespace>/<texture>.json`. Installed
				source PNGs are decoded only inside the game and are never sent to the model. Item and block textures are
					normalized to an indexed 16x16 first frame. Entity/equipment artwork keeps its installed dimensions. A source
					larger than 64 pixels is split into coordinate-labelled 64x64-or-smaller tiles. Every complete texture or tile
					contains an RRGGBBAA palette and rows of hexadecimal palette indices: exactly the representation generated Java
					must author. Preserve UV island positions when reusing an entity profile or humanoid equipment sheet.
				""");
	}

	static String materialize(String virtualPath) throws IOException {
		TextureReference reference = parse(virtualPath);
		BufferedImage source = read(reference);
		boolean sampled = reference.path().startsWith("item/") || reference.path().startsWith("block/");
		BufferedImage image = sampled ? sample16(source) : source;
		if (image.getWidth() < 1 || image.getWidth() > 256 || image.getHeight() < 1 || image.getHeight() > 256) {
			throw new IOException("Installed texture is outside the supported 1-256 pixel reference dimensions: "
					+ image.getWidth() + "x" + image.getHeight());
		}
		JsonObject output = new JsonObject();
		output.addProperty("source", reference.namespace() + ":" + reference.path());
		output.addProperty("sourceWidth", source.getWidth());
		output.addProperty("sourceHeight", source.getHeight());
		output.addProperty("width", image.getWidth());
		output.addProperty("height", image.getHeight());
		output.addProperty("sampledRegion", sampled ? "first square frame normalized to 16x16" : "complete texture");
		if (image.getWidth() <= 64 && image.getHeight() <= 64) {
			appendIndexed(output, encode(image));
		} else {
			JsonArray tiles = new JsonArray();
			for (int y = 0; y < image.getHeight(); y += 64) for (int x = 0; x < image.getWidth(); x += 64) {
				int width = Math.min(64, image.getWidth() - x);
				int height = Math.min(64, image.getHeight() - y);
				JsonObject tile = new JsonObject();
				tile.addProperty("x", x);
				tile.addProperty("y", y);
				tile.addProperty("width", width);
				tile.addProperty("height", height);
				appendIndexed(tile, encode(image.getSubimage(x, y, width, height)));
				tiles.add(tile);
			}
			output.add("tiles", tiles);
			output.addProperty("tileRepresentation",
					"each coordinate-labelled tile is an exact RRGGBBAA palette plus hexadecimal rows");
		}
		return GSON.toJson(output);
	}

	/**
	 * Returns every conventional inventory/state texture and every equipment layer associated with an installed item.
	 * These paths are consumed textually by {@link RecipeVisualReferences}; raster bytes never cross the model boundary.
	 */
	static List<String> referencesForItem(Identifier itemId) {
		return referencesForItem(itemId, null, itemId);
	}

	static List<String> referencesForItem(Identifier itemId, Identifier equipmentAssetId) {
		return referencesForItem(itemId, equipmentAssetId, itemId);
	}

	static List<String> referencesForItem(Identifier itemId, Identifier equipmentAssetId, Identifier itemModelId) {
		return referencesForItem(itemId, equipmentAssetId, itemModelId, true);
	}

	/** Compatibility view used only to migrate recipe digests written before world-carrier sheets were supplied. */
	static List<String> referencesForItemWithoutWorldCarriers(
			Identifier itemId, Identifier equipmentAssetId, Identifier itemModelId) {
		return referencesForItem(itemId, equipmentAssetId, itemModelId, false);
	}

	private static List<String> referencesForItem(Identifier itemId, Identifier equipmentAssetId,
			Identifier itemModelId, boolean includeWorldCarriers) {
		String itemPath = "item/" + itemId.getPath();
		String blockPath = "block/" + itemId.getPath();
		Set<TextureReference> available = new TreeSet<>(Comparator.comparing(TextureReference::qualifiedPath));
		// Conventional exact paths remain useful in stripped unit/runtime class-loader environments where archive roots cannot
		// be enumerated. Materialization later discards candidates that do not actually exist.
		available.add(new TextureReference(itemId.getNamespace(), itemPath));
		available.add(new TextureReference(itemId.getNamespace(), blockPath));
		for (TextureReference reference : installedTextures()) {
			if (!reference.namespace().equals(itemId.getNamespace())) continue;
			// Numeric animation frames are a true family (for example compass_00). Do not include arbitrary prefix
			// matches such as every leather_* item merely because the provided ingredient is minecraft:leather.
			if (reference.path().equals(itemPath) || reference.path().matches(
					java.util.regex.Pattern.quote(itemPath) + "_[0-9]+") || reference.path().equals(blockPath)) {
				available.add(reference);
			}
		}
		Identifier effectiveModel = itemModelId == null ? itemId : itemModelId;
		available.addAll(itemDefinitionTextures(effectiveModel));
		// Compatibility for installed mods that still provide only the legacy item model.
		available.addAll(modelTextures(Identifier.fromNamespaceAndPath(
				effectiveModel.getNamespace(), "item/" + effectiveModel.getPath()), new java.util.HashSet<>()));
		available.addAll(equipmentTextures(itemId, equipmentAssetId));
		if (includeWorldCarriers) available.addAll(nativeWorldCarrierTextures(itemId));
		return available.stream().map(reference -> reference.virtualPath() + ".json").toList();
	}

	/** World-render artwork is not reachable from item-definition JSON, so bridge native carrier classes by convention. */
	private static Set<TextureReference> nativeWorldCarrierTextures(Identifier itemId) {
		String path = itemId.getPath();
		String material = null;
		boolean chest = false;
		for (String suffix : List.of("_chest_boat", "_chest_raft", "_boat", "_raft")) {
			if (path.endsWith(suffix) && path.length() > suffix.length()) {
				material = path.substring(0, path.length() - suffix.length());
				chest = suffix.startsWith("_chest_");
				break;
			}
		}
		if (material == null) return Set.of();
		return Set.of(new TextureReference(itemId.getNamespace(),
				(chest ? "entity/chest_boat/" : "entity/boat/") + material));
	}

	/** Native item carriers whose world texture and model factory can be paired without guessing from a filename. */
	static List<NativeEntityProfile> nativeEntityProfiles(Identifier itemId) {
		String path = itemId.getPath();
		for (String suffix : List.of("_chest_boat", "_chest_raft", "_boat", "_raft")) {
			if (!path.endsWith(suffix) || path.length() <= suffix.length()) continue;
			String material = path.substring(0, path.length() - suffix.length());
			boolean chest = suffix.startsWith("_chest_");
			boolean raft = suffix.endsWith("_raft");
			String texturePath = (chest ? "entity/chest_boat/" : "entity/boat/") + material + ".json";
			String texture = itemId.getNamespace().equals("minecraft")
					? texturePath : itemId.getNamespace() + "/" + texturePath;
			String model = raft ? "net.minecraft.client.model.object.boat.RaftModel"
					: "net.minecraft.client.model.object.boat.BoatModel";
			String factory = raft ? chest ? "createChestRaftModel" : "createRaftModel"
					: chest ? "createChestBoatModel" : "createBoatModel";
			return List.of(new NativeEntityProfile(texture, model, factory));
		}
		return List.of();
	}

	static JsonObject materializeNativeEntityProfile(NativeEntityProfile profile) throws IOException {
		JsonObject texture = JsonParser.parseString(materialize(profile.texturePath())).getAsJsonObject();
		try {
			Class<?> modelType = Class.forName(profile.modelClass(), false,
					MinecraftReferenceExporter.class.getClassLoader());
			String source = JavaSourceDecompiler.decompile(modelType).javaSource();
			JsonObject result = new JsonObject();
			result.addProperty("texturePath", "reference/vanilla/" + profile.texturePath());
			result.add("texture", texture);
			result.add("nativeModel", NativeEntityModelReference.translate(
					profile.modelClass(), profile.factoryMethod(), source, texture));
			return result;
		} catch (ClassNotFoundException | LinkageError unavailable) {
			throw new IOException("Native entity model class is unavailable: " + profile.modelClass(), unavailable);
		}
	}

	/** Traverses the 26.2 item-definition dispatch graph and every legacy model it references. */
	private static Set<TextureReference> itemDefinitionTextures(Identifier itemModelId) {
		Set<TextureReference> result = new TreeSet<>(Comparator.comparing(TextureReference::qualifiedPath));
		try {
			String relative = "assets/" + itemModelId.getNamespace() + "/items/" + itemModelId.getPath() + ".json";
			JsonElement definition = JsonParser.parseString(readText(relative));
			Set<Identifier> models = new TreeSet<>(Comparator.comparing(Identifier::toString));
			collectModelIds(definition, models);
			Set<Identifier> visited = new java.util.HashSet<>();
			for (Identifier model : models) result.addAll(modelTextures(model, visited));
		} catch (IOException | RuntimeException ignored) {
			// Legacy model fallback is handled by referencesForItem.
		}
		return result;
	}

	private static void collectModelIds(JsonElement element, Set<Identifier> result) {
		if (element == null || element.isJsonNull()) return;
		if (element instanceof JsonArray array) {
			for (JsonElement child : array) collectModelIds(child, result);
			return;
		}
		if (!(element instanceof JsonObject object)) return;
		for (var entry : object.entrySet()) {
			if ((entry.getKey().equals("model") || entry.getKey().equals("base"))
					&& entry.getValue().isJsonPrimitive()
					&& entry.getValue().getAsJsonPrimitive().isString()) {
				try {
					result.add(Identifier.parse(entry.getValue().getAsString()));
				} catch (RuntimeException ignored) {
				}
			} else {
				collectModelIds(entry.getValue(), result);
			}
		}
	}

	/** Resolves conventional item-model texture aliases so mod items are not required to name their PNG after the item ID. */
	private static Set<TextureReference> modelTextures(Identifier modelId, Set<Identifier> visited) {
		Set<TextureReference> result = new TreeSet<>(Comparator.comparing(TextureReference::qualifiedPath));
		if (!visited.add(modelId)) return result;
		try {
			String relative = "assets/" + modelId.getNamespace() + "/models/" + modelId.getPath() + ".json";
			JsonObject model = JsonParser.parseString(readText(relative)).getAsJsonObject();
			if (model.get("textures") instanceof JsonObject textures) for (var entry : textures.entrySet()) {
				if (!entry.getValue().isJsonPrimitive()) continue;
				String value = entry.getValue().getAsString();
				if (value.startsWith("#")) continue;
				Identifier texture = Identifier.parse(value);
				result.add(new TextureReference(texture.getNamespace(), texture.getPath()));
			}
			if (model.has("parent")) {
				result.addAll(modelTextures(Identifier.parse(model.get("parent").getAsString()), visited));
			}
			Set<Identifier> referencedModels = new TreeSet<>(Comparator.comparing(Identifier::toString));
			collectModelIds(model, referencedModels);
			for (Identifier referenced : referencedModels) result.addAll(modelTextures(referenced, visited));
		} catch (IOException | RuntimeException ignored) {
			// Conventional exact item/block paths are still attempted when no legacy model JSON exists.
		}
		return result;
	}

	private static void appendIndexed(JsonObject output, DynamicTextureSpec indexed) {
		JsonArray palette = new JsonArray();
		indexed.palette().forEach(palette::add);
		output.add("palette", palette);
		JsonArray rows = new JsonArray();
		indexed.rows().forEach(rows::add);
		output.add("rows", rows);
	}

	private static BufferedImage read(TextureReference reference) throws IOException {
		String relative = "assets/" + reference.namespace() + "/textures/" + reference.path() + ".png";
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) for (Path root : mod.getRootPaths()) {
			Path texture = root.resolve(relative);
			if (!Files.isRegularFile(texture)) continue;
			try (InputStream input = Files.newInputStream(texture)) {
				BufferedImage image = ImageIO.read(input);
				if (image != null) return image;
			}
		}
		try (InputStream input = MinecraftReferenceExporter.class.getClassLoader().getResourceAsStream(relative)) {
			if (input != null) {
				BufferedImage image = ImageIO.read(input);
				if (image != null) return image;
			}
		}
		throw new IOException("Installed texture does not exist: " + reference.qualifiedPath());
	}

	private static TextureReference parse(String virtualPath) throws IOException {
		String normalized = virtualPath.replace('\\', '/');
		if (normalized.startsWith("/") || normalized.contains("../") || !normalized.endsWith(".json")) {
			throw new IOException("Invalid installed texture reference path");
		}
		String path = normalized.substring(0, normalized.length() - 5);
		int separator = path.indexOf('/');
		String namespace = "minecraft";
		if (separator > 0 && installedNamespaces().contains(path.substring(0, separator))) {
			namespace = path.substring(0, separator);
			path = path.substring(separator + 1);
		}
		if (!validNamespace(namespace) || !path.matches("[a-z0-9_./-]+") || path.startsWith("/")
				|| path.endsWith("/") || path.contains("//")) {
			throw new IOException("Invalid installed texture reference path");
		}
		return new TextureReference(namespace, path);
	}

	private static Set<TextureReference> equipmentTextures(Identifier itemId, Identifier equipmentAssetId) {
		Set<TextureReference> result = new TreeSet<>(Comparator.comparing(TextureReference::qualifiedPath));
		try {
			Identifier asset = equipmentAssetId == null ? inferredArmorAsset(itemId) : equipmentAssetId;
			if (asset == null) return result;
			String relative = "assets/" + asset.getNamespace() + "/equipment/" + asset.getPath() + ".json";
			JsonObject equipment = JsonParser.parseString(readText(relative)).getAsJsonObject();
			JsonObject layers = equipment.getAsJsonObject("layers");
			if (layers == null) return result;
			for (var layer : layers.entrySet()) {
				if (!(layer.getValue() instanceof JsonArray entries)) continue;
				for (JsonElement element : entries) {
					if (!(element instanceof JsonObject entry) || !entry.has("texture")) continue;
					Identifier texture = Identifier.parse(entry.get("texture").getAsString());
					TextureReference reference = new TextureReference(texture.getNamespace(),
							"entity/equipment/" + layer.getKey() + "/" + texture.getPath());
					result.add(reference);
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// An unusual mod item may omit conventional equipment resources. Its ordinary item texture remains available.
		}
		return result;
	}

	private static Identifier inferredArmorAsset(Identifier itemId) {
		String path = itemId.getPath();
		String material = null;
		for (String suffix : List.of("_helmet", "_chestplate", "_leggings", "_boots")) {
			if (path.endsWith(suffix)) material = path.substring(0, path.length() - suffix.length());
		}
		if (path.equals("turtle_helmet") || path.equals("turtle_shell")) material = "turtle_scute";
		if (path.equals("elytra")) material = "elytra";
		if (material == null && path.contains("armor")) {
			for (String candidate : List.of("leather", "copper", "chainmail", "iron", "golden", "gold",
					"diamond", "netherite", "turtle_scute", "armadillo_scute")) {
				if (path.startsWith(candidate + "_") || path.endsWith("_" + candidate + "_armor")) {
					material = candidate;
					break;
				}
			}
		}
		if (material == null) return null;
		if (material.equals("golden")) material = "gold";
		return Identifier.fromNamespaceAndPath(itemId.getNamespace(), material);
	}

	private static String readText(String relative) throws IOException {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) for (Path root : mod.getRootPaths()) {
			Path path = root.resolve(relative);
			if (Files.isRegularFile(path)) return Files.readString(path, StandardCharsets.UTF_8);
		}
		try (InputStream input = MinecraftReferenceExporter.class.getClassLoader().getResourceAsStream(relative)) {
			if (input != null) return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		throw new IOException("Installed resource does not exist: " + relative);
	}

	private static List<TextureReference> installedTextures() {
		List<TextureReference> cached = installedTextureCache;
		if (cached != null) return cached;
		return scanInstalledTextures();
	}

	private static synchronized List<TextureReference> scanInstalledTextures() {
		if (installedTextureCache != null) return installedTextureCache;
		Set<TextureReference> entries = new TreeSet<>(Comparator.comparing(TextureReference::qualifiedPath));
		try {
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) for (Path root : mod.getRootPaths()) {
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
									.map(textures::relativize).map(Path::toString)
									.map(path -> path.substring(0, path.length() - 4).replace('\\', '/'))
									.map(path -> new TextureReference(namespaceName, path)).forEach(entries::add);
						}
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// The normal Fabric runtime supplies installed roots; an empty index is safer than leaking raster data.
		}
		installedTextureCache = List.copyOf(entries);
		return installedTextureCache;
	}

	private static Set<String> installedNamespaces() {
		Set<String> namespaces = new java.util.HashSet<>();
		installedTextures().forEach(reference -> namespaces.add(reference.namespace()));
		return namespaces;
	}

	private static boolean validNamespace(String value) {
		return value != null && value.matches("[a-z0-9_.-]+");
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

	static int normalizeAlpha(int argb) {
		return ((argb >>> 24) & 0xFF) < 16 ? 0 : argb | 0xFF000000;
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
		String virtualPath() {
			return namespace.equals("minecraft") ? path : namespace + "/" + path;
		}

		String qualifiedPath() {
			return namespace + ':' + path;
		}
	}

	record NativeEntityProfile(String texturePath, String modelClass, String factoryMethod) { }
}
