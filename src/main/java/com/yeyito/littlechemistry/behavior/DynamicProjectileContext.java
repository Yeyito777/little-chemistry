package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/** Server-side context for a projectile created by a generated bow or crossbow. */
public record DynamicProjectileContext(
		ServerLevel level,
		LivingEntity shooter,
		ItemStack weapon,
		ItemStack ammunition,
		Projectile vanillaProjectile,
		boolean critical,
		DynamicContentDefinition definition
) {
	public DynamicProjectileContext {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(shooter, "shooter");
		weapon = Objects.requireNonNull(weapon, "weapon").copy();
		ammunition = Objects.requireNonNull(ammunition, "ammunition").copy();
		Objects.requireNonNull(vanillaProjectile, "vanillaProjectile");
		Objects.requireNonNull(definition, "definition");
	}

	/**
	 * Prepares any compatible projectile as the outgoing replacement. Minecraft's native weapon code applies final launch
	 * velocity, spread, durability, and ammunition consumption to the returned projectile afterward. Effects applied while
	 * constructing the discarded vanilla projectile are not automatically copied to a replacement.
	 */
	public <T extends Projectile> T replacement(T projectile) {
		Objects.requireNonNull(projectile, "projectile");
		if (projectile.level() != level) {
			throw new IllegalArgumentException("Replacement projectile must belong to the firing level");
		}
		projectile.setOwner(shooter);
		projectile.setPos(vanillaProjectile.getX(), vanillaProjectile.getY(), vanillaProjectile.getZ());
		return projectile;
	}

	/** Replaces the outgoing shot with a native angled firework, using loaded firework data when available. */
	public FireworkRocketEntity firework() {
		ItemStack rocket = ammunition.is(Items.FIREWORK_ROCKET)
				? ammunition.copyWithCount(1) : new ItemStack(Items.FIREWORK_ROCKET);
		return firework(rocket);
	}

	/** Replaces the outgoing shot with a native angled firework carrying caller-authored firework components. */
	public FireworkRocketEntity firework(ItemStack rocket) {
		Objects.requireNonNull(rocket, "rocket");
		if (!rocket.is(Items.FIREWORK_ROCKET)) {
			throw new IllegalArgumentException("Firework projectile data must be carried by a firework rocket stack");
		}
		return replacement(new FireworkRocketEntity(level, rocket.copyWithCount(1), shooter,
				vanillaProjectile.getX(), vanillaProjectile.getY(), vanillaProjectile.getZ(), true));
	}
}
