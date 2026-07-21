package com.yeyito.littlechemistry.ai.generation;

import com.yeyito.littlechemistry.behavior.DynamicBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Backs the workspace's virtual, lazily decompiled runtime class source tree. */
final class JavaCodeInspector {
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaCodeInspector.class);
	private static volatile List<ClassEntry> classIndex;

	private JavaCodeInspector() {
	}

	static void writeIndex(Path destination) throws IOException {
		StringBuilder index = new StringBuilder();
		for (ClassEntry entry : classes()) {
			index.append(entry.modId()).append('\t').append(entry.className()).append('\n');
		}
		Files.createDirectories(destination.getParent());
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.writeString(temporary, index, StandardCharsets.UTF_8);
		Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	static String decompile(String className) throws IOException {
		try {
			Class<?> type = Class.forName(className, false, DynamicBehavior.class.getClassLoader());
			return JavaSourceDecompiler.decompile(type).javaSource();
		} catch (ClassNotFoundException error) {
			throw new IOException("Java class was not found in the installed runtime: " + className, error);
		} catch (LinkageError error) {
			throw new IOException("Java class could not be linked: " + className + ": " + safeMessage(error), error);
		}
	}

	private static List<ClassEntry> classes() {
		List<ClassEntry> cached = classIndex;
		if (cached != null) return cached;
		synchronized (JavaCodeInspector.class) {
			if (classIndex != null) return classIndex;
			long started = System.nanoTime();
			List<ClassEntry> entries = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String modId = mod.getMetadata().getId();
				for (Path root : mod.getRootPaths()) indexRoot(entries, seen, modId, root);
			}
			indexJavaClassPath(entries, seen);
			entries.sort(Comparator.comparing(ClassEntry::className));
			classIndex = List.copyOf(entries);
			LOGGER.info("Indexed {} Java classes for the generation source mirror in {} ms",
					classIndex.size(), (System.nanoTime() - started) / 1_000_000L);
			return classIndex;
		}
	}

	private static void indexRoot(List<ClassEntry> destination, Set<String> seen, String modId, Path root) {
		try (Stream<Path> paths = Files.walk(root)) {
			paths.filter(Files::isRegularFile)
					.map(root::relativize)
					.map(Path::toString)
					.filter(path -> path.endsWith(".class") && !path.endsWith("module-info.class"))
					.map(path -> path.substring(0, path.length() - 6).replace('/', '.').replace('\\', '.'))
					.filter(name -> !name.isBlank())
					.forEach(name -> {
						if (seen.add(name)) destination.add(new ClassEntry(modId, name));
					});
		} catch (IOException | RuntimeException error) {
			LOGGER.debug("Could not index Java classes under {}", root, error);
		}
	}

	private static void indexJavaClassPath(List<ClassEntry> destination, Set<String> seen) {
		String configured = System.getProperty("java.class.path", "");
		for (String entry : configured.split(java.io.File.pathSeparator)) {
			if (entry.isBlank()) continue;
			Path path;
			try {
				path = Path.of(entry).toAbsolutePath().normalize();
			} catch (RuntimeException ignored) {
				continue;
			}
			if (Files.isDirectory(path)) {
				indexRoot(destination, seen, "classpath", path);
			} else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
				try (JarFile jar = new JarFile(path.toFile())) {
					jar.stream().map(java.util.jar.JarEntry::getName)
							.filter(name -> name.endsWith(".class") && !name.endsWith("module-info.class"))
							.map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
							.forEach(name -> {
								if (seen.add(name)) destination.add(new ClassEntry("classpath", name));
							});
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String safe = message.replaceAll("[\\r\\n]+", " ").trim();
		return safe.length() <= 2_000 ? safe : safe.substring(0, 2_000) + "…";
	}

	private record ClassEntry(String modId, String className) {
	}
}
