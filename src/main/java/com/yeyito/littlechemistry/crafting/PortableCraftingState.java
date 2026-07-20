package com.yeyito.littlechemistry.crafting;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The non-idle visual state persisted on a Crafting Table on a Stick. */
public enum PortableCraftingState implements StringRepresentable {
	GENERATING("generating"),
	READY("ready");

	public static final Codec<PortableCraftingState> CODEC = StringRepresentable.fromEnum(PortableCraftingState::values);
	public static final StreamCodec<ByteBuf, PortableCraftingState> STREAM_CODEC = ByteBufCodecs.idMapper(
			index -> index == READY.ordinal() ? READY : GENERATING,
			PortableCraftingState::ordinal
	);

	private final String serializedName;

	PortableCraftingState(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
