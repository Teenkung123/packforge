# Final validation

Date: 2026-08-15

## Outcome

Current continuation state is **PARTIAL** and **NOT RELEASE READY**. The current version is `1.4`; all-loader MixinExtras packaging uses the resolved `0.5.4:slim` classifier.

Current source, current build wiring, and artifacts produced from current bytes are authoritative. The 62-cell runtime result, Quick Pack 1.5.0 run, 20-artifact manifest, and hashes recorded at checkpoints `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` and `a3402866b217ac159d6a3cec70d3585028f79732` remain useful historical regression evidence. They are not current release proof because later direct-build, configuration, reporter, and harness changes changed the code and artifact path without a replacement full runtime matrix.

## Current verified implementation evidence

| Area | Current result | Boundary |
|---|---|---|
| Registry | Schema v2 retains 22 exact Minecraft releases, 62 applicable exact runtime cells, seven publication anchors, and 20 expected loader artifacts. | Registry declarations are not runtime results. |
| Direct build graph | 53 registry-derived loader distributions are authoritative direct cells. Public aggregate tasks use the direct graph. `validateStonecutterDirectContract` is mandatory. Native Stonecutter preprocessing has focused Fabric proof for archive capture on 16 targets, SharedZip access on 17 targets (1.20.2 through 1.21.11), ReloadableResourceManager on 18 pre26 targets (1.20.1 through 1.21.11), RuntimeResourceHash on 17 targets (1.20.1 through 1.21.10), LoadingOverlayToast on 14 targets (eight common 1.20.5 through 1.21.5 plus six modern 1.21.6 through 1.21.11), Bitmap provider definition on all 19 targets (1.20.1 through 26.1), and SimpleReload on four targets (1.21.5 through 1.21.8); later physical seams remain where needed. The direct helper is always-on; the legacy wrapper parity oracle is explicit opt-in. | `buildAllSupported` and `verifyAllArtifacts` now PASS for the current 20 version-1.4 artifacts; focused Fabric `mc1_21_1`, Forge `mc1_20_2`, and NeoForge `mc1_21_1` direct/parity cells also PASS. Full all-53 parity, Java 17/21/25 aggregate proof, and general cross-loader preprocessing proof remain open. |
| Source structure | Current metric snapshot reports 217 production Java files, 16,577 nonblank LOC, 110 bridge files / 5,553 LOC (33.50%), zero exact or normalized duplicate groups, 15 target/version conditional lines, zero platform target-key conditional lines, ten Stonecutter conditional blocks (maximum 40 lines), three renderer bodies, two renderer adapters, and 20 registry publication artifacts. The Phase F ledger records 2,347 mandatory added LOC, 2,607 adjusted reduction, and a 761-LOC shortfall. | Metrics and the ledger prove source shape and known gate outcomes only; they do not prove compilation, packaging, or runtime behavior. |
| Configuration | Unknown JSON fields are preserved; integer input validation is shared; modern screen rendering is shared; configured atlas retry remains preserved while shader presence can make its effective state unavailable. Replacement-stage failure preserves the installed configuration and existing nonempty target and cleans the temporary file. | Focused tests and limited compile checks exist. No current live renderer-family or shader-mod runtime proof exists. |
| Quick Pack ownership | Policy hands off exactly six overlap capabilities and retains the other 23 capabilities. Environment-gated reporting emits loader-observed profile and ownership state. | Current final-JAR Quick Pack runs have not been executed. Version-policy tests do not prove old, current, or future Quick Pack runtime compatibility. |
| Compatibility catalog | 36 recipes validate structurally: 21 are metadata-`AVAILABLE` but `UNTESTED`, 11 are `UNAVAILABLE` with dated public-source evidence, and four remain `PENDING_METADATA`/`UNTESTED`. | `AVAILABLE` means pinned inputs can be materialized, not that a profile passed; `UNAVAILABLE` is a disposition, not a runtime PASS. |
| Profile transport | Schema-2 materialization carries catalog identity, hashes, pinned mod paths, fixture metadata, markers, and evidence paths through all three loader wrappers. Hash-addressed caching and fail-closed validation are implemented. Controlled Fabric/Forge/NeoForge smokes now emit and matrix records bind one deterministic resolved-resource SHA-256; the exact matrix requires ten reloads per cell. | No third-party compatibility-profile download or profile execution was performed. The separate focused slim-MixinExtras smokes did launch one current final JAR per loader and passed one reload. ImmediatelyFast-only path proof remains unavailable. Exactly five safe boolean overrides are accepted; unsupported keys and values fail closed. |
| Fixtures | Nine deterministic 1.21.1 resource-pack fixtures and manifest contracts are generated reproducibly; each fixture includes the deterministic `example`-namespace marker required for positive resolved-resource evidence. | No fixture has been run through current final JARs. Reload repetition and cancellation are execution scenarios, not materialized fixture claims. |
| Default-off candidates | Frozen catalog has five `SAFE_KEEP_DEFAULT_OFF` and six `FAILED_WITH_REASON` dispositions. No candidate was promoted. | All runtime, lifecycle, compatibility, fixture, and performance gates remain `NOT_RUN`. |
| Release verification | Existing-manifest verification, exact fixture contract checks, execution-scenario identity, release-manifest preflight, and mutation self-tests are implemented. | Current `buildAllSupported` produced exactly 20 version-1.4 JARs; `verifyAllArtifacts` and `verifyReleaseManifest` both PASS. |

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

No clean build or broad runtime matrix was run for this reconciliation. Smallest relevant checks already recorded at current checkpoints are:

- Source metrics: PASS; 217 files, 16,577 LOC, 110 bridge files / 5,553 LOC (33.50%), zero duplicate groups, 15 target/version conditional lines, zero platform target-key conditional lines, ten Stonecutter conditional blocks (maximum 40 lines), three renderer bodies, two adapters.
- Direct graph contract: task PASS across 53 direct cells with four native SimpleReload cells. Focused offline Fabric 1.21.4/1.21.5/1.21.6 `compileJava` plus `sourcesJar` reached BUILD SUCCESSFUL in 51 seconds across 18 executed tasks. The 1.21.5 and 1.21.6 generated/source-JAR SimpleReload entries matched SHA-256 prefix `B2F6321B`; both compiled classes were 4,648 bytes. The 1.21.4 generated source stayed inactive, retained its 4,641-byte physical class, and contributed no generated source-JAR entry. Earlier focused LoadingOverlayToast and registry validation remain reusable evidence. The direct/parity split self-test passed with 53 direct cells and 106 rejected mutations.
- Compatibility catalog: 36/36 structural validation PASS; positive available-profile and mutation cases PASS; current disposition is 21 `AVAILABLE`/`UNTESTED`, four `PENDING_METADATA`/`UNTESTED`, and 11 `UNAVAILABLE`.
- Profile materialization: offline self-test PASS; five-script AST validation PASS.
- Fixture generator: deterministic double-generation self-test PASS; unsupported-version rejection PASS; all nine fixture families include the hash-visible marker consumed by the controlled runtime contract.
- Default-off catalog: exact 11-candidate mapping and 14 rejected mutations PASS.
- Compatibility reporter and atlas policy: focused unit tests PASS.
- Existing-manifest verifier: generator/self-test PASS; fixture manifest/scenario and release preflight contract self-tests PASS; current 20-entry version-1.4 manifest verification PASS.
- Focused slim-MixinExtras validation: Fabric, Forge, and NeoForge direct/parity builds PASS; final-JAR inspection and one-reload production smokes PASS with MixinExtras initialization, deterministic resolved-resource hash, and clean exit.
- Resolved-resource hash contract: focused helper, matrix resume, profile transport, ten-reload enforcement, and aggregate implementation-contract self-tests PASS; no final-JAR semantic hash or 62-cell runtime result exists.

## Current acceptance gaps

Release readiness requires new evidence from current bytes:

1. Prove all 53 authoritative direct build cells, including Java 17/21/25, remap/refmap, JarJar, metadata, and structural parity.
2. Extend the proven Fabric archive-capture preprocessing pilot into the intended authoritative source transport, or document and accept direct-source composition for the remaining sources; then remove the explicit legacy parity oracle after direct parity is proven.
3. Run all 62 exact final-distribution-JAR cells with startup, required fixtures, deterministic reload/repetition, semantic/resource evidence, and exact clean-exit evidence.
4. Reprove each publication range with the same current JAR across every exact release it claims.
5. Run required Quick Pack and other available compatibility profiles on Fabric, Forge, and NeoForge. Keep every unexecuted profile `UNTESTED`.
7. Complete live configuration-renderer parity and any required shader/atlas retry runtime checks.
8. Record a new final checkpoint and rollback point only after those gates pass.

## Historical evidence, not current proof

| Historical checkpoint | Recorded result | Current use |
|---|---|---|
| `051aaaca42bdc980a3260d1a6c6fcd4404f228b7` | 62/62 exact runtime cells and 20 publication artifacts. | Regression reference only; later artifact-path changes invalidate byte binding. |
| `a3402866b217ac159d6a3cec70d3585028f79732` | Source consolidation, 18/20 hash continuity, four affected Forge reruns, and final Quick Pack 1.5.0 Fabric run. | Regression reference only; not evidence for current artifacts. |
| Historical Quick Pack profile | Fabric 1.21.1, Quick Pack 1.5.0, ten reloads, module handoff, clean exit. | Confirms earlier design behavior only. Current profile catalog remains `UNTESTED`. |
| Historical artifact hashes | Twenty hashes listed in older reports. | Do not publish or compare as current hashes without rebuilding and verifying current artifacts. |

## Stopping state

No release publishing occurred. The current 20-artifact structural build/manifest and three focused loader smokes are verified; the exact matrix, third-party runtime profiles, benchmark, and live UI run remain unclaimed. Unrelated `.github/ISSUE_TEMPLATE/bug-report.yml`, `.codegraph/`, and `docs/PackForge_Missing_Implementation_Plan.md` work remains outside this refactor.
