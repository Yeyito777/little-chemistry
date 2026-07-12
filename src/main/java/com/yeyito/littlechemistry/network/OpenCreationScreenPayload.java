package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenCreationScreenPayload() implements CustomPacketPayload {
	public static final OpenCreationScreenPayload INSTANCE = new OpenCreationScreenPayload();
	public static final Type<OpenCreationScreenPayload> TYPE = new Type<>(LittleChemistry.id("open_creation_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreationScreenPayload> CODEC =
			CustomPacketPayload.codec(OpenCreationScreenPayload::write, OpenCreationScreenPayload::read);

	private static OpenCreationScreenPayload read(RegistryFriendlyByteBuf buffer) {
		return INSTANCE;
	}

	private void write(RegistryFriendlyByteBuf buffer) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
