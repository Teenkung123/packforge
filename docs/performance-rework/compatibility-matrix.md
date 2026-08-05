# Compatibility validation matrix

Outcome meanings:

- `FULL_OPTIMIZED_PATH`: optimized path and required runtime checks passed.
- `HOOK_PRESERVING_COALESCED_PATH`: original extension hooks remain authoritative while PackForge bounds scheduling.
- `SAFE_ORIGINAL_PATH`: optimization deliberately bypassed for compatibility.
- `UNTESTED`: no live acceptance evidence in this worktree.
- `FAILED`: a required check ran and failed.

## Current evidence

| Profile | Outcome | Evidence / remaining check |
|---|---|---|
| Forge 1.20.1 `47.0.0`, exact beta.2 JAR | `SAFE_ORIGINAL_PATH` | issue hotfix exact-JAR startup reached final atlas; controlled termination; no F3+T series |
| Forge 1.20.1 `47.4.20`, exact beta.2 JAR | `SAFE_ORIGINAL_PATH` | Modrinth production startup reached atlas creation; no F3+T series |
| Forge 1.20.1 `47.4.22`, exact beta.2 JAR | `SAFE_ORIGINAL_PATH` | issue hotfix exact-JAR startup reached final atlas; controlled termination; no F3+T series |
| Primary Fabric vanilla | `UNTESTED` | build/tests pass; runtime/manifest series pending |
| Primary Forge vanilla | `UNTESTED` | build/tests pass; runtime/manifest series pending |
| Primary NeoForge vanilla | `UNTESTED` | build/tests pass; runtime/manifest series pending |
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

## Required target-wide checks

Every one of the 17 target/platform artifacts must retain correct metadata, class version, capabilities, mixin configuration, refmap behavior, and duplicate-free packaging. Each exact runtime cell additionally requires startup, deterministic fixture reload, semantic hash comparison, repeated reloads, no injection failure, and no resource-leak evidence.

