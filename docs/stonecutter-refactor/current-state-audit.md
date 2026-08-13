# Current-state audit

Audited checkout `9baa02f` (`docs: finalize refactor evidence`) on branch `codex/packforge-stonecutter-refactor` before continuation work.

## Audit boundary

- Preserve unrelated user work: `.github/ISSUE_TEMPLATE/bug-report.yml` is modified and `.codegraph/` is untracked. Neither belongs to PackForge implementation checkpoints.
- Current source and retained artifacts are authoritative. Historical reports are evidence claims when current source contradicts them or raw results are unavailable.
- Build/package evidence is separate from final-distribution-JAR runtime proof.
- No implementation reset, history rewrite, or broad source replacement is authorized.

## Live baseline

| Check | Result |
|---|---|
| `git status --short --branch` | Dedicated continuation branch found; unrelated user changes identified above. |
| `./gradlew.bat validateTargetRegistry printResolvedMatrix reportSourceMetrics --no-daemon --stacktrace` | PASS in 11 seconds before the metrics expansion. Registry has 22 exact release rows and 19 build targets. |
| `./gradlew.bat buildAllSupported verifyAllArtifacts --no-daemon --stacktrace` | PASS in 12 minutes 1 second after the command-output channel timed out; final result recovered from Gradle daemon log. This is build/package evidence. |
| Focused Fabric configuration tests | PASS for `PackForgeConfigScreenModelTest`, `PackForgeConfigTranslationsTest`, and `PackForgeConfigPreservationTest`. |
| `build/libs/release/manifest.json` | 20 artifacts; all local JAR SHA-256 values match the manifest. |
| Retained post-consolidation raw production records | Four Forge cells are retained locally for 1.20.1-1.20.4, each with two reloads and controller-directed code-0 exit. Historical documents claim wider hash-continuity evidence, but the other raw records are absent. |

## Phase classification

| Requirement | Status | Source evidence | Test/artifact evidence | Action |
|---|---|---|---|---|
| **A - Reconcile state and add source gates** | `VERIFIED_COMPLETE` | `reportSourceMetrics` writes portable LF-only JSON and Markdown under `build/reports`; `updateSourceMetricsCheckpoint` alone refreshes the checked-in stable snapshot. Metrics cover exclusive source layers, gated exact/normalized duplicate groups, capability ownership, build-target branching, renderer families, artifact count, and release/loader cells. This audit records contradictions instead of rewriting history. | `updateSourceMetricsCheckpoint validateTargetRegistry` passed after expansion; a normal metrics rerun preserved the checked-in snapshot timestamps and reproduced byte-identical report content. The 20-artifact baseline build also passed. | Preserve these outputs as the continuation baseline and rerun the gate after each structural phase. |
| **B - Quick Pack capability-specific ownership** | `PARTIAL` | `QuickPackCompatibility` lists exactly six overlap capabilities and `FeaturePolicy` guards them individually. Loader plugins still suppress whole resource, font, and loading mixins. Some suppressed mixins contain diagnostics, summary toast, or startup-status behavior that Quick Pack does not own. The master `reload_optimizer` screen option is also tagged as `RESOURCE_PACK_INDEX`, creating an overly broad externally-owned display. | Unit policy/effective-state tests exist. Real-mod proof is documented only for Fabric 1.21.1 with Quick Pack 1.5.0. | Split mixed responsibilities or use narrow runtime guards; keep non-overlap operations active; correct master-option ownership; add retained-capability and artifact tests. |
| **C - Safe optional-mod detection on all loaders** | `PARTIAL` | Runtime `PackForgePlatform.isModLoaded/modVersion` is loader-neutral after services initialize. Forge/NeoForge mixin plugins still query `ModList` or duplicated helpers during mixin selection; early failures can return false. No central `OptionalModPresence` contract exists. | Required Forge/NeoForge final-JAR Quick Pack profiles are absent. Registry availability remains `unverified`. | Remove early false-negative detection paths, centralize runtime presence, and execute or explicitly mark unavailable every representative profile. |
| **D - Generate Stonecutter nodes from registry** | `PARTIAL` | Registry schema v2 contains all exact rows, but `settings.gradle` manually lists 19 Stonecutter versions. No registry-to-node bijection guard exists. | `validateTargetRegistry` verifies registry data, not settings-node parity. | Generate nodes during settings evaluation and fail on missing or unregistered nodes. |
| **E - Authoritative direct Stonecutter build** | `PARTIAL` | All 53 registry-derived loader distributions are authoritative direct cells and public graph is direct-only; JOptSimple registry metadata remains explicit; `validateStonecutterDirectContract` is mandatory. Nested Gradle remains parity rollback oracle. | Direct contract AST/self-test passed baseline plus 10 mutations; direct task passed; `validateTargetRegistry` dry-run passed in 31 seconds without compilation; independent review passed. | Complete all-53 build/structural/package parity, Java 17/21/25 proof, remap/JarJar proof, runtime, and true Stonecutter-preprocessed source proof; remove nested rollback oracle only after parity is proven. |
| **F - Reduce duplication and clarify ownership** | `PARTIAL` | Shared-source consolidation exists and normalized duplicate classes are currently zero. Platform scripts still contain about 80 `target.key` references, and prior metrics did not report each required ownership/category field. | Pre-change metric reported 202 files, 16,209 LOC, 99 bridge files, 5,426 bridge LOC, and zero normalized duplicate groups. | Use expanded metrics, replace branch lists with registry data during direct-build migration, and document any justified LOC exception. |
| **G - Consolidate configuration renderers** | `PARTIAL` | `PackForgeConfigScreenModel` owns categories, option state, bounds, ownership, and apply scope. Atomic detached save exists. Four native renderer families remain. Config rewrites serialize `Cfg`, so unknown JSON fields are not preserved. | Focused model, translation, known-v12-value preservation, and failed-write tests pass. Renderer parity and unknown-field tests are missing. | Preserve unknown fields, add renderer-family structural/live evidence, and reduce renderers to shared behavior plus thin widget adapters. |
| **H - Safe same-binary range artifacts** | `IMPLEMENTED_UNVERIFIED` | Registry and manifest define 20 loader-specific artifacts without crossing beta/stable maturity. | All 20 local hashes match the manifest. Historical reports claim same-binary runtime proof, but most raw records are not retained and packaging changes in Phase E invalidate old byte evidence. | Rebuild and retain per-range same-JAR evidence after the authoritative build migration. |
| **I - Compatibility-profile harness** | `PARTIAL` | `Run-Exact-ProductionMatrix.ps1` is registry-driven for base final-JAR cells; Fabric can stage additional JARs. No general profile declaration records coordinates/URL/version/SHA/dependencies/overrides/expected ownership/availability across loaders. | Required third-party isolated and high-risk profiles are mostly `UNTESTED` or absent rather than individually `UNAVAILABLE`. | Add registry-backed profile data and cross-loader staging/provenance, then execute or explicitly mark every required profile. |
| **J - Evaluate default-off optimizations** | `IMPLEMENTED_UNVERIFIED` | Every candidate remains conservatively default-off and has a `SAFE_KEEP_DEFAULT_OFF` document verdict. Existing benchmark code measures the whole reload optimizer, not each candidate. | No candidate is incorrectly promoted, but per-candidate lifecycle, compatibility, fixture, and performance evidence is absent. | Keep defaults off; create reproducible evidence per candidate and promote only through separate verified commits. |
| **K - Exact final-artifact matrix** | `PARTIAL` | Matrix controller resolves 62 registry cells, final JAR paths, and hashes. It uses one large-pack fixture, always permits controller-directed termination, and does not retain semantic hashes in result rows. Required authoritative Stonecutter/release-manifest task surface is incomplete. | Manifest integrity passes. Only four current raw post-consolidation runtime cells are retained locally; 58 cells rely on historical documents/hash continuity. | After Phase E, run and retain all exact cells with required fixtures, semantic/resource evidence, reload/cancellation coverage, and exact exit results. |
| **L - Reconcile documentation and rollback state** | `REGRESSED` | `final-validation.md` and `implementation-report.md` claim completion while `library-decisions.md` says Stonecutter is not migrated and `source-inventory.md` describes standalone builds. README says 17 artifacts while the manifest has 20. | Final docs commit `9baa02f` is not recorded as the latest stable rollback checkpoint; checkpoint docs name `a340286`. | Reconcile all current-status sections only after implementation proof, and commit final evidence/docs as a separate rollback checkpoint. |

## Contradictions blocking a completion claim

1. Direct public graph is authoritative, but nested Gradle remains a parity rollback oracle; full all-53 parity/runtime and true Stonecutter-preprocessed source proof are still absent.
2. Current 20-artifact manifest conflicts with README's 17-artifact claim.
3. Final documentation claims complete final-JAR matrix proof, but only four current raw post-consolidation results are retained locally.
4. `library-decisions.md` and `source-inventory.md` describe the live architecture more accurately than final-status documents.
5. Final documentation commit is not a named stable rollback checkpoint.

## Dependency-ordered continuation

1. Complete and checkpoint Phase A.
2. Complete correctness-critical B/C before changing build ownership.
3. Complete Phase E all-cell package/parity proof, remove nested rollback oracle, then measure and finish F/G.
4. Add I/J profile and candidate evidence.
5. Rebuild and execute H/K final-JAR acceptance.
6. Complete L in separate final verification and documentation checkpoints.
