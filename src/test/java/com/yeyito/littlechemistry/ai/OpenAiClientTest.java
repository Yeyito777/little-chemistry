package com.yeyito.littlechemistry.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiClientTest {
	@TempDir
	Path temporaryDirectory;

	private TestWebSocketServer server;
	private URI endpoint;

	@BeforeEach
	void startServer() throws IOException {
		server = new TestWebSocketServer();
		endpoint = server.endpoint("/responses");
	}

	@AfterEach
	void stopServer() throws Exception {
		server.close();
	}

	@Test
	void retriesTransientHandshakeFailuresBeforeReturningTheResponse() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			int request = requests.incrementAndGet();
			if (request <= 2) {
				connection.reject(503, "temporarily unavailable");
			} else {
				connection.upgradeAndReadRequest();
				sendCompletedText(connection, "recovered");
			}
		});
		List<Long> delays = new ArrayList<>();

		String answer = client(delays::add).ask("hello");

		assertEquals("recovered", answer);
		assertEquals(3, requests.get());
		assertEquals(2, delays.size());
	}

	@Test
	void allowsEightRetriesAfterTheInitialConnection() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			requests.incrementAndGet();
			connection.reject(524, "edge timeout");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(9, requests.get());
		assertEquals(8, delays.size());
		assertTrue(failure.getMessage().contains("after 8 retries"));
		for (int retry = 0; retry < delays.size(); retry++) {
			long baseDelay = Math.min(1_000L << retry, 30_000L);
			assertTrue(delays.get(retry) >= baseDelay && delays.get(retry) <= baseDelay + 1_000L);
		}
	}

	@Test
	void failsFastForTerminalHandshakeErrors() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			requests.incrementAndGet();
			connection.reject(400, "bad request");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("HTTP 400"));
	}

	@Test
	void conservativelyFailsFastForDescriptive403HandshakeErrors() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			requests.incrementAndGet();
			connection.reject(403, "policy denied this request");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("HTTP 403"));
		assertTrue(failure.getMessage().contains("body unavailable"));
	}

	@Test
	void conservativelyFailsFastForUsageLimit429HandshakeErrors() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			requests.incrementAndGet();
			connection.reject(429, "{\"error\":{\"type\":\"usage_limit_reached\"}}");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("HTTP 429"));
		assertTrue(failure.getMessage().contains("body unavailable"));
	}

	@Test
	void retriesWhenAWebSocketClosesBeforeCompletion() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			if (requests.incrementAndGet() == 1) {
				connection.sendText("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}");
			} else {
				sendCompletedText(connection, "complete");
			}
		});
		List<Long> delays = new ArrayList<>();

		String answer = client(delays::add).ask("hello");

		assertEquals("complete", answer);
		assertEquals(2, requests.get());
		assertEquals(1, delays.size());
	}

	@Test
	void retriesStatuslessTransientProviderFailures() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			if (requests.incrementAndGet() == 1) {
				connection.sendText("{\"type\":\"response.failed\",\"response\":{\"error\":{" +
						"\"type\":\"server_error\",\"code\":\"internal_error\"," +
						"\"message\":\"Temporary provider error\"}}}");
			} else {
				sendCompletedText(connection, "recovered");
			}
		});
		List<Long> delays = new ArrayList<>();

		String answer = client(delays::add).ask("hello");

		assertEquals("recovered", answer);
		assertEquals(2, requests.get());
		assertEquals(1, delays.size());
	}

	@Test
	void retriesInBandTransientWebSocketStatusErrors() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			if (requests.incrementAndGet() == 1) {
				connection.sendText("{\"type\":\"error\",\"status\":503,\"error\":{" +
						"\"type\":\"server_error\",\"message\":\"Temporary websocket error\"}}");
			} else {
				sendCompletedText(connection, "recovered");
			}
		});
		List<Long> delays = new ArrayList<>();

		assertEquals("recovered", client(delays::add).ask("hello"));
		assertEquals(2, requests.get());
		assertEquals(1, delays.size());
	}

	@Test
	void retriesA401OnceWhenTheSameSubscriptionAccountRefreshesItsToken() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		AtomicReference<Map<String, String>> refreshedHeaders = new AtomicReference<>();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			if (connections.incrementAndGet() == 1) {
				connection.sendText("{\"type\":\"error\",\"status\":401,\"error\":{" +
						"\"type\":\"authentication_error\",\"message\":\"Expired token\"}}");
			} else {
				refreshedHeaders.set(connection.headers());
				sendCompletedText(connection, "refreshed");
			}
		});
		AtomicInteger credentialReads = new AtomicInteger();
		OpenAiClient.CredentialsProvider credentials = () -> new OpenAiCredentials(
				AuthMode.SUBSCRIPTION,
				credentialReads.incrementAndGet() == 1 ? "expired-token" : "refreshed-token",
				"same-account",
				"test");
		List<Long> delays = new ArrayList<>();
		AuthConfig authConfig = new AuthConfig(temporaryDirectory.resolve("refresh-auth"));
		OpenAiClient client = new OpenAiClient(authConfig, "test-model", "medium", endpoint, endpoint,
				delays::add, credentials);

		assertEquals("refreshed", client.ask("hello"));
		assertEquals(2, connections.get());
		assertEquals(3, credentialReads.get());
		assertEquals(List.of(), delays);
		assertEquals("Bearer refreshed-token", refreshedHeaders.get().get("authorization"));
	}

	@Test
	void doesNotRetryA401WhenTheCredentialsDidNotChange() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		server.handle(connection -> {
			connections.incrementAndGet();
			connection.upgradeAndReadRequest();
			connection.sendText("{\"type\":\"error\",\"status\":401,\"error\":{" +
					"\"type\":\"authentication_error\",\"message\":\"Invalid token\"}}");
		});
		OpenAiClient.CredentialsProvider credentials = () -> new OpenAiCredentials(
				AuthMode.SUBSCRIPTION, "unchanged-token", "same-account", "test");
		List<Long> delays = new ArrayList<>();
		AuthConfig authConfig = new AuthConfig(temporaryDirectory.resolve("unchanged-auth"));
		OpenAiClient client = new OpenAiClient(authConfig, "test-model", "medium", endpoint, endpoint,
				delays::add, credentials);

		IOException failure = assertThrows(IOException.class, () -> client.ask("hello"));
		assertTrue(failure.getMessage().contains("HTTP 401"));
		assertEquals(1, connections.get());
		assertEquals(List.of(), delays);
	}

	@Test
	void retriesGenerationToolRoundsAtTheSharedTransportBoundary() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			if (requests.incrementAndGet() == 1) {
				connection.reject(500, "temporary provider failure");
			} else {
				connection.upgradeAndReadRequest();
				sendCompletedToolCall(connection);
			}
		});
		List<Long> delays = new ArrayList<>();

		OpenAiClient.ToolRound round = client(delays::add).runToolRound(
				"Choose the result", new JsonArray(), new JsonArray());

		assertEquals(2, requests.get());
		assertEquals(1, delays.size());
		assertEquals(1, round.calls().size());
		assertEquals("submit", round.calls().getFirst().name());
		assertEquals("ok", round.calls().getFirst().arguments().get("value").getAsString());
		assertEquals("response-tool-1", round.response().get("id").getAsString());
		assertEquals(7, round.response().getAsJsonObject("usage").get("output_tokens").getAsInt());
	}

	@Test
	void toolRoundUsesTheResponsesWebSocketEnvelopeAndHandshakeHeaders() throws Exception {
		AtomicReference<JsonObject> submitted = new AtomicReference<>();
		AtomicReference<Map<String, String>> handshakeHeaders = new AtomicReference<>();
		server.handle(connection -> {
			submitted.set(connection.upgradeAndReadRequest());
			handshakeHeaders.set(connection.headers());
			sendCompletedToolCall(connection);
		});
		JsonObject text = new JsonObject();
		text.addProperty("type", "input_text");
		text.addProperty("text", "Implement the recipe.");
		JsonArray content = new JsonArray();
		content.add(text);
		JsonObject user = new JsonObject();
		user.addProperty("type", "message");
		user.addProperty("role", "user");
		user.add("content", content);
		JsonArray input = new JsonArray();
		input.add(user);

		client(delay -> {}).runToolRound("one shared generation system prompt", new JsonArray(), input);

		JsonObject request = submitted.get();
		assertEquals("response.create", request.get("type").getAsString());
		assertFalse(request.has("stream"));
		assertEquals("one shared generation system prompt", request.get("instructions").getAsString());
		assertEquals(1, request.entrySet().stream().filter(entry -> entry.getKey().equals("instructions")).count());
		assertEquals(1, request.getAsJsonArray("input").size());
		assertEquals("user", request.getAsJsonArray("input").get(0).getAsJsonObject().get("role").getAsString());
		assertFalse(request.getAsJsonArray("input").toString().contains("system_instructions"));
		assertEquals("Bearer test-api-key", handshakeHeaders.get().get("authorization"));
		assertEquals("application/json", handshakeHeaders.get().get("accept"));
		assertFalse(handshakeHeaders.get().containsKey("openai-beta"));
		assertEquals("little-chemistry/1.2", handshakeHeaders.get().get("user-agent"));
	}

	@Test
	void normalSessionCloseForcesTheSocketClosedWhenThePeerDoesNotReply() throws Exception {
		CountDownLatch clientSocketClosed = new CountDownLatch(1);
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			sendCompletedText(connection, "done");
			connection.awaitClientCloseAndEndOfStream();
			clientSocketClosed.countDown();
		});

		assertEquals("done", client(delay -> {}).ask("hello"));
		assertTrue(clientSocketClosed.await(3, TimeUnit.SECONDS));
	}

	@Test
	void toolSessionReusesOneSocketAndSendsOnlyTheToolResultDelta() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		AtomicReference<JsonObject> firstRequest = new AtomicReference<>();
		AtomicReference<JsonObject> secondRequest = new AtomicReference<>();
		server.handle(connection -> {
			connections.incrementAndGet();
			firstRequest.set(connection.upgradeAndReadRequest());
			connection.sendText("{\"type\":\"response.metadata\",\"headers\":{"
					+ "\"X-Codex-Turn-State\":\"turn-route-1\"}}");
			sendCompletedToolCall(connection);
			secondRequest.set(connection.upgradeAndReadRequest());
			sendCompletedToolCall(connection);
		});

		JsonObject user = new JsonObject();
		user.addProperty("type", "message");
		user.addProperty("role", "user");
		JsonArray input = new JsonArray();
		input.add(user);
		OpenAiClient client = client(delay -> {});
		try (OpenAiClient.ToolSession session = client.openToolSession()) {
			OpenAiClient.ToolRound first = session.runToolRound("shared instructions", new JsonArray(), input);
			JsonArray continuedInput = input.deepCopy();
			first.outputItems().forEach(item -> continuedInput.add(item.deepCopy()));
			JsonObject toolResult = new JsonObject();
			toolResult.addProperty("type", "function_call_output");
			toolResult.addProperty("call_id", "call_1");
			toolResult.addProperty("output", "finished");
			continuedInput.add(toolResult);

			session.runToolRound("shared instructions", new JsonArray(), continuedInput);
		}

		assertEquals(1, connections.get());
		assertFalse(firstRequest.get().has("previous_response_id"));
		assertEquals("response-tool-1", secondRequest.get().get("previous_response_id").getAsString());
		assertEquals(1, secondRequest.get().getAsJsonArray("input").size());
		assertEquals("function_call_output", secondRequest.get().getAsJsonArray("input").get(0)
				.getAsJsonObject().get("type").getAsString());
		assertEquals("turn-route-1", secondRequest.get().getAsJsonObject("client_metadata")
				.get("x-codex-turn-state").getAsString());
	}

	@Test
	void toolSessionUsesTurnStateFromTheSuccessfulUpgradeHeaders() throws Exception {
		AtomicReference<JsonObject> firstRequest = new AtomicReference<>();
		AtomicReference<JsonObject> secondRequest = new AtomicReference<>();
		server.handle(connection -> {
			firstRequest.set(connection.upgradeAndReadRequest(
					Map.of("x-codex-turn-state", "handshake-route-1")));
			sendCompletedToolCall(connection);
			secondRequest.set(connection.upgradeAndReadRequest());
			sendCompletedToolCall(connection);
		});

		JsonObject user = new JsonObject();
		user.addProperty("type", "message");
		user.addProperty("role", "user");
		JsonArray input = new JsonArray();
		input.add(user);
		OpenAiClient client = client(delay -> {});
		try (OpenAiClient.ToolSession session = client.openToolSession()) {
			OpenAiClient.ToolRound first = session.runToolRound("shared instructions", new JsonArray(), input);
			JsonArray continuedInput = input.deepCopy();
			first.outputItems().forEach(item -> continuedInput.add(item.deepCopy()));
			JsonObject toolResult = new JsonObject();
			toolResult.addProperty("type", "function_call_output");
			toolResult.addProperty("call_id", "call_1");
			toolResult.addProperty("output", "finished");
			continuedInput.add(toolResult);
			session.runToolRound("shared instructions", new JsonArray(), continuedInput);
		}

		assertEquals("handshake-route-1", firstRequest.get().getAsJsonObject("client_metadata")
				.get("x-codex-turn-state").getAsString());
		assertEquals("handshake-route-1", secondRequest.get().getAsJsonObject("client_metadata")
				.get("x-codex-turn-state").getAsString());
	}

	@Test
	void toolSessionReconnectsWithTurnStateAndAFullReplayAfterTheSocketDies() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		AtomicReference<JsonObject> replayRequest = new AtomicReference<>();
		AtomicReference<Map<String, String>> replayHeaders = new AtomicReference<>();
		server.handle(connection -> {
			if (connections.incrementAndGet() == 1) {
				connection.upgradeAndReadRequest();
				connection.sendText("{\"type\":\"response.metadata\",\"headers\":{"
						+ "\"x-codex-turn-state\":\"turn-route-reconnect\"}}");
				sendCompletedToolCall(connection);
				return;
			}
			replayRequest.set(connection.upgradeAndReadRequest());
			replayHeaders.set(connection.headers());
			sendCompletedToolCall(connection);
		});

		JsonObject user = new JsonObject();
		user.addProperty("type", "message");
		user.addProperty("role", "user");
		JsonArray input = new JsonArray();
		input.add(user);
		List<Long> delays = new ArrayList<>();
		OpenAiClient client = client(delays::add);
		try (OpenAiClient.ToolSession session = client.openToolSession()) {
			OpenAiClient.ToolRound first = session.runToolRound("shared instructions", new JsonArray(), input);
			JsonArray continuedInput = input.deepCopy();
			first.outputItems().forEach(item -> continuedInput.add(item.deepCopy()));
			JsonObject toolResult = new JsonObject();
			toolResult.addProperty("type", "function_call_output");
			toolResult.addProperty("call_id", "call_1");
			toolResult.addProperty("output", "finished");
			continuedInput.add(toolResult);

			session.runToolRound("shared instructions", new JsonArray(), continuedInput);
		}

		assertEquals(2, connections.get());
		assertEquals(1, delays.size());
		assertFalse(replayRequest.get().has("previous_response_id"));
		assertEquals(3, replayRequest.get().getAsJsonArray("input").size());
		assertEquals("turn-route-reconnect", replayHeaders.get().get("x-codex-turn-state"));
		assertEquals("turn-route-reconnect", replayRequest.get().getAsJsonObject("client_metadata")
				.get("x-codex-turn-state").getAsString());
	}

	@Test
	void incrementalRequestFailureReconnectsAndRetriesWithAFullReplay() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		AtomicReference<JsonObject> incrementalRequest = new AtomicReference<>();
		AtomicReference<JsonObject> replayRequest = new AtomicReference<>();
		server.handle(connection -> {
			if (connections.incrementAndGet() == 1) {
				connection.upgradeAndReadRequest();
				sendCompletedToolCall(connection);
				incrementalRequest.set(connection.upgradeAndReadRequest());
				connection.sendText("{\"type\":\"error\",\"status\":400,\"error\":{" +
						"\"code\":\"previous_response_not_found\"," +
						"\"message\":\"Previous response was not found\"}}");
				return;
			}
			replayRequest.set(connection.upgradeAndReadRequest());
			sendCompletedToolCall(connection);
		});

		JsonObject user = new JsonObject();
		user.addProperty("type", "message");
		user.addProperty("role", "user");
		JsonArray input = new JsonArray();
		input.add(user);
		List<Long> delays = new ArrayList<>();
		OpenAiClient client = client(delays::add);
		try (OpenAiClient.ToolSession session = client.openToolSession()) {
			OpenAiClient.ToolRound first = session.runToolRound("shared instructions", new JsonArray(), input);
			JsonArray continuedInput = input.deepCopy();
			first.outputItems().forEach(item -> continuedInput.add(item.deepCopy()));
			JsonObject toolResult = new JsonObject();
			toolResult.addProperty("type", "function_call_output");
			toolResult.addProperty("call_id", "call_1");
			toolResult.addProperty("output", "finished");
			continuedInput.add(toolResult);

			session.runToolRound("shared instructions", new JsonArray(), continuedInput);
		}

		assertEquals(2, connections.get());
		assertEquals(1, delays.size());
		assertEquals("response-tool-1", incrementalRequest.get().get("previous_response_id").getAsString());
		assertEquals(1, incrementalRequest.get().getAsJsonArray("input").size());
		assertFalse(replayRequest.get().has("previous_response_id"));
		assertEquals(3, replayRequest.get().getAsJsonArray("input").size());
	}

	@Test
	void toolSessionRefusesToReplayHistoryAfterTheApiKeyChanges() throws Exception {
		AtomicInteger connections = new AtomicInteger();
		server.handle(connection -> {
			connections.incrementAndGet();
			connection.upgradeAndReadRequest();
			sendCompletedToolCall(connection);
		});
		AuthConfig authConfig = new AuthConfig(temporaryDirectory.resolve("rotating-auth"));
		authConfig.useApiKey("first-api-key");
		OpenAiClient client = new OpenAiClient(
				authConfig, "test-model", "medium", endpoint, endpoint, delay -> {});
		JsonArray input = new JsonArray();
		input.add(new JsonObject());

		try (OpenAiClient.ToolSession session = client.openToolSession()) {
			session.runToolRound("shared instructions", new JsonArray(), input);
			authConfig.useApiKey("second-api-key");

			IOException failure = assertThrows(IOException.class,
					() -> session.runToolRound("shared instructions", new JsonArray(), input));
			assertTrue(failure.getMessage().contains("account changed"));
		}
		assertEquals(1, connections.get());
	}

	@Test
	void doesNotRetryContextWindowResponseFailures() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			requests.incrementAndGet();
			connection.sendText("{\"type\":\"response.failed\",\"response\":{\"error\":{" +
					"\"code\":\"context_length_exceeded\",\"message\":\"Input exceeds the context window\"}}}");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("context window"));
	}

	@Test
	void doesNotRetryStatuslessTerminalProviderFailures() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			requests.incrementAndGet();
			connection.sendText("{\"type\":\"response.failed\",\"response\":{\"error\":{" +
					"\"type\":\"invalid_request_error\",\"code\":\"unsupported_value\"," +
					"\"message\":\"The request option is unsupported\"}}}");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("unsupported"));
	}

	@Test
	void doesNotRetryACompletedButEmptyResponse() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			requests.incrementAndGet();
			connection.sendText("{\"type\":\"response.completed\",\"response\":{\"output\":[]}}");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("empty response"));
	}

	@Test
	void treatsAnIncompleteWebSocketResponseAsTerminal() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			requests.incrementAndGet();
			connection.sendText("{\"type\":\"response.incomplete\",\"response\":{" +
					"\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("max_output_tokens"));
	}

	@Test
	void anAlreadyInterruptedThreadDoesNotOpenAConnection() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> requests.incrementAndGet());
		OpenAiClient client = client(delay -> {});
		Thread.currentThread().interrupt();
		try {
			assertThrows(InterruptedException.class, () -> client.ask("hello"));
			assertEquals(0, requests.get());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void interruptionDuringTheUpgradeDoesNotLeaveAnOwnerlessSocket() throws Exception {
		CountDownLatch handshakeReceived = new CountDownLatch(1);
		CountDownLatch releaseServer = new CountDownLatch(1);
		server.handle(connection -> {
			connection.awaitHandshake();
			handshakeReceived.countDown();
			releaseServer.await(5, TimeUnit.SECONDS);
		});
		OpenAiClient client = client(delay -> {});
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread requestThread = Thread.ofVirtual().start(() -> {
			try {
				client.ask("hello");
			} catch (Throwable thrown) {
				failure.set(thrown);
			}
		});

		try {
			assertTrue(handshakeReceived.await(5, TimeUnit.SECONDS));
			requestThread.interrupt();
			requestThread.join(5_000);
			assertFalse(requestThread.isAlive());
			assertTrue(failure.get() instanceof InterruptedException,
					() -> "Expected InterruptedException, got " + failure.get());
		} finally {
			releaseServer.countDown();
			requestThread.interrupt();
		}
	}

	@Test
	void interruptionWhileReadingAnActiveWebSocketCancelsWithoutRetrying() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		CountDownLatch partialResponseSent = new CountDownLatch(1);
		CountDownLatch finishResponse = new CountDownLatch(1);
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			requests.incrementAndGet();
			connection.sendText("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}");
			partialResponseSent.countDown();
			finishResponse.await(5, TimeUnit.SECONDS);
		});
		List<Long> delays = new ArrayList<>();
		OpenAiClient client = client(delays::add);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread requestThread = Thread.ofVirtual().start(() -> {
			try {
				client.ask("hello");
			} catch (Throwable thrown) {
				failure.set(thrown);
			}
		});

		try {
			assertTrue(partialResponseSent.await(5, TimeUnit.SECONDS));
			requestThread.interrupt();
			requestThread.join(5_000);
			assertFalse(requestThread.isAlive());
			assertTrue(failure.get() instanceof InterruptedException,
					() -> "Expected InterruptedException, got " + failure.get());
			assertEquals(1, requests.get());
			assertEquals(List.of(), delays);
		} finally {
			finishResponse.countDown();
			requestThread.interrupt();
		}
	}

	@Test
	void closingAToolSessionAbortsItsActiveReadWithoutRetrying() throws Exception {
		CountDownLatch partialResponseSent = new CountDownLatch(1);
		CountDownLatch releaseServer = new CountDownLatch(1);
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			connection.sendText("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}");
			partialResponseSent.countDown();
			releaseServer.await(5, TimeUnit.SECONDS);
		});
		List<Long> delays = new ArrayList<>();
		OpenAiClient.ToolSession session = client(delays::add).openToolSession();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread requestThread = Thread.ofVirtual().start(() -> {
			try {
				session.runToolRound("instructions", new JsonArray(), new JsonArray());
			} catch (Throwable thrown) {
				failure.set(thrown);
			}
		});

		try {
			assertTrue(partialResponseSent.await(5, TimeUnit.SECONDS));
			session.close();
			requestThread.join(5_000);
			assertFalse(requestThread.isAlive());
			assertTrue(failure.get() instanceof IOException,
					() -> "Expected IOException, got " + failure.get());
			assertEquals(List.of(), delays);
		} finally {
			releaseServer.countDown();
			session.close();
			requestThread.interrupt();
		}
	}

	@Test
	void aWaitingToolRoundCanBeInterruptedWhileAnotherRoundOwnsTheSession() throws Exception {
		CountDownLatch partialResponseSent = new CountDownLatch(1);
		CountDownLatch releaseServer = new CountDownLatch(1);
		server.handle(connection -> {
			connection.upgradeAndReadRequest();
			connection.sendText("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}");
			partialResponseSent.countDown();
			releaseServer.await(5, TimeUnit.SECONDS);
		});
		OpenAiClient.ToolSession session = client(delay -> {}).openToolSession();
		Thread active = Thread.ofVirtual().start(() -> {
			try {
				session.runToolRound("instructions", new JsonArray(), new JsonArray());
			} catch (Throwable ignored) {
			}
		});
		AtomicReference<Throwable> waitingFailure = new AtomicReference<>();
		Thread waiting = Thread.ofVirtual().unstarted(() -> {
			try {
				session.runToolRound("instructions", new JsonArray(), new JsonArray());
			} catch (Throwable thrown) {
				waitingFailure.set(thrown);
			}
		});

		try {
			assertTrue(partialResponseSent.await(5, TimeUnit.SECONDS));
			waiting.start();
			waiting.interrupt();
			waiting.join(5_000);
			assertFalse(waiting.isAlive());
			assertTrue(waitingFailure.get() instanceof InterruptedException,
					() -> "Expected InterruptedException, got " + waitingFailure.get());
		} finally {
			session.close();
			releaseServer.countDown();
			active.interrupt();
			active.join(5_000);
			waiting.interrupt();
		}
	}

	@Test
	void interruptionDuringBackoffCancelsFurtherRetries() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.handle(connection -> {
			requests.incrementAndGet();
			connection.reject(503, "temporarily unavailable");
		});
		OpenAiClient client = client(delay -> {
			throw new InterruptedException("cancelled");
		});

		assertThrows(InterruptedException.class, () -> client.ask("hello"));
		assertEquals(1, requests.get());
	}

	@Test
	void convertsHttpResponseUrisToWebSocketUris() {
		assertEquals("wss://chatgpt.com/backend-api/codex/responses",
				OpenAiClient.websocketUri(URI.create("https://chatgpt.com/backend-api/codex/responses")).toString());
		assertEquals("ws://127.0.0.1:1234/responses?test=true",
				OpenAiClient.websocketUri(URI.create("http://127.0.0.1:1234/responses?test=true")).toString());
	}

	@Test
	void subscriptionWebSocketUsesTheCodexBetaIdentityAndStreamingBody() throws Exception {
		OpenAiClient client = client(delay -> {});
		Map<String, String> headers = client.websocketHeaders(new OpenAiCredentials(
				AuthMode.SUBSCRIPTION, "subscription-token", "account-1", "test"), "route-1");

		assertEquals("Bearer subscription-token", headers.get("Authorization"));
		assertEquals("responses_websockets=2026-02-06", headers.get("OpenAI-Beta"));
		assertEquals("codex_cli_rs", headers.get("originator"));
		assertEquals("remote_compaction_v2", headers.get("x-codex-beta-features"));
		assertEquals("account-1", headers.get("ChatGPT-Account-ID"));
		assertEquals("route-1", headers.get("x-codex-turn-state"));

		JsonObject body = OpenAiClient.toolRequestBody(new JsonObject(), AuthMode.SUBSCRIPTION);
		assertTrue(body.get("stream").getAsBoolean());
		assertTrue(body.getAsJsonObject("client_metadata").has("x-codex-installation-id"));
	}

	private OpenAiClient client(OpenAiClient.RetrySleeper retrySleeper) throws IOException {
		AuthConfig authConfig = new AuthConfig(temporaryDirectory.resolve("auth"));
		authConfig.useApiKey("test-api-key");
		return new OpenAiClient(
				authConfig,
				"test-model",
				"medium",
				endpoint,
				endpoint,
				retrySleeper
		);
	}

	private static void sendCompletedText(TestWebSocketConnection connection, String text) throws IOException {
		connection.sendText("{\"type\":\"response.output_text.delta\",\"delta\":\"" + text + "\"}");
		connection.sendText("{\"type\":\"response.completed\",\"response\":{\"output\":[]}}");
	}

	private static void sendCompletedToolCall(TestWebSocketConnection connection) throws IOException {
		String item = "{\"type\":\"function_call\",\"call_id\":\"call_1\",\"name\":\"submit\"," +
				"\"arguments\":\"{\\\"value\\\":\\\"ok\\\"}\"}";
		connection.sendText("{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":" + item + "}");
		connection.sendText("{\"type\":\"response.completed\",\"response\":{\"id\":\"response-tool-1\"," +
				"\"usage\":{\"output_tokens\":7},\"output\":[" + item + "]}}");
	}

	@FunctionalInterface
	private interface ConnectionHandler {
		void handle(TestWebSocketConnection connection) throws Exception;
	}

	private static final class TestWebSocketServer implements AutoCloseable {
		private final ServerSocket serverSocket;
		private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
		private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
		private final AtomicReference<Throwable> failure = new AtomicReference<>();
		private final AtomicBoolean closing = new AtomicBoolean();
		private final Thread acceptThread;
		private volatile ConnectionHandler handler;

		private TestWebSocketServer() throws IOException {
			serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
			acceptThread = Thread.ofPlatform().name("openai-websocket-test-server").start(this::acceptConnections);
		}

		private URI endpoint(String path) {
			return URI.create("ws://127.0.0.1:" + serverSocket.getLocalPort() + path);
		}

		private void handle(ConnectionHandler nextHandler) {
			handler = nextHandler;
		}

		private void acceptConnections() {
			while (!closing.get()) {
				try {
					Socket socket = serverSocket.accept();
					activeSockets.add(socket);
					handlers.submit(() -> serve(socket));
				} catch (SocketException closed) {
					if (!closing.get()) failure.compareAndSet(null, closed);
					return;
				} catch (Throwable error) {
					if (!closing.get()) failure.compareAndSet(null, error);
				}
			}
		}

		private void serve(Socket socket) {
			try (socket) {
				ConnectionHandler current = handler;
				if (current == null) throw new IllegalStateException("No websocket test handler was configured");
				current.handle(new TestWebSocketConnection(socket));
			} catch (Throwable error) {
				if (!(closing.get() && error instanceof IOException)) failure.compareAndSet(null, error);
			} finally {
				activeSockets.remove(socket);
			}
		}

		@Override
		public void close() throws Exception {
			closing.set(true);
			serverSocket.close();
			for (Socket socket : activeSockets) {
				try {
					socket.close();
				} catch (IOException ignored) {
				}
			}
			handlers.shutdownNow();
			handlers.awaitTermination(5, TimeUnit.SECONDS);
			acceptThread.join(5_000);
			Throwable serverFailure = failure.get();
			if (serverFailure != null) throw new AssertionError("Websocket test server failed", serverFailure);
		}
	}

	private static final class TestWebSocketConnection {
		private static final int MAX_HANDSHAKE_BYTES = 64 * 1024;
		private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

		private final InputStream input;
		private final OutputStream output;
		private String requestTarget;
		private Map<String, String> headers;
		private boolean upgraded;

		private TestWebSocketConnection(Socket socket) throws IOException {
			input = socket.getInputStream();
			output = socket.getOutputStream();
		}

		private Map<String, String> headers() {
			return Map.copyOf(headers);
		}

		private void awaitHandshake() throws IOException {
			readHandshake();
		}

		private void reject(int status, String body) throws IOException {
			readHandshake();
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			String response = "HTTP/1.1 " + status + " Test Failure\r\n" +
					"Content-Type: text/plain\r\n" +
					"Content-Length: " + bytes.length + "\r\n" +
					"Connection: close\r\n\r\n";
			output.write(response.getBytes(StandardCharsets.US_ASCII));
			output.write(bytes);
			output.flush();
		}

		private JsonObject upgradeAndReadRequest() throws Exception {
			return upgradeAndReadRequest(Map.of());
		}

		private JsonObject upgradeAndReadRequest(Map<String, String> responseHeaders) throws Exception {
			readHandshake();
			if (!upgraded) {
				if (!"/responses".equals(requestTarget)) {
					throw new AssertionError("Unexpected websocket request target: " + requestTarget);
				}
				String key = headers.get("sec-websocket-key");
				if (key == null) throw new AssertionError("Missing Sec-WebSocket-Key");
				String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
						.digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII)));
				StringBuilder response = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\n")
						.append("Upgrade: websocket\r\n")
						.append("Connection: Upgrade\r\n")
						.append("Sec-WebSocket-Accept: ").append(accept).append("\r\n");
				responseHeaders.forEach((name, value) -> response.append(name).append(": ")
						.append(value.replaceAll("[\\r\\n]+", " ")).append("\r\n"));
				response.append("\r\n");
				output.write(response.toString().getBytes(StandardCharsets.US_ASCII));
				output.flush();
				upgraded = true;
			}
			return JsonParser.parseString(readClientTextFrame()).getAsJsonObject();
		}

		private void readHandshake() throws IOException {
			if (headers != null) return;
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			int matched = 0;
			while (bytes.size() < MAX_HANDSHAKE_BYTES) {
				int next = input.read();
				if (next < 0) throw new EOFException("Websocket client closed during the handshake");
				bytes.write(next);
				byte expected = switch (matched) {
					case 0, 2 -> (byte) '\r';
					case 1, 3 -> (byte) '\n';
					default -> throw new IllegalStateException();
				};
				if ((byte) next == expected) {
					matched++;
					if (matched == 4) break;
				} else {
					matched = (byte) next == '\r' ? 1 : 0;
				}
			}
			if (matched != 4) throw new IOException("Websocket handshake exceeded the test safety limit");
			String[] lines = bytes.toString(StandardCharsets.US_ASCII).split("\\r\\n");
			String[] requestLine = lines[0].split(" ", 3);
			if (requestLine.length != 3 || !"GET".equals(requestLine[0])) {
				throw new IOException("Invalid websocket handshake request line");
			}
			requestTarget = requestLine[1];
			Map<String, String> parsed = new LinkedHashMap<>();
			for (int index = 1; index < lines.length; index++) {
				int colon = lines[index].indexOf(':');
				if (colon <= 0) continue;
				parsed.put(lines[index].substring(0, colon).trim().toLowerCase(Locale.ROOT),
						lines[index].substring(colon + 1).trim());
			}
			headers = Map.copyOf(parsed);
		}

		private String readClientTextFrame() throws IOException {
			ClientFrame frame = readClientFrame();
			if (frame.opcode() != 0x1) {
				throw new IOException("Expected a websocket text frame, got opcode " + frame.opcode());
			}
			return new String(frame.payload(), StandardCharsets.UTF_8);
		}

		private ClientFrame readClientFrame() throws IOException {
			int first = readByte(input);
			int second = readByte(input);
			int opcode = first & 0x0f;
			boolean masked = (second & 0x80) != 0;
			if (!masked) throw new IOException("Client websocket frame was not masked");
			long length = second & 0x7f;
			if (length == 126) {
				length = ((long) readByte(input) << 8) | readByte(input);
			} else if (length == 127) {
				length = 0;
				for (int index = 0; index < 8; index++) length = (length << 8) | readByte(input);
			}
			if (length > 16 * 1024 * 1024) throw new IOException("Test websocket frame exceeded 16 MiB");
			byte[] mask = input.readNBytes(4);
			if (mask.length != 4) throw new EOFException("Incomplete websocket frame mask");
			byte[] payload = input.readNBytes((int) length);
			if (payload.length != (int) length) throw new EOFException("Incomplete websocket frame payload");
			for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
			return new ClientFrame(opcode, payload);
		}

		private void awaitClientCloseAndEndOfStream() throws IOException {
			ClientFrame close = readClientFrame();
			if (close.opcode() != 0x8) throw new IOException("Expected websocket close frame");
			if (input.read() != -1) throw new IOException("Expected websocket transport to close after its grace period");
		}

		private synchronized void sendText(String text) throws IOException {
			byte[] payload = text.getBytes(StandardCharsets.UTF_8);
			output.write(0x81);
			if (payload.length < 126) {
				output.write(payload.length);
			} else if (payload.length <= 0xffff) {
				output.write(126);
				output.write((payload.length >>> 8) & 0xff);
				output.write(payload.length & 0xff);
			} else {
				output.write(127);
				long length = payload.length;
				for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (length >>> shift) & 0xff);
			}
			output.write(payload);
			output.flush();
		}

		private static int readByte(InputStream input) throws IOException {
			int value = input.read();
			if (value < 0) throw new EOFException("Unexpected end of websocket frame");
			return value;
		}

		private record ClientFrame(int opcode, byte[] payload) {
		}
	}
}
