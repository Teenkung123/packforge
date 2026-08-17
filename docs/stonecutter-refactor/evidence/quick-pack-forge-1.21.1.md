# Quick Pack Forge 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the focused Phase 5 final-JAR run for the
exact Forge Quick Pack 1.5.0 profile. The raw matrix output remains under the
ignored `build/production-matrix/quick-pack-forge-current` directory.

## Inputs

- PackForge artifact: `packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `2E845C8BA537C20163DC8B4BEF7E5FBA99C1CEBA53CE226FA0D2E0A36C09DB9C`
- Quick Pack artifact: `quick-pack-forge-1.5.0+1.21.1-all.jar`
- Quick Pack SHA-256: `B3AAF726BDA99123A1E08CCB5F0D6982D1D3EAF1A9BB3EEBF63C2B5CA2807195`
- Fixture SHA-256: `BF5C12AE7C847B762AA8C142134D918F90DFEA5EBDFBBC6B8C244E354D38F943`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `185699B982DCC49EFE4FA84D672BF1124509D891A08D12C354E673AF18B9526B`

## Result

```text
PASS Forge production smoke: version=1.21.1-forge-52.0.0 artifact=packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=2E845C8BA537C20163DC8B4BEF7E5FBA99C1CEBA53CE226FA0D2E0A36C09DB9C resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The exact result record reports `status=PASS`, `exitCode=0`, ten reloads,
`cleanExit=true`, and `controlledTermination=true`. The resolved-resource
hash was stable across startup and all ten reloads. The required Quick Pack
ownership marker was present:

```text
PackForge compatibility profile: id=forge-quick-pack
quick-pack:true:
PackForge Quick Pack compatibility: status=MODULE_HANDOFF
```

No forbidden mixin markers were present. The raw log SHA-256 is
`9DCB6A4EEF3B3B667512FD5D903D24432215EF5020B0F62319A9D36DF85E5DF8`.

This is focused interoperability evidence only; it does not prove the full
62-cell base matrix, heavy fixtures, or performance gates.
