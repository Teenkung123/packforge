# Gradle modernization implementation report

Updated: 2026-09-09. Local branch: `1.4`. Mod version: `1.3.4`.

## Status

The build modernization and representative local runtime validation are complete.
Hosted GitHub Actions validation remains pending: these changes have not been
committed or pushed. This report does not claim full runtime-matrix or release
acceptance. No release, version bump, commit, or push was performed.

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
| Workflow shell snippets | All 20 Bash snippets passed `bash -n` after expression substitution |
| Repository launch scripts | Bash syntax and PowerShell parser checks passed |
| Whitespace check | `git diff --check` passed |
| Unsupported Forge packaged-smoke guard | Expected failure with production-harness guidance; verified using `runClient -Ppackforge_artifact_smoke=true --configure-on-demand --dry-run` |

The final build reused current test results; it did not rerun all 231 tests.
An earlier 83-second build included dependency/cache preparation. Neither it nor
the final eight-second repeat is a new performance benchmark. Final checksums
are recorded in `SHA256SUMS` beside this report.

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

## Remaining delivery gates and limits

1. Commit only modernization files and this evidence, excluding the pre-existing
   documentation deletions and `.codegraph/`, after authorization.
2. Push the reviewed changes and run the build and runtime-smoke workflows on that
   exact revision. Do not dispatch the publishing workflow as a validation step.
3. Inspect hosted runtime-matrix and benchmark results before release acceptance.

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
