package com.yeyito.littlechemistry.ai;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiClientTest {
	@TempDir
	Path temporaryDirectory;

	private HttpServer server;
	private URI endpoint;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.start();
		endpoint = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/responses");
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void retriesTransientHttpFailuresBeforeReturningTheResponse() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			int request = requests.incrementAndGet();
			if (request <= 2) {
				respond(exchange, 503, "temporarily unavailable");
			} else {
				respond(exchange, 200, completedText("recovered"));
			}
		});
		List<Long> delays = new ArrayList<>();

		String answer = client(delays::add).ask("hello");

		assertEquals("recovered", answer);
		assertEquals(3, requests.get());
		assertEquals(2, delays.size());
	}

	@Test
	void allowsEightRetriesAfterTheInitialRequest() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 524, "edge timeout");
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
	void failsFastForTerminalHttpErrors() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 400, "bad request");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("HTTP 400"));
	}

	@Test
	void retriesWhenAStreamEndsBeforeCompletion() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			if (requests.incrementAndGet() == 1) {
				respond(exchange, 200, "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n");
			} else {
				respond(exchange, 200, completedText("complete"));
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
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			if (requests.incrementAndGet() == 1) {
				respond(exchange, 200, "data: {\"type\":\"response.failed\",\"response\":{\"error\":{" +
						"\"type\":\"server_error\",\"code\":\"internal_error\"," +
						"\"message\":\"Temporary provider error\"}}}\n\n");
			} else {
				respond(exchange, 200, completedText("recovered"));
			}
		});
		List<Long> delays = new ArrayList<>();

		String answer = client(delays::add).ask("hello");

		assertEquals("recovered", answer);
		assertEquals(2, requests.get());
		assertEquals(1, delays.size());
	}

	@Test
	void retriesGenerationToolRoundsAtTheSharedTransportBoundary() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			if (requests.incrementAndGet() == 1) {
				respond(exchange, 500, "temporary provider failure");
			} else {
				respond(exchange, 200, completedToolCall());
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
	void doesNotRetryContextWindowResponseFailures() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 200, "data: {\"type\":\"response.failed\",\"response\":{\"error\":{" +
					"\"code\":\"context_length_exceeded\",\"message\":\"Input exceeds the context window\"}}}\n\n");
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
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 200, "data: {\"type\":\"response.failed\",\"response\":{\"error\":{" +
					"\"type\":\"invalid_request_error\",\"code\":\"unsupported_value\"," +
					"\"message\":\"The request option is unsupported\"}}}\n\n");
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
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 200, "data: {\"type\":\"response.completed\",\"response\":{\"output\":[]}}\n\n");
		});
		List<Long> delays = new ArrayList<>();

		IOException failure = assertThrows(IOException.class, () -> client(delays::add).ask("hello"));

		assertEquals(1, requests.get());
		assertEquals(List.of(), delays);
		assertTrue(failure.getMessage().contains("empty response"));
	}

	@Test
	void anAlreadyInterruptedThreadDoesNotSubmitARequest() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 200, completedText("unexpected"));
		});
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
	void interruptionWhileReadingAnActiveStreamCancelsWithoutRetrying() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		CountDownLatch partialResponseSent = new CountDownLatch(1);
		CountDownLatch finishResponse = new CountDownLatch(1);
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
			exchange.sendResponseHeaders(200, 0);
			try (var output = exchange.getResponseBody()) {
				output.write("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n"
						.getBytes(StandardCharsets.UTF_8));
				output.flush();
				partialResponseSent.countDown();
				try {
					finishResponse.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
				output.write(completedText("late").getBytes(StandardCharsets.UTF_8));
			}
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
			finishResponse.countDown();
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
	void interruptionDuringBackoffCancelsFurtherRetries() throws Exception {
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/responses", exchange -> {
			consumeRequest(exchange);
			requests.incrementAndGet();
			respond(exchange, 503, "temporarily unavailable");
		});
		OpenAiClient client = client(delay -> {
			throw new InterruptedException("cancelled");
		});

		assertThrows(InterruptedException.class, () -> client.ask("hello"));
		assertEquals(1, requests.get());
	}

	private OpenAiClient client(OpenAiClient.RetrySleeper retrySleeper) throws IOException {
		AuthConfig authConfig = new AuthConfig(temporaryDirectory.resolve("auth"));
		authConfig.useApiKey("test-api-key");
		return new OpenAiClient(
				authConfig,
				"test-model",
				"medium",
				HttpClient.newHttpClient(),
				endpoint,
				endpoint,
				retrySleeper
		);
	}

	private static void consumeRequest(HttpExchange exchange) throws IOException {
		exchange.getRequestBody().readAllBytes();
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private static String completedText(String text) {
		return "data: {\"type\":\"response.output_text.delta\",\"delta\":\"" + text + "\"}\n\n"
				+ "data: {\"type\":\"response.completed\",\"response\":{\"output\":[]}}\n\n"
				+ "data: [DONE]\n\n";
	}

	private static String completedToolCall() {
		String item = "{\"type\":\"function_call\",\"call_id\":\"call_1\",\"name\":\"submit\"," +
				"\"arguments\":\"{\\\"value\\\":\\\"ok\\\"}\"}";
		return "data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":" + item + "}\n\n"
				+ "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"response-tool-1\","
				+ "\"usage\":{\"output_tokens\":7},\"output\":[" + item + "]}}\n\n"
				+ "data: [DONE]\n\n";
	}
}
