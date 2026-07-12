package com.yeyito.littlechemistry.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DynamicCarrierItem extends Item {
	public DynamicCarrierItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Item")
				: Component.literal(definition.displayName());
	}
}
