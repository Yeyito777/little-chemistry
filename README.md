# Little Chemistry

Little Chemistry is an experimental Fabric mod about using AI to make everything craftable in Minecraft.

The goal is simple: when a player tries to craft something whose recipe does not exist—or whose generated recipe has not yet been cached for the current server—the mod asks AI to invent a fitting recipe and makes it appear. Once generated, the server caches the recipe so everyone can reuse it without another AI request.

The current first milestone provides:

- A dedicated **Little Chemistry** creative-mode tab.
- One item: `little_chemistry:wand_of_creation`.
- A blue/purple wand based on Minecraft's stick texture.
- Right-clicking the wand sends **Behold!** in green chat text.
- Image-generated chemistry artwork for the mod and Prism instance branding.

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
