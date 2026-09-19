# Fabulously Optimized benchmark

This suite adds the real FO workload without replacing the six game/loader
startup/reload gates in `benchmarks/analysis`. Every result remains private under
`.gradle/performance-rework`; the protected resource pack must never enter CI
artifacts. These tools do not inspect Quick Pack implementation or open archives.

```powershell
python -m unittest discover -s benchmarks/full_modpack -p 'test_*.py' -v
python benchmarks/full_modpack/prepare.py freeze --profile 'C:/Users/nathaphon/AppData/Roaming/ModrinthApp/profiles/Fabulously Optimized' --output .gradle/performance-rework/fo-frozen-profile --packforge-file packforge-fabric-1.4-mc26.1-26.2.jar --quickpack-file quick-pack-fabric-1.5.0+26.1.2.jar.disabled
python benchmarks/full_modpack/prepare.py prepare --manifest .gradle/performance-rework/fo-frozen-profile/manifest.json --output .gradle/performance-rework/fo-prepared
python benchmarks/full_modpack/analysis.py schedule --output .gradle/performance-rework/fo-schedule.json
python benchmarks/full_modpack/analysis.py analyze --schedule .gradle/performance-rework/fo-schedule.json --runs .gradle/performance-rework/runs --output .gradle/performance-rework/fo-admission.json
```

Output destinations must be new. Rejected attempts and previous artifacts are
never overwritten. Analyze an isolated set containing exactly the scheduled
runs; including unrelated preflights is rejected instead of silently filtered.

`freeze` reads only `mods`, `config`, `options.txt`, `resourcepacks` and
`shaderpacks`. It rejects symbolic links/junctions, copies bytes with before/after
SHA-256 checks, validates the approved DevelopRP hash, and preserves ordered
selected packs. Original saves/accounts/servers/history are outside its traversal.
It records top-level JAR count separately from loaded nested mod entries; the
latter must come from the isolated runtime. A frozen directory is not a launchable
profile: exact JDK/JVM settings and actual framebuffer still need verification.

`prepare` creates complete options and Dynamic FPS snapshots with identical
benchmark-only throttling changes. It keeps fullscreen, mip level 2, render
distance 8, simulation distance 6, shaders, language, resource order and all other
visual options. Quick Pack's original profile files and binary remain untouched.
The user authorized only `removeLoadingOverlayFadeOut: true -> false` in isolated
equal-fade benchmark copies; every other Quick Pack setting stays identical.
Default-experience references retain the original `true` setting. The incomplete base spec
deliberately omits fabricated runtime pins and matched-fade claims.

The configuration-only control uses the frozen starting PackForge binary. Its
old CPU-mip gate requires `largeAtlasFixerEnabled`; cap, retry and model UV clamp
are explicitly disabled so that enabling that gate cannot enable their pixel
changes. The control disables bitmap retention, ZIP pooling/read reuse,
unadmitted experiments, detailed timings and executor tuning. Its complete
before/after preview is a separate file. This is an explicit benchmark control,
not a migration of the original user's config. Candidate config comes from the
production recommended-settings implementation, after review and preflight.

Each full-modpack runtime spec adds these fields to the existing runner contract:

- `benchmarkSuite: "full-modpack"`, `workload: "fabulously_optimized"`, and
  `scenario: "menu"` or `"inworld"`; the cell ID appends the scenario.
- `minecraft: "26.1.2"`, `loader: "fabric"`, `loaderVersion: "0.19.2"`.
- `frozenProfileSha256`; `framebuffer: {width, height}` with explicit positive
  values checked against actual observer events. A Windows display guess alone
  does not establish measured framebuffer size.
- `java: {path, sha256}` and exact reviewed `jvmOptions`, including one `-Xmx`.
  Supply one `-Xms`, or explicitly select `jvmInitialHeap: "default"` when the
  reviewed launcher leaves initial heap sizing to the JVM. Only reviewed
  memory/GC flags are accepted; no arbitrary properties, agents or main classes.
- Every original active companion in `mods`, using `filename` to preserve its
  basename. Optimizer identities are `packforge` and `quickpack`; one neutral
  `observer` is required. Combined mode explicitly requires both optimizers.
- `resourcePacks`, `shaderPacks` and `configs` with exact byte hashes, plus
  `orderedPackIds` and the full `optionsSnapshot`. These are independently copied
  from private snapshots, not linked to original user inputs. The runner checks
  disk space before copying and reserves 1 GiB for the run plus a 10 GiB free floor.
- In-world `generatedScene` carries the complete observer-created scene manifest
  contract: exact ID, integer seed, ordered fixed entity UUIDs, observer JAR,
  compiled implementation and source hashes, plus manifest path/hash. The runner
  verifies the manifest and compiled scene independently before supplying the
  four expected-scene properties. See `OBSERVER_CONTRACT.md`. No saves are copied.
- `qualityContract: {matched: true, evidenceSha256: ...}` only after the matching
  shader/texture/fade behavior is established in a separate correctness report.
  Unknown or unsupported matching remains unavailable and fails admission.

Modes are `vanilla` (FO without either optimizer), `saved`, `configuration`,
`candidate`, `quickpack` (matched quality), `combined`, and the separate
`quickpack_default` experience reference. Saved/configuration use the same
frozen PackForge binary; candidate/combined use the same candidate binary; all
Quick Pack modes use the same released binary. Changes in unrelated mods,
configurations, resource precedence, shaders or options reject the comparison.
The approved equal-fade config difference must have an exact source/output hash
and one-field review. Its existence alone does not establish full rendering/fade
equivalence: runtime evidence remains required by `qualityContract`.

The seeded design has 112 fresh processes: two scenarios, ten processes each
for saved/configuration/candidate/matched Quick Pack/combined and three each for
vanilla/default Quick Pack. Each process has one priming and five measured
reloads. Five primary modes occupy each primary order position twice. Filesystem
caches remain uncontrolled; fresh processes do not imply cold filesystem caches.

Analysis uses each process's median of five reloads, then paired process-level
bootstrap resampling. Candidate and combined independently require median
ratio <=1.05, upper endpoint of the 95% ratio CI <=1.05, and p95 ratio <=1.05
versus matched Quick Pack, separately for menu and in-world. Startup, completion,
configuration gains and code gains are also reported in absolute milliseconds,
medians, p95 and process-level intervals. Three-process references are descriptive
only. All failed/incomplete inputs remain failures; no outlier deletion or
replacement-run cherry-picking is permitted.

Protocol 3 separately records successful resource completion, new resources
rendered, acknowledged input, usable frame and raw overlay retirement. Menu
input must acknowledge the current rendered generation through actual character
dispatch, with title fading finished and no activity reset. In-world input must
acknowledge natural world key handling; a menu acknowledgment cannot satisfy it.
An allocated but nonblocking RRLS overlay can remain at `frame_ready`; its later
retirement is reported separately and does not inflate usable-frame time. Every
next reload still waits for retirement and the fixed one-second quiescence.
Older protocol-2 observations remain raw diagnostic evidence, not qualifying
FO input-readiness samples. Fixed scene UUIDs must be observed at `world_ready`
and each `frame_ready`; arbitrary nearby entities cannot satisfy the proof.

Passing this FO report alone is insufficient for release: original cross-loader
15% startup/reload, companion workload, rendering/font correctness and memory
gates remain separately required.

## Reviewed runtime metadata normalization

`normalizationPolicy: "fo-runtime-metadata-v1"` is optional and limited to four
exact files. It never rewrites a frozen input or runtime output:

- `config/iris.properties` and
  `shaderpacks/ComplementaryUnbound_r5.7.1.zip.txt`: disregard only one recognized
  Java Properties date comment at the start, optionally after one comment line.
  Every property value, order, other comment and remaining byte stays comparable.
  A date-shaped line in a property continuation is rejected.
- `config/sodium-fingerprint.json`: permit only the reviewed schema `{v,s,u,p,t}`,
  version 1, three 128-character lowercase hex fields and a positive timestamp.
  The installation salt/identity/path hashes and timestamp are runtime metadata.
  New fields or versions require fresh review and are rejected.
- `config/sodium-options.json`: preserve every parsed value except an observed
  `notifications.has_seen_donation_prompt` reset from true to false when the
  installation fingerprint also changed. An unchanged prompt value is accepted;
  other changes, including false-to-true, quality or performance options, fail.

This follows Sodium's documented installation-copy detection behavior:
[Sodium configuration files](https://github.com/CaffeineMC/sodium/wiki/Configuration-File).
All starting `inputHashes` and context config hashes remain the exact frozen raw
byte hashes. `inputHashesAfter`, `rawInputsUnchanged` and `normalizationAudit`
retain raw output hashes, canonical hashes and every changed metadata diff.
`inputsUnchanged` means all effective benchmark inputs stayed equivalent under
that explicit policy; it must not be mistaken for byte equality when
`rawInputsUnchanged` is false. Analysis rejects missing audits, changed canonical
values, missing raw diffs, hidden unreviewed file changes or unknown rules.

Correctness captures set `resourceHashingEnabled:true` and include artifact ID
`texture_correctness`. The runner requires both together and supplies only the
owned `texture-pixels.jsonl` destination. Captures and their flags are rejected
from headline runs; no broad JVM-property escape hatch is provided.
