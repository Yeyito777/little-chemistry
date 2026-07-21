package com.yeyito.littlechemistry.behavior;

import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Compiles a normal multi-file Java source tree from a world's generation workspace. */
public final class WorkspaceJavaCompiler {
	private WorkspaceJavaCompiler() {
	}

	public static <T> Compiled<T> compile(Path jobSourceRoot, Path worldSourceRoot, Path entrySource,
			String entryClassName, Class<T> contract) {
		return compile(jobSourceRoot, worldSourceRoot, List.of(entrySource), entryClassName, contract);
	}

	public static <T> Compiled<T> compile(Path jobSourceRoot, Path worldSourceRoot, List<Path> sourceFiles,
			String entryClassName, Class<T> contract) {
		Path jobRoot = jobSourceRoot.toAbsolutePath().normalize();
		Path worldRoot = worldSourceRoot.toAbsolutePath().normalize();
		List<Path> sources = sourceFiles.stream().map(path -> path.toAbsolutePath().normalize()).toList();
		if (sources.isEmpty()) throw new IllegalArgumentException("Generated factory module has no Java source");
		for (Path source : sources) {
			if (!source.startsWith(jobRoot) || !Files.isRegularFile(source)) {
				throw new IllegalArgumentException("Generated module source is missing: " + source);
			}
		}
		if (entryClassName == null || !entryClassName.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
			throw new IllegalArgumentException("Generated factory class name is invalid");
		}

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StringWriter compilerOutput = new StringWriter();
		EclipseCompiler compiler = new EclipseCompiler();
		try (StandardJavaFileManager standardFiles = compiler.getStandardFileManager(
				diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			DynamicBehaviorCompiler.configureClassPath(standardFiles);
			List<Path> sourceRoots = new ArrayList<>();
			sourceRoots.add(jobRoot);
			if (!worldRoot.equals(jobRoot) && Files.isDirectory(worldRoot)) sourceRoots.add(worldRoot);
			standardFiles.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourceRoots);
			Iterable<? extends JavaFileObject> units = standardFiles.getJavaFileObjectsFromPaths(sources);
			MemoryFileManager files = new MemoryFileManager(standardFiles);
			List<String> options = List.of(
					"--release", "25",
					"-proc:none",
					"-encoding", StandardCharsets.UTF_8.name(),
					"-g:source,lines"
			);
			boolean succeeded = Boolean.TRUE.equals(compiler.getTask(
					compilerOutput, files, diagnostics, options, null, units).call());
			if (!succeeded) throw new IllegalArgumentException(compilationFailure(
					diagnostics.getDiagnostics(), compilerOutput));

			Map<String, byte[]> classFiles = files.classFiles();
			if (!classFiles.containsKey(entryClassName)) {
				throw new IllegalArgumentException("Generated source did not emit " + entryClassName
						+ "; emitted classes: " + classFiles.keySet());
			}
			GeneratedClassLoader loader = new GeneratedClassLoader(contract.getClassLoader(), classFiles);
			loader.linkAll();
			Class<?> loaded = Class.forName(entryClassName, false, loader);
			if (!contract.isAssignableFrom(loaded)) {
				throw new IllegalArgumentException(entryClassName + " must implement " + contract.getName());
			}
			@SuppressWarnings("unchecked")
			Class<? extends T> implementation = (Class<? extends T>) loaded;
			Constructor<? extends T> constructor = implementation.getConstructor();
			return new Compiled<>(constructor, loader, Set.copyOf(classFiles.keySet()));
		} catch (IOException error) {
			throw new IllegalArgumentException("Generated source compiler I/O failed: " + safeMessage(error), error);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalArgumentException("Generated factory class is invalid: " + safeMessage(error), error);
		}
	}

	private static String compilationFailure(List<Diagnostic<? extends JavaFileObject>> diagnostics,
			StringWriter compilerOutput) {
		StringBuilder message = new StringBuilder("Generated source compilation failed");
		diagnostics.stream()
				.sorted(Comparator.comparingLong((Diagnostic<?> value) -> value.getLineNumber())
						.thenComparingLong(Diagnostic::getColumnNumber))
				.limit(40)
				.forEach(value -> {
					message.append(System.lineSeparator());
					if (value.getSource() != null) message.append(value.getSource().getName()).append(':');
					if (value.getLineNumber() >= 0) message.append(value.getLineNumber()).append(':');
					message.append(' ').append(value.getKind().name().toLowerCase(Locale.ROOT)).append(": ")
							.append(value.getMessage(Locale.ROOT));
				});
		String raw = compilerOutput.toString().strip();
		if (diagnostics.isEmpty() && !raw.isEmpty()) message.append(System.lineSeparator()).append(raw);
		return message.toString();
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String normalized = message.replaceAll("[\\r\\n]+", " ").trim();
		return normalized.length() <= 2_000 ? normalized : normalized.substring(0, 2_000) + "…";
	}

	public record Compiled<T>(Constructor<? extends T> constructor, ClassLoader classLoader,
			Set<String> emittedClasses) {
		public T instantiate() {
			try {
				return constructor.newInstance();
			} catch (ReflectiveOperationException | LinkageError error) {
				throw new IllegalArgumentException("Could not instantiate generated factory: " + safeMessage(error), error);
			}
		}
	}

	private static FabricLauncher fabricLauncher() {
		try {
			return FabricLauncherBase.getLauncher();
		} catch (RuntimeException | LinkageError ignored) {
			return null;
		}
	}

	private static final class OutputClass extends SimpleJavaFileObject {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		private OutputClass(String className) {
			super(java.net.URI.create("memory:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
		}

		@Override public OutputStream openOutputStream() { return bytes; }
		private byte[] bytes() { return bytes.toByteArray(); }
	}

	private static final class RuntimeClass extends SimpleJavaFileObject {
		private final String binaryName;
		private final byte[] bytes;

		private RuntimeClass(String binaryName, byte[] bytes) {
			super(java.net.URI.create("runtime:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
			this.binaryName = binaryName;
			this.bytes = bytes;
		}

		@Override public InputStream openInputStream() { return new ByteArrayInputStream(bytes); }
		@Override public String getName() { return binaryName.replace('.', '/') + Kind.CLASS.extension; }
	}

	private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
		private final FabricLauncher launcher = fabricLauncher();
		private final Map<String, RuntimeClass> runtimeClasses = new HashMap<>();
		private final Set<String> unavailableRuntimeClasses = new HashSet<>();
		private final Map<String, OutputClass> outputs = new TreeMap<>();

		private MemoryFileManager(StandardJavaFileManager files) { super(files); }

		@Override
		public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
				throws IOException {
			if (location == StandardLocation.CLASS_PATH && kind == JavaFileObject.Kind.CLASS) {
				RuntimeClass runtime = runtimeClass(className);
				if (runtime != null) return runtime;
			}
			return super.getJavaFileForInput(location, className, kind);
		}

		@Override
		public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
				boolean recurse) throws IOException {
			Iterable<JavaFileObject> listed = super.list(location, packageName, kinds, recurse);
			if (location != StandardLocation.CLASS_PATH || !kinds.contains(JavaFileObject.Kind.CLASS)
					|| launcher == null) return listed;
			List<JavaFileObject> resolved = new ArrayList<>();
			for (JavaFileObject file : listed) {
				if (file.getKind() != JavaFileObject.Kind.CLASS) {
					resolved.add(file);
					continue;
				}
				String binaryName = super.inferBinaryName(location, file);
				RuntimeClass runtime = runtimeClass(binaryName);
				resolved.add(runtime == null ? file : runtime);
			}
			return resolved;
		}

		@Override public String inferBinaryName(Location location, JavaFileObject file) {
			return file instanceof RuntimeClass runtime ? runtime.binaryName : super.inferBinaryName(location, file);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
				FileObject sibling) {
			if (kind != JavaFileObject.Kind.CLASS) throw new IllegalArgumentException("Unexpected output: " + kind);
			String binaryName = className.replace('/', '.').replace('\\', '.');
			OutputClass output = new OutputClass(binaryName);
			outputs.put(binaryName, output);
			return output;
		}

		private Map<String, byte[]> classFiles() {
			Map<String, byte[]> result = new TreeMap<>();
			outputs.forEach((name, output) -> result.put(name, output.bytes()));
			return Map.copyOf(result);
		}

		private RuntimeClass runtimeClass(String className) {
			if (launcher == null || unavailableRuntimeClasses.contains(className)) return null;
			RuntimeClass cached = runtimeClasses.get(className);
			if (cached != null) return cached;
			try {
				byte[] bytes = launcher.getClassByteArray(className, true);
				if (bytes != null) {
					RuntimeClass runtime = new RuntimeClass(className, bytes);
					runtimeClasses.put(className, runtime);
					return runtime;
				}
			} catch (IOException | RuntimeException | LinkageError ignored) {
			}
			unavailableRuntimeClasses.add(className);
			return null;
		}
	}

	private static final class GeneratedClassLoader extends ClassLoader {
		private final Map<String, byte[]> classFiles;

		private GeneratedClassLoader(ClassLoader parent, Map<String, byte[]> classFiles) {
			super(parent);
			this.classFiles = classFiles;
		}

		@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] bytes = classFiles.get(name);
			return bytes == null ? super.findClass(name) : defineClass(name, bytes, 0, bytes.length);
		}

		@Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!classFiles.containsKey(name)) return super.loadClass(name, resolve);
			Class<?> loaded = findLoadedClass(name);
			if (loaded == null) loaded = findClass(name);
			if (resolve) resolveClass(loaded);
			return loaded;
		}

		private void linkAll() throws ClassNotFoundException {
			for (String name : classFiles.keySet()) {
				Class<?> type = loadClass(name, false);
				resolveClass(type);
				type.getDeclaredConstructors();
				type.getDeclaredMethods();
				type.getDeclaredFields();
			}
		}
	}
}
