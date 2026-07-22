package com.yeyito.littlechemistry.content;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TridentItem;

/*
 * These intentionally thin classes preserve vanilla implementation and class identity. Generated
 * callbacks are composed around ItemStack's central dispatch points by DynamicItemStackMixin,
 * rather than reimplementing the mechanics in each subclass.
 */
final class DynamicBowCarrierItem extends BowItem implements DynamicItemCarrier {
	DynamicBowCarrierItem(Item.Properties properties) {
		super(properties);
	}
}

final class DynamicCrossbowCarrierItem extends CrossbowItem implements DynamicItemCarrier {
	DynamicCrossbowCarrierItem(Item.Properties properties) {
		super(properties);
	}
}

final class DynamicFishingRodCarrierItem extends FishingRodItem implements DynamicItemCarrier {
	DynamicFishingRodCarrierItem(Item.Properties properties) {
		super(properties);
	}
}

final class DynamicMaceCarrierItem extends MaceItem implements DynamicItemCarrier {
	DynamicMaceCarrierItem(Item.Properties properties) {
		super(properties);
	}
}

final class DynamicAxeCarrierItem extends AxeItem implements DynamicItemCarrier {
	DynamicAxeCarrierItem(ToolMaterial material, Item.Properties properties) {
		super(material, 0.0F, -3.0F, properties);
	}
}

final class DynamicShovelCarrierItem extends ShovelItem implements DynamicItemCarrier {
	DynamicShovelCarrierItem(ToolMaterial material, Item.Properties properties) {
		super(material, 0.0F, -3.0F, properties);
	}
}

final class DynamicHoeCarrierItem extends HoeItem implements DynamicItemCarrier {
	DynamicHoeCarrierItem(ToolMaterial material, Item.Properties properties) {
		super(material, 0.0F, -3.0F, properties);
	}
}

final class DynamicShieldCarrierItem extends ShieldItem implements DynamicItemCarrier {
	DynamicShieldCarrierItem(Item.Properties properties) {
		super(properties);
	}
}

final class DynamicTridentCarrierItem extends TridentItem implements DynamicItemCarrier {
	DynamicTridentCarrierItem(Item.Properties properties) {
		super(properties);
	}
}
