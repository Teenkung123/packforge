# Final validation

Date: 2026-08-15

## Superseding 1.4 checkpoint (2026-08-16)

The preceding source checkpoint was `8aa6844` on branch `1.4`; this 2026-08-16 checkpoint adds the NeoForge reproducibility repair and full build evidence below. The current source snapshot is 209 production files / 15,814 LOC; the Phase F ledger gate passes at 3,370 adjusted reduction versus 3,368 required, while the status remains `IMPLEMENTED_UNVERIFIED` pending runtime behavior parity.

The artifact verifier now enforces one exact loader-specific MixinExtras `0.5.4:slim` outer artifact, rejects alternate top-level copies, recursively validates Forge's `MixinExtras-0.5.4.jar` metadata/classes, and enforces the 128x128, <=32KiB icon. The requested clean `buildAllSupported` pass produced exactly 20 current artifacts; aggregate structural, existing-artifact, size, and generated-manifest verification all pass.

The direct Fabric PackIndex microbenchmark now passes for target `mc1_21_1`: 20,014 entries, 18.4206 ms index build, 80.299 ms baseline median, 0.2406 ms indexed median, 99.70% query improvement, and equal semantic hash `bf864f1dbb4a77bc7e15193856a33c392ee3a8f7114d8d764a9aa0c11face66d`. The current final-JAR matrix has since passed 62/62 exact cells with ten reloads, a positive stable resolved-resource hash, and clean exit; Quick Pack/third-party profiles and heavy cancellation fixtures remain unexecuted.

## Outcome

Current continuation state is **PARTIAL** and **NOT RELEASE READY**. The current version is `1.4`; all-loader MixinExtras packaging uses the resolved `0.5.4:slim` classifier. The exact base matrix is verified; compatibility profiles, heavy/cancellation fixtures, live renderer checks, and final rollback/documentation reconciliation remain open.

Current source, current build wiring, and artifacts produced from current bytes are authoritative. The Quick Pack 1.5.0 run and hashes recorded at checkpoints `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` and `a3402866b217ac159d6a3cec70d3585028f79732` remain useful historical regression evidence. The current 20-artifact manifest and the new 62-cell result are bound to the current artifact hashes; historical hashes are not substituted for them.

## Current verified implementation evidence

| Area | Current result | Boundary |
|---|---|---|
| Registry | Schema v2 retains 22 exact Minecraft releases, 62 applicable exact runtime cells, seven publication anchors, and 20 expected loader artifacts. | Registry declarations are not runtime results. |
| Direct build graph | 53 registry-derived loader distributions are authoritative direct cells. Public aggregate tasks use the direct graph. `validateStonecutterDirectContract` is mandatory. Native Stonecutter preprocessing has focused proof for archive capture on 16 targets, SharedZip access on 17 targets (1.20.2 through 1.21.11), ReloadableResourceManager on 18 pre26 targets (1.20.1 through 1.21.11), RuntimeResourceHash on 17 targets (1.20.1 through 1.21.10), LoadingOverlayToast on 14 targets (eight common 1.20.5 through 1.21.5 plus six modern 1.21.6 through 1.21.11), Bitmap provider definition on all 19 targets (1.20.1 through 26.1), and SimpleReload on four targets (1.21.5 through 1.21.8); later physical seams remain where needed. The direct helper is always-on; the legacy wrapper parity oracle is explicit opt-in. | `buildStonecutterAll`, `verifyStonecutterAll`, and opt-in `verifyStonecutterParityAll` pass across all 53 direct distributions. The clean 20-artifact publication build and aggregate structural checks pass. Runtime and broader preprocessing proof remain open. |
| Source structure | Current metric snapshot reports 209 production Java files, 15,814 nonblank LOC, 110 bridge files / 5,553 LOC (35.11%), zero exact or normalized duplicate groups, six target/version conditional lines, zero platform target-key conditional lines, ten Stonecutter conditional blocks (maximum 40 lines), three renderer bodies, two renderer adapters, and 20 registry publication artifacts. The Phase F ledger records 2,347 mandatory added LOC and 3,370 adjusted reduction against 3,368 required. | Metrics and the ledger prove source shape and known gate outcomes only; they do not prove runtime behavior. |
| Configuration | Unknown JSON fields are preserved; integer input validation is shared; modern screen rendering is shared; configured atlas retry remains preserved while shader presence can make its effective state unavailable. Replacement-stage failure preserves the installed configuration and existing nonempty target and cleans the temporary file. | Focused tests and limited compile checks exist. No current live renderer-family or shader-mod runtime proof exists. |
| Quick Pack ownership | Policy hands off exactly six overlap capabilities and retains the other 23 capabilities. Environment-gated reporting emits loader-observed profile and ownership state. | Current final-JAR Quick Pack runs have not been executed. Version-policy tests do not prove old, current, or future Quick Pack runtime compatibility. |
| Compatibility catalog | 36 recipes validate structurally: 21 are metadata-`AVAILABLE` but `UNTESTED`, 11 are `UNAVAILABLE` with dated public-source evidence, and four remain `PENDING_METADATA`/`UNTESTED`. | `AVAILABLE` means pinned inputs can be materialized, not that a profile passed; `UNAVAILABLE` is a disposition, not a runtime PASS. |
| Profile transport | Schema-2 materialization carries catalog identity, hashes, pinned mod paths, fixture metadata, markers, and evidence paths through all three loader wrappers. Hash-addressed caching and fail-closed validation are implemented. Controlled Fabric/Forge/NeoForge smokes now emit and matrix records bind one deterministic resolved-resource SHA-256; the exact matrix requires ten reloads per cell. | No third-party compatibility-profile download or profile execution was performed. The separate focused slim-MixinExtras smokes did launch one current final JAR per loader and passed one reload. ImmediatelyFast-only path proof remains unavailable. Exactly five safe boolean overrides are accepted; unsupported keys and values fail closed. |
| Fixtures | Nine deterministic 1.21.1 compatibility fixtures and seven target-specific 20,004-entry production fixtures are generated reproducibly; each carries the deterministic marker required for positive resolved-resource evidence. | The production fixture was run through all 62 base cells. The nine heavy compatibility fixtures and explicit cancellation/failure scenarios remain separate unexecuted gates. |
| Default-off candidates | Frozen catalog has five `SAFE_KEEP_DEFAULT_OFF` and six `FAILED_WITH_REASON` dispositions. No candidate was promoted. | All runtime, lifecycle, compatibility, fixture, and performance gates remain `NOT_RUN`. |
| Release verification | Existing-manifest verification, exact fixture contract checks, execution-scenario identity, release-manifest preflight, and mutation self-tests are implemented. | Current `buildAllSupported` produced exactly 20 version-1.4 JARs; `verifyAllArtifacts` and `verifyReleaseManifest` both PASS. |
| Exact final-JAR matrix | Registry-derived controller ran every declared 22-release/62-loader cell using the current 20 final JARs and target-specific deterministic production fixtures. | `build/production-matrix/full-62-20260816-batched/summary.json` records cumulative `62/62`, `cumulativeFailed=0`; all records have `reloads=10`, a positive stable resolved-resource SHA-256, `cleanExit=true`, and current artifact/fixture/provenance hashes. |

## Focused 1.4 slim-MixinExtras evidence

The direct classifier resolves from Maven Central for Fabric, Forge, and NeoForge at `io.github.llamalad7:mixinextras-{loader}:0.5.4:slim`. The three representative final JARs contain one loader-specific slim nested artifact, no duplicate ZIP entries, and intact mixin metadata:

| Loader / target | Final JAR | Slim nested artifact | Structural result | Runtime result |
|---|---|---|---|---|
| Fabric `mc1_21_1` | `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar` | `META-INF/jars/mixinextras-fabric-0.5.4-slim.jar` | 1 nested copy; 2 mixin configs; no duplicate entries | PASS, one reload, MixinExtras 0.5.4 initialized, resolved hash `5D6A4D61...B94C5F`, clean exit |
| Forge `mc1_20_2` | `packforge-forge-1.4-beta.3-mc1.20.2-1.20.4.jar` | `META-INF/jarjar/mixinextras-forge-0.5.4-slim.jar` | 1 loader artifact; `packforge.refmap.json`; 5 mixin configs; no duplicate entries; nested common runtime retained by the slim shim | PASS, one reload, MixinExtras 0.5.4 initialized, resolved hash `5D6A4D61...B94C5F`, clean exit |
| NeoForge `mc1_21_1` | `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar` | `META-INF/jarjar/mixinextras-neoforge-0.5.4-slim.jar` | 1 nested copy; 2 mixin configs; no duplicate entries | PASS, one reload, MixinExtras 0.5.4 initialized, resolved hash `5D6A4D61...B94C5F`, clean exit |

Compared with the pre-slim 1.3.4 root artifacts, the final JAR reductions were Fabric 53.8% (1,438,170 → 665,001 bytes), Forge 54.5% (1,414,534 → 643,181 bytes), and NeoForge 54.2% (1,427,147 → 654,094 bytes). The intentional 128×128 PackForge icon remains the 10,607-byte resource; it was not restored.

The no-rebuild `inspectArtifactSizes` task (with optional `-Ppackforge_artifact_size_dir=...`) reports final JAR size, PackForge class bytes, resource bytes, largest entries, nested JAR names/sizes, and duplicate ZIP entries.

## Focused validation reused

The 2026-08-16 verification checkpoint ran the requested clean/full build, all-53 opt-in parity oracle, and complete bounded 62-cell final-JAR matrix. Relevant results are:

- Source metrics: PASS; 209 files, 15,814 LOC, 110 bridge files / 5,553 LOC (35.11%), zero duplicate groups, six target/version conditional lines, zero platform target-key conditional lines, ten Stonecutter conditional blocks (maximum 40 lines), three renderer bodies, two adapters.
- Direct graph contract: task PASS across 53 direct cells with four native SimpleReload cells. `buildStonecutterAll` and `verifyStonecutterAll` pass; opt-in `verifyStonecutterParityAll` completed all 53 direct/legacy comparisons successfully in 28m35s. The direct/parity split self-test passed with 53 direct cells and 106 rejected mutations.
- Default root build: `build` PASS in 10m18s across 796 tasks (382 executed); the final root artifact directory remained at exactly 20 JARs and `verifyExistingArtifacts` passed afterward.
- Compatibility catalog: 36/36 structural validation PASS; positive available-profile and mutation cases PASS; current disposition is 21 `AVAILABLE`/`UNTESTED`, four `PENDING_METADATA`/`UNTESTED`, and 11 `UNAVAILABLE`.
- Profile materialization: offline self-test PASS; five-script AST validation PASS.
- Fixture generator: deterministic double-generation self-test PASS; unsupported-version rejection PASS; all nine fixture families include the hash-visible marker consumed by the controlled runtime contract.
- Default-off catalog: exact 11-candidate mapping and 14 rejected mutations PASS.
- Compatibility reporter and atlas policy: focused unit tests PASS.
- Existing-manifest verifier: generator/self-test PASS; fixture manifest/scenario and release preflight contract self-tests PASS; current 20-entry version-1.4 manifest verification PASS.
- Focused slim-MixinExtras validation: Fabric, Forge, and NeoForge direct/parity builds PASS; all 20 final JARs pass nested slim-artifact and duplicate-entry inspection, and the three representative one-reload production smokes PASS with MixinExtras initialization, deterministic resolved-resource hash, and clean exit.
- Resolved-resource hash contract: focused helper, matrix resume, profile transport, ten-reload enforcement, and aggregate implementation-contract self-tests PASS; every current matrix record contains a positive stable final-JAR semantic hash.
- Full matrix: 16 bounded four-cell waves completed with cumulative `62/62` PASS, zero cumulative failures, ten reloads per cell, clean exit, and unchanged artifact hashes after the post-matrix incremental build/parity checks.

## Current acceptance gaps

Release readiness requires new evidence from current bytes:

1. Run representative heavy fixture families plus explicit cancellation/failure lifecycle scenarios against current final JARs.
2. Run required Quick Pack and other available compatibility profiles on Fabric, Forge, and NeoForge. Keep every unexecuted profile `UNTESTED`.
3. Complete live configuration-renderer parity and any required shader/atlas retry runtime checks.
4. Record a new final checkpoint and rollback point only after those gates pass.

## Historical evidence, not current proof

| Historical checkpoint | Recorded result | Current use |
|---|---|---|
| `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` | 62/62 exact runtime cells and 20 publication artifacts. | Regression reference only; later artifact-path changes invalidate byte binding. |
| `a3402866b217ac159d6a3cec70d3585028f79732` | Source consolidation, 18/20 hash continuity, four affected Forge reruns, and final Quick Pack 1.5.0 Fabric run. | Regression reference only; not evidence for current artifacts. |
| Historical Quick Pack profile | Fabric 1.21.1, Quick Pack 1.5.0, ten reloads, module handoff, clean exit. | Confirms earlier design behavior only. Current profile catalog remains `UNTESTED`. |
| Historical artifact hashes | Twenty hashes listed in older reports. | Do not publish or compare as current hashes without rebuilding and verifying current artifacts. |

## Stopping state

No release publishing occurred. The current 20-artifact structural build/manifest, same-binary ranges, and complete 62-cell base matrix are verified; third-party runtime profiles, heavy/cancellation fixture scenarios, and live UI runs remain unclaimed. Unrelated `.github/ISSUE_TEMPLATE/bug-report.yml`, `.codegraph/`, and the untracked plan/README files remain outside this refactor.
