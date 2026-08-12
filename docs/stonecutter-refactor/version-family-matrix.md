# Version-family matrix

## Required exact release ledger

The required ledger is 22 exact Mojang stable releases:

```text
1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6,
1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11,
26.1, 26.1.1, 26.1.2, 26.2
```

At baseline, only the following anchors exist: `1.20.1`, `1.21.1`, `1.21.4`, `1.21.8`, `1.21.11`, and `26.1-26.2`. The missing exact cells are not inferred from metadata ranges.

## Intended source families

| Family | Anchor | Exact releases | Maturity | Java |
|---|---|---|---|---:|
| `mc1_20_1` | 1.20.1 | 1.20.1 | beta | 17 |
| `mc1_20_2` | 1.20.2 | 1.20.2 | beta | 17 |
| `mc1_20_3_4` | 1.20.4 | 1.20.3, 1.20.4 | beta | 17 |
| `mc1_20_5_6` | 1.20.6 | 1.20.5, 1.20.6 | beta | 21 |
| `mc1_21_0_1` | 1.21.1 | 1.21, 1.21.1 | beta | 21 |
| `mc1_21_2_3` | 1.21.3 | 1.21.2, 1.21.3 | beta | 21 |
| `mc1_21_4` | 1.21.4 | 1.21.4 | beta | 21 |
| `mc1_21_5` | 1.21.5 | 1.21.5 | beta | 21 |
| `mc1_21_6` | 1.21.6 | 1.21.6 | beta | 21 |
| `mc1_21_7_8` | 1.21.8 | 1.21.7, 1.21.8 | beta | 21 |
| `mc1_21_9_11` | 1.21.11 | 1.21.9, 1.21.10, 1.21.11 | beta | 21 |
| `mc26` | 26.1 | 26.1, 26.1.1, 26.1.2, 26.2 | stable | 25 |

The registry is both the target design ledger and the source for the exact CI smoke matrix. A release row is accepted only after every loader declared by that row passes final-artifact startup, deterministic reload, semantic/resource evidence where supported, and clean exit.

## Registry status at the current checkpoint

`gradle/minecraft-targets.json` is schema v2 and `validateTargetRegistry`/`printResolvedMatrix` pass with all 22 mandatory exact rows. Every exact row now has an independent runtime result: 62 officially available loader cells pass the production smoke contract, including the repaired early NeoForge 1.20.2/1.20.3/1.20.4 route and the 26.1.1/26.1.2 interior stable-range cells. The registry currently keeps the six existing anchors as the public 17-artifact set; newly verified target rows are `planned-verified` until the same final binary is range-tested, so publication is not silently expanded. The 1.21.2 row remains Fabric + NeoForge because no official Forge line is available. No intermediate release is inferred from a metadata range.
