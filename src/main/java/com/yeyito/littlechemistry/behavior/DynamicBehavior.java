package com.yeyito.littlechemistry.behavior;

import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Server-side behavior implemented in full by a separately loaded generated class. */
public interface DynamicBehavior {
	InteractionResult useAir(DynamicItemUseContext context);

	InteractionResult useOnBlock(DynamicBlockUseContext context);

	InteractionResult interactLivingEntity(DynamicEntityUseContext context);

	void inventoryTick(ServerLevel level, Entity owner, @Nullable EquipmentSlot slot,
			ItemStack stack, DynamicContentDefinition definition);

	void postHurtEnemy(ServerLevel level, LivingEntity attacker, LivingEntity target,
			ItemStack stack, DynamicContentDefinition definition);

	void mineBlock(ServerLevel level, LivingEntity miner, BlockPos position, BlockState state,
			ItemStack stack, DynamicContentDefinition definition);

	ItemStack finishUsing(ServerLevel level, LivingEntity consumer, ItemStack originalStack,
			ItemStack resultStack, DynamicContentDefinition definition);

	void crafted(ServerLevel level, ServerPlayer player, ItemStack stack,
			DynamicContentDefinition definition);

	InteractionResult usePlacedBlock(DynamicPlacedBlockUseContext context);

	void attackPlacedBlock(ServerLevel level, ServerPlayer player, BlockPos position,
			BlockState state, DynamicContentDefinition definition);

	void placedBlock(ServerLevel level, @Nullable LivingEntity placer, BlockPos position,
			BlockState state, ItemStack placedFrom, DynamicContentDefinition definition);

	void brokenBlock(ServerLevel level, ServerPlayer player, BlockPos position, BlockState state,
			ItemStack tool, DynamicContentDefinition definition);

	void stepOnBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			DynamicContentDefinition definition);

	void fallOnBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			double fallDistance, DynamicContentDefinition definition);

	void entityInsideBlock(ServerLevel level, BlockPos position, BlockState state, Entity entity,
			InsideBlockEffectApplier effects, boolean isEntry, DynamicContentDefinition definition);

	void randomTickBlock(ServerLevel level, BlockPos position, BlockState state, RandomSource random,
			DynamicContentDefinition definition);

	void scheduledTickBlock(ServerLevel level, BlockPos position, BlockState state, RandomSource random,
			DynamicContentDefinition definition);

	void neighborChangedBlock(ServerLevel level, BlockPos position, BlockState state, Block neighbor,
			@Nullable Orientation orientation, boolean movedByPiston, DynamicContentDefinition definition);

	void projectileHitBlock(ServerLevel level, BlockPos position, BlockState state,
			BlockHitResult hit, Projectile projectile, DynamicContentDefinition definition);
}
