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
