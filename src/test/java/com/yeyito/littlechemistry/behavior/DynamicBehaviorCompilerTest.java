package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicBehaviorCompilerTest {
	private static final String EXTENSIVE_SOURCE = """
			import com.yeyito.littlechemistry.behavior.*;
			import com.yeyito.littlechemistry.content.DynamicContentDefinition;
			import net.minecraft.core.BlockPos;
			import net.minecraft.server.level.ServerLevel;
			import net.minecraft.server.level.ServerPlayer;
			import net.minecraft.util.RandomSource;
			import net.minecraft.world.InteractionResult;
			import net.minecraft.world.entity.*;
			import net.minecraft.world.entity.projectile.Projectile;
			import net.minecraft.world.item.ItemStack;
			import net.minecraft.world.level.block.Block;
			import net.minecraft.world.level.block.state.BlockState;
			import net.minecraft.world.level.redstone.Orientation;
			import net.minecraft.world.phys.BlockHitResult;

			public final class GeneratedBehaviorImpl implements DynamicBehavior,
			        UseAirBehavior, UseOnBlockBehavior, InteractLivingEntityBehavior, InventoryTickBehavior,
			        PostHurtEnemyBehavior, MineBlockBehavior, FinishUsingBehavior, CraftedBehavior,
			        UsePlacedBlockBehavior, AttackPlacedBlockBehavior, PlacedBlockBehavior, BrokenBlockBehavior,
			        StepOnBlockBehavior, FallOnBlockBehavior, EntityInsideBlockBehavior, RandomTickBlockBehavior,
			        ScheduledTickBlockBehavior, NeighborChangedBlockBehavior, ProjectileHitBlockBehavior {
			    public GeneratedBehaviorImpl() {}
			    @Override public InteractionResult useAir(DynamicItemUseContext c) { return InteractionResult.SUCCESS; }
			    @Override public InteractionResult useOnBlock(DynamicBlockUseContext c) { return InteractionResult.PASS; }
			    @Override public InteractionResult interactLivingEntity(DynamicEntityUseContext c) { return InteractionResult.SUCCESS; }
			    @Override public void inventoryTick(ServerLevel l, Entity e, EquipmentSlot s, ItemStack i, DynamicContentDefinition d) {}
			    @Override public void postHurtEnemy(ServerLevel l, LivingEntity a, LivingEntity t, ItemStack i, DynamicContentDefinition d) {}
			    @Override public void mineBlock(ServerLevel l, LivingEntity m, BlockPos p, BlockState s, ItemStack i, DynamicContentDefinition d) {}
			    @Override public ItemStack finishUsing(ServerLevel l, LivingEntity e, ItemStack o, ItemStack r, DynamicContentDefinition d) { return r; }
			    @Override public void crafted(ServerLevel l, ServerPlayer p, ItemStack i, DynamicContentDefinition d) {}
			    @Override public InteractionResult usePlacedBlock(DynamicPlacedBlockUseContext c) { return InteractionResult.SUCCESS; }
			    @Override public void attackPlacedBlock(ServerLevel l, ServerPlayer p, BlockPos b, BlockState s, DynamicContentDefinition d) {}
			    @Override public void placedBlock(ServerLevel l, LivingEntity e, BlockPos p, BlockState s, ItemStack i, DynamicContentDefinition d) {}
			    @Override public void brokenBlock(ServerLevel l, ServerPlayer p, BlockPos b, BlockState s, ItemStack i, DynamicContentDefinition d) {}
			    @Override public void stepOnBlock(ServerLevel l, BlockPos p, BlockState s, Entity e, DynamicContentDefinition d) {}
			    @Override public void fallOnBlock(ServerLevel l, BlockPos p, BlockState s, Entity e, double f, DynamicContentDefinition d) {}
			    @Override public void entityInsideBlock(ServerLevel l, BlockPos p, BlockState s, Entity e, InsideBlockEffectApplier a, boolean n, DynamicContentDefinition d) {}
			    @Override public void randomTickBlock(ServerLevel l, BlockPos p, BlockState s, RandomSource r, DynamicContentDefinition d) {}
			    @Override public void scheduledTickBlock(ServerLevel l, BlockPos p, BlockState s, RandomSource r, DynamicContentDefinition d) {}
			    @Override public void neighborChangedBlock(ServerLevel l, BlockPos p, BlockState s, Block b, Orientation o, boolean m, DynamicContentDefinition d) {}
			    @Override public void projectileHitBlock(ServerLevel l, BlockPos p, BlockState s, BlockHitResult h, Projectile r, DynamicContentDefinition d) {}
			}
			""";

	@Test
	void compilesAndLoadsEveryPublishedHook() {
		DynamicBehaviorCompiler.Compiled compiled = DynamicBehaviorCompiler.compile(EXTENSIVE_SOURCE);
		DynamicBehavior behavior = compiled.instantiate();
		assertEquals(InteractionResult.SUCCESS, ((UseAirBehavior) behavior).useAir(null));
		assertEquals("GeneratedBehaviorImpl", behavior.getClass().getName());
	}

	@Test
	void identicalGeneratedNamesRemainIsolatedByClassLoader() {
		DynamicBehaviorCompiler.Compiled first = DynamicBehaviorCompiler.compile(EXTENSIVE_SOURCE);
		DynamicBehaviorCompiler.Compiled second = DynamicBehaviorCompiler.compile(EXTENSIVE_SOURCE);
		assertNotSame(first.classLoader(), second.classLoader());
		assertNotSame(first.instantiate().getClass(), second.instantiate().getClass());
	}

	@Test
	void compilerReturnsActionableDiagnostics() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile("public final class GeneratedBehaviorImpl {")
		);
		assertTrue(error.getMessage().contains("Java compilation failed"));
		assertTrue(error.getMessage().contains("Line"));
	}

	@Test
	void compilesMarkerOnlyClassForPassiveContent() {
		String passive = """
				public final class GeneratedBehaviorImpl implements com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				}
				""";

		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(passive).instantiate();

		assertTrue(DynamicBehaviorSource.capabilities(passive).isEmpty());
		assertTrue(behavior instanceof DynamicBehavior);
	}

	@Test
	void generatedBehaviorCanCompileAgainstTheCustomParticleApi() {
		String source = """
				import com.yeyito.littlechemistry.behavior.*;
				import com.yeyito.littlechemistry.particle.DynamicParticles;
				import net.minecraft.world.InteractionResult;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, UseAirBehavior {
				    public GeneratedBehaviorImpl() {}
				    public InteractionResult useAir(DynamicItemUseContext context) {
				        DynamicParticles.spawn(context.level(), context.definition(), "spark",
				                context.player().getX(), context.player().getY() + 1.0, context.player().getZ(),
				                8, 0.2, 0.2, 0.2, 0.05);
				        return InteractionResult.SUCCESS;
				    }
				}
				""";

		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(source).instantiate();

		assertTrue(behavior instanceof UseAirBehavior);
		assertEquals(java.util.List.of("spark"), DynamicBehaviorSource.referencedCustomParticleIds(source));
		assertThrows(IllegalArgumentException.class, () -> DynamicBehaviorSource.referencedCustomParticleIds(
				source.replace("\"spark\"", "context.definition().name()")));
	}

	@Test
	void rejectsCallbackMethodsWithoutTheirCapabilityInterface() {
		String ambiguous = """
				public final class GeneratedBehaviorImpl implements com.yeyito.littlechemistry.behavior.DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    public net.minecraft.world.InteractionResult useAir(
				            com.yeyito.littlechemistry.behavior.DynamicItemUseContext context) {
				        return net.minecraft.world.InteractionResult.SUCCESS;
				    }
				}
				""";

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(ambiguous));

		assertTrue(error.getMessage().contains("UseAirBehavior"), error.getMessage());
	}

	@Test
	void legacySourceMigrationAddsCapabilitiesAndPreservesExistingCode() {
		String legacy = """
				import com.yeyito.littlechemistry.behavior.*;
				import net.minecraft.world.InteractionResult;

				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    // Mentioning useOnBlock(...) in a comment must not create a capability.
				    private final Object decoy = new Object() { public void useOnBlock(Object ignored) {} };
				    @Override public InteractionResult useAir(DynamicItemUseContext context) {
				        return InteractionResult.SUCCESS;
				    }
				}
				""";

		String migrated = DynamicBehaviorSource.completeLegacySource(legacy);
		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(migrated).instantiate();

		assertEquals(InteractionResult.SUCCESS, ((UseAirBehavior) behavior).useAir(null));
		assertEquals(java.util.Set.of(DynamicBehaviorCapability.USE_AIR),
				DynamicBehaviorSource.capabilities(migrated));
	}

	@Test
	void monolithicMigrationRemovesForcedNeutralCallbacks() {
		String monolithic = """
				import com.yeyito.littlechemistry.behavior.*;
				import net.minecraft.world.InteractionResult;

				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}
				    @Override public InteractionResult useAir(DynamicItemUseContext context) {
				        return InteractionResult.SUCCESS;
				    }
				    @Override public InteractionResult useOnBlock(DynamicBlockUseContext context) {
				        return InteractionResult.PASS;
				    }
				    @Override public void crafted(net.minecraft.server.level.ServerLevel level,
				            net.minecraft.server.level.ServerPlayer player,
				            net.minecraft.world.item.ItemStack stack,
				            com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}
				}
				""";

		String migrated = DynamicBehaviorSource.migrateMonolithicSource(monolithic);
		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(migrated).instantiate();

		assertTrue(behavior instanceof UseAirBehavior);
		assertTrue(!(behavior instanceof UseOnBlockBehavior));
		assertTrue(!(behavior instanceof CraftedBehavior));
		assertTrue(!migrated.contains("useOnBlock("));
		assertTrue(!migrated.contains("void crafted("));
	}
}
