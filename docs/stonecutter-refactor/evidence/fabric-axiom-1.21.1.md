# Fabric Axiom 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `fabric-axiom`
compatibility profile after ten controlled reloads and the model-heavy fixture.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`
- Axiom `5.4.2` SHA-256: `ECB14B29E7EB4CDC5C1F299782D32A2C26202292A8A9B4060A9104526E6D7267`
- Fabric API `0.116.15+1.21.1` SHA-256: `A61A10F730AB8AA45FF42486EE65699E6E51C28A0C168DEB161E7D4029473AA3`
- Fixture SHA-256: `5EFDEF343B1CFA81B690464BFC6147C9B5ABF6DB8CCF733382B00B672D6B34BE`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=2 reloads=10 cleanExit=true controlledTermination=false
```

The expected `SAFE_ORIGINAL_PATH`, profile, `axiom:true:`, and
`fabric-api:true:` markers were present. The repaired model-load instrumentation
retained heavy-fixture evidence (`fixtureModelLoads=1`) while preserving the
platform model loader. The resolved-resource hash was stable across startup and
all ten reloads; no forbidden mixin marker appeared, and the client exited with
code 0.

Evidence hashes: summary `3C6BFB70D9D86A413AEB30FD373E60AF45AB00E8D07F2AD468861FE6C72B83EA`;
raw log `C7733B2F36A67DBDA8638E001CEA5CC8D2EACF943206C3CB37E56D2C0DA3D185`;
provenance `2E0C25139B2D76EF9D2739BB5981B87263F515FD9CD622165FBED95ADF609343`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
