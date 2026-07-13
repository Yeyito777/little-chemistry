package com.yeyito.littlechemistry.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.AuthConfig;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.ai.generation.ContentGenerationService;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class LittleChemistryCommands {
	private static final int MAX_QUESTION_LENGTH = 8_000;
	private static final int MAX_RESPONSE_LENGTH = 16_000;
	private static final int CHAT_CHUNK_LENGTH = 900;
	private static final PermissionNode<Boolean> AUTH_PERMISSION = PermissionNode.of(
			LittleChemistry.id("command.auth")
	);
	private static final PermissionNode<Boolean> CREATE_PERMISSION = PermissionNode.of(
			LittleChemistry.id("command.create")
	);
	private static final Predicate<CommandSourceStack> CREATE_PREDICATE =
			PermissionPredicates.require(CREATE_PERMISSION, PermissionLevel.ADMINS);
	private static final ExecutorService AI_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
	private static final Map<UUID, CompletableFuture<String>> ACTIVE_REQUESTS = new ConcurrentHashMap<>();
	private static final AuthConfig AUTH_CONFIG = new AuthConfig();
	private static final OpenAiClient OPEN_AI = new OpenAiClient(AUTH_CONFIG);

	private LittleChemistryCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> dispatcher.register(
				literal("littlechemistry")
						.then(literal("llm")
								.then(argument("question", StringArgumentType.greedyString())
										.executes(LittleChemistryCommands::askLlm)))
						.then(literal("auth")
								.requires(PermissionPredicates.require(AUTH_PERMISSION, PermissionLevel.ADMINS))
								.then(literal("subscription")
										.executes(LittleChemistryCommands::useSubscription))
								.then(literal("apikey")
										.then(argument("api_key", StringArgumentType.word())
												.executes(LittleChemistryCommands::useApiKey))))
							.then(literal("item")
									.requires(CREATE_PREDICATE)
								.then(literal("create")
										.then(argument("name", StringArgumentType.greedyString())
												.executes(context -> createDynamicContent(context, DynamicContentType.ITEM)))))
							.then(literal("block")
									.requires(CREATE_PREDICATE)
								.then(literal("create")
										.then(argument("name", StringArgumentType.greedyString())
												.executes(context -> createDynamicContent(context, DynamicContentType.BLOCK, null)))))
							.then(literal("armor")
									.requires(CREATE_PREDICATE)
									.then(literal("head").then(literal("create")
											.then(argument("name", StringArgumentType.greedyString())
													.executes(context -> createDynamicContent(context, DynamicContentType.ARMOR, DynamicArmorSlot.HEAD)))))
									.then(literal("chest").then(literal("create")
											.then(argument("name", StringArgumentType.greedyString())
													.executes(context -> createDynamicContent(context, DynamicContentType.ARMOR, DynamicArmorSlot.CHEST)))))
									.then(literal("leggings").then(literal("create")
											.then(argument("name", StringArgumentType.greedyString())
													.executes(context -> createDynamicContent(context, DynamicContentType.ARMOR, DynamicArmorSlot.LEGGINGS)))))
									.then(literal("boots").then(literal("create")
											.then(argument("name", StringArgumentType.greedyString())
													.executes(context -> createDynamicContent(context, DynamicContentType.ARMOR, DynamicArmorSlot.BOOTS))))))
		));
	}

	public static void createFromWand(ServerPlayer player, DynamicContentType type, DynamicArmorSlot armorSlot,
			String name) {
		CommandSourceStack source = player.createCommandSourceStack();
		if (!CREATE_PREDICATE.test(source)) {
			player.sendSystemMessage(error("You do not have permission to create dynamic content."));
			return;
		}
		ContentGenerationService.request(player, type, armorSlot, name);
	}

	public static void createFromWand(ServerPlayer player, DynamicContentType type, String name) {
		createFromWand(player, type, null, name);
	}

	public static void deleteFromWand(ServerPlayer player, java.util.List<String> names) {
		if (!CREATE_PREDICATE.test(player.createCommandSourceStack())) {
			player.sendSystemMessage(error("You do not have permission to delete dynamic content."));
			return;
		}
		DynamicContentManager manager = DynamicContentManager.active();
		if (manager == null) {
			player.sendSystemMessage(error("Dynamic content is not available yet."));
			return;
		}
		try {
			int deleted = manager.delete(names);
			player.sendSystemMessage(Component.literal("Deleted " + deleted + " Little Chemistry " +
					(deleted == 1 ? "definition." : "definitions.")).withStyle(ChatFormatting.GREEN));
		} catch (Exception error) {
			player.sendSystemMessage(LittleChemistryCommands.error(rootMessage(error)));
			LittleChemistry.LOGGER.warn("Could not delete Little Chemistry content for {}: {}", player.getUUID(), rootMessage(error));
		}
	}

	private static int createDynamicContent(CommandContext<CommandSourceStack> context, DynamicContentType type) {
		return createDynamicContent(context, type, null);
	}

	private static int createDynamicContent(CommandContext<CommandSourceStack> context, DynamicContentType type,
			DynamicArmorSlot armorSlot) {
		return createDynamicContent(context.getSource(), type, armorSlot, StringArgumentType.getString(context, "name"));
	}

	private static int createDynamicContent(CommandSourceStack source, DynamicContentType type,
			DynamicArmorSlot armorSlot, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(error("AI content generation must be started by a player."));
			return 0;
		}
		return ContentGenerationService.request(player, type, armorSlot, name) ? 1 : 0;
	}

	private static int askLlm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String question = normalizeQuestion(StringArgumentType.getString(context, "question"));
		if (question.isBlank()) {
			player.sendSystemMessage(error("Please provide a question."));
			return 0;
		}
		if (question.length() > MAX_QUESTION_LENGTH) {
			player.sendSystemMessage(error("That question is too long (maximum " + MAX_QUESTION_LENGTH + " characters)."));
			return 0;
		}

		UUID playerId = player.getUUID();
		if (ACTIVE_REQUESTS.containsKey(playerId)) {
			player.sendSystemMessage(error("Your previous AI request is still running."));
			return 0;
		}

		MinecraftServer server = context.getSource().getServer();
		player.sendSystemMessage(Component.literal("[Little Chemistry] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal("Thinking with " + OpenAiClient.MODEL + "…").withStyle(ChatFormatting.GRAY)));

		CompletableFuture<String> request = CompletableFuture.supplyAsync(() -> {
			try {
				return OPEN_AI.ask(question);
			} catch (IOException | InterruptedException error) {
				if (error instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				throw new RuntimeException(error);
			}
		}, AI_EXECUTOR);
		ACTIVE_REQUESTS.put(playerId, request);
		request.whenComplete((answer, throwable) -> server.execute(() -> {
			ACTIVE_REQUESTS.remove(playerId, request);
			ServerPlayer recipient = server.getPlayerList().getPlayer(playerId);
			if (recipient == null) {
				return;
			}
			if (throwable != null) {
				String message = rootMessage(throwable);
				recipient.sendSystemMessage(error(message));
				LittleChemistry.LOGGER.warn("AI request for player {} failed: {}", playerId, message);
				return;
			}
			sendPrivateResponse(recipient, answer);
		}));

		return 1;
	}

	private static int useSubscription(CommandContext<CommandSourceStack> context) {
		try {
			AUTH_CONFIG.useSubscription();
			context.getSource().sendSuccess(
					() -> Component.literal("Little Chemistry now uses subscription auth (Exocortex first, then Codex CLI).")
							.withStyle(ChatFormatting.GREEN),
					false
			);
			return 1;
		} catch (IOException error) {
			context.getSource().sendFailure(error("Could not save auth mode: " + rootMessage(error)));
			return 0;
		}
	}

	private static int useApiKey(CommandContext<CommandSourceStack> context) {
		String apiKey = StringArgumentType.getString(context, "api_key");
		try {
			AUTH_CONFIG.useApiKey(apiKey);
			context.getSource().sendSuccess(
					() -> Component.literal("Little Chemistry now uses the configured OpenAI API key. The key was stored privately and will not be displayed.")
							.withStyle(ChatFormatting.GREEN),
					false
			);
			return 1;
		} catch (IOException | IllegalArgumentException error) {
			context.getSource().sendFailure(error("Could not save API key: " + rootMessage(error)));
			return 0;
		}
	}

	private static void sendPrivateResponse(ServerPlayer player, String response) {
		String normalized = response.length() <= MAX_RESPONSE_LENGTH
				? response
				: response.substring(0, MAX_RESPONSE_LENGTH) + "\n[Response truncated]";
		player.sendSystemMessage(Component.literal("[Little Chemistry · " + OpenAiClient.MODEL + "]")
				.withStyle(ChatFormatting.AQUA));

		for (String paragraph : normalized.split("\\R", -1)) {
			if (paragraph.isEmpty()) {
				player.sendSystemMessage(Component.literal(" "));
				continue;
			}
			for (int start = 0; start < paragraph.length(); start += CHAT_CHUNK_LENGTH) {
				int end = Math.min(paragraph.length(), start + CHAT_CHUNK_LENGTH);
				player.sendSystemMessage(Component.literal(paragraph.substring(start, end)).withStyle(ChatFormatting.WHITE));
			}
		}
	}

	private static String normalizeQuestion(String raw) {
		String value = raw.trim();
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1)
					.replace("\\\"", "\"")
					.replace("\\\\", "\\");
		}
		return value.trim();
	}

	private static Component error(String message) {
		return Component.literal("[Little Chemistry] " + message).withStyle(ChatFormatting.RED);
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		String message = current.getMessage();
		if (message == null || message.isBlank()) {
			return current.getClass().getSimpleName();
		}
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 500 ? safe : safe.substring(0, 500) + "…";
	}
}
