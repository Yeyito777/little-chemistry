package com.yeyito.littlechemistry.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authored textures selected by native bow/crossbow use and charged-state model properties. */
public record DynamicItemVisuals(List<DynamicItemTexture> states) {
	public static final String PULLING_0 = "pulling_0";
	public static final String PULLING_1 = "pulling_1";
	public static final String PULLING_2 = "pulling_2";
	public static final String CHARGED = "charged";
	public static final String CHARGED_FIREWORK = "charged_firework";
	private static final Set<String> BOW_STATES = Set.of(PULLING_0, PULLING_1, PULLING_2);
	private static final Set<String> CROSSBOW_STATES = Set.of(
			PULLING_0, PULLING_1, PULLING_2, CHARGED, CHARGED_FIREWORK);
	public static final DynamicItemVisuals NONE = new DynamicItemVisuals(List.of());

	public DynamicItemVisuals {
		if (states == null) throw new IllegalArgumentException("Item visual states are required");
		if (states.size() > CROSSBOW_STATES.size()) {
			throw new IllegalArgumentException("Items may define at most five visual states");
		}
		Map<String, DynamicItemTexture> unique = new LinkedHashMap<>();
		Set<String> hashes = new java.util.HashSet<>();
		for (DynamicItemTexture state : states) {
			if (state == null || unique.putIfAbsent(state.id(), state) != null) {
				throw new IllegalArgumentException("Item visual state IDs must be unique");
			}
			if (!hashes.add(state.hash())) {
				throw new IllegalArgumentException("Item visual state textures must be visually distinct");
			}
		}
		states = List.copyOf(unique.values());
	}

	public boolean isEmpty() {
		return states.isEmpty();
	}

	public DynamicItemTexture state(String id) {
		for (DynamicItemTexture state : states) if (state.id().equals(id)) return state;
		return null;
	}

	public String textureHash(String id, String fallback) {
		DynamicItemTexture state = state(id);
		return state == null ? fallback : state.hash();
	}

	public Set<String> textureHashes() {
		return states.stream().map(DynamicItemTexture::hash)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	/** Allows empty state sets for definitions created before stateful projectile artwork. */
	public void validateFor(DynamicHeldType heldType) {
		Set<String> allowed = switch (heldType) {
			case BOW -> BOW_STATES;
			case CROSSBOW -> CROSSBOW_STATES;
			default -> Set.of();
		};
		for (DynamicItemTexture state : states) {
			if (!allowed.contains(state.id())) {
				throw new IllegalArgumentException("Held type " + heldType.serializedName()
						+ " does not support item visual state " + state.id());
			}
		}
	}

	/** Requires complete native animation frames for newly generated bows and crossbows. */
	public void requireCompleteFor(DynamicHeldType heldType) {
		validateFor(heldType);
		Set<String> required = switch (heldType) {
			case BOW -> BOW_STATES;
			case CROSSBOW -> CROSSBOW_STATES;
			default -> Set.of();
		};
		Set<String> actual = states.stream().map(DynamicItemTexture::id)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		if (!actual.containsAll(required)) {
			Set<String> missing = new java.util.TreeSet<>(required);
			missing.removeAll(actual);
			throw new IllegalArgumentException("Held type " + heldType.serializedName()
					+ " requires item visual states " + missing);
		}
	}
}
