package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicParticleDefinitionTest {
	@Test
	void acceptsAnimatedTranslucentFramesAndNormalizesTints() {
		DynamicParticleDefinition particle = particle(20, "ffffffc0", "ff400000");

		assertEquals(2, particle.frames().size());
		assertEquals("FFFFFFC0", particle.startColor());
		assertEquals("FF400000", particle.endColor());
		assertEquals(192 / 255.0F, particle.startAlpha());
	}

	@Test
	void rejectsNonSquareAndInvisibleFrames() {
		DynamicTextureSpec rectangular = new DynamicTextureSpec(
				List.of("FFFFFFFF"), List.of("00", "00", "00"));
		DynamicTextureSpec invisible = new DynamicTextureSpec(
				List.of("FFFFFF00"), List.of("00", "00"));

		assertThrows(IllegalArgumentException.class, () -> definitionWithFrames(List.of(
				new DynamicParticleFrame("1".repeat(64), rectangular))));
		assertThrows(IllegalArgumentException.class, () -> definitionWithFrames(List.of(
				new DynamicParticleFrame("2".repeat(64), invisible))));
		assertThrows(IllegalArgumentException.class, () -> particle(20, "FFFFFF00", "FF400000"));
		assertThrows(IllegalArgumentException.class, () -> particle(1, "FFFFFFFF", "FFFFFF00"));
	}

	@Test
	void owningDefinitionRejectsDanglingAndOverBudgetAmbientReferences() {
		DynamicBlockProperties dangling = blockWithEmitter(
				new DynamicParticleEmitter("custom:missing", 0.1, 1, 0.01, true));
		assertThrows(IllegalArgumentException.class, () -> blockDefinition(dangling, List.of(particle(20,
				"FFFFFFFF", "FFFFFF00"))));

		DynamicBlockProperties overBudget = blockWithEmitter(
				new DynamicParticleEmitter("custom:spark", 0.25, 4, 0.01, true));
		assertThrows(IllegalArgumentException.class, () -> blockDefinition(overBudget, List.of(particle(400,
				"FFFFFFFF", "FFFFFF00"))));
	}

	private static DynamicParticleDefinition particle(int lifetime, String startColor, String endColor) {
		DynamicTextureSpec frame = new DynamicTextureSpec(
				List.of("00000000", "FF8020FF"), List.of("01", "10"));
		return new DynamicParticleDefinition("spark", List.of(
				new DynamicParticleFrame("1".repeat(64), frame),
				new DynamicParticleFrame("2".repeat(64), frame)),
				2, true, lifetime, 0.2F, 0.05F, startColor, endColor,
				-0.1F, 0.96F, false, true, 0.05F);
	}

	private static DynamicParticleDefinition definitionWithFrames(List<DynamicParticleFrame> frames) {
		return new DynamicParticleDefinition("spark", frames, 1, false, 20,
				0.1F, 0.1F, "FFFFFFFF", "FFFFFFFF", 0, 1, false, false, 0);
	}

	private static DynamicBlockProperties blockWithEmitter(DynamicParticleEmitter emitter) {
		return new DynamicBlockProperties(DynamicMaterial.STONE, 1.0F, DynamicTool.NONE, false,
				DynamicBlockShape.FULL_CUBE, Rarity.COMMON, 0, 0, 0, false, List.of(emitter));
	}

	private static DynamicContentDefinition blockDefinition(DynamicBlockProperties block,
			List<DynamicParticleDefinition> particles) {
		return new DynamicContentDefinition(DynamicContentType.BLOCK, "particle_block", "Particle Block", "",
				DynamicRarity.COMMON, 0, "0".repeat(64), null, null, null,
				block, null, null, DynamicBehaviorSource.completeLegacySource(null), null, particles);
	}
}
