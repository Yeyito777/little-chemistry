package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicBreakingPower;
import com.yeyito.littlechemistry.content.DynamicHeldType;
import com.yeyito.littlechemistry.content.DynamicItemProperties;
import com.yeyito.littlechemistry.content.DynamicItemTexture;
import com.yeyito.littlechemistry.content.DynamicItemType;
import com.yeyito.littlechemistry.content.DynamicItemVisuals;
import com.yeyito.littlechemistry.content.DynamicRarity;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicTool;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DynamicSpecialItemRendererTest {
	@Test
	void selectsExactStateAndUsesChargedThenBaseFallbacks() {
		DynamicTextureSpec texture = new DynamicTextureSpec(
				List.of("00000000", "FFFFFFFF"), List.of("01".repeat(8)).stream().flatMap(
						row -> java.util.stream.Stream.generate(() -> row).limit(16)).toList());
		DynamicItemVisuals visuals = new DynamicItemVisuals(List.of(
				new DynamicItemTexture(DynamicItemVisuals.PULLING_0, "1".repeat(64), texture),
				new DynamicItemTexture(DynamicItemVisuals.CHARGED, "2".repeat(64), texture)));
		DynamicItemProperties properties = new DynamicItemProperties(
				DynamicItemType.ITEM, DynamicHeldType.CROSSBOW, 1, Rarity.COMMON, false, 1, 0.0,
				DynamicTool.NONE, DynamicBreakingPower.NONE, 1.0F, 0.0, 4.0,
				0, 0, 0, null, null);
		DynamicContentDefinition definition = new DynamicContentDefinition(
				DynamicContentType.ITEM, "test_crossbow", "Test Crossbow", "A test crossbow.",
				DynamicRarity.COMMON, 0L, "0".repeat(64), texture,
				null, null, null, properties, null,
				DynamicBehaviorSource.completeLegacySource(null), null, List.of(), null, null, null, visuals);

		assertEquals("1".repeat(64), DynamicSpecialItemRenderer.selectedTextureHash(
				definition, DynamicItemVisuals.PULLING_0, false));
		assertEquals("2".repeat(64), DynamicSpecialItemRenderer.selectedTextureHash(
				definition, DynamicItemVisuals.CHARGED_FIREWORK, false));
		assertEquals("0".repeat(64), DynamicSpecialItemRenderer.selectedTextureHash(
				definition, DynamicItemVisuals.PULLING_2, false));
		assertEquals("0".repeat(64), DynamicSpecialItemRenderer.selectedTextureHash(
				definition, DynamicItemVisuals.CHARGED, true));
	}
}
