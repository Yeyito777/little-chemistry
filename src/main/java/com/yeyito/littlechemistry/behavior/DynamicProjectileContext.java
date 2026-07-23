package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

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
}
