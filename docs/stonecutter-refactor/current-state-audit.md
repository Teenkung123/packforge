# Current-state audit

Date: 2026-08-15

Audited continuation checkpoints through the Fabric 1.21.5 SimpleReload Phase E boundary. Overall state: **PARTIAL** and **NOT RELEASE READY**.

## Audit boundary

- Preserve unrelated user work: modified `.github/ISSUE_TEMPLATE/bug-report.yml`, untracked `.codegraph/`, and untracked `docs/PackForge_Missing_Implementation_Plan.md` remain outside PackForge implementation checkpoints.
- Current source and artifacts produced from current source are authoritative.
- Historical build/runtime results remain regression evidence only when later source or artifact paths changed.
- Build graph, source structure, compilation, packaging, final-JAR runtime, compatibility profiles, and release publication are separate evidence classes.
- Validation stays proportional: focused structural/unit checks are reused; no clean build or broad matrix is implied.

## Current evidence snapshot

| Evidence class | Current result |
|---|---|
| Registry | Schema v2; 22 exact releases; 12 source families; 19 registry build targets (direct source nodes); 53 direct loader distributions; 62 exact runtime cells; seven publication anchors; 20 expected loader artifacts. |
| Direct build ownership | Public graph is direct-only; 53 leaves authoritative; direct contract AST/self-test and task PASS; registry dry-run PASS. Fabric native preprocessing has focused proof for archive capture on 16 targets, SharedZip access on 17 targets (1.20.2 through 1.21.11), ReloadableResourceManager on 18 pre26 targets (1.20.1 through 1.21.11), RuntimeResourceHash on 17 targets (1.20.1 through 1.21.10), LoadingOverlayToast on 14 targets (eight common 1.20.5 through 1.21.5 plus six modern 1.21.6 through 1.21.11), Bitmap provider definition on all 19 targets (1.20.1 through 26.1), and SimpleReload on four targets (1.21.5 through 1.21.8); later physical seams remain where needed. Nested Gradle remains parity oracle. |
| Source metrics | 217 production Java files; 16,575 LOC; 110 bridge files / 5,555 LOC (33.51%); zero exact/normalized duplicate groups; 15 target/version conditional lines; ten Stonecutter conditional blocks (maximum 40 lines); three renderer bodies; two adapters. |
| Configuration | Unknown-field preservation, shared integer validation, shared modern renderer, and configured/effective atlas-retry policy have focused evidence. No live full renderer/shader proof. |
| Compatibility profiles | 36 recipes; 21 `AVAILABLE`/`UNTESTED`; three `UNAVAILABLE`; 12 `PENDING_METADATA`/`UNTESTED`; no PASS profiles. Twenty unique new artifact pins were independently checked against SHA-256 and embedded metadata. |
| Profile transport | Schema-2 materializer and three loader wrappers structurally verified offline; no downloads or launches. |
| Fixtures | Nine deterministic 1.21.1 fixtures structurally verified; no final-JAR execution. |
| Default-off candidates | Five `SAFE_KEEP_DEFAULT_OFF`; six `FAILED_WITH_REASON`; zero promoted; runtime/performance gates `NOT_RUN`. |
| Release manifest | Verifier and mutation self-test implemented; complete current 20-JAR verification NOT RUN. |
| Runtime matrix | Current all-62 exact final-JAR matrix NOT RUN. |

## Phase classification

| Requirement | Status | Current evidence | Remaining gate |
|---|---|---|---|
| **A — reconcile state and source gates** | `VERIFIED_COMPLETE` | Deterministic metrics cover source layers, duplicate groups, ownership, build branches, renderer families, and artifact declarations. | Refresh after later structural edits. |
| **B — Quick Pack capability ownership** | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Exactly six overlap capabilities are externally owned; other 23 remain PackForge-governed; UI effective state is ownership-aware. | Current final-JAR profile runs. |
| **C — safe optional-mod detection** | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Loader-neutral detection and environment-gated reporter exist; failure is conservative. | Required Forge/NeoForge and combined runtime profiles. |
| **D — registry-derived graph** | `STRUCTURAL_VERIFIED` | 12 source families, 19 registry build targets (direct source nodes), 53 loader distributions, separate 62-cell ledger; registry/direct-node contract mandatory. | Maintain bijection gate. |
| **E — authoritative direct build** | `PARTIAL` | 53 leaves are authoritative direct cells and public graph is direct-only. Seven bounded Fabric source seams have focused compile/source-archive proof; SimpleReload now covers 1.21.5 through 1.21.8. | Extend preprocessing beyond the bounded Fabric seams; all-53 compile/package parity; Java 17/21/25; remap/refmap/JarJar; runtime; remove nested oracle. |
| **F — reduce duplication and clarify ownership** | `PARTIAL` | Zero duplicate groups, 15 conditional lines, source-policy centralization. | Raw shrink target not met; preserve justification and prevent regression. |
| **G — consolidate configuration renderers** | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Unknown fields preserved; shared validation/model; three renderer bodies plus two adapters. | Live renderer parity and current final-JAR checks. |
| **H — same-binary range artifacts** | `IMPLEMENTED_UNVERIFIED` | Registry expresses 20 artifacts without maturity crossing. | Rebuild current bytes and repeat every same-JAR range cell. |
| **I — compatibility-profile harness** | `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED` | Catalog, pins, reporter, materializer, provenance transport, and fixtures exist. | Resolve remaining metadata/instrumentation and execute available profiles. |
| **J — default-off candidates** | `VERIFIED_COMPLETE_CONSERVATIVE` | Exact 11-candidate catalog; five keep off, six fail with reason, zero promotion. | Evidence needed only before any future promotion. |
| **K — exact final-artifact matrix** | `PARTIAL` | Controller, verifier, provenance, and fixtures exist. | Current manifest and full 62-cell startup/reload/fixture/semantic/exit run. |
| **L — docs and rollback state** | `PARTIAL` | Current docs distinguish present structural evidence from historical runtime proof. | New final evidence and rollback checkpoint after H/K/profile completion. |

## Historical evidence invalidated as current proof

1. Checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` recorded 62/62 exact cells and 20 artifacts.
2. Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded source consolidation, 18/20 hash continuity, four Forge reruns, and a real Quick Pack 1.5.0 Fabric run.
3. Later authoritative-direct-build, configuration, reporter, and harness work changed current source/artifact paths.
4. Therefore old hashes and runtime records cannot prove current binaries. They remain historical regression inputs only.

## Current blockers to completion

1. Nested Gradle still exists as parity oracle; all-53 direct parity and preprocessing ownership beyond the bounded Fabric seams are unresolved.
2. Current complete 20-artifact manifest has not been built and verified.
3. Current same-binary range proof and all 62 exact runtime cells are absent.
4. Twenty-one materializable compatibility profiles remain `UNTESTED`; 12 more lack exact compatible metadata; three are explicitly `UNAVAILABLE`.
5. Live renderer-family and shader/atlas behavior remain unverified.
6. No current release-ready hashes or final rollback checkpoint exist.

## Dependency-ordered continuation

1. Extend the proven Fabric preprocessing pilot, finish Phase E direct build/package parity, and remove the nested parity oracle only after equivalent proof.
2. Rebuild and verify exact 20-artifact manifest.
3. Execute H/K same-binary and 62-cell runtime matrix using deterministic fixtures.
4. Execute all currently available Phase I profiles; keep unresolved cells `UNTESTED` or evidence-backed `UNAVAILABLE`.
5. Complete live G checks, refresh source metrics, then create final evidence and rollback checkpoints.
