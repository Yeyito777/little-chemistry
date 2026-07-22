package com.yeyito.littlechemistry.behavior;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkstationBehaviorCompilerTest {
	private static final String WORKSTATION_SOURCE = """
			import com.yeyito.littlechemistry.behavior.*;
			import net.minecraft.core.Direction;
			import net.minecraft.server.level.ServerPlayer;
			import net.minecraft.world.item.ItemStack;

			public final class GeneratedBehaviorImpl implements DynamicBehavior, WorkstationBehavior,
			        WorkstationTickBehavior, WorkstationSlotBehavior, WorkstationButtonBehavior,
			        WorkstationAutomationBehavior {
			    public GeneratedBehaviorImpl() {}

			    @Override public WorkstationRecipeRequest createWorkstationRecipe(DynamicWorkstationContext context) {
			        return WorkstationRecipeRequest.builder("pressing")
			                .consume("ore", 2)
			                .keep("die", 1)
			                .putAiContext("pressure", 400L)
			                .build();
			    }

			    @Override public void workstationTick(DynamicWorkstationContext context) {
			        if (context.recipeStatus() == WorkstationRecipeStatus.READY) {
			            long ticks = context.persistentState("ticks") + 1L;
			            context.setPersistentState("ticks", ticks);
			            context.setUiState("progress", (int) ticks);
			            ItemStack preview = context.recipeOutput();
			            com.google.gson.JsonObject data = context.recipeData();
			            if (!preview.isEmpty() && data.has("duration") && ticks >= data.get("duration").getAsLong()) {
			                context.tryCompleteRecipe();
			            }
			            context.setChanged();
			        }
			    }

			    @Override public boolean canUseWorkstationSlot(DynamicWorkstationContext context,
			            ServerPlayer player, String slotId, ItemStack stack, WorkstationSlotAction action) {
			        return !slotId.equals("output");
			    }

			    @Override public boolean workstationButtonPressed(DynamicWorkstationContext context,
			            ServerPlayer player, String buttonId) {
			        return buttonId.equals("purge");
			    }

			    @Override public boolean canAutomateWorkstationSlot(DynamicWorkstationContext context,
			            String slotId, ItemStack stack, WorkstationSlotAction action, Direction side) {
			        return side == Direction.UP;
			    }
			}
			""";

	@Test
	void compilerLoadsEveryWorkstationCapability() {
		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(WORKSTATION_SOURCE).instantiate();

		assertInstanceOf(WorkstationBehavior.class, behavior);
		assertInstanceOf(WorkstationTickBehavior.class, behavior);
		assertInstanceOf(WorkstationSlotBehavior.class, behavior);
		assertInstanceOf(WorkstationButtonBehavior.class, behavior);
		assertInstanceOf(WorkstationAutomationBehavior.class, behavior);
		assertEquals(Set.of(
				DynamicBehaviorCapability.WORKSTATION,
				DynamicBehaviorCapability.WORKSTATION_TICK,
				DynamicBehaviorCapability.WORKSTATION_SLOT,
				DynamicBehaviorCapability.WORKSTATION_BUTTON,
				DynamicBehaviorCapability.WORKSTATION_AUTOMATION
		), DynamicBehaviorSource.capabilities(WORKSTATION_SOURCE));
		assertEquals(DynamicBehaviorSource.capabilities(WORKSTATION_SOURCE),
				DynamicBehaviorCapability.discover(behavior));

		WorkstationRecipeRequest request = ((WorkstationBehavior) behavior).createWorkstationRecipe(null);
		assertEquals("pressing", request.processId());
		assertEquals(2, request.ingredients().size());
		assertEquals(400L, request.aiContext().get("pressure").getAsLong());
	}

	@Test
	void registryPublishesCapabilitiesFromTheInstalledSingleton() {
		DynamicBehaviorCompiler.Compiled compiled = DynamicBehaviorCompiler.compile(WORKSTATION_SOURCE);
		try {
			DynamicBehaviorRegistry.install("compiler_test_workstation", compiled, compiled.instantiate());

			assertEquals(DynamicBehaviorSource.capabilities(WORKSTATION_SOURCE),
					DynamicBehaviorRegistry.capabilities("compiler_test_workstation"));
		} finally {
			DynamicBehaviorRegistry.clear();
		}
	}

	@Test
	void sourceInspectionOnlyFindsInterfacesInTheClassHeader() {
		String passive = """
				// WorkstationBehavior and WorkstationSlotBehavior in a comment are not capabilities.
				public final class GeneratedBehaviorImpl
				        implements com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    private static final String DECOY = "WorkstationAutomationBehavior";
				}
				""";

		assertTrue(DynamicBehaviorSource.capabilities(passive).isEmpty());
		assertFalse(DynamicBehaviorSource.supports(passive, DynamicBehaviorCapability.WORKSTATION));
	}

	@Test
	void monolithicSourceMigrationAddsTheCoreWorkstationCapability() {
		String legacy = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    public WorkstationRecipeRequest createWorkstationRecipe(DynamicWorkstationContext context) {
				        return WorkstationRecipeRequest.builder().consume("input", 1).build();
				    }
				}
				""";

		String migrated = DynamicBehaviorSource.migrateMonolithicSource(legacy);
		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(migrated).instantiate();

		assertInstanceOf(WorkstationBehavior.class, behavior);
		assertEquals(Set.of(DynamicBehaviorCapability.WORKSTATION),
				DynamicBehaviorSource.capabilities(migrated));
	}

	@Test
	void compilerRejectsWorkstationCallbackWithoutItsInterface() {
		String ambiguous = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    public void workstationTick(DynamicWorkstationContext context) {}
				}
				""";

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(ambiguous));

		assertTrue(error.getMessage().contains("WorkstationTickBehavior"), error.getMessage());
	}

	@Test
	void compilerRequiresTheCoreCapabilityWithOptionalWorkstationHooks() {
		String missingCore = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, WorkstationTickBehavior {
				    public GeneratedBehaviorImpl() {}
				    public void workstationTick(DynamicWorkstationContext context) {}
				}
				""";

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(missingCore));

		assertTrue(error.getMessage().contains("WorkstationBehavior"), error.getMessage());
	}

	@Test
	void compilerRejectsMutableFieldsButAllowsSafeConstantsOnACompiledWorkstationClass() {
		String withInstanceField = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, WorkstationBehavior {
				    private int progress;
				    public GeneratedBehaviorImpl() {}
				    public WorkstationRecipeRequest createWorkstationRecipe(DynamicWorkstationContext context) {
				        return WorkstationRecipeRequest.builder().consume("input", 1).build();
				    }
				}
				""";
		String withStaticField = withInstanceField.replace("private int progress;",
				"private static final int DURATION_TICKS = 20;");

		IllegalArgumentException instanceError = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(withInstanceField));
		DynamicBehavior constantsOnly = DynamicBehaviorCompiler.compile(withStaticField).instantiate();

		assertTrue(instanceError.getMessage().contains("must not declare mutable field"), instanceError.getMessage());
		assertTrue(constantsOnly instanceof WorkstationBehavior);
	}
}
