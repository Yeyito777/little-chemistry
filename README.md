# Little Chemistry

Little Chemistry is an experimental Fabric mod about using AI to make everything craftable and smeltable in Minecraft.

The goal is simple: when a player tries to craft or furnace-smelt something whose recipe does not exist—or whose generated recipe has not yet been cached for the current server—the mod asks AI to invent a fitting result, including how many items the recipe produces, and makes it appear. Recipe snapshots resolve generated ingredients to their complete native gameplay properties and full persisted Java behavior source, so the model reasons about the logical item, block, or armor piece instead of its shared carrier registry entry. Once generated, the server caches the recipe and its output count so everyone can reuse it without another AI request. Crafting tables and normal furnaces expose the same **Make Recipe** workflow; furnace recipes use Minecraft's native fuel, 200-tick cooking cycle, result stacking, and experience handling.

The current first milestone provides:

- A dedicated **Little Chemistry** creative-mode tab.
- Three utility items: the creation and deletion wands plus `little_chemistry:crafting_table_on_a_stick`.
- A blue/purple wand based on Minecraft's stick texture.
- Right-clicking the wand opens an in-game creation screen for naming a new item, block, or armor piece.
- A red-orange **Wand of Deletion** opens a multi-select menu for removing generated definitions.
- A portable **Crafting Table on a Stick** opens a full 3×3 crafting grid with Little Chemistry's **Make Recipe** action.
- Image-generated chemistry artwork for the mod and Prism instance branding.
- Server-owned dynamic items, blocks, and armor, including synchronized generated textures.
- A server-wide recipe book: every data-pack recipe is unlocked for every player, and invented Little Chemistry recipes appear for everyone with their exact runtime ingredients and outputs.
- A private in-game AI command backed by `gpt-5.6-sol` in fast service mode:

  ```mcfunction
  /littlechemistry llm "How do brewing stands work?"
  ```

Only the invoking player receives the answer. Requests offer the model no tools and use medium reasoning.

## Crafting Table on a Stick

Place one crafting table diagonally above one stick in any crafting grid to make a **Crafting Table on a Stick**. Use it to open a portable 3×3 crafting table without placing a block. Each individual stick keeps its own grid when closed, moved, stored, or reloaded. The portable grid supports vanilla recipes, cached Little Chemistry recipes, and the same **Make Recipe** action used to ask AI to invent a result for an otherwise invalid ingredient layout. While its request is running, that specific stick turns gray and reopens to the locked ingredients and the **Making Recipe…** state. It turns green when the invented output is ready to collect; taking the output clears the ready state.

## AI authentication

Subscription authentication is the default. Little Chemistry re-reads credentials for every request, prioritizing an Exocortex OpenAI session and then a Codex CLI session. This allows either external program to refresh its token without restarting Minecraft.

Server administrators can switch the backend authentication mode:

```mcfunction
/littlechemistry auth subscription
/littlechemistry auth apikey <openai-api-key>
```

API keys are stored outside the world in Fabric's `config/little-chemistry/api-key.txt`, with owner-only permissions where the filesystem supports them. Authentication commands require administrator permission and never echo the key.

## Dynamic server content

Server administrators can create content while the server is running:

```mcfunction
/littlechemistry item create cobalt dust
/littlechemistry block create quantum stone
/littlechemistry armor head create stellar crown
/littlechemistry armor chest create cobalt cuirass
/littlechemistry armor leggings create mercury greaves
/littlechemistry armor boots create cloud boots
```

The Wand of Creation provides the same operation without typing a command: hold it, right-click, enter a name, choose **Item**, **Block**, or **Armor**, choose **Luna**, **Terra**, or **Sol**, and press **Done**. For armor, the selected model infers the equipment slot from the name instead of asking for a separate slot. **Cancel** or Escape closes the screen without creating anything. Creation still uses the server's `little_chemistry:command.create` permission (administrator level by default), and the server validates that the requester is holding the wand.

Creation runs asynchronously through the selected fast-service `gpt-5.6-luna`, `gpt-5.6-terra`, or `gpt-5.6-sol` content compiler using low reasoning. The server has no limit on the number of distinct generation jobs that can run in parallel, including multiple requests from the same player, while reserving each normalized content name until its job is committed. The model receives the requested name and kind as design data, infers an armor slot from the name when needed, and mutates an isolated draft through tools. Every generation can search the world's existing generated definitions by identity, description, native properties, callbacks, or behavior concepts, then inspect one definition's complete canonical property, texture, model, and untruncated Java source data. AI recipe generation also embeds the full, untruncated behavior source of every generated ingredient directly in its recipe context. Its vanilla-content fetch tools return indexed artwork references and relevant gameplay metadata for similar items, blocks, and equipment, including tool behavior, reach, durability, food/equipment traits, hardness, light, collision, redstone traits, and mining requirements. Armor additionally exposes a dedicated fetch tool with UV-authoring instructions and complete vanilla humanoid equipment-sheet references. The Java inspection tools search installed Minecraft, Fabric, and Little Chemistry classes, expose their runtime signatures, and use embedded Vineflower to reconstruct untruncated source-like Java—including method bodies—from a selected class-file family. Decompiled Java can differ cosmetically from an author's original source, reflects pre-transform classpath bytecode rather than later access-widener or Mixin changes, and is not truncated by Little Chemistry. Items and armor receive indexed 16×16 inventory textures, while a block receives a complete runtime visual model with one or more named indexed textures, independent 1–64-pixel dimensions, per-face texture assignments, optional UV overrides, and optional AI-authored cuboids. Armor submission also requires a separate indexed 64×32 worn display texture and cannot reuse the inventory icon. Every generated definition also requires a complete Java class. The class implements an empty behavior marker plus only the item and placed-block callback capability interfaces it actually needs, so passive content has no fake event methods. The compile tool hot-compiles that class against the running Minecraft and mod classes and returns diagnostics for the model to repair before `submit` can succeed. The agent has no fixed round, tool-call, invalid-submit, or overall generation timeout; server shutdown still cancels active work.

Every generated definition receives a rarity from Common through Epic, Legendary, and Mythical that colors its name in item tooltips, chat announcements, and the deletion menu, plus a short AI-authored tooltip description. Generated blocks currently support material sound profiles, logical hardness (including instant breaking), preferred mining tools, correct-tool drop requirements, full-cube/slab/no-collision physical profiles, transparent star and cross meshes, upright torches, connecting fences with fence-height collision, and custom models composed from up to 24 axis-aligned cuboids with element-level collision. Every generated block also has a declarative drop table with one primary drop, an optional distinct bonus, fixed or ranged counts, bounded chances, optional ore-like Fortune multiplication, an optional Silk Touch self-drop override, and optional Minecraft-style explosion decay. Drop targets may be the block itself, registered vanilla/mod items, or existing generated content; rules are validated and persisted with the world, evaluated without an AI request when the block breaks, and summarized in the block tooltip. Preset and custom cuboids automatically derive vanilla-style cropped UVs from their dimensions unless the model supplies an explicit 0–16 UV rectangle. Every direction can select a different named texture, and each of up to 12 textures may independently range from 1×1 through 64×64 within a per-model pixel budget. Blocks also support constant redstone and comparator output, true light levels from 0–15, optional full-bright rendering, and up to two budgeted particle emitters. Every generated item, block, or armor definition may additionally own up to four reusable AI-authored particles, each with one to four independently synchronized indexed animation frames, lifetime, linear size and RGBA transitions, gravity, friction, collision, emissive lighting, and spin. Block ambience can mix those custom visuals with the existing vanilla particle profiles, while generated server-side Java can emit custom particles during any supported callback through the stable, rate-budgeted `DynamicParticles` API and Minecraft's native particle packets. Generated content declared as an ordinary item supports stack size, rarity, foil, enchantability, optional reach, and optional food behavior with nutrition, saturation, consumption time, and always-edible control. Generated items can be consumed normally, returned unchanged, or returned with one durability damage when used as AI-recipe ingredients, enabling reusable catalysts, molds, cutters, and hammers. Content declared as a tool additionally requires a pickaxe/axe/shovel/hoe/sword category, vanilla-style breaking power and speed, attack damage and speed, durability, and per-block/per-attack durability costs. Sol chooses a vanilla-derived handheld transform independently from gameplay type: regular flat item, generic tool, reversed rod, bow, crossbow, mace/heavy weapon, or spear/lance. These choices control first- and third-person positioning only, so an ordinary item can use a weapon pose without inheriting that weapon's mechanics or animations. Generated armor requires a head/chest/leggings/boots slot and uses Minecraft's native equipping, dispenser, durability-on-hit, defense, toughness, knockback-resistance, rarity, foil, and enchantability mechanics. Its separately generated 64×32 equipment UV sheet drives a content-addressed runtime humanoid armor layer without a resource reload; Minecraft's separate baby-humanoid UV layout is derived by remapping the authored cuboid faces rather than stretching the icon. Canonical indexed textures are persisted alongside their content-addressed PNGs, so assets can be reconstructed without another AI request. Existing worlds with pre-upgrade armor and single-texture blocks remain loadable through legacy fallbacks, while newly generated content uses the expanded assets.

Generated items may be ordinary materials, food, tools, or placeable decorations. The content AI also decides whether each item is valid furnace fuel and, when it is, its exact burn duration in Minecraft ticks. Fuel behavior is stack-specific despite the shared carrier registry item, works through normal furnace slots and automation, persists with the world, and is summarized in the item tooltip as seconds and standard 200-tick smelts.

The Wand of Deletion lists every generated item, block, and armor piece, preserves selections across pages, and supports deleting any selected set at once. Confirmation atomically updates the world catalog and connected clients, removes unreferenced server assets, clears matching stacks from online/open inventories, loaded entity equipment, and loaded item entities, and removes matching generated blocks that are currently loaded. A generated drop target cannot be deleted while a retained block still references it; selecting the dependent blocks in the same deletion keeps the catalog valid.

Definitions are saved in the current world's `little-chemistry/dynamic-content.json`. Created entries appear in the **Little Chemistry** creative tab and are represented by virtual content IDs carried in synchronized item components and block entities. There is no fixed number of logical item, block, or armor definitions.

Every definition has a server-side Java class loaded in its own class loader. `DynamicBehavior` is an empty marker, while separate one-callback capability interfaces expose item and placed-block events with direct access to the relevant server-side Minecraft objects and the definition. Capability interfaces have no inherited implementations: the generated class opts into and implements only the callbacks it chooses. Compilation is transactional, persisted source is recompiled when the server loads, a failed replacement never enters the catalog, and a behavior that throws while running is disabled. Persisted monolithic classes are upgraded by removing their forced neutral methods and attaching capabilities only to callbacks with actual code. This experimental system executes AI-authored code with the server process's authority and should only be enabled where the server operator trusts generated code.

The server first synchronizes lightweight definitions. Clients reconstruct every content-addressed texture directly from those canonical definitions when possible, verify each SHA-256 hash, decode them off-thread, and publish a catalog revision after all reconstructable GPU textures are registered. Inventory rendering withholds a newly synchronized item or block model until every texture it needs is ready, preventing Minecraft's GUI item atlas from permanently caching a transient missing texture. Network asset requests remain as a compatibility fallback, and received textures are decoded immediately while caching happens independently under `config/little-chemistry/cache/<server-id>/assets/`. This does not install a resource pack, stitch Minecraft's texture atlases, or trigger a global texture, particle, shader, sound, or language reload. One fixed generic particle carrier is registered at bootstrap; logical AI-authored particles remain world-owned runtime definitions rather than registry entries.

Minecraft's built-in item and block registries remain immutable after bootstrap. Little Chemistry registers only a small fixed set of carrier items, one carrier block, and one generic particle type as engine infrastructure; arbitrary logical content is created in the server-owned virtual registry at runtime.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.154.2+26.2
- Java 25

## Build

```bash
./gradlew build
```

The production JAR is written to `build/libs/little-chemistry-1.2.0.jar`.

### Optional automatic Prism installation

Copy `local.properties.example` to `local.properties` and set `prism_mods_dir` to a Prism instance's `mods` directory. `local.properties` is intentionally ignored because it contains a machine-specific path. With that setting present, every successful `build` also installs the JAR into the configured instance.

You can alternatively supply the path for one build:

```bash
./gradlew build -Pprism_mods_dir=/path/to/PrismLauncher/instances/LittleChemistry/.minecraft/mods
```

## License

The project's original code and assets are available under the [MIT License](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the recolored Minecraft stick texture.
