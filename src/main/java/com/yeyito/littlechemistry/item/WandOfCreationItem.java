package com.yeyito.littlechemistry.item;

import com.yeyito.littlechemistry.network.OpenCreationScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class WandOfCreationItem extends Item {
	public WandOfCreationItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer
				&& ServerPlayNetworking.canSend(serverPlayer, OpenCreationScreenPayload.TYPE)) {
			ServerPlayNetworking.send(serverPlayer, OpenCreationScreenPayload.INSTANCE);
		}

		return InteractionResult.SUCCESS;
	}
}
