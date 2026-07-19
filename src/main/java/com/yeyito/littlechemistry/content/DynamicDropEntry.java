package com.yeyito.littlechemistry.content;

import net.minecraft.resources.Identifier;

/** One bounded item-stack entry in a generated block's normal drop rules. */
public record DynamicDropEntry(
		DynamicDropTargetKind targetKind,
		String target,
		int minCount,
		int maxCount,
		double chance,
		DynamicFortuneMode fortune
) {
	public static final String SELF = "self";

	public DynamicDropEntry {
		if (targetKind == null) throw new IllegalArgumentException("Drop target kind is required");
		if (target == null || target.isBlank()) throw new IllegalArgumentException("Drop target is required");
		target = target.strip();
		if (targetKind == DynamicDropTargetKind.SELF) {
			if (!SELF.equals(target)) throw new IllegalArgumentException("A self drop must use target=self");
		} else {
			if (target.indexOf(':') <= 0) {
				throw new IllegalArgumentException("Drop target item IDs must include a namespace");
			}
			try {
				Identifier.parse(target);
			} catch (RuntimeException error) {
				throw new IllegalArgumentException("Drop target must be 'self' or a namespaced item ID", error);
			}
		}
		if (minCount < 1 || maxCount < minCount || maxCount > 64) {
			throw new IllegalArgumentException("Drop count must use an ordered range between 1 and 64");
		}
		if (!Double.isFinite(chance) || chance <= 0.0 || chance > 1.0) {
			throw new IllegalArgumentException("Drop chance must be greater than 0 and at most 1");
		}
		if (fortune == null) throw new IllegalArgumentException("Drop Fortune mode is required");
		if (targetKind == DynamicDropTargetKind.DYNAMIC_CONTENT
				&& !"little_chemistry".equals(Identifier.parse(target).getNamespace())) {
			throw new IllegalArgumentException("Dynamic content drop targets must use the little_chemistry namespace");
		}
		if (targetKind == DynamicDropTargetKind.SELF
				&& (minCount != 1 || maxCount != 1 || fortune != DynamicFortuneMode.NONE)) {
			throw new IllegalArgumentException("A self drop must produce exactly one block and cannot be multiplied by Fortune");
		}
	}

	public boolean isSelf() {
		return targetKind == DynamicDropTargetKind.SELF;
	}

	public Identifier targetId() {
		return isSelf() ? null : Identifier.parse(target);
	}
}
