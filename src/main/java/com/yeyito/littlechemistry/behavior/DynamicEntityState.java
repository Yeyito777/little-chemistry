package com.yeyito.littlechemistry.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded per-entity state that survives world saves independently of the shared behavior instance. */
public final class DynamicEntityState {
	private static final int MAX_ENTRIES = 64;
	private static final int MAX_KEY_LENGTH = 48;
	private static final int MAX_VALUE_LENGTH = 1_024;
	private static final int MAX_ENCODED_LENGTH = 16_384;
	private final Map<String, String> values = new LinkedHashMap<>();

	public String getString(String key, String fallback) {
		return values.getOrDefault(validKey(key), fallback);
	}

	public void setString(String key, String value) {
		String validKey = validKey(key);
		if (value == null) {
			values.remove(validKey);
			return;
		}
		if (value.length() > MAX_VALUE_LENGTH || value.chars().anyMatch(character -> character == 0)) {
			throw new IllegalArgumentException("Entity state values may contain at most " + MAX_VALUE_LENGTH + " characters");
		}
		if (!values.containsKey(validKey) && values.size() >= MAX_ENTRIES) {
			throw new IllegalStateException("Entity state has reached its " + MAX_ENTRIES + "-entry limit");
		}
		String previous = values.put(validKey, value);
		if (encode().length() > MAX_ENCODED_LENGTH) {
			if (previous == null) values.remove(validKey); else values.put(validKey, previous);
			throw new IllegalStateException("Entity state exceeds its encoded size limit");
		}
	}

	public int getInt(String key, int fallback) {
		try {
			return Integer.parseInt(getString(key, Integer.toString(fallback)));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public void setInt(String key, int value) {
		setString(key, Integer.toString(value));
	}

	public double getDouble(String key, double fallback) {
		try {
			double value = Double.parseDouble(getString(key, Double.toString(fallback)));
			return Double.isFinite(value) ? value : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	public void setDouble(String key, double value) {
		if (!Double.isFinite(value)) throw new IllegalArgumentException("Entity state numbers must be finite");
		setString(key, Double.toString(value));
	}

	public boolean getBoolean(String key, boolean fallback) {
		String value = getString(key, Boolean.toString(fallback));
		return value.equalsIgnoreCase("true") ? true : value.equalsIgnoreCase("false") ? false : fallback;
	}

	public void setBoolean(String key, boolean value) {
		setString(key, Boolean.toString(value));
	}

	public void remove(String key) {
		values.remove(validKey(key));
	}

	public boolean contains(String key) {
		return values.containsKey(validKey(key));
	}

	public Map<String, String> snapshot() {
		return Map.copyOf(values);
	}

	public String encode() {
		JsonObject encoded = new JsonObject();
		values.forEach(encoded::addProperty);
		return encoded.toString();
	}

	public void decode(String encoded) {
		values.clear();
		if (encoded == null || encoded.isBlank()) return;
		if (encoded.length() > MAX_ENCODED_LENGTH) throw new IllegalArgumentException("Saved entity state is too large");
		JsonObject object = JsonParser.parseString(encoded).getAsJsonObject();
		if (object.size() > MAX_ENTRIES) throw new IllegalArgumentException("Saved entity state has too many entries");
		for (var entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("Saved entity state values must be strings");
			}
			setString(entry.getKey(), value.getAsString());
		}
	}

	private static String validKey(String key) {
		if (key == null || !key.matches("[A-Za-z0-9_.-]{1," + MAX_KEY_LENGTH + "}")) {
			throw new IllegalArgumentException("Entity state keys may contain letters, numbers, '.', '_', and '-'");
		}
		return key;
	}
}
