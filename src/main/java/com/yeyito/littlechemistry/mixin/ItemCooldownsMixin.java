package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicCarrierBlockItem;
import com.yeyito.littlechemistry.content.DynamicCarrierItem;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives each virtual content definition its own vanilla cooldown group. */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin {
	@Inject(method = "getCooldownGroup", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$useDynamicContentGroup(ItemStack stack,
			CallbackInfoReturnable<Identifier> callback) {
		if (!(stack.getItem() instanceof DynamicCarrierItem)
				&& !(stack.getItem() instanceof DynamicCarrierBlockItem)) {
			return;
		}

		// Preserve an explicit group so generated behavior can intentionally share a cooldown.
		UseCooldown useCooldown = stack.get(DataComponents.USE_COOLDOWN);
		if (useCooldown != null && useCooldown.cooldownGroup().isPresent()) return;

		Identifier contentId = stack.get(DynamicContentObjects.CONTENT_ID);
		if (contentId == null || !LittleChemistry.MOD_ID.equals(contentId.getNamespace())) return;
		callback.setReturnValue(LittleChemistry.id("dynamic_content/" + contentId.getPath()));
	}
}
