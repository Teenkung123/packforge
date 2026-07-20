# PackForge Minecraft Adapters

Minecraft-facing code is selected exclusively from the adapter declared by `apiAdapter` in `gradle/minecraft-targets.json`.

Each adapter may contain:

- `versions/<adapter>/common/src/main` and `src/client` for Minecraft-version-specific shared code and resources.
- `versions/<adapter>/fabric/src/main` and `src/client` for Fabric-specific adaptations.
- `versions/<adapter>/forge/src/main` and `src/client` for Forge-specific adaptations.
- `versions/<adapter>/neoforge/src/main` and `src/client` for NeoForge-specific adaptations.

The platform build includes exactly four layers: neutral `common`, the selected version-common adapter, the loader's base sources, and the selected version-loader adapter. It never adds another Minecraft adapter as a fallback.

`mc26` preserves the existing shared Minecraft 26.1-26.2 implementation. The exact legacy adapters are `mc1_21_1`, `mc1_21_4`, `mc1_21_8`, `mc1_21_11`, and `mc1_20_1`.

Keep only Java-17-compatible, Minecraft-independent code in the root `common` tree. A class or resource that imports or targets Minecraft internals belongs in a version adapter, even when several adapters initially contain identical copies.
