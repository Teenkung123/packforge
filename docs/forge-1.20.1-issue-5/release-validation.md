# Release validation

Target replacement: PackForge `1.3.3-beta.2` legacy artifacts.

## Automated gates

| Gate | Status | Evidence |
|---|---|---|
| Issue attachments retrieved and hashed | PASS | `reproduction.md` |
| Earliest fatal selector identified | PASS | issue `latest.log` and `crash.txt` |
| Broken final refmap/class inspected | PASS | `mixin-remap-audit.md` |
| Baseline artifact gate false negative reproduced | PASS | untouched `verifyTargetArtifacts` exit 0 |
| Forge/Fabric 1.20.1 compile after repair | PASS | builder compile plus main clean Forge assemble and target verification |
| Final Forge 1.20.1 artifact verification | PASS | SHA-256 in `artifact-inspection.md` |
| Final Fabric 1.20.1 artifact verification | PASS | SHA-256 in `artifact-inspection.md` |
| All 17 artifact build/verification | PASS | clean matrix produced 17; `verifyExistingArtifacts` exit 0 |
| Exact 17-artifact `SHA256SUMS` bundle | CONFIGURED | build and runtime-smoke workflows write/download/verify the same manifest |
| Configured mixin class presence audit | PASS | final artifact verifier |
| Forge/NeoForge intermediary selector audit | PASS | final artifact verifier; zero configured raw selectors |
| Forge 1.20.1 refmap audit | PASS | `<init>` and `StateFactory#m_10863_` mapping inspected |

## Runtime matrix

| Profile | Status | Notes |
|---|---|---|
| CI runtime matrix: 18 cells | CONFIGURED | 17 unique target/platform combinations plus extra reporter Forge 1.20.1 version row; exact 17-artifact bundle is downloaded and checksum-verified first |
| Fabric exact-artifact startup | VERIFIED | local runs for 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1, and 26.2 reached PackForge/resource readiness and reported `cleanExit=true` |
| NeoForge exact-artifact startup | VERIFIED | local runs for 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1, and 26.2 reached readiness and reported `cleanExit=true` |
| Forge CI smoke | UNEXECUTED | `artifact_smoke=false`; ForgeGradle source-mode against its Mojmap userdev target |
| Forge 47.4.20, Java 17, exact beta.2 final JAR, startup through atlas | VERIFIED_PARTIAL | Modrinth production profile reached atlas creation without fatal diagnostics; no successful F3+T evidence |
| Forge exact-artifact startup | VERIFIED_STARTUP | 1.20.1 at 47.0.0 and 47.4.22, plus 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1, and 26.2 reached final resource markers with controlled termination |
| Fabric 1.20.1 minimum Loader, exact beta.2 final JAR | VERIFIED | clean startup and shutdown; SHA-256 `a5c53b...f4ce` |
| Previous `1.3-beta.1` control | UNEXECUTED locally | reporter says previous works |
| Broken `1.3.3-beta.1` control | CONFIRMED_FROM_ISSUE_LOG | exact first fatal selector recorded |

`.github/workflows/runtime-smoke.yml` and the publication workflow define 18 runtime cells: 17 unique target/platform combinations plus the extra reporter Forge version row. Both workflows download the build-produced exact 17-artifact bundle and run `sha256sum -c` before smoke. The complete actual-26.2 coverage described here is local evidence, not current CI coverage. Production final-JAR validation uses `scripts/Smoke-Fabric-Production.ps1`, `scripts/Smoke-Forge-Production.ps1`, and `scripts/Smoke-Client.ps1`. All supported target/platform artifact cells reached startup readiness without a detected fatal Mixin, linkage, PackForge, launcher-output, or crash-report signature. The shared 26.1-26.2 artifacts were launched on both actual Minecraft versions for all three loaders. Fabric and NeoForge reported `cleanExit=true`. Forge production runs used controlled termination after readiness, so clean UI exit and successful F3+T reload acceptance remain open.

## Rollback

- Last published control version: `1.3-beta.1` for Forge 1.20.1.
- Do not edit mixin JSON inside a JAR.
- Replacing a mod JAR or mixin list requires full client restart; F3+T is not sufficient.
- `reloadOptimizerEnabled=false` and `loaderIndexEnabled=false` remain runtime feature fallbacks, but neither can repair a startup-time required-mixin failure in `1.3.3-beta.1`.
