package com.yeyito.littlechemistry.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class OpenAiClient {
	public static final String MODEL = "gpt-5.6-sol";

	private static final Gson GSON = new Gson();
	private static final URI SUBSCRIPTION_RESPONSES_URI = URI.create(
			System.getProperty("littlechemistry.chatgptBaseUrl", "https://chatgpt.com").replaceAll("/+$", "")
					+ "/backend-api/codex/responses"
	);
	private static final URI API_RESPONSES_URI = URI.create(
			System.getProperty("littlechemistry.apiBaseUrl", "https://api.openai.com").replaceAll("/+$", "")
					+ "/v1/responses"
	);
	private static final String SYSTEM_PROMPT =
			"You are Little Chemistry's in-game AI assistant. Answer the player's question directly and concisely. " +
			"You have no tools and must not claim to have used tools.";
	private static final String CODEX_CLIENT_VERSION = "0.99.0";
	private static final String CODEX_INSTALLATION_ID = UUID.randomUUID().toString();
	private static final String CODEX_USER_AGENT = "codex_cli_rs/" + CODEX_CLIENT_VERSION + " (" +
			System.getProperty("os.name").toLowerCase() + " " + System.getProperty("os.version") + "; " +
			System.getProperty("os.arch") + ") little-chemistry/1.2";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final AuthConfig authConfig;

	public OpenAiClient(AuthConfig authConfig) {
		this.authConfig = authConfig;
	}

	public String ask(String question) throws IOException, InterruptedException {
		OpenAiCredentials credentials = credentialsForCurrentMode();
		HttpRequest request = buildRequest(credentials, question);
		HttpResponse<Stream<String>> response = HTTP.send(request, HttpResponse.BodyHandlers.ofLines());

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			try (Stream<String> lines = response.body()) {
				String body = lines.limit(20).reduce("", (left, right) -> left + right + "\n").trim();
				throw new IOException("OpenAI request failed (HTTP " + response.statusCode() + "): " + safeError(body));
			}
		}

		try (Stream<String> lines = response.body()) {
			String answer = readServerSentEvents(lines);
			if (answer.isBlank()) {
				throw new IOException("OpenAI returned an empty response.");
			}
			return answer.trim();
		}
	}

	public ToolRound runToolRound(String instructions, JsonArray tools, JsonArray input)
			throws IOException, InterruptedException {
		OpenAiCredentials credentials = credentialsForCurrentMode();
		JsonObject body = new JsonObject();
		body.addProperty("model", MODEL);
		body.addProperty("instructions", instructions);
		body.addProperty("service_tier", "priority");
		body.addProperty("tool_choice", "required");
		body.addProperty("parallel_tool_calls", false);
		body.add("tools", tools.deepCopy());
		body.add("input", input.deepCopy());
		JsonArray include = new JsonArray();
		include.add("reasoning.encrypted_content");
		body.add("include", include);
		JsonObject reasoning = new JsonObject();
		reasoning.addProperty("effort", "medium");
		body.add("reasoning", reasoning);
		if (credentials.mode() == AuthMode.SUBSCRIPTION) {
			JsonObject clientMetadata = new JsonObject();
			clientMetadata.addProperty("x-codex-installation-id", CODEX_INSTALLATION_ID);
			body.add("client_metadata", clientMetadata);
		}
		body.addProperty("stream", true);
		body.addProperty("store", false);

		HttpRequest request = buildJsonRequest(credentials, body);
		HttpResponse<Stream<String>> response = HTTP.send(request, HttpResponse.BodyHandlers.ofLines());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			try (Stream<String> lines = response.body()) {
				String error = lines.limit(20).reduce("", (left, right) -> left + right + "\n").trim();
				throw new IOException("OpenAI request failed (HTTP " + response.statusCode() + "): " + safeError(error));
			}
		}
		try (Stream<String> lines = response.body()) {
			return readToolEvents(lines);
		}
	}

	private OpenAiCredentials credentialsForCurrentMode() throws IOException {
		if (authConfig.mode() == AuthMode.API_KEY) {
			return new OpenAiCredentials(AuthMode.API_KEY, authConfig.readApiKey(), null, "API key");
		}
		// Deliberately re-read external credential storage for every request.
		return SubscriptionCredentials.loadFresh();
	}

	private static HttpRequest buildRequest(OpenAiCredentials credentials, String question) {
		return buildJsonRequest(credentials, GSON.fromJson(requestBody(question, credentials.mode()), JsonObject.class));
	}

	private static HttpRequest buildJsonRequest(OpenAiCredentials credentials, JsonObject body) {
		URI uri = credentials.mode() == AuthMode.SUBSCRIPTION ? SUBSCRIPTION_RESPONSES_URI : API_RESPONSES_URI;
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.header("Authorization", "Bearer " + credentials.secret())
				.header("Content-Type", "application/json")
				.header("Accept", "text/event-stream")
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));

		if (credentials.mode() == AuthMode.SUBSCRIPTION) {
			// Match the Codex HTTP/SSE request identity used by Exocortex. GPT-5.6
			// tier routing on the subscription backend depends on this request shape.
			builder.header("originator", "codex_cli_rs");
			builder.header("User-Agent", CODEX_USER_AGENT);
			builder.header("x-codex-beta-features", "remote_compaction_v2");
			builder.header("x-codex-installation-id", CODEX_INSTALLATION_ID);
			if (credentials.accountId() != null) {
				builder.header("ChatGPT-Account-ID", credentials.accountId());
			}
		} else {
			builder.header("User-Agent", "little-chemistry/1.2");
		}
		return builder.build();
	}

	private static ToolRound readToolEvents(Stream<String> lines) throws IOException {
		Map<Integer, JsonObject> outputItems = new HashMap<>();
		Map<Integer, StringBuilder> argumentDeltas = new HashMap<>();
		List<String> observedEventTypes = new ArrayList<>();
		for (String line : (Iterable<String>) lines::iterator) {
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if (data.isEmpty() || "[DONE]".equals(data)) {
				continue;
			}
			JsonObject event;
			try {
				event = GSON.fromJson(data, JsonObject.class);
			} catch (Exception ignored) {
				continue;
			}
			if (event == null) {
				continue;
			}
			String type = string(event, "type");
			if (type != null && !observedEventTypes.contains(type)) observedEventTypes.add(type);
			int outputIndex = event.has("output_index") ? event.get("output_index").getAsInt() : outputItems.size();
			if ("response.output_item.added".equals(type) && event.get("item") instanceof JsonObject item) {
				outputItems.put(outputIndex, item.deepCopy());
				if ("function_call".equals(string(item, "type"))) {
					argumentDeltas.put(outputIndex, new StringBuilder());
				}
			} else if ("response.function_call_arguments.delta".equals(type)) {
				StringBuilder arguments = argumentDeltas.get(outputIndex);
				if (arguments != null) {
					String delta = string(event, "delta");
					if (delta != null) arguments.append(delta);
				}
			} else if ("response.function_call_arguments.done".equals(type)) {
				String arguments = string(event, "arguments");
				if (arguments != null) argumentDeltas.put(outputIndex, new StringBuilder(arguments));
			} else if ("response.output_item.done".equals(type) && event.get("item") instanceof JsonObject item) {
				JsonObject completed = item.deepCopy();
				StringBuilder arguments = argumentDeltas.get(outputIndex);
				if ("function_call".equals(string(completed, "type")) && arguments != null && !arguments.isEmpty()) {
					completed.addProperty("arguments", arguments.toString());
				}
				outputItems.put(outputIndex, completed);
			} else if ("response.completed".equals(type) && event.get("response") instanceof JsonObject completedResponse) {
				JsonArray canonicalOutput = array(completedResponse, "output");
				// The Codex SSE backend may leave response.completed.output empty after
				// already delivering canonical response.output_item.done events.
				if (canonicalOutput != null && !canonicalOutput.isEmpty()) {
					outputItems.clear();
					for (int index = 0; index < canonicalOutput.size(); index++) {
						if (canonicalOutput.get(index) instanceof JsonObject item) {
							outputItems.put(index, item.deepCopy());
						}
					}
				}
			} else if ("response.failed".equals(type) || "error".equals(type)) {
				throw new IOException("OpenAI response failed: " + extractEventError(event));
			}
		}

		List<Map.Entry<Integer, JsonObject>> ordered = new ArrayList<>(outputItems.entrySet());
		ordered.sort(Comparator.comparingInt(Map.Entry::getKey));
		JsonArray replayItems = new JsonArray();
		List<ToolCall> calls = new ArrayList<>();
		for (Map.Entry<Integer, JsonObject> entry : ordered) {
			JsonObject item = entry.getValue();
			replayItems.add(item.deepCopy());
			if (!"function_call".equals(string(item, "type"))) {
				continue;
			}
			String callId = string(item, "call_id");
			String name = string(item, "name");
			String rawArguments = string(item, "arguments");
			if (callId == null || callId.isBlank() || name == null || name.isBlank()) {
				continue;
			}
			if (rawArguments == null) {
				rawArguments = "{}";
			}
			if (rawArguments.length() > 65_536) {
				throw new IOException("OpenAI tool arguments exceeded the safety limit");
			}
			JsonObject arguments;
			try {
				arguments = GSON.fromJson(rawArguments, JsonObject.class);
				if (arguments == null) {
					arguments = new JsonObject();
				}
			} catch (Exception error) {
				arguments = new JsonObject();
				arguments.addProperty("_malformed", safeError(error.getMessage()));
			}
			calls.add(new ToolCall(callId, name, arguments));
		}
		if (calls.isEmpty()) {
			LittleChemistry.LOGGER.warn("OpenAI tool round completed without function calls; events: {}; output items: {}",
					observedEventTypes, replayItems);
		}
		return new ToolRound(List.copyOf(calls), replayItems);
	}

	private static String requestBody(String question, AuthMode authMode) {
		JsonObject root = new JsonObject();
		root.addProperty("model", MODEL);
		root.addProperty("instructions", SYSTEM_PROMPT);
		root.addProperty("service_tier", "priority");
		root.addProperty("tool_choice", "auto");
		root.addProperty("parallel_tool_calls", true);
		// No tools field is sent, so the model cannot call any tools.
		JsonArray include = new JsonArray();
		include.add("reasoning.encrypted_content");
		root.add("include", include);

		JsonObject reasoning = new JsonObject();
		reasoning.addProperty("effort", "medium");
		root.add("reasoning", reasoning);

		JsonObject content = new JsonObject();
		content.addProperty("type", "input_text");
		content.addProperty("text", question);
		JsonArray contents = new JsonArray();
		contents.add(content);

		JsonObject message = new JsonObject();
		message.addProperty("type", "message");
		message.addProperty("role", "user");
		message.add("content", contents);
		JsonArray input = new JsonArray();
		input.add(message);
		root.add("input", input);

		if (authMode == AuthMode.SUBSCRIPTION) {
			JsonObject clientMetadata = new JsonObject();
			clientMetadata.addProperty("x-codex-installation-id", CODEX_INSTALLATION_ID);
			root.add("client_metadata", clientMetadata);
		}

		root.addProperty("stream", true);
		root.addProperty("store", false);
		return GSON.toJson(root);
	}

	private static String readServerSentEvents(Stream<String> lines) throws IOException {
		StringBuilder text = new StringBuilder();
		for (String line : (Iterable<String>) lines::iterator) {
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if (data.isEmpty() || "[DONE]".equals(data)) {
				continue;
			}

			JsonObject event;
			try {
				event = GSON.fromJson(data, JsonObject.class);
			} catch (Exception ignored) {
				continue;
			}
			if (event == null) {
				continue;
			}

			String type = string(event, "type");
			if ("response.output_text.delta".equals(type)) {
				String delta = string(event, "delta");
				if (delta != null) {
					text.append(delta);
				}
			} else if ("response.output_text.done".equals(type) && text.isEmpty()) {
				String completed = string(event, "text");
				if (completed != null) {
					text.append(completed);
				}
			} else if ("response.completed".equals(type) && text.isEmpty()) {
				appendCompletedResponseText(text, event.get("response"));
			} else if ("response.failed".equals(type) || "error".equals(type)) {
				throw new IOException("OpenAI response failed: " + extractEventError(event));
			}
		}
		return text.toString();
	}

	private static void appendCompletedResponseText(StringBuilder destination, JsonElement responseElement) {
		if (responseElement == null || !responseElement.isJsonObject()) {
			return;
		}
		JsonArray output = array(responseElement.getAsJsonObject(), "output");
		if (output == null) {
			return;
		}
		for (JsonElement itemElement : output) {
			if (!itemElement.isJsonObject()) {
				continue;
			}
			JsonArray content = array(itemElement.getAsJsonObject(), "content");
			if (content == null) {
				continue;
			}
			for (JsonElement partElement : content) {
				if (!partElement.isJsonObject()) {
					continue;
				}
				JsonObject part = partElement.getAsJsonObject();
				if ("output_text".equals(string(part, "type"))) {
					String value = string(part, "text");
					if (value != null) {
						destination.append(value);
					}
				}
			}
		}
	}

	private static String extractEventError(JsonObject event) {
		JsonElement error = event.get("error");
		if (error != null && error.isJsonObject()) {
			String message = string(error.getAsJsonObject(), "message");
			if (message != null) {
				return safeError(message);
			}
		}
		String message = string(event, "message");
		return message == null ? "unknown provider error" : safeError(message);
	}

	private static String safeError(String message) {
		String normalized = message == null ? "unknown provider error" : message.replaceAll("[\\r\\n]+", " ").trim();
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static JsonArray array(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	public record ToolCall(String callId, String name, JsonObject arguments) {
	}

	public record ToolRound(List<ToolCall> calls, JsonArray outputItems) {
	}
}
