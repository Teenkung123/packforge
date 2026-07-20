# PackForge Version Overlays

Platform builds include optional source/resource overlays from:

- `versions/mc26_1/src`
- `versions/mc26_1_1/src`
- `versions/mc26_1_2/src`
- `versions/mc26_2/src`

Keep shared code in `common` whenever it compiles cleanly across all supported Minecraft targets. Add files here only when a Minecraft client internal changes enough that a target-specific class or resource is required.
