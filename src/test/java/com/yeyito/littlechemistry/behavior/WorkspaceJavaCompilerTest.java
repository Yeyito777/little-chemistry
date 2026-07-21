package com.yeyito.littlechemistry.behavior;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class WorkspaceJavaCompilerTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void compilesAnEntryClassAndItsOrdinarySourceDependency() throws Exception {
		Path world = temporaryDirectory.resolve("world");
		Path job = temporaryDirectory.resolve("job");
		Files.createDirectories(world.resolve("helpers/shared"));
		Files.createDirectories(job.resolve("items/example"));
		Files.writeString(world.resolve("helpers/shared/Words.java"), """
				package helpers.shared;
				public final class Words { public static String value() { return "compiled"; } }
				""");
		Path source = job.resolve("items/example/Example.java");
		Files.writeString(source, """
				package items.example;
				public final class Example implements %s {
				    public Example() {}
				    public String value() { return helpers.shared.Words.value(); }
				}
				""".formatted(Factory.class.getCanonicalName()));

		WorkspaceJavaCompiler.Compiled<Factory> compiled = WorkspaceJavaCompiler.compile(
				job, world, source, "items.example.Example", Factory.class);

		assertEquals("compiled", compiled.instantiate().value());
		assertTrue(compiled.emittedClasses().contains("items.example.Example"));
		assertTrue(compiled.emittedClasses().contains("helpers.shared.Words"));
	}

	@Test
	void reportsCompilerDiagnosticsForBrokenWorldSource() throws Exception {
		Path job = temporaryDirectory.resolve("broken");
		Files.createDirectories(job.resolve("items/example"));
		Path source = job.resolve("items/example/Example.java");
		Files.writeString(source, "package items.example; public final class Example { nope }");

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
				WorkspaceJavaCompiler.compile(job, job, source, "items.example.Example", Factory.class));

		assertTrue(failure.getMessage().contains("compilation failed"));
	}

	public interface Factory {
		String value();
	}
}
