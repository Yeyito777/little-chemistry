package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicProjectileCarrierHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Live, server-authoritative context for custom projectile-weapon use ticks and release. */
public record DynamicProjectileWeaponContext(
		ServerLevel level,
		LivingEntity shooter,
		InteractionHand hand,
		ItemStack weapon,
		ItemStack ammunition,
		int ticksUsed,
		int ticksRemaining,
		DynamicContentDefinition definition
) {
	public DynamicProjectileWeaponContext {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(shooter, "shooter");
		Objects.requireNonNull(hand, "hand");
		Objects.requireNonNull(weapon, "weapon");
		ammunition = ammunition == null ? ItemStack.EMPTY : ammunition;
		Objects.requireNonNull(definition, "definition");
		if (ticksUsed < 0 || ticksRemaining < 0) throw new IllegalArgumentException("Projectile use ticks cannot be negative");
	}

	/** Consumes the selected live ammunition stack, preserving creative/infinite materials. */
	public boolean consumeAmmunition(int count) {
		if (count < 0 || count > 64) throw new IllegalArgumentException("Ammunition consumption must be between 0 and 64");
		if (count == 0 || shooter.hasInfiniteMaterials()) return true;
		if (ammunition.isEmpty() || ammunition.getCount() < count) return false;
		ammunition.shrink(count);
		return true;
	}

	/** Applies caller-chosen durability cost; zero is valid for a non-damaging custom shot. */
	public void damageWeapon(int amount) {
		if (amount < 0 || amount > 1_024) throw new IllegalArgumentException("Projectile durability cost is out of range");
		if (amount > 0) weapon.hurtAndBreak(amount, shooter, hand.asEquipmentSlot());
	}

	/** Marks a caller-created projectile so the generated impact callback follows it. */
	public <T extends Projectile> T markOrigin(T projectile) {
		Objects.requireNonNull(projectile, "projectile");
		if (projectile.level() != level) throw new IllegalArgumentException("Projectile belongs to a different level");
		projectile.setOwner(shooter);
		DynamicProjectileCarrierHooks.markProjectile(projectile, definition);
		return projectile;
	}

	/** Convenience launch path; generated Java remains free to configure and add a projectile directly instead. */
	public <T extends Projectile> T launch(T projectile, float power, float uncertainty) {
		if (!Float.isFinite(power) || power < 0 || power > 16 || !Float.isFinite(uncertainty)
				|| uncertainty < 0 || uncertainty > 64) {
			throw new IllegalArgumentException("Projectile launch power or uncertainty is out of range");
		}
		markOrigin(projectile);
		projectile.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
		projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0, power, uncertainty);
		level.addFreshEntity(projectile);
		return projectile;
	}
}
