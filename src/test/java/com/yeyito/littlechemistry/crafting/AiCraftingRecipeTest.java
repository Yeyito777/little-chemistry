package com.yeyito.littlechemistry.crafting;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AiCraftingRecipeTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		bind(Items.SHEARS, DataComponentMap.builder()
				.set(DataComponents.MAX_STACK_SIZE, 1)
				.set(DataComponents.MAX_DAMAGE, 238)
				.set(DataComponents.DAMAGE, 0).build());
		bind(Items.STRING, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
		bind(Items.WATER_BUCKET, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
		bind(Items.BUCKET, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 16).build());
	}

	private static void bind(net.minecraft.world.item.Item item, DataComponentMap components) {
		if (!item.builtInRegistryHolder().areComponentsBound()) {
			item.builtInRegistryHolder().bindComponents(components);
		}
	}

	@Test
	void utilityDamageAndKeptCatalystFollowMirroredIngredients() {
		ItemStack shears = new ItemStack(Items.SHEARS);
		ItemStack string = new ItemStack(Items.STRING);
		RecipeSignature signature = new RecipeSignature(2, 1, List.of(shears, string));
		AiCraftingRecipe recipe = new AiCraftingRecipe(signature, "missing_output", 1,
				List.of(CraftingIngredientUse.DAMAGE, CraftingIngredientUse.KEEP));

		var direct = recipe.getRemainingItems(positioned(string, shears, false));
		assertEquals(Items.SHEARS, direct.get(0).getItem());
		assertEquals(1, direct.get(0).getDamageValue());
		assertEquals(Items.STRING, direct.get(1).getItem());

		var mirrored = recipe.getRemainingItems(positioned(string, shears, true));
		assertEquals(Items.STRING, mirrored.get(0).getItem());
		assertEquals(Items.SHEARS, mirrored.get(1).getItem());
		assertEquals(1, mirrored.get(1).getDamageValue());
	}

	@Test
	void ordinaryDamageableItemsAreConsumedUnlessTheRecipeSelectsUtilityUse() {
		ItemStack shears = new ItemStack(Items.SHEARS);
		ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
		RecipeSignature signature = new RecipeSignature(2, 1, List.of(shears, waterBucket));
		AiCraftingRecipe recipe = new AiCraftingRecipe(signature, "missing_output", 1);

		var remainders = recipe.getRemainingItems(CraftingInput.of(2, 1, List.of(shears, waterBucket)));

		assertTrue(remainders.get(0).isEmpty());
		assertEquals(Items.BUCKET, remainders.get(1).getItem());
		assertEquals(List.of(CraftingIngredientUse.CONSUME, CraftingIngredientUse.DEFAULT),
				recipe.ingredientUses());
	}

	@Test
	void utilityImplementBreaksWhenItsLastDurabilityIsSpent() {
		ItemStack shears = new ItemStack(Items.SHEARS);
		shears.setDamageValue(shears.getMaxDamage() - 1);

		assertTrue(AiCraftingRecipe.craftingRemainder(shears, CraftingIngredientUse.DAMAGE).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new AiCraftingRecipe(
				new RecipeSignature(1, 1, List.of(new ItemStack(Items.STRING))), "missing_output", 1,
				List.of(CraftingIngredientUse.DAMAGE)));
	}

	@Test
	void craftingContextDescribesTheNativeDefaultWithoutMakingTheAestheticDecision() {
		RecipeSignature signature = new RecipeSignature(2, 1,
				List.of(new ItemStack(Items.SHEARS), new ItemStack(Items.WATER_BUCKET)));

		var grid = signature.toAiContext().getAsJsonArray("grid");

		assertEquals("consume", grid.get(0).getAsJsonObject().get("defaultIngredientUse").getAsString());
		assertTrue(grid.get(0).getAsJsonObject().get("damageable").getAsBoolean());
		assertEquals("native_remainder", grid.get(1).getAsJsonObject()
				.get("defaultIngredientUse").getAsString());
	}

	private static CraftingInput positioned(ItemStack string, ItemStack shears, boolean mirrored) {
		return CraftingInput.of(2, 1, List.of(
				mirrored ? string.copy() : shears.copy(),
				mirrored ? shears.copy() : string.copy()));
	}
}
