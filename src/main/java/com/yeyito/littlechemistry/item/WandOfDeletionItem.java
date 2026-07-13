package com.yeyito.littlechemistry.item;

import com.yeyito.littlechemistry.network.OpenDeletionScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class WandOfDeletionItem extends Item {
	public WandOfDeletionItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer
				&& ServerPlayNetworking.canSend(serverPlayer, OpenDeletionScreenPayload.TYPE)) {
			ServerPlayNetworking.send(serverPlayer, OpenDeletionScreenPayload.INSTANCE);
		}
		return InteractionResult.SUCCESS;
	}
}
