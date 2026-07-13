package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.generation.GenerationModel;
import com.yeyito.littlechemistry.content.DynamicContentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record CreateContentRequestPayload(
		DynamicContentType contentType,
		GenerationModel generationModel,
		String name
) implements CustomPacketPayload {
	public static final int MAX_NAME_LENGTH = 64;
	private static final int MAX_KIND_LENGTH = 8;
	private static final int MAX_MODEL_LENGTH = 5;
	public static final Type<CreateContentRequestPayload> TYPE = new Type<>(LittleChemistry.id("create_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CreateContentRequestPayload> CODEC =
			CustomPacketPayload.codec(CreateContentRequestPayload::write, CreateContentRequestPayload::read);

	public CreateContentRequestPayload {
		Objects.requireNonNull(contentType, "contentType");
		Objects.requireNonNull(generationModel, "generationModel");
		Objects.requireNonNull(name, "name");
		if (name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid Little Chemistry content name");
		}
	}

	private static CreateContentRequestPayload read(RegistryFriendlyByteBuf buffer) {
		DynamicContentType contentType = DynamicContentType.fromSerializedName(buffer.readUtf(MAX_KIND_LENGTH));
		GenerationModel generationModel = GenerationModel.parse(buffer.readUtf(MAX_MODEL_LENGTH));
		return new CreateContentRequestPayload(contentType, generationModel, buffer.readUtf(MAX_NAME_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(contentType.serializedName(), MAX_KIND_LENGTH);
		buffer.writeUtf(generationModel.serializedName(), MAX_MODEL_LENGTH);
		buffer.writeUtf(name, MAX_NAME_LENGTH);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
