package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record DynamicAssetRequestPayload(List<String> hashes) implements CustomPacketPayload {
	public static final int MAX_HASHES = 512;
	public static final Type<DynamicAssetRequestPayload> TYPE = new Type<>(LittleChemistry.id("asset_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DynamicAssetRequestPayload> CODEC =
			CustomPacketPayload.codec(DynamicAssetRequestPayload::write, DynamicAssetRequestPayload::read);

	public DynamicAssetRequestPayload {
		hashes = List.copyOf(hashes);
		if (hashes.size() > MAX_HASHES || hashes.stream().anyMatch(hash -> !hash.matches("[a-f0-9]{64}"))) {
			throw new IllegalArgumentException("Invalid Little Chemistry asset request");
		}
	}

	private static DynamicAssetRequestPayload read(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_HASHES) {
			throw new IllegalArgumentException("Too many requested Little Chemistry assets");
		}
		List<String> hashes = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			hashes.add(buffer.readUtf(64));
		}
		return new DynamicAssetRequestPayload(hashes);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(hashes.size());
		for (String hash : hashes) {
			buffer.writeUtf(hash, 64);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
