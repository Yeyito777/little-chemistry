package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.behavior.DynamicItemUsingContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared optional behavior extensions for all dynamic held-item archetypes. */
public final class DynamicCarrierHooks {
	private DynamicCarrierHooks() {
	}

	public static boolean isDynamicHeldStack(ItemStack stack) {
		return stack.getItem() instanceof DynamicItemCarrier;
	}

	public static void inventoryTick(ItemStack stack, ServerLevel level, Entity owner,
			@Nullable EquipmentSlot slot) {
		DynamicContentObjects.refreshDynamicAttributes(stack);
		DynamicContentDefinition definition = definition(stack);
		if (definition != null) {
			DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
		}
	}

	/** Returns a non-null result only when a generated extension handled or rejected the action. */
	public static @Nullable InteractionResult beforeUseOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !supports(definition, DynamicBehaviorCapability.USE_ON_BLOCK)) return null;
		if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
		if (context.getLevel() instanceof ServerLevel level
				&& context.getPlayer() instanceof ServerPlayer player) {
			InteractionResult result = DynamicBehaviorRegistry.useOnBlock(definition, context, level, player);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	/**
	 * Runs opt-in air/begin callbacks before vanilla. Returning {@code null} means vanilla remains
	 * authoritative; in particular, an archetype never needs generated code merely to function.
	 */
	public static @Nullable InteractionResult beforeUse(ItemStack stack, Level level, Player player,
			InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null) return null;
		// Let the native client path predict drawing, charging, casting, or blocking. The server-side
		// extension can still handle an action and authoritatively replace the result.
		if (level.isClientSide()) return null;
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		if (supports(definition, DynamicBehaviorCapability.USE_AIR)) {
			InteractionResult result = DynamicBehaviorRegistry.useAir(
					definition, serverLevel, serverPlayer, hand, stack);
			if (result != InteractionResult.PASS) return result;
		}
		if (supports(definition, DynamicBehaviorCapability.BEGIN_USING)) {
			InteractionResult result = DynamicBehaviorRegistry.beginUsing(
					definition, serverLevel, serverPlayer, hand, stack);
			if (result == InteractionResult.FAIL) return result;
		}
		return null;
	}

	public static void onUseTick(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !(level instanceof ServerLevel serverLevel)
				|| !(user instanceof ServerPlayer player)
				|| !supports(definition, DynamicBehaviorCapability.USING_TICK)) return;
		DynamicBehaviorRegistry.usingTick(definition,
				usingContext(definition, serverLevel, player, stack, remainingUseTicks));
	}

	/** Vanilla always runs; a generated release callback may add success, but cannot suppress it. */
	public static boolean releaseUsing(ItemStack stack, Level level, LivingEntity user,
			int remainingUseTicks, BooleanSupplier vanillaRelease) {
		DynamicContentDefinition definition = definition(stack);
		boolean extensionAccepted = false;
		if (definition != null && level instanceof ServerLevel serverLevel
				&& user instanceof ServerPlayer player
				&& supports(definition, DynamicBehaviorCapability.RELEASE_USING)) {
			extensionAccepted = DynamicBehaviorRegistry.releaseUsing(definition,
					usingContext(definition, serverLevel, player, stack, remainingUseTicks));
		}
		return vanillaRelease.getAsBoolean() || extensionAccepted;
	}

	public static @Nullable InteractionResult beforeInteractLivingEntity(ItemStack stack, Player player,
			LivingEntity target, InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !supports(definition, DynamicBehaviorCapability.INTERACT_LIVING_ENTITY)) {
			return null;
		}
		if (player.level().isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
			InteractionResult result = DynamicBehaviorRegistry.interactLivingEntity(
					definition, serverLevel, serverPlayer, hand, stack, target);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	public static void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && attacker.level() instanceof ServerLevel level) {
			DynamicBehaviorRegistry.postHurtEnemy(definition, level, attacker, target, stack);
		}
	}

	public static void mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position,
			LivingEntity miner) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.mineBlock(definition, serverLevel, miner, position, state, stack);
		}
	}

	public static ItemStack finishUsing(ItemStack original, ItemStack vanillaResult, Level level,
			LivingEntity consumer) {
		DynamicContentDefinition definition = definition(original);
		return definition != null && level instanceof ServerLevel serverLevel
				? DynamicBehaviorRegistry.finishUsing(
						definition, serverLevel, consumer, original.copy(), vanillaResult)
				: vanillaResult;
	}

	public static void crafted(ItemStack stack, Player player) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && player instanceof ServerPlayer serverPlayer
				&& player.level() instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.crafted(definition, serverLevel, serverPlayer, stack);
		}
	}

	public static @Nullable Component name(ItemStack stack) {
		DynamicContentDefinition definition = definition(stack);
		return definition == null ? null
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	public static void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null) return;
		if (!definition.description().isBlank()) {
			DynamicTooltipText.appendWrapped(builder, definition.description(), ChatFormatting.GRAY);
		}
		if (definition.item() == null) return;
		switch (definition.item().craftingUse()) {
			case KEEP -> builder.accept(Component.literal("Not consumed when used in crafting")
					.withStyle(ChatFormatting.DARK_GRAY));
			case DAMAGE -> builder.accept(Component.literal("Costs 1 durability when used in crafting")
					.withStyle(ChatFormatting.DARK_GRAY));
			case CONSUME -> {
			}
		}
		if (definition.item().fuelBurnTicks() > 0) {
			builder.accept(Component.translatable("tooltip.little_chemistry.furnace_fuel",
					formatDecimal(definition.item().fuelBurnTicks() / 20.0),
					formatDecimal(definition.item().fuelBurnTicks() / 200.0))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static @Nullable DynamicContentDefinition definition(ItemStack stack) {
		return isDynamicHeldStack(stack) ? DynamicContentObjects.definition(stack) : null;
	}

	private static boolean supports(DynamicContentDefinition definition, DynamicBehaviorCapability capability) {
		return DynamicBehaviorSource.supports(definition.behaviorSourceBundle(), capability);
	}

	private static DynamicItemUsingContext usingContext(DynamicContentDefinition definition,
			ServerLevel level, ServerPlayer player, ItemStack stack, int remainingUseTicks) {
		int useDuration = Math.max(1, stack.getUseDuration(player));
		int remaining = Math.max(0, remainingUseTicks);
		int used = Math.max(0, useDuration - remaining);
		int chargeDuration = definition.item().heldType().chargeDuration(stack, player);
		return new DynamicItemUsingContext(level, player, player.getUsedItemHand(), stack, definition,
				used, remaining, useDuration, Math.max(1, chargeDuration));
	}

	private static String formatDecimal(double value) {
		String formatted = String.format(Locale.ROOT, "%.2f", value);
		return formatted.replaceFirst("\\.?0+$", "");
	}
}
