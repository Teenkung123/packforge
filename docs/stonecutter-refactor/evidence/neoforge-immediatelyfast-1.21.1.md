# NeoForge ImmediatelyFast 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `neoforge-immediatelyfast`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2`
- ImmediatelyFast `1.6.11+1.21.1-neoforge` SHA-256: `336DF12F099D1A441A3E06850BEA86E9C2D0C8BC022D3D9A201870A201562A04`
- Fixture SHA-256: `FD6C98DE52484E90414D20CC08597E72D92A2E60804B2D45AEB277CE78EF4B7F`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The expected hook-preserving path, profile, and `immediatelyfast:true:` marker
were present. The semantic hash was stable across startup and all ten reloads;
heavy-fixture evidence was retained, no forbidden mixin marker appeared, and the
client exited cleanly.

Evidence hashes: summary `63B24CF52A37CA41A7CABFF1AED59711C121A6DC65BEEC685F5EA4034B85BE31`;
raw log `89D07E37997C310E9C68782E2A9D5AA225D89E772B42A3E4641B193356EC5F7B`;
provenance `30923037EEA0ABDA8FF06FB776889D7A669076EC8BBA77E7C2C4139B8AB941C6`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
