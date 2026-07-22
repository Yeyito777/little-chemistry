package com.yeyito.littlechemistry.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Registered synchronized component infrastructure for bounded generated per-stack state. */
public final class DynamicRuntimeStateComponents {
	public static final int MAX_ENCODED_LENGTH = 16_384;
	public static DataComponentType<String> ITEM_STATE;

	private DynamicRuntimeStateComponents() {
	}

	public static void register() {
		Codec<String> codec = Codec.STRING.validate(value -> value.length() <= MAX_ENCODED_LENGTH
				? DataResult.success(value) : DataResult.error(() -> "Generated item state is too large"));
		ITEM_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
				LittleChemistry.id("generated_item_state"), DataComponentType.<String>builder()
						.persistent(codec)
						.networkSynchronized(ByteBufCodecs.stringUtf8(MAX_ENCODED_LENGTH))
						.build());
	}
}
