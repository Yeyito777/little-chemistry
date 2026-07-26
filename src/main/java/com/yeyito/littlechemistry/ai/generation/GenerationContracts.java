package com.yeyito.littlechemistry.ai.generation;

import java.util.List;

/**
 * Focused authoring contracts delivered only after the model has selected a result kind.
 *
 * <p>Keep shared prompts and {@link #API_INDEX} small. Capability-specific details belong in exactly one contract
 * here; duplicating them into the system prompt or initial query dilutes the recipe and its visual references.</p>
 */
final class GenerationContracts {
	static final String OPTIONAL_PARTICLES_DIRECTION = "If the selected interaction genuinely warrants distinct particle "
			+ "artwork, read `reference/contracts/particles.md`; otherwise use an appropriate vanilla particle or none.";

	static final String API_INDEX = """
			# Generated Java API index

			Choose the result before studying implementation details. When a request uses `.littlechemistry/result.json`,
			the mutating tool that observes the selection returns the relevant contract directly. Follow that contract and
			do not read unrelated capability contracts.

			The factory contract is `com.yeyito.littlechemistry.ai.generation.GeneratedContentFactory`; implement
			`GeneratedContentSpec create(String behaviorSource) throws Exception`. `GeneratedContentBuilder` is an optional
			fluent helper, and `GeneratedContentApi` contains texture/model convenience methods rather than a restricted DSL.
			Generated definitions are ordinary immutable Java values with no hidden property setters. All public Little Chemistry
			and Minecraft constructors are available. Search `reference/classes/INDEX.txt` and read exact class source instead of
			guessing signatures.

				Common definition classes include `GeneratedContentSpec`, `DynamicTextureSpec`, `DynamicBlockProperties`,
				`DynamicItemProperties`, `DynamicArmorProperties`, `DynamicEntityProperties`, `DynamicBlockModel`, `DynamicBlockAssembly`,
			`DynamicParticleDefinition`, and `DynamicItemVisuals`. The vanilla `Rarity` in native properties must equal
			`DynamicRarity.vanillaRarity()`.

			Indexed textures use RRGGBBAA palettes plus hexadecimal pixel-index rows. Icons are 16x16 and equipped armor is
			64x32. Use the hash-producing `GeneratedContentApi` helpers or
			`DynamicTextureAsset.sha256(texture.renderPng())` so persisted hashes are exact. Model-facing textures are always
			textual; there is no PNG, raster preview, or vision tool. Supplied recipe-item artwork is already in the user
			message. Search `reference/vanilla/TEXTURES.txt` and call `read_texture` only for additional relevant inspiration.

			Focused contracts are under `reference/contracts/`: `item.md`, `block.md`, `armor.md`, `entity.md`,
			`workstation.md`, `projectile-weapon.md`, and `storage.md`. `particles.md` is an optional cross-cutting contract;
			read it only when the selected interaction genuinely warrants distinct authored particle artwork.
			""";

	private static final Contract ITEM = new Contract("reference/contracts/item.md", """
			# Ordinary item contract

			Implement the complete native item properties and behavior implied by the recipe. The process and each ingredient
			must materially influence identity, pixels, properties, and gameplay. Begin the icon from the closest automatically
			supplied item carrier: preserve its occupied silhouette and shading language, then make deliberate ingredient-specific
			palette and motif edits rather than redrawing a familiar Minecraft item from memory.

			This `ordinary` capability is for an item whose primary gameplay remains in a hand or inventory without native storage
			or bow/crossbow mechanics. Do not substitute an unrelated passive or particle effect for the interaction naturally
			promised by the recipe.
			""");

	private static final Contract BLOCK = new Contract("reference/contracts/block.md", """
			# Ordinary block contract

			Use an ordinary block for a placed object whose primary behavior is neither an independent entity nor a recipe
			workstation. Define native block properties, drops, shape/model, and meaningful placed behavior from the recipe.
			Do not describe a machine or crafting bench as functional unless the result kind is `workstation` and that contract
			is implemented.

				Placed callbacks may use `DynamicPlacedBlockUseContext.persistentState()`. Setting state key `visual` to `foo`
				selects an authored model texture named `<baseTextureId>_foo`, with base fallback. Do not promise opening, processing,
				or a visual transition unless native capability, state mutation, and matching model variants implement it. When the
				intended object occupies multiple cells or changes physical shape, use an optional `DynamicBlockAssembly`: every named
				variant reserves the same face-connected footprint and supplies cell-local rendered cuboids whose `collision` flags are
				also the live collision shape. The engine places/removes the footprint atomically; state key `geometry` selects its named
				physical variant, independently of the texture-only `visual` state.
				Read the three assembly record sources before authoring one; this is a general geometry option, not a door preset.
			""");

	private static final Contract ARMOR = new Contract("reference/contracts/armor.md", """
			# Armor contract

			Adapt the closest supplied relevant armor icon and matching equipped humanoid layer. Author both the 16x16 inventory
			icon and 64x32 `DynamicArmorDisplayTextureSpec` using Minecraft's real armor UV layout. Choose the display route from
			the intended spatial shape, not from concept-name rules.

			Leave `armorGeometry` null for close-fitting Minecraft armor. On the vanilla outer-head cube, top is
			x=40..47,y=0..7; side faces right/front/left/back are x=32..39/40..47/48..55/56..63 at y=8..15. Side row y=8
			is the top-of-head edge and y=15 is the chin/neck edge; never infer those directions from flat rows.

			For an open, thin, protruding, or displaced silhouette, attach `DynamicArmorGeometry`. Each
			`DynamicArmorGeometryPart` is a UV-wrapped cuboid with an animated `DynamicArmorAnchor`, local model-pixel
			position/size and rotation, and a `textureU`,`textureV` corner on the same 64x32 sheet. HEAD uses the familiar
			(-4,-8,-4) box origin; BODY uses (0,0,0); arms pivot at (+/-5,2,0), legs at (+/-1.9,12,0). A cuboid net consumes
			`2*(depth+width)` by `depth+height` pixels; pack independently authored nets without overlap. Geometry is bounded to
			32 slot-valid parts. It follows adult humanoid/armor-stand poses; baby humanoids fall back to the vanilla wrapped shell,
			and fixed-shell trim overlays are omitted for arbitrary geometry.
			""");

	private static final Contract ENTITY = new Contract("reference/contracts/entity.md", """
			# Generated entity/world-object contract

			Use an entity for an independently placed world object, creature, vehicle, or mount whose primary gameplay happens
			in the world. Its inventory carrier is a native spawner item that creates `DynamicCarrierEntity`; do not substitute
			an ordinary held item that merely manipulates a pre-existing entity. Define dimensions, movement/disposition, a
			complete `DynamicEntityModel`, and actual interaction/lifecycle behavior such as mounting where the concept requires it.

			Plan cuboid geometry and face materials together. `DynamicBlockModel` is shared by custom block/entity geometry.
			Every face chooses a named `DynamicBlockTexture` and optional `DynamicBlockUv` in normalized 0-16 space. Null UV uses
			Minecraft's cuboid-coordinate crop, so a thin element samples only a thin texture region. `uniformFaces(id)` is for a
			coordinate-authored atlas, not a concept-wide painting. Use explicit `face(id,u0,v0,u1,v1)` crops or `fullFaces(id)`
				only for intentional stretching, and prefer separate textures/face assignments for distinct materials. Use any focused
				native entity bundle supplied with this contract; it pairs exact texture rows with cuboids, pivots, and face UVs. For
				additional mobs or world objects, pair a path from `TEXTURES.txt` with its model class/factory through
				`read_entity_reference`. Native geometry is useful source material, not a restriction on custom design.
			""");

	private static final Contract WORKSTATION = new Contract("reference/contracts/workstation.md", """
			# Generated workstation contract

			A workstation is a functional generated block with a non-null `DynamicWorkstationSpec`, never a decorative furnace or
			bench. Attach it through `GeneratedContentBuilder.workstation(...)`. Define at least one recipe-input slot, exactly one
			primary `OUTPUT`, and a `DynamicWorkstationUi` with exactly one `MAKE_RECIPE` button. Slot `emptySlotIcon` values are
			GUI-atlas sprite IDs such as `minecraft:container/slot/lapis_lazuli` from
				`reference/vanilla/GUI_SPRITES.txt`, or null when none fits.
				Use `DynamicWorkstationSlot`'s shorter constructor for ordinary slots: input/fuel/catalyst items remain extractable,
				outputs reject insertion, and the engine's temporary invention lock still freezes the relevant recipe slots. Choose the
				full boolean constructor only for an intentional permanent slot rule, never to imitate processing lock behavior.

			Implement deterministic capture/ingredient use in `WorkstationBehavior.createWorkstationRecipe` and placement-local
			processing, progress, fuel, and state in `WorkstationTickBehavior.workstationTick`; the behavior entry implements both.
			Put Minecraft-tick timing in `processDescription`, third-person declarative output character/balance in `recipePolicy`,
			and bounded recipe fields in the closed `recipeDataSchema`. Future recipe `aiContext` is descriptive and excluded from
			cache identity: every contextual value that can affect output identity, count, recipe data, visuals, properties, or
			behavior must be represented in deterministic `cacheDiscriminator`; the engine supplies `visualReferenceDigest` for
			automatic ingredient artwork. Read `DynamicWorkstationSpec`, its slot/UI records, `WorkstationBehavior`,
			`WorkstationTickBehavior`, `DynamicWorkstationContext`, and `WorkstationRecipeRequest` source.

			Explicitly provide `DynamicWorkstationParticles` with separate inventing and ready-to-take effects. Each uses a supported
			vanilla particle name or `custom:<id>` from the generated particle library; the engine emits them at native AI-table
			cadence. `GeneratedContentApi.workstationParticle(...)` supplies the standard motion profile.

			Author a visibly distinct `<baseTextureId>_active` model texture. The engine selects `active` while inventory or
			processing is present; behavior may use `DynamicWorkstationContext.state()` for additional synchronized state. The engine
				already supplies persistent inventory, the generic screen, recipe locking/cache and transactions, opening, automation,
				and the `AI Workstation` tooltip marker—use those native capabilities rather than imitating them. A workstation whose
				footprint or collision truly changes may also use the general `DynamicBlockAssembly`; inspect its three record sources.
			""");

	private static final Contract PROJECTILE = new Contract("reference/contracts/projectile-weapon.md", """
				# Projectile-weapon contract

				Use ordinary item kind with held type `BOW` or `CROSSBOW` and inspect the complete matching vanilla state family.
				Preserve the useful carrier silhouette and animation progression while applying deliberate concept-specific pixels.
				Attach `DynamicItemVisuals`; bows provide distinct `pulling_0..2`, and crossbows additionally provide `charged` and
				`charged_firework` when those states can occur.

				Choose mechanics rather than assuming them. A null projectile spec preserves legacy vanilla behavior;
				`DynamicProjectileWeaponSpec.nativeBow/nativeCrossbow` is the good native default, with optional synchronized item/tag/
				dynamic-content ammunition overrides. Native mechanics may still transform shots through `ProjectileCreatedBehavior`
				using helpers such as `context.replacement(...)` or `context.firework(...)`, and react through
				`ProjectileImpactBehavior`. Select `CUSTOM` when the concept should own use, charge/release, ammunition,
				consumption, durability, sounds, or firing. `UseAirBehavior`, `ProjectileWeaponUseTickBehavior`, and
				`ProjectileWeaponReleaseBehavior` receive live server context with explicit consume, damage, mark, and launch helpers;
				ordinary Minecraft APIs remain available. Defaults are conveniences, not restrictions—implement the interaction naturally
				promised by the recipe.
				""");

	private static final Contract STORAGE = new Contract("reference/contracts/storage.md", """
			# Native generated-storage contract

			Attach `new DynamicStorageSpec(rows)` through `GeneratedContentBuilder.storage(...)` to an ordinary block or a
			regular-held, max-stack-one item. Select storage when opening/carrying or accessing an inventory is the concept's primary
			interaction; do not replace it with an unrelated passive effect. The engine supplies persistence, native chest menus,
			nesting protection, drops, presentation, and a storage tooltip, so generated behavior must not hand-roll a menu.

			`new DynamicStorageSpec(rows)` gently rejects container-bearing items at the slot boundary. Use the explicit immutable
				constructor `new DynamicStorageSpec(rows, true)` only when intentional nesting is coherent and safe. A storage block must
				author a distinct `<baseTextureId>_open` model texture; the engine selects `open` while viewed. Do not fake opening with
				sounds. If opening changes physical geometry or the object spans cells, block storage may use the general
				`DynamicBlockAssembly`; inspect its three record sources.
			""");

	private static final Contract PARTICLES = new Contract("reference/contracts/particles.md", """
			# Optional custom-particle contract

			Particles support a real interaction; they never replace the gameplay the recipe promises. Prefer an appropriate
			vanilla particle when one already communicates the effect. Define custom particles only when distinct authored pixel
			art or animation materially expresses the concept.

			Attach the bounded library through `GeneratedContentBuilder.particles(List<DynamicParticleDefinition>)`. Author each
			`DynamicParticleFrame` with the same textual RRGGBBAA palette/rows representation as every generated texture;
			`GeneratedContentApi.particleFrame(...)` computes the exact hash. Read `DynamicParticleDefinition` and
			`DynamicParticleFrame` source for the current limits and motion/render fields.

			Emit custom particles only from server behavior through the budgeted `DynamicParticles.spawn(...)` API, passing the
			live context definition and a literal local particle ID declared by the generated content. Choose bounded event-driven
			bursts or restrained cadence; do not hand-roll particle packets or substitute ambient spam for meaningful behavior.
			Blocks may also reference `custom:<id>` from `DynamicParticleEmitter`, and workstation lifecycle effects may reference
			`custom:<id>` from `DynamicWorkstationParticles`.
			""");

	private static final Contract REJECTION = new Contract("reference/contracts/rejection.md", """
			# Workstation recipe rejection

			This is a terminal rejection. Do not create source. Call `verify` immediately and repair only result-file diagnostics.
			""");

	private GenerationContracts() {
	}

	static List<Contract> documents() {
		return List.of(ITEM, BLOCK, ARMOR, ENTITY, WORKSTATION, PROJECTILE, STORAGE, PARTICLES, REJECTION);
	}

	static Contract contractForResult(GenerationRequest request, String encoded) {
		WorkspaceGenerationVerifier.ContractChoice selection =
				WorkspaceGenerationVerifier.validateContractSelection(request, encoded);
		if (selection.kind().equals("rejection")) return REJECTION;
		return switch (selection.kind() + ":" + selection.capability()) {
			case "item:ordinary" -> ITEM;
			case "item:storage", "block:storage" -> STORAGE;
			case "item:projectile_weapon" -> PROJECTILE;
			case "block:ordinary" -> BLOCK;
			case "workstation:workstation" -> WORKSTATION;
			case "helmet:armor", "chestplate:armor", "leggings:armor", "boots:armor" -> ARMOR;
			case "entity:entity" -> ENTITY;
			default -> throw new IllegalArgumentException("No focused contract exists for validated result kind/capability: "
					+ selection.kind() + "/" + selection.capability());
		};
	}

	static Contract fixedContract(GenerationRequest request) {
		return switch (request.fixedType()) {
			case ARMOR -> ARMOR;
			case ENTITY -> ENTITY;
			case ITEM, BLOCK -> null; // These select their concrete capability through result.json first.
		};
	}

	record Contract(String path, String content) {
	}
}
