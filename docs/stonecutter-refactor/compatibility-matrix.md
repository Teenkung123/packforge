# Compatibility matrix

Outcome labels are the assignment labels: `FULL_OPTIMIZED_PATH`, `HOOK_PRESERVING_COALESCED_PATH`, `SAFE_ORIGINAL_PATH`, `EXTERNALLY_OWNED_PATH`, `UNAVAILABLE`, `UNTESTED`, and `FAILED`.

## Exact release acceptance

The final exact smoke pass covered 22 release rows and 62 officially available loader cells. Each cell used its registry target, final distribution artifact, startup marker, deterministic resource reload controller, semantic/resource evidence where supported, and clean client shutdown. The standard base profile requested two reloads; the Quick Pack profile requested ten.

| Minecraft release | Required loaders | Result | Evidence |
|---|---|---|---|
| 1.20.1 | Fabric, Forge | `FULL_OPTIMIZED_PATH` | Existing beta anchor production smoke and artifact verification. |
| 1.20.2 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final Fabric/Forge/NeoForge artifacts; two reloads and clean exit. |
| 1.20.3 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final Fabric/Forge/NeoForge artifacts; legacy NeoForge descriptor/bootstrap repaired and rerun. |
| 1.20.4 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final Fabric/Forge/NeoForge artifacts; two reloads and clean exit. |
| 1.20.5 | Fabric | `FULL_OPTIMIZED_PATH` | Shared Fabric 1.20.5-1.21.1 artifact; two reloads and clean exit. Official loader availability is Fabric-only. |
| 1.20.6 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Shared Java-21 range artifacts; two reloads and clean exit. |
| 1.21 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Same shared range artifacts; two reloads and clean exit. |
| 1.21.1 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Same shared range artifacts; also the Quick Pack module-handoff profile on Fabric. |
| 1.21.2 | Fabric, NeoForge | `FULL_OPTIMIZED_PATH` | Official Forge line unavailable; Fabric/NeoForge exact cells passed. |
| 1.21.3 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.4 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Existing beta anchor production smoke and artifact verification. |
| 1.21.5 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.6 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.7 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.8 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Existing beta anchor production smoke and artifact verification. |
| 1.21.9 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.10 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Final exact artifacts; two reloads and clean exit. |
| 1.21.11 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Existing beta anchor production smoke and artifact verification. |
| 26.1 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Stable range artifact; exact anchor smoke passed. |
| 26.1.1 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Stable range artifact with registry loader overrides; exact smoke passed. |
| 26.1.2 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Stable range artifact with registry loader overrides; exact smoke passed. |
| 26.2 | Fabric, Forge, NeoForge | `FULL_OPTIMIZED_PATH` | Stable range artifact; exact interior-release smoke passed. |

All pre-26.x rows remain beta and all 26.x rows remain stable. No loader is inferred where the registry declares it unavailable.

## Required compatibility profiles

| Profile | Result | Evidence |
|---|---|---|
| Quick Pack Fabric 1.21.1, version 1.5.0 | `EXTERNALLY_OWNED_PATH` | Real third-party JAR; `status=MODULE_HANDOFF`; six overlap capabilities owned by Quick Pack; ten reloads; clean exit. |
| Any other Quick Pack version string | `EXTERNALLY_OWNED_PATH` | Unit policy tests cover old, future, malformed, and missing versions; version is diagnostic only and the same six modules are handed off. Quick Pack 1.4 and older are best-effort/not guaranteed. |
| Sodium/Iris/ImmediatelyFast/ModernFix/FerriteCore pairwise profiles | `UNTESTED` | Not part of the executed exact-release acceptance run; no claim is made. |
| Official Forge 1.21.2 | `UNAVAILABLE` | No official Forge 1.21.2 line is declared in the registry. |

## Build/package evidence

`validateTargetRegistry`, `printResolvedMatrix`, exact target verifiers, and the current 20-artifact release-manifest verifier pass. Build proof remains separate from runtime proof. Final artifact SHA-256 values are recorded in `artifact-consolidation.md` and the phase entries in `work-log.md`.

## Publication status

The exact-cell evidence above is not a claim that the public manifest covers every row. Current registry-derived publication contains 20 artifacts and 42 publication-smoke cells. Minecraft 1.20.2-1.20.4 resolve to one tested range artifact per loader; Minecraft 1.20.5-1.21.1 resolve to one tested Fabric artifact and one tested Forge/NeoForge artifact over their applicable releases. The remaining seven non-anchor pre-26 rows stay `planned-verified` until their range-artifact proof is recorded. The official Mojang stable-release sequence guard passes for all 22 required IDs.
