# Gradle modernization implementation report

Updated: 2026-09-09. Local branch: `1.4`. Mod version: `1.3.4`.

## Status

The build modernization and representative local runtime validation are complete.
The implementation was committed as `2c3bf608be498012965f5adebd97e74684b49ff5`
and pushed to remote `1.4` on 2026-09-09. The user confirmed that local `1.4`
replaces the old remote development line; `1.4-archived` remains untouched.
An attempted merge of the archived history was backed out before publishing.
The hosted build workflow and final optimized runtime workflow passed. All local
implementation, representative runtime, full artifact, and hosted CI gates for
this modernization are complete. This report does not claim release acceptance.
No release or version bump was performed.

The 16 pre-existing `docs/**` deletions and untracked `.codegraph/` remain outside
the modernization change set.

## Implementation

- One Stonecutter 0.9.8 graph covers all 17 loader/version artifacts. Both `build`
  and `buildAllSupported` retain full-matrix behavior.
- Java 17 `:common` compiles shared code once and owns its shared tests. Its output
  is flattened into each mod; players do not need a separate common JAR.
- Eight bounded source templates replace 32 duplicated implementations. Large
  mixin and Minecraft-version differences remain physical adapter files.
- Fabric uses Loom, NeoForge uses ModDevGradle, modern Forge uses ForgeGradle,
  and Forge 1.20.1 uses ModDevGradle Legacy with reobfuscation and refmaps.
- The build-only verifier checks registry/source contracts, exact class inventory,
  metadata, mixins/refmaps, Java versions, and allowed nested MixinExtras content.
- Root commands, launch scripts, documentation, and workflow paths use the new graph.

### Runtime closure fixes

The earlier Fabric fix snapshots the run classpath's declared inputs before
filtering source/common output. The final packaged smoke now passes, confirming
that the former self-referential classpath no longer blocks startup.

Modern Forge had an additional failure: registering `:common`'s source set made
the run task resolve another project's runtime configuration without its Gradle
lock. Loading common as a separate JAR also introduced Java module/package
conflicts, and separate resource/class roots did not reliably form one mod.

`platform/forge/build.gradle` now uses `prepareDevelopmentMod` to synchronize
already-compiled common and adapter classes/resources into one node-local
development directory. A local source-set view exposes that output to Forge;
the run classpath excludes the separate common JAR. Producer dependencies remain
attached to the output. `MOD_CLASSES` identifies the development mod's roots.
This performs no extra Java compilation and does not alter release packaging.

The unsupported modern Forge packaged-smoke flag now fails explicitly with the
production-harness instructions. Its previous guard looked up `runClient` before
ForgeGradle registered that task, hiding the intended diagnostic.

## Verification on 2026-09-09

| Check | Result |
| --- | --- |
| Direct `gradlew.bat buildAllSupported --console plain` | Passed, all 17 artifacts; final repeat after the guard fix: 8s, 167 tasks up-to-date |
| `gradlew.bat :verifyExistingArtifacts --rerun --configure-on-demand --console plain` | Passed; verifier executed, 1s |
| Final artifacts vs pre-incremental SHA-256 snapshot | All 17 byte-identical |
| Current test XML | 231 tests, 27 suites, zero failures/errors: 99 common, 6 adapter, 126 verifier |
| Changed workflows | YAML parsing with duplicate-key rejection and job-dependency checks passed |
| Workflow shell snippets | All 22 final Bash snippets passed `bash -n` after expression substitution |
| Repository launch scripts | Bash syntax and PowerShell parser checks passed |
| Whitespace check | `git diff --check` passed |
| Unsupported Forge packaged-smoke guard | Expected failure with production-harness guidance; verified using `runClient -Ppackforge_artifact_smoke=true --configure-on-demand --dry-run` |

The final build reused current test results; it did not rerun all 231 tests.
An earlier 83-second build included dependency/cache preparation. Neither it nor
the final eight-second repeat is a new performance benchmark. Final checksums
are recorded in `SHA256SUMS` beside this report. Final hosted artifact checksums
are recorded separately in `CI-SHA256SUMS`.

### Runtime evidence

Every passing smoke below initialized PackForge, recorded the expected capability
target, completed two requested reloads, and exited cleanly. The fresh smoke runs
used isolated game directories and the existing runtime-smoke controller.

| Runtime | Mode | Date | Result |
| --- | --- | --- | --- |
| Fabric 26.1 / loader 0.18.4 | Exact packaged JAR, without Fabric API or Mod Menu | 2026-09-09 | Passed |
| NeoForge 26.1 / 26.1.0.1-beta | Exact packaged JAR | 2026-09-09 | Passed |
| Forge 26.1 / 62.0.0 | Source development runtime | 2026-09-09 | Passed |
| Forge 1.21.1 / 52.0.0 | Source development runtime; mapped-version regression check | 2026-09-09 | Passed |
| Forge 1.20.1 / 47.0.0 | Production JAR, prior run with unchanged artifact hash | 2026-09-04 | Passed, retained evidence |

Local evidence directories, relative to the repository:

- `.gradle/modernization-runtime/fabric-mc26_1_to_26_2-828d4d13`
- `.gradle/modernization-runtime/neoforge-mc26_1_to_26_2-e896d52e`
- `.gradle/modernization-runtime/forge-mc26_1_to_26_2-3c9dc3d6`
- `.gradle/modernization-runtime/forge-mc1_21_1-b529db1c`
- `.gradle/modernization-production/packforge-smoke/1.20.1-forge-47.0.0/25690904-054325-50797c78`
- `.gradle/modernization-evidence/final-build.log`
- `.gradle/modernization-evidence/final-artifact-verification.log`

These logs are local ignored evidence, not required build inputs. Fresh client
checks can use the existing `scripts/smoke-client.sh` with an isolated checkout;
set `PACKFORGE_ARTIFACT_SMOKE=true` for primary Fabric/NeoForge and `false` for
Forge. The PowerShell production harness remains available for exact Forge JARs.

## Previously measured build performance

The saved baseline and candidate JSON measurements were re-read; timings were
not remeasured during runtime closure. They measure build performance, not mod
runtime/resource-loading performance.

| Scenario | Baseline median | Candidate median | Improvement | Required |
| --- | ---: | ---: | ---: | ---: |
| No-change full build | 204.964s | 6.780s | 96.69% | 50% |
| Common-source incremental | 213.749s | 33.611s | 84.28% | 30% |
| Clean build, warm caches | 343.202s | 21.236s | 93.81% | 20% |

Measurements are under `.gradle/modernization-baseline/source/.gradle/measurements`
and `.gradle/measurements`. Artifact-size measurements are in
`.gradle/modernization-artifact-sizes.json`: the largest increase is 560 bytes
(0.041%), and the largest decrease is 1,129 bytes. Final artifact hashes still
match those measured artifacts.

Configuration cache remains disabled: strict builds previously stored entries,
but Loom's rewritten shared mappings artifact prevented reliable reuse. Daemon,
build cache, parallel execution, and the two-worker limit remain enabled.

## Hosted delivery and validation limits

The modernization commit is on remote `1.4`; pre-existing documentation
deletions and `.codegraph/` were excluded. Both non-publishing workflows were
dispatched against the exact implementation revision:

- [Build supported targets](https://github.com/Teenkung123/packforge/actions/runs/34326206366): passed all six target-build jobs and the Windows ZIP-handle test.
- [Runtime client smoke](https://github.com/Teenkung123/packforge/actions/runs/34326209330): passed the full 17-JAR bundle, all 27 exact smoke cells, and the benchmark.

The hosted Fabric 1.21.1 benchmark recorded warm medians of 1,456 ms baseline and
1,012 ms optimized (30.49% improvement), and cold medians of 3,436 ms baseline and
2,975 ms optimized (13.42% improvement). Its resource-equivalence gate passed.
These are fixture-specific measurements, not a universal resource-pack speed claim.
The publishing workflow was not dispatched.

### Runtime workflow duration and daily schedule

The original full-bundle job took 27m 36s. Commit `c05cf53` replaces that serial
prerequisite with six parallel `:buildTarget` jobs. A lightweight assembly job
downloads their outputs, executes `:verifyExistingArtifacts` without configuring
loader builds, then writes the same 17-artifact checksum manifest. All 27 smoke
cells and their validation steps are unchanged. Target build caches now share
keys with the build workflow, and registry-version runtime/benchmark caches can
fall back to those target caches. ZIP/JAR uploads use zero extra compression.

[Optimized runtime workflow](https://github.com/Teenkung123/packforge/actions/runs/34329246648)
produced the verified bundle in 6m 21s from the first target-job start to the
assembly-job finish, compared with 27m 36s for the original bundle job: a 77.0%
shorter artifact-preparation stage in these runs. All six target jobs passed;
assembly and the full artifact verifier took 32s. This observed improvement
includes shared-cache reuse and is not a controlled cold-cache benchmark.
All 17 JARs from the serial and parallel CI bundles were downloaded and compared;
their SHA-256 hashes are identical. The first optimized run passed 34 of 35 jobs,
but its Forge 1.20.1/47.4.22 smoke reused cached binary-patch output after restoring
the registry-version build cache and failed with a missing Forge loading class.
Commit `5f25df3` restricts that fallback to registry-version cells. Runtime override
cells retain only their exact-version cache keys/prefixes. The failure checks and
all smoke cells remain intact.

[Corrected optimized workflow](https://github.com/Teenkung123/packforge/actions/runs/34330538070)
produced the verified bundle in 6m 37s (08:41:55Z to 08:48:32Z), 76.0% shorter
than the original 27m 36s stage. Its legacy override passed; 34 of 35 jobs passed.
The remaining Forge 26.1 job failed during Forge's preliminary graphics-window
initialization and stayed at an interactive help prompt until the readiness
timeout. The fatal message existed after ten seconds, but the harness previously
did not recognize it and waited about fourteen additional minutes.

Commit `f6c1350` adds immediate recognition of these fatal graphics messages.
A controlled execution of the actual shell harness with a failing launcher stub
exited nonzero in three seconds, before its readiness timeout. Ordinary GL-version
negotiation is not treated as a fatal error.

Modern Forge CI cells set `earlyWindowControl = false` in their isolated FML
configuration. This uses Forge's supported no-splash window path; the real
Minecraft window, initialization, and resource reload checks still run. See
[Forge's ImmediateWindowHandler](https://github.com/MinecraftForge/MinecraftForge/blob/26.1.2/fmlloader/src/main/java/net/minecraftforge/fml/loading/ImmediateWindowHandler.java).
The setting is confined to CI, not player configuration or production artifacts.
Modern Forge's separately downloaded `~/.minecraft/assets` directory is also
cached; it was absent from the previous cache paths.

[Final optimized workflow](https://github.com/Teenkung123/packforge/actions/runs/34333093321)
passed all 35 jobs on `f6c135000a26c7fdab12aba7081322eea41a92dc`: six target builds,
aggregate artifact verification, the benchmark, and all 27 smoke cells. Both the
legacy Forge override and modern Forge no-splash window paths passed. All 17
final JARs were compared against the original successful CI bundle and remain
byte-identical.

| Observed CI elapsed time | Original successful run | Final successful run | Reduction |
| --- | ---: | ---: | ---: |
| Artifact preparation | 27m 36s | 7m 20s | 73.43% |
| Whole runtime workflow | 36m 22s | 15m 7s | 58.43% |

Times span the earliest relevant job start through the latest job completion,
including setup, verification, artifact transfers, and cleanup. They are actual
run comparisons with different cache availability, not controlled cold-cache
benchmarks. The asset cache's future warm-run benefit is not separately measured.

The daily schedule was removed on `1.4` in `61ca8e2`. With explicit approval, the
same two-line removal was made on default branch `master` in `26d5a22`, where
GitHub evaluates schedules. The default-branch commit used `[skip ci]` and started
no extra run. Manual and pull-request/push triggers remain available.

Local checks do not establish every supported loader/version combination's runtime
behavior, every 26.x point release, optional integrations, large-pack behavior,
or interactive configuration-screen behavior. Modern Forge checks above are
source-mode checks, not production-JAR acceptance for those versions.

Sandboxed attempts failed on existing JAR access and loopback connections; the
successful checks ran outside those restrictions. The previous native-memory
failure did not recur. GitHub authentication works outside the sandbox.

Downloading/executing actionlint was rejected by automatic approval review as an
unverified third-party binary. No such binary was executed. The workflow checks
above used already-installed SnakeYAML and Bash, and do not replace hosted CI.
