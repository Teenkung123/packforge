# Current-state audit

Date: 2026-08-15

## Superseding 1.4 checkpoint (2026-08-16)

This addendum supersedes the older snapshot below for current source and artifact status. HEAD is `8aa6844` on branch `1.4`, after the capability/Quick Pack correction (`1177e23`), unreachable-scaffolding removal (`014f71a`), artifact-hardening checkpoint (`6591e79`), and content-aware duplicate scan repair (`8aa6844`).

- Source metrics are now 209 production Java files / 15,814 nonblank LOC, 110 bridge files / 5,553 LOC (35.11%), zero exact or normalized duplicate groups, six target/version conditional lines, and zero platform target-key conditional lines.
- The Phase F ledger-adjusted reduction is 3,370 LOC against 3,368 required; the executable metric gate is `PASS`, while the implementation status remains `IMPLEMENTED_UNVERIFIED` until behavior parity is proven.
- Quick Pack ownership is version-aware: pre-1.4 index only; 1.4.x index plus fade; 1.5+ adds font preselection and mipmap handoff. ZIP pooling and PackForge's loading-status overlay remain PackForge-owned.
- Fabric Quick Pack profiles carry a profile-only Fabric Loader `0.17.3` override; no profile launch or download evidence is claimed.
- Fresh representative `mc1_21_1` Fabric, Forge, and NeoForge final artifacts pass the structural verifier, including one exact `0.5.4:slim` loader artifact, recursive Forge common-runtime metadata/classes, required mixins/refmaps, and a 128x128 PNG icon of 10,607 bytes. `inspectArtifactSizes` reports the same nested-JAR and duplicate-entry details.
- `verifyExistingArtifacts` across the stale root directory is intentionally not current proof: it fails on stale target-capability metadata in the remaining 17 old files. The three representative artifacts were rebuilt and verified; the full 20-artifact rebuild remains open.
- Direct Fabric PackIndex microbenchmark (`platform/fabric`, target `mc1_21_1`) now passes: 20,014 entries, 18.4206 ms index build, 80.299 ms baseline median, 0.2406 ms indexed median, 99.70% query improvement, and equal semantic hash `bf864f1dbb4a77bc7e15193856a33c392ee3a8f7114d8d764a9aa0c11face66d`. This is microbenchmark evidence only; no end-to-end or comparative Minecraft reload result is claimed.

Audited every phase and completion gate in `PackForge_Missing_Implementation_Plan.md` against branch `1.4`, including the current 1.4/slim-MixinExtras build and focused loader evidence. Historical implementation checkpoints remain `abdf2fe`, `f5a76d7`, and documentation checkpoint `40275dd`. Overall state: **PARTIAL** and **NOT RELEASE READY**.

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
| Direct build ownership | Public graph is direct-only; 53 leaves authoritative; direct contract AST/self-test and task PASS; registry dry-run PASS. Fabric native preprocessing has focused proof for archive capture on 16 targets, SharedZip access on 17 targets (1.20.2 through 1.21.11), ReloadableResourceManager on 18 pre26 targets (1.20.1 through 1.21.11), RuntimeResourceHash on 17 targets (1.20.1 through 1.21.10), LoadingOverlayToast on 14 targets (eight common 1.20.5 through 1.21.5 plus six modern 1.21.6 through 1.21.11), Bitmap provider definition on all 19 targets (1.20.1 through 26.1), and SimpleReload on four targets (1.21.5 through 1.21.8); later physical seams remain where needed. The direct helper is always-on; legacy wrapper parity is isolated behind explicit opt-in. |
| Source metrics | 217 production Java files; 16,577 LOC; 110 bridge files / 5,553 LOC (33.50%); zero exact/normalized duplicate groups; 15 target/version conditional lines; zero platform target-key conditional lines; ten Stonecutter conditional blocks (maximum 40 lines); three renderer bodies; two adapters. |
| Configuration | Unknown-field preservation, shared integer validation, shared modern renderer, and configured/effective atlas-retry policy have focused evidence. No live full renderer/shader proof. |
| Compatibility profiles | 36 recipes; 21 `AVAILABLE`/`UNTESTED`; 11 `UNAVAILABLE`; four `PENDING_METADATA`/`UNTESTED`; no PASS profiles. Dated public-source evidence backs the newly unavailable exact-loader dispositions; no execution is implied. |
| Profile transport | Schema-2 materializer and three loader wrappers structurally verified offline; no downloads or launches. |
| Fixtures | Nine deterministic 1.21.1 fixtures structurally verified; each now includes a deterministic `example`-namespace semantic-hash marker; no final-JAR execution. |
| Default-off candidates | Five `SAFE_KEEP_DEFAULT_OFF`; six `FAILED_WITH_REASON`; zero promoted; runtime/performance gates `NOT_RUN`. |
| Current artifacts | Registry expects 20 names. `buildAllSupported` produced exactly 20 version-1.4 final JARs; `verifyAllArtifacts` PASS confirms names, metadata, mixins, refmaps, JarJar, and pinned slim MixinExtras. |
| Release manifest | `Generate-ReleaseManifest.py` generated a 20-entry version-1.4 manifest and `verifyReleaseManifest` PASS verified registry metadata and artifact hashes. |
| Focused slim/runtime proof | Fabric `mc1_21_1`, Forge `mc1_20_2`, and NeoForge `mc1_21_1` direct/parity builds PASS; each final JAR contains one loader-specific `:slim` artifact with no duplicate ZIP entries. One-reload production smokes PASS with MixinExtras initialization, resolved-resource hash, and clean exit. |
| Runtime matrix | Current all-62 exact final-JAR matrix remains NOT RUN; focused three-loader smokes are not a substitute for ten-reload exact-cell acceptance. |

## Whole-plan phase classification

Only the five statuses defined by the assignment are used below.

| Requirement | Status | Source evidence | Test/artifact evidence | Action |
|---|---|---|---|---|
| **A — current-state audit** | `IMPLEMENTED_UNVERIFIED` | `reportSourceMetrics`, checked-in JSON/Markdown output, source ownership, duplicate, branch, renderer, registry, artifact metrics, and this required A-L audit table exist. | Current `buildAllSupported`, `verifyAllArtifacts`, and 20-entry manifest verification now PASS. Runtime/profile gates remain separate. | Keep the implementation status until the remaining runtime/profile evidence and final checkpoint exist. |
| **B — Quick Pack capability ownership** | `IMPLEMENTED_UNVERIFIED` | `QuickPackCompatibility` owns exactly six overlap capabilities. Mixin plugins select only version shapes; loading toast/startup status and diagnostics are isolated, and configured/effective values remain separate. | Current 1.4 artifacts pass structural ownership checks; focused smokes had no Quick Pack dependency. Required Quick Pack profiles remain unexecuted. | Execute Quick Pack profiles while asserting every retained capability. |
| **C — safe optional-mod detection** | `IMPLEMENTED_UNVERIFIED` | Loader-neutral `OptionalModPresence` is initialized through `PackForgeServices`; detection failure conservatively hands off overlap. Forge API differences are two thin family bridges, and mixin plugins do not query Quick Pack or `ModList`. | Static/focused tests exist, but zero current Quick Pack final-JAR profiles have PASS evidence. | Resolve profile inputs, mark genuine absence `UNAVAILABLE`, and run every available Fabric/Forge/NeoForge family with ten reloads, semantic hash, ownership/retained assertions, fatal-mixin scan, and clean exit. |
| **D — registry-derived Stonecutter graph** | `VERIFIED_COMPLETE` | `settings.gradle` derives source nodes and loader distributions from schema-v2 registry data. Root validation enforces the 19-node/53-distribution bijection separately from 22 releases/62 runtime cells. | Registry and Stonecutter consistency checkpoints passed; no second manually maintained node list was found. | Retain the bijection gate whenever registry/settings code changes. |
| **E — authoritative direct build** | `IMPLEMENTED_UNVERIFIED` | Public tasks use 53 direct leaves and registry toolchains. `stonecutter-build.gradle` now validates node context only; `packforge-stonecutter-direct.gradle` registers the always-on direct leaf, while `packforge-stonecutter-legacy-parity.gradle` is loaded only by explicit parity opt-in. Focused Fabric seams remain proven at their bounded boundaries. | Direct-contract self-test passes; `buildAllSupported` and `verifyAllArtifacts` pass for the current 20 publication artifacts; Fabric/Forge/NeoForge representative direct/parity cells pass. Full all-53 parity and runtime proof remain open. | Extend source transport/capability migration and run the remaining all-53 parity/runtime evidence before removing the optional legacy oracle. |
| **F — source ownership and reduction** | `IMPLEMENTED_UNVERIFIED` | Metrics report 217 files, 16,577 LOC, zero duplicate groups, 15 target/version conditional lines, zero platform target-key conditional lines, and exact-version bridge LOC below version-common LOC. The two Forge mod-list files are thin API-family bridges. | The report records a current-tree 2,347-LOC added ledger, 3,368 LOC required for the plan's 20% reduction, and a 761-LOC adjusted shortfall. Normalized duplicate, byte-identity, version-common ownership, and target-key branch gates pass; the adjusted shrink gate fails. Behavior parity is not exhaustive. | Keep Phase F IMPLEMENTED_UNVERIFIED; close the 761-LOC shortfall, audit semantic ownership/obsolete paths, and bind behavior parity to current artifacts. |
| **G — configuration renderer consolidation** | `IMPLEMENTED_UNVERIFIED` | One screen model owns seven categories, state, validation, ownership, and apply scope. Three renderer bodies plus two adapters serve the widget families; Fabric, Forge, NeoForge, and pack-screen routes converge on `PackForgeConfigScreen`. Config writes use a temporary file and prefer atomic replacement. | Unit/structural tests cover IDs, state, unknown fields, detached save, failed live installation, and replacement-stage failure against an existing nonempty target. The latter preserves the live snapshot and target contents and proves temporary-file cleanup. No current live route/renderer parity exists. | Run the four entry routes across renderer families and verify Beta/Stable support display where surfaced. |
| **H — same-binary range artifacts** | `IMPLEMENTED_UNVERIFIED` | The registry declares a current 20-artifact baseline without crossing beta/stable maturity. | Current 1.4 build, exact 20-artifact structural verification, and 20-entry manifest verification PASS. Same-binary runtime proof for every exact release in each range remains absent. | Reprove each publication range with unchanged current JARs on every claimed exact release/loader before release. |
| **I — compatibility-profile harness** | `IMPLEMENTED_UNVERIFIED` | The schema-2 catalog represents all 30 isolated and six high-risk recipes; materializer, hash cache, provenance, fixtures, execution-scenario declarations, release preflight, and three loader transports exist. Every generated fixture carries the semantic-hash marker required by controlled runtime evidence, and unavailable exact-loader records carry dated public-source evidence. | 21 profiles are `AVAILABLE`/`UNTESTED`, four `PENDING_METADATA`/`UNTESTED`, 11 `UNAVAILABLE`, and zero PASS. No download, launch, reload, cancellation, or immutable runtime evidence was retained. | Resolve the four remaining pending dispositions, retain immutable input/evidence hashes, add any missing path instrumentation, and execute all available isolated/high-risk recipes. |
| **J — default-off candidates** | `VERIFIED_COMPLETE` | The exact 11-candidate catalog records five `SAFE_KEEP_DEFAULT_OFF`, six `FAILED_WITH_REASON`, and zero promotions. Saved defaults remain conservative. | Catalog/self-test evidence exists. Promotion gates are not applicable because no candidate was promoted; all runtime/performance fields remain honestly `NOT_RUN`. | Keep defaults unchanged. Reopen one candidate at a time only with all semantic, lifecycle, performance, compatibility, and configuration gates. |
| **K — exact final-artifact matrix** | `IMPLEMENTED_UNVERIFIED` | Registry-derived controller, smoke wrappers, manifest verifier, provenance, resume checks, nine deterministic fixture definitions, manifest entry/required/duplicate validation, declared execution scenarios, release-manifest preflight, and a shared resolved-resource hash contract exist for 62 cells. Base Fabric controller rows stage an immutable deterministic fixture; every generated fixture has a positive hash-visible resource; Fabric, Forge, and NeoForge controlled smokes emit one deterministic hash token; the matrix binds it into resume evidence and enforces ten reloads per cell. | Current 20-artifact manifest verification PASS. Focused Fabric/Forge/NeoForge final-JAR smokes each PASS with one reload, MixinExtras initialization, positive resolved-resource hash, and clean exit. The required ten-reload all-62 result and cancellation/failure execution remain open. | Execute all 62 base cells plus fixture/reload/cancellation acceptance and clean exits while retaining the bound semantic hash. |
| **L — documentation and rollback** | `IMPLEMENTED_UNVERIFIED` | Required documents exist and current-status files distinguish historical evidence, current structural checkpoints, artifact conflicts, profile dispositions, and runtime gaps. | This reconciliation updates the named current-status reports, but no current fully verified final rollback point or separate final evidence/docs checkpoint exists yet. | Reconcile remaining README/version-family/artifact notes from final evidence, distinguish latest historical runtime from current structural checkpoints, and commit final evidence/docs separately. |

## Validation used for this audit

- Reused the successful registry, resolved-matrix, implementation-contract, and source-metrics validation because later changes affected only the focused SimpleReload preprocessing boundary and its direct contract.
- Current focused direct contract PASS: 53 direct cells and four native SimpleReload cells.
- Current focused Fabric 1.21.4/1.21.5/1.21.6 `compileJava` plus `sourcesJar` PASS across 18 executed tasks; generated/source-JAR ownership matched at the changed boundary.
- Current `reportSourceMetrics` PASS: 217 files, 16,577 LOC, zero duplicate groups, 15 conditional lines, zero platform target-key conditional lines, ten Stonecutter blocks, and 20 declared artifacts.
- Current focused Fabric 1.21.1 `PackForgeConfigPreservationTest` PASS: replacement-stage failure preserved the installed config and existing target contents and left no temporary file. Independent read-only review passed.
- Current Phase I/K fixture-and-hash contract PASS: deterministic fixture generation now guarantees the hash-visible marker across all nine fixture families; resolved-resource marker parsing, positive-entry/deterministic digest checks, strict cross-loader PASS-token parsing, base-Fabric fixture wiring, ten-reload enforcement, manifest/scenario/release-preflight fingerprinting, and 22 resume-evidence mutations passed; the aggregate implementation-contract gate passed.
- Current `buildAllSupported` and `verifyAllArtifacts` PASS for exactly 20 version-1.4 JARs; `verifyReleaseManifest` PASS for the generated 20-entry manifest.
- Focused final-JAR smoke PASS: Fabric `mc1_21_1` (`reloads=1`), Forge `mc1_20_2` (`reloads=1`), and NeoForge `mc1_21_1` (`reloads=1`), each with MixinExtras 0.5.4 initialization, positive resolved-resource hash, clean exit, and no forbidden mixin/crash marker.
- No all-53 parity matrix, 62-cell runtime matrix, third-party profile, benchmark, or release publication was run for this audit.

## Contradictions with earlier reports

- [implementation-report.md](implementation-report.md) still labels A `VERIFIED_COMPLETE`, uses non-assignment statuses for B, C, G, and J, and describes H without today's invalid artifact-directory evidence. The canonical classifications in this audit supersede those current-status rows until final reconciliation.
- [compatibility-matrix.md](compatibility-matrix.md) describes native preprocessing as only the 16-target archive pilot, omitting the later SharedZip, reload-manager, runtime-hash, loading-toast, bitmap, and SimpleReload checkpoints.
- [quick-pack-compatibility.md](quick-pack-compatibility.md) says the catalog has four Quick Pack-containing recipes; the live catalog has six.
- [rollback.md](rollback.md) calls `a3402866` the latest fully verified checkpoint without distinguishing historical full-runtime proof from the newer current structural checkpoints. It is the latest historical full-runtime checkpoint only.
- [artifact-consolidation.md](artifact-consolidation.md) correctly marks its older hashes and range evidence historical; its current-evidence notice remains authoritative until fresh artifacts replace it.

## Historical evidence invalidated as current proof

1. Checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` recorded 62/62 exact cells and 20 artifacts.
2. Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded source consolidation, 18/20 hash continuity, four Forge reruns, and a real Quick Pack 1.5.0 Fabric run.
3. Later authoritative-direct-build, configuration, reporter, and harness work changed current source/artifact paths.
4. Therefore old hashes and runtime records cannot prove current binaries. They remain historical regression inputs only.

## Current blockers to completion

1. Nested Gradle still exists as parity oracle; all-53 direct parity and preprocessing ownership beyond the bounded Fabric seams are unresolved.
2. The current 20-artifact structural set and manifest now pass; current same-binary runtime range proof and the all-62 matrix are still absent.
3. Current same-binary range proof and all 62 exact runtime cells are absent.
4. Twenty-one materializable compatibility profiles remain `UNTESTED`; four more lack an executable disposition; 11 are explicitly `UNAVAILABLE`.
5. Live renderer-family, entry-route, and shader/atlas behavior remain unverified.
6. Phase F now has a precise 2,347-LOC added ledger and executable gate, but the adjusted shrink gate remains 761 LOC short; the target-key platform-branch gate now passes after excluding non-dispatch version fallbacks.
7. The current 1.4 manifest and focused smoke hashes exist, but no release-ready all-cell evidence or final rollback checkpoint exists.

## Dependency-ordered continuation

1. Finish Phase E capability migration and all-53 build/package proof; delete the optional legacy parity path only after direct parity and runtime evidence exist.
2. Complete focused Phase F/G structural gaps and rerun their unit/contract checks.
3. Build a fresh exact 20-artifact set, pass structural verification, and generate/verify the release manifest.
4. Execute H/K unchanged-JAR ranges and the full 62-cell runtime matrix using deterministic fixtures and consistent semantic-hash evidence.
5. Resolve Phase I pending dispositions and execute every available isolated/high-risk profile, including required Quick Pack Phase C coverage.
6. Reconcile every Phase L document and README, then create separate final evidence and documentation rollback checkpoints.
