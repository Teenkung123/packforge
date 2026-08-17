# Default-off candidate dispositions

Machine-readable source: [`gradle/default-off-candidates.json`](../../gradle/default-off-candidates.json). Static guard: [`scripts/Validate-DefaultOffCandidateCatalog.ps1`](../../scripts/Validate-DefaultOffCandidateCatalog.ps1).

## ZIP pool promotion checkpoint (2026-08-17)

`loaderZipPoolEnabled` is now the only candidate promoted to default-on. The promotion is intentionally independent: the font bitmap cache and sprite decode batching remain off because their three-way combination previously regressed against baseline.

The ZIP pool remains PackForge-owned but now requires the PackForge archive index to be effective. Quick Pack ownership disables the index and therefore suppresses the dependent pool without claiming that Quick Pack implements ZIP pooling. Existing explicit `false` values are preserved; configurations missing the field receive the promoted default.

Evidence: [`evidence/zip-pool-default-promotion-2026-08-17.md`](evidence/zip-pool-default-promotion-2026-08-17.md).

`FAILED_WITH_REASON` below means source does not currently implement or reach the candidate behavior required by the assignment. It does not mean a runtime test was run and failed.

| Candidate | Configured default | Effective default | Implementation state | Disposition | Decisive reason |
|---|---:|---:|---|---|---|
| ZIP read pool | **on** | **on when PackForge index is active** | `IMPLEMENTED_UNVERIFIED` | `PROMOTED_DEFAULT_ON` | Isolated final-JAR evidence preserved the semantic hash, completed ten reloads, exited cleanly, and improved median reload time from 632 ms to 590 ms; bounded lifecycle tests close all auxiliary handles and policy fails closed when the PackForge index is inactive. |
| Font bitmap provider cache | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Provider parity, retained-heap lifecycle slope, and qualifying performance evidence remain incomplete; the prior three-feature combination regressed. |
| Atlas mipmap parallelization | off | off | `NOT_STARTED` | `FAILED_WITH_REASON` | Policy/UI flag exists, but no production consumer schedules parallel mip generation. |
| Sprite decode batching | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Native-image cleanup, cancellation, ordering, and qualifying independent performance evidence remain incomplete; the prior three-feature combination regressed. |
| Model adaptive batching | off | off | `UNAVAILABLE` | `UNAVAILABLE` | The direct model optimizer was removed because no production caller exists. |
| Model duplicate parse cache | off | off | `UNAVAILABLE` | `UNAVAILABLE` | The uncalled duplicate-model cache was removed with the direct optimizer. |
| Startup executor tuning | on | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Child option defaults on, but parent `startupOptimizerEnabled=false` keeps it effectively off; oversubscription and startup-performance evidence remain incomplete. |
| Startup async data parsing | off | off | `UNAVAILABLE` | `UNAVAILABLE` | The serialized compatibility flag has no functional startup parser. |
| Startup async class scan | off | off | `UNAVAILABLE` | `UNAVAILABLE` | The serialized compatibility flag has no loader-discovery implementation. |
| Startup async font/atlas | off | off | `UNAVAILABLE` | `UNAVAILABLE` | The serialized compatibility flag has no independent startup publication path. |
| Atlas retry | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Recovery behavior remains opt-in; shader and retry failure semantics require a separate promotion decision. |

## Fail-closed rules

The validator freezes exactly 11 candidate IDs, their config-key/parent-guard mappings, and six Phase J gates. It also verifies:

- catalog configured defaults match `PackForgeConfig.Cfg` source assignments;
- effective defaults follow declared parent guards;
- every source evidence path exists inside the repository;
- keep-off/failed candidates stay effectively off and list missing evidence;
- a `PASS` gate requires immutable checked-in evidence with matching SHA-256;
- `PROMOTED_DEFAULT_ON` requires configured/effective defaults on plus all six gates `PASS`.

Focused verification:

```powershell
.\scripts\Validate-DefaultOffCandidateCatalog.ps1 -SelfTest
```

Expected result after this checkpoint: baseline accepted, invalid mutations rejected, 11 frozen candidates, **1 promoted default**.
