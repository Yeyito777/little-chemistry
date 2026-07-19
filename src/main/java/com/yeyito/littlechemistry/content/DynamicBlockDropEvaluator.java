package com.yeyito.littlechemistry.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

/** Evaluates dynamic block drop declarations against Minecraft's authoritative loot context. */
final class DynamicBlockDropEvaluator {
	private static final int MAX_FORTUNE_LEVEL = 10;
	private static final int MAX_ITEMS_PER_ENTRY = 64;

	private DynamicBlockDropEvaluator() {
	}

	static List<ItemStack> evaluate(DynamicContentDefinition definition, LootParams.Builder params) {
		DynamicBlockProperties properties = definition.block();
		if (properties == null) return List.of();

		ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
		int silkTouch = enchantmentLevel(params, tool, Enchantments.SILK_TOUCH);
		int fortune = enchantmentLevel(params, tool, Enchantments.FORTUNE);
		RandomSource random = params.getLevel().getRandom();
		Float explosionRadius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
		DynamicBlockDrops drops = properties.drops();
		if (silkTouch > 0 && drops.silkTouchDropsSelf()) {
			int count = applyExplosionDecay(1, drops.explosionDecay(), explosionRadius, random);
			return count == 0 ? List.of() : List.of(DynamicContentObjects.createStack(definition));
		}

		List<ItemStack> result = new ArrayList<>();
		for (DynamicDropEntry entry : drops.entries()) {
			if (random.nextDouble() >= entry.chance()) continue;
			ItemStack target = resolveTarget(definition, entry);
			if (target.isEmpty()) continue;
			int count = randomCount(entry, random);
			DynamicFortuneMode fortuneMode = target.getMaxStackSize() == 1
					? DynamicFortuneMode.NONE : entry.fortune();
			count = applyFortune(count, fortuneMode, fortune, random);
			count = applyExplosionDecay(count, drops.explosionDecay(), explosionRadius, random);
			if (count > 0) addStacks(result, target, count);
		}
		return List.copyOf(result);
	}

	private static int enchantmentLevel(LootParams.Builder params, ItemInstance tool,
			net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
		if (tool == null) return 0;
		var holder = params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
		return EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
	}

	static int randomCount(DynamicDropEntry entry, RandomSource random) {
		return entry.minCount() == entry.maxCount()
				? entry.minCount()
				: Mth.nextInt(random, entry.minCount(), entry.maxCount());
	}

	static int applyFortune(int count, DynamicFortuneMode mode, int level, RandomSource random) {
		if (mode == DynamicFortuneMode.NONE || level <= 0) return Math.min(count, MAX_ITEMS_PER_ENTRY);
		int boundedLevel = Math.min(level, MAX_FORTUNE_LEVEL);
		int multiplier = Math.max(1, random.nextInt(boundedLevel + 2));
		return Math.min(MAX_ITEMS_PER_ENTRY, count * multiplier);
	}

	static int applyExplosionDecay(int count, boolean enabled, Float radius, RandomSource random) {
		if (!enabled || radius == null) return count;
		double survivalChance = Float.isNaN(radius) || radius <= 0.0F
				? 1.0 : Mth.clamp(1.0 / radius, 0.0, 1.0);
		int survived = 0;
		for (int index = 0; index < count; index++) {
			if (random.nextDouble() < survivalChance) survived++;
		}
		return survived;
	}

	private static ItemStack resolveTarget(DynamicContentDefinition owner, DynamicDropEntry entry) {
		if (entry.isSelf()) return DynamicContentObjects.createStack(owner);
		Identifier id = entry.targetId();
		if (entry.targetKind() == DynamicDropTargetKind.DYNAMIC_CONTENT) {
			DynamicContentDefinition target = DynamicContentCatalog.find(id);
			return target == null ? ItemStack.EMPTY : DynamicContentObjects.createStack(target);
		}
		return BuiltInRegistries.ITEM.getOptional(id)
				.filter(item -> !DynamicContentObjects.isCarrierItem(item))
				.map(ItemStack::new)
				.orElse(ItemStack.EMPTY);
	}

	private static void addStacks(List<ItemStack> result, ItemStack template, int count) {
		if (template.isEmpty()) return;
		int maximum = template.getMaxStackSize();
		while (count > 0) {
			int stackCount = Math.min(count, maximum);
			result.add(template.copyWithCount(stackCount));
			count -= stackCount;
		}
	}
}
