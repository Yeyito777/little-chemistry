package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlot;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlotRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkstationRecipeRejectionTest {
	@BeforeAll
	@SuppressWarnings("deprecation")
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		if (!Items.BARRIER.builtInRegistryHolder().areComponentsBound()) {
			Items.BARRIER.builtInRegistryHolder().bindComponents(
					DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
		}
		if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
			Items.STONE.builtInRegistryHolder().bindComponents(
					DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
		}
	}

	@Test
	void roundTripsTheAllowedCategoryAndBuildsAnUngrabbableBarrierPreview() {
		for (WorkstationRecipeRejection.Category category : WorkstationRecipeRejection.Category.values()) {
			WorkstationRecipeRejection rejection = new WorkstationRecipeRejection(category,
					"This workstation cannot safely perform that transformation.");
			WorkstationRecipeRejection decoded = WorkstationRecipeRejection.fromJson(rejection.toJson());

			assertEquals(rejection, decoded);
			ItemStack display = decoded.displayStack();
			assertTrue(display.is(Items.BARRIER));
			assertEquals("Rejection", display.getHoverName().getString());
			assertTrue(WorkstationRecipeRejection.isDisplayStack(display));
			assertTrue(display.get(DataComponents.LORE).lines().stream()
					.anyMatch(line -> line.getString().contains("cannot safely")));

			SimpleContainer clientContainer = new SimpleContainer(display);
			WorkstationSlot slot = new WorkstationSlot(clientContainer, 0,
					new DynamicWorkstationSlot("result", DynamicWorkstationSlotRole.OUTPUT,
							0, 0, 64, false, true, null, null), null);
			assertFalse(slot.mayPickup(null));
			assertTrue(slot.safeClone(null).isEmpty());
			assertFalse(slot.mayPlace(new ItemStack(Items.STONE)));
		}
	}


	@Test
	void validatesCategoryAndOneOrTwoShortSentences() {
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRejection.fromJson(
				JsonParser.parseString("""
						{"category":"nonsense_recipe","description":"This is no longer an allowed category."}
						""").getAsJsonObject()));
		assertThrows(IllegalArgumentException.class, () -> WorkstationRecipeRejection.fromJson(
				JsonParser.parseString("""
						{"category":"wrong_workstation","description":"This is no longer an allowed category."}
						""").getAsJsonObject()));
		assertThrows(IllegalArgumentException.class, () -> new WorkstationRecipeRejection(
				WorkstationRecipeRejection.Category.WORKSTATION_TOO_WEAK,
				"One sentence. Two sentences. Three sentences."));
		assertThrows(IllegalArgumentException.class, () -> new WorkstationRecipeRejection(
				WorkstationRecipeRejection.Category.WORKSTATION_TOO_WEAK,
				"This explanation is missing punctuation"));
	}
}
