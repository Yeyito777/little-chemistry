package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.yeyito.littlechemistry.content.DynamicContentDefinition;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
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
		Identifier texture = RuntimeTextureStore.texture(argument.textureHash());
		nodes.order(0).submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, vertices) -> {
			if (argument.block()) {
				DynamicGeometry.cube(pose, vertices, light, overlay);
			} else {
				DynamicGeometry.flat(pose, vertices, light, overlay);
			}
		});
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0, 0, 0));
		output.accept(new Vector3f(1, 1, 1));
	}

	@Override
	public Argument extractArgument(ItemStack stack) {
		DynamicContentDefinition definition = DynamicContentObjects.definition(stack);
		if (definition == null) {
			return null;
		}
		return new Argument(definition.textureHash(), stack.is(DynamicContentObjects.BLOCK_ITEM));
	}

	public record Argument(String textureHash, boolean block) {
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
