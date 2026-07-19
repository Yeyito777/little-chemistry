package com.yeyito.littlechemistry.item;

import com.yeyito.littlechemistry.crafting.PortableCraftingAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Opens a full crafting-table menu without requiring a placed block. */
public final class CraftingTableOnAStickItem extends Item {
	private static final Component TITLE = Component.translatable(
			"container.little_chemistry.crafting_table_on_a_stick");

	public CraftingTableOnAStickItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		openMenu(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		openMenu(context.getPlayer());
		return InteractionResult.SUCCESS;
	}

	private static void openMenu(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider(
					(containerId, inventory, ignored) -> new CraftingMenu(
							containerId,
							inventory,
							new PortableCraftingAccess(serverPlayer.level(), serverPlayer.blockPosition())
					),
					TITLE
			));
		}
	}
}
