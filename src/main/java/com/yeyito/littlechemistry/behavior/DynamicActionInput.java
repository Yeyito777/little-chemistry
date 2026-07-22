package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;

/** Generic synchronized action channels available to every generated behavior. */
public record DynamicActionInput(
		boolean primary,
		boolean secondary,
		boolean ascend,
		boolean descend,
		boolean modeSwitch
) {
	public static final DynamicActionInput NONE = new DynamicActionInput(false, false, false, false, false);

	public static DynamicActionInput capture(ServerPlayer player) {
		return DynamicActionInputState.capture(player);
	}

	static DynamicActionInput fromMask(int mask) {
		return new DynamicActionInput((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0,
				(mask & 8) != 0, (mask & 16) != 0);
	}
}
