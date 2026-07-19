package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicBlockDropsTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void validatesVanillaAndExistingGeneratedTargets() {
		DynamicContentDefinition generated = new DynamicContentDefinition(
				DynamicContentType.ITEM, "ember_shard", "Ember Shard", 0L, "0".repeat(64), null,
				null, DynamicItemProperties.DEFAULT, null, DynamicBehaviorSource.completeLegacySource(null));
		DynamicBlockDrops vanilla = drops("minecraft:diamond", 1, 1);
		DynamicBlockDrops dynamic = drops("little_chemistry:ember_shard", 1, 16);

		assertDoesNotThrow(() -> vanilla.validateAvailableTargets(ignored -> null));
		assertDoesNotThrow(() -> dynamic.validateAvailableTargets(
				name -> name.equals(generated.name()) ? generated : null));
	}

	@Test
	void rejectsMissingLogicalTargetsAndFinalOwnerAliases() {
		DynamicContentDefinition target = new DynamicContentDefinition(
				DynamicContentType.ITEM, "existing_ore", "Existing Ore", 0L, "0".repeat(64), null,
				null, DynamicItemProperties.DEFAULT, null, DynamicBehaviorSource.completeLegacySource(null));
		IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
				() -> drops("little_chemistry:missing", 1, 1).validateAvailableTargets(ignored -> null));
		assertDoesNotThrow(() -> drops("little_chemistry:existing_ore", 1, 2)
				.validateAvailableTargets(name -> target));
		IllegalArgumentException ownerAlias = assertThrows(IllegalArgumentException.class,
				() -> drops("little_chemistry:existing_ore", 1, 2)
						.validateNewTargets("existing_ore", name -> target));

		assertTrue(missing.getMessage().contains("Unknown generated"));
		assertTrue(ownerAlias.getMessage().contains("target=self"));
	}

	@Test
	void dependencyTrackingDistinguishesRegisteredAndDynamicLittleChemistryIds() {
		DynamicBlockDrops registered = new DynamicBlockDrops(List.of(new DynamicDropEntry(
				DynamicDropTargetKind.REGISTERED_ITEM, "little_chemistry:wand_of_creation",
				1, 1, 1.0, DynamicFortuneMode.NONE)), false, false);
		DynamicBlockDrops dynamic = new DynamicBlockDrops(List.of(new DynamicDropEntry(
				DynamicDropTargetKind.DYNAMIC_CONTENT, "little_chemistry:ember_shard",
				1, 1, 1.0, DynamicFortuneMode.NONE)), false, false);

		assertEquals(java.util.Set.of(), registered.referencedDynamicNames());
		assertEquals(java.util.Set.of("ember_shard"), dynamic.referencedDynamicNames());
	}

	@Test
	void rejectsOreLikeFortuneForSingularGeneratedTargets() {
		DynamicContentDefinition armor = new DynamicContentDefinition(
				DynamicContentType.ARMOR, "ember_helm", "Ember Helm", 0L, "0".repeat(64), null,
				null, null, DynamicArmorProperties.defaults(DynamicArmorSlot.HEAD),
				DynamicBehaviorSource.completeLegacySource(null));
		DynamicBlockDrops drops = new DynamicBlockDrops(List.of(new DynamicDropEntry(
				DynamicDropTargetKind.DYNAMIC_CONTENT, "little_chemistry:ember_helm",
				1, 1, 1.0, DynamicFortuneMode.ORE_LIKE)), false, false);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> drops.validateAvailableTargets(name -> armor));

		assertTrue(error.getMessage().contains("stackable generated drop target"));
	}

	private static DynamicBlockDrops drops(String target, int minimum, int maximum) {
		return new DynamicBlockDrops(List.of(
				new DynamicDropEntry(target.startsWith("little_chemistry:")
						? DynamicDropTargetKind.DYNAMIC_CONTENT : DynamicDropTargetKind.REGISTERED_ITEM,
						target, minimum, maximum, 1.0, DynamicFortuneMode.NONE)), false, false);
	}
}
