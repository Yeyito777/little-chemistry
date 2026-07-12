package com.yeyito.littlechemistry.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DynamicContentJson {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private DynamicContentJson() {
	}

	public static byte[] encode(UUID serverId, long revision, List<DynamicContentDefinition> definitions) {
		JsonObject root = new JsonObject();
		root.addProperty("format", 2);
		root.addProperty("serverId", serverId.toString());
		root.addProperty("revision", revision);
		JsonArray entries = new JsonArray();
		for (DynamicContentDefinition definition : definitions) {
			JsonObject entry = new JsonObject();
			entry.addProperty("type", definition.type().serializedName());
			entry.addProperty("name", definition.name());
			entry.addProperty("displayName", definition.displayName());
			entry.addProperty("textureSeed", definition.textureSeed());
			entry.addProperty("textureHash", definition.textureHash());
			entries.add(entry);
		}
		root.add("definitions", entries);
		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	public static Decoded decode(byte[] bytes) {
		JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		int format = root.get("format").getAsInt();
		if (format != 1 && format != 2) {
			throw new IllegalArgumentException("Unsupported dynamic content format");
		}
		UUID serverId = UUID.fromString(root.get("serverId").getAsString());
		long revision = root.get("revision").getAsLong();
		JsonArray encodedDefinitions = root.getAsJsonArray("definitions");
		if (encodedDefinitions.size() > 100_000) {
			throw new IllegalArgumentException("Too many synchronized dynamic content definitions");
		}
		List<DynamicContentDefinition> definitions = new ArrayList<>();
		for (JsonElement element : encodedDefinitions) {
			JsonObject entry = element.getAsJsonObject();
			String name = entry.get("name").getAsString();
			if (!name.matches("[a-z0-9_]{1,64}")) {
				throw new IllegalArgumentException("Invalid dynamic content name in synchronized data");
			}
			String displayName = entry.get("displayName").getAsString();
			if (displayName.isBlank() || displayName.length() > 64
					|| displayName.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("Invalid dynamic content display name in synchronized data");
			}
			long textureSeed = entry.get("textureSeed").getAsLong();
			String textureHash;
			if (format == 2) {
				textureHash = entry.get("textureHash").getAsString();
			} else {
				try {
					textureHash = DynamicTextureAsset.sha256(DynamicTextureAsset.generate(textureSeed));
				} catch (java.io.IOException error) {
					throw new IllegalArgumentException("Could not migrate a legacy dynamic texture", error);
				}
			}
			definitions.add(new DynamicContentDefinition(
					DynamicContentType.fromSerializedName(entry.get("type").getAsString()),
					name,
					displayName,
					textureSeed,
					textureHash
			));
		}
		validateUniqueNames(definitions);
		return new Decoded(serverId, revision, List.copyOf(definitions));
	}

	private static void validateUniqueNames(List<DynamicContentDefinition> definitions) {
		java.util.Set<String> names = new java.util.HashSet<>();
		for (DynamicContentDefinition definition : definitions) {
			if (!names.add(definition.name())) {
				throw new IllegalArgumentException("Duplicate dynamic content name");
			}
		}
	}

	public record Decoded(UUID serverId, long revision, List<DynamicContentDefinition> definitions) {
	}
}
