package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

public final class DynamicCarrierBlockItem extends BlockItem {
	public DynamicCarrierBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Block")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && !definition.description().isBlank()) {
			DynamicTooltipText.appendWrapped(builder, definition.description(), ChatFormatting.GRAY);
		}
		if (definition != null && definition.workstation() != null) {
			builder.accept(Component.translatable("tooltip.little_chemistry.workstation")
					.withStyle(ChatFormatting.AQUA));
		}
		if (definition != null && definition.block() != null) {
			for (int index = 0; index < definition.block().drops().entries().size(); index++) {
				DynamicDropEntry entry = definition.block().drops().entries().get(index);
				builder.accept(Component.translatable(index == 0
						? "tooltip.little_chemistry.block_drop.primary"
						: "tooltip.little_chemistry.block_drop.bonus", describeDrop(entry))
						.withStyle(ChatFormatting.DARK_GRAY));
			}
			if (definition.block().drops().silkTouchDropsSelf()) {
				builder.accept(Component.translatable("tooltip.little_chemistry.block_drop.silk_touch")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
			if (definition.block().drops().explosionDecay()) {
				builder.accept(Component.translatable("tooltip.little_chemistry.block_drop.explosion_decay")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	private static Component describeDrop(DynamicDropEntry entry) {
		var description = Component.empty();
		if (entry.chance() < 1.0) {
			description.append(Component.translatable("tooltip.little_chemistry.block_drop.chance",
					formatPercentage(entry.chance())));
		}
		if (entry.minCount() == entry.maxCount()) {
			if (entry.minCount() != 1) {
				description.append(Component.translatable("tooltip.little_chemistry.block_drop.count",
						entry.minCount()));
			}
		} else {
			description.append(Component.translatable("tooltip.little_chemistry.block_drop.count_range",
					entry.minCount(), entry.maxCount()));
		}
		description.append(dropTargetName(entry));
		if (entry.fortune() != DynamicFortuneMode.NONE && supportsFortune(entry)) {
			description.append(Component.translatable("tooltip.little_chemistry.block_drop.fortune"));
		}
		return description;
	}

	private static Component dropTargetName(DynamicDropEntry entry) {
		if (entry.isSelf()) return Component.translatable("tooltip.little_chemistry.block_drop.self");
		var id = entry.targetId();
		if (entry.targetKind() == DynamicDropTargetKind.DYNAMIC_CONTENT) {
			DynamicContentDefinition generated = DynamicContentCatalog.find(id);
			return generated == null ? Component.literal(entry.target()) : Component.literal(generated.displayName());
		}
		return BuiltInRegistries.ITEM.getOptional(id)
				.filter(item -> !DynamicContentObjects.isCarrierItem(item))
				.<Component>map(item -> new ItemStack(item).getHoverName())
				.orElseGet(() -> Component.literal(entry.target()));
	}

	private static boolean supportsFortune(DynamicDropEntry entry) {
		if (entry.isSelf()) return false;
		var id = entry.targetId();
		if (entry.targetKind() == DynamicDropTargetKind.DYNAMIC_CONTENT) {
			DynamicContentDefinition generated = DynamicContentCatalog.find(id);
			return generated != null && switch (generated.type()) {
				case BLOCK, ENTITY -> true;
				case ITEM -> generated.item().maxStack() > 1;
				case ARMOR -> false;
			};
		}
		return BuiltInRegistries.ITEM.getOptional(id)
				.filter(item -> !DynamicContentObjects.isCarrierItem(item))
				.map(item -> new ItemStack(item).getMaxStackSize() > 1)
				.orElse(false);
	}

	private static String formatPercentage(double chance) {
		double percentage = chance * 100.0;
		return percentage == Math.rint(percentage)
				? Long.toString(Math.round(percentage))
				: String.format(Locale.ROOT, "%.1f", percentage);
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
		if (state == null || definition == null || definition.block() == null) {
			return state;
		}
		state = state
				.setValue(DynamicCarrierBlock.LIGHT_LEVEL, definition.block().lightLevel())
				.setValue(DynamicCarrierBlock.MATERIAL, definition.block().material());
		if (definition.block().directional()) {
			state = state.setValue(DynamicCarrierBlock.FACING, context.getHorizontalDirection().getOpposite());
		}
		return state;
	}

	@Override
	protected boolean canPlace(BlockPlaceContext context, BlockState stateForPlacement) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
		if (definition == null || definition.block() == null) return super.canPlace(context, stateForPlacement);
		BlockPos position = context.getClickedPos();
		return stateForPlacement.canSurvive(context.getLevel(), position)
				&& context.getLevel().isUnobstructed(null,
						DynamicCarrierBlock.collisionShape(
								definition, stateForPlacement, context.getLevel(), position).move(position));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(context.getItemInHand());
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
		return super.useOn(context);
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
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		DynamicContentObjects.refreshDynamicAttributes(stack);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null) {
			DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
		}
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
}
