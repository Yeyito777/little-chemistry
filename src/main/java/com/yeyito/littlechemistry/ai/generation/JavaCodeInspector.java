package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicBehavior;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class JavaCodeInspector {
	private static volatile List<ClassEntry> classIndex;

	private JavaCodeInspector() {
	}

	static ContentGenerationDraft.ToolExecution search(JsonObject arguments) {
		try {
			requireOnly(arguments, "query", "scope");
			String query = requiredString(arguments, "query").strip().toLowerCase(Locale.ROOT);
			String scope = arguments.has("scope") ? requiredString(arguments, "scope") : "any";
			if (query.isEmpty()) throw new IllegalArgumentException("query is empty");
			if (!Set.of("any", "minecraft", "little_chemistry", "fabric").contains(scope)) {
				throw new IllegalArgumentException("scope must be any, minecraft, little_chemistry, or fabric");
			}

			List<ClassEntry> matches = classes().stream()
					.filter(entry -> scope.equals("any") || inScope(entry.modId(), scope))
					.filter(entry -> matches(entry.className(), query))
					.sorted(Comparator
							.comparingInt((ClassEntry entry) -> score(entry.className(), query))
							.thenComparing(ClassEntry::className))
					.limit(80)
					.toList();
			JsonObject output = new JsonObject();
			output.addProperty("query", query);
			output.addProperty("scope", scope);
			JsonArray encoded = new JsonArray();
			for (ClassEntry match : matches) {
				JsonObject value = new JsonObject();
				value.addProperty("className", match.className());
				value.addProperty("mod", match.modId());
				encoded.add(value);
			}
			output.add("matches", encoded);
			output.addProperty("message", matches.isEmpty()
					? "No matching loaded class was found. Try a shorter class or concept name."
					: "Use inspect_java_class for signatures or inspect_java_source for complete decompiled method bodies.");
			return ContentGenerationDraft.ToolExecution.success(output, null);
		} catch (Exception error) {
			return ContentGenerationDraft.ToolExecution.error("JAVA_CLASS_SEARCH_FAILED", safeMessage(error));
		}
	}

	static ContentGenerationDraft.ToolExecution inspect(JsonObject arguments) {
		try {
			requireOnly(arguments, "className", "memberQuery", "includeInherited");
			String className = requiredString(arguments, "className").strip();
			String memberQuery = arguments.has("memberQuery")
					? requiredString(arguments, "memberQuery").strip().toLowerCase(Locale.ROOT) : "";
			boolean inherited = arguments.has("includeInherited") && arguments.get("includeInherited").getAsBoolean();
			Class<?> type = Class.forName(className, false, DynamicBehavior.class.getClassLoader());

			JsonObject output = new JsonObject();
			output.addProperty("className", type.getName());
			output.addProperty("kind", kind(type));
			output.addProperty("modifiers", Modifier.toString(type.getModifiers()));
			if (type.getSuperclass() != null) output.addProperty("superclass", type.getSuperclass().getTypeName());
			JsonArray interfaces = new JsonArray();
			for (Class<?> implemented : type.getInterfaces()) interfaces.add(implemented.getTypeName());
			output.add("interfaces", interfaces);

			if (type.isRecord()) {
				JsonArray components = new JsonArray();
				for (RecordComponent component : type.getRecordComponents()) {
					components.add(component.getGenericType().getTypeName() + " " + component.getName());
				}
				output.add("recordComponents", components);
			}

			JsonArray constructors = new JsonArray();
			for (Constructor<?> constructor : sorted(type.getDeclaredConstructors(), Constructor::toGenericString)) {
				String signature = constructor.toGenericString();
				if (contains(signature, memberQuery)) constructors.add(signature);
			}
			output.add("constructors", constructors);

			Field[] fieldValues = inherited ? type.getFields() : type.getDeclaredFields();
			JsonArray fields = new JsonArray();
			for (Field field : sorted(fieldValues, Field::toGenericString)) {
				if (field.isSynthetic()) continue;
				String signature = field.toGenericString();
				if (contains(signature, memberQuery)) fields.add(signature);
			}
			output.add("fields", fields);

			Method[] methodValues = inherited ? type.getMethods() : type.getDeclaredMethods();
			JsonArray methods = new JsonArray();
			for (Method method : sorted(methodValues, Method::toGenericString)) {
				if (method.isSynthetic() || method.isBridge()) continue;
				String signature = method.toGenericString();
				if (contains(signature, memberQuery)) methods.add(signature);
			}
			output.add("methods", methods);

			JsonArray nested = new JsonArray();
			for (Class<?> child : type.getDeclaredClasses()) nested.add(child.getName());
			output.add("nestedClasses", nested);
			output.addProperty("note", "These are source-like runtime signatures. Use inspect_java_source on this class for complete decompiled method bodies, and inspect parameter or return classes recursively as needed.");
			return ContentGenerationDraft.ToolExecution.success(output, null);
		} catch (ClassNotFoundException error) {
			return ContentGenerationDraft.ToolExecution.error("JAVA_CLASS_NOT_FOUND",
					"Class was not found. Use search_java_classes first: " + safeMessage(error));
		} catch (Exception | LinkageError error) {
			return ContentGenerationDraft.ToolExecution.error("JAVA_CLASS_INSPECTION_FAILED", safeMessage(error));
		}
	}

	static ContentGenerationDraft.ToolExecution inspectSource(JsonObject arguments) {
		try {
			requireOnly(arguments, "className");
			String className = requiredString(arguments, "className").strip();
			Class<?> type = Class.forName(className, false, DynamicBehavior.class.getClassLoader());
			JavaSourceDecompiler.DecompiledSource source = JavaSourceDecompiler.decompile(type);

			JsonObject output = new JsonObject();
			output.addProperty("requestedClassName", type.getName());
			output.addProperty("decompiledClassName", source.className());
			output.addProperty("sourceKind", "vineflower_decompiled_classpath_bytecode");
			output.addProperty("classFileCount", source.classFileCount());
			output.addProperty("javaSource", source.javaSource());
			output.addProperty("truncated", false);
			JsonArray diagnostics = new JsonArray();
			source.diagnostics().forEach(diagnostics::add);
			output.add("decompilerDiagnostics", diagnostics);
			output.addProperty("note", "This is untruncated source-like Java reconstructed from installed classpath bytecode, including method bodies and available companion class files. It may differ cosmetically from the original author's source and does not include transformations applied later by access wideners or Mixins.");
			return ContentGenerationDraft.ToolExecution.success(output, null);
		} catch (ClassNotFoundException error) {
			return ContentGenerationDraft.ToolExecution.error("JAVA_CLASS_NOT_FOUND",
					"Class was not found. Use search_java_classes first: " + safeMessage(error));
		} catch (Exception | LinkageError error) {
			return ContentGenerationDraft.ToolExecution.error("JAVA_SOURCE_INSPECTION_FAILED", safeMessage(error));
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
				for (Path root : mod.getRootPaths()) {
					indexRoot(entries, seen, modId, root);
				}
			}
			entries.sort(Comparator.comparing(ClassEntry::className));
			classIndex = List.copyOf(entries);
			LittleChemistry.LOGGER.info("Indexed {} Java classes for generated behavior inspection in {} ms",
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
			LittleChemistry.LOGGER.debug("Could not index Java classes under {}", root, error);
		}
	}

	private static boolean inScope(String modId, String scope) {
		return switch (scope) {
			case "minecraft" -> modId.equals("minecraft");
			case "little_chemistry" -> modId.equals(LittleChemistry.MOD_ID);
			case "fabric" -> modId.startsWith("fabric") || modId.equals("fabricloader");
			default -> true;
		};
	}

	private static boolean matches(String className, String query) {
		String normalized = className.toLowerCase(Locale.ROOT);
		for (String token : query.split("[^a-z0-9_$]+")) {
			if (!token.isBlank() && !normalized.contains(token)) return false;
		}
		return true;
	}

	private static int score(String className, String query) {
		String normalized = className.toLowerCase(Locale.ROOT);
		String simple = normalized.substring(normalized.lastIndexOf('.') + 1);
		if (simple.equals(query)) return 0;
		if (simple.startsWith(query)) return 1;
		if (simple.contains(query)) return 2;
		return 3;
	}

	private static String kind(Class<?> type) {
		if (type.isAnnotation()) return "annotation";
		if (type.isEnum()) return "enum";
		if (type.isRecord()) return "record";
		if (type.isInterface()) return "interface";
		return "class";
	}

	private static boolean contains(String value, String query) {
		return query.isEmpty() || value.toLowerCase(Locale.ROOT).contains(query);
	}

	private static <T> List<T> sorted(T[] values, java.util.function.Function<T, String> key) {
		List<T> result = new ArrayList<>(List.of(values));
		result.sort(Comparator.comparing(key));
		return result;
	}

	private static void requireOnly(JsonObject object, String... allowed) {
		Set<String> names = Set.of(allowed);
		for (String key : object.keySet()) {
			if (!names.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
		}
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			throw new IllegalArgumentException("Missing string: " + key);
		}
		return object.get(key).getAsString();
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
