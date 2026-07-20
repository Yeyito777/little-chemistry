package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.particle.DynamicParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

public final class DynamicCarrierBlock extends Block implements EntityBlock {
	public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light_level", 0, 15);
	public static final EnumProperty<DynamicMaterial> MATERIAL = EnumProperty.create("material", DynamicMaterial.class);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public DynamicCarrierBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(LIGHT_LEVEL, 0)
				.setValue(MATERIAL, DynamicMaterial.STONE)
				.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIGHT_LEVEL, MATERIAL, FACING);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
		return new DynamicBlockEntity(position, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide() || type != DynamicContentObjects.BLOCK_ENTITY_TYPE) return null;
		return (serverLevel, position, blockState, blockEntity) -> DynamicBlockEntity.serverTick(
				(ServerLevel) serverLevel, position, blockState, (DynamicBlockEntity) blockEntity);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected SoundType getSoundType(BlockState state) {
		return state.getValue(MATERIAL).soundType();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level level, BlockPos position,
			Player player, InteractionHand hand, BlockHitResult hit) {
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && DynamicBehaviorSource.supports(
				definition.behaviorSource(), DynamicBehaviorCapability.USE_PLACED_BLOCK)) {
			if (level.isClientSide()) return InteractionResult.SUCCESS;
				if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
					InteractionResult result = DynamicBehaviorRegistry.usePlacedBlock(
							definition, serverLevel, serverPlayer, hand, heldStack, position, state, hit);
					if (result != InteractionResult.PASS) return result;
				}
			}
			if (definition != null && definition.workstation() != null) {
				return openWorkstation(level, position, player);
			}
			return super.useItemOn(heldStack, state, level, position, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
			Player player, BlockHitResult hit) {
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && DynamicBehaviorSource.supports(
				definition.behaviorSource(), DynamicBehaviorCapability.USE_PLACED_BLOCK)) {
			if (level.isClientSide()) return InteractionResult.SUCCESS;
				if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				InteractionResult result = DynamicBehaviorRegistry.usePlacedBlock(
						definition, serverLevel, serverPlayer, null, ItemStack.EMPTY, position, state, hit);
				if (result != InteractionResult.PASS) return result;
				}
			}
			if (definition != null && definition.workstation() != null) {
				return openWorkstation(level, position, player);
			}
			return super.useWithoutItem(state, level, position, player, hit);
		}

	private static InteractionResult openWorkstation(Level level, BlockPos position, Player player) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(position) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstation()) {
			serverPlayer.openMenu(workstation);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.FAIL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && definition.item() != null && definition.item().placement() != null) {
			return definition.item().placement().shape() == DynamicPlacedShape.TORCH
					? Shapes.box(6.0 / 16.0, 0, 6.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0, 10.0 / 16.0)
					: Shapes.box(1.0 / 16.0, 0, 1.0 / 16.0, 15.0 / 16.0, 1, 15.0 / 16.0);
		}
		if (definition == null || definition.block() == null) return Shapes.block();
		return switch (definition.block().shape()) {
			case FULL_CUBE, NO_COLLISION -> Shapes.block();
			case SLAB -> Shapes.box(0, 0, 0, 1, 0.5, 1);
			case STAR, CROSS -> Shapes.box(1.0 / 16.0, 0, 1.0 / 16.0, 15.0 / 16.0, 1, 15.0 / 16.0);
			case TORCH -> Shapes.box(7.0 / 16.0, 0, 7.0 / 16.0, 9.0 / 16.0, 10.0 / 16.0, 9.0 / 16.0);
			case FENCE -> fenceShape(level, position, 1.0);
			case CUSTOM -> customShape(definition.blockModel(), false, modelFacing(definition, state));
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
		DynamicContentDefinition definition = definition(level, position);
		return collisionShape(definition, state, level, position);
	}

	static VoxelShape collisionShape(DynamicContentDefinition definition, BlockState state,
			BlockGetter level, BlockPos position) {
		if (definition != null && definition.item() != null && definition.item().placement() != null) return Shapes.empty();
		if (definition == null || definition.block() == null) return Shapes.block();
		return switch (definition.block().shape()) {
			case FULL_CUBE -> Shapes.block();
			case SLAB -> Shapes.box(0, 0, 0, 1, 0.5, 1);
			case NO_COLLISION, STAR, CROSS, TORCH -> Shapes.empty();
			case FENCE -> fenceShape(level, position, 1.5);
			case CUSTOM -> customShape(definition.blockModel(), true, modelFacing(definition, state));
		};
	}

	static VoxelShape customShape(DynamicBlockModel model, boolean collisionOnly, Direction facing) {
		if (model == null) return collisionOnly ? Shapes.empty() : Shapes.block();
		VoxelShape result = Shapes.empty();
		for (DynamicBlockModelElement element : model.elements()) {
			if (collisionOnly && !element.collision()) continue;
			result = Shapes.or(result, Shapes.box(
					element.fromX() / 16.0, element.fromY() / 16.0, element.fromZ() / 16.0,
					element.toX() / 16.0, element.toY() / 16.0, element.toZ() / 16.0));
		}
		return switch (facing) {
			case EAST -> Shapes.rotate(result, Rotation.CLOCKWISE_90.rotation());
			case SOUTH -> Shapes.rotate(result, Rotation.CLOCKWISE_180.rotation());
			case WEST -> Shapes.rotate(result, Rotation.COUNTERCLOCKWISE_90.rotation());
			default -> result;
		};
	}

	private static Direction modelFacing(DynamicContentDefinition definition, BlockState state) {
		return definition.block().directional() ? state.getValue(FACING) : Direction.NORTH;
	}

	/** Maps a model-local direction to the world direction when its north face points toward {@code facing}. */
	public static Direction orientFromNorth(Direction direction, Direction facing) {
		if (direction.getAxis().isVertical()) return direction;
		return switch (facing) {
			case EAST -> direction.getClockWise();
			case SOUTH -> direction.getOpposite();
			case WEST -> direction.getCounterClockWise();
			default -> direction;
		};
	}

	public static boolean connectsFence(BlockGetter level, BlockPos position, Direction direction) {
		if (direction.getAxis().isVertical()) return false;
		BlockPos neighborPosition = position.relative(direction);
		DynamicContentDefinition neighborDefinition = definition(level, neighborPosition);
		if (neighborDefinition != null && neighborDefinition.block() != null
				&& neighborDefinition.block().shape() == DynamicBlockShape.FENCE) {
			return true;
		}
		BlockState neighbor = level.getBlockState(neighborPosition);
		if (neighbor.is(BlockTags.FENCES)) return true;
		if (neighbor.getBlock() instanceof FenceGateBlock) {
			return FenceGateBlock.connectsToDirection(neighbor, direction.getOpposite());
		}
		return neighbor.isFaceSturdy(level, neighborPosition, direction.getOpposite());
	}

	private static VoxelShape fenceShape(BlockGetter level, BlockPos position, double height) {
		VoxelShape shape = Shapes.box(6.0 / 16.0, 0, 6.0 / 16.0, 10.0 / 16.0, height, 10.0 / 16.0);
		if (connectsFence(level, position, Direction.NORTH)) {
			shape = Shapes.or(shape, Shapes.box(7.0 / 16.0, 0, 0, 9.0 / 16.0, height, 8.0 / 16.0));
		}
		if (connectsFence(level, position, Direction.SOUTH)) {
			shape = Shapes.or(shape, Shapes.box(7.0 / 16.0, 0, 8.0 / 16.0, 9.0 / 16.0, height, 1));
		}
		if (connectsFence(level, position, Direction.WEST)) {
			shape = Shapes.or(shape, Shapes.box(0, 0, 7.0 / 16.0, 8.0 / 16.0, height, 9.0 / 16.0));
		}
		if (connectsFence(level, position, Direction.EAST)) {
			shape = Shapes.or(shape, Shapes.box(8.0 / 16.0, 0, 7.0 / 16.0, 1, height, 9.0 / 16.0));
		}
		return shape;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos position, Direction direction) {
		DynamicContentDefinition definition = definition(level, position);
		return definition == null || definition.block() == null ? 0 : definition.block().redstonePower();
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos position, Direction direction) {
		return getSignal(state, level, position, direction);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos position, Direction direction) {
		DynamicContentDefinition definition = definition(level, position);
		return definition == null || definition.block() == null ? 0 : definition.block().comparatorPower();
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos position) {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity workstation
				&& workstation.isWorkstationLocked()) return 0.0F;
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && definition.item() != null && definition.item().placement() != null) return 1.0F;
		if (definition == null || definition.block() == null) {
			return super.getDestroyProgress(state, player, level, position);
		}
		DynamicBlockProperties properties = definition.block();
		boolean correctTool = properties.preferredTool().matches(player.getMainHandItem());
		float speed = correctTool ? player.getDestroySpeed(state) : 1.0F;
		int divisor = !properties.requiresCorrectTool() || correctTool ? 30 : 100;
		return speed / properties.hardness() / divisor;
	}

	@Override
	public void playerDestroy(Level level, Player player, BlockPos position, BlockState state,
			BlockEntity blockEntity, ItemStack destroyedWith) {
		DynamicContentDefinition definition = blockEntity instanceof DynamicBlockEntity dynamic
				? DynamicContentCatalog.find(dynamic.contentId()) : null;
		if (definition == null || definition.block() == null || !definition.block().requiresCorrectTool()
				|| definition.block().preferredTool().matches(destroyedWith)) {
			super.playerDestroy(level, player, position, state, blockEntity, destroyedWith);
		} else {
			player.awardStat(Stats.BLOCK_MINED.get(this));
			player.causeFoodExhaustion(0.005F);
		}
		if (definition != null && level instanceof ServerLevel serverLevel
				&& player instanceof ServerPlayer serverPlayer) {
			DynamicBehaviorRegistry.brokenBlock(definition, serverLevel, serverPlayer, position, state, destroyedWith);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, position, state, placer, stack);
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.placedBlock(definition, serverLevel, placer, position, state, stack);
		}
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos position, Player player) {
		super.attack(state, level, position, player);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && level instanceof ServerLevel serverLevel
				&& player instanceof ServerPlayer serverPlayer) {
			DynamicBehaviorRegistry.attackPlacedBlock(definition, serverLevel, serverPlayer, position, state);
		}
	}

	@Override
	public void stepOn(Level level, BlockPos position, BlockState state, Entity entity) {
		super.stepOn(level, position, state, entity);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.stepOnBlock(definition, serverLevel, position, state, entity);
		}
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos position, Entity entity, double fallDistance) {
		super.fallOn(level, state, position, entity, fallDistance);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.fallOnBlock(definition, serverLevel, position, state, entity, fallDistance);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos position, Entity entity,
			InsideBlockEffectApplier effects, boolean isEntry) {
		super.entityInside(state, level, position, entity, effects, isEntry);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.entityInsideBlock(
					definition, serverLevel, position, state, entity, effects, isEntry);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
		super.randomTick(state, level, position, random);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null) {
			DynamicBehaviorRegistry.randomTickBlock(definition, level, position, state, random);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
		super.tick(state, level, position, random);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null) {
			DynamicBehaviorRegistry.scheduledTickBlock(definition, level, position, state, random);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbor,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, position, neighbor, orientation, movedByPiston);
		DynamicContentDefinition definition = definition(level, position);
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.neighborChangedBlock(
					definition, serverLevel, position, state, neighbor, orientation, movedByPiston);
		}
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		super.onProjectileHit(level, state, hit, projectile);
		DynamicContentDefinition definition = definition(level, hit.getBlockPos());
		if (definition != null && level instanceof ServerLevel serverLevel) {
			DynamicBehaviorRegistry.projectileHitBlock(
					definition, serverLevel, hit.getBlockPos(), state, hit, projectile);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
		DynamicContentDefinition definition = definition(level, position);
		if (definition == null || definition.block() == null) {
			return;
		}
		for (DynamicParticleEmitter emitter : definition.block().particles()) {
			if (random.nextDouble() >= emitter.chancePerTick()) {
				continue;
			}
			for (int index = 0; index < emitter.count(); index++) {
				double x = position.getX() + random.nextDouble();
				double y = position.getY() + (emitter.topSurface() ? 1.02 : random.nextDouble());
				double z = position.getZ() + random.nextDouble();
				double velocity = emitter.velocity();
				double velocityX = (random.nextDouble() - 0.5) * velocity;
				double velocityZ = (random.nextDouble() - 0.5) * velocity;
				if (emitter.custom()) {
					level.addParticle(DynamicParticleOptions.of(definition, emitter.customParticleId()),
							x, y, z, velocityX, velocity, velocityZ);
				} else {
					ParticleOptions particle = emitter.type().particle();
					level.addParticle(particle, x, y, z, velocityX, velocity, velocityZ);
				}
			}
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos position, BlockState state, boolean includeData) {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity blockEntity) {
			DynamicContentDefinition definition = DynamicContentCatalog.find(blockEntity.contentId());
			if (definition != null) return DynamicContentObjects.createStack(definition);
		}
		return new ItemStack(DynamicContentObjects.BLOCK_ITEM);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		DynamicContentDefinition definition = blockEntity instanceof DynamicBlockEntity dynamic
				? DynamicContentCatalog.find(dynamic.contentId()) : null;
		if (definition != null && definition.item() != null && definition.item().placement() != null) {
			return List.of(DynamicContentObjects.createStack(definition));
		}
		if (definition != null && definition.block() != null) {
			return DynamicBlockDropEvaluator.evaluate(definition, params);
		}
		return super.getDrops(state, params);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
		DynamicContentDefinition definition = definition(level, position);
		DynamicPlacementProperties placement = definition == null || definition.item() == null
				? null : definition.item().placement();
		if (placement == null) return true;
		BlockPos below = position.below();
		BlockState support = level.getBlockState(below);
		return placement.canPlaceOn(support, level, below);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos position, Direction directionToNeighbour, BlockPos neighbourPosition,
			BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == Direction.DOWN && !canSurvive(state, level, position)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, position, directionToNeighbour, neighbourPosition, neighbourState, random);
	}

	private static DynamicContentDefinition definition(BlockGetter level, BlockPos position) {
		if (level.getBlockEntity(position) instanceof DynamicBlockEntity blockEntity
				&& blockEntity.contentId() != null) {
			return DynamicContentCatalog.find(blockEntity.contentId());
		}
		return null;
	}

}
