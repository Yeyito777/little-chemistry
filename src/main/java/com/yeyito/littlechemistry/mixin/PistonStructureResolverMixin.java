package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow @Final private Level level;
	@Shadow @Final private List<BlockPos> toPush;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void littleChemistry$doNotMoveLockedTables(CallbackInfoReturnable<Boolean> result) {
		if (!result.getReturnValue() || !(level instanceof ServerLevel serverLevel)) return;
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && toPush.stream().anyMatch(pos -> manager.isLocked(serverLevel, pos))) {
			result.setReturnValue(false);
		}
		if (toPush.stream().anyMatch(pos -> serverLevel.getBlockEntity(pos) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstation())) result.setReturnValue(false);
	}
}
