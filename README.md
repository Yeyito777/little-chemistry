# Little Chemistry

Little Chemistry is an experimental Fabric mod about using AI to make everything craftable in Minecraft.

The goal is simple: when a player tries to craft something whose recipe does not exist—or whose generated recipe has not yet been cached for the current server—the mod asks AI to invent a fitting recipe and makes it appear. The crafting snapshot resolves generated ingredients to their complete native gameplay properties and a bounded view of any hot-loaded behavior, so the model reasons about the logical item, block, or armor piece instead of its shared carrier registry entry. Once generated, the server caches the recipe so everyone can reuse it without another AI request.

The current first milestone provides:

- A dedicated **Little Chemistry** creative-mode tab.
- One item: `little_chemistry:wand_of_creation`.
- A blue/purple wand based on Minecraft's stick texture.
- Right-clicking the wand opens an in-game creation screen for naming a new item, block, or armor piece.
- A red-orange **Wand of Deletion** opens a multi-select menu for removing generated definitions.
- Image-generated chemistry artwork for the mod and Prism instance branding.
- Server-owned dynamic items, blocks, and armor, including synchronized generated textures.
- A private in-game AI command backed by `gpt-5.6-sol` in fast service mode:

  ```mcfunction
  /littlechemistry llm "How do brewing stands work?"
  ```

Only the invoking player receives the answer. Requests offer the model no tools and use medium reasoning.

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

Creation runs asynchronously through the selected fast-service `gpt-5.6-luna`, `gpt-5.6-terra`, or `gpt-5.6-sol` content compiler using low reasoning. The server has no limit on the number of distinct generation jobs that can run in parallel, including multiple requests from the same player, while reserving each normalized content name until its job is committed. The model receives the requested name and kind as design data, infers an armor slot from the name when needed, and mutates an isolated draft through tools. Its vanilla-content fetch tools return set-texture-compatible artwork and relevant gameplay metadata for similar items, blocks, and equipment, including tool behavior, reach, durability, food/equipment traits, hardness, light, collision, redstone traits, and mining requirements. Armor additionally exposes a dedicated fetch tool with UV-authoring instructions and complete vanilla humanoid equipment-sheet references. The model supplies an indexed 16×16 inventory texture and native block/item/armor properties; armor submission also requires a separate indexed 64×32 worn display texture and cannot reuse the inventory icon. It must make an explicit behavior plan: ordinary passive materials, blocks, tools, foods, placeables, and armor use native mechanics without generated Java, while conventionally active artifacts such as wands and machines receive one restrained, name-appropriate ability rather than being silently treated as decorative. Optional Java classes override only the callbacks they need. When custom behavior is planned, a compile tool hot-compiles it against the running Minecraft and mod classes and returns diagnostics for the model to repair before `submit` can succeed. The agent has no fixed round, tool-call, invalid-submit, or overall generation timeout; server shutdown still cancels active work.

Generated blocks currently support material sound profiles, logical hardness (including instant breaking), preferred mining tools, correct-tool drop requirements, full-cube/slab/no-collision physical profiles, transparent non-colliding star meshes, connecting fence meshes with fence-height collision, constant redstone and comparator output, true light levels from 0–15, optional full-bright rendering, and up to two budgeted particle emitters. Generated content declared as an ordinary item supports stack size, rarity, foil, enchantability, optional reach, and optional food behavior with nutrition, saturation, consumption time, and always-edible control. Content declared as a tool additionally requires a pickaxe/axe/shovel/hoe/sword category, vanilla-style breaking power and speed, attack damage and speed, durability, and per-block/per-attack durability costs. Sol chooses the regular or tool-style handheld model transform independently from those gameplay types, so an ordinary item can still be posed like a rod, staff, or weapon. Generated armor requires a head/chest/leggings/boots slot and uses Minecraft's native equipping, dispenser, durability-on-hit, defense, toughness, knockback-resistance, rarity, foil, and enchantability mechanics. Its separately generated 64×32 equipment UV sheet drives a content-addressed runtime humanoid armor layer without a resource reload; Minecraft's separate baby-humanoid UV layout is derived by remapping the authored cuboid faces rather than stretching the icon. Both canonical indexed textures are persisted alongside their content-addressed PNGs, so assets can be reconstructed without another AI request. Existing worlds with pre-upgrade armor remain loadable through the legacy icon-based fallback, while all newly generated armor requires proper worn artwork.

The Wand of Deletion lists every generated item, block, and armor piece, preserves selections across pages, and supports deleting any selected set at once. Confirmation atomically updates the world catalog and connected clients, removes unreferenced server assets, clears matching stacks from online/open inventories, loaded entity equipment, and loaded item entities, and removes matching generated blocks that are currently loaded.

Definitions are saved in the current world's `little-chemistry/dynamic-content.json`. Created entries appear in the **Little Chemistry** creative tab and are represented by virtual content IDs carried in synchronized item components and block entities. There is no fixed number of logical item, block, or armor definitions.

Optional generated behavior is server-side Java loaded in a dedicated class loader only for definitions that need it. The behavior API exposes callbacks for item and placed-block events with direct access to the relevant server-side Minecraft objects and the definition. Unimplemented callbacks inherit native-preserving defaults, and returning `PASS` from an implemented interaction callback preserves ordinary placement, food consumption, armor equipping, or block-item behavior. Compilation is transactional, persisted source is recompiled when the server loads, a failed replacement never enters the catalog, and a behavior that throws while running is disabled. This experimental mode intentionally executes AI-authored code with the server process's authority and should only be enabled where the server operator trusts generated code.

The server first synchronizes lightweight definitions. Clients reconstruct content-addressed textures directly from those canonical definitions when possible, verify the SHA-256 hash, decode them off-thread, and publish a catalog revision after its reconstructable GPU textures are registered. Network asset requests remain as a compatibility fallback, and received textures are decoded immediately while caching happens independently under `config/little-chemistry/cache/<server-id>/assets/`. This does not install a resource pack, stitch Minecraft's texture atlases, or trigger a global texture, shader, sound, or language reload.

Minecraft's built-in item and block registries remain immutable after bootstrap. Little Chemistry registers only a small fixed set of carrier items and one carrier block as engine infrastructure; arbitrary logical content is created in the server-owned virtual registry at runtime.

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
