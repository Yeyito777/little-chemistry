package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.ai.OpenAiClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Durable, per-world record of one generation conversation. {@code <job-id>.json} and
 * {@code conversation.json} are native Exocortex conversation files. The exact Responses-API replay and richer
 * append-only execution details remain available as auxiliary artifacts.
 */
final class GenerationConversationLog implements AutoCloseable {
	static final int EXOCORTEX_SCHEMA_VERSION = 17;
	static final String REPLAY_SCHEMA = "little-chemistry.responses-replay";
	static final int REPLAY_SCHEMA_VERSION = 1;
	private static final Gson GSON = new Gson();
	private static final Gson PRETTY_GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
			.withZone(ZoneOffset.UTC);
	private static final int MAX_TRANSCRIPT_VALUE = 64 * 1024;
	private static final String LOGS_README = """
			# Little Chemistry generation conversations

			Every child directory is one durable AI generation trace. The file named `<job-id>.json` is a native Exocortex v17 conversation file:
			copy or import that file directly as a conversation. `conversation.json`
			contains the same native conversation as a stable human-friendly alias. Both include the system instructions,
			user request, visible reasoning summaries, assistant messages, tool calls, exact tool results, timing/token
			metadata, OpenAI response IDs, encrypted reasoning continuation records, and terminal verification or failure.

			`responses-replay.json` is an auxiliary exact Responses-API replay with the generation request and tool schemas.
			`events.jsonl` is append-only and adds precise event timestamps, exact output items, tool durations, verification,
			and failures. `transcript.md` is a bounded human-readable projection. Use the native `<job-id>.json` file when
			dropping a generation into Exocortex; use the auxiliary files only for lower-level debugging.

			Reasoning records preserve every summary/raw reasoning field and `encrypted_content` returned by OpenAI. The API
			does not expose private raw chain-of-thought, so no logger can record text the provider did not return. No
			authentication token or request Authorization header is written by this logger.
			""";

	private final GenerationWorkspace workspace;
	private final String model;
	private final String reasoningEffort;
	private final String instructions;
	private final JsonArray tools;
	private final JsonObject request;
	private final Instant startedAt;
	private final Path directory;
	private final Path portableConversationFile;
	private final Path conversationFile;
	private final Path responsesReplayFile;
	private final FileChannel events;
	private final FileChannel transcript;
	private final ExocortexConversationExporter conversationExporter;
	private final JsonArray messages = new JsonArray();
	private String conversationTitle;
	private long sequence;
	private boolean terminal;

	private GenerationConversationLog(GenerationWorkspace workspace, String model, String reasoningEffort, String instructions,
			JsonArray tools, JsonArray initialInput, ExocortexConversationExporter conversationExporter) throws IOException {
		this.workspace = workspace;
		this.model = model;
		this.reasoningEffort = reasoningEffort;
		this.instructions = instructions;
		this.tools = tools.deepCopy();
		this.conversationExporter = conversationExporter;
		this.request = JsonParser.parseString(Files.readString(
				workspace.root().resolve("request.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		this.conversationTitle = initialTitle();
		this.startedAt = Instant.now();
		Path logsRoot = workspace.logsRoot();
		Files.createDirectories(logsRoot);
		writeLogsReadme(logsRoot.resolve("README.md"));
		this.directory = logsRoot.resolve(DAY.format(startedAt))
				.resolve(FILE_TIME.format(startedAt) + "--" + workspace.jobId());
		Files.createDirectories(directory);
		this.portableConversationFile = directory.resolve(workspace.jobId() + ".json");
		this.conversationFile = directory.resolve("conversation.json");
		this.responsesReplayFile = directory.resolve("responses-replay.json");
		this.events = FileChannel.open(directory.resolve("events.jsonl"),
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
		this.transcript = FileChannel.open(directory.resolve("transcript.md"),
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);

		appendTranscript("# Little Chemistry generation " + workspace.jobId() + "\n\n"
				+ "- Started: `" + startedAt + "`\n"
				+ "- Model: `" + model + "`\n"
				+ "- Reasoning effort: `" + reasoningEffort + "`\n"
				+ "- Native Exocortex conversation: [`" + workspace.jobId() + ".json`](" + workspace.jobId() + ".json)\n"
				+ "- Exact Responses replay: [`responses-replay.json`](responses-replay.json)\n"
				+ "- Append-only events: [`events.jsonl`](events.jsonl)\n\n"
				+ "## Initial request\n\n~~~json\n" + PRETTY_GSON.toJson(request) + "\n~~~\n\n");
		JsonObject started = baseEvent("conversation.started");
		started.addProperty("model", model);
		started.addProperty("reasoningEffort", reasoningEffort);
		started.add("request", request.deepCopy());
		appendEvent(started);
		appendStoredMessage("system_instructions", instructions, null, null);
		for (JsonElement item : initialInput) {
			appendConversationItem("user", -1, item);
			appendInitialMessage(item);
		}
		writeSnapshots("running", initialInput, null);
	}

	static GenerationConversationLog open(GenerationWorkspace workspace, String model, String reasoningEffort,
			String instructions, JsonArray tools, JsonArray initialInput) throws IOException {
		return open(workspace, model, reasoningEffort, instructions, tools, initialInput, null);
	}

	static GenerationConversationLog open(GenerationWorkspace workspace, String model, String reasoningEffort,
			String instructions, JsonArray tools, JsonArray initialInput,
			ExocortexConversationExporter conversationExporter) throws IOException {
		return new GenerationConversationLog(workspace, model, reasoningEffort, instructions, tools, initialInput,
				conversationExporter);
	}

	Path directory() {
		return directory;
	}

	void recordModelRound(int round, OpenAiClient.ToolRound response, JsonArray history) throws IOException {
		JsonObject event = baseEvent("model.response");
		event.addProperty("round", round);
		event.add("response", response.response());
		appendEvent(event);
		JsonArray outputItems = response.outputItems();
		for (JsonElement item : outputItems) {
			appendConversationItem("model", round, item);
			renderModelItem(round, item);
		}
		appendAssistantMessage(round, response);
		writeSnapshots("running", history, null);
	}

	void recordToolResult(int round, OpenAiClient.ToolCall call,
			GeneralistGenerationTools.ToolResult result, long durationNanos,
			JsonObject replayItem, JsonArray history) throws IOException {
		JsonObject event = baseEvent("tool.execution");
		event.addProperty("round", round);
		event.addProperty("callId", call.callId());
		event.addProperty("name", call.name());
		event.addProperty("durationMillis", durationNanos / 1_000_000.0);
		event.add("arguments", call.arguments().deepCopy());
		event.add("result", result.output().deepCopy());
		event.addProperty("verified", result.verified() != null);
		appendEvent(event);
		appendConversationItem("tool", round, replayItem);
		long endedAt = System.currentTimeMillis();
		long durationMillis = Math.max(0L, durationNanos / 1_000_000L);
		JsonObject toolResult = new JsonObject();
		toolResult.addProperty("type", "tool_result");
		toolResult.addProperty("tool_use_id", call.callId());
		toolResult.addProperty("content", string(replayItem, "output") == null
				? GSON.toJson(result.output()) : replayItem.get("output").getAsString());
		toolResult.addProperty("is_error", isErrorResult(result.output()));
		JsonArray content = new JsonArray();
		content.add(toolResult);
		appendStoredMessage("user", content, metadata(Math.max(0L, endedAt - durationMillis), endedAt, 0), null);
		appendTranscript("### Tool result: `" + call.name() + "`\n\n"
				+ "Call ID: `" + call.callId() + "` · " + durationNanos / 1_000_000.0 + " ms\n\n"
				+ "~~~json\n" + preview(PRETTY_GSON.toJson(result.output())) + "\n~~~\n\n");
		writeSnapshots("running", history, null);
	}

	void recordVerified(WorkspaceGenerationVerifier.VerifiedGeneration verified, JsonArray history) throws IOException {
		JsonObject terminalData = new JsonObject();
		terminalData.addProperty("kind", verified.type().serializedName());
		terminalData.addProperty("displayName", verified.displayName());
		terminalData.addProperty("outputCount", verified.outputCount());
		terminalData.addProperty("sourceDigest", verified.sourceDigest());
		terminalData.add("recipeData", verified.recipeData());
		terminalData.addProperty("logDirectory", directory.toString());
		JsonObject event = baseEvent("generation.verified");
		for (var entry : terminalData.entrySet()) event.add(entry.getKey(), entry.getValue().deepCopy());
		appendEvent(event);
		appendTranscript("## Verification succeeded\n\n~~~json\n"
				+ PRETTY_GSON.toJson(terminalData) + "\n~~~\n");
		long now = System.currentTimeMillis();
		appendStoredMessage("system", "Little Chemistry generation verified successfully.\n\n"
				+ PRETTY_GSON.toJson(terminalData), metadata(now, now, 0), null);
		conversationTitle = "Generate " + verified.displayName();
		terminal = true;
		writeSnapshots("verified", history, terminalData);
		exportTerminalConversation();
	}

	void recordFailure(Throwable failure, JsonArray history) throws IOException {
		JsonObject terminalData = failure(failure);
		JsonObject event = baseEvent("generation.failed");
		for (var entry : terminalData.entrySet()) event.add(entry.getKey(), entry.getValue().deepCopy());
		appendEvent(event);
		appendTranscript("## Generation failed\n\n~~~text\n" + preview(terminalData.get("stackTrace").getAsString())
				+ "\n~~~\n");
		long now = System.currentTimeMillis();
		appendStoredMessage("system", "Little Chemistry generation failed.\n\n"
				+ terminalData.get("stackTrace").getAsString(), metadata(now, now, 0), null);
		terminal = true;
		writeSnapshots("failed", history, terminalData);
		exportTerminalConversation();
	}

	private void exportTerminalConversation() {
		if (conversationExporter == null) return;
		try {
			conversationExporter.publish(portableConversationFile);
		} catch (IOException failure) {
			LittleChemistry.LOGGER.warn("Could not export generation conversation {} to Exocortex {}: {}",
					workspace.jobId(), conversationExporter.targetPath(), failure.getMessage());
		}
	}

	private void appendInitialMessage(JsonElement itemElement) {
		if (!(itemElement instanceof JsonObject item) || !"message".equals(string(item, "type"))) {
			long now = startedAt.toEpochMilli();
			appendStoredMessage("user", GSON.toJson(itemElement), metadata(now, now, 0), null);
			return;
		}
		String role = "assistant".equals(string(item, "role")) ? "assistant" : "user";
		JsonArray content = exocortexTextContent(item.get("content"));
		long now = startedAt.toEpochMilli();
		appendStoredMessage(role, collapseTextContent(content), metadata(now, now, 0), null);
	}

	private void appendAssistantMessage(int round, OpenAiClient.ToolRound response) {
		JsonArray content = new JsonArray();
		JsonArray reasoningItems = new JsonArray();
		int itemIndex = 0;
		for (JsonElement itemElement : response.outputItems()) {
			if (!(itemElement instanceof JsonObject item)) {
				itemIndex++;
				continue;
			}
			String type = string(item, "type");
			if ("reasoning".equals(type)) {
				JsonArray summaries = reasoningTexts(item.get("summary"));
				JsonArray rawContent = reasoningTexts(item.get("content"));
				JsonArray visible = rawContent.isEmpty() ? summaries : rawContent;
				for (JsonElement text : visible) {
					if (text.getAsString().isEmpty()) continue;
					JsonObject thinking = new JsonObject();
					thinking.addProperty("type", "thinking");
					thinking.addProperty("thinking", text.getAsString());
					thinking.addProperty("signature", "");
					content.add(thinking);
				}
				JsonObject reasoning = new JsonObject();
				String id = string(item, "id");
				reasoning.addProperty("id", id == null || id.isBlank()
						? "little-chemistry-reasoning-" + round + "-" + itemIndex : id);
				String encrypted = string(item, "encrypted_content");
				if (encrypted == null) reasoning.add("encryptedContent", JsonNull.INSTANCE);
				else reasoning.addProperty("encryptedContent", encrypted);
				reasoning.add("summaries", summaries);
				if (!rawContent.isEmpty()) reasoning.add("rawContent", rawContent);
				reasoningItems.add(reasoning);
			} else if ("function_call".equals(type)) {
				JsonObject toolUse = new JsonObject();
				toolUse.addProperty("type", "tool_use");
				toolUse.addProperty("id", defaultString(string(item, "call_id"), "unknown-call-" + round + "-" + itemIndex));
				toolUse.addProperty("name", defaultString(string(item, "name"), "unknown"));
				toolUse.add("input", parseArguments(string(item, "arguments")));
				content.add(toolUse);
			} else if ("message".equals(type)) {
				for (JsonElement block : exocortexTextContent(item.get("content"))) content.add(block);
			}
			itemIndex++;
		}

		JsonObject openai = new JsonObject();
		String responseId = string(response.response(), "id");
		if (responseId != null && !responseId.isBlank()) openai.addProperty("responseId", responseId);
		openai.add("reasoningItems", reasoningItems);
		JsonObject providerData = new JsonObject();
		providerData.add("openai", openai);
		long endedAt = System.currentTimeMillis();
		long started = responseStartedAt(response.response(), endedAt);
		appendStoredMessage("assistant", content,
				metadata(started, endedAt, outputTokens(response.response())), providerData);
	}

	private void appendConversationItem(String origin, int round, JsonElement item) throws IOException {
		JsonObject event = baseEvent("conversation.item");
		event.addProperty("origin", origin);
		if (round >= 0) event.addProperty("round", round);
		event.add("item", item.deepCopy());
		appendEvent(event);
	}

	private void renderModelItem(int round, JsonElement itemElement) throws IOException {
		if (!itemElement.isJsonObject()) return;
		JsonObject item = itemElement.getAsJsonObject();
		String type = string(item, "type");
		if ("reasoning".equals(type)) {
			StringBuilder summary = new StringBuilder();
			for (JsonElement text : reasoningTexts(item.get("summary"))) {
				if (!text.getAsString().isBlank()) summary.append(text.getAsString()).append("\n\n");
			}
			appendTranscript("## Round " + round + " reasoning summary\n\n"
					+ (summary.isEmpty() ? "_(No plaintext summary was returned; see the exact reasoning item in JSON.)_\n\n"
					: preview(summary.toString())));
		} else if ("function_call".equals(type)) {
			String name = string(item, "name");
			String callId = string(item, "call_id");
			String arguments = string(item, "arguments");
			appendTranscript("### Tool call: `" + (name == null ? "unknown" : name) + "`\n\n"
					+ "Call ID: `" + (callId == null ? "unknown" : callId) + "`\n\n"
					+ "~~~json\n" + preview(prettyJson(arguments)) + "\n~~~\n\n");
		} else if ("message".equals(type)) {
			appendTranscript("## Round " + round + " model message\n\n~~~json\n"
					+ preview(PRETTY_GSON.toJson(item)) + "\n~~~\n\n");
		}
	}

	private void writeSnapshots(String status, JsonArray history, JsonObject terminalData) throws IOException {
		long updatedAt = System.currentTimeMillis();
		String nativeJson = PRETTY_GSON.toJson(nativeConversation(updatedAt)) + "\n";
		writeAtomically(portableConversationFile, nativeJson);
		writeAtomically(conversationFile, nativeJson);
		writeResponsesReplay(status, history, terminalData, updatedAt);
	}

	private JsonObject nativeConversation(long updatedAt) {
		long createdAt = startedAt.toEpochMilli();
		JsonObject conversation = new JsonObject();
		conversation.addProperty("version", EXOCORTEX_SCHEMA_VERSION);
		conversation.addProperty("id", workspace.jobId());
		conversation.addProperty("provider", "openai");
		conversation.addProperty("model", model);
		conversation.addProperty("effort", reasoningEffort);
		conversation.addProperty("fastMode", false);
		conversation.add("messages", messages.deepCopy());
		conversation.add("activeContext", JsonNull.INSTANCE);
		conversation.addProperty("createdAt", createdAt);
		conversation.addProperty("updatedAt", updatedAt);
		conversation.add("lastContextTokens", JsonNull.INSTANCE);
		conversation.addProperty("marked", false);
		conversation.addProperty("pinned", false);
		conversation.addProperty("sortOrder", -createdAt);
		conversation.add("folderId", JsonNull.INSTANCE);
		conversation.addProperty("title", title());
		conversation.add("goal", JsonNull.INSTANCE);
		conversation.add("subagentMaxDepth", JsonNull.INSTANCE);
		conversation.addProperty("storageGeneration", 1);
		conversation.add("lastUnwindReceipt", JsonNull.INSTANCE);
		return conversation;
	}

	private void writeResponsesReplay(String status, JsonArray history, JsonObject terminalData, long updatedAt)
			throws IOException {
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("schema", REPLAY_SCHEMA);
		snapshot.addProperty("schemaVersion", REPLAY_SCHEMA_VERSION);
		snapshot.addProperty("conversationId", workspace.jobId());
		snapshot.addProperty("title", title());
		snapshot.addProperty("startedAt", startedAt.toString());
		snapshot.addProperty("updatedAt", Instant.ofEpochMilli(updatedAt).toString());
		snapshot.addProperty("status", status);
		snapshot.addProperty("provider", "openai");
		snapshot.addProperty("model", model);
		snapshot.addProperty("reasoningEffort", reasoningEffort);
		snapshot.addProperty("instructions", instructions);
		snapshot.add("tools", tools.deepCopy());
		snapshot.add("request", request.deepCopy());
		snapshot.add("input", history.deepCopy());
		snapshot.addProperty("nativeConversationFile", workspace.jobId() + ".json");
		snapshot.addProperty("eventsFile", "events.jsonl");
		snapshot.addProperty("transcriptFile", "transcript.md");
		snapshot.addProperty("reasoningNote",
				"Reasoning summaries, raw content, and encrypted_content are preserved exactly when returned; private raw chain-of-thought is not exposed by the provider API.");
		if (terminalData != null) snapshot.add("terminal", terminalData.deepCopy());
		writeAtomically(responsesReplayFile, PRETTY_GSON.toJson(snapshot) + "\n");
	}

	private void appendStoredMessage(String role, String content, JsonObject metadata, JsonObject providerData) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		message.add("metadata", metadata == null ? JsonNull.INSTANCE : metadata);
		if (providerData != null) message.add("providerData", providerData);
		messages.add(message);
	}

	private void appendStoredMessage(String role, JsonArray content, JsonObject metadata, JsonObject providerData) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.add("content", content);
		message.add("metadata", metadata == null ? JsonNull.INSTANCE : metadata);
		if (providerData != null) message.add("providerData", providerData);
		messages.add(message);
	}

	private JsonObject metadata(long startedAt, long endedAt, int tokens) {
		JsonObject metadata = new JsonObject();
		metadata.addProperty("startedAt", startedAt);
		metadata.addProperty("endedAt", endedAt);
		metadata.addProperty("model", model);
		metadata.addProperty("tokens", Math.max(0, tokens));
		return metadata;
	}

	private String title() {
		return conversationTitle;
	}

	private String initialTitle() {
		if (request.has("requestedName")) return "Generate " + request.get("requestedName").getAsString();
		return request.has("workstationPolicy") ? "Generate workstation recipe" : "Generate recipe";
	}

	private JsonObject baseEvent(String type) {
		JsonObject event = new JsonObject();
		event.addProperty("schemaVersion", REPLAY_SCHEMA_VERSION);
		event.addProperty("sequence", sequence++);
		event.addProperty("timestamp", Instant.now().toString());
		event.addProperty("conversationId", workspace.jobId());
		event.addProperty("type", type);
		return event;
	}

	private void appendEvent(JsonObject event) throws IOException {
		write(events, GSON.toJson(event) + "\n");
	}

	private void appendTranscript(String value) throws IOException {
		write(transcript, value);
	}

	private static void write(FileChannel channel, String value) throws IOException {
		ByteBuffer bytes = StandardCharsets.UTF_8.encode(value);
		while (bytes.hasRemaining()) channel.write(bytes);
		channel.force(false);
	}

	private static void writeAtomically(Path destination, String value) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp-" + UUID.randomUUID());
		Files.writeString(temporary, value, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void writeLogsReadme(Path destination) throws IOException {
		writeAtomically(destination, LOGS_README);
	}

	private static JsonObject failure(Throwable failure) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("class", failure.getClass().getName());
		encoded.addProperty("message", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
		StringWriter stack = new StringWriter();
		failure.printStackTrace(new PrintWriter(stack));
		encoded.addProperty("stackTrace", stack.toString());
		return encoded;
	}

	private static JsonArray exocortexTextContent(JsonElement contentElement) {
		JsonArray encoded = new JsonArray();
		if (!(contentElement instanceof JsonArray content)) return encoded;
		for (JsonElement partElement : content) {
			if (!(partElement instanceof JsonObject part)) continue;
			String type = string(part, "type");
			if (!"input_text".equals(type) && !"output_text".equals(type) && !"text".equals(type)) continue;
			String text = string(part, "text");
			if (text == null) continue;
			JsonObject block = new JsonObject();
			block.addProperty("type", "text");
			block.addProperty("text", text);
			encoded.add(block);
		}
		return encoded;
	}

	private static JsonElement collapseTextContent(JsonArray content) {
		if (content.size() == 1 && content.get(0) instanceof JsonObject block
				&& "text".equals(string(block, "type"))) {
			return block.get("text").deepCopy();
		}
		return content;
	}

	private void appendStoredMessage(String role, JsonElement content, JsonObject metadata, JsonObject providerData) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.add("content", content.deepCopy());
		message.add("metadata", metadata == null ? JsonNull.INSTANCE : metadata);
		if (providerData != null) message.add("providerData", providerData);
		messages.add(message);
	}

	private static JsonArray reasoningTexts(JsonElement contentElement) {
		JsonArray texts = new JsonArray();
		if (!(contentElement instanceof JsonArray content)) return texts;
		for (JsonElement partElement : content) {
			if (!(partElement instanceof JsonObject part)) continue;
			String text = string(part, "text");
			if (text != null) texts.add(text);
		}
		return texts;
	}

	private static JsonObject parseArguments(String raw) {
		if (raw == null || raw.isBlank()) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(raw);
			if (parsed instanceof JsonObject object) return object;
		} catch (RuntimeException ignored) {
		}
		JsonObject malformed = new JsonObject();
		malformed.addProperty("_malformed", raw);
		return malformed;
	}

	private static boolean isErrorResult(JsonObject output) {
		JsonElement ok = output.get("ok");
		return ok != null && ok.isJsonPrimitive() && !ok.getAsBoolean();
	}

	private static long responseStartedAt(JsonObject response, long fallback) {
		JsonElement created = response.get("created_at");
		if (created == null || !created.isJsonPrimitive() || !created.getAsJsonPrimitive().isNumber()) return fallback;
		double seconds = created.getAsDouble();
		if (!Double.isFinite(seconds) || seconds < 0) return fallback;
		return (long) (seconds * 1000.0);
	}

	private static int outputTokens(JsonObject response) {
		if (!(response.get("usage") instanceof JsonObject usage)) return 0;
		JsonElement output = usage.get("output_tokens");
		if (output == null || !output.isJsonPrimitive() || !output.getAsJsonPrimitive().isNumber()) return 0;
		long tokens = output.getAsLong();
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, tokens));
	}

	private static String prettyJson(String raw) {
		if (raw == null || raw.isBlank()) return "{}";
		try {
			return PRETTY_GSON.toJson(JsonParser.parseString(raw));
		} catch (RuntimeException ignored) {
			return raw;
		}
	}

	private static String preview(String value) {
		return value.length() <= MAX_TRANSCRIPT_VALUE ? value
				: value.substring(0, MAX_TRANSCRIPT_VALUE)
				+ "\n…truncated in transcript; exact value is in the native conversation/responses-replay.json/events.jsonl…\n";
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String defaultString(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	@Override
	public void close() throws IOException {
		IOException failure = null;
		try {
			if (!terminal) {
				JsonObject event = baseEvent("conversation.closed");
				event.addProperty("status", "incomplete");
				appendEvent(event);
				long now = System.currentTimeMillis();
				appendStoredMessage("system", "Little Chemistry generation log closed before verification completed.",
						metadata(now, now, 0), null);
				String nativeJson = PRETTY_GSON.toJson(nativeConversation(now)) + "\n";
				writeAtomically(portableConversationFile, nativeJson);
				writeAtomically(conversationFile, nativeJson);
			}
		} catch (IOException error) {
			failure = error;
		}
		try {
			events.close();
		} catch (IOException error) {
			if (failure == null) failure = error;
			else failure.addSuppressed(error);
		}
		try {
			transcript.close();
		} catch (IOException error) {
			if (failure == null) failure = error;
			else failure.addSuppressed(error);
		}
		if (failure != null) throw failure;
	}
}
