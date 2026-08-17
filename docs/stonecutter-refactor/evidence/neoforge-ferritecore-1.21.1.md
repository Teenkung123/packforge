# NeoForge FerriteCore 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `neoforge-ferritecore`
compatibility profile after ten controlled reloads and the declared cancellation
and failure scenario fixture.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2`
- FerriteCore `7.0.0-neoforge` SHA-256: `2BD7B29B5B5D2A2E1D1D4C4A4A8F7CFB86F8503FD9BA88C77A9E0ABB90343B24`
- Fixture SHA-256: `4FB19A4269C4DF6FADB57F313228A3B38B15DE32B7042F91DD709E792CC7227F`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2 resolvedResourceSha256=55DD06B698B6137A13B89330A263755DD06E4BD65B558E75BAD9695A3CCD4D5D scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=true
```

The expected safe path, profile, and `ferritecore:true:` marker were present.
Both declared fixture scenarios were materialized; the semantic hash remained
stable across the ten reloads, no forbidden mixin marker appeared, and the
client exited cleanly.

Evidence hashes: summary `7FDDC7FBE719A21CC2A935BB5A6D171CAC307C418216779FFDBA4A137AFE03BC`;
raw log `F4D60ABF85E09C1DB059D12E786473BC6E14AAF26EE8D355AFB7018185444F51`;
provenance `B9AFEAAAB0D5137C6FB68FA264784F83395AF50DA05B0459810D3C9ABA147157`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
