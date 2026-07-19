package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicParticleDefinition;
import com.yeyito.littlechemistry.particle.DynamicParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import java.util.List;

/** A generic runtime particle driven entirely by a synchronized generated definition. */
public final class RuntimeCustomParticle extends SingleQuadParticle {
	private static final int MAX_PENDING_TICKS = 100;
	private static final Layer PENDING_LAYER = new Layer(
			true, MissingTextureAtlasSprite.getLocation(), RenderPipelines.TRANSLUCENT_PARTICLE);

	private final DynamicParticleOptions options;
	private DynamicParticleDefinition definition;
	private List<Layer> layers = List.of(PENDING_LAYER);
	private int pendingTicks;

	public RuntimeCustomParticle(ClientLevel level, DynamicParticleOptions options,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		super(level, x, y, z,
				Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite());
		this.options = options;
		setParticleSpeed(velocityX, velocityY, velocityZ);
		this.hasPhysics = false;
		this.alpha = 0.0F;
		resolve();
	}

	@Override
	public void tick() {
		if (definition == null) {
			this.xo = this.x;
			this.yo = this.y;
			this.zo = this.z;
			this.oRoll = this.roll;
			if (resolve()) return;
			if (++pendingTicks >= MAX_PENDING_TICKS) remove();
			return;
		}

		this.oRoll = this.roll;
		super.tick();
		if (!removed) {
			this.roll = this.oRoll + definition.spin();
			applyColor(progress(0.0F));
		}
	}

	@Override
	public float getQuadSize(float partialTick) {
		return definition == null ? 0.0F
				: Mth.lerp(progress(partialTick), definition.startSize(), definition.endSize());
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return definition != null && definition.emissive()
				? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	@Override
	protected float getU0() {
		return 0.0F;
	}

	@Override
	protected float getU1() {
		return 1.0F;
	}

	@Override
	protected float getV0() {
		return 0.0F;
	}

	@Override
	protected float getV1() {
		return 1.0F;
	}

	@Override
	protected Layer getLayer() {
		if (definition == null) return PENDING_LAYER;
		int frame = age / definition.frameTicks();
		if (definition.loop()) frame %= layers.size();
		else frame = Math.min(frame, layers.size() - 1);
		return layers.get(frame);
	}

	private boolean resolve() {
		var content = DynamicContentCatalog.find(options.contentId());
		if (content == null) return false;
		DynamicParticleDefinition resolved = content.customParticle(options.particleId());
		if (resolved == null || !RuntimeTextureStore.areTexturesReady(resolved.textureHashes())) return false;

		definition = resolved;
		layers = definition.frames().stream()
				.map(frame -> new Layer(true, RuntimeTextureStore.texture(frame.textureHash()),
						RenderPipelines.TRANSLUCENT_PARTICLE))
				.toList();
		setLifetime(definition.lifetimeTicks());
		this.age = 0;
		this.gravity = definition.gravity();
		this.friction = definition.friction();
		this.hasPhysics = definition.collision();
		applyColor(0.0F);
		return true;
	}

	private float progress(float partialTick) {
		if (lifetime <= 1) return 0.0F;
		return Mth.clamp((age + partialTick) / (lifetime - 1.0F), 0.0F, 1.0F);
	}

	private void applyColor(float progress) {
		this.rCol = Mth.lerp(progress, definition.startRed(), definition.endRed());
		this.gCol = Mth.lerp(progress, definition.startGreen(), definition.endGreen());
		this.bCol = Mth.lerp(progress, definition.startBlue(), definition.endBlue());
		this.alpha = Mth.lerp(progress, definition.startAlpha(), definition.endAlpha());
	}
}
