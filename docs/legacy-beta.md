# Legacy Beta Release Notes

PackForge's exact 1.21.x and 1.20.1 artifacts are intentionally core-first beta builds. They are not promoted as stable parity releases merely because they compile.

## Delivered In Beta 1

- Java-only ZIP central-directory indexing with exact and prefix lookups
- Per-archive index ownership and invalidation
- Failure caching with one contextual warning and vanilla fallback
- Optional bounded ZIP read pooling with close-safe Windows handle behavior
- Overlay prefixes, namespaces, duplicates, pack precedence, removal, and reordering semantics preserved by the exact-version mixins
- Reload timing counters
- Config schema v12 and `config/packforge.json` preserved
- A generated capability profile logged once at startup

## Safe Beta Exceptions

The following 26.x client features are not yet active on legacy beta artifacts: config GUI integration, loading UI/toasts, atlas cap/retry/mipmap/decode/UV behavior, model batching, font caching, shader compatibility, and startup optimization. Their stored config values are retained. PackForge does not inject a partial substitute; vanilla behavior remains active, and the resource-pack ZIP acceleration path is independent of these exceptions.

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
