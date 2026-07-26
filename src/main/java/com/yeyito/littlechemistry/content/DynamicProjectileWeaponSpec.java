package com.yeyito.littlechemistry.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * Synchronized projectile-weapon choices needed by both client prediction and server behavior.
 *
 * <p>{@link DynamicProjectileMechanics#NATIVE} is the convenient default. {@code CUSTOM} keeps the native carrier pose
 * and state artwork but lets generated Java own the action through projectile-weapon callbacks.</p>
 */
public record DynamicProjectileWeaponSpec(
		DynamicProjectileMechanics mechanics,
		boolean startUsing,
		int useDurationTicks,
		ItemUseAnimation useAnimation,
		boolean useOnRelease,
		List<String> ammunition,
		String creativeDefaultAmmunition,
		int durability
) {
	public DynamicProjectileWeaponSpec {
		if (mechanics == null || useAnimation == null) {
			throw new IllegalArgumentException("Projectile mechanics and use animation are required");
		}
		if (useDurationTicks < 1 || useDurationTicks > 72_000) {
			throw new IllegalArgumentException("Projectile use duration must be between 1 and 72000 ticks");
		}
		if (durability < 0 || durability > 100_000) {
			throw new IllegalArgumentException("Projectile weapon durability must be between 0 and 100000");
		}
		ammunition = List.copyOf(ammunition);
		if (ammunition.size() > 32) throw new IllegalArgumentException("Projectile weapons may define at most 32 ammunition matchers");
		for (String matcher : ammunition) validateMatcher(matcher);
		if (creativeDefaultAmmunition != null) {
			parseIdentifier(creativeDefaultAmmunition, "Creative default ammunition");
		}
		if (mechanics == DynamicProjectileMechanics.NATIVE && !startUsing) {
			throw new IllegalArgumentException("Native projectile mechanics must start the native use lifecycle");
		}
	}

	public static DynamicProjectileWeaponSpec nativeBow() {
		return new DynamicProjectileWeaponSpec(DynamicProjectileMechanics.NATIVE, true, 72_000,
				ItemUseAnimation.BOW, false, List.of(), "minecraft:arrow", DynamicHeldType.BOW.nativeDurability());
	}

	public static DynamicProjectileWeaponSpec nativeCrossbow() {
		return new DynamicProjectileWeaponSpec(DynamicProjectileMechanics.NATIVE, true, 72_000,
				ItemUseAnimation.CROSSBOW, true, List.of(), "minecraft:arrow",
				DynamicHeldType.CROSSBOW.nativeDurability());
	}

	/** Empty matcher lists mean native ammo for the native route and no required ammo for the custom route. */
	public boolean overridesAmmunition() {
		return !ammunition.isEmpty();
	}

	public boolean matchesAmmunition(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		for (String matcher : ammunition) {
			if (matcher.startsWith("#")) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(matcher.substring(1)));
				if (stack.is(tag)) return true;
			} else if (matcher.startsWith("dynamic:")) {
				Identifier dynamic = stack.get(DynamicContentObjects.CONTENT_ID);
				String wanted = matcher.substring("dynamic:".length());
				if (dynamic != null && (dynamic.toString().equals(wanted) || dynamic.getPath().equals(wanted))) return true;
			} else if (stack.is(BuiltInRegistries.ITEM.getValue(Identifier.parse(matcher)))) {
				return true;
			}
		}
		return false;
	}

	public ItemStack creativeDefaultStack() {
		if (creativeDefaultAmmunition == null) return ItemStack.EMPTY;
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(creativeDefaultAmmunition))
				.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	private static void validateMatcher(String matcher) {
		if (matcher == null || matcher.isBlank() || matcher.length() > 128) {
			throw new IllegalArgumentException("Projectile ammunition matcher is invalid");
		}
		if (matcher.startsWith("#")) {
			parseIdentifier(matcher.substring(1), "Projectile ammunition tag");
		} else if (matcher.startsWith("dynamic:")) {
			String value = matcher.substring("dynamic:".length());
			if (!value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]{1,96}")) {
				throw new IllegalArgumentException("Dynamic projectile ammunition matcher is invalid: " + matcher);
			}
		} else {
			parseIdentifier(matcher, "Projectile ammunition item");
		}
	}

	private static Identifier parseIdentifier(String value, String description) {
		try {
			return Identifier.parse(value);
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException(description + " is not a valid resource identifier: " + value, invalid);
		}
	}
}
