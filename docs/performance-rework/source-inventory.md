# Performance rework source inventory

## Repository baseline

| Item | Recorded value |
|---|---|
| Repository | `Teenkung123/packforge` |
| Audited revision | `086f3f8686109e81263a155bbc3fd3caa43af611` |
| Optimization starting revision | `4fafbef4b95c2d39bc67131256d34f170195fbc6` |
| Starting commit | `fix(forge): repair 1.20.1 mixin remap` |
| Mod version | `1.3.3` |
| License | MIT |
| Default target | `mc26_1_to_26_2` |
| Root Java floor | Java 17-compatible source |
| Primary runtime/toolchain | Java 25 |
| Target registry | `gradle/minecraft-targets.json`, schema 1 |
| MixinExtras | `0.5.4` |

The optimization pass starts one commit after the audited revision. The intervening commit is the isolated Forge 1.20.1 issue #5 crash hotfix and its release/runtime gates. No reset or history rewrite was performed.

## Supported artifact matrix

| Target | Java | Fabric | Forge | NeoForge | Maturity |
|---|---:|---|---|---|---|
| `mc26_1_to_26_2` | 25 | yes | yes | yes | stable |
| `mc1_21_1` | 21 | yes | yes | yes | beta.2 |
| `mc1_21_4` | 21 | yes | yes | yes | beta.2 |
| `mc1_21_8` | 21 | yes | yes | yes | beta.2 |
| `mc1_21_11` | 21 | yes | yes | yes | beta.2 |
| `mc1_20_1` | 17 | yes | yes | no | beta.2 |

Total: 17 artifacts. Optimization work may not remove a target or weaken its maturity declaration.

## Audited-to-starting delta

The only commit after the audited revision is `4fafbef`. It changes the two unstable synthetic mixin selectors to structural hooks, applies the atlas structural hook to other adapters that shared the risk, strengthens artifact/refmap checks, restores runtime workflows, adds Windows/production smoke harnesses, and records the incident evidence. Performance implementation begins with a clean worktree at that commit.

## Environment

- Windows NT `10.0.26200.0`, amd64.
- Processor identifier: `Intel64 Family 6 Model 191 Stepping 2, GenuineIntel`; 20 logical processors exposed.
- Gradle `9.5.1`.
- Launcher/daemon JVM: Oracle Java `25.0.1`.
- Target toolchains remain Java 25, 21, and 17 as declared by the registry.

