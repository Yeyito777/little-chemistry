package com.yeyito.littlechemistry.item;

import com.yeyito.littlechemistry.crafting.AiCraftingManager;
import com.yeyito.littlechemistry.crafting.PortableCraftingAccess;
import com.yeyito.littlechemistry.crafting.PortableCraftingComponents;
import com.yeyito.littlechemistry.crafting.PortableCraftingState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

/** Opens one persistent, item-owned crafting grid without requiring a placed block. */
public final class CraftingTableOnAStickItem extends Item {
	private static final Component TITLE = Component.translatable(
			"container.little_chemistry.crafting_table_on_a_stick");

	public CraftingTableOnAStickItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		openMenu(player, player.getItemInHand(hand));
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		openMenu(context.getPlayer(), context.getItemInHand());
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager != null && manager.belongsTo(level)) manager.reconcilePortableStack(stack, level);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		PortableCraftingState state = stack.get(PortableCraftingComponents.STATE);
		if (state == PortableCraftingState.GENERATING) {
			builder.accept(Component.translatable("tooltip.little_chemistry.crafting_table_on_a_stick.generating")
					.withStyle(ChatFormatting.GRAY));
		} else if (state == PortableCraftingState.READY) {
			builder.accept(Component.translatable("tooltip.little_chemistry.crafting_table_on_a_stick.ready")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	private static void openMenu(Player player, ItemStack carrier) {
		if (!(player instanceof ServerPlayer serverPlayer)) return;
		AiCraftingManager manager = AiCraftingManager.active();
		if (manager == null || !manager.belongsTo(serverPlayer.level())) return;

		UUID tableId = carrier.get(PortableCraftingComponents.TABLE_ID);
		if (tableId == null || hasDuplicateId(serverPlayer, carrier, tableId)) {
			tableId = UUID.randomUUID();
			carrier.set(PortableCraftingComponents.TABLE_ID, tableId);
			serverPlayer.getInventory().setChanged();
		}
		UUID openedTableId = tableId;
		serverPlayer.openMenu(new SimpleMenuProvider(
				(containerId, inventory, ignored) -> new CraftingMenu(
						containerId,
						inventory,
						new PortableCraftingAccess(
								serverPlayer.level(), serverPlayer.blockPosition(), openedTableId, carrier)
				),
				TITLE
		));
	}

	private static boolean hasDuplicateId(ServerPlayer player, ItemStack carrier, UUID tableId) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack candidate = player.getInventory().getItem(slot);
			if (candidate != carrier && !candidate.isEmpty()
					&& tableId.equals(candidate.get(PortableCraftingComponents.TABLE_ID))) {
				return true;
			}
		}
		return false;
	}
}
