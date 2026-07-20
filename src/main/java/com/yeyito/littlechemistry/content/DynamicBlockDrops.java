package com.yeyito.littlechemistry.content;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Declarative, server-evaluated drops for one generated block definition. */
public record DynamicBlockDrops(
		List<DynamicDropEntry> entries,
		boolean silkTouchDropsSelf,
		boolean explosionDecay
) {
	private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
	public static final DynamicBlockDrops DEFAULT = new DynamicBlockDrops(
			List.of(new DynamicDropEntry(DynamicDropTargetKind.SELF, DynamicDropEntry.SELF,
					1, 1, 1.0, DynamicFortuneMode.NONE)),
			false,
			false
	);

	public DynamicBlockDrops {
		entries = List.copyOf(entries);
		if (entries.isEmpty() || entries.size() > 2) {
			throw new IllegalArgumentException("Block drops require one primary entry and at most one bonus entry");
		}
		Set<String> targets = new HashSet<>();
		for (DynamicDropEntry entry : entries) {
			if (!targets.add(entry.targetKind().serializedName() + ":" + entry.target())) {
				throw new IllegalArgumentException("Primary and bonus drops must use different targets");
			}
		}
	}

	/** Dynamic definitions referenced by this table, excluding its owning block's self target. */
	public Set<String> referencedDynamicNames() {
		Set<String> names = new HashSet<>();
		for (DynamicDropEntry entry : entries) {
			if (entry.targetKind() == DynamicDropTargetKind.DYNAMIC_CONTENT) {
				names.add(entry.targetId().getPath());
			}
		}
		return Set.copyOf(names);
	}

	/**
	 * Resolves every external target against a stable catalog snapshot and the immutable item registry.
	 * New definitions require every target; persisted definitions retain unresolved targets for world compatibility.
	 */
	public void validateAvailableTargets(Function<String, DynamicContentDefinition> dynamicLookup) {
		validateTargets(null, dynamicLookup, true);
	}

	public void validateNewTargets(String ownerName, Function<String, DynamicContentDefinition> dynamicLookup) {
		validateTargets(ownerName, dynamicLookup, true);
	}

	private void validatePersistedTargets(String ownerName,
			Function<String, DynamicContentDefinition> dynamicLookup) {
		validateTargets(ownerName, dynamicLookup, false);
	}

	private void validateTargets(String ownerName, Function<String, DynamicContentDefinition> dynamicLookup,
			boolean requireAvailable) {
		for (DynamicDropEntry entry : entries) {
			if (entry.isSelf()) continue;
			Identifier id = entry.targetId();
			if (entry.targetKind() == DynamicDropTargetKind.DYNAMIC_CONTENT) {
				if (ownerName != null && id.getPath().equals(ownerName)) {
					throw new IllegalArgumentException("Use target=self instead of referring to the generated block by ID");
				}
				DynamicContentDefinition target = dynamicLookup.apply(id.getPath());
				if (target == null) {
					if (requireAvailable) throw new IllegalArgumentException("Unknown generated drop target: " + id);
					LOGGER.warn("Dynamic block '{}' has unresolved generated drop target {}; the entry will remain inactive",
							ownerName, id);
				} else if (requireAvailable && entry.fortune() != DynamicFortuneMode.NONE
						&& logicalMaximumStack(target) == 1) {
					throw new IllegalArgumentException("ore_like Fortune requires a stackable generated drop target: " + id);
				}
			} else {
				var target = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (target == null || target == Items.AIR) {
					if (requireAvailable) throw new IllegalArgumentException("Unknown registered item drop target: " + id);
					LOGGER.warn("Dynamic block '{}' has unresolved registered drop target {}; the entry will remain inactive",
							ownerName, id);
				} else if (DynamicContentObjects.isCarrierItem(target)) {
					if (requireAvailable) {
						throw new IllegalArgumentException("Dynamic carrier infrastructure cannot be a registered drop target: " + id);
					}
					LOGGER.warn("Dynamic block '{}' refers to carrier infrastructure {}; the entry will remain inactive",
							ownerName, id);
				}
			}
		}
	}

	private static int logicalMaximumStack(DynamicContentDefinition definition) {
		return switch (definition.type()) {
			case BLOCK -> 64;
			case ITEM -> definition.item().maxStack();
			case ARMOR -> 1;
			case ENTITY -> 64;
		};
	}

	public static void validateCatalog(List<DynamicContentDefinition> definitions) {
		Map<String, DynamicContentDefinition> byName = definitions.stream().collect(
				java.util.stream.Collectors.toUnmodifiableMap(DynamicContentDefinition::name, definition -> definition));
		for (DynamicContentDefinition definition : definitions) {
			if (definition.block() != null) {
				definition.block().drops().validatePersistedTargets(definition.name(), byName::get);
			}
		}
	}
}
