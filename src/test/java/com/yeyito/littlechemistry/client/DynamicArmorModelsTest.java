package com.yeyito.littlechemistry.client;

import com.yeyito.littlechemistry.content.DynamicArmorAnchor;
import com.yeyito.littlechemistry.content.DynamicArmorGeometry;
import com.yeyito.littlechemistry.content.DynamicArmorGeometryPart;
import org.junit.jupiter.api.Test;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicArmorModelsTest {
	@Test
	void authoredCuboidsRemainAttachedToAnimatedHumanoidAnchors() {
		DynamicArmorGeometry geometry = new DynamicArmorGeometry(List.of(new DynamicArmorGeometryPart(
				"upper_ring", DynamicArmorAnchor.HEAD, 0, 0,
				-5, -9, -5, 10, 1, 10,
				0, 0, 0, 0, 0, 0, 0, false)));

		var selected = new HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState>(LayerDefinition.create(
				HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32).bakeRoot()) {
			@Override public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
				head.setPos(2, 3, 4);
			}
		};
		var adult = DynamicArmorModels.build(geometry, selected);

		assertTrue(adult.head.hasChild("little_chemistry_upper_ring"));
		assertFalse(adult.head.getChild("little_chemistry_upper_ring").isEmpty());
		adult.setupAnim(new net.minecraft.client.renderer.entity.state.HumanoidRenderState());
		assertTrue(adult.head.x == 2 && adult.head.y == 3 && adult.head.z == 4);
	}
}
