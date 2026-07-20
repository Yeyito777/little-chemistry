package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.AuthConfig;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ContentGenerationService {
	private static final String GENERATION_REASONING_EFFORT = "medium";
	private static final ExecutorService GENERATION_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
	private static final Map<GenerationKey, ActiveJob> ACTIVE = new ConcurrentHashMap<>();

	private ContentGenerationService() {
	}

	public static boolean request(ServerPlayer player, DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName) {
		return request(player, type, requestedArmorSlot, requestedName, GenerationModel.SOL);
	}

	public static boolean request(ServerPlayer player, DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName, GenerationModel generationModel) {
		DynamicContentManager manager = DynamicContentManager.active();
		if (manager == null) {
			player.sendSystemMessage(error("Dynamic content is not available yet."));
			return false;
		}
		String displayName;
		String contentName;
		try {
			if (type != DynamicContentType.ARMOR && requestedArmorSlot != null) {
				throw new IllegalArgumentException("Only armor content may specify an armor slot.");
			}
			displayName = DynamicContentManager.normalizeDisplayName(requestedName);
			contentName = DynamicContentManager.normalizeIdentifier(displayName);
			if (manager.containsName(displayName)) {
				player.sendSystemMessage(error("Dynamic content named '" + contentName + "' already exists."));
				return false;
			}
		} catch (IllegalArgumentException invalid) {
			player.sendSystemMessage(error(safeMessage(invalid)));
			return false;
		}

		UUID playerId = player.getUUID();
		MinecraftServer server = player.level().getServer();
		GenerationKey generationKey = new GenerationKey(server, contentName);
		ActiveJob job = new ActiveJob(server);
		if (ACTIVE.putIfAbsent(generationKey, job) != null) {
			player.sendSystemMessage(error("Dynamic content named '" + contentName + "' is already being generated."));
			return false;
		}
		String requestedKind = type == DynamicContentType.ARMOR && requestedArmorSlot != null
				? requestedArmorSlot.serializedName() + " armor" : type.serializedName();
		player.sendSystemMessage(Component.literal("[Little Chemistry] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(generationModel.displayName() + " is creating " + requestedKind + " '" + displayName + "'…")
						.withStyle(ChatFormatting.GRAY)));

		try {
			job.task = GENERATION_EXECUTOR.submit(() -> {
				try {
					if (!job.promise.isDone()) {
						ContentGenerationAgent agent = new ContentGenerationAgent(new OpenAiClient(
								new AuthConfig(), generationModel.modelId(), GENERATION_REASONING_EFFORT));
						job.promise.complete(agent.generate(type, requestedArmorSlot, displayName));
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					job.promise.completeExceptionally(interrupted);
				} catch (Throwable error) {
					job.promise.completeExceptionally(error);
				}
			});
		} catch (Throwable submissionFailure) {
			ACTIVE.remove(generationKey, job);
			player.sendSystemMessage(error("Could not start the generation job."));
			LittleChemistry.LOGGER.error("Could not submit a Little Chemistry generation job", submissionFailure);
			return false;
		}

		job.promise.whenComplete((generated, failure) -> {
			if (!server.isRunning()) {
				ACTIVE.remove(generationKey, job);
				return;
			}
			server.execute(() -> {
				try {
					completeOnServer(server, playerId, type, requestedArmorSlot, displayName,
							generationModel, generated, failure);
				} finally {
					ACTIVE.remove(generationKey, job);
				}
			});
		});
		return true;
	}

	public static boolean request(ServerPlayer player, DynamicContentType type, String requestedName) {
		return request(player, type, null, requestedName);
	}

	public static void cancelForServer(MinecraftServer server) {
		for (Map.Entry<GenerationKey, ActiveJob> entry : ACTIVE.entrySet()) {
			ActiveJob job = entry.getValue();
			if (job.server == server && ACTIVE.remove(entry.getKey(), job)) {
				job.promise.completeExceptionally(new IllegalStateException("Server stopped during content generation"));
				Future<?> task = job.task;
				if (task != null) task.cancel(true);
			}
		}
	}

	private static void completeOnServer(MinecraftServer server, UUID playerId, DynamicContentType type,
			DynamicArmorSlot requestedArmorSlot,
			String displayName, GenerationModel generationModel, GeneratedContentSpec generated, Throwable failure) {
		ServerPlayer recipient = server.getPlayerList().getPlayer(playerId);
		if (failure != null) {
			String message = safeMessage(failure);
			if (recipient != null) {
				recipient.sendSystemMessage(error(generationModel.displayName() + " could not create the content: " + message));
			}
			LittleChemistry.LOGGER.warn("Content generation for {} failed: {}", playerId, message);
			return;
		}
		DynamicContentManager activeManager = DynamicContentManager.active();
		if (activeManager == null || !activeManager.belongsTo(server)) {
			if (recipient != null) recipient.sendSystemMessage(error("The server stopped before the content could be added."));
			return;
		}
		try {
			DynamicContentDefinition definition = requestedArmorSlot == null
					? activeManager.createGenerated(type, displayName, generated)
					: activeManager.createGenerated(type, requestedArmorSlot, displayName, generated);
			if (recipient != null) {
				String createdKind = type == DynamicContentType.ARMOR
						? definition.armor().slot().serializedName() + " armor" : type.serializedName();
				recipient.sendSystemMessage(Component.literal("Created " + createdKind + " ")
						.withStyle(ChatFormatting.GREEN)
						.append(DynamicContentObjects.displayName(definition))
						.append(Component.literal(" as little_chemistry:" + definition.name() + ".")
								.withStyle(ChatFormatting.GREEN)));
			}
		} catch (Exception error) {
			if (recipient != null) recipient.sendSystemMessage(ContentGenerationService.error(safeMessage(error)));
			LittleChemistry.LOGGER.error("Could not commit generated Little Chemistry content", error);
		}
	}

	private static Component error(String message) {
		return Component.literal("[Little Chemistry] " + message).withStyle(ChatFormatting.RED);
	}

	private static String safeMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		String message = current.getMessage();
		if (message == null || message.isBlank()) return current.getClass().getSimpleName();
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 500 ? safe : safe.substring(0, 500) + "…";
	}

	private record GenerationKey(MinecraftServer server, String contentName) {
	}

	private static final class ActiveJob {
		private final MinecraftServer server;
		private final CompletableFuture<GeneratedContentSpec> promise = new CompletableFuture<>();
		private volatile Future<?> task;

		private ActiveJob(MinecraftServer server) {
			this.server = server;
		}
	}
}
