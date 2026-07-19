package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DynamicContentObjects {
	public static DataComponentType<Identifier> CONTENT_ID;
	public static DynamicCarrierItem ITEM;
	public static DynamicCarrierItem TOOL_HELD_ITEM;
	private static final Map<DynamicHeldType, DynamicCarrierItem> HELD_ITEMS = new EnumMap<>(DynamicHeldType.class);
	public static DynamicCarrierItem ARMOR_HEAD;
	public static DynamicCarrierItem ARMOR_CHEST;
	public static DynamicCarrierItem ARMOR_LEGGINGS;
	public static DynamicCarrierItem ARMOR_BOOTS;
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

		ITEM = registerHeldCarrier(DynamicHeldType.REGULAR, "dynamic_item", false);
		TOOL_HELD_ITEM = registerHeldCarrier(DynamicHeldType.TOOL, "dynamic_tool", true);
		registerHeldCarrier(DynamicHeldType.ROD, "dynamic_rod", false);
		registerHeldCarrier(DynamicHeldType.BOW, "dynamic_bow", false);
		registerHeldCarrier(DynamicHeldType.CROSSBOW, "dynamic_crossbow", false);
		registerHeldCarrier(DynamicHeldType.MACE, "dynamic_mace", false);
		registerHeldCarrier(DynamicHeldType.SPEAR, "dynamic_spear", false);

		ARMOR_HEAD = registerArmorCarrier("dynamic_armor_head");
		ARMOR_CHEST = registerArmorCarrier("dynamic_armor_chest");
		ARMOR_LEGGINGS = registerArmorCarrier("dynamic_armor_leggings");
		ARMOR_BOOTS = registerArmorCarrier("dynamic_armor_boots");

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
		Item carrier = switch (definition.type()) {
			case BLOCK -> BLOCK_ITEM;
			case ITEM -> heldCarrier(definition.item().heldType());
			case ARMOR -> armorCarrier(definition.armor().slot());
		};
		ItemStack stack = new ItemStack(carrier);
		stack.set(CONTENT_ID, LittleChemistry.id(definition.name()));
		stack.set(DataComponents.RARITY, definition.rarity());
		if (definition.item() != null) {
			DynamicItemProperties properties = definition.item();
			stack.set(DataComponents.MAX_STACK_SIZE, properties.maxStack());
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
		if (definition.armor() != null) {
			DynamicArmorProperties armor = definition.armor();
			stack.set(DataComponents.MAX_STACK_SIZE, 1);
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, armor.foil());
			if (armor.enchantability() > 0) {
				stack.set(DataComponents.ENCHANTABLE, new Enchantable(armor.enchantability()));
			}
			stack.set(DataComponents.MAX_DAMAGE, armor.durability());
			stack.set(DataComponents.DAMAGE, 0);
			stack.set(DataComponents.EQUIPPABLE, Equippable.builder(armor.slot().equipmentSlot())
					.setAsset(armorAsset(definition.effectiveArmorDisplayTextureHash()))
					.build());
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes(armor));
		}
		return stack;
	}

	/** A tellraw-style item link with the custom tier color and vanilla item hover event. */
	public static Component displayName(DynamicContentDefinition definition) {
		return createStack(definition).getDisplayName().copy().withStyle(definition.rarityTier().color());
	}

	public static void refreshDynamicAttributes(ItemStack stack) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null) return;
		Rarity currentRarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
		if (currentRarity != definition.rarity()) stack.set(DataComponents.RARITY, definition.rarity());
		ItemAttributeModifiers expected = definition.armor() != null
				? attributes(definition.armor())
				: definition.item() != null ? attributes(definition.item()) : null;
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

	private static ItemAttributeModifiers attributes(DynamicArmorProperties armor) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		Identifier modifierId = LittleChemistry.id("armor." + armor.slot().serializedName());
		builder.add(Attributes.ARMOR,
				new AttributeModifier(modifierId, armor.defense(), AttributeModifier.Operation.ADD_VALUE),
				armor.slot().slotGroup());
		builder.add(Attributes.ARMOR_TOUGHNESS,
				new AttributeModifier(modifierId, armor.toughness(), AttributeModifier.Operation.ADD_VALUE),
				armor.slot().slotGroup());
		if (armor.knockbackResistance() > 0.0) {
			builder.add(Attributes.KNOCKBACK_RESISTANCE,
					new AttributeModifier(modifierId, armor.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE),
					armor.slot().slotGroup());
		}
		return builder.build();
	}

	public static ResourceKey<EquipmentAsset> armorAsset(String textureHash) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, LittleChemistry.id("dynamic/" + textureHash));
	}

	private static DynamicCarrierItem registerHeldCarrier(DynamicHeldType heldType, String name, boolean stacksToOne) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LittleChemistry.id(name));
		Item.Properties properties = new Item.Properties().setId(key);
		if (stacksToOne) properties.stacksTo(1);
		DynamicCarrierItem carrier = Registry.register(BuiltInRegistries.ITEM, key, new DynamicCarrierItem(properties));
		HELD_ITEMS.put(heldType, carrier);
		return carrier;
	}

	private static DynamicCarrierItem heldCarrier(DynamicHeldType heldType) {
		DynamicCarrierItem carrier = HELD_ITEMS.get(heldType);
		if (carrier == null) throw new IllegalStateException("No carrier registered for held type " + heldType);
		return carrier;
	}

	private static DynamicCarrierItem registerArmorCarrier(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LittleChemistry.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new DynamicCarrierItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	private static DynamicCarrierItem armorCarrier(DynamicArmorSlot slot) {
		return switch (slot) {
			case HEAD -> ARMOR_HEAD;
			case CHEST -> ARMOR_CHEST;
			case LEGGINGS -> ARMOR_LEGGINGS;
			case BOOTS -> ARMOR_BOOTS;
		};
	}

	public static ItemStack createBlockStack(Identifier contentId) {
		DynamicContentDefinition definition = DynamicContentCatalog.find(contentId);
		if (definition != null && definition.type() == DynamicContentType.BLOCK) {
			return createStack(definition);
		}
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

	/** Infrastructure carriers are never valid standalone item identities. */
	public static boolean isCarrierItem(Item item) {
		return item instanceof DynamicCarrierItem || item instanceof DynamicCarrierBlockItem;
	}
}
