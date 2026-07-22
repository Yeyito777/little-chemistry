package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class DynamicSpecialItemRenderer implements SpecialModelRenderer<DynamicSpecialItemRenderer.Argument> {
	@Override
	public void submit(Argument argument, PoseStack poseStack, SubmitNodeCollector nodes, int light, int overlay,
			boolean hasFoil, int outlineColor) {
		if (argument == null) {
			return;
		}
		boolean[] opaquePixels = RuntimeTextureStore.opaquePixels(argument.textureHash());
		if (argument.block() && argument.blockModel() != null) {
			int order = 0;
			for (var texture : argument.blockModel().textures()) {
				String textureId = texture.id();
				nodes.order(order++).submitCustomGeometry(poseStack,
						RenderTypes.entityCutout(RuntimeTextureStore.texture(texture.hash())),
						(pose, vertices) -> renderBlockModel(argument, textureId, pose, vertices, light, overlay));
			}
		} else {
			Identifier texture = RuntimeTextureStore.texture(argument.textureHash());
			var baseRenderType = argument.block()
					? RenderTypes.entityCutout(texture)
					: RenderTypes.entityCutoutCull(texture);
			nodes.order(0).submitCustomGeometry(poseStack, baseRenderType, (pose, vertices) -> {
				if (argument.block()) renderBlock(argument.blockShape(), pose, vertices, light, overlay);
				else DynamicGeometry.item(pose, vertices, light, overlay, opaquePixels);
			});
		}
		if (hasFoil) {
			int glintOrder = argument.blockModel() == null ? 1 : argument.blockModel().textures().size();
			nodes.order(glintOrder).submitCustomGeometry(poseStack, RenderTypes.entityGlint(), (pose, vertices) -> {
				if (argument.block() && argument.blockModel() != null) renderBlockModel(argument, null, pose, vertices, light, overlay);
				else if (argument.block()) renderBlock(argument.blockShape(), pose, vertices, light, overlay);
				else DynamicGeometry.item(pose, vertices, light, overlay, opaquePixels);
			});
		}
	}

	private static void renderBlockModel(Argument argument, String textureFilter, PoseStack.Pose pose,
			com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay) {
		DynamicBlockModelRenderer.render(argument.blockShape(), argument.blockModel(), textureFilter,
				pose, vertices, light, overlay, true, true, true, true);
	}

	private static void renderBlock(DynamicBlockShape shape, PoseStack.Pose pose,
			com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay) {
		switch (shape) {
			case SLAB -> DynamicGeometry.slab(pose, vertices, light, overlay);
			case STAR -> DynamicGeometry.star(pose, vertices, light, overlay);
			case FENCE -> DynamicGeometry.fence(pose, vertices, light, overlay, true, true, true, true);
			default -> DynamicGeometry.cube(pose, vertices, light, overlay);
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0, 0, 0));
		output.accept(new Vector3f(1, 1, 1));
	}

	@Override
	public Argument extractArgument(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition == null || !RuntimeTextureStore.areTexturesReady(definition.renderTextureHashes())) {
			// GUI items are rasterized into a model-identity cache. Returning no argument while the
			// runtime texture is loading prevents the missing texture from being cached permanently;
			// the real argument changes the identity and redraws the slot as soon as registration finishes.
			return null;
		}
		boolean block = stack.is(DynamicContentObjects.BLOCK_ITEM);
		return new Argument(definition.textureHash(), block,
				block && definition.block() != null ? definition.block().shape() : DynamicBlockShape.FULL_CUBE,
				block ? definition.blockModel() : null);
	}

	public record Argument(String textureHash, boolean block, DynamicBlockShape blockShape, DynamicBlockModel blockModel) {
		@Override
		public boolean equals(Object other) {
			return this == other || other instanceof Argument argument
					&& block == argument.block
					&& blockShape == argument.blockShape
					&& textureHash.equals(argument.textureHash)
					&& blockModel == argument.blockModel;
		}

		@Override
		public int hashCode() {
			int result = textureHash.hashCode();
			result = 31 * result + Boolean.hashCode(block);
			result = 31 * result + blockShape.hashCode();
			return 31 * result + System.identityHashCode(blockModel);
		}
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Argument> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public DynamicSpecialItemRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new DynamicSpecialItemRenderer();
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<Argument>> type() {
			return MAP_CODEC;
		}
	}
}
