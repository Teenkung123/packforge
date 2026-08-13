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

## Phase 13 — first same-binary range promotion

- Date: 2026-08-12
- Commit SHA: `fbd7b3208347230ae17e90f822bde2e100e82992`; parent `0d5dc3ab0820d34fd63d709840696cdbb2335a1d`
- Status: `RANGE_VERIFIED`; Java 21 consolidation remains open.
- Scope: widen the `mc1_20_2` artifact metadata through 1.20.4, add legacy `supported_formats`, make smoke harnesses read the target marker from the final JAR, support interior artifact-version checks, and select the Forge 48/49 reload observer before mixin application.
- Build evidence: `buildMc1_20_2` and artifact verification PASS; Forge selector unit tests PASS; `clean build` PASS in 13m16s with 107 executed tasks; the post-clean focused rebuild reproduced the runtime-tested SHA-256 values.
- Runtime evidence: Fabric, Forge, and NeoForge each passed 1.20.2, 1.20.3, and 1.20.4 with one unchanged loader artifact, two deterministic reloads per cell, semantic/resource evidence, and clean exit.
- Artifact hashes: Fabric `77D690FF0D9956271196CBD96406962C052656AAC8B26FF42AD78B40954F4B1A`; Forge `88F4BB16DD9BEDCEA21100827B7A53794050939F936422F8180B86C451C90547`; NeoForge `24460EEFB3D33F1B6AD5154758A2E829394138ECD65017C8E19F39F5A1D2BC48`.
- Publication evidence: registry validation PASS; generated counts `19/62/35/20` for build/smoke/publish-smoke/publish; exact 20-artifact manifest verification PASS.
- Rollback: `git revert fbd7b3208347230ae17e90f822bde2e100e82992` restores exact-only 1.20.2 metadata and removes the Forge range selector.
- Next mandatory phase: consolidate and same-JAR test 1.20.5-1.21.1, 1.21.2-1.21.4, 1.21.5-1.21.8, and 1.21.9-1.21.11; then rerun the rebuilt 26.x NeoForge artifact.

## Phase 14 — second same-binary range promotion

- Date: 2026-08-12
- Commit SHA: `5ed1480540162547b3de475e0e9a51e7965d968c`; parent `9b060a267e525eda3ce5f47852064fa1e546b079`
- Status: `RANGE_VERIFIED`; three Java 21 consolidation families remain open.
- Scope: widen `mc1_21_1` to Fabric 1.20.5-1.21.1 and Forge/NeoForge 1.20.6-1.21.1; use supported pack formats 32-34; move Forge descriptor/compatibility selection into loader registry data; accept two-part release IDs in Fabric root preparation; harden clean-exit observation after completed reloads; replace Quick Pack version branching with six-capability module handoff.
- Build evidence: `buildMc1_21_1` and artifact verification PASS; Quick Pack policy tests cover old, current, future, malformed, and missing version strings; `clean build` PASS in 8m49s; clean output reproduced all runtime-tested hashes exactly.
- Runtime evidence: Fabric PASS on 1.20.5, 1.20.6, 1.21, and 1.21.1; Forge and NeoForge PASS on 1.20.6, 1.21, and 1.21.1. Every cell used one unchanged loader artifact, completed two deterministic reloads, emitted semantic/resource evidence, and exited cleanly.
- Artifact hashes: Fabric `99D38B658B6D8A19294ADB4D5986BB714016A67C697B91C8FAB01EC7DE5B4E72`; Forge `CA9F0EEAF1B382BA13518C7B5C24149BDA6031C33AA4473A8A08CD510ED78FFA`; NeoForge `969BFADD9B219932FAB7040839CA6ADAB075EC8B0E941CDED829A22C3A41D5AE`.
- Quick Pack evidence: real Quick Pack 1.5.0 SHA-256 `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`; final Fabric range JAR; ten requested reloads; `status=MODULE_HANDOFF`; all six overlap capabilities delegated; clean exit. Version metadata is diagnostic only. Quick Pack 1.4 and older remain best-effort/not guaranteed.
- Publication evidence: registry validation PASS; generated counts `19/62/42/20` for build/smoke/publish-smoke/publish; exactly 20 clean-built JARs accepted by the release-manifest verifier.
- Deviation repaired: the first clean build exposed Java compatibility being incorrectly used as a proxy for descriptor ownership on Forge 1.20.1. Explicit loader `mixinConfigs` preserve each descriptor family and the focused 1.20.1 verifier plus final clean build pass. Fabric 1.21 also exposed a clean-shutdown observation race after all reloads completed; the harness now grants only a bounded 15-second process-exit grace after reload proof.
- Rollback: `git revert 5ed1480540162547b3de475e0e9a51e7965d968c` restores the previous singleton 1.21.1 publication target and version-classified Quick Pack policy.
- Next mandatory phase: consolidate and same-JAR test 1.21.2-1.21.4, 1.21.5-1.21.8, and 1.21.9-1.21.11; then rerun the rebuilt 26.x NeoForge artifact.

## Phase 15 — remaining range families and full exact matrix

- Date: 2026-08-12
- Commit SHA: `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`; parent `9498f45c4b92b97d6ff5f19ca5864488c8a33ab1`
- Status: `FULL_MATRIX_VERIFIED`
- Scope: 1.21.2-1.21.4, 1.21.5-1.21.8, and 1.21.9-1.21.11 range artifacts; exact capability/mixin floors; runtime Minecraft-version bridges; version-independent Quick Pack module handoff; registry validation and seven-row build deduplication.
- Build evidence: aggregate seven-family clean build PASS; all tests and artifact verifiers PASS; exactly 20 final candidates produced.
- Runtime evidence: the resumable production controller records 62/62 unique exact cells PASS, two reloads per cell, semantic/resource evidence, and clean exit. Harness-only repairs isolated Fabric natives by exact profile and normalized Forge/NeoForge controlled shutdown.
- Publication evidence: all 22 release rows and twelve source families promoted after same-JAR proof; generated counts `build=7`, `smoke=62`, `publish-smoke=62`, `publish=20`.
- Rollback: `git revert 051aaaca42bdc980a3260d1a6c6fcd4404f228b7` after reverting the source-consolidation checkpoint.

## Phase 16 — source consolidation and final hash binding

- Date: 2026-08-12
- Commit SHA: `a3402866b217ac159d6a3cec70d3585028f79732`; parent `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`
- Status: `FULLY_VERIFIED`
- Scope: registry-selected canonical shared Java classes, removal of all copied production classes, `reportSourceMetrics` CI gate, isolated Fabric native roots, resumable exact-matrix controller, and final promotion state.
- Source evidence: 16,209 production LOC; 5,426 bridge LOC (33.48%); zero normalized duplicate classes; 100% duplicate-group reduction; 202 production files; 736.77 LOC per exact release.
- Build evidence: clean seven-family build produced exactly 20 artifacts; post-run `reportSourceMetrics validateTargetRegistry verifyExistingArtifacts` PASS; release manifest regenerated with 20 artifacts.
- Hash evidence: 18/20 artifacts remained byte-identical and retain 58-cell proof. The two legacy Forge JARs changed only refmap key order; all mapping values remained identical, and their four exact cells reran 4/4 PASS with two reloads and clean exit.
- Quick Pack evidence: final Fabric hash `AAD7C8126DEFDA7A9FB115675841C4F2210607F722CA4141BC3C23BECED6E71A` plus Quick Pack 1.5.0 hash `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1` PASS with ten reloads, module handoff, and natural clean exit.
- Unrelated state: `.github/ISSUE_TEMPLATE/bug-report.yml` remains modified and unstaged; `.codegraph/` remains untracked and excluded.
- Rollback: `git revert a3402866b217ac159d6a3cec70d3585028f79732`.

## Phase 17 — deterministic compatibility fixtures

- Date: 2026-08-13
- Commit SHA: `458616fb1d3eaa96623ce86dfed41749daf60e38`; parent `d186ab0d1687f7b6cca5f7d11121c77e8028ddca`
- Status: `STRUCTURAL_VERIFIED`
- Scope: generate nine deterministic 1.21.1 compatibility fixture ZIPs plus manifest contracts for normal, high-entry-count, overlay/namespace/duplicate, font-heavy, model-heavy, mipmap-heavy, and malformed-but-ZIP-readable paths.
- Verification: `python scripts/Generate-CompatibilityFixtures.py --self-test` passed; repeated generation kept fixture IDs, bytes, CRCs, and contract manifest identical. Unsupported Minecraft versions reject before generation.
- Limits: no Gradle build, Minecraft launch, final-JAR load, reload/cancellation run, semantic hash, third-party profile, or cross-version fixture proof occurred. These fixtures are inputs for later Phase I/K execution.
- Rollback: `git revert 458616fb1d3eaa96623ce86dfed41749daf60e38` after reverting the later metrics snapshot.

## Phase 18 — source-metrics snapshot refresh

- Date: 2026-08-13
- Commit SHA: `b1161295bd3a41a0349841fd67ff3f0c1634c3bd`; parent `458616fb1d3eaa96623ce86dfed41749daf60e38`
- Status: `FOCUSED_VERIFIED`
- Scope: update checked-in `source-metrics-current.md` and `source-metrics-current.json` from the deterministic source inventory after current structural work.
- Verification: `./gradlew.bat reportSourceMetrics --no-daemon --console=plain` PASS in 36 seconds: 210 production Java files, 16,323 nonblank LOC, zero exact/normalized duplicate groups, 15 target-key/version conditional lines, three renderer bodies, two adapters, and 20 publication artifacts.
- Limits: no compile, package, artifact verification, third-party profile, or Minecraft runtime was run. Metric refresh does not revalidate older final-artifact evidence.
- Rollback: `git revert b1161295bd3a41a0349841fd67ff3f0c1634c3bd`.

## Phase 19 — compatibility profile materialization transport

- Date: 2026-08-13
- Commit SHA: `6257c35d50d04e4d874b1175d4808af52fbacdb1`; parent `f15e8575536706570b415a2307dd0d1d76415de3`
- Status: `STRUCTURAL_VERIFIED`; all seven metadata-`AVAILABLE` profiles remain `UNTESTED`.
- Scope: add schema-2 compatibility-profile materialization to `Run-Exact-ProductionMatrix.ps1`, `Smoke-Fabric-Production.ps1`, `Smoke-Forge-Production.ps1`, `Smoke-NeoForge-Production.ps1`, and `Test-ExactProductionMatrixProfiles.ps1`; transport the catalog/profile identity, pinned mod paths, fixture metadata, expected markers, and expected evidence path through the loader wrappers with fail-closed checks.
- Verification: the offline focused materialization self-test passed in 15.7 seconds; AST validation passed for all five scripts; independent review passed.
- Limits: no network request, dependency download, Gradle task, Minecraft launch, or compatibility runtime was executed. The seven metadata-`AVAILABLE` profiles are still not PASS results. ImmediatelyFast-only execution fails closed because no dedicated path marker is yet available, and any nonempty configuration override fails closed because override transport is not implemented.
- Rollback: revert later documentation/profile dependants first, then `git revert 6257c35d50d04e4d874b1175d4808af52fbacdb1` to restore selector-only profile handling.

## Phase 20 — authoritative direct Stonecutter contract

- Date: 2026-08-13
- Commit SHA: `3c01a50`; parent `b7e60da`
- Scope: 53 registry-derived loader distributions authoritative direct cells; direct-only public graph; JOptSimple registry metadata; mandatory `validateStonecutterDirectContract`.
- Verification: direct contract AST/self-test baseline plus 10 mutations PASS; direct task PASS; `validateTargetRegistry` dry-run PASS in 31 seconds without compilation; independent review PASS.
- Status: `PHASE_E_STRUCTURAL_VERIFIED_PARTIAL`. Nested Gradle remains parity rollback oracle. No all-53 build/structural/package parity, Java 17/21/25 proof, remap/JarJar proof, runtime, or true Stonecutter-preprocessed source proof.
- Rollback: revert later dependants, then `git revert 3c01a50`.

## Phase 21 — current-evidence documentation reconciliation

- Date: 2026-08-13
- Commit SHA: `d833747749ed665595231b6e72acc22f469b7e1a`; parent `e6246082eda901dc7d202a067bcec432843209bd`
- Status: `PARTIAL_NOT_RELEASE_READY`
- Scope: reconcile the README, compatibility/version matrices, current-state audit, final validation, implementation report, library decisions, Quick Pack evidence, and source inventory with the current implementation and evidence boundaries.
- Verification: focused documentation consistency inspection and independent documentation review passed.
- Limits: no build, compile, package, final-JAR verification, compatibility-profile execution, or Minecraft runtime was run. Historical 20-artifact hashes and runtime proofs do not validate current direct-build bytes; release readiness remains open.
- Rollback: revert `07b087fa1e3c862f086a25e29bab68eec37477d2` first, then `git revert d833747749ed665595231b6e72acc22f469b7e1a`.

## Phase 22 — authoritative static implementation-contract gate

- Date: 2026-08-13
- Commit SHA: `07b087fa1e3c862f086a25e29bab68eec37477d2`; parent `d833747749ed665595231b6e72acc22f469b7e1a`
- Status: `STATIC_CONTRACTS_VERIFIED`; not release-ready evidence.
- Scope: add root tasks for the configuration-screen, compatibility-profile catalog, default-off candidate catalog, and deterministic fixture contracts; aggregate them under `validateImplementationContracts`; attach that gate to registry validation and CI; require `--verify-existing` after release-manifest generation in publication CI.
- Verification: `validateImplementationContracts` passed 4/4 contracts in 36.9 seconds without compilation; the focused Python release-manifest test passed; independent review passed. Workflow YAML received focused static inspection because a YAML parser was unavailable.
- Limits: no compile, package build, final-JAR manifest verification, compatibility-profile execution, or Minecraft runtime was run.
- Rollback: `git revert 07b087fa1e3c862f086a25e29bab68eec37477d2`.

## Phase 23 — expanded exact compatibility-profile pins

- Date: 2026-08-13
- Commit SHA: `a80a83e77ed8190ad4de1e0c07665264af48f1c9`; parent `293e7aa`
- Status: `METADATA_VERIFIED_RUNTIME_UNTESTED`; 21 `AVAILABLE`, 12 `PENDING_METADATA`, three `UNAVAILABLE`, zero PASS.
- Scope: pin exact loader-family artifacts, dependencies, runtime IDs, versions, URLs, hashes, and loader floors. Move Fabric Quick Pack recipes to pending because Loader `0.15.11` cannot satisfy `>=0.17.3`.
- Verification: catalog normal/self-test PASS for 36 recipes and 19 rejected mutations; 20 unique new pins independently matched SHA-256 and embedded metadata. Verification downloads were not retained.
- Limits: no Minecraft launch, compatibility runtime, or PASS result. `PARTIAL_NOT_RELEASE_READY` remains.
- Rollback: revert the documentation checkpoint, then `git revert a80a83e77ed8190ad4de1e0c07665264af48f1c9`.

## Phase 24 — schema-2 compatibility-profile configuration contract

- Date: 2026-08-13
- Commit SHA: `eb14498`; parent `a15c4e0`
- Status: `STRUCTURAL_VERIFIED_RUNTIME_UNTESTED`; no default promotion.
- Scope: add shared schema-2 configuration handling for the exact-production materializer and Fabric/Forge/NeoForge smoke wrappers. The fixed smoke baseline serializes all 50 `PackForgeConfig.Cfg` v12 fields. Only five safe boolean profile overrides are accepted: `loaderZipPoolEnabled`, `fontBitmapProviderCacheEnabled`, `atlasDecodeBatchingEnabled`, `atlasRetryEnabled`, and `startupExecutorTuningEnabled`.
- Verification: AST validation of seven scripts passed; catalog normal validation accepted 36/36 frozen recipes; self-test rejected 22 catalog mutations plus an injected Java default drift; offline schema-2 transport passed through Fabric, Forge, and NeoForge in 22.1 seconds; independent review passed. The Java/helper contract requires all 50 initializer values, except the two documented smoke-observability deltas, and verifies JSON/SHA-256 stability.
- Limits: no Gradle task, network request, dependency download, Minecraft launch, compatibility runtime profile, reload, artifact verification, or release proof was run. All profile results remain unexecuted; no production default or default-off candidate was promoted.
- Rollback: newest-first—revert later profile/documentation dependants, then `git revert eb14498` to remove full configuration transport and restore prior fail-closed nonempty override handling.
