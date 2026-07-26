package com.yeyito.littlechemistry.behavior;

import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** Shared, bounded support for the two runtime ECJ compilation paths. */
final class RuntimeCompilerSupport {
	private static final int MAX_TRANSFORMED_CLASSES = 8_192;
	private static final int MAX_CONCURRENT_COMPILATIONS = 2;
	private static final TransformedClassCache TRANSFORMED_CLASSES =
			new TransformedClassCache(MAX_TRANSFORMED_CLASSES);
	private static final Semaphore COMPILATION_PERMITS =
			new Semaphore(MAX_CONCURRENT_COMPILATIONS, true);

	private RuntimeCompilerSupport() {
	}

	static RuntimeClassFile runtimeClass(String className) {
		FabricLauncher launcher = fabricLauncher();
		if (launcher == null) return null;
		try {
			byte[] bytes = TRANSFORMED_CLASSES.resolve(className,
					name -> launcher.getClassByteArray(name, true));
			return bytes == null ? null : new RuntimeClassFile(className, bytes);
		} catch (IOException | RuntimeException | LinkageError ignored) {
			// Fall back to the configured disk class path when runtime transformation is unavailable.
			return null;
		}
	}

	static CompilationPermit acquireCompilationPermit() {
		try {
			COMPILATION_PERMITS.acquire();
			return new CompilationPermit();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalArgumentException("Runtime Java compilation was interrupted", interrupted);
		}
	}

	static FabricLauncher fabricLauncher() {
		try {
			return FabricLauncherBase.getLauncher();
		} catch (RuntimeException | LinkageError ignored) {
			return null;
		}
	}

	static final class RuntimeClassFile extends SimpleJavaFileObject {
		private final String binaryName;
		private final byte[] bytes;

		private RuntimeClassFile(String binaryName, byte[] bytes) {
			super(URI.create("runtime:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
			this.binaryName = binaryName;
			this.bytes = bytes;
		}

		String binaryName() {
			return binaryName;
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

	static final class CompilationPermit implements AutoCloseable {
		private boolean released;

		private CompilationPermit() {
		}

		@Override
		public void close() {
			if (released) return;
			released = true;
			COMPILATION_PERMITS.release();
		}
	}

	@FunctionalInterface
	interface ClassBytesLoader {
		byte[] load(String className) throws IOException;
	}

	/** Caches stable pre-Mixin Fabric transformations, including negative lookups. */
	static final class TransformedClassCache {
		private final int maximumEntries;
		private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

		TransformedClassCache(int maximumEntries) {
			if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
			this.maximumEntries = maximumEntries;
		}

		byte[] resolve(String className, ClassBytesLoader loader) throws IOException {
			Objects.requireNonNull(className, "className");
			Objects.requireNonNull(loader, "loader");
			Entry cached = entries.get(className);
			if (cached != null) return cached.bytes;
			if (entries.size() >= maximumEntries) return loader.load(className);
			try {
				Entry resolved = entries.computeIfAbsent(className, name -> {
					try {
						return new Entry(loader.load(name));
					} catch (IOException error) {
						throw new UncheckedIOException(error);
					}
				});
				return resolved.bytes;
			} catch (UncheckedIOException error) {
				throw error.getCause();
			}
		}

		int size() {
			return entries.size();
		}

		private record Entry(byte[] bytes) {
		}
	}
}
