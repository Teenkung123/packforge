# Gradle build guide

Use the checked-in wrapper: `./gradlew` on Linux/macOS or `.\gradlew.bat` on Windows.
The wrapper selects the project’s Gradle version. Loader plugin and library versions
are declared in `gradle/libs.versions.toml`; Minecraft targets and loader versions are
declared in `gradle/minecraft-targets.json`.

## Where to make changes

| File | Responsibility |
|---|---|
| `settings.gradle` | Reads the target registry and creates Stonecutter loader/target projects. |
| `build.gradle` | Selects a target, collects release JARs, verifies artifacts, and defines build/run aliases. |
| `gradle/node.gradle` | Configures shared sources, Java versions, dependencies, output paths, and archive settings for each loader/target. |
| `gradle/mod-resources.gradle` | Generates mod metadata and expands resource placeholders from the registry. |
| `gradle/stonecutter-sources.gradle` | Selects shared source templates and applies version-specific replacements. |
| `gradle/clean.gradle` | Defines the generated directories removed by the root cleanup task. |
| `platform/<loader>/build.gradle` | Configures the loader plugin, Minecraft dependencies, development client, and final release JAR. |
| `platform/forge/legacy.gradle` | Configures the Forge 1.20.1 toolchain, refmap, and reobfuscation. |
| `common/build.gradle` | Builds the shared Java library and its tests. |
| `verification/build.gradle` | Builds the registry and artifact verification tools and their tests. |

`stonecutter.gradle` is the controller entry point maintained by Stonecutter.
Keep its active-version marker intact.

Loader project paths use `:<loader>:<target>`, for example
`:fabric:mc1_21_1`. Their build outputs go to
`build/nodes/<loader>/<target>`. Source code lives in `common/`,
`platform/`, `versions/`, and the shared templates under `src/`.

The former root `minecraft/` source directory was empty and is no longer used.
Root `fabric/`, `forge/`, and `neoforge/` may still appear as Stonecutter
project directories; loader source files belong under `platform/`.

## Common commands

These examples use Windows syntax. Replace `.\gradlew.bat` with `./gradlew`
on Linux/macOS.

```powershell
# Build, test, and verify the complete supported release matrix.
.\gradlew.bat build

# Build every enabled loader for one target.
.\gradlew.bat :buildTarget -Ppackforge_target=mc1_21_1 --configure-on-demand

# Run the selected target's development client.
.\gradlew.bat runFabricClient -Ppackforge_target=mc1_21_1

# Verify existing release JARs without rebuilding the loader projects.
.\gradlew.bat :verifyExistingArtifacts --configure-on-demand

# Clean generated build outputs without configuring the loader plugins.
.\gradlew.bat :clean --configure-on-demand

# Exercise cleanup against a disposable fixture.
.\gradle\tests\Test-Cleanup.ps1
```

Release JARs are collected into `build/libs`. `buildAllSupported` is also
available as the explicit full-matrix build task. The default target comes from
`packforge_target` in `gradle.properties`, with the registry default as a fallback.

Runtime version overrides and `packforge_run_directory` apply only to the
selected target. The default client directory is `platform/<loader>/run/<target>`.
An overridden client directory is never a cleanup target.

## What clean removes

Both `clean` and `:clean` include the root cleanup task. It removes:

- Root `build/`, including release JARs, generated sources, node outputs, and build reports.
- `common/build/` and `verification/build/` through their normal cleanup tasks.
- `platform/<loader>/build/` left by older builds.
- Old `<loader>/build/` and `<loader>/versions/*/build/` outputs, including targets
  that have since been removed from the registry.
- IDE compiler output in root, common, verification, loader, and platform
  `out/` directories, plus `<loader>/versions/*/out/`.

Cleanup preserves source files, the Gradle wrapper, `.gradle/` caches and saved
evidence, IDE settings, CodeGraph data, logs, development clients, worlds,
resource packs, and configuration files. Old loader directories can remain when
they contain preserved data.

Cleanup validates output paths before root or shared-module deletion, rejects paths
or links that resolve outside the project, and does not follow links while deleting
their contents. It discovers old version folders only when cleanup runs and does
not walk the entire repository.
