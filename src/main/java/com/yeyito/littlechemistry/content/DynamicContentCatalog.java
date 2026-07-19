package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The definitions currently visible to this process. On a dedicated server the
 * server manager owns this snapshot; on a client it is replaced by sync packets.
 */
public final class DynamicContentCatalog {
	private static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
	private static volatile Snapshot visible = EMPTY;

	private DynamicContentCatalog() {
	}

	public static List<DynamicContentDefinition> definitions() {
		return visible.definitions();
	}

	public static void replace(List<DynamicContentDefinition> definitions) {
		List<DynamicContentDefinition> ordered = definitions.stream()
				.sorted(Comparator.comparing(DynamicContentDefinition::type)
						.thenComparing(DynamicContentDefinition::name))
				.toList();
		Map<String, DynamicContentDefinition> byName = ordered.stream().collect(Collectors.toUnmodifiableMap(
				DynamicContentDefinition::name,
				Function.identity()
		));
		visible = new Snapshot(ordered, byName);
	}

	public static DynamicContentDefinition find(String name) {
		return visible.definitionsByName().get(name);
	}

	public static DynamicContentDefinition find(Identifier id) {
		return id != null && LittleChemistry.MOD_ID.equals(id.getNamespace())
				? visible.definitionsByName().get(id.getPath()) : null;
	}

	public static void clear() {
		visible = EMPTY;
	}

	private record Snapshot(List<DynamicContentDefinition> definitions,
			Map<String, DynamicContentDefinition> definitionsByName) {
	}
}
