# Benchmark methodology

## PackIndex microbenchmark

`PackIndexBenchmark` regenerates the deterministic ZIP before each Java invocation, opens it once, measures one index build, then compares repeated full central-directory scans with indexed prefix/namespace queries.

Within each invocation:

1. create a 20,022-entry deterministic fixture;
2. measure one `PackIndex.build` call;
3. run five untimed warm-up pairs;
4. run five measured pairs, each containing 100 repetitions;
5. sort each five-value series and report its median;
6. hash resolved path/byte output through both vanilla `ZipFile.getEntry` and the index;
7. fail if hashes differ;
8. consume counts through a volatile blackhole.

The baseline report additionally uses five separate Gradle/Java invocations and reports the median of each emitted metric. This reduces reliance on one process start but is not a JMH confidence interval. Build time is a single cold index construction per Java invocation and includes no retained-heap measurement.

## Required index comparison

The final index comparison must keep the same fixture SHA-256 and semantic hash. Report:

- all individual build/query samples;
- median and worst build time;
- baseline and indexed query medians;
- central enumeration count;
- duplicate canonicalization count;
- representation fields and bounded-cache limits;
- a retained-memory estimate or measured heap evidence, clearly labelled;
- exact command, commit, Java, target, and fixture identity.

Acceptance is at least 15% median build improvement, or a stronger retained-memory gain with no more than 5% build-time regression; target retained-memory reduction is 20%. Exact lookup may not regress by more than 5%, prefix lookup may not regress, and semantic/duplicate parity must match.

The candidate series was executed with:

```powershell
1..5 | ForEach-Object { .\gradlew.bat benchmarkPackIndex -Ppackforge_target=mc26_1_to_26_2 --no-daemon --console=plain }
```

Gradle required execution outside the filesystem sandbox because its single-use daemon opens a local loopback connection. This changes process isolation only; target, fixture, source tree, and benchmark implementation were unchanged.

## Minecraft runtime methodology

When GUI automation is available, use one priming reload followed by at least five measured reloads in the same process; report median and p95/max. Cold results require at least three fresh processes. Keep pack, options, loader, Java, compatibility mods, and feature profile identical. Record the deterministic resource hash and exact artifact checksum with every series.

Runtime profiles that were not executed remain `UNEXECUTED`; microbenchmark percentages are never presented as end-to-end reload gains.

## Resource evidence

Use JFR allocation data, class histograms, post-GC test-only observations, RSS/native-image proxies, ZIP handles, and repeated-reload slopes where available. Production code must not invoke `System.gc()`. A missing resource measurement is recorded as a gap rather than estimated from timing.
