package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record CreateContentRequestPayload(
		DynamicContentType contentType,
		DynamicArmorSlot armorSlot,
		String name
) implements CustomPacketPayload {
	public static final int MAX_NAME_LENGTH = 64;
	private static final int MAX_KIND_LENGTH = 8;
	public static final Type<CreateContentRequestPayload> TYPE = new Type<>(LittleChemistry.id("create_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CreateContentRequestPayload> CODEC =
			CustomPacketPayload.codec(CreateContentRequestPayload::write, CreateContentRequestPayload::read);

	public CreateContentRequestPayload {
		Objects.requireNonNull(contentType, "contentType");
		Objects.requireNonNull(name, "name");
		if ((contentType == DynamicContentType.ARMOR) != (armorSlot != null)) {
			throw new IllegalArgumentException("Armor creation must specify head, chest, leggings, or boots");
		}
		if (name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid Little Chemistry content name");
		}
	}

	public CreateContentRequestPayload(DynamicContentType contentType, String name) {
		this(contentType, null, name);
	}

	private static CreateContentRequestPayload read(RegistryFriendlyByteBuf buffer) {
		DynamicContentType contentType = DynamicContentType.fromSerializedName(buffer.readUtf(MAX_KIND_LENGTH));
		String encodedSlot = buffer.readUtf(MAX_KIND_LENGTH);
		DynamicArmorSlot armorSlot = encodedSlot.isEmpty() ? null : DynamicArmorSlot.parse(encodedSlot);
		return new CreateContentRequestPayload(contentType, armorSlot, buffer.readUtf(MAX_NAME_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(contentType.serializedName(), MAX_KIND_LENGTH);
		buffer.writeUtf(armorSlot == null ? "" : armorSlot.serializedName(), MAX_KIND_LENGTH);
		buffer.writeUtf(name, MAX_NAME_LENGTH);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
