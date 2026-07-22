package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentJson;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** A real, isolated coding workspace backed by one world's persistent generated-mod source tree. */
public final class GenerationWorkspace implements AutoCloseable {
	static final Set<String> SOURCE_DIRECTORIES = Set.of(
			"blocks", "armors", "items", "entities", "particles", "textures", "workstations", "helpers");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_PUBLISHED_FILES = 2_000;
	private static final long MAX_PUBLISHED_BYTES = 16L * 1024L * 1024L;
	private static final Set<Path> PREPARED_REFERENCE_ROOTS = new java.util.HashSet<>();
	private static final java.util.IdentityHashMap<com.yeyito.littlechemistry.content.GeneratedContentSpec, PendingSource>
			PENDING_SOURCES = new java.util.IdentityHashMap<>();
	private static final java.util.Map<Path, Set<String>> RESERVED_IDENTIFIERS = new java.util.HashMap<>();

	private final Path worldRoot;
	private final Path jobRoot;
	private final Path referenceRoot;
	private final String jobId;
	private boolean staged;

	private GenerationWorkspace(Path worldRoot, Path jobRoot, Path referenceRoot, String jobId) {
		this.worldRoot = worldRoot;
		this.jobRoot = jobRoot;
		this.referenceRoot = referenceRoot;
		this.jobId = jobId;
	}

	static GenerationWorkspace testing(Path requestedWorldRoot, Path requestedJobRoot) throws IOException {
		Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
		Path jobRoot = requestedJobRoot.toAbsolutePath().normalize();
		Files.createDirectories(worldRoot);
		Files.createDirectories(jobRoot);
		for (String directory : SOURCE_DIRECTORIES) {
			Files.createDirectories(worldRoot.resolve(directory));
			Files.createDirectories(jobRoot.resolve(directory));
		}
		return new GenerationWorkspace(worldRoot, jobRoot, worldRoot.resolve("reference"),
				"test-" + UUID.randomUUID());
	}

	static GenerationWorkspace open(Path requestedWorldRoot, GenerationRequest request) throws IOException {
		Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
		Files.createDirectories(worldRoot);
		for (String directory : SOURCE_DIRECTORIES) Files.createDirectories(worldRoot.resolve(directory));
		Path referenceRoot = worldRoot.resolve("reference");
		prepareReference(referenceRoot);

		String jobId = UUID.randomUUID().toString();
		Path jobRoot = worldRoot.resolve(".jobs").resolve(jobId);
		Files.createDirectories(jobRoot);
		Files.createDirectories(jobRoot.resolve(".littlechemistry"));
		for (String directory : SOURCE_DIRECTORIES) Files.createDirectories(jobRoot.resolve(directory));
		copyExistingSnapshot(worldRoot, jobRoot.resolve("existing"));
		buildExistingSourcePath(jobRoot.resolve("existing"), jobRoot.resolve(".existing-sourcepath"));
		copyReferenceIndexes(referenceRoot, jobRoot.resolve("reference"));
		writeText(jobRoot.resolve("request.json"), GSON.toJson(encodeRequest(request)));
		if (!request.flexible()) writeFixedSkeleton(jobRoot, request);
		return new GenerationWorkspace(worldRoot, jobRoot, referenceRoot, jobId);
	}

	Path root() {
		return jobRoot;
	}

	Path worldRoot() {
		return worldRoot;
	}

	String jobId() {
		return jobId;
	}

	Path logsRoot() {
		return worldRoot.resolve("logs");
	}

	Path resolve(String relative) {
		if (relative == null || relative.isBlank()) return jobRoot;
		Path path = jobRoot.resolve(relative).normalize();
		if (!path.startsWith(jobRoot)) throw new IllegalArgumentException("Path escapes the generation workspace");
		Path current = jobRoot;
		for (Path segment : jobRoot.relativize(path)) {
			current = current.resolve(segment);
			if (Files.isSymbolicLink(current)) {
				throw new IllegalArgumentException("Symbolic links are not allowed in the generation workspace: "
						+ jobRoot.relativize(current));
			}
		}
		return path;
	}

	void requireWritable(String relative) {
		Path normalized = Path.of(relative == null ? "" : relative).normalize();
		if (normalized.isAbsolute() || normalized.startsWith("..")) {
			throw new IllegalArgumentException("Path escapes the generation workspace");
		}
		if (normalized.getNameCount() > 0) {
			String first = normalized.getName(0).toString();
			if (first.equals("existing") || first.equals("reference") || first.equals("request.json")
					|| first.equals(".verification") || first.equals(".existing-sourcepath")) {
				throw new IllegalArgumentException(first + " is read-only");
			}
		}
	}

	void materializeReference(String relative) throws IOException {
		Path path = Path.of(relative).normalize();
		if (path.isAbsolute() || path.startsWith("..") || path.getNameCount() < 3
				|| !path.getName(0).toString().equals("reference")) return;
		String family = path.getName(1).toString();
		String nestedPath = path.subpath(2, path.getNameCount()).toString();
		String content;
		if (family.equals("classes") && relative.endsWith(".java")) {
			String className = nestedPath.replace(java.io.File.separatorChar, '.')
					.replace('/', '.').replace('\\', '.');
			className = className.substring(0, className.length() - ".java".length());
			content = JavaCodeInspector.decompile(className);
		} else if (family.equals("vanilla") && relative.endsWith(".json")) {
			content = MinecraftReferenceExporter.materialize(nestedPath);
		} else {
			return;
		}
		Path shared = referenceRoot.resolve(family).resolve(nestedPath);
		writeText(shared, content);
		writeText(resolve(relative), content);
	}

	Path snapshotModule(DynamicContentType type, String identifier) throws IOException {
		Path snapshot = jobRoot.resolve(".verification").resolve(UUID.randomUUID().toString());
		Files.createDirectories(snapshot);
		String primaryCategory = category(type);
		try {
			for (String directory : SOURCE_DIRECTORIES) {
				if (!directory.equals(primaryCategory) && !Set.of(
						"textures", "particles", "workstations", "helpers").contains(directory)) continue;
				Path source = jobRoot.resolve(directory).resolve(identifier);
				if (!Files.isDirectory(source)) continue;
				copyTree(source, snapshot.resolve(directory).resolve(identifier),
						MAX_PUBLISHED_FILES, MAX_PUBLISHED_BYTES);
			}
			return snapshot;
		} catch (IOException | RuntimeException failure) {
			deleteTree(snapshot);
			throw failure;
		}
	}

	Path existingRoot() {
		return jobRoot.resolve(".existing-sourcepath");
	}

	void deleteSnapshot(Path snapshot) {
		try {
			if (snapshot != null && snapshot.normalize().startsWith(jobRoot.resolve(".verification"))) deleteTree(snapshot);
		} catch (IOException ignored) {
		}
	}

	static String sourceDigest(Path sourceRoot) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var paths = Files.walk(sourceRoot)) {
				for (Path file : paths.filter(Files::isRegularFile).filter(path -> {
					Path relative = sourceRoot.relativize(path);
					return relative.getNameCount() > 0
							&& SOURCE_DIRECTORIES.contains(relative.getName(0).toString());
				}).sorted().toList()) {
					Path relative = sourceRoot.relativize(file);
					digest.update(relative.toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
					digest.update((byte) 0);
					digest.update(Files.readAllBytes(file));
					digest.update((byte) 0);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	void stage(WorkspaceGenerationVerifier.VerifiedGeneration verified) throws IOException {
		synchronized (GenerationWorkspace.class) {
			if (staged) return;
			String identifier = DynamicContentManager.normalizeIdentifier(verified.displayName());
			String primaryCategory = category(verified.type());
			Path verifiedSource = verified.sourceSnapshot();
			if (verifiedSource == null || !verifiedSource.normalize().startsWith(jobRoot.resolve(".verification"))
					|| !sourceDigest(verifiedSource).equals(verified.sourceDigest())) {
				throw new IOException("Verified source snapshot is missing or changed");
			}
			Set<String> reserved = RESERVED_IDENTIFIERS.computeIfAbsent(worldRoot, ignored -> new java.util.HashSet<>());
			if (DynamicContentCatalog.find(identifier) != null || !reserved.add(identifier)) {
				deleteSnapshot(verifiedSource);
				if (reserved.isEmpty()) RESERVED_IDENTIFIERS.remove(worldRoot);
				throw new IllegalArgumentException("Dynamic content ID '" + identifier
						+ "' already exists or is reserved by another generation job");
			}
			Path pendingRoot = worldRoot.resolve(".pending").resolve(UUID.randomUUID().toString());
			Files.createDirectories(pendingRoot);
			try {
				JsonObject manifest = new JsonObject();
				manifest.addProperty("type", verified.type().serializedName());
				manifest.addProperty("identifier", identifier);
				manifest.addProperty("sourceDigest", verified.sourceDigest());
				writeText(pendingRoot.resolve("manifest.json"), GSON.toJson(manifest));
				for (String directory : SOURCE_DIRECTORIES) {
					if (!directory.equals(primaryCategory) && !Set.of(
							"textures", "particles", "workstations", "helpers").contains(directory)) continue;
					Path source = verifiedSource.resolve(directory).resolve(identifier);
					if (!Files.isDirectory(source)) continue;
					copyTree(source, pendingRoot.resolve(directory).resolve(identifier),
							MAX_PUBLISHED_FILES, MAX_PUBLISHED_BYTES);
				}
				if (!sourceDigest(pendingRoot).equals(verified.sourceDigest())) {
					throw new IOException("Staged source does not match the verified source digest");
				}
				PENDING_SOURCES.put(verified.content(), new PendingSource(
						pendingRoot, worldRoot, verified.type(), identifier, verified.compiledBehavior()));
				staged = true;
				deleteSnapshot(verifiedSource);
			} catch (IOException | RuntimeException failure) {
				deleteTree(pendingRoot);
				deleteSnapshot(verifiedSource);
				releaseReservation(worldRoot, identifier);
				throw failure;
			}
		}
	}

	/** Binds staged source to the exact immutable definition prepared for the server-thread commit. */
	public static void bindPending(com.yeyito.littlechemistry.content.GeneratedContentSpec generated,
			DynamicContentDefinition prepared) throws IOException {
		synchronized (GenerationWorkspace.class) {
			PendingSource pending = PENDING_SOURCES.get(generated);
			if (pending == null) return;
			if (pending.type() != prepared.type() || !pending.identifier().equals(prepared.name())) {
				throw new IOException("Verified source identity does not match the prepared definition");
			}
			Path manifestPath = pending.root().resolve("manifest.json");
			JsonObject manifest = com.google.gson.JsonParser.parseString(
					Files.readString(manifestPath, StandardCharsets.UTF_8)).getAsJsonObject();
			manifest.addProperty("definitionDigest", definitionDigest(prepared));
			writeText(manifestPath, GSON.toJson(manifest));
		}
	}

	/**
	 * Returns the behavior artifact already built by the asynchronous verifier.
	 *
	 * <p>Runtime content installation must not invoke ECJ again on the server thread. Content that did not come through
	 * the generation workspace has no staged artifact and is intentionally allowed to use the compiler fallback.</p>
	 */
	public static DynamicBehaviorCompiler.Compiled preparedBehavior(
			com.yeyito.littlechemistry.content.GeneratedContentSpec generated) {
		synchronized (GenerationWorkspace.class) {
			PendingSource pending = PENDING_SOURCES.get(generated);
			if (pending == null || pending.compiledBehavior() == null) return null;
			if (!pending.compiledBehavior().source().equals(generated.behaviorSource())) {
				throw new IllegalArgumentException("Staged behavior artifact does not match generated content");
			}
			return pending.compiledBehavior();
		}
	}

	/** Publishes a verified source branch only after its runtime definition has committed on the server thread. */
	public static void commitPending(com.yeyito.littlechemistry.content.GeneratedContentSpec generated,
			DynamicContentDefinition committed) throws IOException {
		synchronized (GenerationWorkspace.class) {
			PendingSource pending = PENDING_SOURCES.remove(generated);
			if (pending == null) return;
			boolean identityMatches = pending.type() == committed.type()
					&& pending.identifier().equals(committed.name());
				try {
					if (!identityMatches) {
						throw new IOException("Verified source identity does not match the committed definition");
					}
					JsonObject manifest = com.google.gson.JsonParser.parseString(Files.readString(
							pending.root().resolve("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
					String expectedDefinitionDigest = manifest.has("definitionDigest")
							? manifest.get("definitionDigest").getAsString() : "";
					if (!expectedDefinitionDigest.equals(definitionDigest(committed))) {
						throw new IOException("Pending source is not bound to the committed definition");
					}
					publishPendingTree(pending);
				deleteTree(pending.root());
			} finally {
				if (!identityMatches) deleteTree(pending.root());
				releaseReservation(pending.worldRoot(), pending.identifier());
			}
		}
	}

	public static void discardPending(com.yeyito.littlechemistry.content.GeneratedContentSpec generated) {
		synchronized (GenerationWorkspace.class) {
			PendingSource pending = PENDING_SOURCES.remove(generated);
			if (pending == null) return;
			try {
				deleteTree(pending.root());
			} catch (IOException ignored) {
			} finally {
				releaseReservation(pending.worldRoot(), pending.identifier());
			}
		}
	}

	private static void releaseReservation(Path worldRoot, String identifier) {
		Set<String> reserved = RESERVED_IDENTIFIERS.get(worldRoot);
		if (reserved == null) return;
		reserved.remove(identifier);
		if (reserved.isEmpty()) RESERVED_IDENTIFIERS.remove(worldRoot);
	}

	public static void discardPendingForWorld(Path requestedWorldRoot) {
		synchronized (GenerationWorkspace.class) {
			Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
			List<com.yeyito.littlechemistry.content.GeneratedContentSpec> stale = PENDING_SOURCES.entrySet().stream()
					.filter(entry -> entry.getValue().worldRoot().equals(worldRoot))
					.map(java.util.Map.Entry::getKey).toList();
			for (com.yeyito.littlechemistry.content.GeneratedContentSpec generated : stale) discardPending(generated);
			RESERVED_IDENTIFIERS.remove(worldRoot);
		}
	}

	@Override
	public void close() {
		try {
			deleteTree(jobRoot);
		} catch (IOException ignored) {
		}
	}

	/** Creates the persistent per-world source layout and exports the current catalog without starting an AI job. */
	public static void initialize(Path requestedWorldRoot, List<DynamicContentDefinition> definitions) throws IOException {
		Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
		Files.createDirectories(worldRoot);
		deleteTree(worldRoot.resolve(".jobs"));
		for (String directory : SOURCE_DIRECTORIES) Files.createDirectories(worldRoot.resolve(directory));
		recoverPending(worldRoot, definitions);
		for (DynamicContentDefinition definition : definitions) exportDefinition(worldRoot, definition);
	}

	private static void publishPendingTree(PendingSource pending) throws IOException {
		long[] totals = {0L, 0L};
		for (String directory : SOURCE_DIRECTORIES) {
			Path source = pending.root().resolve(directory).resolve(pending.identifier());
			if (!Files.isDirectory(source)) continue;
			mergeTree(source, pending.worldRoot().resolve(directory).resolve(pending.identifier()), totals);
		}
	}

	private static void recoverPending(Path worldRoot, List<DynamicContentDefinition> definitions) throws IOException {
		Path pendingDirectory = worldRoot.resolve(".pending");
		if (!Files.isDirectory(pendingDirectory)) return;
		java.util.Map<String, DynamicContentDefinition> byName = definitions.stream().collect(
				java.util.stream.Collectors.toUnmodifiableMap(DynamicContentDefinition::name, value -> value));
		try (var paths = Files.list(pendingDirectory)) {
			for (Path pendingRoot : paths.filter(Files::isDirectory).toList()) {
				boolean committedJournal = false;
				try {
					JsonObject manifest = com.google.gson.JsonParser.parseString(
							Files.readString(pendingRoot.resolve("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
						String identifier = manifest.get("identifier").getAsString();
						String expectedDigest = manifest.get("sourceDigest").getAsString();
						String expectedDefinitionDigest = manifest.get("definitionDigest").getAsString();
						DynamicContentType type = DynamicContentType.fromSerializedName(manifest.get("type").getAsString());
						DynamicContentDefinition definition = byName.get(identifier);
						committedJournal = definition != null && definition.type() == type
								&& expectedDigest.matches("[a-f0-9]{64}")
								&& expectedDigest.equals(sourceDigest(pendingRoot))
								&& expectedDefinitionDigest.matches("[a-f0-9]{64}")
								&& expectedDefinitionDigest.equals(definitionDigest(definition));
					if (committedJournal) {
							publishPendingTree(new PendingSource(pendingRoot, worldRoot, type, identifier, null));
					}
				} catch (RuntimeException ignored) {
					// A malformed or uncommitted journal is discarded below.
				} catch (IOException publishFailure) {
					if (committedJournal) throw publishFailure;
				} finally {
					if (!committedJournal) deleteTree(pendingRoot);
				}
				if (committedJournal) deleteTree(pendingRoot);
			}
		}
		Files.deleteIfExists(pendingDirectory);
	}

	public static void exportDefinition(Path requestedWorldRoot, DynamicContentDefinition definition) throws IOException {
		Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
		String category = category(definition.type());
		Path content = worldRoot.resolve(category).resolve(definition.name());
		Files.createDirectories(content);
		JsonObject canonical = DynamicContentJson.encodeDefinition(definition);
		writeText(content.resolve("definition.json"), GSON.toJson(canonical));
		writeText(content.resolve("GeneratedBehaviorImpl.java"), definition.behaviorSource() + "\n");

		JsonObject textures = new JsonObject();
		if (canonical.has("texture")) textures.add("inventory", canonical.get("texture").deepCopy());
		if (canonical.has("armorDisplayTexture")) {
			textures.add("armorDisplay", canonical.get("armorDisplayTexture").deepCopy());
		}
		if (canonical.has("blockModel")) {
			textures.add("blockModel", canonical.getAsJsonObject("blockModel").deepCopy());
		}
		if (canonical.has("entityModel")) {
			textures.add("entityModel", canonical.getAsJsonObject("entityModel").deepCopy());
		}
		Path textureDirectory = worldRoot.resolve("textures").resolve(definition.name());
		Files.createDirectories(textureDirectory);
		writeText(textureDirectory.resolve("canonical.json"), GSON.toJson(textures));

		Path particleDirectory = worldRoot.resolve("particles").resolve(definition.name());
		Files.createDirectories(particleDirectory);
		writeText(particleDirectory.resolve("canonical.json"), GSON.toJson(canonical.getAsJsonArray("customParticles")));
		if (canonical.has("workstation")) {
			Path workstation = worldRoot.resolve("workstations").resolve(definition.name());
			Files.createDirectories(workstation);
			writeText(workstation.resolve("canonical.json"), GSON.toJson(canonical.getAsJsonObject("workstation")));
		}
	}

	public static void removeDefinitions(Path requestedWorldRoot, Set<String> names) {
		Path worldRoot = requestedWorldRoot.toAbsolutePath().normalize();
		for (String directory : SOURCE_DIRECTORIES) {
			for (String name : names) {
				try {
					deleteTree(worldRoot.resolve(directory).resolve(name));
				} catch (IOException ignored) {
				}
			}
		}
		Path pendingDirectory = worldRoot.resolve(".pending");
		if (Files.isDirectory(pendingDirectory)) {
			try (var paths = Files.list(pendingDirectory)) {
				for (Path pendingRoot : paths.filter(Files::isDirectory).toList()) {
					try {
						JsonObject manifest = com.google.gson.JsonParser.parseString(Files.readString(
								pendingRoot.resolve("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
						if (names.contains(manifest.get("identifier").getAsString())) deleteTree(pendingRoot);
					} catch (IOException | RuntimeException ignored) {
					}
				}
			} catch (IOException ignored) {
			}
		}
	}

	private static String definitionDigest(DynamicContentDefinition definition) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] canonical = DynamicContentJson.encodeDefinition(definition).toString()
					.getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(digest.digest(canonical));
		} catch (NoSuchAlgorithmException impossible) {
			throw new AssertionError(impossible);
		}
	}

	static String category(DynamicContentType type) {
		return switch (type) {
			case BLOCK -> "blocks";
			case ITEM -> "items";
			case ARMOR -> "armors";
			case ENTITY -> "entities";
		};
	}

	static String javaPackageSegment(String identifier) {
		return "c_" + identifier;
	}

	static String className(String identifier) {
		return "C_" + identifier + "_Content";
	}

	private static JsonObject encodeRequest(GenerationRequest request) {
		JsonObject encoded = new JsonObject();
		encoded.addProperty("mode", request.flexible() ? "recipe" : "fixed");
		if (request.flexible()) {
			encoded.addProperty("resultFile", ".littlechemistry/result.json");
			encoded.addProperty("allowedKinds", "item, block, helmet, chestplate, leggings, boots, entity"
					+ (request.workstationPolicy() == null ? "" : ", rejection"));
		} else {
			encoded.addProperty("requestedKind", request.fixedType().serializedName());
			encoded.addProperty("requestedName", request.fixedDisplayName());
			encoded.addProperty("requestedOutputCount", request.fixedOutputCount());
			if (request.fixedArmorSlot() != null) {
				encoded.addProperty("requestedArmorSlot", request.fixedArmorSlot().serializedName());
			}
			String id = DynamicContentManager.normalizeIdentifier(
					DynamicContentManager.normalizeDisplayName(request.fixedDisplayName()));
			String category = category(request.fixedType());
			String factory = className(id);
			encoded.addProperty("factoryClass", category + "." + javaPackageSegment(id) + "." + factory);
			encoded.addProperty("factoryFile", category + "/" + id + "/" + factory + ".java");
			encoded.addProperty("behaviorFile", category + "/" + id + "/GeneratedBehaviorImpl.java");
		}
		if (request.recipeContext() != null) encoded.add("recipe", request.recipeContext());
		if (request.workstationPolicy() != null) encoded.addProperty("workstationPolicy", request.workstationPolicy());
		if (request.recipeDataSchema() != null) encoded.add("recipeDataSchema", request.recipeDataSchema());
		return encoded;
	}

	private static synchronized void prepareReference(Path referenceRoot) throws IOException {
		Path normalized = referenceRoot.toAbsolutePath().normalize();
		if (PREPARED_REFERENCE_ROOTS.contains(normalized)) return;
		try {
			Files.createDirectories(normalized.resolve("classes"));
			JavaCodeInspector.writeIndex(normalized.resolve("classes/INDEX.txt"));
			MinecraftReferenceExporter.writeIndex(normalized.resolve("vanilla"));
			writeText(normalized.resolve("API.md"), API_DOCUMENTATION);
			PREPARED_REFERENCE_ROOTS.add(normalized);
		} catch (IOException | RuntimeException failure) {
			PREPARED_REFERENCE_ROOTS.remove(normalized);
			throw failure;
		}
	}

	private static void writeFixedSkeleton(Path root, GenerationRequest request) throws IOException {
		String id = DynamicContentManager.normalizeIdentifier(
				DynamicContentManager.normalizeDisplayName(request.fixedDisplayName()));
		String category = category(request.fixedType());
		String className = className(id);
		Path directory = root.resolve(category).resolve(id);
		Files.createDirectories(directory);
		writeText(directory.resolve(className + ".java"), """
				package %s.%s;

				import com.yeyito.littlechemistry.ai.generation.GeneratedContentFactory;
				import com.yeyito.littlechemistry.content.GeneratedContentSpec;

				public final class %s implements GeneratedContentFactory {
				    public %s() {}

				    @Override
				    public GeneratedContentSpec create(String behaviorSource) throws Exception {
				        throw new UnsupportedOperationException("Implement the complete generated definition");
				    }
				}
				""".formatted(category, javaPackageSegment(id), className, className));
		writeText(directory.resolve("GeneratedBehaviorImpl.java"), """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				}
				""");
		for (String support : List.of("textures", "particles", "helpers")) {
			Files.createDirectories(root.resolve(support).resolve(id));
		}
		if (request.fixedType() == DynamicContentType.BLOCK) {
			Files.createDirectories(root.resolve("workstations").resolve(id));
		}
	}

	private static void copyReferenceIndexes(Path source, Path destination) throws IOException {
		Files.createDirectories(destination.resolve("classes"));
		Files.createDirectories(destination.resolve("vanilla"));
		for (String relative : List.of("API.md", "classes/INDEX.txt", "vanilla/README.md", "vanilla/TEXTURES.txt")) {
			Path input = source.resolve(relative);
			Path output = destination.resolve(relative);
			Files.createDirectories(output.getParent());
			Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void buildExistingSourcePath(Path existing, Path sourcePath) throws IOException {
		Files.createDirectories(sourcePath);
		Pattern packageDeclaration = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
		try (var paths = Files.walk(existing)) {
			for (Path source : paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> !path.getFileName().toString().equals("GeneratedBehaviorImpl.java")).toList()) {
				String content = Files.readString(source, StandardCharsets.UTF_8);
				var declaration = packageDeclaration.matcher(content);
				if (!declaration.find()) continue;
				Path destination = sourcePath.resolve(declaration.group(1).replace('.', '/'))
						.resolve(source.getFileName().toString());
				Files.createDirectories(destination.getParent());
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static void copyExistingSnapshot(Path worldRoot, Path destination) throws IOException {
		Files.createDirectories(destination);
		for (String directory : SOURCE_DIRECTORIES) {
			Path source = worldRoot.resolve(directory);
			if (Files.isDirectory(source)) copyTree(source, destination.resolve(directory), 50_000,
					128L * 1024L * 1024L);
		}
	}

	private static void copyTree(Path source, Path destination, int maxFiles, long maxBytes) throws IOException {
		long[] totals = {0L, 0L};
		if (!Files.exists(source)) return;
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
					throws IOException {
				Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(file)) throw new IOException("Symbolic links are not allowed: " + file);
				totals[0]++;
				totals[1] += attributes.size();
				if (totals[0] > maxFiles || totals[1] > maxBytes) {
					throw new IOException("Generation workspace snapshot exceeds its bounded size");
				}
				Path target = destination.resolve(source.relativize(file).toString());
				Files.createDirectories(target.getParent());
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void mergeTree(Path source, Path destination, long[] totals) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
					throws IOException {
				Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(file)) throw new IOException("Symbolic links are not allowed: " + file);
				totals[0]++;
				totals[1] += attributes.size();
				if (totals[0] > MAX_PUBLISHED_FILES || totals[1] > MAX_PUBLISHED_BYTES) {
					throw new IOException("Generated source exceeds the publish size limit");
				}
				Path target = destination.resolve(source.relativize(file).toString());
				Files.createDirectories(target.getParent());
				Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
				Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING);
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void writeText(Path path, String text) throws IOException {
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
		Files.writeString(temporary, text, StandardCharsets.UTF_8);
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
				if (error != null) throw error;
				Files.deleteIfExists(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private record PendingSource(Path root, Path worldRoot, DynamicContentType type, String identifier,
			DynamicBehaviorCompiler.Compiled compiledBehavior) {
	}

	private static final String API_DOCUMENTATION = """
			# Generated Java API

			The factory contract is `com.yeyito.littlechemistry.ai.generation.GeneratedContentFactory` and its method is
			`GeneratedContentSpec create(String behaviorSource) throws Exception`. `GeneratedContentBuilder` is an optional
			fluent assembly helper. `GeneratedContentApi` provides `texture`, `modelTexture`, `face`, `uniformFaces`,
			`presetModel`, `selfDrops`, `id`, and `json`; these are conveniences, not a restricted DSL.

			All public constructors and Minecraft APIs are available directly. Search the class index and read source before
			guessing signatures. Common definition classes are in `com.yeyito.littlechemistry.content`: `GeneratedContentSpec`,
			`DynamicTextureSpec`, `DynamicBlockProperties`, `DynamicItemProperties`, `DynamicArmorProperties`,
			`DynamicArmorDisplayTextureSpec`, `DynamicEntityProperties`, `DynamicBlockModel`, `DynamicEntityModel`,
			`DynamicParticleDefinition`, and `DynamicWorkstationSpec`. Common enums include `DynamicRarity`, `DynamicMaterial`,
			`DynamicTool`, `DynamicBlockShape`, `DynamicItemType`, `DynamicHeldType`, `DynamicArmorSlot`,
			`DynamicEntityMovement`, and `DynamicEntityDisposition`.

			A typical factory returns:
			```
			return GeneratedContentBuilder.create()
			    .texture(Textures.icon())
			    .item(properties)
			    .rarity(DynamicRarity.RARE)
			    .description("A concise sentence.")
			    .particles(Particles.definitions())
			    .build(behaviorSource);
			```
			The vanilla `Rarity` stored in block/item/armor properties must equal `DynamicRarity.vanillaRarity()`.
			Indexed texture rows contain hexadecimal palette indices and palettes contain `RRGGBBAA` colors. Icons are exactly
			16 rows of 16; equipped armor is 32 rows of 64. Use `GeneratedContentApi.modelTexture` and particle-frame helpers
			or `DynamicTextureAsset.sha256(texture.renderPng())` so persisted hashes are exact.
			""";
}
