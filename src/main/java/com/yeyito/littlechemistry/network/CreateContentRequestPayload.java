package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record CreateContentRequestPayload(DynamicContentType contentType, String name) implements CustomPacketPayload {
	public static final int MAX_NAME_LENGTH = 64;
	public static final Type<CreateContentRequestPayload> TYPE = new Type<>(LittleChemistry.id("create_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CreateContentRequestPayload> CODEC =
			CustomPacketPayload.codec(CreateContentRequestPayload::write, CreateContentRequestPayload::read);

	public CreateContentRequestPayload {
		Objects.requireNonNull(contentType, "contentType");
		Objects.requireNonNull(name, "name");
		if (name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid Little Chemistry content name");
		}
	}

	private static CreateContentRequestPayload read(RegistryFriendlyByteBuf buffer) {
		DynamicContentType contentType = buffer.readBoolean() ? DynamicContentType.BLOCK : DynamicContentType.ITEM;
		return new CreateContentRequestPayload(contentType, buffer.readUtf(MAX_NAME_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeBoolean(contentType == DynamicContentType.BLOCK);
		buffer.writeUtf(name, MAX_NAME_LENGTH);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
