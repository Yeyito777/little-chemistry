package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow protected ServerLevel level;

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$protectLockedTable(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && manager.isLocked(level, pos)) result.setReturnValue(false);
		if (level.getBlockEntity(pos) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstationLocked()) result.setReturnValue(false);
	}
}
