# Stable checkpoints

Checkpoint entries are chronological and must include commit SHA, parent, scope, exact verification, result, and rollback instruction.

## Baseline documentation

- Commit SHA: `71b6ba396b60b79ed0cf2bdfb8c31ab7f7c76ce0`
- Parent SHA: `609270f533666e7636d11f7d16590be925ec836f`
- Scope: baseline evidence and migration rollback documents only
- Verification: `validateTargetRegistry`, clean `buildAllSupported`, direct `verifyExistingArtifacts`, focused tests, PackIndex benchmark; runtime smoke limitations recorded
- Status: `FOCUSED_VERIFIED`
- Rollback: no functional behavior changed; revert the baseline documentation commit if necessary

## Registry schema v2

- Commit SHA: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Parent SHA: `bfd63ce2549f81731f5ba05a4e7cfeeeeb627c20`
- Scope: schema v2 fields, checked-in 22-release sequence/cells, maturity policy, and resolved-matrix task
- Verification: `./gradlew.bat validateTargetRegistry printResolvedMatrix buildTarget -Ppackforge_target=mc1_21_1 --no-daemon --stacktrace`; final focused build and all three child artifact verifiers passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert the registry checkpoint; schema v1 baseline is parent commit plus the documentation checkpoint

## Stonecutter current-target pilot

- Commit SHA: `e078d00e1b4ffcbb32afc8667f81b99f0ae8fa3b`
- Parent SHA: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Scope: Stonecutter 0.9.7 settings integration and a parallel `mc1_21_1` current-target build path
- Verification: `:mc1_21_1:tasks`; `:mc1_21_1:verifyStonecutterCurrentTarget`; Fabric, Forge, and NeoForge child builds plus exact three-artifact check
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to return to the registry-only build; the legacy root aggregator remains available in the parent

## Central capability ownership and feature policy

- Commit SHA: `de3138344c633ca68b450e9688d0adb556c3de83`
- Parent SHA: `5f3ee749fe080f2f6035d9b5b0adf9a6924d1a3d`
- Scope: immutable `FeaturePolicy`, compatibility `FeatureFlags` facade, and reload-boundary policy capture
- Verification: focused Fabric 1.21.1 test suite; 106 tests completed successfully
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to return to the Stonecutter pilot; capability declarations and public feature-flag calls remain in the parent

## Stonecutter root verification wiring

- Commit SHA: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`
- Parent SHA: `5f3ee749fe080f2f6035d9b5b0adf9a6924d1a3d`
- Scope: restore repository-level registry/release tasks on the Stonecutter root and gate the current-target verifier on registry validation
- Verification: `./gradlew.bat :validateTargetRegistry`; `:mc1_21_1:verifyStonecutterCurrentTarget`
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this build-wiring repair; the Stonecutter pilot remains available but root verification would no longer be attached

## Archive and reload implementation deduplication

- Commit SHA: `723c504ca5ec2012e8535efc15bd96d4fbec8854`
- Parent SHA: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`
- Scope: shared 1.21.1/1.21.4/1.21.8 archive/reload mixins, platform source roots, and effective-source ownership validation
- Verification: root registry guard plus `buildTarget` for 1.21.1, 1.21.4, and 1.21.8; all three loader artifact verifiers passed for each target
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore per-version 1.21.1/1.21.4/1.21.8 hook copies

## Shared 1.21 client bootstrap

- Commit SHA: `e15103a52b52308ed7648cd27e6ff32f4e7ad24d`
- Parent SHA: `b97fc31fc9a978c99c171904c0036350d8f49012`
- Scope: shared 1.21.1/1.21.4/1.21.8 `PackForgeClient` reset bootstrap and exact-one effective-source validation
- Verification: root registry guard and all-loader `buildTarget` for 1.21.1, 1.21.4, and 1.21.8
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the three version-local client bootstrap copies

## Quick Pack ownership

- Commit SHA: `35c47274fa5ccaf7b340327a164c0ebd5effaea7`
- Parent SHA: `b532d3dfe2d72461605db1f1467f95a0044d2da1`
- Scope: public Quick Pack detection, conservative ownership policy, and effective-state model
- Verification: focused policy tests and Fabric target test suite passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to remove the external ownership policy while retaining the prior feature policy

## Quick Pack early gating

- Commit SHA: `2759979bb93afc9ec62703a2ca08d429393e870b`
- Parent SHA: `35c47274fa5ccaf7b340327a164c0ebd5effaea7`
- Scope: loader-specific early Mixin suppression for Quick Pack overlap paths
- Verification: current loader builds and descriptor/plugin artifact checks passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the pre-gating mixin selection

## Unified configuration

- Commit SHA: `e460da0e39f744544b4daf2ceb9844c87282e062`
- Parent SHA: `2759979bb93afc9ec62703a2ca08d429393e870b`
- Scope: central option schema, effective-state reporting, categories, search/paging, reset/save behavior, and native screen bridges
- Verification: current configuration tests and existing target builds passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to restore the prior screen implementations

## Exact-release registry and source-family ledger

- Commit SHA: `cd87525fbe763a313a607d2d15183b02657d0ecd`
- Parent SHA: `e460da0e39f744544b4daf2ceb9844c87282e062`
- Scope: schema-v2 exact release cells, twelve source families, maturity policy, capability contracts, and machine-readable matrix output
- Verification: `validateTargetRegistry` and `printResolvedMatrix` passed with all 22 required release IDs
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to the prior target registry schema

## Stonecutter current-target expansion

- Commit SHA: `e3f6f008965fd127795b2d4abe8efaff41f407af`
- Parent SHA: `cd87525fbe763a313a607d2d15183b02657d0ecd`
- Scope: current Stonecutter anchors plus the focused 1.20.2 node
- Verification: current Stonecutter target tasks and verifiers passed; 1.20.2 remained a focused feasibility cell
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this checkpoint to remove the added Stonecutter node while leaving the prior current anchors

## Planned 1.20.2 bridge

- Commit SHA: `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`
- Parent SHA: `e3f6f008965fd127795b2d4abe8efaff41f407af`
- Scope: 1.20.2 registry/platform coordinates, thin constructor/sprite/UI source bridge, target descriptor/access widener, and exclusion of planned targets from published aggregation
- Verification: Fabric and Forge `clean build` passed; focused JAR contents include the bridge and target descriptor. NeoForge is blocked before source compilation by two bounded toolchain attempts.
- Status: `FOCUSED_VERIFIED` for Fabric/Forge compile/package only; release cell remains `planned`
- Rollback: revert `862022c` first, then this checkpoint

## 1.21.11 descriptor repair

- Commit SHA: `862022c40249155f1d43fb55d97f3c4cb132c136`
- Parent SHA: `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`
- Scope: remove an invalid shared archive bridge reference from the version-local 1.21.11 descriptor
- Verification: Fabric 1.21.11 `clean build` passed
- Status: `FOCUSED_VERIFIED`
- Rollback: revert this descriptor-only repair
