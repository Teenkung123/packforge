# Default-off promotion matrix

Every §16 performance candidate has a final decision. No candidate is promoted by build success or by the correctness smoke alone; all remain default-off until a controlled benchmark meets the assignment’s semantic, lifecycle, compatibility, and measurable-benefit gates.

| Candidate | Current default | Final verdict | Basis |
|---|---:|---|---|
| ZIP read pool | off | `SAFE_KEEP_DEFAULT_OFF` | Exact runtime matrix and Quick Pack 1.5.0 profile pass; no qualifying isolated performance gate was executed. |
| Font bitmap provider cache | off | `SAFE_KEEP_DEFAULT_OFF` | Exact runtime matrix passes; provider/epoch parity and a qualifying performance gate are not established. |
| Atlas mipmap parallelization | off | `SAFE_KEEP_DEFAULT_OFF` | Quick Pack 1.5.x owns this overlap; no independent promotion is permitted. |
| Atlas sprite decode batching | off | `SAFE_KEEP_DEFAULT_OFF` | Correctness smoke passes, but no qualifying native-close/performance gate was executed. |
| Model adaptive batching | off | `SAFE_KEEP_DEFAULT_OFF` | Exact runtime matrix passes; no controlled model-loader benchmark qualifies promotion. |
| Model duplicate parse cache | off | `SAFE_KEEP_DEFAULT_OFF` | Exact runtime matrix passes; pure-input/context-bypass and measurable-benefit gates are incomplete. |
| Startup executor tuning | off | `SAFE_KEEP_DEFAULT_OFF` | No controlled ModernFix/Smooth Boot coexistence and oversubscription gate. |
| Startup async data parsing | off | `SAFE_KEEP_DEFAULT_OFF` | No isolated ordering/failure-propagation benchmark qualifies promotion. |
| Startup async class scan | off | `SAFE_KEEP_DEFAULT_OFF` | No isolated loader-discovery/race-safety benchmark qualifies promotion. |
| Startup async font/atlas | off | `SAFE_KEEP_DEFAULT_OFF` | No render-publication benchmark qualifies promotion; Quick Pack overlap remains externally owned. |
| Atlas retry | off | `SAFE_KEEP_DEFAULT_OFF` | Explicit plan policy requires a separate correctness case before any default-on change. |

Timings, diagnostics, loading fade, and reload toast preferences are not performance candidates and remain outside this verdict table. Explicit user configuration values remain authoritative.
