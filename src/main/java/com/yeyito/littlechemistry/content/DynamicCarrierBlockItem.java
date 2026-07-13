package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class DynamicCarrierBlockItem extends BlockItem {
	public DynamicCarrierBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Block")
				: Component.literal(definition.displayName());
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
		if (state == null || definition == null || definition.block() == null) {
			return state;
		}
		return state
				.setValue(DynamicCarrierBlock.LIGHT_LEVEL, definition.block().lightLevel())
				.setValue(DynamicCarrierBlock.MATERIAL, definition.block().material());
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
		if (definition != null && definition.hasBehavior()) {
			if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
			if (context.getLevel() instanceof ServerLevel serverLevel
					&& context.getPlayer() instanceof ServerPlayer serverPlayer) {
				InteractionResult behaviorResult = DynamicBehaviorRegistry.useOnBlock(
						definition, context, serverLevel, serverPlayer);
				if (behaviorResult != InteractionResult.PASS) return behaviorResult;
			}
		}
		return super.useOn(context);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && definition.hasBehavior()) {
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
		if (definition != null && definition.hasBehavior()) {
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
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && definition.hasBehavior()) {
			DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
		}
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && definition.hasBehavior() && attacker.level() instanceof ServerLevel level) {
			DynamicBehaviorRegistry.postHurtEnemy(definition, level, attacker, target, stack);
		}
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position, LivingEntity miner) {
		boolean result = super.mineBlock(stack, level, state, position, miner);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && definition.hasBehavior() && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.mineBlock(definition, serverLevel, miner, position, state, stack);
		}
		return result;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity consumer) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		ItemStack original = stack.copy();
		ItemStack result = super.finishUsingItem(stack, level, consumer);
		if (definition != null && definition.hasBehavior() && level instanceof ServerLevel serverLevel) {
			return DynamicBehaviorRegistry.finishUsing(definition, serverLevel, consumer, original, result);
		}
		return result;
	}

	@Override
	public void onCraftedBy(ItemStack stack, Player player) {
		super.onCraftedBy(stack, player);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && definition.hasBehavior() && player instanceof ServerPlayer serverPlayer
				&& player.level() instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.crafted(definition, serverLevel, serverPlayer, stack);
		}
	}
}
