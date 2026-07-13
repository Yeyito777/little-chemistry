package com.yeyito.littlechemistry.ai.generation;

public enum GenerationModel {
	LUNA("luna", "gpt-5.6-luna", "Luna"),
	TERRA("terra", "gpt-5.6-terra", "Terra"),
	SOL("sol", "gpt-5.6-sol", "Sol");

	private final String serializedName;
	private final String modelId;
	private final String displayName;

	GenerationModel(String serializedName, String modelId, String displayName) {
		this.serializedName = serializedName;
		this.modelId = modelId;
		this.displayName = displayName;
	}

	public String serializedName() {
		return serializedName;
	}

	public String modelId() {
		return modelId;
	}

	public String displayName() {
		return displayName;
	}

	public static GenerationModel parse(String value) {
		for (GenerationModel model : values()) {
			if (model.serializedName.equals(value)) return model;
		}
		throw new IllegalArgumentException("Unknown generation model: " + value);
	}
}
