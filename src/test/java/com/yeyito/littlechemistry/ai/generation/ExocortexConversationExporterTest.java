package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExocortexConversationExporterTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void resolvesOnlyOneExactExistingLittlechemistryLogsHierarchy() throws Exception {
		Path config = temporaryDirectory.resolve("config");
		Files.createDirectories(config.resolve("data/conversations"));
		Path folders = config.resolve("data/folders.json");
		Files.writeString(folders, """
				{"version":1,"folders":[
				  {"id":"root","name":"littlechemistry","parentId":null},
				  {"id":"logs","name":"logs","parentId":"root"}
				]}
				""");

		var exporter = ExocortexConversationExporter.findExact(config, false).orElseThrow();
		assertEquals("littlechemistry/logs", exporter.targetPath());

		Files.writeString(folders, """
				{"version":1,"folders":[
				  {"id":"root","name":"LittleChemistry","parentId":null},
				  {"id":"logs","name":"logs","parentId":"root"}
				]}
				""");
		assertTrue(ExocortexConversationExporter.findExact(config, false).isEmpty());

		Files.writeString(folders, """
				{"version":1,"folders":[
				  {"id":"root","name":"littlechemistry","parentId":null},
				  {"id":"logs-a","name":"logs","parentId":"root"},
				  {"id":"logs-b","name":"logs","parentId":"root"}
				]}
				""");
		assertTrue(ExocortexConversationExporter.findExact(config, false).isEmpty());
	}

	@Test
	void doesNotCreateOrGuessAMissingFolderTree() throws Exception {
		Path config = temporaryDirectory.resolve("missing-config");
		assertTrue(ExocortexConversationExporter.findExact(config, false).isEmpty());
		assertTrue(Files.notExists(config));
	}

	@Test
	void atomicallyPublishesAndNotifiesTheDaemonToLoadThenMoveTheConversation() throws Exception {
		Path config = temporaryDirectory.resolve("live-config");
		Files.createDirectories(config.resolve("data/conversations"));
		Files.createDirectories(config.resolve("runtime"));
		Files.writeString(config.resolve("data/folders.json"), """
				{"version":1,"folders":[
				  {"id":"root","name":"littlechemistry","parentId":null},
				  {"id":"logs","name":"logs","parentId":"root"}
				]}
				""");
		Path socket = config.resolve("runtime/exocortexd.sock");
		Path source = temporaryDirectory.resolve("native.json");
		Files.writeString(source, """
				{"version":17,"id":"generation-test-id","provider":"openai","model":"gpt-test",
				 "effort":"medium","fastMode":false,"messages":[],"activeContext":null,
				 "createdAt":1,"updatedAt":2,"lastContextTokens":null,"marked":false,"pinned":false,
				 "sortOrder":-1,"folderId":null,"title":"Generate Test","goal":null,
				 "subagentMaxDepth":null,"storageGeneration":1,"lastUnwindReceipt":null}
				""");

		try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
				var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			server.bind(UnixDomainSocketAddress.of(socket));
			var received = executor.submit(() -> {
				try (var client = server.accept()) {
					ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
					StringBuilder text = new StringBuilder();
					while (client.read(buffer) >= 0) {
						buffer.flip();
						text.append(java.nio.charset.StandardCharsets.UTF_8.decode(buffer));
						buffer.clear();
					}
					return text.toString();
				}
			});

			ExocortexConversationExporter.findExact(config, true).orElseThrow().publish(source);
			String[] commands = received.get().lines().toArray(String[]::new);
			assertEquals(2, commands.length);
			assertEquals("load_conversation",
					JsonParser.parseString(commands[0]).getAsJsonObject().get("type").getAsString());
			var move = JsonParser.parseString(commands[1]).getAsJsonObject();
			assertEquals("move_sidebar_items", move.get("type").getAsString());
			assertEquals("logs", move.get("parentId").getAsString());
		}

		Path imported = config.resolve("data/conversations/generation-test-id.json");
		assertTrue(Files.isRegularFile(imported));
		assertTrue(JsonParser.parseString(Files.readString(imported)).getAsJsonObject().get("folderId").isJsonNull());
	}
}
