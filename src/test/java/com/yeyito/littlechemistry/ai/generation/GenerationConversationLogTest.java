package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.ai.OpenAiClient;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenerationConversationLogTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void persistsANativeExocortexConversationAndAuxiliaryExactReplay() throws Exception {
		Path world = temporaryDirectory.resolve("world");
		Path job = temporaryDirectory.resolve("job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.createDirectories(world.resolve("logs"));
			Files.writeString(world.resolve("logs/README.md"), "obsolete custom-format documentation");
			Files.writeString(job.resolve("request.json"), """
					{"mode":"fixed","requestedKind":"item","requestedName":"Logged Dust","requestedOutputCount":1}
					""");
			JsonArray tools = GeneralistGenerationTools.definitions();
			JsonArray history = initialHistory();
			Path logDirectory;
			try (GenerationConversationLog log = GenerationConversationLog.open(
					workspace, "gpt-test", "high", "test system prompt", tools, history)) {
				logDirectory = log.directory();
					JsonObject reasoning = JsonParser.parseString("""
							{"type":"reasoning","id":"reasoning-1",
							 "summary":[{"type":"summary_text","text":"Inspect the API first."}],
							 "encrypted_content":"encrypted-reasoning-payload"}
							""").getAsJsonObject();
				JsonObject functionCall = new JsonObject();
				functionCall.addProperty("type", "function_call");
				functionCall.addProperty("call_id", "call-1");
				functionCall.addProperty("name", "read");
				functionCall.addProperty("arguments", "{\"path\":\"reference/API.md\"}");
				JsonArray outputItems = new JsonArray();
				outputItems.add(reasoning);
				outputItems.add(functionCall);
					JsonObject responseMetadata = JsonParser.parseString("""
							{"id":"response-1","created_at":1234.5,"model":"gpt-test",
							 "usage":{"input_tokens":10,"output_tokens":20}}
						""").getAsJsonObject();
				OpenAiClient.ToolCall call = new OpenAiClient.ToolCall(
						"call-1", "read", JsonParser.parseString("{\"path\":\"reference/API.md\"}").getAsJsonObject());
				OpenAiClient.ToolRound round = new OpenAiClient.ToolRound(List.of(call), outputItems, responseMetadata);
				outputItems.forEach(item -> history.add(item.deepCopy()));
				log.recordModelRound(0, round, history);

				JsonObject result = JsonParser.parseString("""
						{"ok":true,"path":"reference/API.md","content":"complete tool result"}
						""").getAsJsonObject();
				var execution = new GeneralistGenerationTools.ToolResult(result, null);
				JsonObject replay = new JsonObject();
				replay.addProperty("type", "function_call_output");
				replay.addProperty("call_id", "call-1");
				replay.addProperty("output", result.toString());
				history.add(replay);
				log.recordToolResult(0, call, execution, 12_500_000L, replay, history);
				log.recordFailure(new IllegalStateException("test failure"), history);
			}

			JsonObject conversation = JsonParser.parseString(
					Files.readString(logDirectory.resolve("conversation.json"))).getAsJsonObject();
			String conversationId = conversation.get("id").getAsString();
			assertEquals(GenerationConversationLog.EXOCORTEX_SCHEMA_VERSION, conversation.get("version").getAsInt());
			assertEquals("openai", conversation.get("provider").getAsString());
			assertEquals("gpt-test", conversation.get("model").getAsString());
			assertEquals("high", conversation.get("effort").getAsString());
			assertEquals("Generate Logged Dust", conversation.get("title").getAsString());
			assertTrue(conversation.get("activeContext").isJsonNull());
			assertEquals(Files.readString(logDirectory.resolve("conversation.json")),
					Files.readString(logDirectory.resolve(conversationId + ".json")));

			JsonArray messages = conversation.getAsJsonArray("messages");
			assertEquals(5, messages.size());
			assertEquals("system_instructions", messages.get(0).getAsJsonObject().get("role").getAsString());
			assertEquals("test system prompt", messages.get(0).getAsJsonObject().get("content").getAsString());
			assertTrue(messages.get(0).getAsJsonObject().get("metadata").isJsonNull());
			assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
			assertEquals("Implement the request.", messages.get(1).getAsJsonObject().get("content").getAsString());

			JsonObject assistant = messages.get(2).getAsJsonObject();
			assertEquals("assistant", assistant.get("role").getAsString());
			JsonArray assistantContent = assistant.getAsJsonArray("content");
			assertEquals("thinking", assistantContent.get(0).getAsJsonObject().get("type").getAsString());
			assertEquals("Inspect the API first.", assistantContent.get(0).getAsJsonObject().get("thinking").getAsString());
			assertEquals("tool_use", assistantContent.get(1).getAsJsonObject().get("type").getAsString());
			assertEquals("read", assistantContent.get(1).getAsJsonObject().get("name").getAsString());
			assertEquals("reference/API.md", assistantContent.get(1).getAsJsonObject()
					.getAsJsonObject("input").get("path").getAsString());
			assertEquals(20, assistant.getAsJsonObject("metadata").get("tokens").getAsInt());
			assertEquals(1_234_500L, assistant.getAsJsonObject("metadata").get("startedAt").getAsLong());
			JsonObject openai = assistant.getAsJsonObject("providerData").getAsJsonObject("openai");
			assertEquals("response-1", openai.get("responseId").getAsString());
			JsonObject persistedReasoning = openai.getAsJsonArray("reasoningItems").get(0).getAsJsonObject();
			assertEquals("reasoning-1", persistedReasoning.get("id").getAsString());
			assertEquals("encrypted-reasoning-payload", persistedReasoning.get("encryptedContent").getAsString());
			assertEquals("Inspect the API first.", persistedReasoning.getAsJsonArray("summaries").get(0).getAsString());

			JsonObject storedToolResult = messages.get(3).getAsJsonObject().getAsJsonArray("content")
					.get(0).getAsJsonObject();
			assertEquals("tool_result", storedToolResult.get("type").getAsString());
			assertEquals("call-1", storedToolResult.get("tool_use_id").getAsString());
			assertTrue(storedToolResult.get("content").getAsString().contains("complete tool result"));
			assertTrue(!storedToolResult.get("is_error").getAsBoolean());
			assertEquals("system", messages.get(4).getAsJsonObject().get("role").getAsString());
			assertTrue(messages.get(4).getAsJsonObject().get("content").getAsString().contains("test failure"));

			JsonObject replay = JsonParser.parseString(
					Files.readString(logDirectory.resolve("responses-replay.json"))).getAsJsonObject();
			assertEquals(GenerationConversationLog.REPLAY_SCHEMA, replay.get("schema").getAsString());
			assertEquals("failed", replay.get("status").getAsString());
			assertEquals(conversationId + ".json", replay.get("nativeConversationFile").getAsString());
			assertEquals(4, replay.getAsJsonArray("input").size());
			assertEquals("encrypted-reasoning-payload", replay.getAsJsonArray("input").get(1)
					.getAsJsonObject().get("encrypted_content").getAsString());

			List<JsonObject> events = Files.readAllLines(logDirectory.resolve("events.jsonl")).stream()
					.map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
			assertEquals(java.util.stream.IntStream.range(0, events.size()).boxed().toList(),
					events.stream().map(event -> event.get("sequence").getAsInt()).toList());
			JsonObject toolEvent = events.stream()
					.filter(event -> event.get("type").getAsString().equals("tool.execution"))
					.findFirst().orElseThrow();
			assertEquals("reference/API.md", toolEvent.getAsJsonObject("arguments").get("path").getAsString());
			assertEquals("complete tool result", toolEvent.getAsJsonObject("result").get("content").getAsString());
			assertEquals(12.5, toolEvent.get("durationMillis").getAsDouble());
			assertTrue(events.stream().anyMatch(event -> event.get("type").getAsString().equals("generation.failed")));

			String transcript = Files.readString(logDirectory.resolve("transcript.md"));
			assertTrue(transcript.contains("Inspect the API first."));
			assertTrue(transcript.contains("Tool call: `read`"));
			assertTrue(transcript.contains("complete tool result"));
			String logsReadme = Files.readString(world.resolve("logs/README.md"));
			assertTrue(logsReadme.contains("native Exocortex v17 conversation file"));
			assertTrue(logsReadme.contains("copy or import that file directly"));
		}
	}

	@Test
	void recordsARecipeRejectionAsATerminalConversationOutcome() throws Exception {
		Path world = temporaryDirectory.resolve("rejected-world");
		Path job = temporaryDirectory.resolve("rejected-job");
		try (GenerationWorkspace workspace = GenerationWorkspace.testing(world, job)) {
			Files.writeString(job.resolve("request.json"), """
					{"mode":"recipe","workstationPolicy":"Reject invalid recipes."}
					""");
			JsonArray history = initialHistory();
			WorkstationRecipeRejection rejection = new WorkstationRecipeRejection(
					WorkstationRecipeRejection.Category.WORKSTATION_TOO_WEAK,
					"This workstation is too weak for the requested transformation.");
			Path logDirectory;
			try (GenerationConversationLog log = GenerationConversationLog.open(
					workspace, "gpt-test", "medium", "test prompt",
					GeneralistGenerationTools.definitions(), history)) {
				logDirectory = log.directory();
				log.recordRejected(rejection, history);
			}

			JsonObject conversation = JsonParser.parseString(
					Files.readString(logDirectory.resolve("conversation.json"))).getAsJsonObject();
			assertEquals("Reject workstation recipe", conversation.get("title").getAsString());
			JsonArray messages = conversation.getAsJsonArray("messages");
			assertTrue(messages.get(messages.size() - 1).getAsJsonObject()
					.get("content").getAsString().contains(rejection.description()));
			JsonObject replay = JsonParser.parseString(
					Files.readString(logDirectory.resolve("responses-replay.json"))).getAsJsonObject();
			assertEquals("rejected", replay.get("status").getAsString());
			assertTrue(Files.readString(logDirectory.resolve("events.jsonl")).contains("generation.rejected"));
		}
	}

	private static JsonArray initialHistory() {
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", "Implement the request.");
		JsonArray content = new JsonArray();
		content.add(text);
		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		message.add("content", content);
		JsonArray history = new JsonArray();
		history.add(message);
		return history;
	}
}
