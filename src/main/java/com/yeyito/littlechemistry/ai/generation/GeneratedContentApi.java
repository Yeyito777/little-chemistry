package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicBlockDrops;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockModelFace;
import com.yeyito.littlechemistry.content.DynamicBlockTexture;
import com.yeyito.littlechemistry.content.DynamicBlockUv;
import com.yeyito.littlechemistry.content.DynamicParticleFrame;
import com.yeyito.littlechemistry.content.DynamicItemTexture;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationParticleEffect;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Small, general-purpose source helpers for generated factories; all runtime records remain directly available. */
public final class GeneratedContentApi {
	private GeneratedContentApi() {
	}

	public static DynamicTextureSpec texture(List<String> palette, String... rows) {
		return new DynamicTextureSpec(palette, List.of(rows));
	}

	public static DynamicBlockTexture modelTexture(String id, DynamicTextureSpec texture) throws IOException {
		return new DynamicBlockTexture(id, DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	public static DynamicItemTexture itemTexture(String id, DynamicTextureSpec texture) throws IOException {
		return new DynamicItemTexture(id, DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	public static DynamicParticleFrame particleFrame(DynamicTextureSpec texture) throws IOException {
		return new DynamicParticleFrame(DynamicTextureAsset.sha256(texture.renderPng()), texture);
	}

	/** Standard AI-crafting-table motion profile with an authored vanilla or {@code custom:<id>} particle. */
	public static DynamicWorkstationParticleEffect workstationParticle(String particle) {
		return new DynamicWorkstationParticleEffect(particle, 2, 0.32, 0.08, 0.04);
	}

	public static DynamicBlockModelFace face(String textureId) {
		return new DynamicBlockModelFace(textureId, null);
	}

	/** Explicit face crop in Minecraft's normalized 0-16 model UV space. */
	public static DynamicBlockModelFace face(String textureId, float u0, float v0, float u1, float v1) {
		return new DynamicBlockModelFace(textureId, new DynamicBlockUv(u0, v0, u1, v1));
	}

	/** Maps the complete authored texture to one face rather than taking the cuboid-coordinate default crop. */
	public static DynamicBlockModelFace fullFace(String textureId) {
		return face(textureId, 0, 0, 16, 16);
	}

	public static Map<Direction, DynamicBlockModelFace> uniformFaces(String textureId) {
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) faces.put(direction, face(textureId));
		return faces;
	}

	/** Maps the complete authored texture independently onto every cuboid face. */
	public static Map<Direction, DynamicBlockModelFace> fullFaces(String textureId) {
		EnumMap<Direction, DynamicBlockModelFace> faces = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.values()) faces.put(direction, fullFace(textureId));
		return faces;
	}

	public static DynamicBlockModel presetModel(DynamicBlockTexture texture) {
		return new DynamicBlockModel(List.of(texture), texture.id(), uniformFaces(texture.id()), List.of());
	}

	public static DynamicBlockDrops selfDrops() {
		return DynamicBlockDrops.DEFAULT;
	}

	public static Identifier id(String value) {
		return Identifier.parse(value);
	}

	public static JsonObject json(String value) {
		return JsonParser.parseString(value).getAsJsonObject();
	}
}
