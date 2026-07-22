package com.yeyito.littlechemistry.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Named runtime textures selected by native item-use and charged-state model properties. */
public record DynamicItemVisuals(List<DynamicItemTexture> states) {
	public static final int MAX_STATES = 32;
	public static final String PULLING_0 = "pulling_0";
	public static final String PULLING_1 = "pulling_1";
	public static final String PULLING_2 = "pulling_2";
	public static final String CHARGED = "charged";
	public static final String CHARGED_FIREWORK = "charged_firework";
	public static final String CAST = "cast";
	public static final String BLOCKING = "blocking";
	public static final String THROWING = "throwing";
	public static final DynamicItemVisuals NONE = new DynamicItemVisuals(List.of());
	private static final Set<String> BOW_STATES = Set.of(PULLING_0, PULLING_1, PULLING_2);
	private static final Set<String> CROSSBOW_REQUIRED_STATES = Set.of(
			PULLING_0, PULLING_1, PULLING_2, CHARGED, CHARGED_FIREWORK);

	public DynamicItemVisuals {
		if (states == null) throw new IllegalArgumentException("Item visual states are required");
		if (states.size() > MAX_STATES) {
			throw new IllegalArgumentException("Items may define at most " + MAX_STATES + " visual states");
		}
		Map<String, DynamicItemTexture> unique = new LinkedHashMap<>();
		for (DynamicItemTexture state : states) {
			if (state == null || unique.putIfAbsent(state.id(), state) != null) {
				throw new IllegalArgumentException("Item visual state IDs must be unique");
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
		return states.stream().map(DynamicItemTexture::hash).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	/** Validates optional persisted states while retaining compatibility with definitions created before this format. */
	public void validateFor(DynamicHeldType heldType) {
		if (heldType == null) throw new IllegalArgumentException("Held type is required for item visuals");
	}

	/** Requires complete native animation frames for newly generated bows and crossbows. */
	public void requireCompleteFor(DynamicHeldType heldType) {
		validateFor(heldType);
		Set<String> required = switch (heldType) {
			case BOW -> BOW_STATES;
			case CROSSBOW -> CROSSBOW_REQUIRED_STATES;
			case ROD -> Set.of(CAST);
			case SHIELD -> Set.of(BLOCKING);
			case TRIDENT -> Set.of(THROWING);
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
