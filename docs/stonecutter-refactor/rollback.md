# Rollback plan

## Baseline

The functional baseline is commit `609270f533666e7636d11f7d16590be925ec836f`. The implementation branch is `codex/packforge-stonecutter-refactor`; the unrelated modified `.github/ISSUE_TEMPLATE/bug-report.yml` must remain outside functional commits.

## Rules

- Use the chronological map in `stable-checkpoints.md`.
- Revert verified functional units with normal `git revert <sha>`.
- Do not reset, discard, amend, squash, rebase away, or force-push verified checkpoints.
- Keep legacy build tasks available until Stonecutter current-matrix parity is proven.
- Keep per-target capability exclusions and per-feature defaults as rollback controls.
- If an optional path fails, return to vanilla/original control flow without disabling unrelated capabilities.

## Migration rollback points

1. Before registry schema migration: baseline HEAD above.
2. Before Stonecutter switch: last verified parallel-build checkpoint.
3. Before each capability migration: immediately preceding checkpoint.
4. Before each range consolidation/default promotion: preceding exact-release proof checkpoint.
5. Final rollback: revert the latest named checkpoint(s) in reverse dependency order.

## Latest checkpoints

- `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`: planned 1.20.2 target, Fabric/Forge bridge, Stonecutter node, and planned-target exclusion from the published 17-artifact aggregate. Revert after `862022c` if the feasibility cell is abandoned.
- `862022c40249155f1d43fb55d97f3c4cb132c136`: keep the 1.21.11 archive constructor hook version-local. Revert this first to restore the prior descriptor before reverting the 1.20.2 bridge.
- `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`: complete all remaining Java 21 range candidates, capability floors, and exact runtime gates. Revert before removing their source-family definitions.
- `a3402866b217ac159d6a3cec70d3585028f79732`: latest fully verified checkpoint. It promotes all exact rows, adds the resumable 62-cell controller and per-version Fabric natives, consolidates canonical shared sources, and adds the source-metrics gate. Revert this first to restore per-adapter source ownership and the prior publication ledger.
- `71f29b116f40e967996fa5e75e45aad89a7b0bb6`: latest continuation baseline. It adds the honest phase audit and portable deterministic source/ownership/branch metrics with exact-duplicate enforcement. Revert this checkpoint to return to the earlier limited metrics task; it does not alter PackForge runtime behavior.
- `0c2fb7bcff5dd634254f3ad25b205b896acfe34e`: operation-level Quick Pack ownership and phase-safe loader metadata. Revert this checkpoint to restore the prior early whole-mixin suppression; do not treat its build/package proof as real Quick Pack runtime evidence.
- `3840a63bb8361cadd216ad15853efb4428ac29d1`: immutable compatibility-profile inputs, cross-loader additional-mod provenance, log-marker assertions, and evidence-bound matrix resume. Revert this harness checkpoint before reverting operation-level Quick Pack ownership; no live compatibility result is implied by this checkpoint alone.
- `8241e600d807f3900224e90a17d3467413d30ee1`: registry-derived Stonecutter graph with 19 source anchors, 53 loader distributions, and a separate 62-cell exact runtime ledger. Revert later direct-build work first, then revert this checkpoint to restore the prior manually enumerated Stonecutter project graph and settings; the retained delegated platform builds remain the rollback path until Phase E parity is proven.
- `6125316572a086369320616ec754a8076068e29a`: direct Fabric `mc1_21_1` Stonecutter pilot and registry-mode-aware root aggregation. Revert this checkpoint before the Phase D graph checkpoint to return that cell to delegated build, collection, verification, and clean ownership; no other loader cell is cut over by this commit.
- `68866ccffae9a06d404d5a791dd4e68d089f9486`: direct-build ownership for all 19 Fabric Stonecutter leaves plus the shared direct/parity helper. Revert this first to return to the verified single-cell Fabric pilot. This checkpoint has representative Java 17/21/25 and one 213-entry parity proof, not a completed all-19 aggregate or runtime matrix.
- `c28cc8cf62dbafa7cd3a52496f259848788c3a78`: direct Forge `mc1_20_1` legacy-remap and `mc1_21_1` modern-JarJar pilots. Revert this before the Fabric matrix/helper checkpoint if Forge direct packaging must return to delegation; no Forge-wide parity or runtime proof is implied.
- `2beb0bd821c51d37f2a4b93cb57293ce0be70d5f`: guarded direct NeoForge `mc1_20_2` UserDev and `mc1_21_1` ModDev pilots. Revert this before earlier direct-loader checkpoints if NeoForge must return to delegation. Package and structural proof exists, but actual NeoForge ZIP parity/runtime proof does not.
- `9b6e25512c4363829f3b1a5f86538cabf2797fa5`: registry-default direct ownership for all 53 loader leaves. Revert this first to return Forge and NeoForge to their verified pilots while keeping Fabric direct. The all-cell build/parity/runtime matrices remain deferred at this checkpoint.
- `782510c685db64a90d99e177c5d7531e07d9668f`: forward-compatible configuration persistence and duplicate option-ID enforcement. Revert this before older configuration-model work if unknown-field preservation must be removed; doing so can make later-schema fields disappear on the next save.
- `e0a644d3443fbecc37fef7537de96e5d9cee148a`: README publication declaration and exact loader table consistency gate. Revert this documentation contract before changing the canonical publication count or target keys outside the registry; no runtime behavior depends on it.
- `d21a2a5f0652b46afd92bde87725fb943e1842a3`: isolates README publication validation as an explicit contract. Revert before changing its task ownership or restoring the registry-integrated gate; no compile or runtime evidence belongs to this checkpoint.
- `4c76d6520080834a3c278bbefd7c0ce31189da84`: centralizes seven registry-owned source policies and all target assignments. Revert this first if policy interpretation mis-selects sources, then restore loader-local selection. Static golden comparison, independent review, and `validateStonecutterRegistry` (34 seconds, 53 leaves, 62 cells) passed; compile/package/runtime remain unproven.
- `839928cb7ee2be3ebcab73db859e0c2a5fc34a84`: shared modern configuration renderer extraction. Revert only after later integer-validation and documentation checkpoints; Fabric `mc1_21_10` and `mc1_21_11 compileClientJava` each passed in 10 seconds, but no live UI/client evidence exists.
- `35a6b4ca39b8afc0a9d1a096a76780b463ff2b5e`: integer validation parity and structural screen contract. Revert this first, then `839928c`, after later documentation. Focused Fabric test (five tests, zero failures/errors), legacy/26.x client compilation, and `Validate-ConfigScreenContract` (`bodies=3`, `wrappers=2`, `entryPoints=9`, `sharedTargets=14`) passed; structural proof is bounded and live UI/client runtime remains unverified.
- `5f668bc597ffdc094c91fda645fde19e7f0ed5f2`: catalog-backed compatibility-profile runner selection. Revert this newest Phase I checkpoint first to remove profile-ID selection and its duplicate/conflicting selector guards; then revert `b35a0b0` and `934c283` if the catalog foundation must also be removed. Runner AST and focused selector tests passed, but no profile was downloaded or launched.
- `b35a0b04305aed040d0ae6d9f203acb3a0f3d84f`: fail-closed profile pin/evidence validation. Revert after `5f668bc` to return to catalog-only validation. The positive fixture plus 14 rejected mutations prove validator behavior only; fake-pin/nonexistent-evidence retests likewise did not resolve real metadata.
- `934c283695a099593134e178bbe4b26f274ffe1a`: declarative 36-profile compatibility catalog. Revert last among the Phase I foundation commits. All entries remain `PENDING_METADATA` and `UNTESTED`; `AVAILABLE` materialization intentionally fails closed until exact metadata, hashes, and execution evidence exist. The direct catalog validator is invoked by the runner, not by a root Gradle/CI gate.
- `67a7bf5e57de4d35089983db00ae84328d530c7c`: existing release-manifest verifier. Revert after later documentation-only checkpoints and before its parent `1914912723bcfa19742d58e3bb549d2c203ab5b5`; this removes non-writing manifest verification and its self-test without changing generated artifacts. Generator/self-test and independent review passed, but the real verifier was not run: only 7/20 expected JARs existed and `build/libs/release/manifest.json` was absent. No release, full-matrix, or Minecraft runtime proof belongs to this checkpoint.

The unrelated `.github/ISSUE_TEMPLATE/bug-report.yml` edit remains unstaged and is not part of any rollback.
