package com.yeyito.littlechemistry.content;

import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorRegistry;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.behavior.DynamicEntityState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Registered ground-mob carrier whose identity, mechanics, model, and behavior come from a runtime definition. */
public class DynamicCarrierEntity extends PathfinderMob {
	private static final String SAVE_CONTENT = "LittleChemistryContent";
	private static final String SAVE_STATE = "LittleChemistryState";
	private static final String SAVE_INITIALIZED = "LittleChemistryInitialized";
	private static final String SAVE_DEFINITION_SEED = "LittleChemistryDefinitionSeed";
	private static final EntityDataAccessor<String> CONTENT_NAME = SynchedEntityData.defineId(
			DynamicCarrierEntity.class, EntityDataSerializers.STRING);

	private final DynamicEntityState dynamicState = new DynamicEntityState();
	private boolean behaviorInitialized;
	private boolean deathCallbackFired;
	private DynamicContentDefinition appliedDefinition;
	private long definitionSeed;

	public DynamicCarrierEntity(EntityType<? extends DynamicCarrierEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FLYING_SPEED, 0.4)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.ARMOR, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override public boolean canUse() { return canFight() && super.canUse(); }
			@Override public boolean canContinueToUse() { return canFight() && super.canContinueToUse(); }
		});
		registerMovementGoal();
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override public boolean canUse() { return canRetaliate() && super.canUse(); }
		});
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10,
				true, false, (target, level) -> isHostile()));
	}

	protected void registerMovementGoal() {
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CONTENT_NAME, "");
	}

	public void setDefinition(DynamicContentDefinition definition) {
		if (definition == null || definition.type() != DynamicContentType.ENTITY) {
			throw new IllegalArgumentException("Carrier entities require an entity definition");
		}
		if (!DynamicEntityObjects.matches(this, definition.entity())) {
			throw new IllegalArgumentException("Entity definition does not match this fixed carrier family");
		}
		entityData.set(CONTENT_NAME, definition.name());
		definitionSeed = definition.textureSeed();
		appliedDefinition = definition;
		applyDefinition(definition, true);
	}

	public String contentName() {
		return entityData.get(CONTENT_NAME);
	}

	public @Nullable Identifier contentId() {
		String name = contentName();
		return name.isEmpty() ? null : LittleChemistry.id(name);
	}

	public @Nullable DynamicContentDefinition definition() {
		DynamicContentDefinition definition = DynamicContentCatalog.find(contentName());
		return definition != null && definition.type() == DynamicContentType.ENTITY ? definition : null;
	}

	public DynamicEntityState dynamicState() {
		return dynamicState;
	}

	/** Marks a newly spawned carrier initialized and invokes the generated spawn callback exactly once. */
	public void initializeBehavior() {
		if (behaviorInitialized || !(level() instanceof ServerLevel serverLevel)) return;
		DynamicContentDefinition definition = reconcileDefinition(false);
		if (definition == null) return;
		behaviorInitialized = true;
		DynamicBehaviorRegistry.entitySpawned(definition, serverLevel, this, dynamicState);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (CONTENT_NAME.equals(accessor)) {
			appliedDefinition = null;
			reconcileDefinition(false);
			refreshDimensions();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel serverLevel)) return;
		DynamicContentDefinition definition = reconcileDefinition(false);
		if (definition == null) {
			DynamicContentManager manager = DynamicContentManager.active();
			if (!contentName().isEmpty() && manager != null && manager.belongsTo(serverLevel.getServer())) discard();
			return;
		}
		if (definition.entity().disposition() == DynamicEntityDisposition.HOSTILE
				&& serverLevel.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			discard();
			return;
		}
		if (!behaviorInitialized) initializeBehavior();
		if (DynamicBehaviorSource.supports(definition.behaviorSource(), DynamicBehaviorCapability.ENTITY_TICK)) {
			DynamicBehaviorRegistry.entityTick(definition, serverLevel, this, dynamicState);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		DynamicContentDefinition definition = definition();
		if (definition != null && player instanceof ServerPlayer serverPlayer
				&& level() instanceof ServerLevel serverLevel) {
			InteractionResult result = DynamicBehaviorRegistry.entityInteract(
					definition, serverLevel, this, dynamicState, serverPlayer, hand, player.getItemInHand(hand));
			if (result != InteractionResult.PASS) return result;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean accepted = super.hurtServer(level, source, amount);
		DynamicContentDefinition definition = definition();
		if (accepted && definition != null) {
			DynamicBehaviorRegistry.entityHurt(definition, level, this, dynamicState, source, amount);
		}
		return accepted;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean accepted = super.doHurtTarget(level, target);
		DynamicContentDefinition definition = definition();
		if (accepted && definition != null) {
			DynamicBehaviorRegistry.entityAttack(definition, level, this, dynamicState, target);
		}
		return accepted;
	}

	@Override
	public void die(DamageSource source) {
		DynamicContentDefinition definition = definition();
		if (!deathCallbackFired && definition != null && level() instanceof ServerLevel serverLevel) {
			deathCallbackFired = true;
			DynamicBehaviorRegistry.entityDeath(definition, serverLevel, this, dynamicState, source);
		}
		super.die(source);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		DynamicContentDefinition definition = definition();
		if (definition == null) return;
		for (DynamicEntityDrop drop : definition.entity().drops()) {
			if (random.nextDouble() > drop.chance()) continue;
			var item = BuiltInRegistries.ITEM.getOptional(drop.item()).orElse(null);
			if (item == null) continue;
			int count = drop.minimum() + (drop.maximum() == drop.minimum()
					? 0 : random.nextInt(drop.maximum() - drop.minimum() + 1));
			if (count > 0) spawnAtLocation(level, new ItemStack(item, count));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return sound(definition() == null ? null : definition().entity().ambientSound(), super.getAmbientSound());
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return sound(definition() == null ? null : definition().entity().hurtSound(), super.getHurtSound(source));
	}

	@Override
	protected SoundEvent getDeathSound() {
		return sound(definition() == null ? null : definition().entity().deathSound(), super.getDeathSound());
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		DynamicContentDefinition definition = definition();
		return definition == null ? super.getDefaultDimensions(pose) : EntityDimensions.scalable(
				definition.entity().width(), definition.entity().height()).withEyeHeight(definition.entity().eyeHeight());
	}

	@Override
	public boolean fireImmune() {
		DynamicContentDefinition definition = definition();
		return definition != null ? definition.entity().fireImmune() : super.fireImmune();
	}

	@Override
	public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
		DynamicContentDefinition definition = definition();
		return definition != null && definition.entity().disposition() != DynamicEntityDisposition.PASSIVE
				&& super.canAttack(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null && !canFight()) return;
		super.setTarget(target);
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public Component getName() {
		DynamicContentDefinition definition = definition();
		return definition == null ? Component.literal("Unresolved Little Chemistry Entity")
				: Component.literal(definition.displayName()).withStyle(definition.rarityTier().color());
	}

	@Override
	public ItemStack getPickResult() {
		DynamicContentDefinition definition = definition();
		return definition == null ? ItemStack.EMPTY : DynamicContentObjects.createStack(definition);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString(SAVE_CONTENT, contentName());
		output.putString(SAVE_STATE, dynamicState.encode());
		output.putBoolean(SAVE_INITIALIZED, behaviorInitialized);
		output.putLong(SAVE_DEFINITION_SEED, definitionSeed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String savedName = input.getStringOr(SAVE_CONTENT, "");
		String name = validContentName(savedName) ? savedName : "";
		entityData.set(CONTENT_NAME, name);
		try {
			dynamicState.decode(input.getStringOr(SAVE_STATE, ""));
		} catch (RuntimeException corrupt) {
			LittleChemistry.LOGGER.warn("Discarded corrupt generated-entity state for '{}'", name, corrupt);
			dynamicState.decode("");
		}
		behaviorInitialized = input.getBooleanOr(SAVE_INITIALIZED, !name.isEmpty());
		definitionSeed = input.getLongOr(SAVE_DEFINITION_SEED, 0L);
		appliedDefinition = null;
		reconcileDefinition(false);
		refreshDimensions();
	}

	private boolean isHostile() {
		DynamicContentDefinition definition = definition();
		return definition != null && definition.entity().disposition() == DynamicEntityDisposition.HOSTILE;
	}

	private boolean canRetaliate() {
		DynamicContentDefinition definition = definition();
		return definition != null && definition.entity().disposition() != DynamicEntityDisposition.PASSIVE;
	}

	private boolean canFight() {
		return canRetaliate();
	}

	private @Nullable DynamicContentDefinition reconcileDefinition(boolean restoreHealth) {
		DynamicContentDefinition definition = definition();
		if (definition == null) return null;
		if (!level().isClientSide() && definitionSeed != 0L && definitionSeed != definition.textureSeed()) {
			LittleChemistry.LOGGER.warn("Discarding stale generated entity '{}' after its definition name was reused",
					definition.name());
			discard();
			return null;
		}
		if (!DynamicEntityObjects.matches(this, definition.entity())) {
			if (!level().isClientSide()) {
				LittleChemistry.LOGGER.error("Discarding generated entity '{}' bound to an incompatible carrier type",
						definition.name());
				discard();
			}
			return null;
		}
		if (appliedDefinition != definition) {
			applyDefinition(definition, restoreHealth);
			appliedDefinition = definition;
			if (definition.entity().disposition() == DynamicEntityDisposition.PASSIVE) super.setTarget(null);
		}
		return definition;
	}

	private void applyDefinition(DynamicContentDefinition definition, boolean restoreHealth) {
		DynamicEntityProperties properties = definition.entity();
		setAttribute(Attributes.MAX_HEALTH, properties.maxHealth());
		setAttribute(Attributes.MOVEMENT_SPEED, properties.movementSpeed());
		setAttribute(Attributes.FLYING_SPEED, properties.movementSpeed());
		setAttribute(Attributes.ATTACK_DAMAGE, properties.attackDamage());
		setAttribute(Attributes.ARMOR, properties.armor());
		setAttribute(Attributes.KNOCKBACK_RESISTANCE, properties.knockbackResistance());
		setAttribute(Attributes.FOLLOW_RANGE, properties.followRange());
		xpReward = properties.experienceReward();
		if (restoreHealth) setHealth(getMaxHealth());
		refreshDimensions();
	}

	private void setAttribute(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private static SoundEvent sound(@Nullable Identifier id, @Nullable SoundEvent fallback) {
		if (id == null) return fallback;
		return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(fallback);
	}

	private static boolean validContentName(String name) {
		return name != null && name.matches("[a-z0-9_]{1,64}");
	}
}
