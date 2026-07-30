# Legacy Beta Release Notes

PackForge's exact 1.21.x and 1.20.1 artifacts remain beta builds. Capability promotion requires packaged runtime, semantic, and performance evidence; compilation alone is not sufficient.

## Delivered Reload Support

- Java-only ZIP central-directory indexing with exact and prefix lookups
- Per-archive index ownership and invalidation
- Failure caching with one contextual warning and vanilla fallback
- Optional bounded ZIP read pooling with close-safe Windows handle behavior
- Overlay prefixes, namespaces, duplicates, pack precedence, removal, and reordering semantics preserved by the exact-version mixins
- Reload lifecycle, pack-diff capture, listener prepare/apply telemetry, and resource hash reporting
- Readiness-gated percentage, elapsed-time, phase/detail text, native completion/failure toast, and fade-out-only control
- Ordered model parsing batches, adaptive sizes, reload-scoped duplicate source caching, timings, and Fabric model-hook fallback
- Asynchronous font-provider preselection on 1.21.x, reload-scoped reference-counted bitmap caching on every target, and font diagnostics
- Ordered atlas decode batches with source/decode/stitch/mipmap/upload timing only
- ImmediatelyFast pack-removal compatibility handling
- Searchable, typed Reload configuration UI through the resource-pack cog, Mod Menu, and loader-native config entry points
- Config schema v12 and `config/packforge.json` preserved
- A generated capability profile logged once at startup

Minecraft 1.21.x exposes all 21 Reload options. Minecraft 1.20.1 exposes 20 and omits font-provider preselection because that vanilla version has no conditional provider-selection preparation phase.

## Deliberate Exclusions

The entire Large Atlas Fixer and Startup Optimizer categories remain unavailable on legacy artifacts. Atlas cap, UV clamp, retry/recovery, parallel mipmap generation, experimental splitting, and all startup features are not advertised or injected. Their stored schema-v12 values are retained for compatibility.

Experimental atlas splitting remains config-only on every target and is not counted as delivered functionality.

## Promotion Gates

Each platform/version artifact is promoted independently only after it passes:

- exact metadata, Java, mixin, access-widener, duplicate-entry, and package checks
- title-screen boot, repeated fixture reloads, add/remove/reorder coverage, and clean exit
- immediate ZIP rename/delete on Windows after reload
- identical resolved-resource hashes with optimization disabled and enabled
- at least 15% faster median warm reload after one priming reload and five measured reloads
- no more than 5% cold-start regression across three fresh processes
- applicable Sodium/Embeddium, Iris/Oculus, ImmediatelyFast, FerriteCore, and SmoothBoot combinations

OptiFine remains unsupported.

The automated warm/cold/hash gate is implemented by `scripts/benchmark-client.sh` and `scripts/Test-ReloadBenchmark.ps1`. It loads the packaged artifact, keeps loader timing instrumentation active in the disabled baseline without enabling the index or ZIP pool, and compares hashes emitted from Minecraft's resolved fixture resources. The standalone index microbenchmark remains diagnostic evidence only.
