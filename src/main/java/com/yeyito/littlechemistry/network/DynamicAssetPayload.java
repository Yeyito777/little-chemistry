package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DynamicAssetPayload(String hash, byte[] pngBytes) implements CustomPacketPayload {
	public static final Type<DynamicAssetPayload> TYPE = new Type<>(LittleChemistry.id("asset"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DynamicAssetPayload> CODEC =
			CustomPacketPayload.codec(DynamicAssetPayload::write, DynamicAssetPayload::read);

	public DynamicAssetPayload {
		if (!hash.matches("[a-f0-9]{64}") || pngBytes.length > DynamicTextureAsset.MAX_ENCODED_BYTES) {
			throw new IllegalArgumentException("Invalid Little Chemistry asset payload");
		}
	}

	private static DynamicAssetPayload read(RegistryFriendlyByteBuf buffer) {
		return new DynamicAssetPayload(
				buffer.readUtf(64),
				buffer.readByteArray(DynamicTextureAsset.MAX_ENCODED_BYTES)
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(hash, 64);
		buffer.writeByteArray(pngBytes);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
