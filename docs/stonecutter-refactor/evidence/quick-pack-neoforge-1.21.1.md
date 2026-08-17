# Quick Pack NeoForge 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the focused Phase 5 final-JAR run for the
exact NeoForge Quick Pack 1.5.0 profile. The raw matrix output remains under
the ignored `build/production-matrix/quick-pack-neoforge-current` directory.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `5C2A316D7D6E0E01A76C9563BD0FB92A9637C27CBE09062DAF009EB62AE8B28F`
- Quick Pack artifact: `quick-pack-neoforge-1.5.0+1.21.1.jar`
- Quick Pack SHA-256: `6914B74F9374617B0019096E30FDB1EC963752DA6D855FE7F8E43762363E3560`
- Fixture SHA-256: `BF5C12AE7C847B762AA8C142134D918F90DFEA5EBDFBBC6B8C244E354D38F943`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `185699B982DCC49EFE4FA84D672BF1124509D891A08D12C354E673AF18B9526B`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=5C2A316D7D6E0E01A76C9563BD0FB92A9637C27CBE09062DAF009EB62AE8B28F resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The exact result record reports `status=PASS`, `exitCode=0`, ten reloads,
`cleanExit=true`, and `controlledTermination=true`. The resolved-resource
hash was stable across startup and all ten reloads. The required Quick Pack
ownership marker was present:

```text
PackForge compatibility profile: id=neoforge-quick-pack
quick-pack:true:
PackForge Quick Pack compatibility: status=MODULE_HANDOFF
```

No forbidden mixin markers were present. The raw log SHA-256 is
`31844F6DA6B8DDEC0786AF4D4674F7F9C06E6B3243A24FE3CB5D7EE5BABF2BAC`.

This is focused interoperability evidence only; it does not prove the full
62-cell base matrix, heavy fixtures, or performance gates.
