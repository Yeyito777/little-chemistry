package com.yeyito.littlechemistry.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class OpenAiClient {
	public static final String DEFAULT_MODEL = "gpt-5.6-sol";
	public static final String DEFAULT_REASONING_EFFORT = "medium";

	private static final Gson GSON = new Gson();
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiClient.class);
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
	private static final int MAX_RETRIES = 8;
	private static final Set<Integer> RETRIABLE_STATUS_CODES = Set.of(
			429, 500, 502, 503, 504, 507, 520, 521, 522, 523, 524
	);
	private static final Set<String> TERMINAL_ERROR_TYPES = Set.of(
			"authentication_error", "invalid_request_error", "not_found_error",
			"permission_error", "unprocessable_entity_error"
	);
	private static final Set<String> TERMINAL_ERROR_CODES = Set.of(
			"account_deactivated", "billing_hard_limit_reached", "billing_not_active",
			"content_policy_violation", "insufficient_quota", "invalid_api_key", "invalid_prompt",
			"model_not_found", "organization_deactivated", "terms_of_use_violation",
			"unsupported_country_region_territory", "unsupported_value"
	);
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final AuthConfig authConfig;
	private final String model;
	private final String reasoningEffort;
	private final HttpClient http;
	private final URI subscriptionResponsesUri;
	private final URI apiResponsesUri;
	private final RetrySleeper retrySleeper;

	public OpenAiClient(AuthConfig authConfig) {
		this(authConfig, DEFAULT_MODEL, DEFAULT_REASONING_EFFORT);
	}

	public OpenAiClient(AuthConfig authConfig, String model, String reasoningEffort) {
		this(authConfig, model, reasoningEffort, HTTP, SUBSCRIPTION_RESPONSES_URI, API_RESPONSES_URI, Thread::sleep);
	}

	OpenAiClient(AuthConfig authConfig, String model, String reasoningEffort, HttpClient http,
			URI subscriptionResponsesUri, URI apiResponsesUri, RetrySleeper retrySleeper) {
		this.authConfig = Objects.requireNonNull(authConfig, "authConfig");
		this.model = Objects.requireNonNull(model, "model");
		this.reasoningEffort = Objects.requireNonNull(reasoningEffort, "reasoningEffort");
		this.http = Objects.requireNonNull(http, "http");
		this.subscriptionResponsesUri = Objects.requireNonNull(subscriptionResponsesUri, "subscriptionResponsesUri");
		this.apiResponsesUri = Objects.requireNonNull(apiResponsesUri, "apiResponsesUri");
		this.retrySleeper = Objects.requireNonNull(retrySleeper, "retrySleeper");
	}

	public String model() {
		return model;
	}

	public String ask(String question) throws IOException, InterruptedException {
		return sendWithRetries(credentials -> buildRequest(credentials, question), lines -> {
			String answer = readServerSentEvents(lines);
			if (answer.isBlank()) {
				throw new OpenAiRequestException("OpenAI returned an empty response.", false);
			}
			return answer.trim();
		});
	}

	public ToolRound runToolRound(String instructions, JsonArray tools, JsonArray input)
			throws IOException, InterruptedException {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
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
		reasoning.addProperty("effort", reasoningEffort);
		reasoning.addProperty("summary", "detailed");
		body.add("reasoning", reasoning);
		body.addProperty("stream", true);
		body.addProperty("store", false);

		return sendWithRetries(credentials -> buildJsonRequest(credentials, toolRequestBody(body, credentials.mode())),
				OpenAiClient::readToolEvents);
	}

	private static JsonObject toolRequestBody(JsonObject base, AuthMode authMode) {
		JsonObject body = base.deepCopy();
		if (authMode == AuthMode.SUBSCRIPTION) {
			JsonObject clientMetadata = new JsonObject();
			clientMetadata.addProperty("x-codex-installation-id", CODEX_INSTALLATION_ID);
			body.add("client_metadata", clientMetadata);
		}
		return body;
	}

	private OpenAiCredentials credentialsForCurrentMode() throws IOException {
		if (authConfig.mode() == AuthMode.API_KEY) {
			return new OpenAiCredentials(AuthMode.API_KEY, authConfig.readApiKey(), null, "API key");
		}
		// Deliberately re-read external credential storage for every request.
		return SubscriptionCredentials.loadFresh();
	}

	private HttpRequest buildRequest(OpenAiCredentials credentials, String question) {
		return buildJsonRequest(credentials,
				GSON.fromJson(requestBody(question, credentials.mode(), model, reasoningEffort), JsonObject.class));
	}

	private HttpRequest buildJsonRequest(OpenAiCredentials credentials, JsonObject body) {
		URI uri = credentials.mode() == AuthMode.SUBSCRIPTION ? subscriptionResponsesUri : apiResponsesUri;
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

	private <T> T sendWithRetries(RequestFactory requestFactory, ResponseReader<T> responseReader)
			throws IOException, InterruptedException {
		int retries = 0;
		while (true) {
			throwIfInterrupted();
			// Authentication failures are configuration errors, not transient transport
			// failures. Re-read credentials before every actual request so an external
			// Exocortex or Codex refresh is visible to each retry.
			OpenAiCredentials credentials = credentialsForCurrentMode();
			IOException failure;
			try {
				return sendOnce(requestFactory.create(credentials), responseReader);
			} catch (OpenAiRequestException requestFailure) {
				if (!requestFailure.retryable()) throw requestFailure;
				failure = requestFailure;
			} catch (UncheckedIOException unchecked) {
				failure = unchecked.getCause();
			} catch (IOException terminalFailure) {
				throw terminalFailure;
			}
			throwIfInterrupted();

			if (retries >= MAX_RETRIES) {
				throw new IOException("OpenAI request failed after " + MAX_RETRIES + " retries: "
						+ safeError(failure.getMessage()), failure);
			}

			long delayMillis = retryDelayMillis(retries);
			LOGGER.warn(
					"OpenAI request failed; retrying in {} ms ({}/{}): {}",
					delayMillis, retries + 1, MAX_RETRIES, safeError(failure.getMessage())
			);
			retrySleeper.sleep(delayMillis);
			retries++;
		}
	}

	private <T> T sendOnce(HttpRequest request, ResponseReader<T> responseReader)
			throws IOException, InterruptedException {
		HttpResponse<Stream<String>> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofLines());
		} catch (IOException transportFailure) {
			throw new OpenAiRequestException(transportFailure.getMessage(), true, transportFailure);
		}
		try (Stream<String> lines = response.body()) {
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				String body = lines.limit(20).reduce("", (left, right) -> left + right + "\n").trim();
				throw httpFailure(response.statusCode(), body);
			}
			return responseReader.read(lines);
		}
	}

	private static OpenAiRequestException httpFailure(int status, String body) {
		boolean usageLimit = status == 429 && hasErrorType(body, "usage_limit_reached");
		// Match Exocortex: OpenAI/Cloudflare occasionally sends a bare 403 as a
		// transient edge refusal. A descriptive 403 remains terminal.
		boolean retryable = !usageLimit && (RETRIABLE_STATUS_CODES.contains(status)
				|| (status == 403 && body.isBlank()));
		String detail = body.isBlank() ? "<empty body>" : safeError(body);
		return new OpenAiRequestException(
				"OpenAI request failed (HTTP " + status + "): " + detail, retryable
		);
	}

	private static void throwIfInterrupted() throws InterruptedException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("OpenAI request was interrupted");
		}
	}

	private static long retryDelayMillis(int retry) {
		long exponential = Math.min(1_000L << Math.min(retry, 30), 30_000L);
		return exponential + ThreadLocalRandom.current().nextLong(1_001L);
	}

	private static boolean hasErrorType(String json, String expectedType) {
		try {
			JsonObject root = GSON.fromJson(json, JsonObject.class);
			JsonObject error = root == null ? null : object(root, "error");
			return error != null && expectedType.equals(string(error, "type"));
		} catch (Exception ignored) {
			return false;
		}
	}

	private static ToolRound readToolEvents(Stream<String> lines) throws IOException, InterruptedException {
		Map<Integer, JsonObject> outputItems = new HashMap<>();
		Map<Integer, StringBuilder> argumentDeltas = new HashMap<>();
		List<String> observedEventTypes = new ArrayList<>();
		boolean responseCompleted = false;
		for (String line : (Iterable<String>) lines::iterator) {
			throwIfInterrupted();
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if (data.isEmpty()) {
				continue;
			}
			if ("[DONE]".equals(data)) break;
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
				responseCompleted = true;
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
				break;
			} else if ("response.failed".equals(type) || "error".equals(type)) {
				throw eventFailure(event);
			}
		}
		if (!responseCompleted) {
			throw new OpenAiRequestException("OpenAI stream ended before response.completed", true);
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
				throw new OpenAiRequestException("OpenAI tool arguments exceeded the safety limit", false);
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
			LOGGER.warn("OpenAI tool round completed without function calls; events: {}; output items: {}",
					observedEventTypes, replayItems);
		}
		return new ToolRound(List.copyOf(calls), replayItems);
	}

	private static String requestBody(String question, AuthMode authMode, String model, String reasoningEffort) {
		JsonObject root = new JsonObject();
		root.addProperty("model", model);
		root.addProperty("instructions", SYSTEM_PROMPT);
		root.addProperty("service_tier", "priority");
		root.addProperty("tool_choice", "auto");
		root.addProperty("parallel_tool_calls", true);
		// No tools field is sent, so the model cannot call any tools.
		JsonArray include = new JsonArray();
		include.add("reasoning.encrypted_content");
		root.add("include", include);

		JsonObject reasoning = new JsonObject();
		reasoning.addProperty("effort", reasoningEffort);
		reasoning.addProperty("summary", "detailed");
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

	private static String readServerSentEvents(Stream<String> lines) throws IOException, InterruptedException {
		StringBuilder text = new StringBuilder();
		boolean responseCompleted = false;
		for (String line : (Iterable<String>) lines::iterator) {
			throwIfInterrupted();
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if (data.isEmpty()) {
				continue;
			}
			if ("[DONE]".equals(data)) break;

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
			} else if ("response.completed".equals(type)) {
				responseCompleted = true;
				if (text.isEmpty()) appendCompletedResponseText(text, event.get("response"));
				break;
			} else if ("response.failed".equals(type) || "error".equals(type)) {
				throw eventFailure(event);
			}
		}
		if (!responseCompleted) {
			throw new OpenAiRequestException("OpenAI stream ended before response.completed", true);
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
		JsonObject error = eventError(event);
		String errorMessage = error == null ? null : string(error, "message");
		if (errorMessage != null) return safeError(errorMessage);
		String message = string(event, "message");
		return message == null ? "unknown provider error" : safeError(message);
	}

	private static OpenAiRequestException eventFailure(JsonObject event) {
		String message = "OpenAI response failed: " + extractEventError(event);
		Integer status = integer(event, "status");
		if (status == null) status = integer(event, "status_code");
		if (status != null) {
			JsonObject error = eventError(event);
			boolean usageLimit = status == 429 && error != null
					&& "usage_limit_reached".equals(string(error, "type"));
			boolean retryable = !usageLimit && RETRIABLE_STATUS_CODES.contains(status);
			return new OpenAiRequestException(message, retryable);
		}

		JsonObject error = eventError(event);
		String code = error == null ? null : string(error, "code");
		String type = error == null ? null : string(error, "type");
		boolean retryable = !isContextWindowError(message, code, type)
				&& !containsIgnoreCase(TERMINAL_ERROR_TYPES, type)
				&& !containsIgnoreCase(TERMINAL_ERROR_CODES, code);
		return new OpenAiRequestException(message, retryable);
	}

	private static boolean containsIgnoreCase(Set<String> values, String candidate) {
		return candidate != null && values.contains(candidate.toLowerCase(Locale.ROOT));
	}

	private static JsonObject eventError(JsonObject event) {
		JsonObject direct = object(event, "error");
		if (direct != null) return direct;
		JsonObject response = object(event, "response");
		return response == null ? null : object(response, "error");
	}

	private static boolean isContextWindowError(String... values) {
		for (String value : values) {
			if (value == null) continue;
			String normalized = value.toLowerCase(Locale.ROOT);
			if (normalized.contains("context_length_exceeded")
					|| normalized.contains("maximum context length")
					|| normalized.contains("too many tokens")
					|| (normalized.contains("context window")
					&& (normalized.contains("exceed") || normalized.contains("too large")))
					|| (normalized.contains("input") && normalized.contains("exceed")
					&& normalized.contains("context"))) {
				return true;
			}
		}
		return false;
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

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static Integer integer(JsonObject object, String key) {
		JsonElement value = object.get(key);
		try {
			return value != null && value.isJsonPrimitive() ? value.getAsInt() : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(long delayMillis) throws InterruptedException;
	}

	@FunctionalInterface
	private interface RequestFactory {
		HttpRequest create(OpenAiCredentials credentials);
	}

	@FunctionalInterface
	private interface ResponseReader<T> {
		T read(Stream<String> lines) throws IOException, InterruptedException;
	}

	private static final class OpenAiRequestException extends IOException {
		private final boolean retryable;

		private OpenAiRequestException(String message, boolean retryable) {
			super(message);
			this.retryable = retryable;
		}

		private OpenAiRequestException(String message, boolean retryable, Throwable cause) {
			super(message, cause);
			this.retryable = retryable;
		}

		private boolean retryable() {
			return retryable;
		}
	}

	public record ToolCall(String callId, String name, JsonObject arguments) {
	}

	public record ToolRound(List<ToolCall> calls, JsonArray outputItems) {
	}
}
