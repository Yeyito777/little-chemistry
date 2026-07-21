package com.yeyito.littlechemistry.ai.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaCodeInspectorTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void writesARealSearchableRuntimeClassIndex() throws Exception {
		Path index = temporaryDirectory.resolve("INDEX.txt");

		JavaCodeInspector.writeIndex(index);

		String content = Files.readString(index);
		assertTrue(content.contains("com.yeyito.littlechemistry.behavior.DynamicBehavior"));
		assertTrue(content.contains("net.minecraft.world.item.Item"));
	}

	@Test
	void decompilesCompleteLoadedMethodBodies() throws Exception {
		String source = JavaCodeInspector.decompile(JavaSourceInspectionFixture.class.getName());

		assertTrue(source.contains("return value * 2"));
	}

	@Test
	void decompilesVanillaMinecraftClassFamilies() throws Exception {
		String source = JavaCodeInspector.decompile("net.minecraft.world.item.Item$Properties");

		assertTrue(source.contains("package net.minecraft.world.item;"));
		assertTrue(source.contains("class Properties"));
		assertTrue(source.contains("InteractionResult use("));
	}

	@Test
	void rejectsUnknownVirtualClassPaths() {
		assertThrows(IOException.class, () -> JavaCodeInspector.decompile("not.a.real.MinecraftClass"));
	}
}

final class JavaSourceInspectionFixture {
	int twice(int value) {
		return value * 2;
	}
}
