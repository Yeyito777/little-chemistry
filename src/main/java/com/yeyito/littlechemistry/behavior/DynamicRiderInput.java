package com.yeyito.littlechemistry.behavior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

import java.util.Objects;

/**
 * Authoritative input most recently received from the client controlling a generated entity.
 *
 * <p>Forward and left strafe are positive. The shift flag normally causes vanilla riding to dismount, so generated
 * vehicles should generally use {@link #jump()} for ascent and retain shift as the standard dismount control.</p>
 */
public record DynamicRiderInput(
		float forward,
		float strafe,
		boolean jump,
		boolean shift,
		boolean sprint,
		float yaw,
		float pitch
) {
	public static final DynamicRiderInput NONE = new DynamicRiderInput(
			0.0F, 0.0F, false, false, false, 0.0F, 0.0F);

	/** Captures one immutable server-tick input snapshot for a rider. */
	public static DynamicRiderInput capture(ServerPlayer rider) {
		Objects.requireNonNull(rider, "rider");
		return from(rider.getLastClientInput(), rider.getYRot(), rider.getXRot());
	}

	/** Converts Minecraft's synchronized directional button state into signed movement axes. */
	public static DynamicRiderInput from(Input input, float yaw, float pitch) {
		Objects.requireNonNull(input, "input");
		float forward = axis(input.forward(), input.backward());
		float strafe = axis(input.left(), input.right());
		return new DynamicRiderInput(forward, strafe, input.jump(), input.shift(), input.sprint(), yaw, pitch);
	}

	private static float axis(boolean positive, boolean negative) {
		return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
	}
}
