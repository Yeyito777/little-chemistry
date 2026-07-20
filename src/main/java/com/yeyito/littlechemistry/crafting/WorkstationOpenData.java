package com.yeyito.littlechemistry.crafting;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicWorkstationJson;
import com.yeyito.littlechemistry.content.DynamicWorkstationSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Full compact opening data so a newly synchronized workstation can build an identical client menu immediately. */
public record WorkstationOpenData(BlockPos position, Identifier contentId, DynamicWorkstationSpec specification) {
	private static final int MAX_SPEC_JSON_BYTES = DynamicWorkstationJson.MAX_SERIALIZED_UTF8_BYTES;
	public static final StreamCodec<RegistryFriendlyByteBuf, WorkstationOpenData> STREAM_CODEC = StreamCodec.of(
			(buffer, value) -> {
				BlockPos.STREAM_CODEC.encode(buffer, value.position);
				Identifier.STREAM_CODEC.encode(buffer, value.contentId);
				ByteBufCodecs.byteArray(MAX_SPEC_JSON_BYTES).encode(buffer,
						DynamicWorkstationJson.encode(value.specification).toString()
								.getBytes(StandardCharsets.UTF_8));
			},
			buffer -> new WorkstationOpenData(
					BlockPos.STREAM_CODEC.decode(buffer),
					Identifier.STREAM_CODEC.decode(buffer),
					DynamicWorkstationJson.decode(JsonParser.parseString(
							decodeUtf8(ByteBufCodecs.byteArray(MAX_SPEC_JSON_BYTES).decode(buffer)))
							.getAsJsonObject()))
	);

	public WorkstationOpenData {
		if (position == null || contentId == null || specification == null) {
			throw new IllegalArgumentException("Complete workstation opening data is required");
		}
		position = position.immutable();
	}

	private static String decodeUtf8(byte[] encoded) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(encoded)).toString();
		} catch (CharacterCodingException error) {
			throw new IllegalArgumentException("Workstation opening data is not valid UTF-8", error);
		}
	}
}
