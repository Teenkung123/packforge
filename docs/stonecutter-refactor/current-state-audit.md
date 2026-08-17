# Current-state audit

Date: 2026-08-15

## Final handoff continuation checkpoint (2026-08-16)

This addendum supersedes the older current-status counts and source-metric
claims below. The current generated metrics report is
`build/reports/source-metrics/source-metrics.md`: 216 production Java files,
16,731 nonblank LOC, 113 bridge files / 5,671 LOC, zero duplicate groups, and a
Phase F ledger-adjusted reduction of 2,453 LOC against 3,368 required (915 LOC
shortfall). Phase F therefore remains `IMPLEMENTED_UNVERIFIED`; the older
3,370-LOC/`PASS` snapshot is historical.

- The refreshed `mc1_21_1` Fabric artifact is
  `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`.
  Axiom now passes ten reloads with `heavyFixtureEvidence=11` after the model
  instrumentation repair; the repaired evidence is
  `evidence/fabric-axiom-1.21.1.md`.
- All 21 available compatibility profiles now have explicit current
  dispositions: 14 `SAFE_ORIGINAL_PATH`, four
  `HOOK_PRESERVING_COALESCED_PATH`, two `EXTERNALLY_OWNED_PATH`, and one
  evidence-backed `FAILED` VulkanMod renderer crash. The remaining 15 exact
  loader recipes are `UNAVAILABLE`; no profile is `UNTESTED` or
  `PENDING_METADATA`. The catalog validator passes 36/36.
- The VulkanMod failure is an external renderer initialization crash
  (`java.lang.OutOfMemoryError: Out of stack space` in
  `net.vulkanmod.vulkan.Vulkan.initVulkan`), with no PackForge fatal-mixin
  marker; see `evidence/fabric-vulkanmod-1.21.1.md`.
- Focused Forge, NeoForge, and Fabric profile evidence uses current final JARs,
  ten reloads, stable resolved-resource hashes, heavy-fixture markers where
  required, forbidden-marker scans, and clean exits. The complete current-byte
  62-cell matrix now records `62/62 PASS` against the final-byte manifest;
  immutable summaries and hashes are in `evidence/final-validation-2026-08-17.md`.
- A fresh `clean buildAllSupported` completed successfully and regenerated all
  20 registry-named 1.4 JARs. `verifyAllArtifacts`, `verifyExistingArtifacts`,
  `inspectArtifactSizes`, and generated-manifest verification pass; the current
  manifest SHA-256 is
  `410E8855AD674CE6E2A4E41C88C6E70B380F36A61C7732A77ED41DC4786028F3`.
  Size inspection reports the expected Fabric, Forge, and NeoForge slim
  MixinExtras nested artifact, 10,607-byte 128x128 icons, and no duplicate ZIP
  entries.
- Phase 6 defaults remain conservative because all six promotion candidates
  still have `NOT_RUN` promotion gates. Phase 7 live UI/renderer checks and
  Phase 9 comparative Quick Pack performance evidence are not yet run. Phase 11
  final evidence/rollback reconciliation remains open; overall state is
  **PARTIAL** and **NOT RELEASE READY**.

## Focused continuation checkpoint (2026-08-16)

This addendum supersedes older Quick Pack and CI-current paragraphs below.

- Phase 5 now has exact Quick Pack 1.5.0 pins for Fabric, Forge, and
  NeoForge. Current final-JAR Forge and NeoForge runs passed ten reloads,
  stable positive resolved-resource SHA-256, required ownership/retained
  markers, no forbidden mixin markers, and clean exit. Immutable summaries
  are `evidence/quick-pack-forge-1.21.1.md` and
  `evidence/quick-pack-neoforge-1.21.1.md`.
- The exact Fabric Quick Pack pin is explicitly unavailable: Fabric Loader
  0.17.3 rejects Quick Pack's declared `classTweaker` file as an
  `accessWidener` before PackForge initializes. The isolated Fabric,
  Quick Pack + Sodium/Iris, and Quick Pack + ImmediatelyFast profiles are
  unavailable for that concrete reason; see `quick-pack-fabric-loader-failure.md`.
- `fabric-quick-pack-default-on` remains deferred because no default-off
  candidate has been promoted. All 21 `AVAILABLE` profiles now have either
  immutable runtime evidence or a narrow documented failure disposition: 14
  `SAFE_ORIGINAL_PATH`, four `HOOK_PRESERVING_COALESCED_PATH`, two
  `EXTERNALLY_OWNED_PATH`, and one VulkanMod `FAILED` run. The other 15
  recipes are explicitly `UNAVAILABLE`; no `UNTESTED` or `PENDING_METADATA`
  record remains. The new evidence-bound Fabric profiles are Sodium, Iris,
  ImmediatelyFast, and ModernFix; their immutable summaries are under
  `evidence/`.
- Runtime CI is now tiered from the canonical registry: pull requests use
  33 representative cells, master pushes use 45 expanded cells, and
  scheduled/manual runs retain all 62 cells. The benchmark is skipped by
  default and runs only on scheduled/manual or performance-labelled pull
  requests. `scripts/Test-CiWorkflow.py` and the publication workflow
  contract both pass.
- The targeted reporter/path-marker repair rebuilt the Fabric `mc1_21_1`
  artifact as `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`.
  The earlier bounded 62-cell hash set remains historical, while the final-byte
  rerun is now recorded separately with the rebuilt artifact hashes.

## Superseding 1.4 checkpoint (2026-08-16)

This addendum supersedes the older snapshot below for current source and artifact status. The preceding source checkpoint was `8aa6844` on branch `1.4`; the current clean-build checkpoint is recorded below after the capability/Quick Pack correction (`1177e23`), unreachable-scaffolding removal (`014f71a`), artifact-hardening checkpoint (`6591e79`), and content-aware duplicate scan repair (`8aa6844`).

- The current generated source metrics are 216 production Java files / 16,731 nonblank LOC, 113 bridge files / 5,671 LOC (33.90%), zero exact or normalized duplicate groups, six target/version conditional lines, and zero platform target-key conditional lines.
- The Phase F ledger-adjusted reduction is 2,453 LOC against 3,368 required; the executable metric gate is `FAIL` with a 915-LOC shortfall, and the implementation status remains `IMPLEMENTED_UNVERIFIED` pending further source consolidation and runtime behavior parity.
- Quick Pack ownership is version-aware: pre-1.4 index only; 1.4.x index plus fade; 1.5+ adds font preselection and mipmap handoff. ZIP pooling and PackForge's loading-status overlay remain PackForge-owned.
- Fabric Quick Pack profiles carry a profile-only Fabric Loader `0.17.3` override; the exact pinned artifact is now recorded as unavailable with a loader validation failure, while Forge and NeoForge have focused current PASS evidence.
- Fresh representative `mc1_21_1` Fabric, Forge, and NeoForge final artifacts pass the structural verifier, including one exact `0.5.4:slim` loader artifact, recursive Forge common-runtime metadata/classes, required mixins/refmaps, and a 128x128 PNG icon of 10,607 bytes. `inspectArtifactSizes` reports the same nested-JAR and duplicate-entry details.
- A fresh `clean buildAllSupported` now produces exactly the 20 registry-named version-1.4 artifacts. `verifyAllArtifacts`, `verifyExistingArtifacts`, `inspectArtifactSizes`, and generated-manifest verification all pass against that clean directory.
- Direct Fabric PackIndex microbenchmark (`platform/fabric`, target `mc1_21_1`) now passes: 20,014 entries, 18.4206 ms index build, 80.299 ms baseline median, 0.2406 ms indexed median, 99.70% query improvement, and equal semantic hash `bf864f1dbb4a77bc7e15193856a33c392ee3a8f7114d8d764a9aa0c11face66d`. This is microbenchmark evidence only; no end-to-end or comparative Minecraft reload result is claimed.
- The pre-path-marker bounded run `build/production-matrix/full-62-20260816-batched` passed 62/62 exact cells with ten deterministic reloads, stable semantic hashes, clean exit, and no failed-mixin/crash marker. It is historical for cells using the subsequently rebuilt Fabric `mc1_21_1` artifact.
- The targeted post-marker build, artifact validators, manifest verification,
  and final-byte `62/62` matrix all pass; UI and comparative-performance gates
  remain separate.

### Phase 4 runtime-scenario checkpoint (2026-08-16)

- The test-only `packforge.runtimeSmokeScenario` selector now supports `cancel-in-flight`, `forced-resource-failure`, `retry-success`, and `retry-exhaustion`; normal launches remain inert. Failure injection is delivered through the reload future, and the controller clears Minecraft's cached pending reload before recovery.
- Fabric `mc1_21_1` current final JAR passed all four scenarios with `scenarioEvidence=true`, `cleanExit=true`, stable positive resolved-resource SHA-256 `80B4CD91224732DD5864132F5D5F39988AE0BBAC78F79CE753232342281AC632`, and PASS evidence for context, settled futures, stale-overlay cleanup, recovery, and clean exit.
- Forge `mc1_20_2`, NeoForge `mc1_20_2`, and Forge `26.2` current final JARs each passed `retry-success` with the same stable semantic hash and clean exit. Forge/NeoForge use Java 21; Forge 26.2 uses Java 25 and its overlay seam is version-specific because ownership moved to `Gui`.
- The controller delays the final PASS by the existing one-second stabilization interval so asynchronous recovery semantic-hash evidence is retained before wrapper validation. The 26.x seam uses `MinecraftGuiCompat` rather than shadowing the removed `Minecraft#setOverlay` method.
- This checkpoint proves representative cancellation/failure/retry behavior only. The nine heavy compatibility fixtures, third-party compatibility profiles, and per-resource ZIP/font/native-image leak counters remain separate unverified gates.

Audited every phase and completion gate in `PackForge_Missing_Implementation_Plan.md` against branch `1.4`, including the current 1.4/slim-MixinExtras build, full direct/parity build graph, 62-cell final-JAR evidence, and the representative Phase 4 scenario checkpoint above. Historical implementation checkpoints remain `abdf2fe`, `f5a76d7`, and documentation checkpoint `40275dd`. Overall state: **PARTIAL** and **NOT RELEASE READY** because compatibility profiles, heavy fixtures, live renderer checks, and final rollback/documentation reconciliation remain open.

## Audit boundary

- Preserve unrelated user work: modified `.github/ISSUE_TEMPLATE/bug-report.yml`, untracked `.codegraph/`, and untracked `docs/PackForge_Missing_Implementation_Plan.md` remain outside PackForge implementation checkpoints.
- Current source and artifacts produced from current source are authoritative.
- Historical build/runtime results remain regression evidence only when later source or artifact paths changed.
- Build graph, source structure, compilation, packaging, final-JAR runtime, compatibility profiles, and release publication are separate evidence classes.
- Validation stays proportional: the 2026-08-16 checkpoint includes the clean/full build, all-53 parity oracle, and the complete 62-cell final-artifact runtime matrix; third-party profiles and heavier cancellation fixtures remain separate gates.

## Current evidence snapshot

| Evidence class | Current result |
|---|---|
| Registry | Schema v2; 22 exact releases; 12 source families; 19 registry build targets (direct source nodes); 53 direct loader distributions; 62 exact runtime cells; seven publication anchors; 20 expected loader artifacts. |
| Direct build ownership | Public graph is direct-only; 53 leaves authoritative; direct contract AST/self-test and task PASS; registry dry-run PASS. Fabric native preprocessing has focused proof for archive capture on 16 targets, SharedZip access on 17 targets (1.20.2 through 1.21.11), ReloadableResourceManager on 18 pre26 targets (1.20.1 through 1.21.11), RuntimeResourceHash on 17 targets (1.20.1 through 1.21.10), LoadingOverlayToast on 14 targets (eight common 1.20.5 through 1.21.5 plus six modern 1.21.6 through 1.21.11), Bitmap provider definition on all 19 targets (1.20.1 through 26.1), and SimpleReload on four targets (1.21.5 through 1.21.8); later physical seams remain where needed. The direct helper is always-on; legacy wrapper parity is isolated behind explicit opt-in. `buildStonecutterAll`, `verifyStonecutterAll`, and the opt-in `verifyStonecutterParityAll` all pass across the 53 direct distributions. |
| Source metrics | 216 production Java files; 16,731 LOC; 113 bridge files / 5,671 LOC (33.90%); zero exact/normalized duplicate groups; six target/version conditional lines; zero platform target-key conditional lines; ten Stonecutter conditional blocks (maximum 40 lines); three renderer bodies; two adapters. Phase F gate is `FAIL` with a 915-LOC shortfall. |
| Configuration | Unknown-field preservation, shared integer validation, shared modern renderer, and configured/effective atlas-retry policy have focused evidence. No live full renderer/shader proof. |
| Compatibility profiles | 36 recipes; 14 safe-path PASS, four hook-preserving PASS, two externally owned PASS, one evidence-backed FAILED renderer run, and 15 `UNAVAILABLE`; no `UNTESTED` or `PENDING_METADATA` records remain. |
| Profile transport | Schema-2 materializer and three loader wrappers structurally verified offline; no downloads or launches. |
| Fixtures | Nine deterministic 1.21.1 compatibility fixtures are structurally verified and each includes a deterministic `example`-namespace semantic-hash marker; seven target-specific 20,004-entry production fixtures also passed through all 62 final-JAR cells. |
| Default-off candidates | Five `SAFE_KEEP_DEFAULT_OFF`; six `FAILED_WITH_REASON`; zero promoted; runtime/performance gates `NOT_RUN`. |
| Current artifacts | Registry expects 20 names. `buildAllSupported` produced exactly 20 version-1.4 final JARs; `verifyAllArtifacts` PASS confirms names, metadata, mixins, refmaps, JarJar, and pinned slim MixinExtras. |
| Release manifest | `Generate-ReleaseManifest.py` generated a 20-entry version-1.4 manifest and `verifyReleaseManifest` PASS verified registry metadata and artifact hashes. |
| Focused slim/runtime proof | Fabric `mc1_21_1`, Forge `mc1_20_2`, and NeoForge `mc1_21_1` direct/parity builds PASS; each final JAR contains one loader-specific `:slim` artifact with no duplicate ZIP entries. Focused one-reload smokes PASS, and the full current matrix now adds ten-reload proof for every exact cell. |
| Runtime matrix | The final-byte `build/production-matrix/final-62-20260817` result is 62/62 PASS with ten reloads, stable hashes, and clean exits. The pre-path-marker result remains historical regression evidence. |

## Whole-plan phase classification

Only the five statuses defined by the assignment are used below.

| Requirement | Status | Source evidence | Test/artifact evidence | Action |
|---|---|---|---|---|
| **A — current-state audit** | `IMPLEMENTED_UNVERIFIED` | `reportSourceMetrics`, checked-in JSON/Markdown output, source ownership, duplicate, branch, renderer, registry, artifact metrics, and this required A-L audit table exist. | Current clean/incremental builds, all-53 parity, and 20-entry manifest verification pass; the retained 62-cell result is pre-marker historical evidence and the current-byte rerun remains open. | Retain this evidence boundary when later implementation units change. |
| **B — Quick Pack capability ownership** | `IMPLEMENTED_UNVERIFIED` | `QuickPackCompatibility` owns exactly four known overlap capabilities; unknown or failed metadata uses that full conservative set. ZIP pooling, loading-status overlay, and diagnostics remain PackForge-owned; configured/effective values remain separate. | Forge and NeoForge Quick Pack final-JAR profiles pass ten reloads with ownership/retained markers, stable hashes, no fatal-mixin markers, and clean exit; exact Fabric Quick Pack is documented unavailable. | Live UI and broader performance evidence remain open; preserve the conservative fallback. |
| **C — safe optional-mod detection** | `IMPLEMENTED_UNVERIFIED` | Loader-neutral `OptionalModPresence` is initialized through `PackForgeServices`; detection failure conservatively hands off overlap. Forge API differences are two thin family bridges, and mixin plugins do not query Quick Pack or `ModList`. | All available isolated/high-risk profiles now have immutable current evidence or a narrow documented failure; profile reporter markers and clean-exit gates pass where startup completed. | Preserve the no-silent-untested catalog rule and rerun only after artifact changes. |
| **D — registry-derived Stonecutter graph** | `VERIFIED_COMPLETE` | `settings.gradle` derives source nodes and loader distributions from schema-v2 registry data. Root validation enforces the 19-node/53-distribution bijection separately from 22 releases/62 runtime cells. | Registry and Stonecutter consistency checkpoints passed; no second manually maintained node list was found. | Retain the bijection gate whenever registry/settings code changes. |
| **E — authoritative direct build** | `IMPLEMENTED_UNVERIFIED` | Public tasks use 53 direct leaves and registry toolchains. `stonecutter-build.gradle` validates node context only; `packforge-stonecutter-direct.gradle` registers the always-on direct leaf, while `packforge-stonecutter-legacy-parity.gradle` is loaded only by explicit parity opt-in. | Direct-contract self-test, `buildStonecutterAll`, `verifyStonecutterAll`, opt-in all-53 `verifyStonecutterParityAll`, clean/incremental `buildAllSupported`, and final artifact verification pass. Cross-toolchain/remap/refmap/JarJar breadth and runtime parity remain separate gates. | Retain the legacy oracle as an explicit rollback/parity tool until a later cleanup checkpoint removes it deliberately. |
| **F — source ownership and reduction** | `IMPLEMENTED_UNVERIFIED` | Metrics report 216 files, 16,731 LOC, zero duplicate groups, six target/version conditional lines, zero platform target-key conditional lines, and exact-version bridge LOC below version-common LOC. The two Forge mod-list files are thin API-family bridges. | The current generated report records a 2,347-LOC added ledger and 2,453-LOC adjusted reduction against 3,368 required; the executable metric gate fails with a 915-LOC shortfall. | Retain the explicit shortfall; do not call Phase F complete without additional measured consolidation. |
| **G — configuration renderer consolidation** | `IMPLEMENTED_UNVERIFIED` | One screen model owns seven categories, state, validation, ownership, and apply scope. Three renderer bodies plus two adapters serve the widget families; Fabric, Forge, NeoForge, and pack-screen routes converge on `PackForgeConfigScreen`. Config writes use a temporary file and prefer atomic replacement. | Unit/structural tests cover IDs, state, unknown fields, detached save, failed live installation, and replacement-stage failure against an existing nonempty target. The latter preserves the live snapshot and target contents and proves temporary-file cleanup. No current live route/renderer parity exists. | Run the four entry routes across renderer families and verify Beta/Stable support display where surfaced. |
| **H — same-binary range artifacts** | `IMPLEMENTED_UNVERIFIED` | The registry declares a current 20-artifact baseline without crossing beta/stable maturity; every range filename and maturity boundary is registry-derived. | Current manifest/artifact verification passes for the rebuilt 20-artifact set; same-binary range/runtime binding remains to be rerun against the post-freeze bytes. | Preserve the exact artifact hash and range-cell binding whenever publication metadata changes. |
| **I — compatibility-profile harness** | `IMPLEMENTED_UNVERIFIED` | The schema-2 catalog represents all 30 isolated and six high-risk recipes; materializer, hash cache, provenance, fixtures, execution-scenario declarations, release preflight, and three loader transports exist. Every generated fixture carries the semantic-hash marker required by controlled runtime evidence, and unavailable exact-loader records carry dated public-source evidence. | 21 available profiles have immutable ten-reload evidence or a narrow failure disposition (14 safe, four hook-preserving, two external, one VulkanMod failure); repaired font/model/mipmap fixtures and cancellation/failure/retry scenarios now have focused runtime evidence; 15 are `UNAVAILABLE`; catalog/self-test passes 36/36. | Resolve the VulkanMod user-decision/waiver boundary or rerun in a supported renderer environment before calling Phase I verified complete. |
| **J — default-off candidates** | `IMPLEMENTED_UNVERIFIED` | The exact 11-candidate catalog records five `SAFE_KEEP_DEFAULT_OFF`, six `FAILED_WITH_REASON`, and zero promotions. Saved defaults remain conservative. | Individual candidate ten-reload runs and lifecycle scenarios pass, but the required four-mode comparison, handle/provider lifecycle measurements, and Quick Pack compatibility overrides have not completed all six promotion gates; the catalog therefore remains default-off. | Keep defaults unchanged until a candidate’s focused gates pass; never promote unavailable/no-op settings. |
| **K — exact final-artifact matrix** | `IMPLEMENTED_UNVERIFIED` | Registry-derived controller, smoke wrappers, manifest verifier, provenance, resume checks, deterministic target fixtures, manifest entry/required/duplicate validation, declared execution scenarios, release-manifest preflight, and a shared resolved-resource hash contract exist for 62 cells. Base Fabric controller rows stage an immutable target-specific fixture; every generated production fixture has a positive hash-visible resource; Fabric, Forge, and NeoForge controlled smokes emit one deterministic hash token; the matrix binds it into resume evidence and enforces ten reloads per cell. | Final-byte `62/62 PASS` with ten reloads, stable semantic hashes, and clean exits is recorded in `evidence/final-validation-2026-08-17.md`. | UI-route automation, neutral performance modes, and final rollback/docs commits remain open. |
| **L — documentation and rollback** | `IMPLEMENTED_UNVERIFIED` | Required documents exist and current-status files distinguish historical evidence, current structural checkpoints, artifact conflicts, profile dispositions, and runtime gaps. | This reconciliation updates the named current-status reports, but no current fully verified final rollback point or separate final evidence/docs checkpoint exists yet. | Reconcile remaining README/version-family/artifact notes from final evidence, distinguish latest historical runtime from current structural checkpoints, and commit final evidence/docs separately. |

## Validation used for this audit

- Reused the successful registry, resolved-matrix, implementation-contract, and source-metrics validation; the final pass additionally reran the incremental all-target build, direct/legacy Stonecutter parity, artifact/manifest validators, and the complete bounded 62-cell runtime matrix.
- Current focused direct contract PASS: 53 direct cells and four native SimpleReload cells.
- Current focused Fabric 1.21.4/1.21.5/1.21.6 `compileJava` plus `sourcesJar` PASS across 18 executed tasks; generated/source-JAR ownership matched at the changed boundary.
- Current `reportSourceMetrics`: 216 files, 16,731 LOC, zero duplicate groups, six conditional lines, zero platform target-key conditional lines, ten Stonecutter blocks, 20 declared artifacts, and a 915-LOC Phase F shortfall (`FAIL`).
- Current focused Fabric 1.21.1 `PackForgeConfigPreservationTest` PASS: replacement-stage failure preserved the installed config and existing target contents and left no temporary file. Independent read-only review passed.
- Current Phase I/K fixture-and-hash contract PASS: deterministic fixture generation now guarantees the hash-visible marker across all nine fixture families; resolved-resource marker parsing, positive-entry/deterministic digest checks, strict cross-loader PASS-token parsing, base-Fabric fixture wiring, ten-reload enforcement, manifest/scenario/release-preflight fingerprinting, and 22 resume-evidence mutations passed; the aggregate implementation-contract gate passed.
- Heavy fixture consumption evidence is current focused runtime evidence: the corrected font fixture retains vanilla glyph providers and records 37 provider attempts/successes across 11 reload markers; the model fixture records 256 fixture model loads; and the mipmap fixture records three high-resolution sprites and 14 mipmap stages across 11 reload markers. The screenshot-triggering font fixture corruption is fixed and documented in `evidence/font-heavy-rendering-2026-08-16.md`.
- Current `buildAllSupported` and `verifyAllArtifacts` PASS for exactly 20 version-1.4 JARs; `verifyReleaseManifest` PASS for the generated 20-entry manifest.
- Focused final-JAR smoke PASS: Fabric `mc1_21_1` (`reloads=1`), Forge `mc1_20_2` (`reloads=1`), and NeoForge `mc1_21_1` (`reloads=1`), each with MixinExtras 0.5.4 initialization, positive resolved-resource hash, clean exit, and no forbidden mixin/crash marker.
- The all-53 opt-in parity oracle, clean/incremental publication builds, structural artifact checks, size inspection, release-manifest verification, final-byte 62-cell matrix, and current focused Forge/NeoForge Quick Pack, heavy-fixture, and cancellation/failure/retry evidence all pass or are separately dispositioned; UI and comparative performance remain open.

## Contradictions with earlier reports

- [implementation-report.md](implementation-report.md) still labels A `VERIFIED_COMPLETE`, uses non-assignment statuses for B, C, G, and J, and describes H without today's invalid artifact-directory evidence. The canonical classifications in this audit supersede those current-status rows until final reconciliation.
- [compatibility-matrix.md](compatibility-matrix.md) previously described native preprocessing as only the 16-target archive pilot; the current checkpoint now lists the later SharedZip, reload-manager, runtime-hash, loading-toast, bitmap, and SimpleReload coverage.
- [quick-pack-compatibility.md](quick-pack-compatibility.md) previously said the catalog had four Quick Pack-containing recipes; the current catalog has six, with two runnable Forge/NeoForge profiles and four explicit Fabric `UNAVAILABLE` dispositions.
- [rollback.md](rollback.md) calls `a3402866` the latest fully verified checkpoint without distinguishing historical full-runtime proof from the newer current structural checkpoints. It is the latest historical full-runtime checkpoint only.
- [artifact-consolidation.md](artifact-consolidation.md) correctly marks its older hashes and range evidence historical; its current-evidence notice remains authoritative until fresh artifacts replace it.

## Historical evidence invalidated as current proof

1. Checkpoint `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` recorded 62/62 exact cells and 20 artifacts.
2. Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded source consolidation, 18/20 hash continuity, four Forge reruns, and a real Quick Pack 1.5.0 Fabric run.
3. Later authoritative-direct-build, configuration, reporter, and harness work changed current source/artifact paths.
4. Therefore old hashes and runtime records cannot prove current binaries. They remain historical regression inputs only.

## Current blockers to completion

1. Nested Gradle still exists as an explicit parity oracle; its all-53 comparison and the current direct/runtime evidence pass, but the oracle should remain until a deliberate cleanup checkpoint removes it.
2. The current 20-artifact structural set and manifest pass, representative cancellation/failure/retry plus repaired heavy-fixture execution passes, and the final-byte 62-cell matrix is 62/62; same-binary binding, UI, and comparative performance remain open.
3. Twenty-one materializable compatibility profiles have immutable evidence or a narrow failure disposition; 15 exact-loader recipes are explicitly `UNAVAILABLE`, with no `UNTESTED` or `PENDING_METADATA` record remaining.
4. Live renderer-family, entry-route, and shader/atlas behavior remain unverified.
5. Phase F now has a precise 2,347-LOC added ledger and executable gate; the adjusted shrink gate is 915 LOC short of the 3,368-LOC requirement while the target-key platform-branch gate passes after excluding non-dispatch version fallbacks.
6. The current 1.4 manifest and all-cell base evidence exist, but no final rollback/documentation checkpoint has been committed and compatibility-profile release readiness remains open.

## Dependency-ordered continuation

1. Retain the direct-build/parity evidence and remove the optional legacy oracle only in a deliberate later cleanup checkpoint.
2. Complete focused Phase G live renderer-family and shader/atlas checks.
3. Execute representative heavy fixtures against current final JARs; retain the Phase 4 scenario evidence already captured.
4. Resolve Phase I pending dispositions and execute every available isolated/high-risk profile, including required Quick Pack Phase C coverage.
5. Reconcile every Phase L document and README, then create separate final evidence and documentation rollback checkpoints.
