package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.client.RuntimeTextureParticle;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	protected ClientLevel level;

	@Inject(method = "makeParticle", at = @At("HEAD"), cancellable = true)
	private <T extends ParticleOptions> void littleChemistry$dynamicItemParticle(T options,
			double x, double y, double z, double xa, double ya, double za,
			CallbackInfoReturnable<Particle> callback) {
		if (!(options instanceof ItemParticleOption itemParticle)) return;
		ItemStack stack = itemParticle.getItem().create();
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition == null) return;
		callback.setReturnValue(RuntimeTextureParticle.item(level, x, y, z, xa, ya, za, definition.textureHash()));
	}
}
