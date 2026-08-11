# Default-off promotion baseline

No performance candidate has a promotion verdict from the required semantic, lifecycle, compatibility, and performance gates at baseline. Unexecuted profiles are recorded as `UNTESTED`.

| Candidate | Current default | Baseline verdict | Required evidence |
|---|---:|---|---|
| ZIP read pool | off | `UNTESTED` | duplicate entries, stale archive, Windows handle slope, Quick Pack |
| Font bitmap provider cache | off | `UNTESTED` | epoch/provider identity/close/glyph parity, ImmediatelyFast/Quick Pack |
| Atlas mipmap parallelization | off | `UNTESTED` | bounded scheduling, Quick Pack ownership |
| Atlas sprite decode batching | off | `UNTESTED` | order, cancellation, native close |
| Model adaptive batching | off | `UNTESTED` | custom model-loader hooks and performance |
| Model duplicate parse cache | off | `UNTESTED` | pure-input proof and contextual-loader bypass |
| Startup executor tuning | off | `UNTESTED` | ModernFix/Smooth Boot coexistence and oversubscription |
| Startup async data parsing | off | `UNTESTED` | ordering and failure propagation |
| Startup async class scan | off | `UNTESTED` | loader discovery/race safety |
| Startup async font/atlas | off | `UNTESTED` | render publication and Quick Pack ownership |
| Atlas retry | off | `SAFE_KEEP_DEFAULT_OFF` by plan policy | separate correctness case required before promotion |

Timings, diagnostics, and fade/toast preferences are not performance candidates and remain outside promotion decisions.
