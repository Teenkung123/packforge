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
| Exact 22-release × official-loader matrix | `UNTESTED` | registry still has six anchors only |

Build and package success is not treated as runtime acceptance.
