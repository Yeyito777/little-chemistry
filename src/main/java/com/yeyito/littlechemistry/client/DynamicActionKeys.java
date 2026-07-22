package com.yeyito.littlechemistry.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yeyito.littlechemistry.LittleChemistry;
import com.yeyito.littlechemistry.network.DynamicActionInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Fixed, remappable client controls that generated behavior may assign semantic meaning to at runtime. */
final class DynamicActionKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			LittleChemistry.id("generated_actions"));
	private static KeyMapping primary;
	private static KeyMapping secondary;
	private static KeyMapping ascend;
	private static KeyMapping descend;
	private static KeyMapping modeSwitch;
	private static int sentMask = -1;

	private DynamicActionKeys() {
	}

	static void register() {
		primary = key("key.little_chemistry.generated.primary", GLFW.GLFW_KEY_R, 0);
		secondary = key("key.little_chemistry.generated.secondary", GLFW.GLFW_KEY_V, 1);
		ascend = key("key.little_chemistry.generated.ascend", GLFW.GLFW_KEY_SPACE, 2);
		descend = key("key.little_chemistry.generated.descend", GLFW.GLFW_KEY_C, 3);
		modeSwitch = key("key.little_chemistry.generated.mode_switch", GLFW.GLFW_KEY_M, 4);
	}

	static void tick(Minecraft client) {
		if (client.player == null || client.getConnection() == null
				|| !ClientPlayNetworking.canSend(DynamicActionInputPayload.TYPE)) {
			sentMask = -1;
			return;
		}
		int mask = (primary.isDown() ? 1 : 0) | (secondary.isDown() ? 2 : 0)
				| (ascend.isDown() ? 4 : 0) | (descend.isDown() ? 8 : 0)
				| (modeSwitch.isDown() ? 16 : 0);
		if (mask == sentMask) return;
		ClientPlayNetworking.send(new DynamicActionInputPayload(mask));
		sentMask = mask;
	}

	static void reset() {
		sentMask = -1;
	}

	private static KeyMapping key(String translation, int key, int order) {
		return new KeyMapping(translation, InputConstants.Type.KEYSYM, key, CATEGORY, order);
	}
}
