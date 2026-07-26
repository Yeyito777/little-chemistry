package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.behavior.DynamicProjectileWeaponContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Composes generated callbacks and presentation around either the native or custom projectile-weapon route. */

public final class DynamicProjectileCarrierHooks {
	private static final String WEAPON_TAG_PREFIX = "little_chemistry.weapon.";
	private DynamicProjectileCarrierHooks() {
	}

	static void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		DynamicContentObjects.refreshDynamicAttributes(stack);
		DynamicContentDefinition definition = definition(stack);
		if (definition != null) DynamicBehaviorRegistry.inventoryTick(definition, level, owner, slot, stack);
	}

	static @Nullable InteractionResult beforeUseOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !supports(definition, DynamicBehaviorCapability.USE_ON_BLOCK)) return null;
		if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
		if (context.getLevel() instanceof ServerLevel level
				&& context.getPlayer() instanceof ServerPlayer player) {
			InteractionResult result = DynamicBehaviorRegistry.useOnBlock(definition, context, level, player);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	/** The native route reaches BowItem/CrossbowItem; the synchronized custom route predicts its selected use lifecycle. */
	static @Nullable InteractionResult beforeUse(ItemStack stack, Level level, Player player, InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null) return null;
		DynamicProjectileWeaponSpec weapon = definition.item().projectileWeapon();
		boolean custom = weapon != null && weapon.mechanics() == DynamicProjectileMechanics.CUSTOM;
		if (custom && weapon.overridesAmmunition() && player.getProjectile(stack).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide() && supports(definition, DynamicBehaviorCapability.USE_AIR)
				&& level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			InteractionResult result = DynamicBehaviorRegistry.useAir(
					definition, serverLevel, serverPlayer, hand, stack);
			if (result != InteractionResult.PASS) return result;
		}
		if (!custom) return null;
		if (weapon.startUsing()) player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	static @Nullable InteractionResult beforeInteractLivingEntity(ItemStack stack, Player player,
			LivingEntity target, InteractionHand hand) {
		DynamicContentDefinition definition = definition(stack);
		if (definition == null || !supports(definition, DynamicBehaviorCapability.INTERACT_LIVING_ENTITY)) return null;
		if (player.level().isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
			InteractionResult result = DynamicBehaviorRegistry.interactLivingEntity(
					definition, serverLevel, serverPlayer, hand, stack, target);
			return result == InteractionResult.PASS ? null : result;
		}
		return null;
	}

	static void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && attacker.level() instanceof ServerLevel level) {
			DynamicBehaviorRegistry.postHurtEnemy(definition, level, attacker, target, stack);
		}
	}

	static void mineBlock(ItemStack stack, Level level, BlockState state, BlockPos position, LivingEntity miner) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.mineBlock(definition, serverLevel, miner, position, state, stack);
		}
	}

	static ItemStack finishUsing(ItemStack original, ItemStack vanillaResult, Level level, LivingEntity consumer) {
		DynamicContentDefinition definition = definition(original);
		return definition != null && level instanceof ServerLevel serverLevel
				? DynamicBehaviorRegistry.finishUsing(definition, serverLevel, consumer, original, vanillaResult)
				: vanillaResult;
	}

	static void crafted(ItemStack stack, Player player) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && player instanceof ServerPlayer serverPlayer
				&& player.level() instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.crafted(definition, serverLevel, serverPlayer, stack);
		}
	}

	static Projectile projectileCreated(Projectile vanillaProjectile, Level level, LivingEntity shooter,
			ItemStack weapon, ItemStack ammunition, boolean critical) {
		DynamicContentDefinition definition = definition(weapon);
		Projectile result = vanillaProjectile;
		if (definition != null && level instanceof ServerLevel serverLevel) {
			result = DynamicBehaviorRegistry.projectileCreated(
					definition, serverLevel, shooter, weapon, ammunition, vanillaProjectile, critical);
			markProjectile(result, definition);
		}
		return result;
	}

	public static void markProjectile(Projectile projectile, DynamicContentDefinition definition) {
		projectile.addTag(WEAPON_TAG_PREFIX + definition.name());
	}

	static @Nullable Boolean customRelease(ItemStack stack, Level level, LivingEntity shooter, int remainingTicks) {
		DynamicContentDefinition definition = definition(stack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		if (weapon == null || weapon.mechanics() != DynamicProjectileMechanics.CUSTOM) return null;
		if (level.isClientSide()) return true;
		if (!(level instanceof ServerLevel serverLevel)) return false;
		int ticksUsed = Math.max(0, weapon.useDurationTicks() - remainingTicks);
		ItemStack ammunition = selectedAmmunition(shooter, stack, weapon);
		DynamicProjectileWeaponContext context = new DynamicProjectileWeaponContext(
				serverLevel, shooter, shooter.getUsedItemHand(), stack, ammunition,
				ticksUsed, Math.max(0, remainingTicks), definition);
		return DynamicBehaviorRegistry.projectileWeaponRelease(definition, context);
	}

	static boolean customUseTick(ItemStack stack, Level level, LivingEntity shooter, int remainingTicks) {
		DynamicContentDefinition definition = definition(stack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		if (weapon == null || weapon.mechanics() != DynamicProjectileMechanics.CUSTOM) return false;
		if (level instanceof ServerLevel serverLevel
				&& supports(definition, DynamicBehaviorCapability.PROJECTILE_WEAPON_USE_TICK)) {
			int ticksUsed = Math.max(0, weapon.useDurationTicks() - remainingTicks);
			DynamicBehaviorRegistry.projectileWeaponUseTick(definition, new DynamicProjectileWeaponContext(
					serverLevel, shooter, shooter.getUsedItemHand(), stack,
					selectedAmmunition(shooter, stack, weapon), ticksUsed,
					Math.max(0, remainingTicks), definition));
		}
		return true;
	}

	static int useDuration(ItemStack stack, LivingEntity user, int nativeDuration) {
		DynamicContentDefinition definition = definition(stack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		return weapon != null && weapon.mechanics() == DynamicProjectileMechanics.CUSTOM
				? weapon.useDurationTicks() : nativeDuration;
	}

	static ItemUseAnimation useAnimation(ItemStack stack, ItemUseAnimation nativeAnimation) {
		DynamicContentDefinition definition = definition(stack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		return weapon != null && weapon.mechanics() == DynamicProjectileMechanics.CUSTOM
				? weapon.useAnimation() : nativeAnimation;
	}

	static boolean useOnRelease(ItemStack stack, boolean nativeValue) {
		DynamicContentDefinition definition = definition(stack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		return weapon != null && weapon.mechanics() == DynamicProjectileMechanics.CUSTOM
				? weapon.useOnRelease() : nativeValue;
	}

	/** Null means the generated stack did not override native ammunition selection. */
	public static @Nullable ItemStack customAmmunition(Player player, ItemStack weaponStack) {
		DynamicContentDefinition definition = definition(weaponStack);
		DynamicProjectileWeaponSpec weapon = definition == null ? null : definition.item().projectileWeapon();
		if (weapon == null || !weapon.overridesAmmunition()) return null;
		ItemStack selected = selectedAmmunition(player, weaponStack, weapon);
		if (weapon.mechanics() != DynamicProjectileMechanics.NATIVE || selected.isEmpty()) return selected;
		boolean supported = definition.item().heldType() == DynamicHeldType.BOW
				? selected.getItem() instanceof ArrowItem || selected.is(ItemTags.ARROWS)
				: crossbowAmmunition().test(selected);
		return supported ? selected : ItemStack.EMPTY;
	}

	private static ItemStack selectedAmmunition(LivingEntity shooter, ItemStack weaponStack,
			DynamicProjectileWeaponSpec weapon) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = shooter.getItemInHand(hand);
			if (held != weaponStack && weapon.matchesAmmunition(held)) return held;
		}
		if (shooter instanceof Player player) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack candidate = player.getInventory().getItem(slot);
				if (weapon.matchesAmmunition(candidate)) return candidate;
			}
		}
		return shooter.hasInfiniteMaterials() ? weapon.creativeDefaultStack() : ItemStack.EMPTY;
	}

	static Predicate<ItemStack> crossbowAmmunition() {
		return stack -> stack.getItem() instanceof ArrowItem
				|| stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET);
	}

	public static void projectileImpact(Projectile projectile, HitResult hit) {
		if (!(projectile.level() instanceof ServerLevel level)) return;
		for (String tag : projectile.entityTags()) {
			if (!tag.startsWith(WEAPON_TAG_PREFIX)) continue;
			DynamicContentDefinition definition = DynamicContentCatalog.find(
					com.yeyito.littlechemistry.LittleChemistry.id(tag.substring(WEAPON_TAG_PREFIX.length())));
			if (definition == null) return;
			DynamicBehaviorRegistry.projectileImpact(definition, level, projectile, hit);
			return;
		}
	}

	static Component name(ItemStack stack) {
		DynamicContentDefinition definition = definition(stack);
		return definition == null ? Component.literal("Unresolved Little Chemistry Item")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	static void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
			Consumer<Component> builder, TooltipFlag flag) {
		DynamicContentDefinition definition = definition(stack);
		if (definition != null && !definition.description().isBlank()) {
			DynamicTooltipText.appendWrapped(builder, definition.description(), ChatFormatting.GRAY);
		}
		if (definition == null || definition.item() == null) return;
		switch (definition.item().craftingUse()) {
			case KEEP -> builder.accept(Component.literal("Not consumed when used in crafting")
					.withStyle(ChatFormatting.DARK_GRAY));
			case DAMAGE -> builder.accept(Component.literal("Costs 1 durability when used in crafting")
					.withStyle(ChatFormatting.DARK_GRAY));
			case CONSUME -> {
			}
		}
		if (definition.item().fuelBurnTicks() > 0) {
			builder.accept(Component.translatable("tooltip.little_chemistry.furnace_fuel",
					formatDecimal(definition.item().fuelBurnTicks() / 20.0),
					formatDecimal(definition.item().fuelBurnTicks() / 200.0))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static @Nullable DynamicContentDefinition definition(ItemStack stack) {
		return stack.getItem() instanceof DynamicItemCarrier ? DynamicContentObjects.definition(stack) : null;
	}

	private static boolean supports(DynamicContentDefinition definition, DynamicBehaviorCapability capability) {
		return DynamicBehaviorSource.supports(definition.behaviorSource(), capability);
	}

	private static String formatDecimal(double value) {
		String formatted = String.format(Locale.ROOT, "%.2f", value);
		return formatted.replaceFirst("\\.?0+$", "");
	}
}
