# Default-off candidate dispositions

Machine-readable source: [`gradle/default-off-candidates.json`](../../gradle/default-off-candidates.json). Static guard: [`scripts/Validate-DefaultOffCandidateCatalog.ps1`](../../scripts/Validate-DefaultOffCandidateCatalog.ps1).

This checkpoint is a conservative source/default audit, not runtime or benchmark proof. No Phase J final-JAR profile, repeated-reload lifecycle run, or qualifying multi-pack benchmark was executed here. Accordingly, every runtime gate in the catalog is `NOT_RUN` and no default is promoted.

`FAILED_WITH_REASON` below means source does not currently implement or reach the candidate behavior required by the assignment. It does not mean a runtime test was run and failed.

| Candidate | Configured default | Effective default | Implementation state | Disposition | Decisive reason |
|---|---:|---:|---|---|---|
| ZIP read pool | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Bounded fallback/close paths exist; qualifying multi-pack performance, handle-slope, final-JAR, and Quick Pack evidence do not. |
| Font bitmap provider cache | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Epoch/ref-count/close code exists; provider parity, lifecycle slope, final-JAR, ImmediatelyFast, and performance evidence do not. |
| Atlas mipmap parallelization | off | off | `NOT_STARTED` | `FAILED_WITH_REASON` | Policy/UI flag exists, but no production consumer schedules parallel mip generation. |
| Sprite decode batching | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Decode adapters exist; Minecraft-native cleanup, cancellation, ordering, final-JAR, compatibility, and performance gates are unrun. |
| Model adaptive batching | off | off | `PARTIAL` | `FAILED_WITH_REASON` | Planning code exists, but `ModelParseOptimizer.load`/`loadBlockModels` has no production caller; client initialization only resets state. |
| Model duplicate parse cache | off | off | `PARTIAL` | `FAILED_WITH_REASON` | Cache code exists inside the same unreachable optimizer path; pure-context and custom-loader bypass are unproved. |
| Startup executor tuning | on | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Child option defaults on, but parent `startupOptimizerEnabled=false` keeps it effectively off; oversubscription and coexistence gates are unrun. |
| Startup async data parsing | off | off | `PARTIAL` | `FAILED_WITH_REASON` | Flag logs and aliases reload-side model flags; no independent startup parsing task exists. |
| Startup async class scan | off | off | `PARTIAL` | `FAILED_WITH_REASON` | Current task is `mode=observed-only` telemetry, not loader discovery optimization. |
| Startup async font/atlas | off | off | `PARTIAL` | `FAILED_WITH_REASON` | Flag logs and aliases reload-side options; no independent startup task or render-thread publication path exists. |
| Atlas retry | off | off | `IMPLEMENTED_UNVERIFIED` | `SAFE_KEEP_DEFAULT_OFF` | Recovery path and shader guard exist, but retry/failure cleanup and final-JAR compatibility cases are incomplete; plan normally keeps recovery opt-in. |

## Fail-closed rules

Validator freezes exactly 11 candidate IDs, their config-key/parent-guard mappings, and six Phase J gates. It also verifies:

- catalog configured defaults match `PackForgeConfig.Cfg` source assignments;
- effective defaults follow declared parent guards;
- every source evidence path exists inside repository;
- keep-off/failed candidates stay effectively off and list missing evidence;
- a `PASS` gate requires immutable checked-in evidence with matching SHA-256;
- `PROMOTED_DEFAULT_ON` requires configured/effective defaults on plus all six gates `PASS`.

Focused static verification:

```powershell
.\scripts\Validate-DefaultOffCandidateCatalog.ps1 -SelfTest
```

Result: `PASS`; baseline accepted, 14 invalid mutations rejected, 11 frozen candidates, 0 promoted defaults.

Timings, diagnostics, loading fade, reload toast, and UI preferences are user-controlled behavior, not Phase J performance candidates. Explicit existing config values remain preserved; this checkpoint changes no production default.
