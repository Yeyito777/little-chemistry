package com.yeyito.littlechemistry.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class OpenAiClient {
	public static final String DEFAULT_MODEL = "gpt-5.6-sol";
	public static final String DEFAULT_REASONING_EFFORT = "medium";

	private static final Gson GSON = new Gson();
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiClient.class);
	private static final URI SUBSCRIPTION_RESPONSES_URI = websocketUri(URI.create(
			System.getProperty("littlechemistry.chatgptBaseUrl", "https://chatgpt.com").replaceAll("/+$", "")
					+ "/backend-api/codex/responses"
	));
	private static final URI API_RESPONSES_URI = websocketUri(URI.create(
			System.getProperty("littlechemistry.apiBaseUrl", "https://api.openai.com").replaceAll("/+$", "")
					+ "/v1/responses"
	));
	private static final String SYSTEM_PROMPT =
			"You are Little Chemistry's in-game AI assistant. Answer the player's question directly and concisely. " +
			"You have no tools and must not claim to have used tools.";
	private static final String CODEX_CLIENT_VERSION = "0.99.0";
	private static final String CODEX_INSTALLATION_ID = UUID.randomUUID().toString();
	private static final String CODEX_USER_AGENT = "codex_cli_rs/" + CODEX_CLIENT_VERSION + " (" +
			System.getProperty("os.name").toLowerCase() + " " + System.getProperty("os.version") + "; " +
			System.getProperty("os.arch") + ") little-chemistry/1.2";
	private static final int MAX_RETRIES = 8;
	private static final String RESPONSES_WEBSOCKET_BETA = "responses_websockets=2026-02-06";
	private static final String WEBSOCKET_CONNECTION_LIMIT_CODE = "websocket_connection_limit_reached";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration STREAM_STALL_TIMEOUT = Duration.ofMinutes(5);
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
	private final String model;
	private final String reasoningEffort;
	private final URI subscriptionResponsesUri;
	private final URI apiResponsesUri;
	private final RetrySleeper retrySleeper;
	private final CredentialsProvider credentialsProvider;

	public OpenAiClient(AuthConfig authConfig) {
		this(authConfig, DEFAULT_MODEL, DEFAULT_REASONING_EFFORT);
	}

	public OpenAiClient(AuthConfig authConfig, String model, String reasoningEffort) {
		this(authConfig, model, reasoningEffort, SUBSCRIPTION_RESPONSES_URI, API_RESPONSES_URI, Thread::sleep);
	}

	OpenAiClient(AuthConfig authConfig, String model, String reasoningEffort,
			URI subscriptionResponsesUri, URI apiResponsesUri, RetrySleeper retrySleeper) {
		this(authConfig, model, reasoningEffort, subscriptionResponsesUri, apiResponsesUri, retrySleeper,
				() -> credentialsFor(Objects.requireNonNull(authConfig, "authConfig")));
	}

	OpenAiClient(AuthConfig authConfig, String model, String reasoningEffort,
			URI subscriptionResponsesUri, URI apiResponsesUri, RetrySleeper retrySleeper,
			CredentialsProvider credentialsProvider) {
		Objects.requireNonNull(authConfig, "authConfig");
		this.model = Objects.requireNonNull(model, "model");
		this.reasoningEffort = Objects.requireNonNull(reasoningEffort, "reasoningEffort");
		this.subscriptionResponsesUri = websocketUri(Objects.requireNonNull(
				subscriptionResponsesUri, "subscriptionResponsesUri"));
		this.apiResponsesUri = websocketUri(Objects.requireNonNull(apiResponsesUri, "apiResponsesUri"));
		this.retrySleeper = Objects.requireNonNull(retrySleeper, "retrySleeper");
		this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
	}

	public String model() {
		return model;
	}

	public String reasoningEffort() {
		return reasoningEffort;
	}

	public String ask(String question) throws IOException, InterruptedException {
		return sendWithRetries(credentials -> requestBody(question, credentials.mode(), model, reasoningEffort), events -> {
			String answer = readWebSocketEvents(events);
			if (answer.isBlank()) {
				throw new OpenAiRequestException("OpenAI returned an empty response.", false);
			}
			return answer.trim();
		});
	}

	public ToolRound runToolRound(String instructions, JsonArray tools, JsonArray input)
			throws IOException, InterruptedException {
		try (ToolSession session = openToolSession()) {
			return session.runToolRound(instructions, tools, input);
		}
	}

	/** Opens one serialized Responses websocket session for a model/tool conversation. */
	public ToolSession openToolSession() {
		return new ToolSession();
	}

	private JsonObject toolRoundRequest(String instructions, JsonArray tools, JsonArray input) {
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
		return body;
	}

	static JsonObject toolRequestBody(JsonObject base, AuthMode authMode) {
		JsonObject body = base.deepCopy();
		if (authMode == AuthMode.SUBSCRIPTION) {
			JsonObject clientMetadata = new JsonObject();
			clientMetadata.addProperty("x-codex-installation-id", CODEX_INSTALLATION_ID);
			body.add("client_metadata", clientMetadata);
			body.addProperty("stream", true);
		} else {
			// The public Responses websocket protocol is inherently streaming and rejects
			// transport-only stream/background fields in response.create.
			body.remove("stream");
		}
		return body;
	}

	private OpenAiCredentials credentialsForCurrentMode() throws IOException {
		return credentialsProvider.load();
	}

	private static OpenAiCredentials credentialsFor(AuthConfig authConfig) throws IOException {
		if (authConfig.mode() == AuthMode.API_KEY) {
			return new OpenAiCredentials(AuthMode.API_KEY, authConfig.readApiKey(), null, "API key");
		}
		// Deliberately re-read external credential storage for every request.
		return SubscriptionCredentials.loadFresh();
	}

	Map<String, String> websocketHeaders(OpenAiCredentials credentials, String turnState) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + credentials.secret());
		headers.put("Accept", "application/json");

		if (credentials.mode() == AuthMode.SUBSCRIPTION) {
			// Match the Codex websocket request identity used by Exocortex. GPT-5.6
			// tier routing on the subscription backend depends on this request shape.
			headers.put("originator", "codex_cli_rs");
			headers.put("User-Agent", CODEX_USER_AGENT);
			headers.put("OpenAI-Beta", RESPONSES_WEBSOCKET_BETA);
			headers.put("x-codex-beta-features", "remote_compaction_v2");
			headers.put("x-codex-installation-id", CODEX_INSTALLATION_ID);
			if (credentials.accountId() != null) {
				headers.put("ChatGPT-Account-ID", credentials.accountId());
			}
		} else {
			headers.put("User-Agent", "little-chemistry/1.2");
		}
		if (turnState != null) headers.put("x-codex-turn-state", turnState);
		return Map.copyOf(headers);
	}

	private <T> T sendWithRetries(RequestFactory requestFactory, ResponseReader<T> responseReader)
			throws IOException, InterruptedException {
		try (WebSocketSession session = new WebSocketSession()) {
			return sendWithRetries(requestFactory, responseReader, session);
		}
	}

	private <T> T sendWithRetries(RequestFactory requestFactory, ResponseReader<T> responseReader,
			WebSocketSession session) throws IOException, InterruptedException {
		int retries = 0;
		boolean retriedRefreshedAuthentication = false;
		while (true) {
			throwIfInterrupted();
			session.throwIfClosed();
			// Authentication failures are configuration errors, not transient transport
			// failures. Re-read credentials before every actual request so an external
			// Exocortex or Codex refresh is visible to each retry.
			OpenAiCredentials credentials = credentialsForCurrentMode();
			IOException failure;
			try {
				return sendOnce(credentials, requestFactory.create(credentials), responseReader, session);
			} catch (OpenAiRequestException requestFailure) {
				if (Integer.valueOf(401).equals(requestFailure.statusCode())
						&& !retriedRefreshedAuthentication) {
					OpenAiCredentials refreshed = credentialsForCurrentMode();
					if (!credentials.secret().equals(refreshed.secret())
							&& sameReplayScope(credentials, refreshed)) {
						retriedRefreshedAuthentication = true;
						continue;
					}
				}
				if (!requestFailure.retryable()) throw requestFailure;
				failure = requestFailure;
			} catch (IOException terminalFailure) {
				throw terminalFailure;
			}
			throwIfInterrupted();
			session.throwIfClosed();

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

	private <T> T sendOnce(OpenAiCredentials credentials, JsonObject fullRequestBody, ResponseReader<T> responseReader,
			WebSocketSession session) throws IOException, InterruptedException {
		OpenAiWebSocketConnection connection;
		try {
			connection = session.connection(credentials);
		} catch (OpenAiRequestException terminalSessionFailure) {
			throw terminalSessionFailure;
		} catch (OpenAiWebSocketConnection.HandshakeException handshake) {
			throw handshakeFailure(handshake.statusCode());
		} catch (IOException transportFailure) {
			throw new OpenAiRequestException(transportFailure.getMessage(), true, transportFailure);
		}
		try {
			JsonObject envelope = session.prepareRequestBody(fullRequestBody);
			envelope.addProperty("type", "response.create");
			connection.sendText(GSON.toJson(envelope));
			return responseReader.read(new WebSocketEventReader(connection, session::recordEvent));
		} catch (OpenAiRequestException requestFailure) {
			boolean retryWithoutIncremental = session.requestUsedPreviousResponseId()
					&& (Integer.valueOf(400).equals(requestFailure.statusCode())
					|| Integer.valueOf(404).equals(requestFailure.statusCode()));
			session.resetConnection();
			if (retryWithoutIncremental) {
				throw new OpenAiRequestException(requestFailure.getMessage(), true, requestFailure);
			}
			throw requestFailure;
		} catch (IOException transportFailure) {
			session.resetConnection();
			throw new OpenAiRequestException(transportFailure.getMessage(), true, transportFailure);
		} catch (InterruptedException interrupted) {
			session.resetConnection();
			throw interrupted;
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
				"OpenAI request failed (HTTP " + status + "): " + detail, retryable, status
		);
	}

	private static OpenAiRequestException handshakeFailure(int status) {
		// Java-WebSocket exposes the rejected status but not the HTTP response body.
		// Classify ambiguous 403/429 handshakes conservatively instead of treating a
		// descriptive policy refusal or usage_limit_reached as a transient failure.
		boolean retryable = status != 403 && status != 429 && RETRIABLE_STATUS_CODES.contains(status);
		return new OpenAiRequestException(
				"OpenAI websocket handshake failed (HTTP " + status + "; response body unavailable)",
				retryable,
				status
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

	private static boolean sameReplayScope(OpenAiCredentials left, OpenAiCredentials right) {
		if (left.mode() != right.mode()) return false;
		if (left.mode() == AuthMode.API_KEY) return left.secret().equals(right.secret());
		if (left.accountId() == null || right.accountId() == null) {
			return left.accountId() == null && right.accountId() == null && left.secret().equals(right.secret());
		}
		return left.accountId().equals(right.accountId());
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

	private static ToolRound readToolEvents(WebSocketEventReader events) throws IOException, InterruptedException {
		Map<Integer, JsonObject> outputItems = new HashMap<>();
		Map<Integer, StringBuilder> argumentDeltas = new HashMap<>();
		List<String> observedEventTypes = new ArrayList<>();
		JsonObject responseMetadata = new JsonObject();
		boolean responseCompleted = false;
		JsonObject event;
		while ((event = events.nextEvent()) != null) {
			throwIfInterrupted();
			String type = string(event, "type");
			if (type != null && !observedEventTypes.contains(type)) observedEventTypes.add(type);
			int outputIndex = event.has("output_index") ? event.get("output_index").getAsInt() : outputItems.size();
			if ("response.created".equals(type) && event.get("response") instanceof JsonObject created) {
				responseMetadata = created.deepCopy();
			} else if ("response.output_item.added".equals(type) && event.get("item") instanceof JsonObject item) {
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
			} else if ("response.completed".equals(type) && event.get("response") instanceof JsonObject completed) {
				responseCompleted = true;
				JsonObject createdMetadata = responseMetadata;
				responseMetadata = completed.deepCopy();
				if (!responseMetadata.has("id") && createdMetadata.has("id")) {
					responseMetadata.add("id", createdMetadata.get("id").deepCopy());
				}
				JsonArray canonicalOutput = array(completed, "output");
				// The Codex backend may leave response.completed.output empty after
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
			} else if ("response.incomplete".equals(type)) {
				throw incompleteResponse(event);
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
			if (rawArguments.length() > 1_048_576) {
				throw new OpenAiRequestException("OpenAI tool arguments exceeded the 1 MiB safety limit", false);
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
		return new ToolRound(List.copyOf(calls), replayItems, responseMetadata);
	}

	private static JsonObject requestBody(String question, AuthMode authMode, String model, String reasoningEffort) {
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
			root.addProperty("stream", true);
		}

		root.addProperty("store", false);
		return root;
	}

	private static String readWebSocketEvents(WebSocketEventReader events) throws IOException, InterruptedException {
		StringBuilder text = new StringBuilder();
		boolean responseCompleted = false;
		JsonObject event;
		while ((event = events.nextEvent()) != null) {
			throwIfInterrupted();
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
			} else if ("response.incomplete".equals(type)) {
				throw incompleteResponse(event);
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
		Integer status = integer(event, "status");
		if (status == null) status = integer(event, "status_code");
		JsonObject error = eventError(event);
		if (error != null && WEBSOCKET_CONNECTION_LIMIT_CODE.equals(string(error, "code"))) {
			return new OpenAiRequestException("OpenAI response failed: " + extractEventError(event), true, status);
		}
		if (status != null) return httpFailure(status, GSON.toJson(event));

		String message = "OpenAI response failed: " + extractEventError(event);
		String code = error == null ? null : string(error, "code");
		String type = error == null ? null : string(error, "type");
		boolean retryable = !isContextWindowError(message, code, type)
				&& !containsIgnoreCase(TERMINAL_ERROR_TYPES, type)
				&& !containsIgnoreCase(TERMINAL_ERROR_CODES, code);
		return new OpenAiRequestException(message, retryable);
	}

	private static OpenAiRequestException incompleteResponse(JsonObject event) {
		JsonObject response = object(event, "response");
		JsonObject details = response == null ? null : object(response, "incomplete_details");
		String reason = details == null ? null : string(details, "reason");
		return new OpenAiRequestException("OpenAI returned an incomplete response"
				+ (reason == null ? "" : ": " + safeError(reason)), false);
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

	static URI websocketUri(URI responsesUri) {
		String scheme = responsesUri.getScheme();
		if ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) return responsesUri;
		String websocketScheme;
		if ("http".equalsIgnoreCase(scheme)) websocketScheme = "ws";
		else if ("https".equalsIgnoreCase(scheme)) websocketScheme = "wss";
		else throw new IllegalArgumentException("Unsupported OpenAI Responses URI scheme: " + scheme);
		String raw = responsesUri.toString();
		return URI.create(websocketScheme + raw.substring(raw.indexOf(':')));
	}

	/**
	 * A single-flight model/tool session. Provider rounds are serialized on one socket and use
	 * previous_response_id only while the exact replay baseline remains valid.
	 */
	public final class ToolSession implements AutoCloseable {
		private final WebSocketSession transport = new WebSocketSession();
		private final ReentrantLock requestLock = new ReentrantLock();
		private final AtomicBoolean closed = new AtomicBoolean();
		private final AtomicBoolean inFlight = new AtomicBoolean();

		private ToolSession() {
		}

		public ToolRound runToolRound(String instructions, JsonArray tools, JsonArray input)
				throws IOException, InterruptedException {
			requestLock.lockInterruptibly();
			try {
				if (closed.get()) throw new IllegalStateException("OpenAI tool session is closed");
				inFlight.set(true);
				if (closed.get()) {
					transport.abortAndClose();
					throw new IllegalStateException("OpenAI tool session is closed");
				}
				JsonObject base = toolRoundRequest(instructions, tools, input);
				ToolRound round = sendWithRetries(credentials -> toolRequestBody(base, credentials.mode()),
						OpenAiClient::readToolEvents, transport);
				transport.recordSuccessfulRequest(base, round);
				return round;
			} finally {
				inFlight.set(false);
				requestLock.unlock();
			}
		}

		@Override
		public void close() {
			if (!closed.compareAndSet(false, true)) return;
			if (inFlight.get()) transport.abortAndClose();
			else transport.close();
		}
	}

	private final class WebSocketSession implements AutoCloseable {
		private volatile OpenAiWebSocketConnection activeConnection;
		private OpenAiCredentials connectedCredentials;
		private OpenAiCredentials replayScopeCredentials;
		private volatile boolean closed;
		private String turnState;
		private JsonObject lastFullRequestBody;
		private JsonArray lastResponseOutputItems;
		private String lastResponseId;
		private boolean requestUsedPreviousResponseId;

		private OpenAiWebSocketConnection connection(OpenAiCredentials credentials)
				throws IOException, InterruptedException {
			throwIfClosed();
			if (replayScopeCredentials == null) {
				replayScopeCredentials = credentials;
			} else if (!sameReplayScope(replayScopeCredentials, credentials)) {
				resetConnection();
				turnState = null;
				throw new OpenAiRequestException(
						"OpenAI authentication account changed during the active generation session", false);
			}
			if (activeConnection != null && !credentials.equals(connectedCredentials)) {
				resetConnection();
			}
			if (activeConnection == null) {
				URI uri = credentials.mode() == AuthMode.SUBSCRIPTION ? subscriptionResponsesUri : apiResponsesUri;
				OpenAiWebSocketConnection opening = new OpenAiWebSocketConnection(
						uri, websocketHeaders(credentials, turnState), CONNECT_TIMEOUT);
				activeConnection = opening;
				connectedCredentials = credentials;
				try {
					opening.open(CONNECT_TIMEOUT);
				} catch (IOException | InterruptedException failure) {
					if (activeConnection == opening) resetConnection();
					throw failure;
				}
				if (closed || activeConnection != opening) {
					opening.abort();
					throw new OpenAiRequestException("OpenAI tool session was closed while connecting", false);
				}
				String handshakeTurnState = opening.responseHeader("x-codex-turn-state");
				if (turnState == null && handshakeTurnState != null && !handshakeTurnState.isBlank()) {
					turnState = handshakeTurnState;
				}
			}
			return activeConnection;
		}

		private void throwIfClosed() throws OpenAiRequestException {
			if (closed) throw new OpenAiRequestException("OpenAI tool session is closed", false);
		}

		private JsonObject prepareRequestBody(JsonObject fullRequestBody) {
			requestUsedPreviousResponseId = false;
			JsonObject request = requestBodyWithTurnState(fullRequestBody);
			if (lastFullRequestBody == null || lastResponseOutputItems == null || lastResponseId == null) {
				return request;
			}
			if (!requestShape(lastFullRequestBody).equals(requestShape(fullRequestBody))) return request;
			JsonArray previousInput = array(lastFullRequestBody, "input");
			JsonArray currentInput = array(fullRequestBody, "input");
			if (previousInput == null || currentInput == null) return request;

			int baselineSize = previousInput.size() + lastResponseOutputItems.size();
			if (currentInput.size() <= baselineSize) return request;
			for (int index = 0; index < previousInput.size(); index++) {
				if (!previousInput.get(index).equals(currentInput.get(index))) return request;
			}
			for (int index = 0; index < lastResponseOutputItems.size(); index++) {
				if (!lastResponseOutputItems.get(index).equals(currentInput.get(previousInput.size() + index))) {
					return request;
				}
			}

			JsonArray incrementalInput = new JsonArray();
			for (int index = baselineSize; index < currentInput.size(); index++) {
				incrementalInput.add(currentInput.get(index).deepCopy());
			}
			request.addProperty("previous_response_id", lastResponseId);
			request.add("input", incrementalInput);
			requestUsedPreviousResponseId = true;
			return request;
		}

		private boolean requestUsedPreviousResponseId() {
			return requestUsedPreviousResponseId;
		}

		private JsonObject requestBodyWithTurnState(JsonObject fullRequestBody) {
			JsonObject request = fullRequestBody.deepCopy();
			if (turnState == null) return request;
			JsonObject clientMetadata = object(request, "client_metadata");
			if (clientMetadata == null) {
				clientMetadata = new JsonObject();
				request.add("client_metadata", clientMetadata);
			}
			clientMetadata.addProperty("x-codex-turn-state", turnState);
			return request;
		}

		private JsonObject requestShape(JsonObject body) {
			JsonObject shape = body.deepCopy();
			shape.remove("input");
			shape.remove("previous_response_id");
			JsonObject clientMetadata = object(shape, "client_metadata");
			if (clientMetadata != null) clientMetadata.remove("x-codex-turn-state");
			return shape;
		}

		private void recordEvent(JsonObject event) {
			if (turnState != null || !"response.metadata".equals(string(event, "type"))) return;
			JsonObject headers = object(event, "headers");
			if (headers == null) return;
			for (Map.Entry<String, JsonElement> header : headers.entrySet()) {
				if ("x-codex-turn-state".equalsIgnoreCase(header.getKey())
						&& header.getValue().isJsonPrimitive()) {
					String candidate = header.getValue().getAsString();
					if (!candidate.isBlank()) turnState = candidate;
					return;
				}
			}
		}

		private void recordSuccessfulRequest(JsonObject baseRequestBody, ToolRound round) {
			if (connectedCredentials == null) throw new IllegalStateException("OpenAI websocket is not connected");
			lastFullRequestBody = toolRequestBody(baseRequestBody, connectedCredentials.mode());
			lastResponseOutputItems = round.outputItems();
			lastResponseId = string(round.response(), "id");
		}

		private void resetConnection() {
			if (activeConnection != null) activeConnection.abort();
			activeConnection = null;
			connectedCredentials = null;
			lastFullRequestBody = null;
			lastResponseOutputItems = null;
			lastResponseId = null;
			requestUsedPreviousResponseId = false;
		}

		@Override
		public void close() {
			closed = true;
			OpenAiWebSocketConnection connection = activeConnection;
			if (connection != null) connection.close();
			activeConnection = null;
			connectedCredentials = null;
			replayScopeCredentials = null;
		}

		private void abortAndClose() {
			closed = true;
			OpenAiWebSocketConnection connection = activeConnection;
			if (connection != null) connection.abort();
			activeConnection = null;
			connectedCredentials = null;
			replayScopeCredentials = null;
		}
	}

	private static final class WebSocketEventReader {
		private final OpenAiWebSocketConnection connection;
		private final Consumer<JsonObject> eventObserver;

		private WebSocketEventReader(OpenAiWebSocketConnection connection, Consumer<JsonObject> eventObserver) {
			this.connection = connection;
			this.eventObserver = eventObserver;
		}

		private JsonObject nextEvent() throws IOException, InterruptedException {
			while (true) {
				OpenAiWebSocketConnection.IncomingMessage message = connection.nextMessage(STREAM_STALL_TIMEOUT);
				if (message instanceof OpenAiWebSocketConnection.Closed closed) {
					String reason = closed.reason() == null || closed.reason().isBlank()
							? "" : ": " + safeError(closed.reason());
					throw new IOException("OpenAI websocket closed before response.completed (code "
							+ closed.statusCode() + ")" + reason);
				}
				if (message instanceof OpenAiWebSocketConnection.BinaryMessage) {
					throw new IOException("OpenAI websocket returned an unexpected binary event");
				}
				String text = ((OpenAiWebSocketConnection.TextMessage) message).text();
				JsonObject event = null;
				try {
					event = GSON.fromJson(text, JsonObject.class);
				} catch (Exception ignored) {
					// Ignore non-JSON websocket messages just as the previous SSE transport ignored malformed events.
				}
				if (event == null) continue;
				eventObserver.accept(event);
				return event;
			}
		}
	}

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(long delayMillis) throws InterruptedException;
	}

	@FunctionalInterface
	interface CredentialsProvider {
		OpenAiCredentials load() throws IOException;
	}

	@FunctionalInterface
	private interface RequestFactory {
		JsonObject create(OpenAiCredentials credentials);
	}

	@FunctionalInterface
	private interface ResponseReader<T> {
		T read(WebSocketEventReader events) throws IOException, InterruptedException;
	}

	private static final class OpenAiRequestException extends IOException {
		private final boolean retryable;
		private final Integer statusCode;

		private OpenAiRequestException(String message, boolean retryable) {
			this(message, retryable, null, null);
		}

		private OpenAiRequestException(String message, boolean retryable, Throwable cause) {
			this(message, retryable, cause, null);
		}

		private OpenAiRequestException(String message, boolean retryable, Integer statusCode) {
			this(message, retryable, null, statusCode);
		}

		private OpenAiRequestException(String message, boolean retryable, Throwable cause, Integer statusCode) {
			super(message, cause);
			this.retryable = retryable;
			this.statusCode = statusCode;
		}

		private boolean retryable() {
			return retryable;
		}

		private Integer statusCode() {
			return statusCode;
		}
	}

	public record ToolCall(String callId, String name, JsonObject arguments) {
	}

	public record ToolRound(List<ToolCall> calls, JsonArray outputItems, JsonObject response) {
		public ToolRound {
			calls = List.copyOf(calls);
			outputItems = outputItems.deepCopy();
			response = response.deepCopy();
		}

		@Override public JsonArray outputItems() { return outputItems.deepCopy(); }
		@Override public JsonObject response() { return response.deepCopy(); }
	}
}
