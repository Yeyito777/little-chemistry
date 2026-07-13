package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenDeletionScreenPayload() implements CustomPacketPayload {
	public static final OpenDeletionScreenPayload INSTANCE = new OpenDeletionScreenPayload();
	public static final Type<OpenDeletionScreenPayload> TYPE = new Type<>(LittleChemistry.id("open_deletion_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDeletionScreenPayload> CODEC =
			CustomPacketPayload.codec(OpenDeletionScreenPayload::write, OpenDeletionScreenPayload::read);

	private static OpenDeletionScreenPayload read(RegistryFriendlyByteBuf buffer) {
		return INSTANCE;
	}

	private void write(RegistryFriendlyByteBuf buffer) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
