package com.yeyito.littlechemistry.behavior;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

/**
 * Supplies exact Fabric-transformed classes without transforming ECJ's package-discovery listings.
 *
 * <p>ECJ uses {@link #list} primarily for package existence and type-name discovery. Transforming every listed class
 * made a tiny generated module re-read and rewrite broad Minecraft and mod packages. Actual type bytes are requested
 * through {@link #getJavaFileForInput}, so the precise runtime ABI remains available on demand.</p>
 */
class RuntimeClassPathFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
	@FunctionalInterface
	interface Resolver {
		JavaFileObject resolve(String className);
	}

	private final Resolver resolver;

	RuntimeClassPathFileManager(StandardJavaFileManager files) {
		this(files, RuntimeCompilerSupport::runtimeClass);
	}

	RuntimeClassPathFileManager(StandardJavaFileManager files, Resolver resolver) {
		super(files);
		this.resolver = resolver;
	}

	@Override
	public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
			throws IOException {
		if (location == StandardLocation.CLASS_PATH && kind == JavaFileObject.Kind.CLASS) {
			JavaFileObject runtimeClass = resolver.resolve(className.replace('/', '.').replace('\\', '.'));
			if (runtimeClass != null) return runtimeClass;
		}
		return super.getJavaFileForInput(location, className, kind);
	}

	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
			boolean recurse) throws IOException {
		Iterable<JavaFileObject> listed = super.list(location, packageName, kinds, recurse);
		if (location != StandardLocation.CLASS_PATH || !kinds.contains(JavaFileObject.Kind.CLASS)) return listed;
		// Preserve lazy iteration: ECJ often calls only hasNext() to establish that a package exists.
		return () -> new Iterator<>() {
			private final Iterator<JavaFileObject> delegate = listed.iterator();

			@Override public boolean hasNext() { return delegate.hasNext(); }

			@Override
			public JavaFileObject next() {
				JavaFileObject file = delegate.next();
				if (file.getKind() != JavaFileObject.Kind.CLASS) return file;
				String binaryName = RuntimeClassPathFileManager.super.inferBinaryName(location, file);
				return new LazyRuntimeClassFile(file, binaryName);
			}
		};
	}

	@Override
	public String inferBinaryName(Location location, JavaFileObject file) {
		if (file instanceof RuntimeCompilerSupport.RuntimeClassFile runtimeClass) {
			return runtimeClass.binaryName();
		}
		if (file instanceof LazyRuntimeClassFile runtimeClass) return runtimeClass.binaryName;
		return super.inferBinaryName(location, file);
	}

	private final class LazyRuntimeClassFile extends SimpleJavaFileObject {
		private final JavaFileObject delegate;
		private final String binaryName;

		private LazyRuntimeClassFile(JavaFileObject file, String binaryName) {
			super(java.net.URI.create("runtime-lazy:///" + binaryName.replace('.', '/') + Kind.CLASS.extension),
					Kind.CLASS);
			this.delegate = file;
			this.binaryName = binaryName;
		}

		@Override
		public InputStream openInputStream() throws IOException {
			JavaFileObject runtimeClass = resolver.resolve(binaryName);
			return runtimeClass == null ? delegate.openInputStream() : runtimeClass.openInputStream();
		}

		@Override public String getName() { return binaryName.replace('.', '/') + Kind.CLASS.extension; }
		@Override public long getLastModified() { return delegate.getLastModified(); }
	}
}
