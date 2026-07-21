package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkspaceGenerationVerifierTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void compilesAndValidatesACompleteWorldAuthoredItemFactory() throws Exception {
		try (GenerationWorkspace workspace = itemWorkspace(64)) {
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Prismatic Dust", 8, null);

			WorkspaceGenerationVerifier.VerifiedGeneration verified =
					WorkspaceGenerationVerifier.verify(workspace, request);

			assertEquals(DynamicContentType.ITEM, verified.type());
			assertEquals("Prismatic Dust", verified.displayName());
			assertEquals(8, verified.outputCount());
			assertEquals(64, verified.content().item().maxStack());
			assertTrue(verified.content().behaviorSource().contains("GeneratedBehaviorImpl"));
			workspace.stage(verified);
			assertSame(verified.compiledBehavior(), GenerationWorkspace.preparedBehavior(verified.content()));
			assertFalse(Files.exists(workspace.worldRoot()
					.resolve("items/prismatic_dust/C_prismatic_dust_Content.java")));
			GenerationWorkspace.discardPending(verified.content());
			assertNull(GenerationWorkspace.preparedBehavior(verified.content()));
		}
	}

	@Test
	void flexibleRecipeResultIsBoundByWorkstationSchemaAndOutputCapacity() throws Exception {
		try (GenerationWorkspace workspace = itemWorkspace(64)) {
			Files.createDirectories(workspace.root().resolve(".littlechemistry"));
			Files.writeString(workspace.root().resolve(".littlechemistry/result.json"), """
					{"kind":"item","displayName":"Prismatic Dust","outputCount":9,
					 "recipeData":{"duration_ticks":200}}
					""");
			var context = JsonParser.parseString("""
					{"workstation":{"primaryOutput":{"id":"result","capacity":8}}}
					""").getAsJsonObject();
			var schema = JsonParser.parseString("""
					{"type":"object","properties":{"duration_ticks":{"type":"integer","minimum":1}},
					 "required":["duration_ticks"],"additionalProperties":false}
					""").getAsJsonObject();
			GenerationRequest request = GenerationRequest.recipe(context, "Run for 200 ticks.", schema);

			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
					() -> WorkspaceGenerationVerifier.verify(workspace, request));

			assertTrue(failure.getMessage().contains("output capacity 8"));
		}
	}

	@Test
	void concurrentJobsCannotStageTheSameRuntimeIdentifier() throws Exception {
		Path world = temporaryDirectory.resolve("shared-world");
		try (GenerationWorkspace first = itemWorkspace(world, temporaryDirectory.resolve("first-job"), 64);
				GenerationWorkspace second = itemWorkspace(world, temporaryDirectory.resolve("second-job"), 64)) {
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Prismatic Dust", 1, null);
			var firstVerified = WorkspaceGenerationVerifier.verify(first, request);
			var secondVerified = WorkspaceGenerationVerifier.verify(second, request);

			first.stage(firstVerified);
			IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
					() -> second.stage(secondVerified));

			assertTrue(conflict.getMessage().contains("reserved"));
			GenerationWorkspace.discardPending(firstVerified.content());
		}
	}

	@Test
	void pendingSourceIsBoundToTheExactCommittedDefinition() throws Exception {
		Path world = temporaryDirectory.resolve("bound-world");
		try (GenerationWorkspace workspace = itemWorkspace(
				world, temporaryDirectory.resolve("bound-job"), 64)) {
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Prismatic Dust", 1, null);
			var verified = WorkspaceGenerationVerifier.verify(workspace, request);
			workspace.stage(verified);
			DynamicContentDefinition definition = definition(verified, verified.content().description());

			GenerationWorkspace.bindPending(verified.content(), definition);
			GenerationWorkspace.commitPending(verified.content(), definition);

			assertTrue(Files.isRegularFile(world.resolve(
					"items/prismatic_dust/C_prismatic_dust_Content.java")));
		}
	}

	@Test
	void recoveryRejectsPendingSourceFromAnOlderDefinitionUsingTheSameId() throws Exception {
		Path world = temporaryDirectory.resolve("replacement-world");
		try (GenerationWorkspace workspace = itemWorkspace(
				world, temporaryDirectory.resolve("replacement-job"), 64)) {
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Prismatic Dust", 1, null);
			var verified = WorkspaceGenerationVerifier.verify(workspace, request);
			workspace.stage(verified);
			GenerationWorkspace.bindPending(verified.content(),
					definition(verified, verified.content().description()));

			DynamicContentDefinition replacement = definition(verified, "A replacement definition.");
			GenerationWorkspace.initialize(world, java.util.List.of(replacement));

			assertFalse(Files.exists(world.resolve(
					"items/prismatic_dust/C_prismatic_dust_Content.java")));
			GenerationWorkspace.discardPending(verified.content());
		}
	}

	@Test
	void finalBuildRejectsARecipeCountThatDoesNotFitTheFactoryItem() throws Exception {
		try (GenerationWorkspace workspace = itemWorkspace(4)) {
			GenerationRequest request = GenerationRequest.fixed(
					DynamicContentType.ITEM, null, "Prismatic Dust", 8, null);

			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
					() -> WorkspaceGenerationVerifier.verify(workspace, request));

			assertTrue(failure.getMessage().contains("maxStack 4"));
		}
	}

	private GenerationWorkspace itemWorkspace(int maxStack) throws Exception {
		return itemWorkspace(temporaryDirectory.resolve("world-" + maxStack),
				temporaryDirectory.resolve("job-" + maxStack), maxStack);
	}

	private GenerationWorkspace itemWorkspace(Path world, Path job, int maxStack) throws Exception {
		GenerationWorkspace workspace = GenerationWorkspace.testing(world, job);
		Path item = job.resolve("items/prismatic_dust");
		Files.createDirectories(item);
		Files.writeString(item.resolve("GeneratedBehaviorImpl.java"), """
				public final class GeneratedBehaviorImpl implements
				        com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				}
				""");
		Files.writeString(item.resolve("C_prismatic_dust_Content.java"), factory(maxStack));
		return workspace;
	}

	private static String factory(int maxStack) {
		String rows = java.util.stream.IntStream.range(0, 16)
				.mapToObj(y -> y < 2 ? "\"0111111111111120\"" : "\"0000000000000000\"")
				.collect(java.util.stream.Collectors.joining(",\n                        "));
		return """
				package items.c_prismatic_dust;

				import com.yeyito.littlechemistry.ai.generation.GeneratedContentApi;
				import com.yeyito.littlechemistry.ai.generation.GeneratedContentBuilder;
				import com.yeyito.littlechemistry.ai.generation.GeneratedContentFactory;
				import com.yeyito.littlechemistry.content.DynamicItemProperties;
				import com.yeyito.littlechemistry.content.DynamicRarity;
				import com.yeyito.littlechemistry.content.GeneratedContentSpec;
				import net.minecraft.world.item.Rarity;
				import java.util.List;

				public final class C_prismatic_dust_Content implements GeneratedContentFactory {
				    public C_prismatic_dust_Content() {}
				    public GeneratedContentSpec create(String behaviorSource) {
				        var icon = GeneratedContentApi.texture(
				                List.of("00000000", "202040FF", "E0F0FFFF"),
				                %s);
				        return GeneratedContentBuilder.create()
				                .texture(icon)
				                .item(DynamicItemProperties.ordinary(%d, Rarity.COMMON, false, 0, 0.0))
				                .rarity(DynamicRarity.COMMON)
				                .description("A many-hued reactive powder.")
				                .build(behaviorSource);
				    }
				}
				""".formatted(rows, maxStack);
	}

	private static DynamicContentDefinition definition(
			WorkspaceGenerationVerifier.VerifiedGeneration verified, String description) throws Exception {
		var content = verified.content();
		return new DynamicContentDefinition(
				verified.type(),
				"prismatic_dust",
				verified.displayName(),
				description,
				content.rarityTier(),
				123L,
				DynamicTextureAsset.sha256(content.texture().renderPng()),
				content.texture(),
				null,
				null,
				content.block(),
				content.item(),
				content.armor(),
				content.behaviorSource(),
				content.blockModel(),
				content.customParticles(),
				content.workstation(),
				content.entity(),
				content.entityModel());
	}
}
