package com.yeyito.littlechemistry.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Generic carrier for profiles whose native mechanics are component-driven in Minecraft 26.2.
 * Native class-based profiles use the thin subclasses in {@code DynamicNativeCarrierItems}.
 */
public final class DynamicCarrierItem extends Item implements DynamicItemCarrier {
	public DynamicCarrierItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		DynamicPlacementProperties placement = definition == null || definition.item() == null
				? null : definition.item().placement();
		if (placement == null) {
			// Definitions created before tool-specific carrier IDs remain usable in-place. The native
			// AxeItem/ShovelItem/HoeItem implementation acts on the legacy stack from the context.
			Item compatibilityTool = DynamicContentObjects.compatibilityToolFor(stack, definition);
			return compatibilityTool == null ? super.useOn(context) : compatibilityTool.useOn(context);
		}

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
			com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry.placedBlock(
					definition, serverLevel, context.getPlayer(), position, state, stack);
		}
		SoundType sound = state.getSoundType();
		level.playSound(context.getPlayer(), position, sound.getPlaceSound(), SoundSource.BLOCKS,
				(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, position, GameEvent.Context.of(context.getPlayer(), state));
		stack.consume(1, context.getPlayer());
		return InteractionResult.SUCCESS;
	}
}
