package com.yeyito.littlechemistry.content;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Resolves workstation empty-slot artwork against installed GUI-atlas sprites. */
public final class DynamicWorkstationSlotIcon {
	private static volatile Set<Identifier> installed;

	private DynamicWorkstationSlotIcon() {
	}

	/** Returns a safe GUI sprite ID, including general legacy item-path aliases, or null when no sprite exists. */
	public static Identifier resolve(String raw) {
		if (raw == null || raw.isBlank()) return null;
		Identifier original;
		try {
			original = Identifier.parse(raw);
		} catch (RuntimeException invalid) {
			return null;
		}
		Identifier normalized = normalizeAlias(original);
		Set<Identifier> available = availableIds();
		if (available.contains(normalized) || resourceExists(normalized)) return normalized;
		return available.contains(original) || resourceExists(original) ? original : null;
	}

	static Identifier normalizeAlias(Identifier original) {
		String path = original.getPath();
		if (path.startsWith("textures/gui/sprites/")) {
			path = path.substring("textures/gui/sprites/".length());
		} else if (path.startsWith("gui/sprites/")) {
			path = path.substring("gui/sprites/".length());
		} else if (path.startsWith("item/empty_slot_")) {
			path = "container/slot/" + path.substring("item/empty_slot_".length());
		} else if (path.startsWith("item/")) {
			path = "container/slot/" + path.substring("item/".length());
		}
		if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
		return Identifier.fromNamespaceAndPath(original.getNamespace(), path);
	}

	/** Complete logical GUI-sprite IDs packaged by currently installed mods. */
	public static Set<Identifier> availableIds() {
		Set<Identifier> current = installed;
		if (current != null) return current;
		synchronized (DynamicWorkstationSlotIcon.class) {
			if (installed == null) installed = scanInstalled();
			return installed;
		}
	}

	private static boolean resourceExists(Identifier id) {
		String path = "assets/" + id.getNamespace() + "/textures/gui/sprites/" + id.getPath() + ".png";
		return DynamicWorkstationSlotIcon.class.getClassLoader().getResource(path) != null;
	}

	private static Set<Identifier> scanInstalled() {
		Set<Identifier> result = new HashSet<>();
		for (var mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				Path assets = root.resolve("assets");
				if (!Files.isDirectory(assets)) continue;
				try (var namespaces = Files.list(assets)) {
					for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
						Path sprites = namespace.resolve("textures/gui/sprites");
						if (!Files.isDirectory(sprites)) continue;
						try (var paths = Files.walk(sprites)) {
							paths.filter(Files::isRegularFile)
									.filter(path -> path.getFileName().toString().endsWith(".png"))
									.forEach(path -> {
										String relative = sprites.relativize(path).toString().replace('\\', '/');
										relative = relative.substring(0, relative.length() - 4);
										try {
											result.add(Identifier.fromNamespaceAndPath(
													namespace.getFileName().toString(), relative));
										} catch (RuntimeException ignored) {
										}
									});
						}
					}
				} catch (IOException ignored) {
				}
			}
		}
		return Set.copyOf(result);
	}
}
