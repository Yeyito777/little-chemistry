package com.yeyito.littlechemistry.ai.generation;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Produces untruncated source-like Java from the classpath bytecode available to the running game. */
final class JavaSourceDecompiler {
	private static final int CACHED_CLASS_FAMILIES = 32;
	private static final Semaphore DECOMPILERS = new Semaphore(2, true);
	private static final ConcurrentHashMap<String, CompletableFuture<DecompiledSource>> IN_FLIGHT =
			new ConcurrentHashMap<>();
	private static final Map<String, DecompiledSource> CACHE = Collections.synchronizedMap(
			new LinkedHashMap<>(CACHED_CLASS_FAMILIES, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, DecompiledSource> eldest) {
					return size() > CACHED_CLASS_FAMILIES;
				}
			});

	private JavaSourceDecompiler() {
	}

	static DecompiledSource decompile(Class<?> requestedType) throws IOException {
		String cacheKey = cacheKey(requestedType);
		DecompiledSource existing = CACHE.get(cacheKey);
		if (existing != null) return existing;
		CompletableFuture<DecompiledSource> pending = new CompletableFuture<>();
		CompletableFuture<DecompiledSource> active = IN_FLIGHT.putIfAbsent(cacheKey, pending);
		if (active != null) return await(active);

		try {
			DecompiledSource result = decompileUncached(requestedType);
			CACHE.put(cacheKey, result);
			pending.complete(result);
			return result;
		} catch (Throwable error) {
			pending.completeExceptionally(error);
			return rethrow(error);
		} finally {
			IN_FLIGHT.remove(cacheKey, pending);
		}
	}

	private static DecompiledSource decompileUncached(Class<?> requestedType) throws IOException {
		boolean acquired = false;
		try {
			DECOMPILERS.acquire();
			acquired = true;
			ClassFamily family = classFamily(requestedType);
			Map<String, String> outputs = new HashMap<>();
			MemoryClassSource input = new MemoryClassSource(family.classes(), outputs);
			DiagnosticLogger diagnostics = new DiagnosticLogger();
			Decompiler decompiler = Decompiler.builder()
					.inputs(input)
					.output(NoOpResultSaver.INSTANCE)
					.logger(diagnostics)
					.option(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, true)
					.option(IFernflowerPreferences.DECOMPILE_INNER, true)
					.option(IFernflowerPreferences.REMOVE_BRIDGE, false)
					.option(IFernflowerPreferences.REMOVE_SYNTHETIC, false)
					.option(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, true)
					.option(IFernflowerPreferences.USE_METHOD_PARAMETERS, true)
					.option(IFernflowerPreferences.THREADS, "1")
					.option(IFernflowerPreferences.LOG_LEVEL, "WARN")
					.option(IFernflowerPreferences.BANNER, "")
					.build();
			decompiler.decompile();

			String source = outputs.get(family.topLevelInternalName());
			if (source == null && outputs.size() == 1) source = outputs.values().iterator().next();
			if (source == null || source.isBlank()) {
				throw new IOException("Vineflower did not produce Java source for "
						+ family.topLevelInternalName().replace('/', '.'));
			}
			return new DecompiledSource(family.topLevelInternalName().replace('/', '.'), source,
					family.classes().size(), diagnostics.messages());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("Java source inspection was interrupted", interrupted);
		} finally {
			if (acquired) DECOMPILERS.release();
		}
	}

	private static DecompiledSource await(CompletableFuture<DecompiledSource> active) throws IOException {
		try {
			return active.get();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("Java source inspection was interrupted", interrupted);
		} catch (ExecutionException failed) {
			return rethrow(failed.getCause());
		}
	}

	private static DecompiledSource rethrow(Throwable error) throws IOException {
		if (error instanceof IOException io) throw io;
		if (error instanceof RuntimeException runtime) throw runtime;
		if (error instanceof Error fatal) throw fatal;
		throw new IOException("Java source inspection failed", error);
	}

	private static String cacheKey(Class<?> requestedType) {
		String topLevelName = topLevelInternalName(requestedType.getName().replace('.', '/'));
		CodeSource source = requestedType.getProtectionDomain().getCodeSource();
		String origin = source == null || source.getLocation() == null ? "unknown" : source.getLocation().toExternalForm();
		return topLevelName + "|" + origin + "|" + System.identityHashCode(requestedType.getClassLoader());
	}

	private static ClassFamily classFamily(Class<?> requestedType) throws IOException {
		String requestedInternalName = requestedType.getName().replace('.', '/');
		String topLevelInternalName = topLevelInternalName(requestedInternalName);
		ClassFamily codeSource = codeSourceClassFamily(requestedType, requestedInternalName, topLevelInternalName);
		if (codeSource != null) return codeSource;
		ClassFamily installed = installedClassFamily(requestedInternalName, topLevelInternalName);
		if (installed != null) return installed;

		String resourceName = topLevelInternalName + IContextSource.CLASS_SUFFIX;
		try (InputStream input = classResource(requestedType, resourceName)) {
			if (input == null) throw new IOException("Classpath bytecode is unavailable: " + requestedType.getName());
			return new ClassFamily(topLevelInternalName, Map.of(topLevelInternalName, input.readAllBytes()));
		}
	}

	private static ClassFamily installedClassFamily(String requestedInternalName, String topLevelInternalName)
			throws IOException {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (Path root : mod.getRootPaths()) {
				ClassFamily family = directoryClassFamily(root, requestedInternalName, topLevelInternalName);
				if (family != null) return family;
			}
		}
		return null;
	}

	private static ClassFamily codeSourceClassFamily(Class<?> requestedType, String requestedInternalName,
			String topLevelInternalName) throws IOException {
		CodeSource codeSource = requestedType.getProtectionDomain().getCodeSource();
		if (codeSource == null || codeSource.getLocation() == null) return null;
		Path location;
		try {
			location = Path.of(codeSource.getLocation().toURI());
		} catch (URISyntaxException | IllegalArgumentException error) {
			return null;
		}
		if (Files.isDirectory(location)) {
			return directoryClassFamily(location, requestedInternalName, topLevelInternalName);
		}
		if (!Files.isRegularFile(location)) return null;

		String requestedResource = requestedInternalName + IContextSource.CLASS_SUFFIX;
		String familyPrefix = topLevelInternalName + "$";
		Map<String, byte[]> classes = new HashMap<>();
		try (JarFile jar = new JarFile(location.toFile())) {
			if (jar.getJarEntry(requestedResource) == null) return null;
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				String name = entry.getName();
				if (!name.equals(topLevelInternalName + IContextSource.CLASS_SUFFIX)
						&& !(name.startsWith(familyPrefix) && name.endsWith(IContextSource.CLASS_SUFFIX))) continue;
				String internalName = name.substring(0, name.length() - IContextSource.CLASS_SUFFIX.length());
				try (InputStream input = jar.getInputStream(entry)) {
					classes.put(internalName, input.readAllBytes());
				}
			}
		}
		return completedFamily(requestedInternalName, topLevelInternalName, classes);
	}

	private static ClassFamily directoryClassFamily(Path root, String requestedInternalName,
			String topLevelInternalName) throws IOException {
		if (!Files.isRegularFile(root.resolve(requestedInternalName + IContextSource.CLASS_SUFFIX))) return null;
		Path topLevelClass = root.resolve(topLevelInternalName + IContextSource.CLASS_SUFFIX);
		Path packageDirectory = topLevelClass.getParent();
		if (packageDirectory == null || !Files.isDirectory(packageDirectory)) return null;
		String baseName = topLevelClass.getFileName().toString();
		baseName = baseName.substring(0, baseName.length() - IContextSource.CLASS_SUFFIX.length());
		Map<String, byte[]> classes = new HashMap<>();
		try (var paths = Files.list(packageDirectory)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				String fileName = path.getFileName().toString();
				if (!fileName.equals(baseName + IContextSource.CLASS_SUFFIX)
						&& !(fileName.startsWith(baseName + "$")
						&& fileName.endsWith(IContextSource.CLASS_SUFFIX))) continue;
				String simpleInternal = fileName.substring(0,
						fileName.length() - IContextSource.CLASS_SUFFIX.length());
				int packageEnd = topLevelInternalName.lastIndexOf('/');
				String internalName = packageEnd < 0 ? simpleInternal
						: topLevelInternalName.substring(0, packageEnd + 1) + simpleInternal;
				classes.put(internalName, Files.readAllBytes(path));
			}
		}
		return completedFamily(requestedInternalName, topLevelInternalName, classes);
	}

	private static ClassFamily completedFamily(String requestedInternalName, String topLevelInternalName,
			Map<String, byte[]> classes) throws IOException {
		if (!classes.containsKey(topLevelInternalName)) {
			throw new IOException("Top-level class bytecode is unavailable for " + requestedInternalName);
		}
		return new ClassFamily(topLevelInternalName, Map.copyOf(classes));
	}

	private static String topLevelInternalName(String internalName) {
		int nested = internalName.indexOf('$');
		return nested < 0 ? internalName : internalName.substring(0, nested);
	}

	private static InputStream classResource(Class<?> type, String resourceName) {
		ClassLoader loader = type.getClassLoader();
		return loader == null ? ClassLoader.getSystemResourceAsStream(resourceName) : loader.getResourceAsStream(resourceName);
	}

	record DecompiledSource(String className, String javaSource, int classFileCount, List<String> diagnostics) {
	}

	private record ClassFamily(String topLevelInternalName, Map<String, byte[]> classes) {
	}

	private static final class DiagnosticLogger extends IFernflowerLogger {
		private static final int MAX_DIAGNOSTICS = 100;
		private final List<String> messages = new ArrayList<>();

		private DiagnosticLogger() {
			setSeverity(Severity.WARN);
		}

		List<String> messages() {
			return List.copyOf(messages);
		}

		@Override
		public void writeMessage(String message, Severity severity) {
			if (messages.size() < MAX_DIAGNOSTICS) messages.add(severity + ": " + oneLine(message));
		}

		@Override
		public void writeMessage(String message, Severity severity, Throwable error) {
			if (messages.size() >= MAX_DIAGNOSTICS) return;
			String detail = error == null || error.getMessage() == null ? "" : ": " + oneLine(error.getMessage());
			messages.add(severity + ": " + oneLine(message) + detail);
		}

		private static String oneLine(String value) {
			return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").strip();
		}
	}

	private record MemoryClassSource(Map<String, byte[]> classes, Map<String, String> outputs)
			implements IContextSource {
		@Override
		public String getName() {
			return "little-chemistry-runtime-bytecode";
		}

		@Override
		public Entries getEntries() {
			return new Entries(classes.keySet().stream().sorted().map(Entry::atBase).toList(), List.of(), List.of());
		}

		@Override
		public InputStream getInputStream(String resource) {
			String internalName = resource.endsWith(CLASS_SUFFIX)
					? resource.substring(0, resource.length() - CLASS_SUFFIX.length()) : resource;
			byte[] bytes = classes.get(internalName);
			return bytes == null ? null : new ByteArrayInputStream(bytes);
		}

		@Override
		public IOutputSink createOutputSink(IResultSaver ignored) {
			return new IOutputSink() {
				@Override public void begin() { }

				@Override
				public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
					outputs.put(qualifiedName, content);
				}

				@Override public void acceptDirectory(String directory) { }
				@Override public void acceptOther(String path) { }
				@Override public void close() { }
			};
		}
	}

	private enum NoOpResultSaver implements IResultSaver {
		INSTANCE;

		@Override public void saveFolder(String path) { }
		@Override public void copyFile(String source, String path, String entryName) { }
		@Override public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) { }
		@Override public void createArchive(String path, String archiveName, Manifest manifest) { }
		@Override public void saveDirEntry(String path, String archiveName, String entryName) { }
		@Override public void copyEntry(String source, String path, String archiveName, String entryName) { }
		@Override public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) { }
		@Override public void closeArchive(String path, String archiveName) { }
	}
}
