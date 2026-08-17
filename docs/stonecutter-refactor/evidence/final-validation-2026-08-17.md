# PackForge 1.4 final-byte validation evidence

Date: 2026-08-17
Branch: `1.4`

## Final artifact and matrix identity

- Registry-derived publication artifacts: `20`
- Release manifest SHA-256: `D8F4A54AF5BBB51D146B8077AA65AEBC8E1AFE345E8CED18506FF2FC320F4241`
- Current matrix summary: `build/production-matrix/final-62-20260817/summary.json`
- Summary SHA-256: `842D4BA9C2DCF3A4FC0318E837CF821C10CE78BA74DA628087138D90F660DB7D`
- Results SHA-256: `A8CDEF15FDD62E69FE3CC8FCFB4355FF461099C45D83663AED27C8A0B309E7ED`
- Exact runtime cells: `62/62 PASS`
- Reloads per cell: `10`
- Clean exits: `62/62`
- Resolved-resource hashes: one stable digest across the matrix,
  `80B4CD91224732DD5864132F5D5F39988AE0BBAC78F79CE753232342281AC632`

The matrix was executed in four-cell waves. Parallel waves used isolated
result roots and were merged by newest cell record before the aggregate resume
validation. Fabric cells sharing one client root were run sequentially to
avoid client-cache contention.

## Representative current-byte lifecycle evidence

- Fabric `mc1_21_1` + FerriteCore: cancellation, forced failure, retry success,
  and retry exhaustion all PASS with `scenarioEvidence=true`, `cleanExit=true`,
  and artifact SHA-256 `10D34BEAA21DC656341394C58ADC2D5701E41BC73F827A6E75D694A4A087C347`.
- Forge `1.20.2`: retry success PASS; artifact SHA-256
  `3B48FEFC767BCFF1B03DC35DA941E9A1CF3D2529F2425B179480623D2D4B5CF8`.
- NeoForge `1.21.1`: retry success PASS; artifact SHA-256
  `D5BC706654ACED0F99F88C49761CEF5D9812E908D9DE1309F1D8EFCD81FAA15B`.
- Forge `26.2`: retry success PASS; artifact SHA-256
  `8FA6BADDFCC7C5DCA16BF681E9783FE8044C8B624501C4C286AB75B0D862421B`.

An injected retry-exhaustion run with the ImmediatelyFast Fabric profile did
not reach readiness, while the same scenario passed with FerriteCore. Normal
ImmediatelyFast and Quick Pack repeat profiles pass; the injected-failure
interaction remains a documented profile-specific limitation rather than a
matrix PASS claim.

## Structural gates

- `verifyAllArtifacts`, `verifyExistingArtifacts`, and `verifyReleaseManifest`
  PASS.
- All loaders contain exactly one expected `0.5.4:slim` MixinExtras artifact;
  duplicate ZIP entries are absent.
- PackForge icon remains `128x128`, `10,607` bytes.
- Current source metrics: `216` production files, `16,731` LOC,
  `2,453` ledger-adjusted reduction, `915` LOC Phase F shortfall; the metric
  gate remains `IMPLEMENTED_UNVERIFIED`.

## Still-unexecuted gates

Live UI route interaction and four-mode Vanilla/Quick Pack/PackForge/combined
performance comparison remain unverified. The existing smoke harness has no
neutral launcher mode or UI route automation; no result below claims those
gates.
