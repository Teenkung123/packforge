# Compatibility validation matrix

Outcome meanings:

- `FULL_OPTIMIZED_PATH`: optimized path and required runtime checks passed.
- `HOOK_PRESERVING_COALESCED_PATH`: original extension hooks remain authoritative while PackForge bounds scheduling.
- `SAFE_ORIGINAL_PATH`: optimization deliberately bypassed for compatibility.
- `STARTUP_VERIFIED`: exact artifact reached vanilla startup readiness; repeated reload and third-party profile acceptance remain separate.
- `UNTESTED`: no live acceptance evidence in this worktree.
- `FAILED`: a required check ran and failed.

## Current evidence

| Profile | Outcome | Evidence / remaining check |
|---|---|---|
| Forge 1.20.1 `47.0.0`, final integrated beta.2 JAR | `SAFE_ORIGINAL_PATH` | exact JAR SHA-256 `d8fe15c7...adbd` reached readiness; controlled termination; no F3+T series |
| Forge 1.20.1 `47.4.20`, crash-hotfix beta.2 JAR | `SAFE_ORIGINAL_PATH` | earlier Modrinth production startup reached atlas creation; final integrated JAR not rerun; no F3+T series |
| Forge 1.20.1 `47.4.22`, final integrated beta.2 JAR | `SAFE_ORIGINAL_PATH` | exact JAR SHA-256 `d8fe15c7...adbd` reached readiness after runtime-discovered Mixin fixes; controlled termination; no F3+T series |
| Primary Fabric vanilla | `STARTUP_VERIFIED` | local exact-final-artifact runs reported `cleanExit=true` on every supported target; shared artifact checked on actual 26.1 and 26.2 |
| Primary Forge vanilla | `STARTUP_VERIFIED` | exact final artifacts reached startup readiness on every supported target; shared artifact checked on actual 26.1 and 26.2; controlled termination |
| Primary NeoForge vanilla | `STARTUP_VERIFIED` | local exact-final-artifact runs reported `cleanExit=true` on every supported target; shared artifact checked on actual 26.1 and 26.2 |
| Fabric + Sodium | `UNTESTED` | exact profile unavailable |
| Fabric + Sodium + Iris | `UNTESTED` | exact profile unavailable |
| Fabric + ImmediatelyFast | `UNTESTED` | exact profile unavailable |
| Fabric + Continuity | `UNTESTED` | exact profile unavailable |
| Fabric + CIT/model extension | `UNTESTED` | fixture and exact profile pending |
| Fabric + ETF/EMF | `UNTESTED` | exact profile unavailable |
| Fabric + Vulkan integration | `UNTESTED` | exact profile unavailable |
| Fabric + ResourcePack Unbounded | `UNTESTED` | exact profile unavailable; ownership bypass must remain |
| Forge + Embeddium/Oculus | `UNTESTED` | exact profile unavailable |
| NeoForge + Embeddium/Oculus | `UNTESTED` | exact profile unavailable |

Build proof is not runtime proof. Matrix outcomes will be updated only from exact artifact/profile evidence. Missing external profiles are reported as unexecuted, not inferred compatible.

The model path is intentionally `HOOK_PRESERVING_COALESCED_PATH` only when model features are requested and no platform compatibility guard is active. Guarded profiles use `SAFE_ORIGINAL_PATH`. These labels describe selected code paths, not live acceptance outcomes.

## Final build/test/package coverage

| Target family | Clean evidence |
|---|---|
| 1.20.1 | Fabric + Forge |
| 1.21.1 | Fabric + Forge + NeoForge |
| 1.21.4 | Fabric + Forge + NeoForge |
| 1.21.8 | Fabric + Forge + NeoForge |
| 1.21.11 | Fabric + Forge + NeoForge |
| 26.1-26.2 | Fabric + Forge + NeoForge |

`gradlew.bat clean buildAllSupported verifyAllArtifacts --no-daemon --console=plain` passed in 5m26s with 32/32 root tasks executed after the final startup fixes. The verifier accepted exactly 17 artifacts and checked metadata, configured mixin classes, refmap/selector safety, structural operation anchors, duplicate-free packaging, and target/platform cardinality. Startup acceptance then used those exact `build/libs` artifacts across every supported target/platform cell. This does not substitute for the still-unexecuted third-party compatibility profiles or repeated F3+T series.

## Required target-wide checks

Every one of the 17 target/platform artifacts must retain correct metadata, class version, capabilities, mixin configuration, refmap behavior, and duplicate-free packaging. Each exact runtime cell additionally requires startup, deterministic fixture reload, semantic hash comparison, repeated reloads, no injection failure, and no resource-leak evidence.
