# Source inventory

Date: 2026-08-15

Baseline production inventory remains in `baseline.md`. Current deterministic snapshot is checked in under the source-metrics reports.

## Current architecture

- Root `common` contains loader-neutral algorithms, configuration, ownership policy, compatibility reporting, diagnostics, and tests.
- `versions/shared` and version-family roots contain canonical Minecraft-facing implementations plus thin API/descriptor bridges.
- `platform/fabric`, `platform/forge`, and `platform/neoforge` retain loader build logic and loader-specific integration.
- `gradle/minecraft-targets.json` owns release, loader, Java, source-policy, capability, metadata, and publication selections.
- Registry generation defines 12 source families and 19 registry build targets (direct source nodes), then expands them into 53 authoritative direct loader distributions. Public aggregate graph is direct-only.
- The always-on direct helper is authoritative production ownership. The legacy nested Gradle path remains only as an explicit opt-in parity oracle.

This is a direct registry-derived build graph. Native Stonecutter preprocessing has focused Fabric proof for archive capture across 16 targets, SharedZip across 17, ReloadableResourceManager across 18 pre26 targets, RuntimeResourceHash across 17, LoadingOverlayToast across 14, Bitmap provider definition across all 19, and SimpleReload across four targets from 1.21.5 through 1.21.8; general cross-loader source transport and full all-53 build/package parity remain open.

## Core seams

- `PackIndex`, `PackArchiveState`, `ZipReadPool`, and `ZipFilePools` own ZIP indexing and lifecycle.
- `OrderedAsync`, `CoalescingExecutor`, and `ModelSchedulingPlan` own bounded ordered work.
- `PackForgeConfig`, `PackForgeConfigScreenModel`, `PackForgeConfigDraft`, `PackForgeCapabilityProfile`, `FeaturePolicy`, `FeatureFlags`, and `ReloadFeatureSnapshot` own config/capability decisions.
- `ReloadExecutionContext`, `ReloadLifecycle`, `ReloadHooks`, and `ReloadSessionTracker` own reload identity and lifecycle.
- `QuickPackCompatibility` owns four-capability handoff policy, including the conservative unknown-metadata path; `CompatibilityProfileReporter` owns opt-in runtime profile evidence.
- Root validation enforces source ownership, direct-node contracts, metadata, class floors, artifact declarations, renderer structure, and duplicate limits.

## Current deterministic metrics

| Metric | Current |
|---|---:|
| Production Java files | 217 |
| Production nonblank LOC | 16,575 |
| Bridge files | 110 |
| Bridge LOC | 5,555 (33.51%) |
| Exact duplicate groups | 0 |
| Normalized duplicate groups | 0 |
| Target/version conditional lines | 15 |
| Platform target conditional lines | 1 |
| Stonecutter conditional blocks | 10 (maximum 40 lines) |
| Renderer bodies | 3 |
| Renderer adapters | 2 |
| Registry publication artifacts | 20 |

Baseline was 212 production files and 16,837 LOC with 20 normalized duplicate groups. Current source has five additional production files, removes all measured duplicate groups, and is 262 LOC lower, a 1.56% raw LOC reduction.

## Phase F added-LOC exception ledger

The committed baseline is 609270f533666e7636d11f7d16590be925ec836f. The ledger counts nonblank production-Java additions from that baseline to the current source tree, excluding exact-content renames/copies, tests, generated sources, build output, documentation, and resources. It records current-tree additions rather than gross historical insertions, so the enforceable mandatory-added total is 2,347 LOC.

| Production root | Mandatory added LOC |
|---|---:|
| common | 1,051 |
| fabric | 146 |
| platform | 256 |
| versions | 894 |
| **Total** | **2,347** |

The plan requires a 20% reduction of the 16,837-line baseline, or 3,368 LOC. Raw reduction is 262 LOC; adding the 2,347-LOC ledger produces 2,609 LOC (15.50%) of adjusted reduction, leaving a 759-LOC shortfall. The adjusted shrink gate therefore remains FAIL. Normalized duplicate reduction, no byte-identical production group, and version-common-below-neutral-common pass. Platform target-conditional lines are 1 against a 4-line baseline, a 75% reduction; the plan's 80% branch gate remains FAIL. The ledger/report contract is IMPLEMENTED_UNVERIFIED; Phase F cleanup gates remain FAIL.

Three renderer bodies plus two adapters are measured structure, not live UI parity. Twenty publication artifacts is registry output, not proof that 20 current JARs were built or verified.

## Version-specific boundary

Keep Minecraft class names/descriptors, mixin targets, native widget differences, access wideners, remap/refmap behavior, and loader packaging in thin bridges. Shared roots own loader-neutral behavior. New target-key branches or copied production classes must fail the metric/ownership gates unless a documented adapter boundary requires them.

## Remaining proof

Extend preprocessing beyond the bounded Fabric seams, run current all-53 compilation/package checks, prove Java 17/21/25 and remap/JarJar output, remove the optional legacy parity oracle, and then refresh metrics. Runtime and release acceptance remain separate from this inventory.
