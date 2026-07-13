package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record DeleteContentRequestPayload(List<String> names) implements CustomPacketPayload {
	public static final int MAX_NAMES = 100_000;
	public static final int MAX_ENCODED_BYTES = 8 * 1024 * 1024;
	public static final Type<DeleteContentRequestPayload> TYPE = new Type<>(LittleChemistry.id("delete_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DeleteContentRequestPayload> CODEC =
			CustomPacketPayload.codec(DeleteContentRequestPayload::write, DeleteContentRequestPayload::read);

	public DeleteContentRequestPayload {
		names = List.copyOf(names);
		if (names.isEmpty() || names.size() > MAX_NAMES
				|| names.stream().anyMatch(name -> name == null || !name.matches("[a-z0-9_]{1,64}"))
				|| new HashSet<>(names).size() != names.size()) {
			throw new IllegalArgumentException("Invalid Little Chemistry deletion selection");
		}
	}

	private static DeleteContentRequestPayload read(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 1 || count > MAX_NAMES) {
			throw new IllegalArgumentException("Invalid Little Chemistry deletion count");
		}
		List<String> names = new ArrayList<>(count);
		for (int index = 0; index < count; index++) names.add(buffer.readUtf(64));
		return new DeleteContentRequestPayload(names);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(names.size());
		for (String name : names) buffer.writeUtf(name, 64);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
