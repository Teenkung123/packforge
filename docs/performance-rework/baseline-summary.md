# Performance baseline summary

## Gate state

| Check | Result |
|---|---|
| Worktree at `4fafbef` | PASS, clean |
| `validateTargetRegistry` | PASS |
| Clean `mc26_1_to_26_2` Fabric/Forge/NeoForge build and tests | PASS |
| Primary artifact verification | PASS inside `buildTarget` |
| PackIndex deterministic semantic hash | PASS, baseline and indexed hashes identical |
| Runtime performance profiles | UNEXECUTED; Windows GUI automation backend unavailable |

The Forge 1.20.1 hotfix remains the rollback base. Its exact beta.2 Forge JAR passed final-atlas startup on Forge `47.0.0`, `47.4.20`, and `47.4.22`; automated F3+T completion remains open and is not described as passed.

## Deterministic ZIP fixture

| Property | Value |
|---|---|
| Path | `platform/fabric/build/mc26_1_to_26_2/benchmark/deterministic-large-pack.zip` |
| SHA-256 | `722e45e3cfedf4b5d9cf0761bb94e42ac7d860c70dc612d350485fd13da8aba3` |
| ZIP bytes | 5,057,823 |
| Central entries | 20,022 |
| Entry compressed bytes | 1,174,013 |
| Entry uncompressed bytes | 1,154,097 |
| Benchmark semantic hash | `c5d57e4819095cf193b43c68df49e94f904634acf22225541bfbbb6a13f1e547` |

The fixture includes normal and deliberately invalid namespace forms. Namespace validation remains adapter-owned; the neutral index must preserve names and ordering.

## PackIndex baseline samples

Five separate Gradle benchmark task invocations were run from the clean hotfix commit. Times are nanoseconds as emitted by `PackIndexBenchmark`.

| Sample | Index build | Baseline query median | Indexed query median | Reported improvement |
|---:|---:|---:|---:|---:|
| 1 | 31,251,000 | 79,806,700 | 1,875,700 | 97.65% |
| 2 | 31,987,300 | 104,772,000 | 2,078,400 | 98.02% |
| 3 | 32,070,000 | 83,876,400 | 1,793,900 | 97.86% |
| 4 | 29,632,800 | 83,437,300 | 2,297,400 | 97.25% |
| 5 | 29,682,200 | 82,713,400 | 1,666,500 | 97.99% |
| Median across invocations | **31,251,000** | **83,437,300** | **1,875,700** | **97.86%** |

These are microbenchmark observations for one deterministic fixture, not end-to-end reload claims. The current benchmark does not provide retained-heap evidence; the index phase must add representation/resource measurements before claiming its memory gate.

## Compact one-pass index candidate

Five separate candidate invocations used the same fixture, semantic hash, target, and Java runtime as the baseline. The representation is `IndexedEntry[]+int[]`; all 20,022 unique paths were resolved during the central-directory pass without a `ZipFile.getEntry` call. Duplicate-path fixtures retain first-entry semantics and perform one fallback lookup per duplicate path.

| Sample | Index build | Baseline query median | Indexed query median | Reported improvement |
|---:|---:|---:|---:|---:|
| 1 | 16,724,900 | 78,602,700 | 291,700 | 99.63% |
| 2 | 17,414,600 | 77,975,600 | 282,900 | 99.64% |
| 3 | 23,071,600 | 87,626,000 | 238,100 | 99.73% |
| 4 | 16,048,400 | 77,854,300 | 290,000 | 99.63% |
| 5 | 16,647,700 | 79,197,000 | 249,600 | 99.68% |
| Median across invocations | **16,724,900** | **78,602,700** | **282,900** | **99.64%** |

Median index construction improved by **46.5%** versus the recorded 31.251 ms baseline, exceeding the 15% phase gate. Semantic hashes matched in every invocation. Prefix and namespace caches are bounded to 64 entries / 262,144 cached ordinals and 32 entries respectively, and are disabled or cleared when their owning archive state is invalidated. Retained-heap evidence remains unmeasured, so no memory-reduction percentage is claimed.

## Known baseline limitations

- No cold/warm Minecraft runtime timing series was captured in this environment.
- No JFR allocation recording, class histogram, post-GC heap, native-image count, or ZIP-handle slope is yet available.
- Computer-use was explicitly retried, but Windows enumeration failed with `0x80070003`; no visual claim is based on that backend.
- Compatibility-mod profiles remain unexecuted until their exact installed profiles are available.
