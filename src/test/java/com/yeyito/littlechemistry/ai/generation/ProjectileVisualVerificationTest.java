package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.content.DynamicBreakingPower;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemTexture;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicItemVisuals;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicTool;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProjectileVisualVerificationTest {
	private static final String BEHAVIOR = """
			public final class GeneratedBehaviorImpl implements
			        com.yeyito.littlechemistry.behavior.DynamicBehavior {
			    public GeneratedBehaviorImpl() {}
			}
			""";

	@Test
	void rejectsTokenFrameChangesButAcceptsMeaningfulCrossbowStates() throws Exception {
		List<DynamicItemTexture> meaningful = List.of(
				frame(DynamicItemVisuals.PULLING_0, "704020FF"),
				frame(DynamicItemVisuals.PULLING_1, "805020FF"),
				frame(DynamicItemVisuals.PULLING_2, "906020FF"),
				frame(DynamicItemVisuals.CHARGED, "A07020FF"),
				frame(DynamicItemVisuals.CHARGED_FIREWORK, "B08020FF"));
		GeneratedContentSpec valid = spec(new DynamicItemVisuals(meaningful));
		assertDoesNotThrow(() -> WorkspaceGenerationVerifier.validateProjectileVisuals(valid));

		List<DynamicItemTexture> tokenChange = new ArrayList<>(meaningful);
		DynamicTextureSpec almostBase = onePixelChangedBase();
		tokenChange.set(0, new DynamicItemTexture(DynamicItemVisuals.PULLING_0,
				DynamicTextureAsset.sha256(almostBase.renderPng()), almostBase));
		GeneratedContentSpec invalid = spec(new DynamicItemVisuals(tokenChange));
		assertThrows(IllegalArgumentException.class,
				() -> WorkspaceGenerationVerifier.validateProjectileVisuals(invalid));

		List<DynamicItemTexture> invisibleChanges = new ArrayList<>(meaningful);
		DynamicTextureSpec transparentRgbOnly = new DynamicTextureSpec(
				List.of("FFFFFF00", "604020FF"), base().rows());
		invisibleChanges.set(0, new DynamicItemTexture(DynamicItemVisuals.PULLING_0,
				DynamicTextureAsset.sha256(transparentRgbOnly.renderPng()), transparentRgbOnly));
		GeneratedContentSpec invisiblyDifferent = spec(new DynamicItemVisuals(invisibleChanges));
		assertThrows(IllegalArgumentException.class,
				() -> WorkspaceGenerationVerifier.validateProjectileVisuals(invisiblyDifferent));
	}

	private static GeneratedContentSpec spec(DynamicItemVisuals visuals) {
		DynamicItemProperties properties = new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.CROSSBOW, 1, Rarity.COMMON, false, 1, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, null, null);
		return new GeneratedContentSpec(base(), null, properties, null, null, BEHAVIOR, null,
				DynamicRarity.COMMON, "A test crossbow.", List.of(), null, null, null, visuals);
	}

	private static DynamicItemTexture frame(String id, String color) throws Exception {
		DynamicTextureSpec texture = checker(color);
		return new DynamicItemTexture(id, DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	private static DynamicTextureSpec base() {
		return checker("604020FF");
	}

	private static DynamicTextureSpec checker(String color) {
		List<String> rows = new ArrayList<>();
		for (int y = 0; y < 16; y++) {
			StringBuilder row = new StringBuilder();
			for (int x = 0; x < 16; x++) row.append((x + y) % 2);
			rows.add(row.toString());
		}
		return new DynamicTextureSpec(List.of("00000000", color), rows);
	}

	private static DynamicTextureSpec onePixelChangedBase() {
		List<String> rows = new ArrayList<>(base().rows());
		String first = rows.getFirst();
		rows.set(0, (first.charAt(0) == '0' ? "1" : "0") + first.substring(1));
		return new DynamicTextureSpec(base().palette(), rows);
	}
}
