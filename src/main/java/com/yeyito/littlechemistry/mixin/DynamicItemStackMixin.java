package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.content.DynamicCarrierHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Composes generated callbacks around vanilla item dispatch without replacing native mechanics. */
@Mixin(ItemStack.class)
public abstract class DynamicItemStackMixin {
	@Inject(method = "useOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"),
			cancellable = true)
	private void littleChemistry$beforeUseOn(UseOnContext context,
			CallbackInfoReturnable<InteractionResult> callback) {
		InteractionResult result = DynamicCarrierHooks.beforeUseOn(context);
		if (result != null) callback.setReturnValue(result);
	}

	@Inject(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"),
			cancellable = true)
	private void littleChemistry$beforeUse(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> callback) {
		ItemStack stack = (ItemStack) (Object) this;
		InteractionResult result = DynamicCarrierHooks.beforeUse(stack, level, player, hand);
		if (result != null) callback.setReturnValue(result);
	}

	@Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack littleChemistry$finishUsing(Item item, ItemStack stack, Level level,
			LivingEntity consumer) {
		ItemStack original = stack.copy();
		ItemStack vanillaResult = item.finishUsingItem(stack, level, consumer);
		return DynamicCarrierHooks.isDynamicHeldStack(original)
				? DynamicCarrierHooks.finishUsing(original, vanillaResult, level, consumer)
				: vanillaResult;
	}

	@Inject(method = "postHurtEnemy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;postHurtEnemy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V",
			shift = At.Shift.AFTER))
	private void littleChemistry$postHurtEnemy(LivingEntity target, LivingEntity attacker,
			CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			DynamicCarrierHooks.postHurtEnemy(stack, target, attacker);
		}
	}

	@Inject(method = "mineBlock", at = @At("TAIL"))
	private void littleChemistry$mineBlock(Level level, BlockState state, BlockPos position,
			Player owner, CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			DynamicCarrierHooks.mineBlock(stack, level, state, position, owner);
		}
	}

	@Inject(method = "interactLivingEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;interactLivingEntity(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"),
			cancellable = true)
	private void littleChemistry$beforeInteractLivingEntity(Player player, LivingEntity target,
			InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
		ItemStack stack = (ItemStack) (Object) this;
		InteractionResult result = DynamicCarrierHooks.beforeInteractLivingEntity(stack, player, target, hand);
		if (result != null) callback.setReturnValue(result);
	}

	@Inject(method = "inventoryTick", at = @At("TAIL"))
	private void littleChemistry$inventoryTick(Level level, Entity owner, @Nullable EquipmentSlot slot,
			CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (level instanceof ServerLevel serverLevel && DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			DynamicCarrierHooks.inventoryTick(stack, serverLevel, owner, slot);
		}
	}

	@Inject(method = "onCraftedBy", at = @At("TAIL"))
	private void littleChemistry$crafted(Player player, int craftCount, CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			DynamicCarrierHooks.crafted(stack, player);
		}
	}

	@Redirect(method = "releaseUsing", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/Item;releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z"))
	private boolean littleChemistry$releaseUsing(Item item, ItemStack stack, Level level,
			LivingEntity user, int remainingUseTicks) {
		if (!DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			return item.releaseUsing(stack, level, user, remainingUseTicks);
		}
		return DynamicCarrierHooks.releaseUsing(stack, level, user, remainingUseTicks,
				() -> item.releaseUsing(stack, level, user, remainingUseTicks));
	}

	@Inject(method = "onUseTick", at = @At("TAIL"))
	private void littleChemistry$usingTick(Level level, LivingEntity user, int remainingUseTicks,
			CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		if (DynamicCarrierHooks.isDynamicHeldStack(stack)) {
			DynamicCarrierHooks.onUseTick(stack, level, user, remainingUseTicks);
		}
	}
}
