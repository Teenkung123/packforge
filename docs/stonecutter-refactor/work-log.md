# Stonecutter refactor work log

## Phase 0 — baseline and branch safety

- Date: 2026-08-11
- Commit SHA: `71b6ba396b60b79ed0cf2bdfb8c31ab7f7c76ce0`
- Parent stable checkpoint: `609270f533666e7636d11f7d16590be925ec836f`
- Status: `FOCUSED_VERIFIED`
- Files changed: `docs/stonecutter-refactor/*.md`
- Architecture decision: use a staged strangler migration; preserve standalone loader builds until a one-target Stonecutter parity pilot passes.
- Commands: see `baseline.md`.
- Results: registry validation PASS; clean 17-artifact build PASS; direct final-artifact verification PASS; Fabric/Forge/NeoForge focused tests PASS; PackIndex benchmark PASS with equal semantic hashes; aggregate `verifyAllArtifacts` timed out and is not claimed; runtime harnesses reached startup/reload but stopped at GUI window activation.
- Artifacts: 17 baseline JARs, 23,691,877 total bytes; SHA-256 list in `artifact-consolidation.md`.
- Semantic hash: `28ba0ce3fce57d83e14e7e9d47c386316eba77156baaa3ac5984fe47952f4a8b` for baseline and indexed paths.
- Compatibility: exact current matrix build/test evidence only; runtime and third-party profiles remain `UNTESTED` where stated.
- Source metrics: 16,837 production LOC / 212 Java files; 20 normalized duplicate-file groups across versions.
- Rollback point: baseline HEAD `609270f533666e7636d11f7d16590be925ec836f`.
- Next mandatory phase: registry schema v2 without artifact behavior change.

## Phase 1 — canonical registry schema v2

- Date: 2026-08-11
- Commit SHA: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Parent stable checkpoint: `bfd63ce2549f81731f5ba05a4e7cfeeeeb627c20`
- Status: `FOCUSED_VERIFIED`
- Files changed: `gradle/minecraft-targets.json`, `build.gradle`, `gradle/packforge-target.gradle`, registry evidence docs
- Architecture decision: keep current six build targets and 17 artifacts while adding schema v2 family metadata, fixed maturity policy, checked-in 22-release exact ledger, and registry-derived JSON output. Planned cells do not claim support until exact artifacts pass.
- Commands: `./gradlew.bat validateTargetRegistry printResolvedMatrix --no-daemon --stacktrace`; `./gradlew.bat buildTarget -Ppackforge_target=mc1_21_1 --no-daemon --stacktrace`.
- Results: schema validation and matrix output PASS; focused Fabric, Forge, and NeoForge 1.21.1 builds plus target artifact verifiers PASS. First build attempt exposed a stale schema v1 guard in `gradle/packforge-target.gradle`; repaired before checkpoint and reran successfully.
- Generated artifacts: current 1.21.1 target artifacts rebuilt; no target count or public artifact naming change.
- Semantic hashes: unchanged baseline/indexed PackIndex hash; no algorithm change.
- Compatibility: no runtime behavior change intended; exact-release cells marked planned or range-unverified where proof is absent.
- Source metrics: unchanged from Phase 0.
- Deviations: Stonecutter not yet authoritative; this unit intentionally stops at registry authority and current-build compatibility.
- Rollback point: parent `bfd63ce2549f81731f5ba05a4e7cfeeeeb627c20`.
- Next mandatory phase: Stonecutter current-target scaffold/pilot.

## Phase 2 — Stonecutter current-target pilot

- Date: 2026-08-11
- Commit SHA: `e078d00e1b4ffcbb32afc8667f81b99f0ae8fa3b`
- Parent stable checkpoint: `8d53b8abd996b6d03a0cf135325144db5748c985`
- Status: `FOCUSED_VERIFIED`
- Files changed: `settings.gradle`, `stonecutter.gradle`, `stonecutter-build.gradle`, checkpoint documentation
- Architecture decision: apply Stonecutter 0.9.7 only to the registered `mc1_21_1` pilot and keep the existing root aggregator as the fallback authority. The pilot invokes standalone loader builds directly, avoiding recursive root task re-entry and preserving Forge/NeoForge packaging.
- Commands: `./gradlew.bat :mc1_21_1:tasks --no-daemon --stacktrace`; `./gradlew.bat :mc1_21_1:verifyStonecutterCurrentTarget --no-daemon --stacktrace`.
- Results: Stonecutter target project task discovery PASS; Fabric, Forge, and NeoForge 1.21.1 standalone builds PASS; exact three-artifact pilot set PASS.
- Generated artifacts: `build/stonecutter/mc1_21_1/libs` contains the three verified pilot JARs; public artifact names are unchanged.
- Repair evidence: the first pilot delegation recursively re-entered the root Stonecutter task. The process tree was stopped and the task was repaired to invoke each standalone platform build directly before checkpointing.
- Follow-up build wiring: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14` explicitly reapplies the repository build script to the Stonecutter root project and makes the Stonecutter verifier depend on the root registry guard. This preserves root release checks without re-entering the platform build graph.
- Compatibility: no runtime hook or artifact packaging behavior changed; this is a parallel build path only.
- Rollback point: parent `8d53b8abd996b6d03a0cf135325144db5748c985`.
- Next mandatory phase: central effective ownership policy and capability deduplication.

## Phase 5 — central capability ownership and feature policy

- Date: 2026-08-11
- Commit SHA: `de3138344c633ca68b450e9688d0adb556c3de83`
- Parent stable checkpoint: `5f3ee749fe080f2f6035d9b5b0adf9a6924d1a3d`
- Status: `FOCUSED_VERIFIED`
- Files changed: `common/src/main/java/com/teenkung/packforge/config/FeaturePolicy.java`, `FeatureFlags.java`, `PackForgeCapabilities.java`, `ReloadFeatureSnapshot.java`, `FeaturePolicyTest.java`
- Architecture decision: keep `FeatureFlags` as the compatibility facade, but move capability/config gating into an immutable `FeaturePolicy`. Reload capture copies the mutable config and reads the artifact-generated capability profile once at the reload boundary.
- Commands: `./gradlew.bat -p platform/fabric -Ppackforge_target=mc1_21_1 test --no-daemon --stacktrace`.
- Results: Fabric 1.21.1 suite PASS; 106 tests completed.
- Compatibility: no feature default or capability identifier changed; reserved atlas-split settings remain disabled and absent capability profiles still fail closed.
- Rollback point: parent `5f3ee74`.
- Next mandatory phase: capability declaration deduplication and generated-profile ownership checks.

## Phase 6 — archive and reload implementation deduplication

- Date: 2026-08-11
- Commit SHA: `723c504ca5ec2012e8535efc15bd96d4fbec8854`
- Parent stable checkpoint: `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`
- Status: `FOCUSED_VERIFIED`
- Files changed: `versions/mc1_21_shared/common/src/main/java/.../FilePackResourcesMixin.java`, `SharedZipFileAccessMixin.java`, `ReloadableResourceManagerMixin.java`, three platform source-set declarations, and the effective-source registry validator
- Architecture decision: make the byte-identical 1.21.1/1.21.4/1.21.8 archive and reload hooks one shared implementation. Keep mixin descriptors version-local because resource processing rejects duplicate paths; keep 1.21.11 version-owned until its full source family is proven compatible.
- Commands: `./gradlew.bat :validateTargetRegistry --no-daemon --stacktrace`; `./gradlew.bat -Ppackforge_target=mc1_21_1 :buildTarget --no-daemon --stacktrace`; same `buildTarget` command for `mc1_21_4` and `mc1_21_8`.
- Results: registry guard PASS; all Fabric, Forge, and NeoForge builds plus artifact verifiers PASS for 1.21.1, 1.21.4, and 1.21.8. Direct inspection of the three 1.21.1 final JARs found exactly one packaged class for each relocated hook.
- Compatibility: hook source text is unchanged; only effective source ownership changed. No broad selector or fallback hook was introduced.
- Rollback point: parent `80a35d7bff2a186bf62a3696dc54dcf2dc639e14`.
- Next mandatory phase: model/sprite scheduling and remaining capability-specific source deduplication.

## Phase 6b — shared 1.21 client bootstrap

- Date: 2026-08-11
- Commit SHA: `e15103a52b52308ed7648cd27e6ff32f4e7ad24d`
- Parent stable checkpoint: `b97fc31fc9a978c99c171904c0036350d8f49012`
- Status: `FOCUSED_VERIFIED`
- Files changed: shared `versions/mc1_21_shared/common/src/client/java/.../PackForgeClient.java` and the exact-one effective-source validator
- Architecture decision: the identical 1.21.1/1.21.4/1.21.8 client reset bootstrap is shared; model parser and model-manager mixins remain version-owned because their Minecraft value types and hooks differ.
- Commands: `./gradlew.bat :validateTargetRegistry --no-daemon --stacktrace`; `./gradlew.bat -Ppackforge_target=mc1_21_1 :buildTarget --no-daemon --stacktrace`; same `buildTarget` command for `mc1_21_4` and `mc1_21_8`.
- Results: registry guard PASS; all Fabric, Forge, and NeoForge builds plus artifact verifiers PASS for all three affected targets.
- Compatibility: reset-hook order and target-specific parser linkage are unchanged; mixin descriptors remain version-local.
- Rollback point: parent `b97fc31fc9a978c99c171904c0036350d8f49012`.
- Next mandatory phase: configuration schema/effective-state unification and Quick Pack ownership.
