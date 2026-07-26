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
	/*
	 * Keep the high-priority prompt universal and concise. Result- and capability-specific contracts are staged only after
	 * classification; never duplicate their details here or in the initial query.
	 */
	static final String SYSTEM_PROMPT = """
			You are Little Chemistry's autonomous world-mod coding agent. Work like a capable Codex-style software engineer in the
			supplied filesystem: understand the request, inspect exact source, author ordinary Java, and iteratively build the result.
			You have general-purpose bash/read/read_texture/grep/glob/write/edit/patch tools and the final verify boundary.
			Choose the result before implementation. For result-file jobs, the observing tool returns the selected focused contract;
			follow it and do not read unrelated capability contracts. `reference/API.md` is only a concise common index. Use native
			Minecraft mechanics and composable engine helpers.
			Before texturing, study the automatically supplied recipe-item references and any additional relevant indexed textures:
			their palette, rows, silhouette, shading, animation, and UV layout. Every texture crosses the model boundary only as the same
			RRGGBBAA palette plus hexadecimal rows you must author, never as an image.
			Call verify only after completing the request. Repair every diagnostic and finish through tools, not a prose answer.
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
			RecipeVisualReferences.Bundle visualReferences = RecipeVisualReferences.forRequest(request.recipeContext());
			GeneralistGenerationTools toolset = new GeneralistGenerationTools(
					workspace, request, visualReferences.entityReferenceSection());
			JsonArray tools = GeneralistGenerationTools.definitions();
			String initialReferences = visualReferences.promptSection();
			if (request.fixedType() == DynamicContentType.ENTITY) {
				initialReferences += visualReferences.entityReferenceSection();
			}
			JsonArray history = initialHistory(request, initialReferences);
			WorkspaceGenerationVerifier.VerifiedGeneration staged = null;
			try (GenerationConversationLog conversation = GenerationConversationLog.open(
					workspace, openAi.model(), openAi.reasoningEffort(), SYSTEM_PROMPT, tools, history,
					conversationExporter)) {
				LittleChemistry.LOGGER.info("{} generation conversation log: {}", openAi.model(),
						conversation.directory());
				try (OpenAiClient.ToolSession modelSession = openAi.openToolSession()) {
					for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
						if (Thread.currentThread().isInterrupted()) {
							throw new InterruptedException("Content generation was interrupted");
						}
						OpenAiClient.ToolRound response = modelSession.runToolRound(SYSTEM_PROMPT, tools, history);
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
							if (execution.rejection() != null) {
								conversation.recordRejected(execution.rejection(), history);
								throw new RecipeRejectedException(execution.rejection());
							}
							if (staged != null) {
								conversation.recordVerified(staged, history);
								return staged;
							}
						}
					}
					throw new IOException(openAi.model() + " exceeded " + MAX_TOOL_ROUNDS
							+ " workspace rounds without a successful verify");
				} catch (RecipeRejectedException rejection) {
					throw rejection;
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

	private static JsonArray initialHistory(GenerationRequest request, String visualReferenceSection) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", request.userPrompt(visualReferenceSection));
		content.add(text);
		message.add("content", content);
		JsonArray history = new JsonArray();
		history.add(message);
		return history;
	}
}
