# NeoForge ModernFix 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `neoforge-modernfix`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2`
- ModernFix `5.27.20+mc1.21.1` SHA-256: `6F367EA4002C39E3CFE27E0A0BE1008880A4B35A8482A78A437CFB9D8EE38273`
- Fixture SHA-256: `5EFDEF343B1CFA81B690464BFC6147C9B5ABF6DB8CCF733382B00B672D6B34BE`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The expected safe path, profile, and `modernfix:true:` marker were present. The
semantic hash was stable across startup and all ten reloads; heavy-fixture
evidence was retained, no forbidden mixin marker appeared, and the client
exited cleanly.

Evidence hashes: summary `C11185F3BE97EE3CFDD67A631ADAAC5E3CF23957CD0251C15D09787F7D837B1E`;
raw log `956E2534268D59D18391AC15B406B73AA44ADF6F10E759A7586D9B37568FC060`;
provenance `570C3F5371299D2341AA43068835A4156D5C976E36FD933BCFD0FF85D69E1ED7`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
