package com.yeyito.littlechemistry.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/** Dynamic inventory carrier that spawns the logical generated entity stored in its content component. */
public final class DynamicEntitySpawnerItem extends Item {
	public DynamicEntitySpawnerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition == null || definition.type() != DynamicContentType.ENTITY) return InteractionResult.FAIL;
		if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
		if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.FAIL;

		var position = context.getClickedPos().relative(context.getClickedFace());
		DynamicCarrierEntity entity = DynamicEntitySpawner.spawn(level, definition,
				net.minecraft.world.phys.Vec3.atBottomCenterOf(position), context.getRotation(),
				context.getPlayer(), EntitySpawnReason.SPAWN_ITEM_USE);
		if (entity == null) return InteractionResult.FAIL;
		if (context.getPlayer() == null || !context.getPlayer().isCreative()) stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Entity")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null) {
			if (!definition.description().isBlank()) {
				builder.accept(Component.literal(definition.description()).withStyle(ChatFormatting.GRAY));
			}
			builder.accept(Component.literal("Use on a block to spawn").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
