package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCapability;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorCompiler;
import com.yeyito.littlechemistry.behavior.DynamicBehaviorSource;
import com.yeyito.littlechemistry.behavior.WorkspaceJavaCompiler;
import com.yeyito.littlechemistry.content.DynamicArmorDisplayTextureSpec;
import com.yeyito.littlechemistry.content.DynamicArmorSlot;
import com.yeyito.littlechemistry.content.DynamicBlockModel;
import com.yeyito.littlechemistry.content.DynamicBlockShape;
import com.yeyito.littlechemistry.content.DynamicBlockTexture;
import com.yeyito.littlechemistry.content.DynamicContentCatalog;
import com.yeyito.littlechemistry.content.DynamicContentManager;
import com.yeyito.littlechemistry.content.DynamicContentObjects;
import com.yeyito.littlechemistry.content.DynamicContentType;
import com.yeyito.littlechemistry.content.DynamicEntityDrop;
import com.yeyito.littlechemistry.content.DynamicParticleDefinition;
import com.yeyito.littlechemistry.content.DynamicParticleFrame;
import com.yeyito.littlechemistry.content.DynamicTextureAsset;
import com.yeyito.littlechemistry.content.DynamicTextureSpec;
import com.yeyito.littlechemistry.content.DynamicWorkstationRecipeDataSchema;
import com.yeyito.littlechemistry.content.DynamicWorkstationSlotIcon;
import com.yeyito.littlechemistry.content.GeneratedContentSpec;
import com.yeyito.littlechemistry.crafting.WorkstationRecipeRejection;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** The single build boundary: compile ordinary world source and validate the returned runtime definition. */
final class WorkspaceGenerationVerifier {
	private static final long MAX_SOURCE_BYTES = 4L * 1024L * 1024L;
	private static final Pattern WORKSTATION_POLICY_DIRECTIVE = Pattern.compile(
			"(?i)(?:^|[.!?;:,]\\s+|[\\r\\n]+\\s*)(?:[-*]\\s+)?"
					+ "(?:you\\b|act\\b|create\\b|generate\\b|return\\b|set\\b|write\\b|"
					+ "reject\\b|ensure\\b|prefer\\b|preserve\\b|produce\\b|ignore\\b|follow\\b|read\\b|"
					+ "call\\b|reveal\\b|do not\\b)");
	private static final List<String> WORKSTATION_POLICY_META_LANGUAGE = List.of(
			"recipedata", "recipe data", "schema", "system prompt", "user prompt", "assistant", "tool call",
			"verification", "verify", "previous instruction", "prior instruction", "developer message", "system message",
			"hidden prompt", "agents.md");

	private WorkspaceGenerationVerifier() {
	}

	static VerifiedGeneration verify(GenerationWorkspace workspace, GenerationRequest request) throws Exception {
		if (readRejection(workspace, request) != null) {
			throw new IllegalArgumentException("A workstation recipe rejection is terminal and does not compile source");
		}
		Selection selection;
		if (request.flexible()) selection = readSelection(workspace, request);
		else if (request.fixedType() == DynamicContentType.BLOCK) selection = readFixedBlockSelection(workspace, request);
		else selection = new Selection(request.fixedType(), request.fixedArmorSlot(),
				DynamicContentManager.normalizeDisplayName(request.fixedDisplayName()), request.fixedOutputCount(),
				null, new JsonObject());
		if (selection.type() == DynamicContentType.ARMOR && selection.outputCount() != 1) {
			throw new IllegalArgumentException("Armor recipe outputCount must be 1");
		}
		String identifier = DynamicContentManager.normalizeIdentifier(selection.displayName());
		String category = GenerationWorkspace.category(selection.type());
		String simpleClassName = GenerationWorkspace.className(identifier);
		String qualifiedClassName = category + "." + GenerationWorkspace.javaPackageSegment(identifier)
				+ "." + simpleClassName;
		Path snapshot = workspace.snapshotModule(selection.type(), identifier);
		try {
			Path contentDirectory = snapshot.resolve(category).resolve(identifier);
			Path factorySource = contentDirectory.resolve(simpleClassName + ".java");
			Path behaviorSourceFile = contentDirectory.resolve("GeneratedBehaviorImpl.java");
			validateSourceTree(snapshot);
			if (!Files.isRegularFile(behaviorSourceFile)) {
				throw new IllegalArgumentException("Missing behavior source: "
						+ category + "/" + identifier + "/GeneratedBehaviorImpl.java");
			}

			String digestBeforeCompilation = GenerationWorkspace.sourceDigest(snapshot);
			String behaviorSource = Files.readString(behaviorSourceFile, StandardCharsets.UTF_8);
			DynamicBehaviorCompiler.Compiled compiledBehavior = DynamicBehaviorCompiler.compile(behaviorSource);
			validateCapabilities(selection.type(), compiledBehavior.source());
			validateFactorySourcePolicy(snapshot, behaviorSourceFile);

			List<Path> moduleSources;
			try (var paths = Files.walk(snapshot)) {
				moduleSources = paths.filter(Files::isRegularFile)
						.filter(path -> path.toString().endsWith(".java"))
						.filter(path -> !path.equals(behaviorSourceFile)).toList();
			}
			WorkspaceJavaCompiler.Compiled<GeneratedContentFactory> compiledFactory = WorkspaceJavaCompiler.compile(
					snapshot, workspace.existingRoot(), moduleSources, qualifiedClassName, GeneratedContentFactory.class);
			GeneratedContentSpec generated;
			try {
				generated = compiledFactory.instantiate().create(compiledBehavior.source());
			} catch (Exception error) {
				throw new IllegalArgumentException("Generated factory failed while constructing content: "
						+ safeMessage(error), error);
			}
			validateResult(selection, generated, compiledBehavior.source());
			String digestAfterCompilation = GenerationWorkspace.sourceDigest(snapshot);
			if (!digestBeforeCompilation.equals(digestAfterCompilation)) {
				throw new IllegalArgumentException("Generated source changed while verification was running");
			}
			return new VerifiedGeneration(selection.type(), selection.armorSlot(), selection.displayName(),
					selection.outputCount(), selection.recipeData(), generated, compiledBehavior,
					snapshot, digestAfterCompilation);
		} catch (Exception | Error failure) {
			workspace.deleteSnapshot(snapshot);
			throw failure;
		}
	}

	static WorkstationRecipeRejection readRejection(GenerationWorkspace workspace, GenerationRequest request)
			throws IOException {
		if (!request.flexible()) return null;
		JsonObject result = readResult(workspace);
		if (!result.has("kind") || !result.get("kind").isJsonPrimitive()
				|| !"rejection".equals(result.get("kind").getAsString())) return null;
		if (request.workstationPolicy() == null) {
			throw new IllegalArgumentException("Only workstation recipes may be rejected");
		}
		if (!result.keySet().equals(Set.of("kind", "category", "description"))) {
			throw new IllegalArgumentException("Rejection requires exactly kind, category, and description");
		}
		JsonObject encoded = new JsonObject();
		encoded.add("category", result.get("category").deepCopy());
		encoded.add("description", result.get("description").deepCopy());
		return WorkstationRecipeRejection.fromJson(encoded);
	}

	private static Selection readFixedBlockSelection(GenerationWorkspace workspace, GenerationRequest request)
			throws IOException {
		JsonObject result = readResult(workspace);
		if (!result.keySet().equals(Set.of("kind")) || !result.get("kind").isJsonPrimitive()) {
			throw new IllegalArgumentException("Fixed block classification requires exactly one kind field set to "
					+ "block or workstation");
		}
		String kind = result.get("kind").getAsString();
		if (!kind.equals("block") && !kind.equals("workstation")) {
			throw new IllegalArgumentException("Fixed block result kind must be block or workstation");
		}
		return new Selection(DynamicContentType.BLOCK, null,
				DynamicContentManager.normalizeDisplayName(request.fixedDisplayName()), request.fixedOutputCount(),
				kind, new JsonObject());
	}

	private static Selection readSelection(GenerationWorkspace workspace, GenerationRequest request) throws IOException {
		JsonObject result = readResult(workspace);
		Set<String> allowed = request.recipeDataSchema() == null
				? Set.of("kind", "displayName", "outputCount")
				: Set.of("kind", "displayName", "outputCount", "recipeData");
		for (String key : result.keySet()) {
			if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown result field: " + key);
		}
		String kind;
		String displayName;
		double rawCount;
		try {
			kind = result.get("kind").getAsString();
			displayName = DynamicContentManager.normalizeDisplayName(result.get("displayName").getAsString());
			rawCount = result.get("outputCount").getAsDouble();
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("Result requires kind, displayName, and integer outputCount", invalid);
		}
		if (!Double.isFinite(rawCount) || rawCount != Math.rint(rawCount) || rawCount < 1 || rawCount > 64) {
			throw new IllegalArgumentException("Recipe outputCount must be an integer from 1 to 64");
		}
		int count = (int) rawCount;
		DynamicContentType type;
		DynamicArmorSlot slot = null;
		switch (kind) {
			case "item" -> type = DynamicContentType.ITEM;
			case "block", "workstation" -> type = DynamicContentType.BLOCK;
			case "entity" -> type = DynamicContentType.ENTITY;
			case "helmet" -> { type = DynamicContentType.ARMOR; slot = DynamicArmorSlot.HEAD; }
			case "chestplate" -> { type = DynamicContentType.ARMOR; slot = DynamicArmorSlot.CHEST; }
			case "leggings" -> { type = DynamicContentType.ARMOR; slot = DynamicArmorSlot.LEGGINGS; }
			case "boots" -> { type = DynamicContentType.ARMOR; slot = DynamicArmorSlot.BOOTS; }
			default -> throw new IllegalArgumentException("Unknown recipe result kind: " + kind);
		}
		if (type == DynamicContentType.ARMOR && count != 1) {
			throw new IllegalArgumentException("Armor recipe outputCount must be 1");
		}
		int capacity = primaryOutputCapacity(request.recipeContext());
		if (count > capacity) {
			throw new IllegalArgumentException("Recipe outputCount " + count + " exceeds output capacity " + capacity);
		}
		JsonObject recipeData = new JsonObject();
		if (request.recipeDataSchema() != null) {
			try {
				recipeData = result.getAsJsonObject("recipeData").deepCopy();
			} catch (RuntimeException missing) {
				throw new IllegalArgumentException("Workstation result requires object recipeData", missing);
			}
			new DynamicWorkstationRecipeDataSchema(request.recipeDataSchema()).validateValue(recipeData);
		}
		return new Selection(type, slot, displayName, count, kind, recipeData);
	}

	private static JsonObject readResult(GenerationWorkspace workspace) throws IOException {
		Path resultFile = workspace.root().resolve(".littlechemistry/result.json");
		if (!Files.isRegularFile(resultFile)) {
			throw new IllegalArgumentException("Write .littlechemistry/result.json before verification");
		}
		try {
			return JsonParser.parseString(Files.readString(resultFile, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("Result file is not a JSON object", invalid);
		}
	}

	private static int primaryOutputCapacity(JsonObject recipeContext) {
		if (recipeContext == null || !recipeContext.has("workstation")) return 64;
		try {
			double raw = recipeContext.getAsJsonObject("workstation").getAsJsonObject("primaryOutput")
					.get("capacity").getAsDouble();
			if (Double.isFinite(raw) && raw == Math.rint(raw) && raw >= 1 && raw <= 64) return (int) raw;
		} catch (RuntimeException ignored) {
		}
		throw new IllegalArgumentException("Workstation request has an invalid primary output capacity");
	}

	private static void validateResult(Selection selection, GeneratedContentSpec generated, String behaviorSource)
			throws IOException {
		if (generated == null) throw new IllegalArgumentException("Generated factory returned null");
		if (!generated.behaviorSource().equals(behaviorSource)) {
			throw new IllegalArgumentException(
					"Factory must pass the supplied compiled behaviorSource unchanged into GeneratedContentSpec");
		}
		boolean typeMatches = switch (selection.type()) {
			case BLOCK -> generated.block() != null;
			case ITEM -> generated.item() != null;
			case ARMOR -> generated.armor() != null && (selection.armorSlot() == null
					|| generated.armor().slot() == selection.armorSlot());
			case ENTITY -> generated.entity() != null;
		};
		if (!typeMatches) throw new IllegalArgumentException("Factory property kind does not match the requested result");
		validateWorkstationKind(selection.resultKind(), generated.workstation() != null);
		if (generated.description().isBlank()) {
			throw new IllegalArgumentException("Generated content requires a non-empty tooltip description");
		}
		if (selection.type() == DynamicContentType.ITEM
				&& selection.outputCount() > generated.item().maxStack()) {
			throw new IllegalArgumentException("Recipe outputCount " + selection.outputCount()
					+ " exceeds generated item maxStack " + generated.item().maxStack());
		}
		validateProjectileVisuals(generated);
		if (generated.block() == null) validateIcon(generated.texture());
		else generated.block().drops().validateNewTargets(
				DynamicContentManager.normalizeIdentifier(selection.displayName()), DynamicContentCatalog::find);
		Set<String> itemTextureHashes = new HashSet<>();
		itemTextureHashes.add(DynamicTextureAsset.sha256(generated.texture().renderPng()));
		for (var state : generated.itemVisuals().states()) {
			validateIcon(state.texture());
			String actual = DynamicTextureAsset.sha256(state.texture().renderPng());
			if (!actual.equals(state.hash())) {
				throw new IllegalArgumentException("Item visual state '" + state.id() + "' has a stale texture hash");
			}
			if (!itemTextureHashes.add(actual)) {
				throw new IllegalArgumentException("Item visual states and base texture must be visually distinct");
			}
		}
		if (generated.armorDisplayTexture() != null) {
			validateArmorTexture(generated.armorDisplayTexture(), generated.armor().slot());
		}
		if (generated.blockModel() != null) {
			validateModelTextures(generated.blockModel().textures());
			validateBlockVisuals(generated.blockModel(), generated.block().shape());
		}
		if (generated.entityModel() != null) validateModelTextures(generated.entityModel().textures());
		if (generated.entity() != null) validateEntityRegistries(generated);
		for (DynamicParticleDefinition particle : generated.customParticles()) {
			for (DynamicParticleFrame frame : particle.frames()) {
				String actual = DynamicTextureAsset.sha256(frame.texture().renderPng());
				if (!actual.equals(frame.textureHash())) {
					throw new IllegalArgumentException("Particle '" + particle.id() + "' has a stale texture hash");
				}
			}
		}
		Set<String> particles = new HashSet<>();
		for (DynamicParticleDefinition particle : generated.customParticles()) particles.add(particle.id());
		for (String referenced : DynamicBehaviorSource.referencedCustomParticleIds(behaviorSource)) {
			if (!particles.contains(referenced)) {
				throw new IllegalArgumentException("Behavior references undefined custom particle: " + referenced);
			}
		}
		if (generated.workstation() != null) validateWorkstationDesign(generated.workstation());
	}

	static void validateProjectileVisuals(GeneratedContentSpec generated) {
		if (generated.item() == null || !generated.item().heldType().isNativeProjectileWeapon()) return;
		generated.itemVisuals().requireCompleteFor(generated.item().heldType());
		List<DynamicTextureSpec> textures = new java.util.ArrayList<>();
		textures.add(generated.texture());
		generated.itemVisuals().states().forEach(state -> textures.add(state.texture()));
		for (int first = 0; first < textures.size(); first++) {
			for (int second = first + 1; second < textures.size(); second++) {
				if (differentPixels(textures.get(first), textures.get(second)) < 8) {
					throw new IllegalArgumentException(
							"Projectile base and visual states must differ by at least eight pixels");
				}
			}
		}
	}

	private static int differentPixels(DynamicTextureSpec first, DynamicTextureSpec second) {
		int changed = 0;
		for (int y = 0; y < first.height(); y++) {
			for (int x = 0; x < first.width(); x++) {
				String firstColor = effectiveColor(first.palette().get(Character.digit(first.rows().get(y).charAt(x), 16)));
				String secondColor = effectiveColor(second.palette().get(Character.digit(second.rows().get(y).charAt(x), 16)));
				if (!firstColor.equals(secondColor)) changed++;
			}
		}
		return changed;
	}

	private static String effectiveColor(String rgba) {
		return rgba.regionMatches(true, 6, "00", 0, 2) ? "00000000" : rgba.toUpperCase(java.util.Locale.ROOT);
	}

	static void validateWorkstationKind(String resultKind, boolean hasWorkstationSpec) {
		if ("workstation".equals(resultKind) && !hasWorkstationSpec) {
			throw new IllegalArgumentException("Result kind workstation requires a non-null DynamicWorkstationSpec; "
					+ "the spec enables the workstation runtime and AI Workstation tooltip");
		}
		if ("block".equals(resultKind) && hasWorkstationSpec) {
			throw new IllegalArgumentException(
					"A generated block with DynamicWorkstationSpec must use result kind workstation, not ordinary block");
		}
	}

	static void validateWorkstationDesign(com.yeyito.littlechemistry.content.DynamicWorkstationSpec workstation) {
		if (!workstation.processDescription().toLowerCase(java.util.Locale.ROOT).contains("tick")) {
			throw new IllegalArgumentException("Workstation process description must express timing in Minecraft ticks");
		}
		String policy = workstation.recipePolicy();
		String normalized = policy.toLowerCase(java.util.Locale.ROOT);
		if (WORKSTATION_POLICY_DIRECTIVE.matcher(policy).find()
				|| WORKSTATION_POLICY_META_LANGUAGE.stream().anyMatch(normalized::contains)) {
			throw new IllegalArgumentException("Workstation recipePolicy must be concise third-person output-design data, "
					+ "not model, workflow, verification, recipeData, or schema instructions");
		}
		for (var slot : workstation.slots()) {
			if (slot.emptySlotIcon() != null && DynamicWorkstationSlotIcon.resolve(slot.emptySlotIcon()) == null) {
				throw new IllegalArgumentException("Workstation slot '" + slot.id() + "' uses unknown GUI sprite '"
						+ slot.emptySlotIcon() + "'; choose an ID from reference/vanilla/GUI_SPRITES.txt or use null");
			}
		}
	}

	private static void validateEntityRegistries(GeneratedContentSpec generated) {
		for (var sound : List.of(generated.entity().ambientSound(), generated.entity().hurtSound(),
				generated.entity().deathSound())) {
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(sound)) {
				throw new IllegalArgumentException("Unknown generated entity sound event: " + sound);
			}
		}
		for (DynamicEntityDrop drop : generated.entity().drops()) {
			var item = BuiltInRegistries.ITEM.getOptional(drop.item()).orElse(null);
			if (item == null) {
				throw new IllegalArgumentException("Unknown generated entity drop item: " + drop.item());
			}
			if (DynamicContentObjects.isCarrierItem(item)) {
				throw new IllegalArgumentException(
						"Generated entities cannot drop Little Chemistry carrier infrastructure: " + drop.item());
			}
		}
	}

	private static void validateCapabilities(DynamicContentType type, String behaviorSource) {
		Set<DynamicBehaviorCapability> capabilities = DynamicBehaviorSource.capabilities(behaviorSource);
		EnumSet<DynamicBehaviorCapability> invalid = EnumSet.noneOf(DynamicBehaviorCapability.class);
		for (DynamicBehaviorCapability capability : capabilities) {
			if (!allows(type, capability)) invalid.add(capability);
		}
		if (!invalid.isEmpty()) {
			throw new IllegalArgumentException("Behavior implements callbacks that do not apply to "
					+ type.serializedName() + ": " + invalid);
		}
	}

	private static boolean allows(DynamicContentType type, DynamicBehaviorCapability capability) {
		boolean entity = switch (capability) {
			case ENTITY_SPAWNED, ENTITY_TICK, ENTITY_INTERACT, ENTITY_HURT, ENTITY_ATTACK, ENTITY_DEATH -> true;
			default -> false;
		};
		if (type == DynamicContentType.ENTITY) return entity;
		if (entity) return false;
		if (type == DynamicContentType.BLOCK) return true;
		return switch (capability) {
			case USE_AIR, USE_ON_BLOCK, INTERACT_LIVING_ENTITY, INVENTORY_TICK, POST_HURT_ENEMY,
					MINE_BLOCK, FINISH_USING, CRAFTED -> true;
			default -> false;
		};
	}

	private static void validateIcon(DynamicTextureSpec texture) {
		texture.requireDimensions(16, 16);
		texture.requireBinaryAlpha();
		int visible = 0;
		Set<Integer> colors = new HashSet<>();
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				int color = Character.digit(row.charAt(index), 16);
				if (!texture.palette().get(color).regionMatches(true, 6, "00", 0, 2)) {
					visible++;
					colors.add(color);
				}
			}
		}
		Set<Integer> used = usedColors(texture);
		if (visible < 12 || colors.size() < 2 || used.size() < 3 || luminanceRange(texture.palette(), used) < 40) {
			throw new IllegalArgumentException(
					"Inventory texture needs 12 visible pixels, three used colors, and readable contrast");
		}
	}

	static void validateArmorTexture(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot) {
		int visible = 0;
		int transparent = 0;
		Set<Integer> used = new HashSet<>();
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				int colorIndex = Character.digit(row.charAt(index), 16);
				used.add(colorIndex);
				String color = texture.palette().get(colorIndex);
				if (color.regionMatches(true, 6, "00", 0, 2)) transparent++;
				else visible++;
			}
		}
		if (visible < 16 || transparent == 0) {
			throw new IllegalArgumentException("Armor display texture needs visible armor pixels and transparent UV space");
		}
		if (used.size() < 3 || luminanceRange(texture.palette(), used) < 32) {
			throw new IllegalArgumentException("Armor display texture needs three used colors and readable contrast");
		}
		if (relevantOpaquePixels(texture, slot) < 16) {
			throw new IllegalArgumentException("Armor display texture has too few visible pixels in the "
					+ slot.serializedName() + " UV islands");
		}
		if (slot == DynamicArmorSlot.HEAD
				&& (opaquePixels(texture, 0, 0, 32, 16) < 16
				|| opaquePixels(texture, 32, 0, 32, 16) < 8)) {
			throw new IllegalArgumentException(
					"Head armor must paint both the base-head and hat/outer-head UV regions");
		}
	}

	private static void validateModelTextures(List<DynamicBlockTexture> textures) throws IOException {
		int totalPixels = 0;
		for (DynamicBlockTexture texture : textures) {
			DynamicTextureSpec specification = texture.texture();
			totalPixels += specification.width() * specification.height();
			String actual = DynamicTextureAsset.sha256(specification.renderPng());
			if (!actual.equals(texture.hash())) {
				throw new IllegalArgumentException("Model texture '" + texture.id() + "' has a stale texture hash");
			}
			Set<Integer> used = usedColors(specification);
			int visible = visiblePixels(specification);
			if (visible == 0) throw new IllegalArgumentException("Model texture '" + texture.id() + "' is invisible");
			if (specification.width() * specification.height() > 1 && used.size() < 2) {
				throw new IllegalArgumentException("Model texture '" + texture.id() + "' needs two used colors");
			}
			if (used.size() > 1 && luminanceRange(specification.palette(), used) < 20) {
				throw new IllegalArgumentException("Model texture '" + texture.id() + "' has too little contrast");
			}
		}
		if (totalPixels > 16_384) throw new IllegalArgumentException("Model textures exceed the 16,384-pixel budget");
	}

	private static void validateBlockVisuals(DynamicBlockModel model, DynamicBlockShape shape) {
		for (DynamicBlockTexture texture : model.textures()) validateBlockAlpha(texture.texture(), shape);
		if (shape != DynamicBlockShape.STAR && shape != DynamicBlockShape.CROSS) return;
		for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
			DynamicTextureSpec texture = model.texture(model.faces().get(direction).texture()).texture();
			if (!hasTransparentPixel(texture)) {
				throw new IllegalArgumentException(shape.serializedName()
						+ " textures used on crossed planes need transparent background pixels");
			}
		}
	}

	private static void validateBlockAlpha(DynamicTextureSpec texture, DynamicBlockShape shape) {
		if (shape != DynamicBlockShape.STAR && shape != DynamicBlockShape.CROSS
				&& shape != DynamicBlockShape.TORCH && shape != DynamicBlockShape.CUSTOM) {
			texture.requireOpaque();
			return;
		}
		texture.requireBinaryAlpha();
		int visible = visiblePixels(texture);
		Set<Integer> opaqueColors = new HashSet<>();
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				int color = Character.digit(row.charAt(index), 16);
				if (!texture.palette().get(color).regionMatches(true, 6, "00", 0, 2)) opaqueColors.add(color);
			}
		}
		if (visible < 16 || opaqueColors.size() < 3) {
			throw new IllegalArgumentException("Cutout block textures need 16 visible pixels and three opaque colors");
		}
		if ((shape == DynamicBlockShape.STAR || shape == DynamicBlockShape.CROSS)
				&& texture.width() * texture.height() - visible < 16) {
			throw new IllegalArgumentException("Crossed-plane textures need at least 16 transparent pixels");
		}
	}

	private static int visiblePixels(DynamicTextureSpec texture) {
		int visible = 0;
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				String color = texture.palette().get(Character.digit(row.charAt(index), 16));
				if (!color.regionMatches(true, 6, "00", 0, 2)) visible++;
			}
		}
		return visible;
	}

	private static Set<Integer> usedColors(DynamicTextureSpec texture) {
		Set<Integer> used = new HashSet<>();
		for (String row : texture.rows()) {
			for (int index = 0; index < row.length(); index++) {
				used.add(Character.digit(row.charAt(index), 16));
			}
		}
		return used;
	}

	private static boolean hasTransparentPixel(DynamicTextureSpec texture) {
		return visiblePixels(texture) < texture.width() * texture.height();
	}

	private static int luminanceRange(List<String> palette, Set<Integer> used) {
		int minimum = 256;
		int maximum = -1;
		for (int index : used) {
			String color = palette.get(index);
			if (color.regionMatches(true, 6, "00", 0, 2)) continue;
			int red = Integer.parseInt(color.substring(0, 2), 16);
			int green = Integer.parseInt(color.substring(2, 4), 16);
			int blue = Integer.parseInt(color.substring(4, 6), 16);
			int luminance = (int) Math.round(red * 0.2126 + green * 0.7152 + blue * 0.0722);
			minimum = Math.min(minimum, luminance);
			maximum = Math.max(maximum, luminance);
		}
		return maximum < 0 ? 0 : maximum - minimum;
	}

	private static int relevantOpaquePixels(DynamicArmorDisplayTextureSpec texture, DynamicArmorSlot slot) {
		return switch (slot) {
			case HEAD -> opaquePixels(texture, 0, 0, 32, 16);
			case CHEST -> opaquePixels(texture, 16, 16, 24, 16) + opaquePixels(texture, 40, 16, 16, 16);
			case LEGGINGS -> opaquePixels(texture, 0, 16, 40, 16);
			case BOOTS -> opaquePixels(texture, 0, 16, 16, 16);
		};
	}

	private static int opaquePixels(DynamicArmorDisplayTextureSpec texture, int left, int top, int width, int height) {
		int count = 0;
		for (int y = top; y < top + height; y++) {
			String row = texture.rows().get(y);
			for (int x = left; x < left + width; x++) {
				String color = texture.palette().get(Character.digit(row.charAt(x), 16));
				if (!color.regionMatches(true, 6, "00", 0, 2)) count++;
			}
		}
		return count;
	}

	private static void validateFactorySourcePolicy(Path root, Path behaviorSource) throws IOException {
		Pattern forbidden = Pattern.compile("(?s)\\b(?:System|Runtime|ProcessBuilder|Thread|ThreadGroup|ClassLoader|"
				+ "SecurityManager|Executors|ExecutorService|ForkJoinPool|CompletableFuture|Unsafe|MethodHandles|"
				+ "VarHandle|DynamicContentCatalog|DynamicContentManager|DynamicBehaviorRegistry)\\b"
				+ "|\\b(?:getClassLoader|forName|getDeclaredMethod|getDeclaredField|getDeclaredConstructor|setAccessible)\\b"
				+ "|\\bjava\\.(?:io|net|nio\\.file|lang\\.reflect)\\b|\\bjavax\\.tools\\b"
				+ "|\\bjdk\\.internal\\b|\\bsun\\.misc\\b|\\bstatic\\s*\\{|\\bnative\\b|\\\\u+[0-9a-fA-F]{4}");
		try (var paths = Files.walk(root)) {
			for (Path file : paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> !path.equals(behaviorSource)).toList()) {
				String masked = maskNonCode(Files.readString(file, StandardCharsets.UTF_8));
				var match = forbidden.matcher(masked);
				if (match.find()) {
					throw new IllegalArgumentException("Factory source uses a forbidden process, I/O, reflection, thread, "
							+ "unsafe, native, or static-initializer API in " + root.relativize(file)
							+ " near '" + match.group() + "'");
				}
			}
		}
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
			} else if (blockComment) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '*' && next == '/') {
					masked.setCharAt(++index, ' ');
					blockComment = false;
				}
			} else if (textBlock) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '"' && next == '"' && index + 2 < source.length()
						&& source.charAt(index + 2) == '"') {
					masked.setCharAt(++index, ' ');
					masked.setCharAt(++index, ' ');
					textBlock = false;
				}
			} else if (string || character) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (escaped) escaped = false;
				else if (value == '\\') escaped = true;
				else if (string && value == '"') string = false;
				else if (character && value == '\'') character = false;
			} else if (value == '/' && next == '/') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				lineComment = true;
			} else if (value == '/' && next == '*') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				blockComment = true;
			} else if (value == '"' && next == '"' && index + 2 < source.length()
					&& source.charAt(index + 2) == '"') {
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

	private static void validateSourceTree(Path root) throws IOException {
		long total = 0;
		try (var paths = Files.walk(root)) {
			for (Path file : paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> {
						Path relative = root.relativize(path);
						return relative.getNameCount() > 0
								&& GenerationWorkspace.SOURCE_DIRECTORIES.contains(relative.getName(0).toString());
					}).toList()) {
				long size = Files.size(file);
				if (size > 512L * 1024L) throw new IllegalArgumentException("Java source is too large: " + root.relativize(file));
				total += size;
			}
		}
		if (total > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Generated Java source tree exceeds 4 MiB");
	}

	private static String safeMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null) current = current.getCause();
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName()
				: message.replaceAll("[\\r\\n]+", " ").trim();
	}

	private record Selection(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName,
			int outputCount, String resultKind, JsonObject recipeData) {
		private Selection {
			recipeData = recipeData == null ? new JsonObject() : recipeData.deepCopy();
		}

		@Override public JsonObject recipeData() { return recipeData.deepCopy(); }
	}

	record VerifiedGeneration(DynamicContentType type, DynamicArmorSlot armorSlot, String displayName,
			int outputCount, JsonObject recipeData, GeneratedContentSpec content,
			DynamicBehaviorCompiler.Compiled compiledBehavior, Path sourceSnapshot, String sourceDigest) {
		VerifiedGeneration {
			recipeData = recipeData == null ? new JsonObject() : recipeData.deepCopy();
			if (compiledBehavior == null || !compiledBehavior.source().equals(content.behaviorSource())) {
				throw new IllegalArgumentException("Verified behavior artifact does not match generated content");
			}
		}

		@Override public JsonObject recipeData() { return recipeData.deepCopy(); }
	}
}
