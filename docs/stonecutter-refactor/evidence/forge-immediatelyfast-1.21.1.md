# Forge ImmediatelyFast 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `forge-immediatelyfast`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `721AED7C58434123BAFA49374BCECFF52F3F2C10FAC47D3AFC844E39EEBBB750`
- ImmediatelyFast `1.6.11+1.21.1-forge` SHA-256: `D423AE4373AAE66E545DA8F2742012CE4644FEDFBB0DC6C6036BF2369890A05C`
- Fixture SHA-256: `FD6C98DE52484E90414D20CC08597E72D92A2E60804B2D45AEB277CE78EF4B7F`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS Forge production smoke: version=1.21.1-forge-52.0.0 artifact=packforge-forge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=721AED7C58434123BAFA49374BCECFF52F3F2C10FAC47D3AFC844E39EEBBB750 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The expected hook-preserving path, profile, and `immediatelyfast:true:` marker
were present. The semantic hash was stable across startup and all ten reloads;
heavy-fixture evidence was retained, no forbidden mixin marker appeared, and the
client exited cleanly.

Evidence hashes: summary `4CA7DCDC57AC0D75164BFCE7ABE6BA87F8989BB215C838EBF3957C0552E4A80E`;
raw log `20595DF53E99C7F9AAEFCDB471C365D9A76272DE76AB26B94919BF62EB4BE23E`;
provenance `A5318D2B9E21E52C2B71D17C3E209929A90062E601365025DBFB3FBBC14C0A83`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
