# Forge ETF/EMF 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `forge-etf-emf`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `721AED7C58434123BAFA49374BCECFF52F3F2C10FAC47D3AFC844E39EEBBB750`
- Entity Texture Features `7.1-forge-1.21` SHA-256: `4BEF016B75C14DA84AF6453821978F9D779F9B31BA4FDDDDA449D7CBFAEFB927`
- Entity Model Features `3.2.4-forge-1.21` SHA-256: `A04EC7B7D65D89893FCE752FC0D7D3CE8D7B336D848CA94899019656440C8B1C`
- Fixture SHA-256: `2DDD7C015DC1E325CD2B452F0150BB6E1D36C250A047703CDB2105F00DE02E7D`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS Forge production smoke: version=1.21.1-forge-52.0.0 artifact=packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=721AED7C58434123BAFA49374BCECFF52F3F2C10FAC47D3AFC844E39EEBBB750 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=2 reloads=10 cleanExit=true controlledTermination=true
```

The expected safe path, profile, ETF, and EMF markers were present. The
semantic hash was stable across startup and all ten reloads; heavy-fixture
evidence was retained, no forbidden mixin marker appeared, and the client
exited cleanly.

Evidence hashes: summary `BB50F282B618E68DCF9D153E366729E927642821A8685DF3980BC6E8B86B1E5C`;
raw log `43EC84F332C0B5C8829E058D925F3855C11C3EE8001F4D6276386BF2D8DE9D93`;
provenance `37E693F772D90CE2B349F5885C5CC79F2A18C1385AF64F4360EBCB3E2C8B13E3`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
