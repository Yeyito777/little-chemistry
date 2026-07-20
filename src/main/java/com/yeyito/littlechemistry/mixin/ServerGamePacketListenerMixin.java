package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.AiRecipeMenuAccess;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
	@Shadow @Final private ServerPlayer player;

	@Inject(method = "handlePlaceRecipe", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.AFTER), cancellable = true)
	private void littleChemistry$blockRecipeBookWhileLocked(ServerboundPlaceRecipePacket packet, CallbackInfo callback) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && manager.handleRecipeBookPlacement(
				player, packet.containerId(), packet.recipe(), packet.useMaxItems())) {
			callback.cancel();
			return;
		}
		if (player.containerMenu instanceof AiRecipeMenuAccess access
				&& access.littleChemistry$getRecipeState() == AiRecipeMenuAccess.GENERATING) {
			callback.cancel();
		}
	}
}
