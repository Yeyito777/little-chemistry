package com.yeyito.littlechemistry.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source migration support for definitions created before every behavior method became mandatory. */
public final class DynamicBehaviorSource {
	private static final String CLASS_NAME = DynamicBehaviorCompiler.GENERATED_CLASS_NAME;
	private static final List<MethodSource> METHODS = List.of(
			new MethodSource("useAir", "public net.minecraft.world.InteractionResult useAir(com.yeyito.littlechemistry.behavior.DynamicItemUseContext context) { return net.minecraft.world.InteractionResult.PASS; }"),
			new MethodSource("useOnBlock", "public net.minecraft.world.InteractionResult useOnBlock(com.yeyito.littlechemistry.behavior.DynamicBlockUseContext context) { return net.minecraft.world.InteractionResult.PASS; }"),
			new MethodSource("interactLivingEntity", "public net.minecraft.world.InteractionResult interactLivingEntity(com.yeyito.littlechemistry.behavior.DynamicEntityUseContext context) { return net.minecraft.world.InteractionResult.PASS; }"),
			new MethodSource("inventoryTick", "public void inventoryTick(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("postHurtEnemy", "public void postHurtEnemy(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity attacker, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.item.ItemStack stack, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("mineBlock", "public void mineBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity miner, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack stack, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("finishUsing", "public net.minecraft.world.item.ItemStack finishUsing(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity consumer, net.minecraft.world.item.ItemStack originalStack, net.minecraft.world.item.ItemStack resultStack, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) { return resultStack; }"),
			new MethodSource("crafted", "public void crafted(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack stack, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("usePlacedBlock", "public net.minecraft.world.InteractionResult usePlacedBlock(com.yeyito.littlechemistry.behavior.DynamicPlacedBlockUseContext context) { return net.minecraft.world.InteractionResult.PASS; }"),
			new MethodSource("attackPlacedBlock", "public void attackPlacedBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("placedBlock", "public void placedBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity placer, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack placedFrom, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("brokenBlock", "public void brokenBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack tool, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("stepOnBlock", "public void stepOnBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.entity.Entity entity, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("fallOnBlock", "public void fallOnBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.entity.Entity entity, double fallDistance, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("entityInsideBlock", "public void entityInsideBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean isEntry, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("randomTickBlock", "public void randomTickBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.util.RandomSource random, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("scheduledTickBlock", "public void scheduledTickBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.util.RandomSource random, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("neighborChangedBlock", "public void neighborChangedBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.Block neighbor, net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}"),
			new MethodSource("projectileHitBlock", "public void projectileHitBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.phys.BlockHitResult hit, net.minecraft.world.entity.projectile.Projectile projectile, com.yeyito.littlechemistry.content.DynamicContentDefinition definition) {}")
	);

	private DynamicBehaviorSource() {
	}

	/** Adds explicit neutral implementations only while upgrading legacy persisted source. */
	public static String completeLegacySource(String source) {
		if (source == null || source.isBlank()) {
			source = "public final class " + CLASS_NAME
					+ " implements com.yeyito.littlechemistry.behavior.DynamicBehavior { public "
					+ CLASS_NAME + "() {} }";
		}
		String normalized = source.strip();
		String masked = maskNonCode(normalized);
		Matcher declaration = Pattern.compile("\\bclass\\s+" + Pattern.quote(CLASS_NAME) + "\\b").matcher(masked);
		if (!declaration.find()) throw new IllegalArgumentException("Legacy behavior does not declare " + CLASS_NAME);
		int openingBrace = masked.indexOf('{', declaration.end());
		if (openingBrace < 0) throw new IllegalArgumentException("Legacy behavior class has no body");
		int closingBrace = matchingBrace(masked, openingBrace);
		if (closingBrace < 0) throw new IllegalArgumentException("Legacy behavior class body is incomplete");

		String classBody = masked.substring(openingBrace + 1, closingBrace);
		List<MethodSource> missing = new ArrayList<>();
		for (MethodSource method : METHODS) {
			if (!declaresMethod(classBody, method.name())) missing.add(method);
		}
		if (missing.isEmpty()) return normalized;

		StringBuilder additions = new StringBuilder();
		for (MethodSource method : missing) {
			additions.append("\n    @Override ").append(method.source());
		}
		additions.append('\n');
		return normalized.substring(0, closingBrace) + additions + normalized.substring(closingBrace);
	}

	private static boolean declaresMethod(String classBody, String methodName) {
		Pattern implementation = Pattern.compile("\\bpublic\\b[^;{}=]*\\b"
				+ Pattern.quote(methodName) + "\\s*\\(", Pattern.DOTALL);
		Matcher matcher = implementation.matcher(classBody);
		while (matcher.find()) {
			if (braceDepthAt(classBody, matcher.start()) == 0) return true;
		}
		return false;
	}

	private static int braceDepthAt(String source, int end) {
		int depth = 0;
		for (int index = 0; index < end; index++) {
			if (source.charAt(index) == '{') depth++;
			else if (source.charAt(index) == '}') depth--;
		}
		return depth;
	}

	private static int matchingBrace(String source, int openingBrace) {
		int depth = 0;
		for (int index = openingBrace; index < source.length(); index++) {
			char value = source.charAt(index);
			if (value == '{') depth++;
			else if (value == '}' && --depth == 0) return index;
		}
		return -1;
	}

	private static String maskNonCode(String source) {
		StringBuilder masked = new StringBuilder(source);
		boolean lineComment = false;
		boolean blockComment = false;
		boolean string = false;
		boolean character = false;
		boolean textBlock = false;
		boolean escaped = false;
		for (int index = 0; index < source.length(); index++) {
			char value = source.charAt(index);
			char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
			if (lineComment) {
				if (value == '\n') lineComment = false;
				else masked.setCharAt(index, ' ');
				continue;
			}
			if (blockComment) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '*' && next == '/') {
					masked.setCharAt(++index, ' ');
					blockComment = false;
				}
				continue;
			}
			if (textBlock) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
					masked.setCharAt(++index, ' ');
					masked.setCharAt(++index, ' ');
					textBlock = false;
				}
				continue;
			}
			if (string || character) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (escaped) escaped = false;
				else if (value == '\\') escaped = true;
				else if (string && value == '"') string = false;
				else if (character && value == '\'') character = false;
				continue;
			}
			if (value == '/' && next == '/') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				lineComment = true;
			} else if (value == '/' && next == '*') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				blockComment = true;
			} else if (value == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				masked.setCharAt(++index, ' ');
				textBlock = true;
			} else if (value == '"') {
				masked.setCharAt(index, ' ');
				string = true;
			} else if (value == '\'') {
				masked.setCharAt(index, ' ');
				character = true;
			}
		}
		return masked.toString();
	}

	private record MethodSource(String name, String source) {
	}
}
