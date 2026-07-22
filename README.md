# Little Chemistry

Little Chemistry is an experimental Fabric mod about using AI to make everything craftable and smeltable in Minecraft.

The goal is simple: when a player tries to craft or furnace-smelt something whose recipe does not exist—or whose generated recipe has not yet been cached for the current server—the mod asks AI to invent a fitting result, including how many items the recipe produces, and makes it appear. Recipe snapshots resolve generated ingredients to their complete native gameplay properties and full persisted Java behavior source, so the model reasons about the logical item, block, armor piece, or entity spawner instead of its shared carrier registry entry. Once generated, the server caches the recipe and its output count so everyone can reuse it without another AI request. Crafting tables and normal furnaces expose the same **Make Recipe** workflow; furnace recipes use Minecraft's native fuel, 200-tick cooking cycle, result stacking, and experience handling.

The current first milestone provides:

- A dedicated **Little Chemistry** creative-mode tab.
- Three utility items: the creation and deletion wands plus `little_chemistry:crafting_table_on_a_stick`.
- A blue/purple wand based on Minecraft's stick texture.
- Right-clicking the wand opens an in-game creation screen for naming a new item, block, armor piece, or entity.
- A red-orange **Wand of Deletion** opens a multi-select menu for removing generated definitions.
- A portable **Crafting Table on a Stick** opens a full 3×3 crafting grid with Little Chemistry's **Make Recipe** action.
- AI-defined generated blocks can become persistent **custom workstations** with their own slots, UI, process prompt,
  generated Java mechanics, and server-wide runtime recipe cache.
- Image-generated chemistry artwork for the mod and Prism instance branding.
- Server-owned dynamic items, blocks, armor, and entities, including synchronized generated textures, cuboid models, and animated vanilla entity-model profiles.
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

## Custom workstations

Whenever the content AI creates a block, it must explicitly classify the block as ordinary or as a workstation. A
workstation remains a normal runtime Little Chemistry block, but its definition additionally owns a persistent process
description, a declarative output policy for every recipe invented inside it, a closed schema for process-specific
recipe data, named inventory slots, and a complete declarative screen layout. The AI controls slot roles and positions,
title and player-inventory placement, colors, labels, gauges, state channels, the **Make Recipe** control, and optional
custom buttons. One fixed client screen renders every validated layout; clients never compile Java received from a
server.

The workstation's generated `GeneratedBehaviorImpl` supplies the mechanics. Its pure recipe callback captures exact
named slots and decides required counts plus consume, retain, or damage semantics. Its server-tick callback
can implement instant assembly, timed pressure or heat, fuel, redstone gates, environmental checks, probabilistic
stages, custom particles, and arbitrary Minecraft-side effects. Optional generated capabilities control player slots,
sided automation, and custom buttons. Generated behavior objects are shared definition singletons, so placement-local
values live in the engine's bounded persistent state and synchronized UI channels rather than Java fields.

For an uncached valid input capture, the screen offers **Make Recipe** and locks only the captured slots while the AI
works. Output selection uses the workstation's declarative output policy and recipe-data schema, then the ordinary content
compiler creates the resulting item, block, armor, or entity spawner with native properties, textures, particles, and Java behavior.
The exact recipe is cached server-wide by workstation identity, process ID, normalized item components, required counts,
uses, and an optional bounded discriminator, so every placement can reuse it. Descriptive AI context is not part of that
identity; generated behavior must put every output-affecting machine mode or environmental value in the discriminator.
Normal completion uses an atomic engine
transaction that revalidates inputs, verifies output capacity, applies ingredient uses, and inserts the result without
partial consumption.

Placed workstation inventories, process state, and UI state are saved in the block entity and survive menu closure,
chunk unload, and server restart. Workstations support vanilla hoppers and Fabric item-transfer automation through the
same generated slot rules. In-flight AI locks are deliberately not persisted, so an interrupted server unlocks the
items on restart. Cached workstation recipes are stored beside the existing AI crafting and smelting recipes and are
removed when their workstation, result, or a dynamic ingredient is deleted.

## Dynamic server content

Server administrators can create content while the server is running:

```mcfunction
/littlechemistry item create cobalt dust
/littlechemistry block create quantum stone
/littlechemistry armor head create stellar crown
/littlechemistry armor chest create cobalt cuirass
/littlechemistry armor leggings create mercury greaves
/littlechemistry armor boots create cloud boots
/littlechemistry entity create crystal golem
/littlechemistry entity spawn crystal_golem
```

Generation test suites can replay a recorded batch of crafting inventions through the ordinary physical-table workflow:

```mcfunction
/littlechemistry test 1
```

Suite `1` contains the ten-recipe V4 manual run (phantom boat, wildflower crown, door barricade, both custom crossbows, lapis crafting table, twin and magma furnaces, and gilded nautilus armor) plus a backpack regression grid with a chest in the center, string above it, and leather in the other seven surrounding slots. The command requires a player with creation permission and a clear eleven-block row four blocks ahead. It places and preloads all eleven shared crafting tables, then submits each unknown grid through the same ordinary `AiCraftingManager.requestRecipe` path as player crafts, with no test-only logging behavior. Recipes already known in the current world remain populated but are not regenerated; run the suite in a fresh test world for a full eleven-job replay. Additional suites are data files under `data/little_chemistry/generation_test/`.

Exocortex logging is a persistent mod setting and is **on by default**. Every new AI recipe-generation job—crafting table, furnace, or generated workstation—resolves exactly one existing top-level `littlechemistry` Exocortex folder with exactly one direct child named `logs`. When that exact path and the live daemon socket exist, the terminal conversation is atomically imported there and registered through the daemon. Little Chemistry never creates, guesses, or case-folds an Exocortex folder; when the setting is off or the exact target is absent, the conversation remains in its per-world logs only. Administrators can inspect or change the setting with:

```mcfunction
/littlechemistry settings exocortex-logging
/littlechemistry settings exocortex-logging on
/littlechemistry settings exocortex-logging off
```

The value is stored as `"exocortex-logging"` in `config/little-chemistry/settings.json`.

The Wand of Creation provides the same operation without typing a command: hold it, right-click, enter a name, choose **Item**, **Block**, **Armor**, or **Entity**, choose **Luna**, **Terra**, or **Sol**, and press **Done**. For armor, the selected model infers the equipment slot from the name instead of asking for a separate slot. **Cancel** or Escape closes the screen without creating anything. Creation still uses the server's `little_chemistry:command.create` permission (administrator level by default), and the server validates that the requester is holding the wand.

Creation runs asynchronously through the selected fast-service `gpt-5.6-luna`, `gpt-5.6-terra`, or `gpt-5.6-sol` coding agent using medium reasoning. Each world owns a real source tree at `little-chemistry/generation-workspace/`, organized into `blocks/`, `armors/`, `items/`, `entities/`, `particles/`, `textures/`, `workstations/`, and `helpers/`. Concurrent requests work in isolated job branches, stage only their content-specific source directories after verification, and publish that immutable source snapshot only after the runtime definition commits on the server thread. Pending source is bound to the exact canonical definition digest and recovered after an interrupted commit; recipe/content pairs use a second journal that either recognizes the persisted recipe or rolls back orphan content at startup. Existing definitions are exported there as complete canonical JSON and untruncated behavior Java, so future generations can grep and read the world's actual mod history instead of querying a special inspection tool.

The model now receives the same kind of general coding tools used by Exocortex—`bash`, `read`, `grep`, `glob`, `write`, exact `edit`, and unified `patch`—plus one final `verify` build boundary. There are no content-property setter tools, no in-memory draft, no `choose_output` handoff, and no `submit` tool. The model-facing user turn is a natural content request rather than an instruction to open workspace metadata: fixed requests ask it to generate and code the named Minecraft item/block/armor/entity, while recipe requests present the complete recipe directly and ask it to infer the natural content kind. Every request explicitly requires inspecting relevant vanilla or modded textures and studying their palettes, pixel arrangements, silhouettes, shading, and UV/layout conventions before creating original artwork. Wand requests provide a fixed factory path; recipe and workstation requests choose their output in a small source-controlled `result.json` and author the result in the same agent pass. Generated factories are ordinary multi-file Java implementing the tiny `GeneratedContentFactory` contract. They may directly use every public Minecraft, Fabric, and Little Chemistry class, existing world helpers, or the optional composable builder utilities. Textures, models, particles, workstation policies, and native properties are all constructed in that code.

Every AI job also writes a durable per-world conversation under `little-chemistry/generation-workspace/logs/<UTC-day>/<timestamp>--<job-id>/`. `<job-id>.json` is a native Exocortex v17 conversation file that can be copied or imported directly as a conversation; `conversation.json` is an identical stable alias. Its native stored messages contain the immutable system instructions, user request, assistant reasoning summaries and text, structured tool calls, exact tool results, timing/token metadata, OpenAI response IDs and encrypted reasoning continuation records, plus terminal verification or failure. `responses-replay.json` separately preserves the tool schemas, generation request, and exact ordered Responses-API input items for low-level replay. `events.jsonl` is append-only and adds precise event timestamps, response metadata, exact output items, tool arguments/results, execution durations, verification, and stack traces; every record is flushed before generation continues. `transcript.md` is a bounded human-readable projection. Logs never contain the OpenAI authorization token. OpenAI does not expose private raw chain-of-thought, so the logs preserve every reasoning field the provider actually returns rather than inventing an unavailable trace.

The workspace exposes a searchable runtime class index as a virtual source tree. Reading `reference/classes/net/minecraft/.../SomeClass.java` lazily reconstructs the installed class-file family with embedded Vineflower, including method bodies; Little Chemistry and Fabric classes work the same way. A second indexed virtual tree materializes installed vanilla item, block, armor-equipment, and entity PNGs as text-readable palette/row JSON only when the agent reads them. Final verification uses the runtime-aware in-process Eclipse compiler, compiles the separate self-contained `GeneratedBehaviorImpl`, executes the factory, and enforces request type/count, workstation recipe-data schema and output capacity, callback applicability, model/texture hashes and budgets, texture visibility/contrast and armor UV coverage, particle references, and all record-level gameplay requirements. Diagnostics go back to the same coding loop until the build passes. The agent has no short overall generation timeout; server shutdown still cancels active work.

Generated entities use a fixed bootstrap-time carrier matrix for ground/flying movement and creature/monster semantics, so logical entity definitions remain compatible with registry freeze, networking, saves, and existing worlds. An entity definition supplies dimensions, health, speed, attack, armor, knockback resistance, follow range, passive/neutral/hostile targeting, sounds, experience, fire immunity, drops, and a 16×16 spawner icon. For its world appearance the AI can either author an original rigid model of up to 24 cuboids and 12 textures, or read a complete indexed vanilla entity UV sheet from the workspace artwork mirror, recolor or edit it, and reuse a compatible animated zombie, skeleton, enderman, cow, pig, spider, creeper, blaze, or cod model profile. Reused profiles retain Minecraft's articulated walk/look animation but do not inherit the reference mob's gameplay, equipment, emissive eyes, overlays, or other special render layers. Entity spawner items appear in the creative tab and can also be generated as recipe outputs; use one on a block to spawn its logical entity. Each instance persists its content ID and a bounded per-instance key/value state container. Generated Java can opt into spawn, server-tick, player-interaction, hurt, attack, and death callbacks. Additional model families and generated projectiles remain future carrier extensions.

Every generated definition receives a rarity from Common through Epic, Legendary, and Mythical that colors its name in item tooltips, chat announcements, and the deletion menu, plus a short AI-authored tooltip description. Generated blocks currently support material sound profiles, logical hardness (including instant breaking), preferred mining tools, correct-tool drop requirements, full-cube/slab/no-collision physical profiles, transparent star and cross meshes, upright torches, connecting fences with fence-height collision, and custom models composed from up to 24 axis-aligned cuboids with element-level collision. Every generated block also has a declarative drop table with one primary drop, an optional distinct bonus, fixed or ranged counts, bounded chances, optional ore-like Fortune multiplication, an optional Silk Touch self-drop override, and optional Minecraft-style explosion decay. Drop targets may be the block itself, registered vanilla/mod items, or existing generated content; rules are validated and persisted with the world, evaluated without an AI request when the block breaks, and summarized in the block tooltip. Preset and custom cuboids automatically derive vanilla-style cropped UVs from their dimensions unless the model supplies an explicit 0–16 UV rectangle. Every direction can select a different named texture, and each of up to 12 textures may independently range from 1×1 through 64×64 within a per-model pixel budget. Blocks also support constant redstone and comparator output, true light levels from 0–15, optional full-bright rendering, and up to two budgeted particle emitters. Every generated item, block, armor, or entity definition may additionally own up to four reusable AI-authored particles, each with one to four independently synchronized indexed animation frames, lifetime, linear size and RGBA transitions, gravity, friction, collision, emissive lighting, and spin. Block ambience can mix those custom visuals with the existing vanilla particle profiles, while generated server-side Java can emit custom particles during any supported callback through the stable, rate-budgeted `DynamicParticles` API and Minecraft's native particle packets. Generated content declared as an ordinary item supports stack size, rarity, foil, enchantability, optional reach, and optional food behavior with nutrition, saturation, consumption time, and always-edible control. Generated items can be consumed normally, returned unchanged, or returned with one durability damage when used as AI-recipe ingredients, enabling reusable catalysts, molds, cutters, and hammers. Content declared as a tool additionally requires a pickaxe/axe/shovel/hoe/sword category, vanilla-style breaking power and speed, attack damage and speed, durability, and per-block/per-attack durability costs. Sol chooses a vanilla-derived handheld form: regular flat item, generic tool, reversed rod, bow, crossbow, mace/heavy weapon, or spear/lance. Regular, rod, mace, and spear choices affect first- and third-person positioning only; bow and crossbow deliberately select native projectile-weapon mechanics and animations. Generated armor requires a head/chest/leggings/boots slot and uses Minecraft's native equipping, dispenser, durability-on-hit, defense, toughness, knockback-resistance, rarity, foil, and enchantability mechanics. Its separately generated 64×32 equipment UV sheet drives a content-addressed runtime humanoid armor layer without a resource reload; Minecraft's separate baby-humanoid UV layout is derived by remapping the authored cuboid faces rather than stretching the icon. Canonical indexed textures are persisted alongside their content-addressed PNGs, so assets can be reconstructed without another AI request. Existing worlds with pre-upgrade armor and single-texture blocks remain loadable through legacy fallbacks, while newly generated content uses the expanded assets.

Generated items may be ordinary materials, food, tools, or placeable decorations. Items with the bow or crossbow held type use native Minecraft drawing, charging, ammunition, firing, sound, enchantment, durability, and charged-projectile mechanics while retaining their generated identity, texture, tooltip, and optional behavior callbacks. Their native use/charge/loaded model predicates reuse the item's single generated artwork rather than requiring separate AI-authored frames. The content AI also decides whether each item is valid furnace fuel and, when it is, its exact burn duration in Minecraft ticks. Fuel behavior is stack-specific despite the shared carrier registry item, works through normal furnace slots and automation, persists with the world, and is summarized in the item tooltip as seconds and standard 200-tick smelts.

The Wand of Deletion lists every generated item, block, armor piece, and entity, preserves selections across pages, and supports deleting any selected set at once. Confirmation atomically updates the world catalog and connected clients, removes unreferenced server assets, clears matching stacks from online/open inventories, loaded entity equipment, and loaded item entities, removes matching generated blocks that are currently loaded, and discards matching loaded entity carriers. A generated drop target cannot be deleted while a retained block still references it; selecting the dependent blocks in the same deletion keeps the catalog valid.

Definitions are saved in the current world's `little-chemistry/dynamic-content.json`. Created entries appear in the **Little Chemistry** creative tab and are represented by virtual content IDs carried in synchronized item components, block entities, and carrier-entity tracked data. There is no fixed number of logical item, block, armor, or entity definitions.

Every definition retains a server-side `GeneratedBehaviorImpl` loaded in its own class loader. `DynamicBehavior` is an empty marker, while separate one-callback capability interfaces expose item, placed-block, workstation, and entity events with direct access to the relevant server-side Minecraft objects and the definition. Capability interfaces have no inherited implementations: the generated class opts into and implements only the callbacks it chooses. The broader factory and helper source remains in the per-world mod workspace for inspection and reuse, while the self-contained behavior source remains in the canonical catalog so it can be recompiled after restart. Compilation is transactional, a failed verification never enters the catalog, and a behavior that throws while running is disabled. The `bash` and `patch` tools run in a Bubblewrap namespace with only the isolated job branch writable, immutable engine inputs over-mounted read-only, no inherited environment, no network, per-process limits, and—when a user systemd session is available—a bounded memory/PID/CPU scope. AI-authored Java still executes with the server process's authority and should only be enabled where the server operator trusts generated code.

The server first synchronizes lightweight definitions. Clients reconstruct every content-addressed texture directly from those canonical definitions when possible, verify each SHA-256 hash, decode them off-thread, and publish a catalog revision after all reconstructable GPU textures are registered. Inventory rendering withholds a newly synchronized item or block model until every texture it needs is ready, preventing Minecraft's GUI item atlas from permanently caching a transient missing texture. Network asset requests remain as a compatibility fallback, and received textures are decoded immediately while caching happens independently under `config/little-chemistry/cache/<server-id>/assets/`. This does not install a resource pack, stitch Minecraft's texture atlases, or trigger a global texture, particle, shader, sound, or language reload. One fixed generic particle carrier is registered at bootstrap; logical AI-authored particles remain world-owned runtime definitions rather than registry entries.

Minecraft's built-in item, block, and entity registries remain immutable after bootstrap. Little Chemistry registers only a small fixed set of carrier items, one carrier block, four movement/disposition carrier entity types, and one generic particle type as engine infrastructure; arbitrary logical content is created in the server-owned virtual registry at runtime.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.154.2+26.2
- Java 25
- Bubblewrap at `/usr/bin/bwrap` on the Linux server host (for isolated generation `bash` and `patch` tools)

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
