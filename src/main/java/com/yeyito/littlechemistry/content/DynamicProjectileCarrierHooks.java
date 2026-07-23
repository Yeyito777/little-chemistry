package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
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
import java.util.function.Consumer;

/** Composes existing generated item callbacks and presentation around native projectile-weapon mechanics. */
final class DynamicProjectileCarrierHooks {
	private DynamicProjectileCarrierHooks() {
	}

	static void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		DynamicContentObjects.refreshDynamicAttributes(stack);
		DynamicContentDefinition definition = definition(stack);
		if (definition != null) DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
	}

	static @Nullable InteractionResult beforeUseOn(UseOnContext context) {
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

	/** Client use must always reach BowItem/CrossbowItem so drawing and charging are predicted normally. */
	static @Nullable InteractionResult beforeUse(ItemStack stack, Level level, Player player, InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || level.isClientSide()
				|| !supports(definition, DynamicBehaviorCapability.USE_AIR)) return null;
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			InteractionResult result = DynamicBehaviorRegistry.useAir(
					definition, serverLevel, serverPlayer, hand, stack);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	static @Nullable InteractionResult beforeInteractLivingEntity(ItemStack stack, Player player,
			LivingEntity target, InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !supports(definition, DynamicBehaviorCapability.INTERACT_LIVING_ENTITY)) return null;
		if (player.level().isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
			InteractionResult result = DynamicBehaviorRegistry.interactLivingEntity(
					definition, serverLevel, serverPlayer, hand, stack, target);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	static void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && attacker.level() instanceof ServerLevel level) {
			DynamicBehaviorRegistry.postHurtEnemy(definition, level, attacker, target, stack);
		}
	}

	static void mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position, LivingEntity miner) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.mineBlock(definition, serverLevel, miner, position, state, stack);
		}
	}

	static ItemStack finishUsing(ItemStack original, ItemStack vanillaResult, Level level, LivingEntity consumer) {
		DynamicContentDefinition definition = definition(original);
		return definition != null && level instanceof ServerLevel serverLevel
				? DynamicBehaviorRegistry.finishUsing(definition, serverLevel, consumer, original, vanillaResult)
				: vanillaResult;
	}

	static void crafted(ItemStack stack, Player player) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && player instanceof ServerPlayer serverPlayer
				&& player.level() instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.crafted(definition, serverLevel, serverPlayer, stack);
		}
	}

	static Component name(ItemStack stack) {
		DynamicContentDefinition definition = definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Item")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	static void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && !definition.description().isBlank()) {
			DynamicTooltipText.appendWrapped(builder, definition.description(), ChatFormatting.GRAY);
		}
		if (definition == null || definition.item() == null) return;
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
		return stack.getItem() instanceof DynamicItemCarrier ? DynamicContentObjects.definition(stack) : null;
	}

	private static boolean supports(DynamicContentDefinition definition, DynamicBehaviorCapability capability) {
		return DynamicBehaviorSource.supports(definition.behaviorSource(), capability);
	}

	private static String formatDecimal(double value) {
		String formatted = String.format(Locale.ROOT, "%.2f", value);
		return formatted.replaceFirst("\\.?0+$", "");
	}
}
