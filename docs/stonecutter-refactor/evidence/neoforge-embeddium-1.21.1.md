# NeoForge Embeddium 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `neoforge-embeddium`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2`
- Embeddium `1.0.10+mc1.21.1` SHA-256: `5913A79896B9415AAE1B66AB404E48A5E6ED4856E2D95E0A71FA6BA66ADC71FB`
- Fixture SHA-256: `AAC284CF3F1E9CBD99DF72FB46388117AE02B2AD89F1BF17FC8B3E02DEE52D28`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The expected safe path, profile, and `embeddium:true:` marker were present. The
semantic hash was stable across startup and all ten reloads; heavy-fixture
evidence was retained, no forbidden mixin marker appeared, and the client
exited cleanly.

Evidence hashes: summary `871B5FD29790F7CC6D05D375F00B4EFBDCFD9CC06308430F79D68596106F58E5`;
raw log `2576DEA0451B6A373B5EAE8B0C261A62254CABC4539533523CF8BA9830B79ED7`;
provenance `495014896CB5741010FAAAEA0C59C5BB1B91B0F22850CEBD59FDD5FCC77D6A94`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
