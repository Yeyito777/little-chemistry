package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.content.DynamicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$noLockedTableProgress(BlockState state, net.minecraft.world.entity.player.Player player,
			BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> result) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (state.is(Blocks.CRAFTING_TABLE) && level instanceof ServerLevel serverLevel
				&& manager != null && manager.isLocked(serverLevel, pos)) {
			result.setReturnValue(0.0F);
		}
		if (level.getBlockEntity(pos) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstationLocked()) result.setReturnValue(0.0F);
	}

	@Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$protectLockedTableFromExplosion(BlockState state, ServerLevel level, BlockPos pos,
			Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit, CallbackInfo callback) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (state.is(Blocks.CRAFTING_TABLE) && manager != null && manager.isLocked(level, pos)) callback.cancel();
		if (level.getBlockEntity(pos) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstationLocked()) callback.cancel();
	}

	@Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
	private void littleChemistry$dropSharedTableContents(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston, CallbackInfo callback) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (state.is(Blocks.CRAFTING_TABLE) && manager != null) manager.tableRemoved(level, pos);
	}
}
