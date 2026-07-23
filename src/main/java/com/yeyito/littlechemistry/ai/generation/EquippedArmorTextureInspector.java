package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;

import java.util.Arrays;
import java.util.List;

/**
 * Produces text-only equipped-armor mappings in the same indexed palette/row representation authored by the model.
 *
 * This class intentionally has no raster encoder and must never grow one. The generation model must inspect exact palette
 * keys and pixel rows, not a PNG or other vision attachment that it would then have to approximate back into source text.
 */
final class EquippedArmorTextureInspector {
	private static final int VIEW_WIDTH = 16;
	private static final int VIEW_HEIGHT = 32;
	private static final String REPRESENTATION =
			"Every texture is {palette:[RRGGBBAA...],rows:[hexadecimal palette-index strings...]}, exactly the format authored by DynamicTextureSpec and DynamicArmorDisplayTextureSpec.";

	private EquippedArmorTextureInspector() {
	}

	static JsonObject inspect(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot) {
		JsonObject result = new JsonObject();
		result.addProperty("slot", slot.serializedName());
		result.addProperty("representation", REPRESENTATION);
		result.add("authoredEquipmentTexture", texture(texture.palette(), texture.rows()));
		JsonObject views = new JsonObject();
		for (View view : View.values()) {
			views.add(view.serializedName, mappedView(texture, slot, view));
		}
		result.add("equippedViews", views);
		JsonArray notes = new JsonArray();
		notes.add("Each equipped view is a faithful textual mapping of the authored palette keys onto a 16x32 adult humanoid silhouette.");
		notes.add("Transparent palette keys mean the underlying player/model remains exposed; outer-head pixels overlay base-head pixels only when opaque.");
		notes.add("The second arm and leg use Minecraft's mirrored limb mapping. Side faces retain their true depth width instead of being stretched.");
		result.add("mappingNotes", notes);
		return result;
	}

	private static JsonObject mappedView(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot, View view) {
		char transparent = transparentKey(texture.palette());
		char[][] rows = new char[VIEW_HEIGHT][VIEW_WIDTH];
		for (char[] row : rows) Arrays.fill(row, transparent);
		if (slot == DynamicArmorSlot.HEAD) {
			paintCuboidFace(rows, texture, 0, 0, 8, 8, 8, view, 4, 0, false);
			paintCuboidFace(rows, texture, 32, 0, 8, 8, 8, view, 4, 0, false);
		}
		if (slot == DynamicArmorSlot.CHEST || slot == DynamicArmorSlot.LEGGINGS) {
			paintCuboidFace(rows, texture, 16, 16, 8, 12, 4, view, 4, 8, false);
		}
		if (slot == DynamicArmorSlot.CHEST) {
			paintCuboidFace(rows, texture, 40, 16, 4, 12, 4, view, 0, 8, false);
			paintCuboidFace(rows, texture, 40, 16, 4, 12, 4, view, 12, 8, true);
		}
		if (slot == DynamicArmorSlot.LEGGINGS || slot == DynamicArmorSlot.BOOTS) {
			paintCuboidFace(rows, texture, 0, 16, 4, 12, 4, view, 4, 20, false);
			paintCuboidFace(rows, texture, 0, 16, 4, 12, 4, view, 8, 20, true);
		}
		return texture(texture.palette(), Arrays.stream(rows).map(String::new).toList());
	}

	private static void paintCuboidFace(char[][] destination, DynamicArmorDisplayTextureSpec texture,
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
		int centeredX = destinationX + (width - sourceWidth) / 2;
		for (int y = 0; y < height; y++) {
			String sourceRow = texture.rows().get(v + depth + y);
			for (int x = 0; x < sourceWidth; x++) {
				int sampledX = mirrored ? sourceX + sourceWidth - 1 - x : sourceX + x;
				char key = Character.toUpperCase(sourceRow.charAt(sampledX));
				if (!transparent(texture.palette().get(Character.digit(key, 16)))) {
					destination[destinationY + y][centeredX + x] = key;
				}
			}
		}
	}

	private static char transparentKey(List<String> palette) {
		for (int index = 0; index < palette.size(); index++) {
			if (transparent(palette.get(index))) return "0123456789ABCDEF".charAt(index);
		}
		throw new IllegalArgumentException("Armor texture has no transparent palette entry");
	}

	private static boolean transparent(String rgba) {
		return rgba.regionMatches(true, 6, "00", 0, 2);
	}

	private static JsonObject texture(List<String> paletteValues, List<String> rowValues) {
		JsonObject texture = new JsonObject();
		JsonArray palette = new JsonArray();
		paletteValues.forEach(palette::add);
		texture.add("palette", palette);
		JsonArray rows = new JsonArray();
		rowValues.forEach(rows::add);
		texture.add("rows", rows);
		return texture;
	}

	private enum View {
		FRONT("front"), BACK("back"), LEFT_SIDE("leftSide"), RIGHT_SIDE("rightSide");

		private final String serializedName;

		View(String serializedName) {
			this.serializedName = serializedName;
		}
	}
}
