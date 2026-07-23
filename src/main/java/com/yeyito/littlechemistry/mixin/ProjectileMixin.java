package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.content.DynamicProjectileCarrierHooks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes the common native projectile collision path back to the originating generated weapon behavior. */
@Mixin(Projectile.class)
abstract class ProjectileMixin {
	@Inject(method = "hitTargetOrDeflectSelf", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;onHit(Lnet/minecraft/world/phys/HitResult;)V",
			shift = At.Shift.AFTER))
	private void littleChemistry$generatedWeaponImpact(HitResult hit,
			CallbackInfoReturnable<ProjectileDeflection> callback) {
		DynamicProjectileCarrierHooks.projectileImpact((Projectile) (Object) this, hit);
	}
}
