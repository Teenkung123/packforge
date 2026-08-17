# Font-heavy fixture rendering correction — 2026-08-16

## Finding

The supplied screenshot (`Screenshot 2026-08-16 184027.png`) showed the
Minecraft main-menu labels with opaque green/red/black blocks replacing their
leading uppercase glyphs (`ingleplayer`, `ultiplayer`, `inecraft Realms`,
`ptions`, and `uit Game`).  The matching 18:40:27 run was the Fabric
`font-heavy-resource-pack` smoke.  Its log had no crash or mixin failure; the
visual defect came from the fixture replacing `assets/minecraft/font/default.json`
with 32 one-character bitmap providers for `A`–`Z`.  The deterministic fixture
PNG pixels therefore became the glyphs for ordinary UI text.

## Correction

`scripts/Generate-CompatibilityFixtures.py` now preserves the exact 1.21.1
vanilla `default.json` references (`include/space`, filtered `include/default`,
and `include/unifont`) and appends the 32 fixture bitmap providers using only
private-use characters U+E000–U+E01F.  The fixture still exercises every
bitmap-provider load and the cache hook, but it no longer overrides normal
Minecraft text.  The self-test also freezes the vanilla references and private-
use character mapping.

## Focused verification

- `python scripts/Generate-CompatibilityFixtures.py --self-test` — PASS.
- Regenerated fixture: 17,851 bytes,
  SHA-256 `176F7F7DFBC8E30AAC03204A13D12C7CEDDBB9164A841B8C4E2B5E6B5D23DF40`.
- Representative command:

  ```text
  pwsh -NoProfile -File scripts/Run-Exact-ProductionMatrix.ps1 -ProfileId fabric-immediatelyfast -CompatibilityCacheRoot build/compatibility-profile-cache -ResultsRoot build/production-matrix/heavy-font-fabric-immediatelyfast-fixed-20260816 -ReloadCount 10
  ```

- Result: Fabric 1.21.1 PASS, 10 reloads, `heavyFixtureEvidence=11`,
  `cleanExit=true`, artifact SHA-256
  `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`.
- Every evidence marker reported
  `fontProviderAttempts=37 fontProviderSuccesses=37`; the corrected run log
  SHA-256 is `F9D53E2EF3AEB3D87CAAAD27656F9781D59B596CF093234050D5EF74E7A3CC9D`.
- No `ERROR`, `Exception`, or `Critical injection failure` occurred.  The
  existing `Shader rendertype_entity_translucent_emissive ... Sampler2`
  warning remains a non-fatal external shader diagnostic and is unrelated to
  the font fixture correction.

This is focused fixture/runtime evidence, not a claim that the full 62-cell
matrix has been rerun.

## Companion model-heavy check

The same corrected artifact also passed the model-heavy Fabric representative:

- Profile `fabric-modernfix`, ten reloads, `heavyFixtureEvidence=11`,
  `cleanExit=true`.
- Every evidence marker reported
  `fixtureModelLoads=256` and `status=PASS`.
- Run log SHA-256:
  `2804FC747DF495766CE6B2DCFDE9CB1B5F7E5A01273A30EF0CF647C7B3F139E6`.
- Fixture SHA-256:
  `5EFDEF343B1CFA81B690464BFC6147C9B5ABF6DB8CCF733382B00B672D6B34BE`.

## Companion mipmap-heavy check

The available NeoForge mipmap representative also passed:

- Profile `neoforge-embeddium`, ten reloads, `heavyFixtureEvidence=11`,
  `cleanExit=true`.
- Every evidence marker reported `highResolutionSprites=3`,
  `spriteDecodeCount=3142`, and `mipmapStageCount=14` with `status=PASS`.
  `mipmapOwnedCount=0` is expected for this NeoForge adapter; the high-resolution
  decode and mipmap-stage counters are the loader-independent fixture proof.
- Run log SHA-256:
  `EC1C5AB4687BBBF7EBFFE85957B17D72D64E20E18169D9F5203A3E1DADB40EDE`.
- Fixture SHA-256:
  `AAC284CF3F1E9CBD99DF72FB46388117AE02B2AD89F1BF17FC8B3E02DEE52D28`.
