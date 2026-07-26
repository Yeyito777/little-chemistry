package com.yeyito.littlechemistry.behavior;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeCompilerSupportTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void packageDiscoveryDoesNotRequestRuntimeTransformations() throws Exception {
		EclipseCompiler compiler = new EclipseCompiler();
		try (var standard = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
			DynamicBehaviorCompiler.configureClassPath(standard);
			AtomicInteger resolutions = new AtomicInteger();
			JavaFileObject exact = new SimpleJavaFileObject(
					URI.create("memory:///java/lang/String.class"), JavaFileObject.Kind.CLASS) {
			};
			RuntimeClassPathFileManager files = new RuntimeClassPathFileManager(standard, name -> {
				resolutions.incrementAndGet();
				return exact;
			});

			assertTrue(files.list(StandardLocation.CLASS_PATH, "com.yeyito.littlechemistry.behavior",
					Set.of(JavaFileObject.Kind.CLASS), false).iterator().hasNext());
			assertEquals(0, resolutions.get());
			assertSame(exact, files.getJavaFileForInput(StandardLocation.CLASS_PATH,
					"java.lang.String", JavaFileObject.Kind.CLASS));
			assertEquals(1, resolutions.get());
		}
	}

	@Test
	void ecjLoadsExactTransformedBytesAfterDiscoveringTheTypeOnDisk() throws Exception {
		Path diskClasses = compileFixture("disk", "public class Api {}");
		Path runtimeClasses = compileFixture("runtime", "public class Api { public void runtimeOnly() {} }");
		byte[] runtimeApi = Files.readAllBytes(runtimeClasses.resolve("fixture/Api.class"));
		JavaFileObject runtimeClass = classFile("fixture.Api", runtimeApi);
		Path consumerSource = temporaryDirectory.resolve("consumer-src/Consumer.java");
		Files.createDirectories(consumerSource.getParent());
		Files.writeString(consumerSource, """
				import fixture.Api;
				public class Consumer {
				    public void use() { new Api().runtimeOnly(); }
				}
				""");

		EclipseCompiler compiler = new EclipseCompiler();
		try (StandardJavaFileManager standard = compiler.getStandardFileManager(
				null, Locale.ROOT, StandardCharsets.UTF_8)) {
			standard.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(diskClasses));
			Path output = temporaryDirectory.resolve("consumer-classes");
			Files.createDirectories(output);
			standard.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
			AtomicInteger exactLookups = new AtomicInteger();
			RuntimeClassPathFileManager files = new RuntimeClassPathFileManager(standard, name -> {
				if (!name.equals("fixture.Api")) return null;
				exactLookups.incrementAndGet();
				return runtimeClass;
			});

			var consumerUnits = standard.getJavaFileObjectsFromPaths(List.of(consumerSource));
			boolean compiled = compiler.getTask(null, files, null,
					List.of("--release", "25", "-proc:none"), null, consumerUnits).call();
			assertTrue(compiled, "exact transformed lookups: " + exactLookups.get());
			assertTrue(exactLookups.get() > 0);
		}
	}

	@Test
	void transformedClassCacheSharesHitsAndNegativeLookupsWithinItsBound() throws Exception {
		RuntimeCompilerSupport.TransformedClassCache cache =
				new RuntimeCompilerSupport.TransformedClassCache(2);
		AtomicInteger loads = new AtomicInteger();
		byte[] expected = {1, 2, 3};

		assertArrayEquals(expected, cache.resolve("example.Present", name -> {
			loads.incrementAndGet();
			return expected;
		}));
		assertArrayEquals(expected, cache.resolve("example.Present", name -> {
			loads.incrementAndGet();
			return new byte[] {9};
		}));
		assertEquals(null, cache.resolve("example.Missing", name -> {
			loads.incrementAndGet();
			return null;
		}));
		assertEquals(null, cache.resolve("example.Missing", name -> {
			loads.incrementAndGet();
			return new byte[] {9};
		}));

		assertEquals(2, loads.get());
		assertEquals(2, cache.size());
	}

	private Path compileFixture(String directory, String body) throws Exception {
		Path root = temporaryDirectory.resolve(directory);
		Path source = root.resolve("src/fixture/Api.java");
		Path output = root.resolve("classes");
		Files.createDirectories(source.getParent());
		Files.createDirectories(output);
		Files.writeString(source, "package fixture; " + body);
		int exitCode = ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"--release", "25", "-proc:none", "-d", output.toString(), source.toString());
		assertEquals(0, exitCode);
		return output;
	}

	private static JavaFileObject classFile(String className, byte[] bytes) {
		return new SimpleJavaFileObject(
				URI.create("runtime:///" + className.replace('.', '/') + JavaFileObject.Kind.CLASS.extension),
				JavaFileObject.Kind.CLASS) {
			@Override public ByteArrayInputStream openInputStream() { return new ByteArrayInputStream(bytes); }
		};
	}
}
