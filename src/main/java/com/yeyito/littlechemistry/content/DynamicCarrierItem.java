package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

public final class DynamicCarrierItem extends Item {
	public DynamicCarrierItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		DynamicContentObjects.refreshDynamicAttributes(stack);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null) {
			DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && DynamicBehaviorSource.supports(
				definition.behaviorSource(), DynamicBehaviorCapability.USE_ON_BLOCK)) {
			if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
			if (context.getLevel() instanceof ServerLevel serverLevel
					&& context.getPlayer() instanceof ServerPlayer serverPlayer) {
				InteractionResult behaviorResult = DynamicBehaviorRegistry.useOnBlock(
						definition, context, serverLevel, serverPlayer);
				if (behaviorResult != InteractionResult.PASS) return behaviorResult;
			}
		}
		DynamicPlacementProperties placement = definition == null || definition.item() == null
				? null : definition.item().placement();
		if (placement == null) return super.useOn(context);

		BlockPlaceContext placeContext = new BlockPlaceContext(context);
		Level level = context.getLevel();
		BlockPos position = placeContext.getClickedPos();
		BlockPos supportPosition = position.below();
		BlockState support = level.getBlockState(supportPosition);
		if (!placeContext.canPlace() || !placement.canPlaceOn(support, level, supportPosition)) {
			return InteractionResult.FAIL;
		}

		BlockState state = DynamicContentObjects.BLOCK.defaultBlockState()
				.setValue(DynamicCarrierBlock.LIGHT_LEVEL, placement.lightLevel())
				.setValue(DynamicCarrierBlock.MATERIAL,
						placement.shape() == DynamicPlacedShape.TORCH ? DynamicMaterial.WOOD : DynamicMaterial.ORGANIC);
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!level.setBlock(position, state, 11)) return InteractionResult.FAIL;

		BlockEntity blockEntity = level.getBlockEntity(position);
		if (blockEntity != null) {
			blockEntity.applyComponentsFromItemStack(stack);
			blockEntity.setChanged();
			level.sendBlockUpdated(position, state, state, 3);
		}
		if (level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.placedBlock(
					definition, serverLevel, context.getPlayer(), position, state, stack);
		}
		SoundType sound = state.getSoundType();
		level.playSound(context.getPlayer(), position, sound.getPlaceSound(), SoundSource.BLOCKS,
				(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, position, GameEvent.Context.of(context.getPlayer(), state));
		stack.consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && DynamicBehaviorSource.supports(
				definition.behaviorSource(), DynamicBehaviorCapability.USE_AIR)) {
			if (level.isClientSide()) return InteractionResult.SUCCESS;
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				InteractionResult behaviorResult = DynamicBehaviorRegistry.useAir(
						definition, serverLevel, serverPlayer, hand, stack);
				if (behaviorResult != InteractionResult.PASS) return behaviorResult;
			}
		}
		return super.use(level, player, hand);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && DynamicBehaviorSource.supports(
				definition.behaviorSource(), DynamicBehaviorCapability.INTERACT_LIVING_ENTITY)) {
			if (player.level().isClientSide()) return InteractionResult.SUCCESS;
			if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
				InteractionResult result = DynamicBehaviorRegistry.interactLivingEntity(
						definition, serverLevel, serverPlayer, hand, stack, target);
				if (result != InteractionResult.PASS) return result;
			}
		}
		return super.interactLivingEntity(stack, player, target, hand);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && attacker.level() instanceof ServerLevel level) {
			DynamicBehaviorRegistry.postHurtEnemy(definition, level, attacker, target, stack);
		}
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position, LivingEntity miner) {
		boolean result = super.mineBlock(stack, level, state, position, miner);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.mineBlock(definition, serverLevel, miner, position, state, stack);
		}
		return result;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity consumer) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		ItemStack original = stack.copy();
		ItemStack result = super.finishUsingItem(stack, level, consumer);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			return DynamicBehaviorRegistry.finishUsing(definition, serverLevel, consumer, original, result);
		}
		return result;
	}

	@Override
	public void onCraftedBy(ItemStack stack, Player player) {
		super.onCraftedBy(stack, player);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && player instanceof ServerPlayer serverPlayer
				&& player.level() instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.crafted(definition, serverLevel, serverPlayer, stack);
		}
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Item")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && !definition.description().isBlank()) {
			definition.description().lines().forEach(line ->
					builder.accept(Component.literal(line).withStyle(ChatFormatting.GRAY)));
		}
		if (definition != null && definition.item() != null) {
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
	}

	private static String formatDecimal(double value) {
		String formatted = String.format(Locale.ROOT, "%.2f", value);
		return formatted.replaceFirst("\\.?0+$", "");
	}
}
