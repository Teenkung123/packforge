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
| Fabric/NeoForge CI smoke | UNEXECUTED | `artifact_smoke=true`; consumes the checksum-verified `build/libs` artifact selected by `PACKFORGE_ARTIFACT_INPUT_DIR` |
| Forge CI smoke | UNEXECUTED | `artifact_smoke=false`; ForgeGradle source-mode against its Mojmap userdev target |
| Forge 47.4.20, Java 17, exact beta.2 final JAR, startup through atlas | VERIFIED_PARTIAL | Modrinth production profile reached atlas creation without fatal diagnostics; no successful F3+T evidence |
| Forge 47.0.0 minimum, Java 17, exact beta.2 final JAR | VERIFIED_STARTUP | isolated production harness reached the final atlas marker; SHA-256 `050509...1e85e`; `ReloadCount=0`, controlled termination |
| Forge 47.4.22, Java 17, exact beta.2 final JAR | VERIFIED_STARTUP | isolated production harness reached the final atlas marker; SHA-256 `050509...1e85e`; `ReloadCount=0`, controlled termination |
| Fabric 1.20.1 minimum Loader, exact beta.2 final JAR | UNEXECUTED | production/package acceptance not recorded |
| Previous `1.3-beta.1` control | UNEXECUTED locally | reporter says previous works |
| Broken `1.3.3-beta.1` control | CONFIRMED_FROM_ISSUE_LOG | exact first fatal selector recorded |

`.github/workflows/runtime-smoke.yml` and the publication workflow define 18 runtime cells: 17 unique target/platform combinations plus the extra reporter Forge version row. Both workflows download the build-produced exact 17-artifact bundle and run `sha256sum -c` before smoke. Fabric/NeoForge use `artifact_smoke=true` with `PACKFORGE_ARTIFACT_INPUT_DIR=build/libs`, so they consume the verified packaged bytes; Forge uses `artifact_smoke=false` and is explicitly source-mode. Production final-JAR validation uses `scripts/Smoke-Forge-Production.ps1`. Forge `47.0.0` and `47.4.22` isolated runs, plus the existing `47.4.20` Modrinth profile run, reached atlas creation with the exact beta.2 JAR and no fatal Mixin signature. The isolated runs used controlled termination after startup; clean UI exit and successful F3+T reload acceptance remain open.

## Rollback

- Last published control version: `1.3-beta.1` for Forge 1.20.1.
- Do not edit mixin JSON inside a JAR.
- Replacing a mod JAR or mixin list requires full client restart; F3+T is not sufficient.
- `reloadOptimizerEnabled=false` and `loaderIndexEnabled=false` remain runtime feature fallbacks, but neither can repair a startup-time required-mixin failure in `1.3.3-beta.1`.
