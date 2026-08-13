# Version-family matrix

Date: 2026-08-13

## Required exact release ledger

Registry requires 22 exact Mojang stable releases:

```text
1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6,
1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
26.1, 26.1.1, 26.1.2, 26.2
```

Registry declaration does not prove current runtime support. Current exact final-JAR result is `UNTESTED` for all 62 applicable release/loader cells after direct artifact changes.

## Source families

| Family | Publication anchor | Exact releases represented | Maturity | Java |
|---|---|---|---|---:|
| `mc1_20_1` | 1.20.1 | 1.20.1 | beta | 17 |
| `mc1_20_2` | 1.20.2 | 1.20.2-1.20.4 | beta | 17 |
| `mc1_20_3_4` | none | 1.20.3, 1.20.4 exact source nodes | beta | 17 |
| `mc1_20_5_6` | none | 1.20.5, 1.20.6 exact source nodes | beta | 21 |
| `mc1_21_0_1` | 1.21.1 | 1.20.5-1.21.1 publication range | beta | 21 |
| `mc1_21_2_3` | none | 1.21.2, 1.21.3 exact source nodes | beta | 21 |
| `mc1_21_4` | 1.21.4 | 1.21.2-1.21.4 publication range | beta | 21 |
| `mc1_21_5` | none | 1.21.5 exact source node | beta | 21 |
| `mc1_21_6` | none | 1.21.6 exact source node | beta | 21 |
| `mc1_21_7_8` | 1.21.8 | 1.21.5-1.21.8 publication range | beta | 21 |
| `mc1_21_9_11` | 1.21.11 | 1.21.9-1.21.11 publication range | beta | 21 |
| `mc26` | 26.1 | 26.1-26.2 publication range | stable | 25 |

Seven publication anchors resolve to 20 expected loader artifacts. Twelve source families and 19 registry build targets (direct source nodes) expand to 53 loader-specific direct distributions. Exact runtime expansion is a separate 62-cell ledger.

## Current graph status

- `gradle/minecraft-targets.json` is schema v2 and owns releases, loaders, source policies, Java levels, metadata, and publication ranges.
- Registry-derived settings create 19 registry build targets (direct source nodes) and 53 loader distributions from 12 source families.
- All 53 loader leaves are authoritative direct cells; public aggregate graph is direct-only.
- `validateStonecutterDirectContract` enforces direct-node ownership and rejects delegated public leaves.
- Nested Gradle remains a temporary parity rollback oracle.
- Native Stonecutter preprocessing is proven only for Fabric archive capture across 16 targets. Full all-53 build/structural/package parity, Java 17/21/25 proof, remap/refmap/JarJar proof, and general cross-loader preprocessing are not complete.

No intermediate release is inferred from metadata alone. A publication range becomes current-proven only when one current loader JAR passes every exact release it claims.

## Historical range evidence

Earlier checkpoints proved the declared ranges and 62 exact cells using older final JARs. Later direct-build changes invalidate that byte binding. Historical results remain useful regression evidence, but every current same-binary and exact-release runtime cell requires revalidation.
