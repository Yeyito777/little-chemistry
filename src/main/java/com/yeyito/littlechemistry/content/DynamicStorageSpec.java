package com.yeyito.littlechemistry.content;

/** Native chest-style storage attached to a generated ordinary item or block. */
public record DynamicStorageSpec(int rows, boolean acceptsContainerItems) {
	public static final int MIN_ROWS = 1;
	public static final int MAX_ROWS = 6;

	public DynamicStorageSpec {
		if (rows < MIN_ROWS || rows > MAX_ROWS) {
			throw new IllegalArgumentException("Generated storage must contain between 1 and 6 rows");
		}
	}

	/** Safe default: container-bearing stacks are gently rejected rather than nested. */
	public DynamicStorageSpec(int rows) {
		this(rows, false);
	}

	public int slots() {
		return rows * 9;
	}
}
