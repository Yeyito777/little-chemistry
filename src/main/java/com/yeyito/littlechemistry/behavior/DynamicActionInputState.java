package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side latest-state store for the fixed generated-action input packet. */
public final class DynamicActionInputState {
	private static final ConcurrentHashMap<UUID, DynamicActionInput> INPUTS = new ConcurrentHashMap<>();

	private DynamicActionInputState() {
	}

	public static void update(ServerPlayer player, int mask) {
		if (player == null || (mask & ~0x1F) != 0) return;
		DynamicActionInput input = DynamicActionInput.fromMask(mask);
		if (input.equals(DynamicActionInput.NONE)) INPUTS.remove(player.getUUID());
		else INPUTS.put(player.getUUID(), input);
	}

	public static DynamicActionInput capture(ServerPlayer player) {
		return player == null ? DynamicActionInput.NONE
				: INPUTS.getOrDefault(player.getUUID(), DynamicActionInput.NONE);
	}

	public static void clear(ServerPlayer player) {
		if (player != null) INPUTS.remove(player.getUUID());
	}
}
