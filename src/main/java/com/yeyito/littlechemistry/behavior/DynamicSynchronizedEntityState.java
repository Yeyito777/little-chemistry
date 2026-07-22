package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicCarrierEntity;

import java.util.Map;
import java.util.Objects;

/** Persistent state whose bounded encoded snapshot is also synchronized to entity-tracking clients. */
public final class DynamicSynchronizedEntityState {
	private final DynamicCarrierEntity entity;

	public DynamicSynchronizedEntityState(DynamicCarrierEntity entity) {
		this.entity = Objects.requireNonNull(entity, "entity");
	}

	public String getString(String key, String fallback) {
		return entity.synchronizedStateValue(key, fallback);
	}

	public void setString(String key, String value) {
		entity.setSynchronizedStateValue(key, value);
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
		if (!Double.isFinite(value)) throw new IllegalArgumentException("Entity synchronized state numbers must be finite");
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
		setString(key, null);
	}

	public Map<String, String> snapshot() {
		return entity.synchronizedStateSnapshot();
	}
}
