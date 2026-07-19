package com.yeyito.littlechemistry.particle;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounds generated behavior emissions independently of Minecraft's client-side particle setting.
 * Reservations represent the maximum possible live lifetime rather than just one tick's burst.
 */
final class DynamicParticleEmissionBudget {
	static final int MAX_LIVE_PER_DEFINITION = 64;
	static final int MAX_LIVE_PER_LEVEL = 512;
	static final int MAX_PARTICLES_PER_TICK_PER_DEFINITION = 32;
	static final int MAX_PARTICLES_PER_TICK_PER_LEVEL = 128;
	static final int MAX_CALLS_PER_TICK_PER_DEFINITION = 8;
	static final int MAX_CALLS_PER_TICK_PER_LEVEL = 32;

	private final PriorityQueue<Reservation> reservations = new PriorityQueue<>(
			java.util.Comparator.comparingLong(Reservation::expiresAt));
	private final Map<String, Integer> liveByDefinition = new HashMap<>();
	private final Map<String, Integer> particlesThisTickByDefinition = new HashMap<>();
	private final Map<String, Integer> callsThisTickByDefinition = new HashMap<>();
	private long currentTick = Long.MIN_VALUE;
	private int live;
	private int particlesThisTick;
	private int callsThisTick;

	Reservation reserve(String definitionId, long gameTime, int lifetimeTicks, int requested) {
		if (definitionId == null || lifetimeTicks < 1 || requested < 1) return null;
		advance(gameTime);

		int definitionCalls = callsThisTickByDefinition.getOrDefault(definitionId, 0);
		if (definitionCalls >= MAX_CALLS_PER_TICK_PER_DEFINITION || callsThisTick >= MAX_CALLS_PER_TICK_PER_LEVEL) {
			return null;
		}
		int allowed = Math.min(requested, MAX_LIVE_PER_DEFINITION
				- liveByDefinition.getOrDefault(definitionId, 0));
		allowed = Math.min(allowed, MAX_LIVE_PER_LEVEL - live);
		allowed = Math.min(allowed, MAX_PARTICLES_PER_TICK_PER_DEFINITION
				- particlesThisTickByDefinition.getOrDefault(definitionId, 0));
		allowed = Math.min(allowed, MAX_PARTICLES_PER_TICK_PER_LEVEL - particlesThisTick);
		if (allowed <= 0) return null;

		Reservation reservation = new Reservation(gameTime + lifetimeTicks, definitionId, allowed);
		reservations.add(reservation);
		live += allowed;
		liveByDefinition.merge(definitionId, allowed, Integer::sum);
		particlesThisTick += allowed;
		particlesThisTickByDefinition.merge(definitionId, allowed, Integer::sum);
		callsThisTick++;
		callsThisTickByDefinition.merge(definitionId, 1, Integer::sum);
		return reservation;
	}

	/** Releases the live-particle portion of a reservation when no client received its packet. */
	void refund(Reservation reservation) {
		if (reservation == null || !reservations.remove(reservation)) return;
		live -= reservation.count();
		liveByDefinition.computeIfPresent(reservation.definitionId(), (ignored, count) -> {
			int remaining = count - reservation.count();
			return remaining == 0 ? null : remaining;
		});
	}

	private void advance(long gameTime) {
		while (!reservations.isEmpty() && reservations.peek().expiresAt() <= gameTime) {
			Reservation expired = reservations.remove();
			live -= expired.count();
			liveByDefinition.computeIfPresent(expired.definitionId(), (ignored, count) -> {
				int remaining = count - expired.count();
				return remaining == 0 ? null : remaining;
			});
		}
		if (currentTick != gameTime) {
			currentTick = gameTime;
			particlesThisTick = 0;
			callsThisTick = 0;
			particlesThisTickByDefinition.clear();
			callsThisTickByDefinition.clear();
		}
	}

	static final class Reservation {
		private final long expiresAt;
		private final String definitionId;
		private final int count;

		private Reservation(long expiresAt, String definitionId, int count) {
			this.expiresAt = expiresAt;
			this.definitionId = definitionId;
			this.count = count;
		}

		long expiresAt() {
			return expiresAt;
		}

		String definitionId() {
			return definitionId;
		}

		int count() {
			return count;
		}
	}
}
