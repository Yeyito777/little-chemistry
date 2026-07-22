package com.yeyito.littlechemistry.behavior;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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
			import net.minecraft.world.InteractionHand;
			import net.minecraft.world.damagesource.DamageSource;
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
			        ScheduledTickBlockBehavior, NeighborChangedBlockBehavior, ProjectileHitBlockBehavior,
			        EntitySpawnedBehavior, EntityTickBehavior, EntityInteractBehavior, EntityHurtBehavior,
			        EntityAttackBehavior, EntityDeathBehavior {
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
			    @Override public void entitySpawned(DynamicGeneratedEntityContext c) {}
			    @Override public void entityTick(DynamicGeneratedEntityContext c) {}
			    @Override public InteractionResult entityInteract(DynamicGeneratedEntityContext c, ServerPlayer p, InteractionHand h, ItemStack i) { return InteractionResult.SUCCESS; }
			    @Override public void entityHurt(DynamicGeneratedEntityContext c, DamageSource s, float a) {}
			    @Override public void entityAttack(DynamicGeneratedEntityContext c, Entity t) {}
			    @Override public void entityDeath(DynamicGeneratedEntityContext c, DamageSource s) {}
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
	void compilesPackagedEntryAndHelperSourcesAsOneRuntimeBundle() {
		String entry = """
				package generated.behavior;
				import com.yeyito.littlechemistry.behavior.*;
				import net.minecraft.world.InteractionResult;
				public final class PackagedBehavior implements DynamicBehavior, UseAirBehavior {
				    public PackagedBehavior() {}
				    public InteractionResult useAir(DynamicItemUseContext context) {
				        return BehaviorHelper.result();
				    }
				}
				""";
		DynamicBehaviorSourceBundle bundle = new DynamicBehaviorSourceBundle(
				"generated.behavior.PackagedBehavior",
				"entry/PackagedBehavior.java",
				java.util.Map.of(
						"entry/PackagedBehavior.java", entry,
						"helpers/BehaviorHelper.java", """
								package generated.behavior;
								import net.minecraft.world.InteractionResult;
								public final class BehaviorHelper {
								    private BehaviorHelper() {}
								    public static InteractionResult result() { return InteractionResult.SUCCESS; }
								}
								"""));

		DynamicBehaviorCompiler.Compiled compiled = DynamicBehaviorCompiler.compile(bundle);
		DynamicBehavior behavior = compiled.instantiate();

		assertEquals(InteractionResult.SUCCESS, ((UseAirBehavior) behavior).useAir(null));
		assertEquals("generated.behavior.PackagedBehavior", behavior.getClass().getName());
		assertEquals(bundle, compiled.sourceBundle());
		assertEquals(java.util.Set.of(DynamicBehaviorCapability.USE_AIR),
				DynamicBehaviorSource.capabilities(bundle));
	}

	@Test
	void compilesInheritedInterfaceDefaultSuperCallsInAnonymousClasses() throws Exception {
		String source = """
				import com.yeyito.littlechemistry.behavior.DynamicBehavior;
				import net.minecraft.world.SimpleContainer;
				import net.minecraft.world.item.ItemStack;

				public final class GeneratedBehaviorImpl implements DynamicBehavior {
				    public GeneratedBehaviorImpl() {}

				    public boolean canPlace(ItemStack stack) {
				        SimpleContainer container = new SimpleContainer(1) {
				            @Override public boolean canPlaceItem(int slot, ItemStack candidate) {
				                return super.canPlaceItem(slot, candidate);
				            }
				        };
				        return container.canPlaceItem(0, stack);
				    }
				}
				""";

		DynamicBehavior behavior = DynamicBehaviorCompiler.compile(source).instantiate();
		boolean accepted = (boolean) behavior.getClass()
				.getMethod("canPlace", ItemStack.class)
				.invoke(behavior, ItemStack.EMPTY);

		assertTrue(accepted);
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
		assertThrows(IllegalArgumentException.class, () -> DynamicBehaviorSource.referencedCustomParticleIds(
				source.replace(
						"import com.yeyito.littlechemistry.particle.DynamicParticles;",
						"import com.yeyito.littlechemistry.particle.DynamicParticleOptions;")));
	}

	@Test
	void entityCallbacksMustKeepPerInstanceDataInDynamicEntityState() {
		String source = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, EntityTickBehavior {
					private int sharedTicks;
					public GeneratedBehaviorImpl() {}
					public void entityTick(DynamicGeneratedEntityContext context) { sharedTicks++; }
				}
				""";

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(source));

		assertTrue(error.getMessage().contains("DynamicEntityState"), error.getMessage());
	}

	@Test
	void entityPreHooksMustAlsoKeepPerInstanceDataInDynamicEntityState() {
		String preHurt = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, EntityPreHurtBehavior {
					private float sharedDamage;
					public GeneratedBehaviorImpl() {}
					public float entityPreHurt(DynamicGeneratedEntityContext context,
							net.minecraft.world.damagesource.DamageSource source, float amount) {
						return sharedDamage = amount;
					}
				}
				""";
		String preAttack = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior, EntityPreAttackBehavior {
					private boolean sharedDecision;
					public GeneratedBehaviorImpl() {}
					public boolean entityPreAttack(DynamicGeneratedEntityContext context,
							net.minecraft.world.entity.Entity target) {
						return sharedDecision = true;
					}
				}
				""";

		IllegalArgumentException hurtError = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(preHurt));
		IllegalArgumentException attackError = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(preAttack));

		assertTrue(hurtError.getMessage().contains("DynamicEntityState"), hurtError.getMessage());
		assertTrue(attackError.getMessage().contains("DynamicEntityState"), attackError.getMessage());
	}

	@Test
	void entityBehaviorRejectsMutableFieldsInheritedFromRuntimeHelpers() {
		String entry = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl extends StatefulBase
						implements DynamicBehavior, EntityTickBehavior {
					public GeneratedBehaviorImpl() {}
					public void entityTick(DynamicGeneratedEntityContext context) { sharedTicks++; }
				}
				""";
		DynamicBehaviorSourceBundle bundle = new DynamicBehaviorSourceBundle(
				"GeneratedBehaviorImpl", "entry/GeneratedBehaviorImpl.java", java.util.Map.of(
						"entry/GeneratedBehaviorImpl.java", entry,
						"helpers/StatefulBase.java", "class StatefulBase { protected int sharedTicks; }"));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(bundle));

		assertTrue(error.getMessage().contains("StatefulBase.sharedTicks"), error.getMessage());
		assertTrue(error.getMessage().contains("DynamicEntityState"), error.getMessage());
	}

	@Test
	void workstationBehaviorRejectsMutableStaticHelperStateButAllowsConstants() {
		String entry = """
				import com.yeyito.littlechemistry.behavior.*;
				public final class GeneratedBehaviorImpl implements DynamicBehavior,
						WorkstationBehavior, WorkstationTickBehavior {
					public GeneratedBehaviorImpl() {}
					public WorkstationRecipeRequest createWorkstationRecipe(DynamicWorkstationContext context) {
						return null;
					}
					public void workstationTick(DynamicWorkstationContext context) { StatefulHelper.tick(); }
				}
				""";
		DynamicBehaviorSourceBundle mutable = new DynamicBehaviorSourceBundle(
				"GeneratedBehaviorImpl", "entry/GeneratedBehaviorImpl.java", java.util.Map.of(
						"entry/GeneratedBehaviorImpl.java", entry,
						"helpers/StatefulHelper.java", """
								final class StatefulHelper {
								    static final String ID = "constant";
								    static int ticks;
								    static void tick() { ticks++; }
								}
								"""));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DynamicBehaviorCompiler.compile(mutable));

		assertTrue(error.getMessage().contains("StatefulHelper.ticks"), error.getMessage());
		assertTrue(error.getMessage().contains("DynamicWorkstationContext state"), error.getMessage());

		DynamicBehaviorSourceBundle constantsOnly = new DynamicBehaviorSourceBundle(
				"GeneratedBehaviorImpl", "entry/GeneratedBehaviorImpl.java", java.util.Map.of(
						"entry/GeneratedBehaviorImpl.java", entry.replace("StatefulHelper.tick();",
								"String ignored = StatefulHelper.ID;"),
						"helpers/StatefulHelper.java", """
								final class StatefulHelper {
								    static final String ID = "constant";
								}
								"""));
		assertTrue(DynamicBehaviorCompiler.compile(constantsOnly).instantiate() instanceof WorkstationBehavior);
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
				    @Override public InteractionResult beginUsing(DynamicItemUseContext context) {
				        return InteractionResult.PASS;
				    }
				    @Override public void usingTick(DynamicItemUsingContext context) {}
				    @Override public boolean releaseUsing(DynamicItemUsingContext context) {
				        return false;
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
		assertTrue(!(behavior instanceof BeginUsingBehavior));
		assertTrue(!(behavior instanceof UsingTickBehavior));
		assertTrue(!(behavior instanceof ReleaseUsingBehavior));
		assertTrue(!(behavior instanceof CraftedBehavior));
		assertTrue(!migrated.contains("useOnBlock("));
		assertTrue(!migrated.contains("beginUsing("));
		assertTrue(!migrated.contains("usingTick("));
		assertTrue(!migrated.contains("releaseUsing("));
		assertTrue(!migrated.contains("void crafted("));
	}
}
