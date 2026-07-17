package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.crafting.AiCraftingMenuAccess;
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

	@Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$blockRecipeBookWhileLocked(ServerboundPlaceRecipePacket packet, CallbackInfo callback) {
		if (player.containerMenu instanceof AiCraftingMenuAccess access
				&& access.littleChemistry$getSharedTable() != null
				&& access.littleChemistry$getSharedTable().isLocked()) {
			callback.cancel();
		}
	}
}
