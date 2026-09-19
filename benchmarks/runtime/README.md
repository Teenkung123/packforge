# Neutral production launcher

The original minimal-profile protocol below remains supported. The separate
`benchmarks/full_modpack` suite adds explicitly frozen FO companion mods, selected
resource packs/shaders, configurable exact JDK/JVM/framebuffer pins, menu/in-world
scenarios and combined PackForge/Quick Pack mode. See its README before preparing
full-profile runs; minimal defaults must not be applied to FO comparisons.

FO can explicitly use `normalizationPolicy: "fo-runtime-metadata-v1"` for the
four reviewed runtime metadata files described in the full-modpack README.
Frozen input hashes remain exact. Raw output hashes and diffs remain in the
manifest; canonical comparison covers only generated date comments and Sodium
installation/notification bookkeeping. All visual/performance values remain
strict. `rawInputsUnchanged` distinguishes byte equality from effective equality.

Diagnostic texture capture requires `resourceHashingEnabled: true` together
with mod artifact ID `texture_correctness`. The runner supplies
`packforge.correctness.resourceHashingEnabled=true` and its owned
`texture-pixels.jsonl` output. Capture artifacts/flags cannot enter headline runs.

`runner.py` is a Python-standard-library Windows launcher, independent of Gradle,
PackForge classes, and private launcher authentication. All measured clients use
Java 25 at `C:/Program Files/Java/jdk-25/bin/java.exe`, `-Xms1G -Xmx4G`, and a
1280×720 offline menu. The observer controls priming, measured reloads, and clean
shutdown. No original profile, worlds, accounts, or credentials are copied.

```
python -m unittest discover -s benchmarks/runtime -p test_runner.py -v
python benchmarks/runtime/runner.py provision download-lock.json
python benchmarks/runtime/runner.py provision download-lock.json --execute
python benchmarks/runtime/runner.py inspect run-spec.json
python benchmarks/runtime/runner.py run run-spec.json
```

Only `run` launches a client. `inspect` checks every required asset, library,
client, and native before displaying arguments and hashes. It is deliberately
strict: inherited metadata requires an explicit `metadataRoot`; missing libraries and unknown
rules/tokens fail rather than substituting guessed launch arguments. Provisioning
is an explicit separate phase. A download lock contains `files` objects with
`url`, `path` (relative to `.gradle/performance-rework/provisioned`), `hash`, and
optional `algorithm` (`sha1` default or `sha256`). Only official Mojang/loader
HTTPS origins and published hashes are accepted. Existing complete caches may be
referenced directly in the run spec; they are read-only.

Forge and NeoForge must first be installed with their official client installer
in `.gradle/performance-rework/provisioned/<cell>`. Installer processors are not
part of a measured run. This utility does not execute installers or invent the
resulting classpath. Obtain and checksum the official installer, finish its client
installation, and supply the resulting version JSON and explicit metadata root.
Inheritance recursively combines parent/child arguments and selects child library
versions, with cycle and traversal guards. The client JAR defaults to the parent
unless the child specifies `jar` or a client download; `clientJar` is an optional
explicit path override, still checked against the selected metadata checksum.
Exact supported pins are:

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 26.1.2 | 0.19.3 | 26.1.2-64.1.0 | 26.1.2.94 |
| 26.2 | 0.19.3 | 26.2-65.1.0 | 26.2.0.48-beta |

For source-mode Fabric checks with a Minecraft override, pin the matching Fabric
API too. For example, the noninteractive 26.2 test command is:

```powershell
.\gradlew.bat :fabric:mc26_1_to_26_2:test --configure-on-demand '-Ppackforge_minecraft_version_override=26.2' '-Ppackforge_fabric_api_version_override=0.158.0+26.2'
```

The API override affects the selected target only; normal release dependencies
remain registry-pinned. Do not use `packforge_artifact_smoke=true` for unit tests:
that launch mode deliberately removes the shared module from runtime dependencies
so a packaged client loads those classes from its mod JAR.

## Run spec

Each scheduled process has a separate spec. Paths are absolute. `context.hardware`
must contain measured CPU, GPU/driver, RAM bytes, and OS details as required by
`benchmarks/analysis/README.md`; the launcher supplies actual JDK output/hash,
artifacts, configuration hashes, fixed settings, and launcher inputs.

```json
{
  "runId":"26.1.2-fabric-developrp-current-00",
  "cellId":"26.1.2-fabric-developrp", "mode":"current", "block":0,
  "workload":"developrp", "minecraft":"26.1.2", "loader":"fabric", "loaderVersion":"0.19.3",
  "versionJson":"C:/absolute/installed/version.json",
  "metadataRoot":"C:/absolute/installed/versions",
  "clientJar":"C:/absolute/installed/client.jar",
  "librariesRoot":"C:/absolute/meta/libraries", "assetsRoot":"C:/absolute/meta/assets",
  "nativesRoot":"C:/absolute/meta/natives/exact-version",
  "loggingConfig":"C:/absolute/meta/log_configs/client-1.21.2.xml",
  "mods":[{"id":"observer","path":"C:/absolute/observer.jar","sha256":"REPLACE"},
          {"id":"packforge","path":"C:/absolute/frozen-baseline.jar","sha256":"REPLACE"}],
  "pack":{"id":"DevelopRP-Full-1.9.5.zip","path":"C:/absolute/protected.zip","sha256":"REPLACE"},
  "acceptIncompatiblePack":true,
  "configs":[{"id":"packforge.json","path":"C:/absolute/prepared-config.json","sha256":"REPLACE"}],
  "context":{"hardware":{"cpu":"RECORD","gpu":"RECORD driver","ramBytes":0,"os":"RECORD"}},
  "timeoutSeconds":600
}
```

Vanilla specs contain the observer only and no optimizer config. Current,
corrected, and candidate contain `packforge`; Quick Pack contains `quickpack`
only when an official matching released binary has been acquired. Additional
required mod dependencies must appear explicitly in `mods`. Do not put user
profiles or their arbitrary mods on this classpath.

The protected pack is streamed byte-for-byte with SHA-256 verification, never
opened as ZIP. Every run uses a fresh directory under
`.gradle/performance-rework/runs/<runId>`; existing results are never overwritten.
The manifest uses schema 1 from the analysis contract and records the OS process
creation time from Windows `GetProcessTimes`, PID, exit status, full neutral
command, and hashes. Inputs are hashed before launch and after exit, outside
timing. Config changes, timeout, nonzero exit, and missing/error events invalidate
the run. The analysis tool remains the authoritative full event-order and
statistical gate validator. Missing observations never become passing results.

Before headline collection, perform untimed preparation runs to generate stable
version-specific default configuration snapshots; explicit configs must not
change during collection. Inspect actual options after a preflight because
vanilla can normalize lists or add options. Complete the separate first-use
text/model/texture and memory/visual correctness harness before admitting wins.
These scripts alone establish neither runtime success nor a 15% improvement.

Qualifying runs provide `optionsSnapshot: {"path":"<private-preflight>/options.txt",
"sha256":"<exact SHA-256>"}`. The runner verifies its controlled settings, copies
the complete file byte-for-byte without changing the source, and pins its full
hash before/after execution. This includes language, Unicode, texture filtering,
and every other option rather than only the generated subset. Snapshot mismatch
or any later byte change rejects the run. Without a snapshot, functional
preflights retain the existing vanilla-normalization behavior.

Context records `optionsSnapshotPinned` and postflight `effectiveOptions` with
every parsed option key/value. `configs` includes an `options.txt` hash (the actual
postflight file; `inputHashes` preserves its initial pinned hash). Missing or
malformed options reject the run and leave `effectiveOptions` empty. Headline
analysis must require a pinned snapshot and compare all effective options across
modes; historical unpinned runs remain functional evidence only.

`acceptIncompatiblePack` defaults to `false` and accepts only a JSON boolean.
For a pack whose format is older than the client, explicit `true` uses vanilla's
normal accepted-incompatible list (`incompatibleResourcePacks`), without changing
the archive. This option is recorded with the graphics/options context and must
remain equal across compared modes and after exit. Numbered JNA temporary DLLs
(`jna[digits].dll`) are excluded from pinned native inputs because JNA owns their
deletion/recreation; stable DLLs and all dependency JARs remain hash checked.
Postflight missing files or malformed events produce a rejected manifest with
safe `failureReason`, preserving actual PID, process creation time, and exit code.
Diagnostics list changed option keys, changed-input count, and relative owned-profile
paths for changed/missing/unexpected files. External cached paths are not included
in that diagnostic summary. Current video settings pin `graphicsPreset:"custom"`
and `improvedTransparency:false`; missing settings still reject the run.
`inactivityFpsLimit:"minimized"` disables vanilla's AFK-based throttle without
simulating input. The observer separately rejects minimized/focus-lost runs;
postflight rejects a missing setting or a return to `"afk"`.

`reloadCount` defaults to 6 and accepts integers 1–128. Smaller counts are for
functional preflights only; the analyzer rejects incomplete six-reload sequences.
`profilingEnabled` and `resourceHashingEnabled` are strict JSON booleans, default
false, recorded in comparison context. Setting these labels does not install or
activate instrumentation. Instrumented diagnostic runs must set the appropriate
labels, and the headline analyzer rejects either true value regardless of exit
status. A runner-valid functional result is not a qualifying performance result.

Optional `flightRecording:true` explicitly starts JDK Flight Recorder with the
`profile` settings and dumps to the fresh run's `profile.jfr` on exit. It requires
`profilingEnabled:true`, is recorded in context, and accepts no user-supplied
output path. Neither flag defaults to true; a profiling label alone does not
enable recording. Use only for PackForge/Minecraft diagnostics, not Quick Pack
implementation inspection, and never admit its timings as headline results.

The observer receives `expectedWidth=1280`, `expectedHeight=720`, and
`expectedPackId=file/<pack.id>` under the `packforge.benchmark` property prefix.
These use the same fixed dimensions and selected pack as launcher arguments and
manifest context. The integrity observer reports actual framebuffer dimensions
on events and ordered active pack IDs at ready/frame observations; headline
analysis must require the expected resolution and selected protected pack rather
than trusting only the written options file. This check performs no resource I/O.

Every child JVM receives private `TEMP` and `TMP` pointing to
`.gradle/modernization-temp`, created before launch. The parent process environment
is unchanged. Manifest `context.runtimeEnvironment` contains exactly these two
controlled values, never the inherited environment or credentials. Compare this
field strictly across headline runs; historical manifests without it are not
equivalent to this environment. Using one stable short private temp path avoids
the Forge version-check networking failure observed with the inherited path.

## Existing Fabric cache paths on this host

Use these read-only paths in a Fabric 26.1.2 spec (replace `26.1.2` with `26.2`
for its matching cell). These are public game metadata/assets, not account files:

```json
{
  "metadataRoot":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/versions",
  "versionJson":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/versions/26.1.2-0.19.3/26.1.2-0.19.3.json",
  "librariesRoot":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/libraries",
  "assetsRoot":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/assets",
  "nativesRoot":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/natives/26.1.2-0.19.3",
  "loggingConfig":"C:/Users/nathaphon/AppData/Roaming/ModrinthApp/meta/log_configs/client-1.21.2.xml"
}
```

An `inspect` success is required before using any cached profile. Cached metadata
can reference missing libraries/assets; existence of a profile alone is not a
provisioning proof. Every inherited JSON is included in the launch input hashes.
