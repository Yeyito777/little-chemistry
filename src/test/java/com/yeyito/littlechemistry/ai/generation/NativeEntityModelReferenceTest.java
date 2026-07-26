package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeEntityModelReferenceTest {
	@Test
	void translatesInheritedPivotsCuboidsAndEveryNativeFaceUv() {
		String source = """
				public final class ExampleModel {
				  private static void common(PartDefinition root) {
				    PartDefinition body = root.addOrReplaceChild("body",
				        CubeListBuilder.create().texOffs(4, 8).addBox(-2.0F, -1.0F, -3.0F, 4.0F, 2.0F, 6.0F),
				        PartPose.offset(1.0F, 2.0F, 3.0F));
				    body.addOrReplaceChild("wing",
				        CubeListBuilder.create().texOffs(20, 4).mirror().addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F),
				        PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, (float)(Math.PI / 2)));
				  }
				  public static LayerDefinition createBodyLayer() {
				    MeshDefinition mesh = new MeshDefinition();
				    PartDefinition root = mesh.getRoot();
				    common(root);
				    return LayerDefinition.create(mesh, 64, 32);
				  }
				}
				""";
		JsonObject texture = JsonParser.parseString("""
				{"width":64,"height":32,"palette":["00000000"],"rows":["0"]}
				""").getAsJsonObject();

		JsonObject translated = NativeEntityModelReference.translate(
				"example.ExampleModel", "createBodyLayer", source, texture);

		assertEquals(64, translated.get("textureWidth").getAsInt());
		assertEquals(32, translated.get("textureHeight").getAsInt());
		assertEquals(2, translated.getAsJsonArray("parts").size());
		JsonObject body = translated.getAsJsonArray("parts").get(0).getAsJsonObject();
		assertEquals("root/body", body.get("path").getAsString());
		JsonObject bodyCube = body.getAsJsonArray("cubes").get(0).getAsJsonObject();
		assertEquals(6, bodyCube.getAsJsonObject("faces").size());
		assertEquals(4.0, bodyCube.getAsJsonObject("faces").getAsJsonObject("west")
				.getAsJsonArray("pixelUv").get(0).getAsDouble());
		assertEquals(2.5, bodyCube.getAsJsonObject("faces").getAsJsonObject("north")
				.getAsJsonArray("generatedUv").get(0).getAsDouble());
		JsonObject wing = translated.getAsJsonArray("parts").get(1).getAsJsonObject();
		assertEquals("root/body/wing", wing.get("path").getAsString());
		assertTrue(wing.getAsJsonArray("cubes").get(0).getAsJsonObject().get("mirrored").getAsBoolean());
		assertTrue(wing.getAsJsonArray("rotationRadians").get(2).getAsDouble() > 1.5);
	}
}
