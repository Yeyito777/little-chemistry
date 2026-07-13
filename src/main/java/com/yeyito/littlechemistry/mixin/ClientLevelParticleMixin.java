package com.yeyito.littlechemistry.mixin;

import com.yeyito.littlechemistry.client.DynamicParticleTextures;
import com.yeyito.littlechemistry.client.RuntimeTextureParticle;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
	private void littleChemistry$rememberRemovedDynamicBlock(BlockPos position, BlockState newState,
			int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> callback) {
		ClientLevel level = (ClientLevel)(Object)this;
		if (level.getBlockState(position).is(DynamicContentObjects.BLOCK)) {
			DynamicParticleTextures.textureFor(level, position);
		}
	}

	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$dynamicDestroyParticles(BlockPos position, BlockState state, CallbackInfo callback) {
		if (!state.is(DynamicContentObjects.BLOCK)) return;
		ClientLevel level = (ClientLevel)(Object)this;
		String textureHash = DynamicParticleTextures.textureFor(level, position);
		if (textureHash != null) spawnDestroyParticles(level, position, state, textureHash);
		callback.cancel();
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void littleChemistry$dynamicHitParticles(BlockPos position, Direction direction, CallbackInfo callback) {
		ClientLevel level = (ClientLevel)(Object)this;
		BlockState state = level.getBlockState(position);
		if (!state.is(DynamicContentObjects.BLOCK)) return;
		String textureHash = DynamicParticleTextures.textureFor(level, position);
		if (textureHash != null) spawnHitParticle(level, position, state, direction, textureHash);
		callback.cancel();
	}

	private static void spawnDestroyParticles(ClientLevel level, BlockPos position, BlockState state, String textureHash) {
		VoxelShape shape = state.getShape(level, position);
		if (shape.isEmpty()) shape = Shapes.block();
		shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
			double widthX = Math.min(1.0, x2 - x1);
			double widthY = Math.min(1.0, y2 - y1);
			double widthZ = Math.min(1.0, z2 - z1);
			int countX = Math.max(2, Mth.ceil(widthX / 0.25));
			int countY = Math.max(2, Mth.ceil(widthY / 0.25));
			int countZ = Math.max(2, Mth.ceil(widthZ / 0.25));
			for (int xx = 0; xx < countX; xx++) {
				for (int yy = 0; yy < countY; yy++) {
					for (int zz = 0; zz < countZ; zz++) {
						double relX = (xx + 0.5) / countX;
						double relY = (yy + 0.5) / countY;
						double relZ = (zz + 0.5) / countZ;
						Minecraft.getInstance().particleEngine.add(RuntimeTextureParticle.block(
								level,
								position.getX() + relX * widthX + x1,
								position.getY() + relY * widthY + y1,
								position.getZ() + relZ * widthZ + z1,
								relX - 0.5, relY - 0.5, relZ - 0.5,
								textureHash
						));
					}
				}
			}
		});
	}

	private static void spawnHitParticle(ClientLevel level, BlockPos position, BlockState state,
			Direction direction, String textureHash) {
		VoxelShape voxelShape = state.getShape(level, position);
		if (voxelShape.isEmpty()) voxelShape = Shapes.block();
		AABB shape = voxelShape.bounds();
		double xp = position.getX() + level.getRandom().nextDouble() * Math.max(0.0, shape.getXsize() - 0.2) + 0.1 + shape.minX;
		double yp = position.getY() + level.getRandom().nextDouble() * Math.max(0.0, shape.getYsize() - 0.2) + 0.1 + shape.minY;
		double zp = position.getZ() + level.getRandom().nextDouble() * Math.max(0.0, shape.getZsize() - 0.2) + 0.1 + shape.minZ;
		if (direction == Direction.DOWN) yp = position.getY() + shape.minY - 0.1;
		if (direction == Direction.UP) yp = position.getY() + shape.maxY + 0.1;
		if (direction == Direction.NORTH) zp = position.getZ() + shape.minZ - 0.1;
		if (direction == Direction.SOUTH) zp = position.getZ() + shape.maxZ + 0.1;
		if (direction == Direction.WEST) xp = position.getX() + shape.minX - 0.1;
		if (direction == Direction.EAST) xp = position.getX() + shape.maxX + 0.1;
		Minecraft.getInstance().particleEngine.add(
				RuntimeTextureParticle.block(level, xp, yp, zp, 0, 0, 0, textureHash).setPower(0.2F).scale(0.6F));
	}
}
