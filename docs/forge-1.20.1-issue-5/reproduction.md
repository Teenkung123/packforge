# Reproduction evidence

## Issue attachments

Downloaded from the public issue attachment URLs on 2026-08-05 after the browser fetch path failed and direct HTTPS retrieval was retried outside the restricted sandbox.

| File | SHA-256 | Bytes |
|---|---|---:|
| `crash.txt` | `cab349f95f8d57e171d2c4a31d4bea07762d8b1c03088d73b8a232977bbe6a06` | 18,828 |
| `latest.log` | `047c91651d3d9eb97f00cdde649e340caf4b3218c25ee39ace8f498ecfc5b3bd` | 50,760 |

Generated copies live under ignored `build/forge-1.20.1-issue-5/attachments/`.

## Earliest fatal chain

`latest.log` line 175 and `crash.txt` line 48 identify:

```text
Mixin apply failed packforge.fabric.mixins.json:observe.SimpleReloadInstanceMixin
```

The causal exception is:

```text
InvalidInjectionException: @WrapOperation ... could not find any targets matching 'method_18368'
```

The exception explicitly reports `packforge.refmap.json`. This establishes that the final runtime artifact loaded its declared refmap but the enclosing selector remained unresolved.

## Published artifact controls

| Role | Modrinth version | Version ID | File |
|---|---|---|---|
| Broken | `1.3.3-beta.1` | `dPA7zSgm` | `packforge-forge-1.3.3-beta.1-mc1.20.1.jar` |
| Previous control | `1.3-beta.1` | `d7wj0W6O` | `packforge-forge-1.3-beta.1-mc1.20.1.jar` |

Both downloads matched Modrinth's published SHA-512 metadata. Exact hashes are recorded in `artifact-inspection.md`.

## Repository baseline

- Branch: `codex/forge-1.20.1-performance`
- Starting HEAD: `086f3f8686109e81263a155bbc3fd3caa43af611`
- Audited HEAD expected by assignment: exact match
- Starting worktree: clean
- Gradle: 9.5.1
- Build JVM: Oracle JDK 25.0.1
- Target runtime Java: 17

Untouched audited HEAD successfully ran:

```text
gradlew.bat -p platform/forge clean assemble -Ppackforge_target=mc1_20_1 --no-daemon
gradlew.bat verifyTargetArtifacts -Ppackforge_target=mc1_20_1 --no-daemon
```

The compiler emitted warnings that it could not determine descriptors for `method_18368` and `method_47660`, yet both commands passed. This is the documented baseline false negative.

## Live reproduction status

The issue log is direct runtime evidence from Forge 47.4.22. The ForgeGradle `artifact_smoke=true` attempt is not production evidence: it injected the final SRG JAR into a Mojmap userdev target and failed on `LoadingOverlay @Shadow f_96163_`. Forge CI therefore uses source-mode; Fabric/NeoForge packaged smoke consumes the checksum-verified `build/libs` bytes through `PACKFORGE_ARTIFACT_INPUT_DIR`.

Production final-JAR validation uses `scripts/Smoke-Forge-Production.ps1`. The exact `packforge-forge-1.3.3-beta.2-mc1.20.1.jar` (`SHA-256 0505099080d0e64b5fa468f2fc0447987eac3951e8fae6d957218d852ae1e85e`) reached the final atlas marker without a fatal Mixin signature on Forge `47.0.0`, `47.4.20`, and `47.4.22`. The `47.0.0` and `47.4.22` isolated harness runs used controlled termination after startup (`ReloadCount=0`), not a clean UI exit. No successful automated F3+T completion is recorded, so reload/visual acceptance remains open.
