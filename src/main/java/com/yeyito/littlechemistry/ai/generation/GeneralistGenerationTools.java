package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Exocortex-style general filesystem/process tools plus one final build verifier. */
final class GeneralistGenerationTools {
	private static final int MAX_TEXT_ARGUMENT = 512 * 1024;
	private static final int MAX_TOOL_OUTPUT = 80 * 1024;
	private static final int MAX_WALK_FILES = 50_000;
	private static final Set<String> NAMES = Set.of("bash", "read", "grep", "glob", "write", "edit", "patch", "verify");

	private final GenerationWorkspace workspace;
	private final GenerationRequest request;

	GeneralistGenerationTools(GenerationWorkspace workspace, GenerationRequest request) {
		this.workspace = workspace;
		this.request = request;
	}

	static JsonArray definitions() {
		JsonArray tools = new JsonArray();
		tools.add(tool("bash", "Run a bash command in the generation workspace. Use for ordinary source inspection and diagnostics; commands time out and return stdout/stderr.",
				objectSchema(new String[] {"command"}, property("command", stringSchema(1, 32_768)),
						property("timeout_seconds", integerSchema(1, 30)))));
		tools.add(tool("read", "Read a UTF-8 text file with line numbers. Runtime class-source paths under reference/classes are materialized on demand.",
				objectSchema(new String[] {"path"}, property("path", stringSchema(1, 1_024)),
						property("offset", integerSchema(1, 1_000_000)), property("limit", integerSchema(1, 2_000)))));
		tools.add(tool("grep", "Search UTF-8 files recursively with a Java regular expression and optional glob filter.",
				objectSchema(new String[] {"pattern"}, property("pattern", stringSchema(1, 4_096)),
						property("path", stringSchema(0, 1_024)), property("glob", stringSchema(0, 512)),
						property("ignore_case", booleanSchema()), property("limit", integerSchema(1, 2_000)))));
		tools.add(tool("glob", "List workspace files matching a glob such as **/*.java, sorted by path.",
				objectSchema(new String[] {"pattern"}, property("pattern", stringSchema(1, 512)),
						property("path", stringSchema(0, 1_024)), property("limit", integerSchema(1, 5_000)))));
		tools.add(tool("write", "Create or replace a UTF-8 source file inside the writable job workspace.",
				objectSchema(new String[] {"path", "content"}, property("path", stringSchema(1, 1_024)),
						property("content", stringSchema(0, MAX_TEXT_ARGUMENT)))));
		tools.add(tool("edit", "Replace one unique exact text region in a writable file.",
				objectSchema(new String[] {"path", "old_text", "new_text"}, property("path", stringSchema(1, 1_024)),
						property("old_text", stringSchema(1, MAX_TEXT_ARGUMENT)),
						property("new_text", stringSchema(0, MAX_TEXT_ARGUMENT)))));
		tools.add(tool("patch", "Apply a standard unified diff relative to the workspace (patch -p0). Paths must be writable and may not escape the workspace.",
				objectSchema(new String[] {"diff"}, property("diff", stringSchema(1, MAX_TEXT_ARGUMENT)))));
		tools.add(tool("verify", "Compile the generated factory and behavior, execute the factory, and validate all request and runtime requirements. Fix every diagnostic and call verify again until successful.",
				objectSchema(new String[0])));
		return tools;
	}

	ToolResult execute(String name, JsonObject arguments) {
		if (!NAMES.contains(name)) return failure("UNKNOWN_TOOL", "Unknown generalist tool: " + name);
		try {
			if (arguments.has("_malformed")) throw new IllegalArgumentException("Tool arguments were not valid JSON");
			return switch (name) {
				case "bash" -> bash(arguments);
				case "read" -> read(arguments);
				case "grep" -> grep(arguments);
				case "glob" -> glob(arguments);
				case "write" -> write(arguments);
				case "edit" -> edit(arguments);
				case "patch" -> patch(arguments);
				case "verify" -> verify(arguments);
				default -> throw new AssertionError(name);
			};
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return failure("INTERRUPTED", "Tool execution was interrupted");
		} catch (Exception error) {
			return failure(name.equals("verify") ? "VERIFICATION_FAILED" : "TOOL_FAILED",
					name.equals("verify") ? verificationMessage(error) : safeMessage(error));
		}
	}

	private ToolResult bash(JsonObject arguments) throws IOException, InterruptedException {
		requireOnly(arguments, "command", "timeout_seconds");
		String command = requiredString(arguments, "command");
		int timeout = optionalInt(arguments, "timeout_seconds", 20, 1, 30);
		List<String> sandbox = sandboxCommand(List.of("/bin/bash", "-lc", command));
		ProcessBuilder builder = new ProcessBuilder(sandbox)
				.directory(workspace.root().toFile()).redirectErrorStream(false);
		configureSandboxEnvironment(builder);
		Process process = builder.start();
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		Thread outReader = Thread.startVirtualThread(() -> copyBounded(process.getInputStream(), stdout));
		Thread errorReader = Thread.startVirtualThread(() -> copyBounded(process.getErrorStream(), stderr));
		boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
		terminateProcessTree(process);
		if (!completed) process.waitFor(5, TimeUnit.SECONDS);
		outReader.join(Duration.ofSeconds(5));
		errorReader.join(Duration.ofSeconds(5));
		JsonObject output = success();
		output.addProperty("exitCode", completed ? process.exitValue() : -1);
		output.addProperty("timedOut", !completed);
		output.addProperty("stdout", truncate(stdout.toString(StandardCharsets.UTF_8)));
		output.addProperty("stderr", truncate(stderr.toString(StandardCharsets.UTF_8)));
		return new ToolResult(output, null);
	}

	private List<String> sandboxCommand(List<String> command) throws IOException {
		Path bubblewrap = Path.of("/usr/bin/bwrap");
		if (!Files.isExecutable(bubblewrap)) {
			throw new IOException("General process tools require /usr/bin/bwrap so the world job can be isolated from the host");
		}
		List<String> sandbox = new ArrayList<>(List.of(
				bubblewrap.toString(), "--die-with-parent", "--unshare-all", "--new-session", "--clearenv",
				"--ro-bind", "/usr", "/usr", "--ro-bind", "/bin", "/bin",
				"--ro-bind", "/lib", "/lib", "--ro-bind", "/etc", "/etc",
				"--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp",
				"--bind", workspace.root().toString(), "/workspace", "--chdir", "/workspace",
				"--setenv", "HOME", "/workspace", "--setenv", "PATH", "/usr/bin:/bin",
				"--setenv", "LANG", "C.UTF-8", "--setenv", "LC_ALL", "C.UTF-8"));
		if (Files.exists(Path.of("/lib64"))) {
			sandbox.addAll(11, List.of("--ro-bind", "/lib64", "/lib64"));
		}
		for (String readOnly : List.of("existing", "reference", ".existing-sourcepath", "request.json")) {
			Path source = workspace.root().resolve(readOnly);
			if (Files.exists(source)) sandbox.addAll(List.of(
					"--ro-bind", source.toString(), "/workspace/" + readOnly));
		}
		sandbox.addAll(List.of(
				"/usr/bin/prlimit", "--as=805306368", "--fsize=16777216", "--cpu=30", "--nofile=256", "--"));
		sandbox.addAll(command);
		Path systemdRun = Path.of("/usr/bin/systemd-run");
		String runtimeDirectory = System.getenv("XDG_RUNTIME_DIR");
		String busAddress = System.getenv("DBUS_SESSION_BUS_ADDRESS");
		if (!Files.isExecutable(systemdRun) || runtimeDirectory == null || runtimeDirectory.isBlank()
				|| busAddress == null || busAddress.isBlank()) return sandbox;
		List<String> limited = new ArrayList<>(List.of(
				systemdRun.toString(), "--user", "--scope", "--quiet", "--collect",
				"--property=MemoryMax=768M", "--property=MemorySwapMax=0",
				"--property=TasksMax=64", "--property=CPUQuota=200%", "--"));
		limited.addAll(sandbox);
		return limited;
	}

	private static void configureSandboxEnvironment(ProcessBuilder builder) {
		String runtimeDirectory = System.getenv("XDG_RUNTIME_DIR");
		String busAddress = System.getenv("DBUS_SESSION_BUS_ADDRESS");
		builder.environment().clear();
		if (runtimeDirectory != null && !runtimeDirectory.isBlank()) {
			builder.environment().put("XDG_RUNTIME_DIR", runtimeDirectory);
		}
		if (busAddress != null && !busAddress.isBlank()) {
			builder.environment().put("DBUS_SESSION_BUS_ADDRESS", busAddress);
		}
	}

	private ToolResult read(JsonObject arguments) throws IOException {
		requireOnly(arguments, "path", "offset", "limit");
		String relative = requiredString(arguments, "path");
		workspace.materializeReference(relative);
		Path path = workspace.resolve(relative);
		if (!Files.isRegularFile(path)) throw new IllegalArgumentException("File does not exist: " + relative);
		if (Files.size(path) > 2L * 1024L * 1024L) throw new IllegalArgumentException("File is too large for read: " + relative);
		String text = Files.readString(path, StandardCharsets.UTF_8);
		if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("File is not UTF-8 text: " + relative);
		List<String> lines = text.lines().toList();
		int offset = optionalInt(arguments, "offset", 1, 1, 1_000_000);
		int limit = optionalInt(arguments, "limit", 400, 1, 2_000);
		StringBuilder selected = new StringBuilder();
		int end = Math.min(lines.size(), offset - 1 + limit);
		for (int index = offset - 1; index < end; index++) {
			selected.append(index + 1).append('\t').append(lines.get(index)).append('\n');
			if (selected.length() >= MAX_TOOL_OUTPUT) break;
		}
		JsonObject output = success();
		output.addProperty("path", relative);
		output.addProperty("totalLines", lines.size());
		output.addProperty("content", truncate(selected.toString()));
		return new ToolResult(output, null);
	}

	private ToolResult grep(JsonObject arguments) throws IOException {
		requireOnly(arguments, "pattern", "path", "glob", "ignore_case", "limit");
		String expression = requiredString(arguments, "pattern");
		boolean ignoreCase = arguments.has("ignore_case") && arguments.get("ignore_case").getAsBoolean();
		Pattern pattern;
		try {
			pattern = Pattern.compile(expression, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
		} catch (PatternSyntaxException invalid) {
			throw new IllegalArgumentException("Invalid grep regular expression: " + invalid.getMessage());
		}
		String relative = optionalString(arguments, "path", "");
		Path start = workspace.resolve(relative);
		if (!Files.exists(start)) throw new IllegalArgumentException("Search path does not exist: " + relative);
		String fileGlob = optionalString(arguments, "glob", "**");
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + fileGlob);
		int limit = optionalInt(arguments, "limit", 500, 1, 2_000);
		List<Path> files = files(start);
		StringBuilder matches = new StringBuilder();
		int count = 0;
		for (Path file : files) {
			Path relativeFile = start.equals(file) ? file.getFileName() : start.relativize(file);
			if (!matcher.matches(relativeFile) && !matcher.matches(file.getFileName())) continue;
			if (Files.size(file) > 2L * 1024L * 1024L) continue;
			List<String> lines;
			try {
				lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			} catch (IOException invalidText) {
				continue;
			}
			for (int line = 0; line < lines.size(); line++) {
				if (!pattern.matcher(lines.get(line)).find()) continue;
				matches.append(workspace.root().relativize(file)).append(':').append(line + 1).append(':')
						.append(lines.get(line)).append('\n');
				if (++count >= limit || matches.length() >= MAX_TOOL_OUTPUT) break;
			}
			if (count >= limit || matches.length() >= MAX_TOOL_OUTPUT) break;
		}
		JsonObject output = success();
		output.addProperty("matches", count);
		output.addProperty("content", truncate(matches.toString()));
		return new ToolResult(output, null);
	}

	private ToolResult glob(JsonObject arguments) throws IOException {
		requireOnly(arguments, "pattern", "path", "limit");
		String expression = requiredString(arguments, "pattern");
		String relative = optionalString(arguments, "path", "");
		Path start = workspace.resolve(relative);
		if (!Files.exists(start)) throw new IllegalArgumentException("Glob path does not exist: " + relative);
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + expression);
		int limit = optionalInt(arguments, "limit", 1_000, 1, 5_000);
		JsonArray results = new JsonArray();
		for (Path file : files(start)) {
			Path candidate = workspace.root().relativize(file);
			Path fromStart = start.equals(file) ? file.getFileName() : start.relativize(file);
			if (!matcher.matches(candidate) && !matcher.matches(fromStart)) continue;
			results.add(candidate.toString());
			if (results.size() >= limit) break;
		}
		JsonObject output = success();
		output.add("paths", results);
		output.addProperty("limited", results.size() >= limit);
		return new ToolResult(output, null);
	}

	private ToolResult write(JsonObject arguments) throws IOException {
		requireOnly(arguments, "path", "content");
		String relative = requiredString(arguments, "path");
		String content = requiredString(arguments, "content");
		workspace.requireWritable(relative);
		Path path = workspace.resolve(relative);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		JsonObject output = success();
		output.addProperty("path", relative);
		output.addProperty("bytes", content.getBytes(StandardCharsets.UTF_8).length);
		return new ToolResult(output, null);
	}

	private ToolResult edit(JsonObject arguments) throws IOException {
		requireOnly(arguments, "path", "old_text", "new_text");
		String relative = requiredString(arguments, "path");
		workspace.requireWritable(relative);
		Path path = workspace.resolve(relative);
		String original = Files.readString(path, StandardCharsets.UTF_8);
		String oldText = requiredString(arguments, "old_text");
		int first = original.indexOf(oldText);
		if (first < 0) throw new IllegalArgumentException("old_text was not found exactly");
		if (original.indexOf(oldText, first + oldText.length()) >= 0) {
			throw new IllegalArgumentException("old_text matches more than once; include more context");
		}
		String replacement = requiredString(arguments, "new_text");
		Files.writeString(path, original.substring(0, first) + replacement + original.substring(first + oldText.length()),
				StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		JsonObject output = success();
		output.addProperty("path", relative);
		output.addProperty("replacements", 1);
		return new ToolResult(output, null);
	}

	private ToolResult patch(JsonObject arguments) throws IOException, InterruptedException {
		requireOnly(arguments, "diff");
		String diff = requiredString(arguments, "diff");
		for (String line : diff.lines().toList()) {
			if (!line.startsWith("--- ") && !line.startsWith("+++ ")) continue;
			String path = line.substring(4).split("[\\t ]", 2)[0];
			if (path.equals("/dev/null")) continue;
			while (path.startsWith("a/") || path.startsWith("b/")) path = path.substring(2);
			workspace.requireWritable(path);
			workspace.resolve(path);
		}
		ProcessBuilder builder = new ProcessBuilder(sandboxCommand(
				List.of("/usr/bin/patch", "--batch", "--forward", "-p0")))
				.directory(workspace.root().toFile()).redirectErrorStream(true);
		configureSandboxEnvironment(builder);
		Process process = builder.start();
		process.getOutputStream().write(diff.getBytes(StandardCharsets.UTF_8));
		process.getOutputStream().close();
		boolean completed = process.waitFor(20, TimeUnit.SECONDS);
		if (!completed) terminateProcessTree(process);
		String output = new String(process.getInputStream().readNBytes(MAX_TOOL_OUTPUT), StandardCharsets.UTF_8);
		if (!completed || process.exitValue() != 0) {
			throw new IllegalArgumentException("Patch failed: " + output.strip());
		}
		JsonObject result = success();
		result.addProperty("output", output);
		return new ToolResult(result, null);
	}

	private ToolResult verify(JsonObject arguments) throws Exception {
		requireOnly(arguments);
		var rejection = WorkspaceGenerationVerifier.readRejection(workspace, request);
		if (rejection != null) {
			JsonObject output = success();
			output.addProperty("verified", true);
			output.addProperty("kind", "rejection");
			output.addProperty("category", rejection.category().serializedName());
			output.addProperty("description", rejection.description());
			output.addProperty("message", "The workstation recipe rejection was accepted.");
			return new ToolResult(output, null, rejection);
		}
		WorkspaceGenerationVerifier.VerifiedGeneration verified = WorkspaceGenerationVerifier.verify(workspace, request);
		workspace.stage(verified);
		JsonObject output = success();
		output.addProperty("verified", true);
		output.addProperty("kind", verified.type().serializedName());
		output.addProperty("displayName", verified.displayName());
		output.addProperty("outputCount", verified.outputCount());
		output.addProperty("message", "Compilation and all runtime requirements passed.");
		return new ToolResult(output, verified);
	}

	private static List<Path> files(Path start) throws IOException {
		if (Files.isRegularFile(start, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return List.of(start);
		try (var paths = Files.walk(start)) {
			return paths.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
					.limit(MAX_WALK_FILES)
					.sorted(Comparator.comparing(Path::toString)).toList();
		}
	}

	private static void terminateProcessTree(Process process) {
		List<ProcessHandle> descendants = process.toHandle().descendants()
				.sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).toList();
		for (ProcessHandle descendant : descendants) descendant.destroy();
		for (ProcessHandle descendant : descendants) {
			if (descendant.isAlive()) descendant.destroyForcibly();
		}
		if (process.isAlive()) process.destroy();
		if (process.isAlive()) process.destroyForcibly();
	}

	private static void copyBounded(java.io.InputStream input, ByteArrayOutputStream output) {
		try (input) {
			byte[] buffer = new byte[8_192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				if (output.size() < MAX_TOOL_OUTPUT) {
					output.write(buffer, 0, Math.min(read, MAX_TOOL_OUTPUT - output.size()));
				}
			}
		} catch (IOException ignored) {
		}
	}

	private static ToolResult failure(String code, String message) {
		JsonObject output = new JsonObject();
		output.addProperty("ok", false);
		output.addProperty("code", code);
		output.addProperty("message", message);
		return new ToolResult(output, null);
	}

	private static JsonObject success() {
		JsonObject output = new JsonObject();
		output.addProperty("ok", true);
		return output;
	}

	private static String truncate(String value) {
		return value.length() <= MAX_TOOL_OUTPUT ? value : value.substring(0, MAX_TOOL_OUTPUT) + "\n…truncated…";
	}

	private static String verificationMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) current = current.getCause();
		String message = current.getMessage();
		if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
		return message.length() <= 24_000 ? message : message.substring(0, 24_000) + "\n…truncated…";
	}

	private static String safeMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) current = current.getCause();
		String message = current.getMessage();
		if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
		String normalized = message.replaceAll("[\\r\\n]+", " ").trim();
		return normalized.length() <= 8_000 ? normalized : normalized.substring(0, 8_000) + "…";
	}

	private static void requireOnly(JsonObject arguments, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String name : arguments.keySet()) {
			if (!names.contains(name)) throw new IllegalArgumentException("Unknown argument: " + name);
		}
	}

	private static String requiredString(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing string: " + key);
		return value.getAsString();
	}

	private static String optionalString(JsonObject object, String key, String fallback) {
		return object.has(key) ? requiredString(object, key) : fallback;
	}

	private static int optionalInt(JsonObject object, String key, int fallback, int minimum, int maximum) {
		if (!object.has(key)) return fallback;
		double raw = object.get(key).getAsDouble();
		if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < minimum || raw > maximum) {
			throw new IllegalArgumentException(key + " must be an integer from " + minimum + " to " + maximum);
		}
		return (int) raw;
	}

	private static JsonObject tool(String name, String description, JsonObject parameters) {
		JsonObject tool = new JsonObject();
		tool.addProperty("type", "function");
		tool.addProperty("name", name);
		tool.addProperty("description", description);
		tool.add("parameters", parameters);
		tool.addProperty("strict", false);
		return tool;
	}

	private static JsonObject objectSchema(String[] required, JsonObject... properties) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		JsonObject encodedProperties = new JsonObject();
		for (JsonObject property : properties) {
			String name = property.remove("_name").getAsString();
			encodedProperties.add(name, property);
		}
		schema.add("properties", encodedProperties);
		JsonArray requiredFields = new JsonArray();
		for (String name : required) requiredFields.add(name);
		schema.add("required", requiredFields);
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	private static JsonObject property(String name, JsonObject schema) {
		schema.addProperty("_name", name);
		return schema;
	}

	private static JsonObject stringSchema(int minimum, int maximum) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "string");
		schema.addProperty("minLength", minimum);
		schema.addProperty("maxLength", maximum);
		return schema;
	}

	private static JsonObject integerSchema(int minimum, int maximum) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "integer");
		schema.addProperty("minimum", minimum);
		schema.addProperty("maximum", maximum);
		return schema;
	}

	private static JsonObject booleanSchema() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "boolean");
		return schema;
	}

	record ToolResult(JsonObject output, WorkspaceGenerationVerifier.VerifiedGeneration verified,
			com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection rejection) {
		ToolResult(JsonObject output, WorkspaceGenerationVerifier.VerifiedGeneration verified) {
			this(output, verified, null);
		}
	}
}
