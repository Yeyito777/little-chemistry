package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import com.yeyito.littlechemistry.content.DynamicBlockAssemblyRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow protected ServerLevel level;

	/** Mining any visible assembly cell is one ordinary break of its authoritative root. */
	@ModifyVariable(method = "destroyBlock", at = @At("HEAD"), argsOnly = true)
	private BlockPos littleChemistry$canonicalizeAssemblyPart(BlockPos pos) {
		DynamicBlockEntity root = DynamicBlockAssemblyRuntime.rootEntity(level, pos);
		return root == null ? pos : root.getBlockPos();
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$protectLockedTable(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
		DynamicBlockEntity root = DynamicBlockAssemblyRuntime.rootEntity(level, pos);
		if (root != null) pos = root.getBlockPos();
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && manager.isLocked(level, pos)) result.setReturnValue(false);
		if ((root != null ? root : level.getBlockEntity(pos)) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstationLocked()) result.setReturnValue(false);
	}
}
