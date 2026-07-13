package com.yeyito.littlechemistry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.data.AtlasIds;

public final class RuntimeTextureParticle extends SingleQuadParticle {
	private final float uo;
	private final float vo;
	private final Layer layer;

	private RuntimeTextureParticle(ClientLevel level, double x, double y, double z,
			double xa, double ya, double za, String textureHash, boolean terrain) {
		super(level, x, y, z, xa, ya, za,
				Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite());
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.xd += xa;
		this.yd += ya;
		this.zd += za;
		this.gravity = 1.0F;
		this.quadSize /= 2.0F;
		if (terrain) {
			this.rCol = 0.6F;
			this.gCol = 0.6F;
			this.bCol = 0.6F;
		}
		this.uo = this.random.nextFloat() * 3.0F;
		this.vo = this.random.nextFloat() * 3.0F;
		this.layer = new Layer(true, RuntimeTextureStore.texture(textureHash), RenderPipelines.TRANSLUCENT_PARTICLE);
	}

	public static RuntimeTextureParticle item(ClientLevel level, double x, double y, double z,
			double xa, double ya, double za, String textureHash) {
		return new RuntimeTextureParticle(level, x, y, z, xa, ya, za, textureHash, false);
	}

	public static RuntimeTextureParticle block(ClientLevel level, double x, double y, double z,
			double xa, double ya, double za, String textureHash) {
		return new RuntimeTextureParticle(level, x, y, z, xa, ya, za, textureHash, true);
	}

	@Override
	protected float getU0() {
		return (uo + 1.0F) / 4.0F;
	}

	@Override
	protected float getU1() {
		return uo / 4.0F;
	}

	@Override
	protected float getV0() {
		return vo / 4.0F;
	}

	@Override
	protected float getV1() {
		return (vo + 1.0F) / 4.0F;
	}

	@Override
	public Layer getLayer() {
		return layer;
	}
}
