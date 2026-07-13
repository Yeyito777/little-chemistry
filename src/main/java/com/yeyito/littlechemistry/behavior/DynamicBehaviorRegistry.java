package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DynamicBehaviorRegistry {
	private static final Map<String, Loaded> LOADED = new HashMap<>();

	private DynamicBehaviorRegistry() {
	}

	public static synchronized void replace(Collection<DynamicContentDefinition> definitions) {
		Map<String, Loaded> replacement = new HashMap<>();
		for (DynamicContentDefinition definition : definitions) {
			if (!definition.hasBehavior()) continue;
			try {
				replacement.put(definition.name(), load(definition.behaviorSource()));
			} catch (RuntimeException error) {
				LittleChemistry.LOGGER.error("Could not load generated behavior for {}", definition.name(), error);
			}
		}
		LOADED.clear();
		LOADED.putAll(replacement);
	}

	public static synchronized DynamicBehaviorCompiler.Compiled compile(String source) {
		return DynamicBehaviorCompiler.compile(source);
	}

	public static synchronized void install(String name, DynamicBehaviorCompiler.Compiled compiled,
			DynamicBehavior behavior) {
		LOADED.put(name, new Loaded(behavior, compiled.classLoader()));
	}

	public static synchronized void remove(Set<String> names) {
		names.forEach(LOADED::remove);
	}

	public static synchronized void clear() {
		LOADED.clear();
	}

	public static InteractionResult useAir(DynamicContentDefinition definition, ServerLevel level,
			ServerPlayer player, InteractionHand hand, ItemStack stack) {
		return interaction(definition, player, behavior -> behavior.useAir(
				new DynamicItemUseContext(level, player, hand, stack, definition)));
	}

	public static InteractionResult useOnBlock(DynamicContentDefinition definition, UseOnContext context,
			ServerLevel level, ServerPlayer player) {
		return interaction(definition, player, behavior -> behavior.useOnBlock(new DynamicBlockUseContext(
				level,
				player,
				context.getHand(),
				context.getItemInHand(),
				definition,
				context.getClickedPos(),
				context.getClickedFace(),
				context.getClickLocation()
		)));
	}

	public static InteractionResult interactLivingEntity(DynamicContentDefinition definition, ServerLevel level,
			ServerPlayer player, InteractionHand hand, ItemStack stack, LivingEntity target) {
		return interaction(definition, player, behavior -> behavior.interactLivingEntity(
				new DynamicEntityUseContext(level, player, hand, stack, target, definition)));
	}

	public static void inventoryTick(DynamicContentDefinition definition, ServerLevel level, Entity owner,
			@Nullable EquipmentSlot slot, ItemStack stack) {
		invoke(definition, owner instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.inventoryTick(level, owner, slot, stack, definition);
			return null;
		});
	}

	public static void postHurtEnemy(DynamicContentDefinition definition, ServerLevel level, LivingEntity attacker,
			LivingEntity target, ItemStack stack) {
		invoke(definition, attacker instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.postHurtEnemy(level, attacker, target, stack, definition);
			return null;
		});
	}

	public static void mineBlock(DynamicContentDefinition definition, ServerLevel level, LivingEntity miner,
			BlockPos position, BlockState state, ItemStack stack) {
		invoke(definition, miner instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.mineBlock(level, miner, position, state, stack, definition);
			return null;
		});
	}

	public static ItemStack finishUsing(DynamicContentDefinition definition, ServerLevel level,
			LivingEntity consumer, ItemStack originalStack, ItemStack resultStack) {
		ItemStack result = invoke(definition, consumer instanceof ServerPlayer player ? player : null,
				resultStack, behavior -> behavior.finishUsing(level, consumer, originalStack, resultStack, definition));
		return result == null ? resultStack : result;
	}

	public static void crafted(DynamicContentDefinition definition, ServerLevel level, ServerPlayer player,
			ItemStack stack) {
		invoke(definition, player, null, behavior -> {
			behavior.crafted(level, player, stack, definition);
			return null;
		});
	}

	public static InteractionResult usePlacedBlock(DynamicContentDefinition definition, ServerLevel level,
			ServerPlayer player, @Nullable InteractionHand hand, ItemStack heldStack, BlockPos position,
			BlockState state, BlockHitResult hit) {
		return interaction(definition, player, behavior -> behavior.usePlacedBlock(
				new DynamicPlacedBlockUseContext(level, player, hand, heldStack, position, state, hit, definition)));
	}

	public static void attackPlacedBlock(DynamicContentDefinition definition, ServerLevel level,
			ServerPlayer player, BlockPos position, BlockState state) {
		invoke(definition, player, null, behavior -> {
			behavior.attackPlacedBlock(level, player, position, state, definition);
			return null;
		});
	}

	public static void placedBlock(DynamicContentDefinition definition, ServerLevel level,
			@Nullable LivingEntity placer, BlockPos position, BlockState state, ItemStack placedFrom) {
		invoke(definition, placer instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.placedBlock(level, placer, position, state, placedFrom, definition);
			return null;
		});
	}

	public static void brokenBlock(DynamicContentDefinition definition, ServerLevel level, ServerPlayer player,
			BlockPos position, BlockState state, ItemStack tool) {
		invoke(definition, player, null, behavior -> {
			behavior.brokenBlock(level, player, position, state, tool, definition);
			return null;
		});
	}

	public static void stepOnBlock(DynamicContentDefinition definition, ServerLevel level, BlockPos position,
			BlockState state, Entity entity) {
		invoke(definition, entity instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.stepOnBlock(level, position, state, entity, definition);
			return null;
		});
	}

	public static void fallOnBlock(DynamicContentDefinition definition, ServerLevel level, BlockPos position,
			BlockState state, Entity entity, double fallDistance) {
		invoke(definition, entity instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.fallOnBlock(level, position, state, entity, fallDistance, definition);
			return null;
		});
	}

	public static void entityInsideBlock(DynamicContentDefinition definition, ServerLevel level, BlockPos position,
			BlockState state, Entity entity, InsideBlockEffectApplier effects, boolean isEntry) {
		invoke(definition, entity instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.entityInsideBlock(level, position, state, entity, effects, isEntry, definition);
			return null;
		});
	}

	public static void randomTickBlock(DynamicContentDefinition definition, ServerLevel level, BlockPos position,
			BlockState state, RandomSource random) {
		invoke(definition, null, null, behavior -> {
			behavior.randomTickBlock(level, position, state, random, definition);
			return null;
		});
	}

	public static void scheduledTickBlock(DynamicContentDefinition definition, ServerLevel level, BlockPos position,
			BlockState state, RandomSource random) {
		invoke(definition, null, null, behavior -> {
			behavior.scheduledTickBlock(level, position, state, random, definition);
			return null;
		});
	}

	public static void neighborChangedBlock(DynamicContentDefinition definition, ServerLevel level,
			BlockPos position, BlockState state, Block neighbor, @Nullable Orientation orientation,
			boolean movedByPiston) {
		invoke(definition, null, null, behavior -> {
			behavior.neighborChangedBlock(level, position, state, neighbor, orientation, movedByPiston, definition);
			return null;
		});
	}

	public static void projectileHitBlock(DynamicContentDefinition definition, ServerLevel level,
			BlockPos position, BlockState state, BlockHitResult hit, Projectile projectile) {
		invoke(definition, projectile.getOwner() instanceof ServerPlayer player ? player : null, null, behavior -> {
			behavior.projectileHitBlock(level, position, state, hit, projectile, definition);
			return null;
		});
	}

	private static InteractionResult interaction(DynamicContentDefinition definition, ServerPlayer player,
			BehaviorCall<InteractionResult> call) {
		InteractionResult result = invoke(definition, player, InteractionResult.FAIL, call);
		return result == null ? InteractionResult.PASS : result;
	}

	private static <T> T invoke(DynamicContentDefinition definition, @Nullable ServerPlayer player, T failureResult,
			BehaviorCall<T> call) {
		Loaded loaded = loaded(definition.name());
		if (loaded == null) return failureResult == InteractionResult.FAIL ? castPass() : failureResult;
		try {
			return call.invoke(loaded.behavior);
		} catch (Throwable error) {
			disableAfterFailure(definition, loaded, player, error);
			return failureResult;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T castPass() {
		return (T) InteractionResult.PASS;
	}

	private static synchronized Loaded loaded(String name) {
		return LOADED.get(name);
	}

	private static Loaded load(String source) {
		DynamicBehaviorCompiler.Compiled compiled = DynamicBehaviorCompiler.compile(source);
		return new Loaded(compiled.instantiate(), compiled.classLoader());
	}

	private static void disableAfterFailure(DynamicContentDefinition definition, Loaded failed,
			@Nullable ServerPlayer player, Throwable error) {
		synchronized (DynamicBehaviorRegistry.class) {
			if (LOADED.get(definition.name()) == failed) LOADED.remove(definition.name());
		}
		String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		LittleChemistry.LOGGER.error("Disabled generated behavior for {} after a runtime failure", definition.name(), error);
		if (player != null) {
			player.sendSystemMessage(Component.literal("[Little Chemistry] Disabled broken behavior for " +
					definition.displayName() + ": " + message).withStyle(ChatFormatting.RED));
		}
	}

	@FunctionalInterface
	private interface BehaviorCall<T> {
		T invoke(DynamicBehavior behavior);
	}

	private record Loaded(DynamicBehavior behavior, ClassLoader classLoader) {
	}
}
