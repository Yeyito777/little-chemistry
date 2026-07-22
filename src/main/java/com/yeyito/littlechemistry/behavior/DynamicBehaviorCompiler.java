package com.yeyito.littlechemistry.behavior;

import net.fabricmc.loader.api.FabricLoader;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class DynamicBehaviorCompiler {
	public static final String GENERATED_CLASS_NAME = "GeneratedBehaviorImpl";

	private DynamicBehaviorCompiler() {
	}

	public static Compiled compile(String source) {
		return compile(DynamicBehaviorSourceBundle.legacy(normalize(source)));
	}

	/** Compiles and links an entry class and all of its persisted runtime helper sources as one isolated module. */
	public static Compiled compile(DynamicBehaviorSourceBundle sourceBundle) {
		if (sourceBundle == null) throw new IllegalArgumentException("Behavior source bundle is empty");
		DynamicBehaviorSourceBundle normalized = new DynamicBehaviorSourceBundle(
				sourceBundle.entryClass(), sourceBundle.entrySourcePath(), sourceBundle.sources());
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StringWriter compilerOutput = new StringWriter();
		EclipseCompiler compiler = new EclipseCompiler();
		try (StandardJavaFileManager standardFiles = compiler.getStandardFileManager(
				diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			configureClassPath(standardFiles);
			List<SourceFile> compilationUnits = normalized.sources().entrySet().stream()
					.map(source -> new SourceFile(source.getKey(), source.getValue())).toList();
			MemoryFileManager files = new MemoryFileManager(standardFiles, compilationUnits);
			List<String> options = List.of(
					"--release", "25",
					"-proc:none",
					"-encoding", StandardCharsets.UTF_8.name(),
					"-g:source,lines"
			);
			boolean succeeded = Boolean.TRUE.equals(compiler.getTask(
					compilerOutput,
					files,
					diagnostics,
					options,
					null,
					compilationUnits
			).call());
			if (!succeeded) {
				throw new IllegalArgumentException(compilationFailure(diagnostics.getDiagnostics(), compilerOutput));
			}

			Map<String, byte[]> classFiles = files.classFiles();
			if (!classFiles.containsKey(normalized.entryClass())) {
				throw new IllegalArgumentException("Generated behavior did not emit " + normalized.entryClass());
			}
			GeneratedClassLoader classLoader = new GeneratedClassLoader(
					DynamicBehavior.class.getClassLoader(), classFiles);
			classLoader.linkAll();
			Class<?> generated = Class.forName(normalized.entryClass(), false, classLoader);
			if (!DynamicBehavior.class.isAssignableFrom(generated)) {
				throw new IllegalArgumentException(normalized.entryClass() + " must implement DynamicBehavior");
			}
			validateCapabilities(normalized, generated, classLoader, classFiles.keySet());
			@SuppressWarnings("unchecked")
			Class<? extends DynamicBehavior> behaviorClass = (Class<? extends DynamicBehavior>) generated;
			Constructor<? extends DynamicBehavior> constructor = behaviorClass.getConstructor();
			return new Compiled(normalized.entrySource(), normalized, constructor, classLoader);
		} catch (IOException error) {
			throw new IllegalArgumentException("Java compiler I/O failed: " + safeMessage(error), error);
		} catch (ReflectiveOperationException | LinkageError error) {
			throw new IllegalArgumentException("Generated behavior class is invalid: " + safeMessage(error), error);
		}
	}

	static void configureClassPath(StandardJavaFileManager files) throws IOException {
		LinkedHashSet<Path> classPath = new LinkedHashSet<>();
		String configured = System.getProperty("java.class.path", "");
		if (!configured.isBlank()) {
			for (String entry : configured.split(java.io.File.pathSeparator)) {
				if (!entry.isBlank()) addClassPath(classPath, Path.of(entry));
			}
		}
		addCodeSource(classPath, DynamicBehavior.class);
		FabricLauncher launcher = fabricLauncher();
		if (launcher != null) {
			for (Path path : launcher.getClassPath()) addClassPath(classPath, path);
		}
		try {
			FabricLoader.getInstance().getAllMods().forEach(mod -> {
				for (Path path : mod.getOrigin().getPaths()) addClassPath(classPath, path);
			});
		} catch (RuntimeException | LinkageError ignored) {
			// Fabric Loader is not initialized in ordinary unit-test JVMs; java.class.path is sufficient there.
		}
		files.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
		files.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of());
	}

	private static FabricLauncher fabricLauncher() {
		try {
			return FabricLauncherBase.getLauncher();
		} catch (RuntimeException | LinkageError ignored) {
			return null;
		}
	}

	private static void addCodeSource(Set<Path> classPath, Class<?> type) {
		CodeSource source = type.getProtectionDomain().getCodeSource();
		if (source == null || source.getLocation() == null) return;
		try {
			addClassPath(classPath, Path.of(source.getLocation().toURI()));
		} catch (URISyntaxException | IllegalArgumentException ignored) {
			// The surrounding runtime class path remains available when a non-file code source cannot be represented.
		}
	}

	private static void addClassPath(Set<Path> classPath, Path path) {
		try {
			Path normalized = path.toAbsolutePath().normalize();
			if (normalized.getFileSystem().equals(Path.of(".").toAbsolutePath().getFileSystem())
					&& (Files.isDirectory(normalized) || Files.isRegularFile(normalized))) {
				classPath.add(normalized);
			}
		} catch (RuntimeException ignored) {
			// Ignore virtual or inaccessible mod roots that the standard compiler file manager cannot consume.
		}
	}

	private static String compilationFailure(List<Diagnostic<? extends JavaFileObject>> diagnostics,
			StringWriter compilerOutput) {
		StringBuilder message = new StringBuilder("Java compilation failed");
		diagnostics.stream()
				.sorted(Comparator.comparingLong((Diagnostic<?> diagnostic) -> diagnostic.getLineNumber())
						.thenComparingLong(Diagnostic::getColumnNumber))
				.limit(20)
				.forEach(diagnostic -> {
					message.append(System.lineSeparator());
					if (diagnostic.getLineNumber() >= 0) {
						message.append("Line ").append(diagnostic.getLineNumber());
						if (diagnostic.getColumnNumber() >= 0) {
							message.append(", column ").append(diagnostic.getColumnNumber());
						}
						message.append(": ");
					}
					message.append(diagnostic.getKind().name().toLowerCase(Locale.ROOT))
							.append(": ").append(diagnostic.getMessage(Locale.ROOT));
				});
		String rawOutput = compilerOutput.toString().strip();
		if (diagnostics.isEmpty() && !rawOutput.isEmpty()) {
			message.append(System.lineSeparator()).append(rawOutput);
		}
		return message.toString();
	}

	private static void validateCapabilities(DynamicBehaviorSourceBundle sourceBundle, Class<?> generated,
			ClassLoader classLoader, Set<String> generatedClassNames) {
		Set<DynamicBehaviorCapability> declared = DynamicBehaviorSource.capabilities(sourceBundle);
		String entryClass = sourceBundle.entryClass();
		for (DynamicBehaviorCapability capability : DynamicBehaviorCapability.values()) {
			if (declared.contains(capability) && !declared.containsAll(capability.requiredCapabilities())) {
				throw new IllegalArgumentException(entryClass + " must declare "
						+ capability.requiredCapabilities().iterator().next().interfaceName() + " with "
						+ capability.interfaceName());
			}
			boolean implemented = capability.interfaceClass().isAssignableFrom(generated);
			if (implemented != declared.contains(capability)) {
				throw new IllegalArgumentException(entryClass + " must declare "
						+ capability.interfaceName() + " directly when implementing "
						+ capability.callbackName());
			}
			boolean declaresCallback = Arrays.stream(generated.getDeclaredMethods())
					.anyMatch(method -> method.getName().equals(capability.callbackName()));
			if (declaresCallback && !implemented) {
				throw new IllegalArgumentException(entryClass + " declares "
						+ capability.callbackName() + " but does not implement "
						+ capability.interfaceName());
			}
		}
		boolean workstationBehavior = WorkstationBehavior.class.isAssignableFrom(generated);
		boolean entityBehavior = EntitySpawnedBehavior.class.isAssignableFrom(generated)
				|| EntityTickBehavior.class.isAssignableFrom(generated)
				|| EntityInteractBehavior.class.isAssignableFrom(generated)
				|| EntityPreHurtBehavior.class.isAssignableFrom(generated)
				|| EntityPreAttackBehavior.class.isAssignableFrom(generated)
				|| EntityHurtBehavior.class.isAssignableFrom(generated)
				|| EntityAttackBehavior.class.isAssignableFrom(generated)
				|| EntityDeathBehavior.class.isAssignableFrom(generated);
		if (workstationBehavior || entityBehavior) validateSingletonState(
				entryClass, generated, classLoader, generatedClassNames, workstationBehavior);
	}

	private static void validateSingletonState(String entryClass, Class<?> generated, ClassLoader classLoader,
			Set<String> generatedClassNames, boolean workstationBehavior) {
		Set<Class<?>> inspected = new HashSet<>();
		for (Class<?> current = generated; current != null && current != Object.class; current = current.getSuperclass()) {
			inspectSingletonFields(entryClass, current, inspected, workstationBehavior);
		}
		for (String className : generatedClassNames) {
			try {
				inspectSingletonFields(entryClass, Class.forName(className, false, classLoader),
						inspected, workstationBehavior);
			} catch (ClassNotFoundException impossible) {
				throw new IllegalArgumentException("Generated behavior helper could not be inspected: " + className, impossible);
			}
		}
	}

	private static void inspectSingletonFields(String entryClass, Class<?> type, Set<Class<?>> inspected,
			boolean workstationBehavior) {
		if (!inspected.add(type)) return;
		for (java.lang.reflect.Field field : type.getDeclaredFields()) {
			if (field.isSynthetic()) continue;
			int modifiers = field.getModifiers();
			boolean safeConstant = java.lang.reflect.Modifier.isStatic(modifiers)
					&& java.lang.reflect.Modifier.isFinal(modifiers) && isSafeConstantType(field.getType());
			if (safeConstant) continue;
			String stateApi = workstationBehavior ? "DynamicWorkstationContext state" : "DynamicEntityState";
			throw new IllegalArgumentException(entryClass + " and its runtime helpers must not declare mutable field "
					+ type.getName() + "." + field.getName() + "; use " + stateApi);
		}
	}

	private static boolean isSafeConstantType(Class<?> type) {
		return type.isPrimitive() || type.isEnum() || type == String.class || type == Class.class
				|| type == Boolean.class || type == Character.class || type == Byte.class || type == Short.class
				|| type == Integer.class || type == Long.class || type == Float.class || type == Double.class;
	}

	private static String normalize(String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalArgumentException("Behavior source is empty");
		}
		String normalized = source.strip();
		if (normalized.length() > 65_536 || normalized.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Behavior source must be valid text no longer than 65,536 characters");
		}
		return normalized;
	}

	private static String safeMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) return error.getClass().getSimpleName();
		String oneLine = message.replaceAll("[\\r\\n]+", " ").trim();
		return oneLine.length() <= 2_000 ? oneLine : oneLine.substring(0, 2_000) + "…";
	}

	public record Compiled(
			String source,
			DynamicBehaviorSourceBundle sourceBundle,
			Constructor<? extends DynamicBehavior> constructor,
			ClassLoader classLoader
	) {
		public DynamicBehavior instantiate() {
			try {
				return constructor.newInstance();
			} catch (ReflectiveOperationException | LinkageError error) {
				throw new IllegalArgumentException("Could not instantiate generated behavior: " + safeMessage(error), error);
			}
		}
	}

	private static final class SourceFile extends SimpleJavaFileObject {
		private final String path;
		private final String source;

		private SourceFile(String path, String source) {
			super(URI.create("string:///" + path), Kind.SOURCE);
			this.path = path;
			this.source = source;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return source;
		}

		@Override
		public String getName() {
			return path;
		}
	}

	private static final class ClassFile extends SimpleJavaFileObject {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		private ClassFile(String className) {
			super(URI.create("memory:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
		}

		@Override
		public OutputStream openOutputStream() {
			return bytes;
		}

		private byte[] bytes() {
			return bytes.toByteArray();
		}
	}

	private static final class RuntimeClassFile extends SimpleJavaFileObject {
		private final String binaryName;
		private final byte[] bytes;

		private RuntimeClassFile(String binaryName, byte[] bytes) {
			super(URI.create("runtime:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
			this.binaryName = binaryName;
			this.bytes = bytes;
		}

		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public String getName() {
			return binaryName.replace('.', '/') + Kind.CLASS.extension;
		}
	}

	private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
		private final Set<SourceFile> sources;
		private final FabricLauncher launcher = fabricLauncher();
		private final Map<String, RuntimeClassFile> runtimeClasses = new HashMap<>();
		private final Set<String> unavailableRuntimeClasses = new HashSet<>();
		private final Map<String, ClassFile> outputs = new TreeMap<>();

		private MemoryFileManager(StandardJavaFileManager fileManager, List<SourceFile> sources) {
			super(fileManager);
			this.sources = Set.copyOf(sources);
		}

		@Override
		public boolean hasLocation(Location location) {
			return location == StandardLocation.SOURCE_PATH || super.hasLocation(location);
		}

		@Override
		public boolean contains(Location location, FileObject file) throws IOException {
			if (location == StandardLocation.SOURCE_PATH && sources.contains(file)) return true;
			return super.contains(location, file);
		}

		@Override
		public JavaFileObject getJavaFileForInput(Location location, String className,
				JavaFileObject.Kind kind) throws IOException {
			if (location == StandardLocation.CLASS_PATH && kind == JavaFileObject.Kind.CLASS) {
				RuntimeClassFile runtimeClass = runtimeClass(className);
				if (runtimeClass != null) return runtimeClass;
			}
			return super.getJavaFileForInput(location, className, kind);
		}

		@Override
		public Iterable<JavaFileObject> list(Location location, String packageName,
				Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
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
				RuntimeClassFile runtimeClass = runtimeClass(binaryName);
				resolved.add(runtimeClass == null ? file : runtimeClass);
			}
			return resolved;
		}

		@Override
		public String inferBinaryName(Location location, JavaFileObject file) {
			if (file instanceof RuntimeClassFile runtimeClass) return runtimeClass.binaryName;
			return super.inferBinaryName(location, file);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className,
				JavaFileObject.Kind kind, FileObject sibling) {
			if (kind != JavaFileObject.Kind.CLASS) {
				throw new IllegalArgumentException("Unexpected compiler output kind: " + kind);
			}
			String binaryName = className.replace('/', '.').replace('\\', '.');
			ClassFile output = new ClassFile(binaryName);
			outputs.put(binaryName, output);
			return output;
		}

		private Map<String, byte[]> classFiles() {
			Map<String, byte[]> result = new TreeMap<>();
			outputs.forEach((name, output) -> result.put(name, output.bytes()));
			return Map.copyOf(result);
		}

		private RuntimeClassFile runtimeClass(String className) {
			if (launcher == null || unavailableRuntimeClasses.contains(className)) return null;
			RuntimeClassFile cached = runtimeClasses.get(className);
			if (cached != null) return cached;
			try {
				// Disk jars do not reflect Fabric access wideners or Mixins. Compile against the same transformed
				// bytecode Knot defines at runtime so generated source sees the API it will actually execute against.
				byte[] bytes = launcher.getClassByteArray(className, true);
				if (bytes != null) {
					RuntimeClassFile runtimeClass = new RuntimeClassFile(className, bytes);
					runtimeClasses.put(className, runtimeClass);
					return runtimeClass;
				}
			} catch (IOException | RuntimeException | LinkageError ignored) {
				// Fall back to the configured disk class path when runtime transformation is unavailable.
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

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] bytes = classFiles.get(name);
			if (bytes == null) return super.findClass(name);
			return defineClass(name, bytes, 0, bytes.length);
		}

		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!classFiles.containsKey(name)) return super.loadClass(name, resolve);
			Class<?> loaded = findLoadedClass(name);
			if (loaded == null) loaded = findClass(name);
			if (resolve) resolveClass(loaded);
			return loaded;
		}

		private void linkAll() throws ClassNotFoundException {
			List<Class<?>> generated = new ArrayList<>();
			for (String name : classFiles.keySet()) {
				Class<?> type = loadClass(name, false);
				resolveClass(type);
				generated.add(type);
			}
			// Reflection resolves every emitted member signature without running generated static initializers.
			for (Class<?> type : generated) {
				type.getDeclaredConstructors();
				type.getDeclaredMethods();
				type.getDeclaredFields();
				type.getDeclaredClasses();
			}
		}
	}
}
