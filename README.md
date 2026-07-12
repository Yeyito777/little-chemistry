# Little Chemistry

Little Chemistry is an experimental Fabric mod about using AI to make everything craftable in Minecraft.

The goal is simple: when a player tries to craft something whose recipe does not exist—or whose generated recipe has not yet been cached for the current server—the mod asks AI to invent a fitting recipe and makes it appear. Once generated, the server caches the recipe so everyone can reuse it without another AI request.

The current first milestone provides:

- A dedicated **Little Chemistry** creative-mode tab.
- One item: `little_chemistry:wand_of_creation`.
- A blue/purple wand based on Minecraft's stick texture.
- Right-clicking the wand opens an in-game creation screen for naming a new item or block.
- Image-generated chemistry artwork for the mod and Prism instance branding.
- Server-owned dynamic items and blocks, including synchronized generated textures.
- A private in-game AI command backed by `gpt-5.6-luna`:

  ```mcfunction
  /littlechemistry llm "How do brewing stands work?"
  ```

  Only the invoking player receives the answer. Requests offer the model no tools and use no reasoning for fast responses.

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
```

The Wand of Creation provides the same operation without typing a command: hold it, right-click, enter a name, choose **Item** or **Block**, and press **Done**. **Cancel** or Escape closes the screen without creating anything. Creation still uses the server's `little_chemistry:command.create` permission (administrator level by default), and the server validates that the requester is holding the wand.

Definitions are saved in the current world's `little-chemistry/dynamic-content.json`. Created entries appear in the **Little Chemistry** creative tab and are represented by virtual content IDs carried in synchronized item components and block entities. There is no fixed number of item or block slots.

The server first synchronizes lightweight definitions. Clients immediately show the new entry with Minecraft's missing-texture fallback, request only texture hashes absent from their cache, verify each received PNG with SHA-256, decode it off-thread, and upload it directly as a runtime GPU texture. Assets are cached under `config/little-chemistry/cache/<server-id>/assets/`. This does not install a resource pack, stitch Minecraft's texture atlases, or trigger a global texture, shader, sound, or language reload.

Minecraft's built-in item and block registries remain immutable after bootstrap. Little Chemistry registers only one carrier item and one carrier block as engine infrastructure; arbitrary logical content is created in the server-owned virtual registry at runtime.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.154.2+26.2
- Java 25

## Build

```bash
./gradlew build
```

The production JAR is written to `build/libs/little-chemistry-1.0.0.jar`.

### Optional automatic Prism installation

Copy `local.properties.example` to `local.properties` and set `prism_mods_dir` to a Prism instance's `mods` directory. `local.properties` is intentionally ignored because it contains a machine-specific path. With that setting present, every successful `build` also installs the JAR into the configured instance.

You can alternatively supply the path for one build:

```bash
./gradlew build -Pprism_mods_dir=/path/to/PrismLauncher/instances/LittleChemistry/.minecraft/mods
```

## License

The project's original code and assets are available under the [MIT License](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the recolored Minecraft stick texture.
