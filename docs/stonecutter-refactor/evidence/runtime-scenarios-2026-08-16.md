# Runtime cancellation/failure/retry evidence — 2026-08-16

The exact production matrix now exposes a validated `-RuntimeSmokeScenario`
selector and includes it in the evidence fingerprint, result record, PASS-line
contract, resume validation, and summary.  Repeat mode remains the default.

All four selectors were run against the exact Fabric 1.21.1 artifact
`packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar` (SHA-256
`018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`) with ten
requested reloads and the corrected font-heavy fixture.  Each run reached
`cleanExit=true`, retained the startup/recovery resource hash, and emitted
`scenarioEvidence=true`.

| Scenario | Lifecycle result | Log SHA-256 |
| --- | --- | --- |
| `cancel-in-flight` | 2 attempts; 1 cancellation; recovery PASS; context/future/overlay cleared | `8BFC345EBCE55F94FD68B9F04B600CCAB25339F5E85680A1D254FDE3149BE12D` |
| `forced-resource-failure` | 2 attempts; 1 injected failure; recovery PASS; context/future/overlay cleared | `CE37DF1EADB8098EB38726240B66C9CE66189F20398461A1708BD20E00460ABB` |
| `retry-success` | 2 attempts; 1 injected failure; recovery PASS; context/future/overlay cleared | `72503F15FBDAEEF3042D1915A46C56C167D21214F6D798C73F6AD80F66248D82` |
| `retry-exhaustion` | 3 attempts; 2 injected failures; recovery PASS; context/future/overlay cleared | `A285C8B3817D781BF06062AD43B650276C1FFAB7F7C91584DA2AEB68DDF43F24` |

The manifest’s explicit `reload-cancellation-failure` fixture is the high-entry
resource-pressure pack.  Its two required lifecycle checks were also run on
the available `fabric-ferritecore` profile:

| High-entry scenario | Lifecycle result | Log SHA-256 |
| --- | --- | --- |
| `cancel-in-flight` | 2 attempts; 1 cancellation; recovery PASS; context/future/overlay cleared | `52A6A4455F4B4B31959CE28591D17792B9E16E1194CBF831E21F668865967CCA` |
| `forced-resource-failure` | 2 attempts; 1 injected failure; recovery PASS; context/future/overlay cleared | `5DDA9D79EB55F6C988156530CBD161B9E17A3432653CA0CC89B0F25D5C57B9DC` |

Focused structural checks also pass:

- `Run-Exact-ProductionMatrix.ps1 -SelfTestResumeEvidence`
- `Test-ExactProductionMatrixProfiles.ps1`

These are focused scenario cells, not a claim that the current 62-cell matrix
has been rerun.
