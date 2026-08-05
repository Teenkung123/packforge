# Forge 1.20.1 issue #5 incident summary

## Status

Root cause status: `CONFIRMED_FROM_ISSUE_LOG`.

PackForge `1.3.3-beta.1` crashes during initial resource reload on Minecraft 1.20.1 with Forge 47.4.22. The first fatal transformation error is the required `observe.SimpleReloadInstanceMixin` failing to resolve `method_18368` in `SimpleReloadInstance`.

The source selector was copied from Fabric/Yarn intermediary naming into a common adapter packaged for Forge. The final Forge refmap remapped the invocation target but did not map the enclosing `method_18368` selector. The final class constant pool retained the raw selector, so Forge Mixin could not find a target method.

`SpriteLoaderMixin` also retained raw `method_47660` in final bytecode and lacked a matching refmap entry. It was not reached before the first fatal mixin and is treated as a confirmed packaging defect plus a likely next startup failure, not as the first observed exception.

## Impact

- Affected reported artifact: `packforge-forge-1.3.3-beta.1-mc1.20.1.jar`.
- Reported environment: Minecraft 1.20.1, Forge 47.4.22, Java 17.
- ZIP indexing is not the cause and remains enabled by the repair.
- Build and pre-existing artifact checks passed the broken artifact, proving compile/package validation alone was insufficient.

## Repair

- Replace `method_18368` with the stable constructor invocation of `SimpleReloadInstance.StateFactory#create`.
- Replace legacy `method_47660` wrappers with the structural `CompletableFuture.supplyAsync` invocation inside `loadAndStitch`.
- Reject configured Forge/NeoForge mixin classes that retain `method_<number>` or `field_<number>` constants.
- Add checksum-verified runtime smoke for 18 cells: 17 unique target/platform combinations plus the extra reporter Forge 1.20.1 version row. Fabric/NeoForge rows use the verified `build/libs` bytes through `PACKFORGE_ARTIFACT_INPUT_DIR`; Forge rows use source-mode because ForgeGradle userdev cannot validate a final SRG JAR against its Mojmap target.
- Keep production Forge final-JAR validation separate through `scripts/Smoke-Forge-Production.ps1` and make its result an explicit release gate.
- Prepare legacy artifacts as `1.3.3-beta.2`; do not overwrite `1.3.3-beta.1`.

## Evidence level

Issue attachments and published artifacts were retrieved and hashed. Static verification and checksum-bundle handoff are recorded separately from runtime evidence in `release-validation.md`. The exact beta.2 final JAR reached the final atlas marker without a fatal Mixin signature on production Forge `47.0.0`, `47.4.20`, and `47.4.22`. The isolated `47.0.0` and `47.4.22` runs used controlled termination after startup; successful automated F3+T and clean UI-exit acceptance remain open.
