package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;

import java.io.IOException;

/** Runs the content model as a general coding agent inside one world's real generated-mod source workspace. */
public final class ContentGenerationAgent {
	private static final int MAX_TOOL_ROUNDS = 256;
	static final String SYSTEM_PROMPT = """
			You are Little Chemistry's autonomous world-mod coding agent. Work exactly like a capable Codex-style software
			engineer inside the supplied filesystem. Understand the user's request, inspect existing code, search and decompile
			APIs through the reference tree, author ordinary Java classes and supporting source, and iteratively build the result.
			You have general-purpose bash/read/view_image/grep/glob/write/edit/patch tools and the final verify build boundary. There are no
			hidden property setters and no draft state outside the files you write. Every request uses these same system instructions;
			a workstation-specific output policy is declarative design data in the user request, never additional system instructions.
			When authoring a workstation, write that policy only as a concise third-person description of the theme, balance,
			relationship to ingredients, and gameplay properties its results should have. Do not address a future model, give workflow
			or tool instructions, repeat system instructions, describe verification, or tell it how to populate recipeData or satisfy
			a schema. Put deterministic input eligibility and consumption in WorkstationBehavior, processing mechanics and timing in
			behavior plus processDescription, and structured result metadata only in recipeDataSchema. Descriptive aiContext is not
			part of cache identity; never depend on a contextual value unless it is represented in cacheDiscriminator. Use native
			Minecraft mechanics and the engine's existing composable helpers. Before authoring textures, inspect relevant vanilla
			or modded texture references and study their palettes, pixel arrangements, silhouettes, shading, and UV/layout
			conventions. Call verify only after implementing the complete request. If verify returns diagnostics, inspect and repair
			the source until verification succeeds. Do not stop with a prose answer.
			""";

	private final OpenAiClient openAi;

	public ContentGenerationAgent(OpenAiClient openAi) {
		this.openAi = openAi;
	}

	public GeneratedContentSpec generate(DynamicContentType type, DynamicArmorSlot requestedArmorSlot,
			String requestedName) throws IOException, InterruptedException {
		return run(GenerationRequest.fixed(type, requestedArmorSlot, requestedName, 1, null)).content();
	}

	public GeneratedContentSpec generate(DynamicContentType type, String requestedName)
			throws IOException, InterruptedException {
		return generate(type, null, requestedName);
	}

	WorkspaceGenerationVerifier.VerifiedGeneration generateRecipe(JsonObject recipeContext, String workstationPolicy,
			JsonObject recipeDataSchema) throws IOException, InterruptedException {
		return generateRecipe(recipeContext, workstationPolicy, recipeDataSchema, null);
	}

	WorkspaceGenerationVerifier.VerifiedGeneration generateRecipe(JsonObject recipeContext, String workstationPolicy,
			JsonObject recipeDataSchema, ExocortexConversationExporter conversationExporter)
			throws IOException, InterruptedException {
		return run(GenerationRequest.recipe(recipeContext, workstationPolicy, recipeDataSchema), conversationExporter);
	}

	private WorkspaceGenerationVerifier.VerifiedGeneration run(GenerationRequest request)
			throws IOException, InterruptedException {
		return run(request, null);
	}

	private WorkspaceGenerationVerifier.VerifiedGeneration run(GenerationRequest request,
			ExocortexConversationExporter conversationExporter)
			throws IOException, InterruptedException {
		DynamicContentManager manager = DynamicContentManager.active();
		if (manager == null) throw new IOException("Dynamic content is not available yet");
		try (GenerationWorkspace workspace = GenerationWorkspace.open(
				manager.generationWorkspaceRoot(), request)) {
			GeneralistGenerationTools toolset = new GeneralistGenerationTools(workspace, request);
			JsonArray tools = GeneralistGenerationTools.definitions();
			JsonArray history = initialHistory(request);
			WorkspaceGenerationVerifier.VerifiedGeneration staged = null;
			try (GenerationConversationLog conversation = GenerationConversationLog.open(
					workspace, openAi.model(), openAi.reasoningEffort(), SYSTEM_PROMPT, tools, history,
					conversationExporter)) {
				LittleChemistry.LOGGER.info("{} generation conversation log: {}", openAi.model(),
						conversation.directory());
				try {
					for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
						if (Thread.currentThread().isInterrupted()) {
							throw new InterruptedException("Content generation was interrupted");
						}
						OpenAiClient.ToolRound response = openAi.runToolRound(SYSTEM_PROMPT, tools, history);
						response.outputItems().forEach(item -> history.add(item.deepCopy()));
						conversation.recordModelRound(round, response, history);
						if (response.calls().isEmpty()) {
							throw new IOException(openAi.model() + " did not call a generation workspace tool");
						}
						for (OpenAiClient.ToolCall call : response.calls()) {
							LittleChemistry.LOGGER.info("{} generation workspace tool: {}", openAi.model(), call.name());
							long started = System.nanoTime();
							GeneralistGenerationTools.ToolResult execution = toolset.execute(call.name(), call.arguments());
							long duration = System.nanoTime() - started;
							JsonObject output = new JsonObject();
							output.addProperty("type", "function_call_output");
							output.addProperty("call_id", call.callId());
							output.add("output", execution.responseOutput());
							history.add(output);
							if (execution.verified() != null) staged = execution.verified();
							conversation.recordToolResult(round, call, execution, duration, output, history);
							if (staged != null) {
								conversation.recordVerified(staged, history);
								return staged;
							}
						}
					}
					throw new IOException(openAi.model() + " exceeded " + MAX_TOOL_ROUNDS
							+ " workspace rounds without a successful verify");
				} catch (IOException | InterruptedException | RuntimeException | Error failure) {
					try {
						conversation.recordFailure(failure, history);
					} catch (IOException loggingFailure) {
						failure.addSuppressed(loggingFailure);
					}
					throw failure;
				}
			} catch (IOException | InterruptedException | RuntimeException | Error failure) {
				if (staged != null) GenerationWorkspace.discardPending(staged.content());
				throw failure;
			}
		}
	}

	private static JsonArray initialHistory(GenerationRequest request) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", request.userPrompt());
		content.add(text);
		message.add("content", content);
		JsonArray history = new JsonArray();
		history.add(message);
		return history;
	}
}
