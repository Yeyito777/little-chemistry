package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.core.Registry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public final class DynamicContentObjects {
	public static DataComponentType<Identifier> CONTENT_ID;
	public static DynamicCarrierItem ITEM;
	public static DynamicCarrierItem TOOL_ITEM;
	public static DynamicCarrierBlock BLOCK;
	public static DynamicCarrierBlockItem BLOCK_ITEM;
	public static BlockEntityType<DynamicBlockEntity> BLOCK_ENTITY_TYPE;

	private DynamicContentObjects() {
	}

	public static void register() {
		CONTENT_ID = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				LittleChemistry.id("content_id"),
				DataComponentType.<Identifier>builder()
						.persistent(Identifier.CODEC)
						.networkSynchronized(Identifier.STREAM_CODEC)
						.build()
		);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, LittleChemistry.id("dynamic_item"));
		ITEM = Registry.register(
				BuiltInRegistries.ITEM,
				itemKey,
				new DynamicCarrierItem(new Item.Properties().setId(itemKey))
		);

		ResourceKey<Item> toolItemKey = ResourceKey.create(Registries.ITEM, LittleChemistry.id("dynamic_tool"));
		TOOL_ITEM = Registry.register(
				BuiltInRegistries.ITEM,
				toolItemKey,
				new DynamicCarrierItem(new Item.Properties().setId(toolItemKey).stacksTo(1))
		);

		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, LittleChemistry.id("dynamic_block"));
		BLOCK = Registry.register(
				BuiltInRegistries.BLOCK,
				blockKey,
				new DynamicCarrierBlock(BlockBehaviour.Properties.of()
						.setId(blockKey)
						.strength(1.5F, 6.0F)
						.randomTicks()
						.noOcclusion()
						.dynamicShape()
						.lightLevel(state -> state.getValue(DynamicCarrierBlock.LIGHT_LEVEL)))
		);

		ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, LittleChemistry.id("dynamic_block"));
		BLOCK_ITEM = Registry.register(
				BuiltInRegistries.ITEM,
				blockItemKey,
				new DynamicCarrierBlockItem(BLOCK, new Item.Properties().setId(blockItemKey).useBlockDescriptionPrefix())
		);

		BLOCK_ENTITY_TYPE = Registry.register(
				BuiltInRegistries.BLOCK_ENTITY_TYPE,
				LittleChemistry.id("dynamic_block"),
				new BlockEntityType<>(DynamicBlockEntity::new, Set.of(BLOCK))
		);
	}

	public static ItemStack createStack(DynamicContentDefinition definition) {
		Item carrier = definition.type() == DynamicContentType.BLOCK
				? BLOCK_ITEM
				: definition.item().itemType() == DynamicItemType.TOOL ? TOOL_ITEM : ITEM;
		ItemStack stack = new ItemStack(carrier);
		stack.set(CONTENT_ID, LittleChemistry.id(definition.name()));
		if (definition.item() != null) {
			DynamicItemProperties properties = definition.item();
			stack.set(DataComponents.MAX_STACK_SIZE, properties.maxStack());
			stack.set(DataComponents.RARITY, properties.rarity());
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, properties.foil());
			if (properties.enchantability() > 0) {
				stack.set(DataComponents.ENCHANTABLE, new Enchantable(properties.enchantability()));
			}
			if (properties.food() != null) {
				DynamicFoodProperties food = properties.food();
				stack.set(DataComponents.FOOD, new FoodProperties(food.hunger(), food.saturation(), food.alwaysEdible()));
				var consumable = Consumables.defaultFood().consumeSeconds(food.consumeSeconds());
				for (DynamicFoodEffect effect : food.effects()) {
					consumable.onConsume(new ApplyStatusEffectsConsumeEffect(effect.instance(), effect.probability()));
				}
				stack.set(DataComponents.CONSUMABLE, consumable.build());
			}
			if (properties.tool() != DynamicTool.NONE) {
				List<Tool.Rule> rules = new ArrayList<>();
				rules.add(Tool.Rule.deniesDrops(BuiltInRegistries.BLOCK.getOrThrow(properties.breakingPower().incorrectBlocks())));
				rules.add(Tool.Rule.minesAndDrops(BuiltInRegistries.BLOCK.getOrThrow(properties.tool().mineableBlocks()), properties.breakingSpeed()));
				stack.set(DataComponents.TOOL, new Tool(List.copyOf(rules), 1.0F, properties.damagePerBlock(), true));
				stack.set(DataComponents.MAX_DAMAGE, properties.durability());
				stack.set(DataComponents.DAMAGE, 0);
				stack.set(DataComponents.WEAPON, new Weapon(properties.damagePerAttack()));
			}
			ItemAttributeModifiers attributes = attributes(properties);
			if (attributes != null) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
		}
		return stack;
	}

	public static void refreshDynamicAttributes(ItemStack stack) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || definition.item() == null) return;
		ItemAttributeModifiers expected = attributes(definition.item());
		if (expected == null) return;
		ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (!expected.equals(current)) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, expected);
	}

	private static ItemAttributeModifiers attributes(DynamicItemProperties properties) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		boolean hasAttributes = false;
		if (properties.itemType() == DynamicItemType.TOOL) {
			double baseAttackDamage = Attributes.ATTACK_DAMAGE.value().getDefaultValue();
			double baseAttackSpeed = Attributes.ATTACK_SPEED.value().getDefaultValue();
			builder
					.add(Attributes.ATTACK_DAMAGE,
							new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, properties.attackDamage() - baseAttackDamage, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED,
							new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, properties.attackSpeed() - baseAttackSpeed, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND);
			hasAttributes = true;
		}
		if (properties.reach() > 0.0) {
			builder
					.add(Attributes.BLOCK_INTERACTION_RANGE,
							new AttributeModifier(LittleChemistry.id("dynamic_item_block_reach"), properties.reach(), AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ENTITY_INTERACTION_RANGE,
							new AttributeModifier(LittleChemistry.id("dynamic_item_entity_reach"), properties.reach(), AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND);
			hasAttributes = true;
		}
		return hasAttributes ? builder.build() : null;
	}

	public static ItemStack createBlockStack(Identifier contentId) {
		ItemStack stack = new ItemStack(BLOCK_ITEM);
		if (contentId != null) {
			stack.set(CONTENT_ID, contentId);
		}
		return stack;
	}

	public static DynamicContentDefinition definition(ItemStack stack) {
		Identifier contentId = stack.get(CONTENT_ID);
		return DynamicContentCatalog.find(contentId);
	}
}
