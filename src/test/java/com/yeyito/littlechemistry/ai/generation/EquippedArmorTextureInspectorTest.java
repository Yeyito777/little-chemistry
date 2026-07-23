package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EquippedArmorTextureInspectorTest {
	@Test
	void mapsBaseAndOuterHeadFrontAsExactIndexedRows() {
		List<String> rows = emptyRows();
		for (int y = 8; y < 16; y++) {
			String row = rows.get(y);
			row = row.substring(0, 8) + "1".repeat(8) + row.substring(16);
			row = row.substring(0, 40) + "2".repeat(8) + row.substring(48);
			rows.set(y, row);
		}
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "FF0000FF", "0060FFFF"), rows);

		JsonObject inspection = EquippedArmorTextureInspector.inspect(texture, DynamicArmorSlot.HEAD);
		JsonObject authored = inspection.getAsJsonObject("authoredEquipmentTexture");
		JsonObject front = inspection.getAsJsonObject("equippedViews").getAsJsonObject("front");

		assertEquals(texture.palette(), authored.getAsJsonArray("palette").asList().stream()
				.map(element -> element.getAsString()).toList());
		assertEquals(texture.rows(), authored.getAsJsonArray("rows").asList().stream()
				.map(element -> element.getAsString()).toList());
		assertEquals("0000222222220000", front.getAsJsonArray("rows").get(0).getAsString());
		assertTrue(inspection.get("representation").getAsString().contains("RRGGBBAA"));
		assertFalse(inspection.toString().contains("png"));
	}

	@Test
	void mapsEveryArmDirectionAndMirrorsTheSecondArm() {
		List<String> rows = emptyRows();
		for (int y = 20; y < 32; y++) {
			char[] row = rows.get(y).toCharArray();
			row[48] = '1';
			row[51] = '2';
			row[40] = '3';
			row[43] = '4';
			row[44] = '5';
			row[47] = '6';
			row[52] = '7';
			row[55] = '8';
			rows.set(y, new String(row));
		}
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "FF0000FF", "00FF00FF", "0060FFFF", "FFFF00FF",
						"FF00FFFF", "00FFFFFF", "8030FFFF", "FF8040FF"), rows);

		JsonObject views = EquippedArmorTextureInspector.inspect(texture, DynamicArmorSlot.CHEST)
				.getAsJsonObject("equippedViews");

		assertEquals("5006000000006005", row(views, "front", 8));
		assertEquals("7008000000008007", row(views, "back", 8));
		assertEquals("1002000000004003", row(views, "leftSide", 8));
		assertEquals("3004000000002001", row(views, "rightSide", 8));
		for (String name : List.of("front", "back", "leftSide", "rightSide")) {
			JsonObject mapped = views.getAsJsonObject(name);
			assertEquals(9, mapped.getAsJsonArray("palette").size());
			assertEquals(32, mapped.getAsJsonArray("rows").size());
			mapped.getAsJsonArray("rows").forEach(row -> assertEquals(16, row.getAsString().length()));
		}
	}

	@Test
	void mapsLeggingsAndBootsWithMirroredSideFaces() {
		List<String> rows = emptyRows();
		for (int y = 20; y < 32; y++) {
			char[] row = rows.get(y).toCharArray();
			row[8] = '1';
			row[11] = '2';
			row[0] = '3';
			row[3] = '4';
			row[4] = '5';
			row[7] = '6';
			row[12] = '7';
			row[15] = '8';
			rows.set(y, new String(row));
		}
		DynamicArmorDisplayTextureSpec texture = new DynamicArmorDisplayTextureSpec(
				List.of("00000000", "FF0000FF", "00FF00FF", "0060FFFF", "FFFF00FF",
						"FF00FFFF", "00FFFFFF", "8030FFFF", "FF8040FF"), rows);

		for (DynamicArmorSlot slot : List.of(DynamicArmorSlot.LEGGINGS, DynamicArmorSlot.BOOTS)) {
			JsonObject views = EquippedArmorTextureInspector.inspect(texture, slot).getAsJsonObject("equippedViews");
			assertEquals("0000500660050000", row(views, "front", 20));
			assertEquals("0000700880070000", row(views, "back", 20));
			assertEquals("0000100240030000", row(views, "leftSide", 20));
			assertEquals("0000300420010000", row(views, "rightSide", 20));
		}
	}

	private static String row(JsonObject views, String view, int y) {
		return views.getAsJsonObject(view).getAsJsonArray("rows").get(y).getAsString();
	}

	private static List<String> emptyRows() {
		List<String> rows = new ArrayList<>();
		for (int y = 0; y < 32; y++) rows.add("0".repeat(64));
		return rows;
	}
}
