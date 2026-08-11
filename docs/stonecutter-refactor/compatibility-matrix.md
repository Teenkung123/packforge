# Compatibility baseline

Outcome labels follow the assignment: `FULL_OPTIMIZED_PATH`, `HOOK_PRESERVING_COALESCED_PATH`, `SAFE_ORIGINAL_PATH`, `EXTERNALLY_OWNED_PATH`, `UNAVAILABLE`, `UNTESTED`, and `FAILED`.

| Profile | Result | Evidence |
|---|---|---|
| Fabric 1.21.1 focused unit suite | PASS | Gradle `test`, 40s |
| Forge 1.20.1 focused unit suite | PASS | Gradle `test`, 48s |
| NeoForge 26.1-26.2 focused unit suite | PASS | Gradle `test`, 40s |
| PackIndex deterministic fixture | PASS | equal baseline/indexed SHA-256; 99.66% indexed lookup improvement |
| Fabric 26.x packaged startup/reload | `UNTESTED` full harness / partial runtime | startup and reload-complete log; F3+T window activation failed |
| Forge 1.20.1 source-mode startup/reload, Forge 47.4.22 | `UNTESTED` final-JAR / partial runtime | startup and reload-complete log; F3+T window activation failed |
| Forge 1.20.1 final remapped/JarJar artifact | `UNTESTED` | dedicated final-JAR harness not executed |
| Quick Pack 1.5.x | `UNTESTED` | no runtime dependency/profile yet |
| Sodium/Iris/ImmediatelyFast/ModernFix/FerriteCore/etc. | `UNTESTED` | no exact third-party profile run |
| Current six-anchor × declared-loader production matrix | PASS | forced `buildAllSupported`; 17 artifacts and per-target artifact verifiers passed in 5m31s |
| Aggregate current-artifact verification | PASS | `verifyAllArtifacts`; exact 17-name set passed in 5m18s |
| Fabric 1.20.2 focused production build | `UNTESTED` runtime | `platform/fabric ... clean build` PASS; no startup/reload harness |
| Forge 1.20.2 focused production build | `UNTESTED` runtime | `platform/forge ... clean build` PASS; no final-JAR startup/reload harness |
| NeoForge 1.20.2 focused build | `FAILED` toolchain | ModDev 2.0.141 lacks the 20.2 capability; NeoGradle 7.0.116 fails under Gradle 9.5.1 before compile |
| Fabric 1.20.3 focused production build | `UNTESTED` runtime | exact `buildMc1_20_3` Fabric build PASS; final JAR structural evidence captured, no startup/reload harness |
| Forge 1.20.3 focused production build | `UNTESTED` runtime | exact `buildMc1_20_3` Forge build PASS; final JAR structural evidence captured, no final-JAR startup/reload harness |
| NeoForge 1.20.3 focused build | `FAILED` toolchain | NeoForge 20.3.8-beta has no `neoforge-moddev-bundle` capability for ModDev 2.0.141; failure occurs before source compilation |
| Fabric/Forge/NeoForge 1.20.4 final artifacts | `UNTESTED` runtime | exact `verifyMc1_20_4Artifacts` PASS; all three JARs package the archive bridge, SpriteLoader hook, descriptor, and beta metadata; no startup/reload harness |
| Fabric 1.20.5 final artifact | `UNTESTED` runtime | exact `buildMc1_20_5` and `verifyMc1_20_5Artifacts` PASS; final JAR contains the Java21 family client mixin, descriptor, `fabric.mod.json`, and access widener; no startup/reload harness |
| Fabric/Forge/NeoForge 1.20.6 final artifacts | `UNTESTED` runtime | exact `buildMc1_20_6` and `verifyMc1_20_6Artifacts` PASS; all three JARs contain the Java21 family archive/reload/client hooks and loader metadata; no startup/reload harness |
| Fabric/Forge/NeoForge 1.21 final artifacts | `UNTESTED` runtime | exact `buildMc1_21` and `verifyMc1_21Artifacts` PASS; all three final JARs contain the shared archive/reload/client hooks, `PackSelectionScreenMixin`, and loader metadata; no startup/reload harness |
| Fabric/NeoForge 1.21.2 final artifacts | `UNTESTED` runtime | exact `buildMc1_21_2` and `verifyMc1_21_2Artifacts` PASS; official Forge 1.21.2 is unavailable, so the exact loader matrix is Fabric + NeoForge; no startup/reload harness |
| Fabric/Forge/NeoForge 1.21.3 final artifacts | `UNTESTED` runtime | exact `buildMc1_21_3` and `verifyMc1_21_3Artifacts` PASS; all three final JARs contain the shared archive/reload/client hooks, `PackSelectionScreenMixin`, and loader metadata; no startup/reload harness |
| Exact 22-release × official-loader matrix | `UNTESTED` | registry has all 22 rows; only current anchors are published, and 1.20.2 remains planned |

Build and package success is not treated as runtime acceptance.
