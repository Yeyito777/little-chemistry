package com.yeyito.littlechemistry.network;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded client action-key state used by generated items and entities. */
public record DynamicActionInputPayload(int mask) implements CustomPacketPayload {
	public static final int ALLOWED_MASK = 0x1F;
	public static final Type<DynamicActionInputPayload> TYPE = new Type<>(LittleChemistry.id("action_input"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DynamicActionInputPayload> CODEC =
			CustomPacketPayload.codec(DynamicActionInputPayload::write, DynamicActionInputPayload::read);

	public DynamicActionInputPayload {
		if ((mask & ~ALLOWED_MASK) != 0) throw new IllegalArgumentException("Invalid generated action input mask");
	}

	private static DynamicActionInputPayload read(RegistryFriendlyByteBuf buffer) {
		return new DynamicActionInputPayload(buffer.readUnsignedByte());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeByte(mask);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
