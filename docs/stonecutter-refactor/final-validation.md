# Final validation

Date: 2026-08-12

Implementation checkpoint: `a3402866b217ac159d6a3cec70d3585028f79732`

Parent checkpoint: `051aaaca42bdc980a3260d1a6c6fcd4404f228b7`

## Outcome

The Stonecutter/Quick Pack refactor implementation is complete. The schema-v2 registry contains every stable Minecraft release from 1.20.1 through 26.2, CI derives its matrices from that registry, and the final distribution contains 20 artifacts. All 62 officially available release/loader cells have final-artifact startup, two-reload, semantic/resource, and clean-exit evidence. Pre-26 artifacts remain beta; 26.x artifacts remain stable.

Quick Pack compatibility is module-based rather than version-locked. Presence of `quick-pack` delegates only the six overlapping modules. Quick Pack 1.5.0 passed the final Fabric artifact with ten reloads and natural clean exit. Quick Pack 1.4 and older are best-effort/not guaranteed; future or unreadable versions use the same conservative module handoff.

## Final commands and evidence

| Check | Result |
|---|---|
| `gradlew.bat reportSourceMetrics validateTargetRegistry --no-daemon --console=plain --stacktrace` | PASS; metrics and registry gate pass. |
| `gradlew.bat clean buildMc1_20_1 buildMc1_20_2 buildMc1_21_1 buildMc1_21_4 buildMc1_21_8 buildMc1_21_11 buildMc26_1To26_2 verifyExistingArtifacts --no-daemon --console=plain --stacktrace` | The command host detached at its 15-minute limit; the same Gradle process continued and exited after producing all 20 artifacts. All target tests/builds completed, and the explicit post-run verifier below passed. This is not recorded as a command-level PASS because the detached host did not return Gradle's final line. |
| `gradlew.bat reportSourceMetrics validateTargetRegistry verifyExistingArtifacts --no-daemon --console=plain` | PASS in 11 seconds against the clean-built 20-artifact set. |
| `scripts/Run-Exact-ProductionMatrix.ps1` full matrix | Latest pre-consolidation record: 62 unique cells, 62 PASS, 0 FAIL, two reloads per cell. |
| Final hash continuity | 18 of 20 rebuilt artifacts are byte-identical to the 62-cell-tested set and cover 58 cells. |
| Final affected-cell rerun | 1.20.1, 1.20.2, 1.20.3, and 1.20.4 Forge: 4/4 PASS, two reloads, clean exit, final hashes. Evidence: `build/production-matrix/final-source-consolidation/summary.json`. |
| Final Quick Pack profile | Minecraft 1.21.1, Fabric Loader 0.19.3, Quick Pack 1.5.0: PASS, ten reloads, `MODULE_HANDOFF`, natural clean exit, PackForge SHA-256 `AAD7C812...`. |
| `python scripts/Generate-CiMatrix.py <mode>` | `build=7`, `smoke=62`, `publish-smoke=62`, `publish=20`. |
| `python scripts/Generate-ReleaseManifest.py --artifacts-dir build/libs --output-dir build/libs/release` | PASS; 20 artifacts in `manifest.json` and `release-table.md`. |

The two changed legacy Forge archives retained 209/209 entries. All entries except `packforge.refmap.json` were byte-identical; canonical comparison found zero changed refmap mappings (56 values for 1.20.1, 58 for 1.20.2-1.20.4). The difference was JSON key order. The four exact cells were still rerun so final proof is bound to the new archive hashes.

## Source metrics

Production Java excludes tests, docs, generated Stonecutter output, build output, and third-party code.

| Area | Baseline files | Final files | Baseline LOC | Final LOC | LOC delta |
|---|---:|---:|---:|---:|---:|
| `common/src` | 54 | 60 | 5,400 | 5,929 | +529 |
| `platform/fabric/src` | 4 | 6 | 70 | 165 | +95 |
| `platform/forge/src` | 2 | 5 | 63 | 243 | +180 |
| `platform/neoforge/src` | 2 | 5 | 61 | 222 | +161 |
| `versions` | 150 | 126 | 11,243 | 9,650 | -1,593 |
| Total | 212 | 202 | 16,837 | 16,209 | -628 (-3.73%) |

`reportSourceMetrics` reports:

```text
productionFiles=202
productionLoc=16209
bridgeFiles=99
bridgeLoc=5426
bridgePercent=33.48
normalizedDuplicateBlocks=0
normalizedDuplicateFiles=0
normalizedDuplicateSurplusLoc=0
baselineNormalizedDuplicateBlocks=20
normalizedDuplicateBlockReductionPercent=100.00
generatedLocExcluded=0
supportedExactTargetCount=22
registeredBuildTargetCount=19
productionLocPerSupportedTarget=736.77
```

The raw total reduction is smaller than 25% because the final source supports 22 exact releases instead of six baseline anchors, adds registry-driven capability floors, and supplies explicit loader/version bridges for Java 17, 21, and 25. The exact per-area evidence is: version adapters fell 14.17%; shared common grew 9.80%; loader platforms grew by 436 LOC; bridge code remains a minority at 33.48%. LOC per supported exact release fell from 2,806.17 to 736.77, a 73.74% reduction. No entire comment/whitespace-normalized production class remains copied across version families. The Gradle gate fails if one is reintroduced.

## Final artifact hashes

| Loader | Minecraft range | SHA-256 |
|---|---|---|
| Fabric | 1.20.1 | `3dafe7d9e341e15a3849dc95728fe88cb9ef40751ce1c9b0679c5024b341b864` |
| Forge | 1.20.1 | `86415ce66b254b35a8eec52daa78925dd9c1b03d1b9b54b2791243f6ed1b59b4` |
| Fabric | 1.20.2-1.20.4 | `44a6811603abd81bb02e6b1604a24630f44c02cd1af313d9a4540846f6c8bc14` |
| Forge | 1.20.2-1.20.4 | `2d3b96eef1eb8b10219880ac66e038ffa2e5500b3fa19da7f3583eb6b9d86076` |
| NeoForge | 1.20.2-1.20.4 | `526bd7f7627808ad76f6497f24fbacd600d8cdd5305013e49712588bd939442c` |
| Fabric | 1.20.5-1.21.1 | `aad7c8126defda7a9fb115675841c4f2210607f722ca4141bc3c23beced6e71a` |
| Forge | 1.20.6-1.21.1 | `5c9abac3b8f1d8bf4fceace3d9a8d7e4509bb34d8169ca305fb18ef50928f183` |
| NeoForge | 1.20.6-1.21.1 | `62db915a9a6fc73d7483acc3dc968557d35d5fe714a5a87eb5c3628d3b845e43` |
| Fabric | 1.21.2-1.21.4 | `40b2d8eaba0ac2ac7e5e61c7cfbc7747634b8054d243efd202bcc7204a220870` |
| Forge | 1.21.3-1.21.4 | `5299b4316c52a2aa3a38e7bf44db45ad60b31618b8d9f3d11bb9dabd511c2b38` |
| NeoForge | 1.21.2-1.21.4 | `f5e8bce85b283b7615d259ce241e3a9bded9bc4af6f6f0ff5b97e40a4e8b72fc` |
| Fabric | 1.21.5-1.21.8 | `6792e92a3f72492a04410a7cdc5e33f19820e218fab5efdc9034adb420ff3baa` |
| Forge | 1.21.5-1.21.8 | `31fa446b98b7efaa08d406dbec4f0ced74d56bd11eaf0c247f41402e982b323a` |
| NeoForge | 1.21.5-1.21.8 | `65f18818a39a86faa43cefacbfede479160df847c543de8f82e7a8456efad15d` |
| Fabric | 1.21.9-1.21.11 | `e67a315958cc9044ec3137ad8f1e26808b2ef7754f30477372a2dec576b126b4` |
| Forge | 1.21.9-1.21.11 | `bcc7ccd82fad1110ff4ab655b3bfc1ba6e6aa226a144468474b2b628a8a83670` |
| NeoForge | 1.21.9-1.21.11 | `0240ad0f84e5e21d68bd1ae0e7ef0f73496b60d7b2e9e243cf779a644e280814` |
| Fabric | 26.1-26.2 | `312ec96bb988c319d706110a801680b7af7a51defbff60a62072a57d9f865e95` |
| Forge | 26.1-26.2 | `447d6c727406bde34d81a3b0df17b554f43f924e28cc805b13abfd8df08819fc` |
| NeoForge | 26.1-26.2 | `7e946799ea1b3c81b068acb9048a93f8cfa6907316c08ef886a87b32d321aba6` |

The 20-artifact set is 69.70% below the naïve 66-artifact Minecraft/loader Cartesian product and 67.74% below the 62 officially applicable exact cells. It stays below the evidence-backed fallback ceiling of 21. Remaining splits correspond to official loader availability, Java/API boundaries, or independently proven Minecraft hook families.

## Defaults, limits, and stopping state

All optional performance candidates retain their recorded `SAFE_KEEP_DEFAULT_OFF` verdict; no candidate bypassed its performance gate. Existing explicit configuration remains preserved. There is no required runtime dependency; Mod Menu remains optional and Cloth Config/YACL are not required.

Unrelated renderer/optimization-mod pairwise profiles remain `UNTESTED` and are not claimed compatible. This does not weaken the required Quick Pack profile or the 62-cell base matrix. The two integrated repair cycles were: exact-matrix harness/toolchain repair, then final source-consolidation/hash reproof. No release publishing or unrelated repository work was performed.

The latest fully verified implementation rollback point is `a3402866b217ac159d6a3cec70d3585028f79732`. The unrelated `.github/ISSUE_TEMPLATE/bug-report.yml` modification and untracked `.codegraph/` directory remain outside PackForge commits.
