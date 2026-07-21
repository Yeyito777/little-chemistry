package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Optional local-development bridge that imports completed suite conversations into one exact existing Exocortex folder.
 * It never creates or guesses folders and is not enabled for ordinary generation jobs.
 */
public final class ExocortexConversationExporter {
	private static final Gson GSON = new Gson();
	private static final Gson PRETTY_GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
	private static final long MAX_FOLDERS_BYTES = 4L * 1024L * 1024L;
	private static final long MAX_CONVERSATION_BYTES = 32L * 1024L * 1024L;
	private static final String ROOT_FOLDER = "littlechemistry";
	private static final String LOGS_FOLDER = "logs";

	private final Path configDirectory;
	private final Path foldersFile;
	private final Path conversationsDirectory;
	private final Path daemonSocket;
	private final String rootFolderId;
	private final String logsFolderId;

	private ExocortexConversationExporter(Path configDirectory, String rootFolderId, String logsFolderId) {
		this.configDirectory = configDirectory;
		this.foldersFile = configDirectory.resolve("data/folders.json");
		this.conversationsDirectory = configDirectory.resolve("data/conversations");
		this.daemonSocket = configDirectory.resolve("runtime/exocortexd.sock");
		this.rootFolderId = rootFolderId;
		this.logsFolderId = logsFolderId;
	}

	/** Resolves only the exact top-level littlechemistry/logs path on a running default Exocortex instance. */
	public static Optional<ExocortexConversationExporter> findExactLittleChemistryLogs() {
		for (Path candidate : configCandidates()) {
			Optional<ExocortexConversationExporter> exporter = findExact(candidate, true);
			if (exporter.isPresent()) return exporter;
		}
		return Optional.empty();
	}

	static Optional<ExocortexConversationExporter> findExact(Path requestedConfigDirectory, boolean requireDaemon) {
		Path configDirectory = requestedConfigDirectory.toAbsolutePath().normalize();
		Path foldersFile = configDirectory.resolve("data/folders.json");
		Path conversations = configDirectory.resolve("data/conversations");
		Path socket = configDirectory.resolve("runtime/exocortexd.sock");
		if (!Files.isRegularFile(foldersFile) || !Files.isDirectory(conversations)
				|| requireDaemon && !Files.exists(socket)) return Optional.empty();
		try {
			FolderIds ids = exactFolderIds(foldersFile);
			return ids == null ? Optional.empty()
					: Optional.of(new ExocortexConversationExporter(configDirectory, ids.rootId(), ids.logsId()));
		} catch (IOException | RuntimeException ignored) {
			return Optional.empty();
		}
	}

	public String targetPath() {
		return ROOT_FOLDER + "/" + LOGS_FOLDER;
	}

	/** Atomically installs one terminal native conversation and asks the live daemon to load and move it. */
	public void publish(Path sourceConversation) throws IOException {
		if (!matchesExactFolder()) {
			throw new IOException("The exact Exocortex folder " + targetPath() + " no longer exists");
		}
		if (!Files.exists(daemonSocket)) throw new IOException("The Exocortex daemon socket is unavailable");
		if (!Files.isRegularFile(sourceConversation) || Files.size(sourceConversation) > MAX_CONVERSATION_BYTES) {
			throw new IOException("Native generation conversation is missing or too large");
		}
		JsonElement parsed = JsonParser.parseString(Files.readString(sourceConversation, StandardCharsets.UTF_8));
		if (!(parsed instanceof JsonObject conversation)
				|| !conversation.has("version")
				|| conversation.get("version").getAsInt() != GenerationConversationLog.EXOCORTEX_SCHEMA_VERSION) {
			throw new IOException("Generation log is not a native Exocortex conversation");
		}
		String id = primitiveString(conversation, "id");
		if (id == null || !id.matches("[A-Za-z0-9._-]+")) throw new IOException("Generation conversation has an unsafe ID");

		// Loading the new file first and then moving it through the daemon both registers it in memory and broadcasts the
		// sidebar update. Keep the initial persisted parent null so the move is a real mutation rather than a silent no-op.
		conversation.add("folderId", JsonNull.INSTANCE);
		Path destination = conversationsDirectory.resolve(id + ".json");
		writeAtomically(destination, PRETTY_GSON.toJson(conversation) + "\n");
		try {
			notifyDaemon(id);
		} catch (IOException notificationFailure) {
			Files.deleteIfExists(destination);
			throw notificationFailure;
		}
	}

	private boolean matchesExactFolder() {
		try {
			FolderIds current = exactFolderIds(foldersFile);
			return current != null && current.rootId().equals(rootFolderId) && current.logsId().equals(logsFolderId);
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}

	private void notifyDaemon(String conversationId) throws IOException {
		JsonObject load = new JsonObject();
		load.addProperty("type", "load_conversation");
		load.addProperty("reqId", "little-chemistry-load-" + UUID.randomUUID());
		load.addProperty("convId", conversationId);
		load.addProperty("turns", 1);

		JsonObject item = new JsonObject();
		item.addProperty("type", "conversation");
		item.addProperty("id", conversationId);
		JsonArray items = new JsonArray();
		items.add(item);
		JsonObject move = new JsonObject();
		move.addProperty("type", "move_sidebar_items");
		move.addProperty("reqId", "little-chemistry-move-" + UUID.randomUUID());
		move.add("items", items);
		move.addProperty("parentId", logsFolderId);
		move.addProperty("placement", "bottom");

		byte[] payload = (GSON.toJson(load) + "\n" + GSON.toJson(move) + "\n").getBytes(StandardCharsets.UTF_8);
		try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(UnixDomainSocketAddress.of(daemonSocket));
			ByteBuffer bytes = ByteBuffer.wrap(payload);
			while (bytes.hasRemaining()) channel.write(bytes);
		}
	}

	private static FolderIds exactFolderIds(Path foldersFile) throws IOException {
		if (Files.size(foldersFile) > MAX_FOLDERS_BYTES) throw new IOException("Exocortex folders file is too large");
		JsonElement parsed = JsonParser.parseString(Files.readString(foldersFile, StandardCharsets.UTF_8));
		if (!(parsed instanceof JsonObject root) || !(root.get("folders") instanceof JsonArray folders)) return null;
		List<JsonObject> roots = new ArrayList<>();
		for (JsonElement element : folders) {
			if (!(element instanceof JsonObject folder)) continue;
			if (ROOT_FOLDER.equals(primitiveString(folder, "name")) && nullParent(folder)) roots.add(folder);
		}
		if (roots.size() != 1) return null;
		String rootId = primitiveString(roots.getFirst(), "id");
		if (rootId == null) return null;
		List<JsonObject> logs = new ArrayList<>();
		for (JsonElement element : folders) {
			if (!(element instanceof JsonObject folder)) continue;
			if (LOGS_FOLDER.equals(primitiveString(folder, "name"))
					&& rootId.equals(primitiveString(folder, "parentId"))) logs.add(folder);
		}
		if (logs.size() != 1) return null;
		String logsId = primitiveString(logs.getFirst(), "id");
		return logsId == null ? null : new FolderIds(rootId, logsId);
	}

	private static boolean nullParent(JsonObject folder) {
		JsonElement parent = folder.get("parentId");
		return parent == null || parent.isJsonNull();
	}

	private static String primitiveString(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : null;
	}

	private static List<Path> configCandidates() {
		LinkedHashSet<Path> candidates = new LinkedHashSet<>();
		String property = System.getProperty("littlechemistry.exocortexConfigDir");
		if (property != null && !property.isBlank()) candidates.add(Path.of(property));
		String environment = System.getenv("EXOCORTEX_CONFIG_DIR");
		if (environment != null && !environment.isBlank()) candidates.add(Path.of(environment));
		Path home = Path.of(System.getProperty("user.home"));
		candidates.add(home.resolve("Workspace/exocortex/config"));
		candidates.add(home.resolve("Workspace/Exocortex/config"));
		candidates.add(home.resolve(".config/exocortex"));
		return List.copyOf(candidates);
	}

	private static void writeAtomically(Path destination, String value) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp-" + UUID.randomUUID());
		Files.writeString(temporary, value, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
		try {
			Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		} catch (UnsupportedOperationException ignored) {
		}
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private record FolderIds(String rootId, String logsId) {
	}
}
