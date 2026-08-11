# Stonecutter refactor baseline

Recorded before functional migration on 2026-08-11 (Asia/Bangkok).

## Git and workspace

- Repository: `Teenkung123/packforge`
- Baseline HEAD: `609270f533666e7636d11f7d16590be925ec836f`
- Baseline parent: `891beddbfc1ee39c13ad90ea55181de8b005f459`
- Baseline subject: `test bug report template #2`
- Starting branch: `master`, `ahead 2, behind 2` versus `origin/master`
- Implementation branch: `codex/packforge-stonecutter-refactor`
- Unrelated user change preserved and not staged: `.github/ISSUE_TEMPLATE/bug-report.yml`
- Stonecutter references at baseline: none

## Toolchain

- Gradle wrapper: 9.5.1
- Launcher/daemon Java: 25.0.1
- Existing target floors: Java 17, 21, and 25
- OS: Windows 11 amd64

## Current matrix

| Target | Minecraft metadata | Java | Platforms | Maturity | Artifacts |
|---|---|---:|---|---|---:|
| `mc26_1_to_26_2` | `26.1-26.2` | 25 | Fabric, Forge, NeoForge | stable | 3 |
| `mc1_21_1` | `1.21.1` | 21 | Fabric, Forge, NeoForge | beta | 3 |
| `mc1_21_4` | `1.21.4` | 21 | Fabric, Forge, NeoForge | beta | 3 |
| `mc1_21_8` | `1.21.8` | 21 | Fabric, Forge, NeoForge | beta | 3 |
| `mc1_21_11` | `1.21.11` | 21 | Fabric, Forge, NeoForge | beta | 3 |
| `mc1_20_1` | `1.20.1` | 17 | Fabric, Forge | beta | 2 |

Total: 6 registry targets and 17 release artifacts (Fabric 6, Forge 6, NeoForge 5). The required 22 exact-release ledger is not yet present.

## Source metrics

Production Java only; tests, docs, generated output, and third-party code excluded.

| Root | Files | LOC |
|---|---:|---:|
| `common/src` | 54 | 5,400 |
| `platform/fabric/src` | 4 | 70 |
| `platform/forge/src` | 2 | 63 |
| `platform/neoforge/src` | 2 | 61 |
| `versions` | 150 | 11,243 |
| Total | 212 | 16,837 |

Normalized exact-file comparison across version production Java found 20 duplicate groups. Largest groups include five copies of `ForgeModListCompat`, five copies of `ReloadableResourceManagerMixin`, four copies of `RuntimeResourceHash`, four copies of `SharedZipFileAccessMixin`, three copies of `FilePackResourcesMixin`, three copies of `PackForgeConfigScreen`, and three copies of `PackForgeClient`.

## Baseline commands and results

| Command | Result |
|---|---|
| `./gradlew.bat validateTargetRegistry verifyExistingArtifacts --no-daemon --stacktrace` | PASS |
| `./gradlew.bat -p platform/fabric -Ppackforge_target=mc1_21_1 test --no-daemon --stacktrace` | PASS, 40s |
| `./gradlew.bat -p platform/forge -Ppackforge_target=mc1_20_1 test --no-daemon --stacktrace` | PASS, 48s |
| `./gradlew.bat -p platform/neoforge -Ppackforge_target=mc26_1_to_26_2 test --no-daemon --stacktrace` | PASS, 40s |
| `./gradlew.bat benchmarkPackIndex --no-daemon --stacktrace` | PASS; see benchmark evidence below |
| `./gradlew.bat clean buildAllSupported --no-daemon --stacktrace` | PASS, 4m19s; all 17 child artifact verifiers passed |
| `./gradlew.bat validateTargetRegistry verifyExistingArtifacts --no-daemon --stacktrace` after clean build | PASS, 7s |
| `./gradlew.bat verifyAllArtifacts --no-daemon --stacktrace` | TIMEOUT at 120s, retried and TIMEOUT at 300s; not counted as pass |

## Benchmark evidence

The deterministic fixture contained 20,014 central-directory entries and 20,014 unique paths. Baseline and indexed semantic hashes matched:

```text
baselineHash=28ba0ce3fce57d83e14e7e9d47c386316eba77156baaa3ac5984fe47952f4a8b
indexedHash=28ba0ce3fce57d83e14e7e9d47c386316eba77156baaa3ac5984fe47952f4a8b
indexBuildNs=18070000
baselineMedianNs=79432700
indexedMedianNs=272500
improvementPercent=99.66
checksum=251000
```

## Runtime smoke evidence

- Packaged Fabric `mc26_1_to_26_2`: the client reached startup and logged `PackForge reload complete: id=1 ... status=ok`; the harness failed at `scripts/Smoke-Client.ps1:555` while activating the window for F3+T. The run is incomplete, not a pass. The generated 26.x fixture also emitted pack-metadata parsing warnings and needs a fixture audit.
- Forge `mc1_20_1` source-mode run with Forge `47.4.22`: startup and one reload reached `PackForge reload complete: id=1 ... status=ok`; the same window-activation failure stopped the harness. This is not final-JAR production proof.
- Forge final remapped/JarJar production smoke: `UNTESTED` at baseline.
- Full exact-version/loader smoke matrix: `UNTESTED` at baseline.

## Baseline conclusion

Current legacy build, focused tests, deterministic benchmark, clean 17-artifact build, and direct final-artifact verification pass. Stonecutter migration, schema v2, contiguous exact-release support, Quick Pack ownership, unified effective-state policy, artifact consolidation, and default promotion remain unimplemented.
