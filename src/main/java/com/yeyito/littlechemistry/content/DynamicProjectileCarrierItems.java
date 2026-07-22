package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Native bow carrier; vanilla remains authoritative when generated behavior does not handle an action. */
final class DynamicBowCarrierItem extends BowItem implements DynamicItemCarrier {
	DynamicBowCarrierItem(Item.Properties properties) {
		super(properties);
	}

	@Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		DynamicProjectileCarrierHooks.inventoryTick(stack, level, owner, slot);
	}

	@Override public InteractionResult useOn(UseOnContext context) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeUseOn(context);
		return result == null ? super.useOn(context) : result;
	}

	@Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeUse(player.getItemInHand(hand), level, player, hand);
		return result == null ? super.use(level, player, hand) : result;
	}

	@Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeInteractLivingEntity(stack, player, target, hand);
		return result == null ? super.interactLivingEntity(stack, player, target, hand) : result;
	}

	@Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		DynamicProjectileCarrierHooks.postHurtEnemy(stack, target, attacker);
	}

	@Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position,
			LivingEntity miner) {
		boolean result = super.mineBlock(stack, level, state, position, miner);
		DynamicProjectileCarrierHooks.mineBlock(stack, level, state, position, miner);
		return result;
	}

	@Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity consumer) {
		ItemStack original = stack.copy();
		return DynamicProjectileCarrierHooks.finishUsing(
				original, super.finishUsingItem(stack, level, consumer), level, consumer);
	}

	@Override public void onCraftedBy(ItemStack stack, Player player) {
		super.onCraftedBy(stack, player);
		DynamicProjectileCarrierHooks.crafted(stack, player);
	}

	@Override public Component getName(ItemStack stack) {
		return DynamicProjectileCarrierHooks.name(stack);
	}

	@Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		DynamicProjectileCarrierHooks.appendHoverText(stack, context, display, builder, flag);
	}
}

/** Native crossbow carrier; charging, charged-projectile storage, and firing are inherited from Minecraft. */
final class DynamicCrossbowCarrierItem extends CrossbowItem implements DynamicItemCarrier {
	DynamicCrossbowCarrierItem(Item.Properties properties) {
		super(properties);
	}

	@Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		DynamicProjectileCarrierHooks.inventoryTick(stack, level, owner, slot);
	}

	@Override public InteractionResult useOn(UseOnContext context) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeUseOn(context);
		return result == null ? super.useOn(context) : result;
	}

	@Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeUse(player.getItemInHand(hand), level, player, hand);
		return result == null ? super.use(level, player, hand) : result;
	}

	@Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		InteractionResult result = DynamicProjectileCarrierHooks.beforeInteractLivingEntity(stack, player, target, hand);
		return result == null ? super.interactLivingEntity(stack, player, target, hand) : result;
	}

	@Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		DynamicProjectileCarrierHooks.postHurtEnemy(stack, target, attacker);
	}

	@Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position,
			LivingEntity miner) {
		boolean result = super.mineBlock(stack, level, state, position, miner);
		DynamicProjectileCarrierHooks.mineBlock(stack, level, state, position, miner);
		return result;
	}

	@Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity consumer) {
		ItemStack original = stack.copy();
		return DynamicProjectileCarrierHooks.finishUsing(
				original, super.finishUsingItem(stack, level, consumer), level, consumer);
	}

	@Override public void onCraftedBy(ItemStack stack, Player player) {
		super.onCraftedBy(stack, player);
		DynamicProjectileCarrierHooks.crafted(stack, player);
	}

	@Override public Component getName(ItemStack stack) {
		return DynamicProjectileCarrierHooks.name(stack);
	}

	@Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		DynamicProjectileCarrierHooks.appendHoverText(stack, context, display, builder, flag);
	}
}
