package com.yeyito.littlechemistry.behavior;

import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable, persistable Java source module for one runtime behavior.
 *
 * <p>Paths are logical bundle paths rather than host paths. Entry sources live below {@code entry/}; runtime helper
 * sources live below {@code helpers/}. The explicit binary entry-class name lets the compiler load a packaged entry
 * class without assuming that every behavior is a single default-package {@code GeneratedBehaviorImpl} unit.</p>
 */
public record DynamicBehaviorSourceBundle(
		String entryClass,
		String entrySourcePath,
		Map<String, String> sources
) {
	public static final String LEGACY_ENTRY_CLASS = DynamicBehaviorCompiler.GENERATED_CLASS_NAME;
	public static final String LEGACY_ENTRY_SOURCE_PATH = "entry/" + LEGACY_ENTRY_CLASS + ".java";
	private static final int MAX_SOURCE_FILES = 128;
	private static final int MAX_PATH_LENGTH = 256;
	private static final int MAX_ENTRY_CHARACTERS = 65_536;
	private static final int MAX_SOURCE_CHARACTERS = 512 * 1024;
	private static final int MAX_TOTAL_CHARACTERS = 4 * 1024 * 1024;
	private static final String BINARY_NAME = "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*";
	private static final String PATH_SEGMENT = "[A-Za-z0-9_$.-]+";

	/**
	 * Convenience form for API callers. Unqualified source paths are placed below {@code entry/} when their filename
	 * matches the entry class and below {@code helpers/} otherwise.
	 */
	public DynamicBehaviorSourceBundle(String entryClass, Map<String, String> sources) {
		this(entryClass, prepare(entryClass, sources));
	}

	private DynamicBehaviorSourceBundle(String entryClass, Prepared prepared) {
		this(entryClass, prepared.entrySourcePath(), prepared.sources());
	}

	public DynamicBehaviorSourceBundle {
		entryClass = entryClass == null ? "" : entryClass.strip();
		if (!entryClass.matches(BINARY_NAME)) {
			throw new IllegalArgumentException("Behavior entry class name is invalid");
		}
		entrySourcePath = normalizePath(entrySourcePath);
		if (!entrySourcePath.startsWith("entry/")) {
			throw new IllegalArgumentException("Behavior entry source must live below entry/");
		}
		if (sources == null || sources.isEmpty() || sources.size() > MAX_SOURCE_FILES) {
			throw new IllegalArgumentException("Behavior source bundle must contain 1 to " + MAX_SOURCE_FILES + " sources");
		}

		TreeMap<String, String> normalized = new TreeMap<>();
		int totalCharacters = 0;
		for (Map.Entry<String, String> source : sources.entrySet()) {
			String path = normalizePath(source.getKey());
			if (!path.startsWith("entry/") && !path.startsWith("helpers/")) {
				throw new IllegalArgumentException("Behavior source must live below entry/ or helpers/: " + path);
			}
			String text = source.getValue() == null ? "" : source.getValue().strip();
			if (text.isEmpty() || text.indexOf('\0') >= 0 || text.length() > MAX_SOURCE_CHARACTERS) {
				throw new IllegalArgumentException("Behavior Java source is invalid: " + path);
			}
			if (normalized.put(path, text) != null) {
				throw new IllegalArgumentException("Duplicate behavior source path: " + path);
			}
			totalCharacters += text.length();
			if (totalCharacters > MAX_TOTAL_CHARACTERS) {
				throw new IllegalArgumentException("Behavior source bundle exceeds 4 MiB of Java source");
			}
		}
		String entrySource = normalized.get(entrySourcePath);
		if (entrySource == null) {
			throw new IllegalArgumentException("Behavior bundle is missing its entry source: " + entrySourcePath);
		}
		if (entrySource.length() > MAX_ENTRY_CHARACTERS) {
			throw new IllegalArgumentException("Behavior entry source exceeds 65,536 characters");
		}
		String expectedFile = entryClass.substring(entryClass.lastIndexOf('.') + 1) + ".java";
		if (!entrySourcePath.substring(entrySourcePath.lastIndexOf('/') + 1).equals(expectedFile)) {
			throw new IllegalArgumentException("Behavior entry source filename must be " + expectedFile);
		}
		sources = Map.copyOf(normalized);
	}

	/** Wraps the historical single, default-package behavior source without changing its persisted Java text. */
	public static DynamicBehaviorSourceBundle legacy(String source) {
		return new DynamicBehaviorSourceBundle(
				LEGACY_ENTRY_CLASS,
				LEGACY_ENTRY_SOURCE_PATH,
				Map.of(LEGACY_ENTRY_SOURCE_PATH, source)
		);
	}

	public String entrySource() {
		return sources.get(entrySourcePath);
	}

	private static Prepared prepare(String entryClass, Map<String, String> rawSources) {
		if (entryClass == null || rawSources == null) {
			throw new IllegalArgumentException("Behavior source bundle is incomplete");
		}
		String simpleName = entryClass.strip();
		simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
		String entryFile = simpleName + ".java";
		TreeMap<String, String> logical = new TreeMap<>();
		String entryPath = null;
		for (Map.Entry<String, String> source : rawSources.entrySet()) {
			String rawPath = source.getKey() == null ? "" : source.getKey().strip().replace('\\', '/');
			String path = rawPath.startsWith("entry/") || rawPath.startsWith("helpers/")
					? rawPath
					: rawPath.substring(rawPath.lastIndexOf('/') + 1).equals(entryFile)
							? "entry/" + rawPath : "helpers/" + rawPath;
			if (path.startsWith("entry/") && path.substring(path.lastIndexOf('/') + 1).equals(entryFile)) {
				if (entryPath != null) throw new IllegalArgumentException("Behavior bundle has multiple entry source candidates");
				entryPath = path;
			}
			if (logical.put(path, source.getValue()) != null) {
				throw new IllegalArgumentException("Duplicate behavior source path: " + path);
			}
		}
		if (entryPath == null) throw new IllegalArgumentException("Behavior bundle has no source for entry class " + entryClass);
		return new Prepared(entryPath, logical);
	}

	private static String normalizePath(String raw) {
		String path = raw == null ? "" : raw.strip();
		if (path.isEmpty() || path.length() > MAX_PATH_LENGTH || path.indexOf('\0') >= 0
				|| path.startsWith("/") || path.endsWith("/") || path.contains("\\") || path.contains("//")
				|| !path.endsWith(".java")) {
			throw new IllegalArgumentException("Behavior source path is invalid: " + path);
		}
		for (String segment : path.split("/")) {
			if (segment.equals(".") || segment.equals("..") || !segment.matches(PATH_SEGMENT)) {
				throw new IllegalArgumentException("Behavior source path is invalid: " + path);
			}
		}
		return path;
	}

	private record Prepared(String entrySourcePath, Map<String, String> sources) {
	}
}
