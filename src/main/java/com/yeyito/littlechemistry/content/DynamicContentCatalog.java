package com.yeyito.littlechemistry.content;

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
	private static volatile List<DynamicContentDefinition> visibleDefinitions = List.of();
	private static volatile Map<String, DynamicContentDefinition> definitionsByName = Map.of();

	private DynamicContentCatalog() {
	}

	public static List<DynamicContentDefinition> definitions() {
		return visibleDefinitions;
	}

	public static void replace(List<DynamicContentDefinition> definitions) {
		visibleDefinitions = definitions.stream()
				.sorted(Comparator.comparing(DynamicContentDefinition::type)
						.thenComparing(DynamicContentDefinition::name))
				.toList();
		definitionsByName = visibleDefinitions.stream().collect(Collectors.toUnmodifiableMap(
				DynamicContentDefinition::name,
				Function.identity()
		));
	}

	public static DynamicContentDefinition find(String name) {
		return definitionsByName.get(name);
	}

	public static void clear() {
		visibleDefinitions = List.of();
		definitionsByName = Map.of();
	}
}
