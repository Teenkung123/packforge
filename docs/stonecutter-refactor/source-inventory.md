# Source inventory

Date: 2026-08-13

Baseline production inventory remains in `baseline.md`. Current deterministic snapshot is checked in under the source-metrics reports.

## Current architecture

- Root `common` contains loader-neutral algorithms, configuration, ownership policy, compatibility reporting, diagnostics, and tests.
- `versions/shared` and version-family roots contain canonical Minecraft-facing implementations plus thin API/descriptor bridges.
- `platform/fabric`, `platform/forge`, and `platform/neoforge` retain loader build logic and loader-specific integration.
- `gradle/minecraft-targets.json` owns release, loader, Java, source-policy, capability, metadata, and publication selections.
- Registry generation defines 12 source families and 19 registry build targets (direct source nodes), then expands them into 53 authoritative direct loader distributions. Public aggregate graph is direct-only.
- Nested Gradle remains a temporary parity oracle; it is not authoritative production ownership.

This is a direct registry-derived build graph. Native Stonecutter preprocessing is proven only for Fabric archive capture across 16 targets; general cross-loader source transport and full all-53 build/package parity remain open.

## Core seams

- `PackIndex`, `PackArchiveState`, `ZipReadPool`, and `ZipFilePools` own ZIP indexing and lifecycle.
- `OrderedAsync`, `CoalescingExecutor`, and `ModelSchedulingPlan` own bounded ordered work.
- `PackForgeConfig`, `PackForgeConfigScreenModel`, `PackForgeConfigDraft`, `PackForgeCapabilityProfile`, `FeaturePolicy`, `FeatureFlags`, and `ReloadFeatureSnapshot` own config/capability decisions.
- `ReloadExecutionContext`, `ReloadLifecycle`, `ReloadHooks`, and `ReloadSessionTracker` own reload identity and lifecycle.
- `QuickPackCompatibility` owns six-capability handoff policy; `CompatibilityProfileReporter` owns opt-in runtime profile evidence.
- Root validation enforces source ownership, direct-node contracts, metadata, class floors, artifact declarations, renderer structure, and duplicate limits.

## Current deterministic metrics

| Metric | Current |
|---|---:|
| Production Java files | 213 |
| Production nonblank LOC | 16,430 |
| Bridge files | 106 |
| Bridge LOC | 5,410 (32.93%) |
| Exact duplicate groups | 0 |
| Normalized duplicate groups | 0 |
| Target/version conditional lines | 15 |
| Stonecutter conditional blocks | 5 (maximum 40 lines) |
| Renderer bodies | 3 |
| Renderer adapters | 2 |
| Registry publication artifacts | 20 |

Baseline was 212 production files and 16,837 LOC with 20 normalized duplicate groups. Current source has one additional production file, removes all measured duplicate groups, and is 407 LOC lower, a 2.42% raw LOC reduction. It does not meet a 20-25% raw shrink target; added registry, compatibility, validation, and reporter capabilities must remain explicitly justified rather than recast as shrink proof.

Three renderer bodies plus two adapters are measured structure, not live UI parity. Twenty publication artifacts is registry output, not proof that 20 current JARs were built or verified.

## Version-specific boundary

Keep Minecraft class names/descriptors, mixin targets, native widget differences, access wideners, remap/refmap behavior, and loader packaging in thin bridges. Shared roots own loader-neutral behavior. New target-key branches or copied production classes must fail the metric/ownership gates unless a documented adapter boundary requires them.

## Remaining proof

Extend preprocessing beyond the Fabric archive pilot, run current all-53 compilation/package checks, prove Java 17/21/25 and remap/JarJar output, remove the nested parity oracle, and then refresh metrics. Runtime and release acceptance remain separate from this inventory.
