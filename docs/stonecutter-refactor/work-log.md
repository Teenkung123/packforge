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

## Phase 8 — Quick Pack compatibility and unified effective state

- Date: 2026-08-11
- Checkpoints: `35c47274fa5ccaf7b340327a164c0ebd5effaea7`, then `2759979bb93afc9ec62703a2ca08d429393e870b`
- Status: `FOCUSED_VERIFIED`
- Files changed: common Quick Pack detector/policy, loader metadata bridges, Fabric/Forge/NeoForge early mixin plugins, ownership tests, and current mixin descriptors
- Architecture decision: use public mod ID/version metadata only; fail closed for unknown majors and suppress overlap-bearing hooks before Mixin application while preserving PackForge-only paths
- Commands: focused loader tests/builds and final artifact scans; all executed checks passed
- Compatibility: unit and structural evidence PASS; Quick Pack combined runtime remains `UNTESTED`
- Rollback point: `b532d3dfe2d72461605db1f1467f95a0044d2da1`

## Phase 7 — Unified configuration UI

- Date: 2026-08-11
- Commit SHA: `e460da0e39f744544b4daf2ceb9844c87282e062`
- Parent stable checkpoint: `2759979bb93afc9ec62703a2ca08d429393e870b`
- Status: `FOCUSED_VERIFIED`
- Files changed: `PackForgeConfigScreenModel`, language entries, and native screen adapters for all six current anchors
- Architecture decision: one central schema owns category order, defaults, validation, configured/effective values, ownership, and warnings; native screens only bridge API signatures
- Results: current config tests and target builds passed; 1.20.2 UI signature bridge later passed focused Fabric/Forge builds
- Rollback point: parent `2759979bb93afc9ec62703a2ca08d429393e870b`

## Phase 9 — First exact-release feasibility cell

- Date: 2026-08-11
- Checkpoints: `cd87525fbe763a313a607d2d15183b02657d0ecd`, `e3f6f008965fd127795b2d4abe8efaff41f407af`, `0dc06bc2a6e684e7efd3d1df5f3f0f1aba6a7db0`, `862022c40249155f1d43fb55d97f3c4cb132c136`
- Status: `FOCUSED_VERIFIED`; exact release support not promoted
- Files changed: registry schema/family ledger, Stonecutter settings, loader source selection, 1.20.2 descriptor/access widener, constructor bridge, and published-target aggregation
- Architecture decision: keep exact rows authoritative but prevent `planned` cells from entering the published artifact set. Share operation-level archive code and isolate only constructor signature differences in thin bridge mixins.
- Commands/results: `validateTargetRegistry` PASS; Fabric 1.20.2 clean production build PASS; Forge 1.20.2 clean production build PASS; Fabric/Forge/NeoForge 1.21.1 clean production builds PASS; Fabric 1.21.11 clean production build PASS. NeoForge 1.20.2 attempt 1 failed on ModDev capability resolution; attempt 2 failed with Gradle/Groovy `AbstractMethodError` before compile.
- Generated artifacts: 1.20.2 Fabric/Forge focused JARs only; they are not published. Final current aggregation remains 17 JARs.
- Compatibility: 1.20.2 runtime/reload and exact interior release cells remain `UNTESTED`; NeoForge 1.20.2 is blocked by toolchain.
- Rollback point: `862022c40249155f1d43fb55d97f3c4cb132c136`

## Phase 12 — Current-matrix evidence

- Date: 2026-08-11
- Status: `FOCUSED_VERIFIED`, not full exact-release acceptance
- Commands: `./gradlew.bat buildAllSupported --rerun-tasks --no-daemon --stacktrace` PASS in 5m31s; `./gradlew.bat verifyAllArtifacts --no-daemon --stacktrace` PASS in 5m18s
- Results: all six published targets, all 17 published loader artifacts, and final structural artifact checks passed. The 22-row registry remains honest about planned/unexecuted cells.
- Next mandatory phase: exact 1.20.3 onward cells, production startup/reload evidence, final-JAR smoke, CI/publication generation, candidate gates, and final full-matrix checkpoint.

## Phase 9 — exact 1.20.3 and 1.20.4 source-family cells

- Date: 2026-08-11
- Commit SHA: `948e437`
- Parent stable checkpoint: `42838f2`
- Status: `FOCUSED_VERIFIED`; exact release support not promoted
- Files changed: registry exact target cells, Stonecutter nodes, Fabric/Forge/NeoForge source selection, the shared-family constructor descriptor/access widener, and the NeoForge 20.4 config-screen bridge
- Architecture decision: keep one `mc1_20_3_4` operation-level source family, but split 1.20.3 and 1.20.4 into exact target metadata because the legacy Fabric production namespace requires exact compatibility. The family bridge is shared by source root; only loader/API wiring is target-specific.
- Commands: `./gradlew.bat validateTargetRegistry --no-daemon --stacktrace`; `./gradlew.bat buildMc1_20_4 --rerun-tasks --no-daemon --stacktrace`; `./gradlew.bat verifyMc1_20_4Artifacts --no-daemon --stacktrace`; `./gradlew.bat buildMc1_20_3 --rerun-tasks --no-daemon --stacktrace`.
- Results: registry validation PASS; 1.20.4 Fabric, Forge, and NeoForge production builds PASS and `verifyMc1_20_4Artifacts` PASS. 1.20.3 Fabric and Forge production builds PASS. NeoForge 1.20.3 fails before compile because official `20.3.8-beta` does not expose the `neoforge-moddev-bundle` capability required by ModDev 2.0.141.
- Generated artifacts and SHA-256: `packforge-fabric-1.3.4-beta.4-mc1.20.3.jar` `4BC0004A1DE3168952019A72AB2F43093162E00E8BA95DA289A4F04C0F29B9CB`; `packforge-forge-1.3.4-beta.4-mc1.20.3.jar` `B2DE9FC92EF2C7A72C5093DABFC66C93A8B13C566ABD03781DD2B45B3BF224D8`; `packforge-fabric-1.3.4-beta.4-mc1.20.4.jar` `23BDC3DBDC2578F060DAE6C2FA68194B9426BDB48F064E2CBA954D561FED78BE`; `packforge-forge-1.3.4-beta.4-mc1.20.4.jar` `2DF33FFEDE575A9A2C088F6B5DD7D3EDB7FBECA86012395C3EF3C59AC92128F8`; `packforge-neoforge-1.3.4-beta.4-mc1.20.4.jar` `6467CE01A4E3D8D5E0DFDB8FE8580764895EAC69B1923EFA9B656089F16B3E54`.
- Compatibility: final JAR inspection confirms the archive constructor bridge, operation-level archive hook, SpriteLoader hook, exact family descriptor, and beta metadata are packaged where available. All 1.20.3/1.20.4 startup, deterministic reload, semantic-hash, Quick Pack, and clean-exit cells remain `UNTESTED`; no cell enters published aggregation.
- Rollback point: `948e437`.
- Next mandatory phase: exact 1.20.5 and 1.20.6 Java-21 source-family cells, then exact 1.21 gaps.

## Phase 10 — exact 1.20.5 and 1.20.6 Java21 source-family cells

- Date: 2026-08-11
- Commit SHA: `21524f3`
- Parent stable checkpoint: `1569eff`
- Status: `FOCUSED_VERIFIED`; exact release support not promoted
- Files changed: exact registry target cells, Stonecutter nodes, Java21 family client/resource/test source selection, and the family Fabric descriptor/access widener
- Architecture decision: reuse the proven 1.21 shared archive/reload/client hooks and `mc1_21_1` adapter contracts; isolate only the 1.20.x `ResourceLocation` client constructor and Fabric font-test helper in family-local sources. Keep 1.20.5 Fabric-only because official loader metadata exposes no Forge or NeoForge cell for that release; keep all three declared loaders for 1.20.6.
- Commands/results: `./gradlew.bat validateTargetRegistry --no-daemon --stacktrace` PASS; `./gradlew.bat buildMc1_20_5 --rerun-tasks --no-daemon --console plain --stacktrace` PASS, including `verifyMc1_20_5Artifacts`; `./gradlew.bat buildMc1_20_6 --rerun-tasks --no-daemon --console plain --stacktrace` PASS, including `verifyMc1_20_6Artifacts`.
- Generated artifacts and SHA-256: `packforge-fabric-1.3.4-beta.5-mc1.20.5.jar` `B957D0ABC9453D1965E552EC84BC77A02EBB6186D10B85F3E9E486098F25E774`; `packforge-fabric-1.3.4-beta.5-mc1.20.6.jar` `B14EEE15EB261B4FB708E038E75430E8E0028712ADF429DB776D4D6188045FF8`; `packforge-forge-1.3.4-beta.5-mc1.20.6.jar` `8A98DE2A14DBEC361667818E2DC57C923E9E8F80D5937BAC7B1960DC1E560070`; `packforge-neoforge-1.3.4-beta.5-mc1.20.6.jar` `1B3501C27B94F2872007D93CBC69619A7CCCAFB750798FA3D7295B41B2A02C4D`.
- Compatibility: final JAR inspection confirms the family client mixin, archive/reload hooks, exact family descriptor, access widener where applicable, and loader metadata. All 1.20.5/1.20.6 production startup, deterministic reload, semantic-hash, Quick Pack, and clean-exit cells remain `UNTESTED`; no cell enters published aggregation.
- Rollback point: `21524f3`.
- Next mandatory phase: exact 1.21 release gaps, then final-artifact runtime evidence for every exact applicable cell.

## Phase 11 — exact 1.21, 1.21.2, and 1.21.3 source-family cells

- Date: 2026-08-11
- Commit SHA: `71de8fe`
- Parent stable checkpoint: `3c52d21`
- Status: `FOCUSED_VERIFIED`; exact release support not promoted
- Files changed: exact 1.21/1.21.2/1.21.3 registry cells, exact loader availability and coordinates, Stonecutter nodes, and shared 1.21 source selection
- Architecture decision: keep exact target metadata separate while reusing the proven `mc1_21_1` adapter for 1.21 and the `mc1_21_4` adapter for 1.21.2/1.21.3. Preserve the official loader matrix: Fabric/Forge/NeoForge for 1.21 and 1.21.3; Fabric/NeoForge for 1.21.2 because no official Forge 1.21.2 line is available.
- Coordinates and pack metadata: 1.21 uses Fabric Loader `0.19.3`, Fabric API `0.102.0+1.21`, Forge `1.21-51.0.17`, NeoForge `21.0.167`, pack format `34`; 1.21.2 uses Fabric API `0.106.1+1.21.2`, Mod Menu `12.0.1`, NeoForge `21.2.1-beta`, pack format `42`; 1.21.3 uses Fabric API `0.114.1+1.21.3`, Forge `1.21.3-53.1.12`, NeoForge `21.3.97`, pack format `42`.
- Commands/results: `./gradlew.bat validateTargetRegistry --no-daemon --console plain --stacktrace` PASS; `buildMc1_21` plus `verifyMc1_21Artifacts` PASS; `buildMc1_21_2` plus `verifyMc1_21_2Artifacts` PASS; `buildMc1_21_3` plus `verifyMc1_21_3Artifacts` PASS. Forge 1.21 required one bounded retry after Mavenizer cache hydration; NeoForge 1.21 required one bounded retry after a transient missing Gradle test-results file. The final aggregate commands passed.
- Generated artifacts and SHA-256: `packforge-fabric-1.3.4-beta.6-mc1.21.jar` `2DA454952BE45379071001581159BECB8A2B4049FF89A259F10ED1CF53E6347F`; `packforge-forge-1.3.4-beta.6-mc1.21.jar` `731D50DD7E3B2FE1218DE0F6C9484700EB23A185ADB961450AC3DB9438077869`; `packforge-neoforge-1.3.4-beta.6-mc1.21.jar` `A8CBE85069B2BCA0DCF003D1DC642AD77CDEDA190ADBA2577FC9478EFB424733`; `packforge-fabric-1.3.4-beta.6-mc1.21.2.jar` `249FF0B3AF2268CAEAB0E5AFACC1A29CE7CF7C5CAB2AA0EBB3821869EF6F7C22`; `packforge-neoforge-1.3.4-beta.6-mc1.21.2.jar` `EC3473C0F629858657F58030AC48DC3B78B24A772540D2371137E53430C99001`; `packforge-fabric-1.3.4-beta.6-mc1.21.3.jar` `44E479D761B045732F1CFF65510E10A176A94DACDF9638C3AE30ED360C44E072`; `packforge-forge-1.3.4-beta.6-mc1.21.3.jar` `F2C7C409196B0C5DC180C81082FBACD0D3DCC81D3D27E5E7DF147EB1B2B26BD2`; `packforge-neoforge-1.3.4-beta.6-mc1.21.3.jar` `F954036BC8D4CFCCF46B15F034CB2E0C971C2418816C94438BB0F550A3AD642D`.
- Structural evidence: all eight final JARs contain `PackForgeClient.class`, `PackSelectionScreenMixin.class`, `FilePackResourcesMixin.class`, `SharedZipFileAccessMixin.class`, and `ReloadableResourceManagerMixin.class`; Fabric JARs contain `fabric.mod.json` and `packforge.accesswidener`, Forge JARs contain `META-INF/mods.toml`, and NeoForge JARs contain `META-INF/neoforge.mods.toml`.
- Compatibility: all 1.21/1.21.2/1.21.3 production startup, deterministic reload, semantic-hash, Quick Pack, and clean-exit cells remain `UNTESTED`; no cell enters published aggregation.
- Rollback point: `71de8fe`.
- Next mandatory phase: exact 1.21.5/1.21.6/1.21.7 cells, then 1.21.9/1.21.10 and exact hotfix evidence for 26.1.1/26.1.2.

## Phase 12 — exact matrix wiring and toolchain repair

- Date: 2026-08-12
- Commit SHA: `b71e281dc7f4318c872982d6dcdf9e5e74508116`; parent `a17d2ca6d5f0926746b9b2d721bec9f572b67db5`
- Status: `FOCUSED_VERIFIED`; final artifact-range publication remains open.
- Files changed: registry-derived CI/publication generators and workflows, Mojang manifest guard, production smoke wrappers, Forge Java17 mixin descriptors/bridge, legacy NeoForge wrapper and descriptor, modern NeoForge JarJar packaging, and current evidence docs.
- Commands/results: `scripts/Verify-Mojang-ReleaseSequence.ps1` PASS with 22 IDs and SHA-256 `380769b566afa9e768c82e1337fa3af3052aea47c7a9fe09d2c5a96edcef2e6c`; `gradlew.bat validateTargetRegistry printResolvedMatrix --no-daemon --console plain --stacktrace` PASS; matrix counts `build=19`, `smoke=62`, `publish-smoke=26`, `publish=17`; `gradlew.bat build --no-daemon --console plain --stacktrace` PASS in 4m22s (`87 actionable tasks: 24 executed, 63 up-to-date`); release manifest generation PASS with 17 artifacts.
- Artifact evidence: current 17 public hashes are in `artifact-consolidation.md`; the current modern NeoForge 26.x all-in-one artifact contains MixinExtras exactly once and passes `verifyMc26_1To26_2Artifacts`.
- Compatibility evidence: recorded exact final-artifact runtime evidence covers all 62 officially available loader cells; Quick Pack 1.5.0 Fabric passes ten reloads and clean exit; unrelated optimization/render-mod pairwise profiles remain `UNTESTED`.
- Architecture decision: keep exact release rows and exact build/smoke matrix registry-driven, but do not promote pre-26 rows until one final binary passes every exact release declared by its publication range. This preserves metadata honesty while range proof is unfinished.
- Deviation/blocker: 13 non-anchor pre-26 release rows remain `planned-verified`; current rebuilt modern NeoForge 26.x production smoke has not yet been rerun after the JarJar packaging repair.
- Rollback point: `git revert b71e281dc7f4318c872982d6dcdf9e5e74508116`.
- Next mandatory phase: range-proof/publication consolidation, current modern NeoForge 26.x production smoke, and final full-matrix evidence.
