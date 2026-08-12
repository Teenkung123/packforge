# Implementation report

This file starts as the baseline report and is extended after each verified phase. Executed baseline evidence remains in `baseline.md`, `artifact-consolidation.md`, and `compatibility-matrix.md`.

Current status: the ordered Stonecutter/refactor implementation, 22-row exact release expansion, Quick Pack ownership seam, exact runtime matrix, and registry-derived CI wiring are implemented on `codex/packforge-stonecutter-refactor`. Two same-binary range families are verified and promoted: 1.20.2-1.20.4 and 1.20.5-1.21.1. The generated manifest remains at 20 artifacts because the second promotion replaces three anchor artifacts with three wider artifacts. The goal remains open while the remaining Java 21 families are consolidated and range-tested.

Phase 1 evidence: `validateTargetRegistry` and `printResolvedMatrix` pass with 22 exact release cells; a focused all-loader `mc1_21_1` build and artifact verification pass. The first focused attempt found and repaired the child-build schema guard; no functional artifact change was intended.

Phase 2 evidence: Stonecutter 0.9.7 generates `:mc1_21_1`, and `verifyStonecutterCurrentTarget` builds the three standalone loader projects and checks the exact current artifact set. The first delegation was intentionally rejected as a recursive task graph and replaced with direct platform invocations. Follow-up commit `80a35d7bff2a186bf62a3696dc54dcf2dc639e14` restores the repository-level registry/release tasks on the Stonecutter root and wires the pilot verifier to the registry guard.

Phase 5 evidence: `FeaturePolicy` now owns capability/config gating and copies the live config at a policy boundary. `ReloadFeatureSnapshot.capture()` uses one policy instance, while the unchanged `FeatureFlags` facade preserves existing callers. The focused Fabric suite passes with 106 tests.

Phase 6 evidence: the identical 1.21.1/1.21.4/1.21.8 `FilePackResourcesMixin`, `SharedZipFileAccessMixin`, and `ReloadableResourceManagerMixin` implementations now live in `versions/mc1_21_shared/common/src/main/java`. Fabric, Forge, and NeoForge source sets include that root for the three proven targets, while the registry verifier resolves effective ownership and keeps 1.21.11 version-local. Full three-platform target builds and artifact verification pass for all three affected targets.

Phase 6b evidence: the exact duplicate 1.21.1/1.21.4/1.21.8 `PackForgeClient` reset bootstrap now lives in the shared 1.21 client root. The registry verifier enforces one effective owner per target, and all nine affected loader/target builds pass.

The earlier open-work list above is historical baseline context. The final evidence and remaining explicit limits are recorded at the end of this report.

## Current checkpoint: registry, Quick Pack seams, and the 1.20.2 feasibility cell

The current branch now has a schema-v2 registry with 22 checked-in exact release rows and twelve source families. The registry distinguishes planned cells from the six public anchor targets; planned cells do not enter `buildAllSupported` or the 17-artifact release verifier. Stonecutter generates the exact target projects used by the build and smoke matrices, including the focused legacy nodes.

The unified configuration schema/effective-state model and native screen adapters are committed. Quick Pack detection is clean-room metadata detection only, with strict ownership of overlapping index, ZIP pool, font preselection, atlas-mip, loading-fade, and status-overlay paths. Version metadata is informational only: any loaded Quick Pack version receives the same six-module handoff, and unreadable metadata fails safely to that handoff. The real Quick Pack 1.5.0 Fabric profile passed ten reloads and clean exit; 1.4 and older remain best-effort/not guaranteed, and unrelated third-party pairwise profiles remain `UNTESTED`.

The 1.20.2 source-family feasibility cell is now build/package/runtime verified for Fabric, Forge, and NeoForge with the thin constructor/sprite/UI bridge. The earlier ModDev/NeoGradle failures remain historical toolchain evidence; the repaired legacy NeoGradle route now produces a final all-loader result. No 1.20.2 artifact is published or counted in the 17-artifact set because range-publication proof is still separate from exact-cell proof.

Final current-matrix evidence at checkpoint `862022c40249155f1d43fb55d97f3c4cb132c136`: `buildAllSupported --rerun-tasks --no-daemon --stacktrace` PASS in 5m31s; `verifyAllArtifacts --no-daemon --stacktrace` PASS in 5m18s; 17 artifacts collected and structurally verified. These are build/package results, not production startup or resource-reload acceptance. The remaining exact-release, Quick Pack combined-runtime, and candidate-promotion cells are explicitly unexecuted.

## Exact 1.20.3/1.20.4 focused evidence

Checkpoint `948e437` adds separate exact `mc1_20_3` and `mc1_20_4` targets under the shared `mc1_20_3_4` source family. `validateTargetRegistry` passes. The exact 1.20.4 Fabric, Forge, and NeoForge builds pass and `verifyMc1_20_4Artifacts` passes. The exact 1.20.3 Fabric and Forge builds pass; NeoForge 20.3.8-beta fails before source compilation because the official artifact does not expose the `neoforge-moddev-bundle` capability required by ModDev 2.0.141. These are build/package results only: no exact 1.20.3/1.20.4 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and neither exact cell enters publication.

## Exact 1.20.5/1.20.6 focused evidence

Checkpoint `21524f3` adds exact `mc1_20_5` and `mc1_20_6` targets under the shared `mc1_20_5_6` Java21 source family. `validateTargetRegistry` passes. The 1.20.5 Fabric build and artifact verifier pass. The 1.20.6 Fabric, Forge, and NeoForge builds and artifact verifier pass. The family reuses the proven 1.21 shared archive/reload/client implementations and keeps only the 1.20.x `ResourceLocation` and Fabric test constructor helpers version-local. The final JARs contain the family client mixin and archive/reload hooks; loader metadata and the access widener are present where applicable. These are build/package results only: no exact 1.20.5/1.20.6 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and neither exact cell enters publication.

## Exact 1.21/1.21.2/1.21.3 focused evidence

Checkpoint `71de8fe` adds exact `mc1_21`, `mc1_21_2`, and `mc1_21_3` targets while retaining the existing adapter families. `validateTargetRegistry` passes. The exact 1.21 and 1.21.3 Fabric, Forge, and NeoForge builds and artifact verifiers pass. The exact 1.21.2 Fabric and NeoForge builds and artifact verifier pass; Forge is not declared because no official Forge 1.21.2 line is available. The eight final JARs package the shared archive/reload hooks, client bootstrap, `PackSelectionScreenMixin`, and the correct loader metadata. These are build/package results only: no exact 1.21/1.21.2/1.21.3 startup, deterministic reload, semantic hash, Quick Pack, or clean-exit claim is made, and no exact cell enters publication.

## Final exact-matrix and compatibility evidence

The exact release implementation is complete for the required 22-row ledger. `gradle/minecraft-targets.json` now marks the early 1.20.2/1.20.3/1.20.4 rows and source families `planned-verified`; they remain outside the public 17-artifact aggregation by policy. The current public set therefore remains six target anchors and 17 loader artifacts, while the expanded target artifacts are independently built and checked.

Recorded exact-release runtime evidence covers 62 officially declared loader cells across the 22 releases. Each recorded base cell passed startup, deterministic resource reload, semantic/resource evidence where supported, and clean exit. The standard runtime controller requested two reloads. These are exact-target evidence; they do not by themselves prove that one representative binary can claim a wider publication range. The repaired legacy NeoForge production cells are:

```text
1.20.2  NeoForge 20.2.93       6742DF5281A60D580AC6C7BC5A2275398836DCBEB6FD6A7A1E56208BA587711C
1.20.3  NeoForge 20.3.8-beta   7FB0A87F52CE61440452F5D9C1D90976A485653A844870C07EBD7F631B9AE73A
1.20.4  NeoForge 20.4.251      666A7DF5BB402C0551F7572534AD0D2923BA6724180A57E6E7C447ABA2C98203
```

The exact 26.x hotfix loader coordinates are now registry data (`requiredExactSmokeLoaderVersions`) rather than workflow literals. The registry-generated matrix emits 19 build targets, 62 exact smoke cells, 26 public-anchor smoke cells, and 17 publication rows. The official Mojang manifest guard also passes for all 22 release IDs with recorded SHA-256 provenance.

## Quick Pack and default-off decisions

The real Quick Pack 1.5.0 Fabric JAR for 1.21.1 passed a ten-reload production profile beside the final PackForge range artifact. The log reports `status=MODULE_HANDOFF`; version `1.5.0` is diagnostic only. The six overlap capabilities are externally owned, ten requested reloads completed, and the client exited cleanly. Unit tests prove identical handoff for old, future, malformed, and missing version strings. `Smoke-Fabric-Production.ps1` accepts optional additional profile JARs and records their SHA-256 values in provenance without adding a runtime dependency.

Every §16 performance candidate has a recorded `SAFE_KEEP_DEFAULT_OFF` verdict. No candidate was promoted without its controlled performance gate; explicit user configuration remains preserved. Timings, diagnostics, fade, and toast preferences remain outside the performance-candidate table.

## CI/publication wiring

`scripts/Generate-CiMatrix.py` is the single workflow matrix generator. Build, full exact-release runtime smoke, publication smoke, and Modrinth publication rows all consume `gradle/minecraft-targets.json`; the previous manually duplicated release lists were removed. ForgeGradle’s Linux userdev limitation is recorded in the workflows: Forge source smoke remains a correctness check there, while final SRG artifact acceptance stays in the Windows production harness.

## Remaining explicit limits

The exact base matrix and the required Quick Pack profile are proven. Pairwise profiles for unrelated third-party optimization/render mods were not executed and remain `UNTESTED`; no claim is made for them. The 1.20.2-1.20.4 and 1.20.5-1.21.1 rows were promoted only after same-JAR runtime proof on every applicable loader cell. Seven non-anchor pre-26 release rows remain `planned-verified`, so Java 21 artifact consolidation remains incomplete. The latest root build also verifies the repaired modern NeoForge all-in-one packaging structurally; a fresh production smoke on that rebuilt 26.x NeoForge byte set is still a remaining acceptance check.

## First proven range artifact: 1.20.2-1.20.4

Checkpoint `fbd7b3208347230ae17e90f822bde2e100e82992` widens the 1.20.2 anchor into one beta range artifact per loader. Fabric uses legacy `supported_formats` metadata for pack formats 18-22. Forge packages both reload-observer variants and selects the Forge 48 or Forge 49 hook from `FMLLoader.versionInfo().mcVersion()` before mixin application.

All nine exact production cells used the same final JAR for their loader, completed two deterministic reloads, emitted semantic/resource evidence, and exited cleanly:

```text
Fabric   1.20.2, 1.20.3, 1.20.4  77D690FF0D9956271196CBD96406962C052656AAC8B26FF42AD78B40954F4B1A
Forge    1.20.2, 1.20.3, 1.20.4  88F4BB16DD9BEDCEA21100827B7A53794050939F936422F8180B86C451C90547
NeoForge 1.20.2, 1.20.3, 1.20.4  24460EEFB3D33F1B6AD5154758A2E829394138ECD65017C8E19F39F5A1D2BC48
```

`gradlew.bat clean build --no-daemon --console=plain` passed in 13m16s with 107 executed tasks. Rebuilding `buildMc1_20_2` after the clean build reproduced all three tested SHA-256 values exactly. Registry promotion then produced matrix counts `build=19`, `smoke=62`, `publish-smoke=35`, and `publish=20`; release-manifest verification accepted exactly 20 staged artifacts.

## Second proven range artifact: 1.20.5-1.21.1

Checkpoint `5ed1480540162547b3de475e0e9a51e7965d968c` makes the `mc1_21_1` anchor publish one Fabric artifact for 1.20.5-1.21.1 and one Forge/NeoForge artifact for 1.20.6-1.21.1, matching official loader availability. Pack metadata uses supported formats 32-34. Forge uses explicit loader-specific Java-17-compatible mixin descriptors while the compiled code remains Java 21; descriptor ownership is registry data rather than a hard-coded target list.

All ten applicable production cells used the same final JAR for their loader, completed two deterministic reloads, emitted semantic/resource evidence, and exited cleanly:

```text
Fabric   1.20.5, 1.20.6, 1.21, 1.21.1  99D38B658B6D8A19294ADB4D5986BB714016A67C697B91C8FAB01EC7DE5B4E72
Forge    1.20.6, 1.21, 1.21.1          CA9F0EEAF1B382BA13518C7B5C24149BDA6031C33AA4473A8A08CD510ED78FFA
NeoForge 1.20.6, 1.21, 1.21.1          969BFADD9B219932FAB7040839CA6ADAB075EC8B0E941CDED829A22C3A41D5AE
```

The final Fabric artifact also passed a real Quick Pack 1.5.0 profile with ten requested reloads, `status=MODULE_HANDOFF`, all six overlap capabilities delegated, and clean exit. The compatibility policy does not branch on Quick Pack version; 1.4 and older are documented as best-effort/not guaranteed.

`gradlew.bat clean build --no-daemon --console=plain` passed in 8m49s. The clean build reproduced all three runtime-tested SHA-256 values exactly. Registry generation now produces `build=19`, `smoke=62`, `publish-smoke=42`, and `publish=20`; release-manifest verification accepts exactly 20 artifacts.
