package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record DynamicContentPayload(
		UUID serverId,
		long revision,
		byte[] definitionsJson
) implements CustomPacketPayload {
	public static final int MAX_DEFINITIONS_BYTES = 8 * 1024 * 1024;
	public static final Type<DynamicContentPayload> TYPE = new Type<>(LittleChemistry.id("dynamic_content_v4"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DynamicContentPayload> CODEC =
			CustomPacketPayload.codec(DynamicContentPayload::write, DynamicContentPayload::read);

	private static DynamicContentPayload read(RegistryFriendlyByteBuf buffer) {
		return new DynamicContentPayload(
				buffer.readUUID(),
				buffer.readVarLong(),
				buffer.readByteArray(MAX_DEFINITIONS_BYTES)
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(serverId);
		buffer.writeVarLong(revision);
		buffer.writeByteArray(definitionsJson);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
