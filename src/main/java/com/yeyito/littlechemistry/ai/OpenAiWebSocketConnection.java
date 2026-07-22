package com.yeyito.littlechemistry.ai;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Blocking adapter around Java-WebSocket for one serialized OpenAI Responses connection. */
final class OpenAiWebSocketConnection implements AutoCloseable {
	private static final Pattern HANDSHAKE_STATUS = Pattern.compile("Invalid status code received: (\\d{3})");
	private static final long CLOSE_GRACE_SECONDS = 1;

	private final IncomingClient client;

	OpenAiWebSocketConnection(URI uri, Map<String, String> headers, Duration connectTimeout) {
		Objects.requireNonNull(uri, "uri");
		client = new IncomingClient(uri, headers, Math.toIntExact(connectTimeout.toMillis()));
	}

	void open(Duration connectTimeout) throws IOException, InterruptedException {
		boolean opened;
		try {
			opened = client.connectBlocking(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException interrupted) {
			client.abort();
			throw interrupted;
		}
		if (opened && !client.cancelled.get()) return;

		Exception failure = client.connectionFailure;
		String closeReason = client.connectionCloseReason;
		client.abort();
		Integer status = handshakeStatus(failure, closeReason);
		if (status != null) throw new HandshakeException(status, failure);
		throw new IOException("Could not open the OpenAI websocket: "
				+ (closeReason == null || closeReason.isBlank() ? safeMessage(failure) : closeReason), failure);
	}

	String responseHeader(String name) {
		for (Map.Entry<String, String> header : client.responseHeaders.entrySet()) {
			if (header.getKey().equalsIgnoreCase(name)) return header.getValue();
		}
		return null;
	}

	void sendText(String text) throws IOException, InterruptedException {
		if (Thread.currentThread().isInterrupted()) throw new InterruptedException("OpenAI websocket send was interrupted");
		if (!client.isOpen()) throw new IOException("OpenAI websocket is closed");
		try {
			// Java-WebSocket enqueues the complete text message synchronously; its daemon
			// writer owns framing and socket backpressure after this bounded operation.
			client.send(text);
		} catch (RuntimeException closed) {
			throw new IOException("Could not send the OpenAI websocket request: " + safeMessage(closed), closed);
		}
	}

	IncomingMessage nextMessage(Duration timeout) throws IOException, InterruptedException {
		IncomingMessage message = client.messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (message == null) {
			throw new IOException("No OpenAI websocket data for " + timeout.toSeconds() + " seconds");
		}
		if (message instanceof Failure failure) throw failure.failure();
		return message;
	}

	@Override
	public void close() {
		client.close();
		CompletableFuture.delayedExecutor(CLOSE_GRACE_SECONDS, TimeUnit.SECONDS).execute(() -> {
			if (!client.isClosed()) client.abort();
		});
	}

	void abort() {
		client.abort();
	}

	private static Integer handshakeStatus(Exception failure, String closeReason) {
		Throwable current = failure;
		while (current != null) {
			Matcher matcher = HANDSHAKE_STATUS.matcher(String.valueOf(current.getMessage()));
			if (matcher.find()) return Integer.parseInt(matcher.group(1));
			current = current.getCause();
		}
		Matcher closeMatcher = HANDSHAKE_STATUS.matcher(String.valueOf(closeReason));
		if (closeMatcher.find()) return Integer.parseInt(closeMatcher.group(1));
		return null;
	}

	private static String safeMessage(Throwable throwable) {
		if (throwable == null) return "unknown transport error";
		String message = throwable.getMessage();
		return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
	}

	sealed interface IncomingMessage permits TextMessage, BinaryMessage, Closed, Failure {
	}

	record TextMessage(String text) implements IncomingMessage {
	}

	record BinaryMessage() implements IncomingMessage {
	}

	record Closed(int statusCode, String reason) implements IncomingMessage {
	}

	record Failure(IOException failure) implements IncomingMessage {
	}

	static final class HandshakeException extends IOException {
		private final int statusCode;

		private HandshakeException(int statusCode, Throwable cause) {
			super("OpenAI websocket handshake failed with HTTP " + statusCode, cause);
			this.statusCode = statusCode;
		}

		int statusCode() {
			return statusCode;
		}
	}

	private static final class IncomingClient extends WebSocketClient {
		private final BlockingQueue<IncomingMessage> messages = new LinkedBlockingQueue<>();
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private volatile Map<String, String> responseHeaders = Map.of();
		private volatile Exception connectionFailure;
		private volatile String connectionCloseReason;

		private IncomingClient(URI uri, Map<String, String> headers, int connectTimeoutMillis) {
			super(uri, new Draft_6455(), headers, connectTimeoutMillis);
			setDaemon(true);
			setConnectionLostTimeout(0);
			setTcpNoDelay(true);
		}

		@Override
		public void onOpen(ServerHandshake handshake) {
			Map<String, String> captured = new LinkedHashMap<>();
			handshake.iterateHttpFields().forEachRemaining(name -> captured.put(name, handshake.getFieldValue(name)));
			responseHeaders = Map.copyOf(captured);
			if (cancelled.get()) closeConnection(CloseFrame.ABNORMAL_CLOSE, "cancelled");
		}

		@Override
		public void onMessage(String message) {
			messages.add(new TextMessage(message));
		}

		@Override
		public void onMessage(ByteBuffer bytes) {
			messages.add(new BinaryMessage());
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			connectionCloseReason = reason;
			messages.add(new Closed(code, reason));
		}

		@Override
		public void onError(Exception error) {
			connectionFailure = error;
			messages.add(new Failure(error instanceof IOException io
					? io : new IOException("OpenAI websocket failed: " + safeMessage(error), error)));
		}

		private void abort() {
			cancelled.set(true);
			try {
				Socket socket = getSocket();
				if (socket != null) socket.close();
			} catch (IOException ignored) {
			}
			try {
				closeConnection(CloseFrame.ABNORMAL_CLOSE, "aborted");
			} catch (RuntimeException ignored) {
			}
		}
	}
}
