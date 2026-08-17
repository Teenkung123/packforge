# Fabric Sodium 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `fabric-sodium`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`
- Sodium `mc1.21-0.5.11` SHA-256: `3BF1783F9081D28067457734EFF52F4579B6B8378C075663784684F6F2F1AA37`
- Fixture SHA-256: `BF5C12AE7C847B762AA8C142134D918F90DFEA5EBDFBBC6B8C244E354D38F943`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `8CC3302D94117D9CDB5F7476796071EBC6F53D8A347AE56C03E67A875E836F95`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=false
```

The expected `SAFE_ORIGINAL_PATH` and `sodium:true:` markers were present.
The resolved-resource hash was stable across startup and all ten reloads; no
forbidden mixin marker appeared, and the client exited with code 0.

Evidence hashes: summary `5B39E485E6BE6F9469790AD94AB67661EF9910DAB090398C424F77D851B51E4D`;
raw log `201388046FBC608476972AD6CB28165FD7BBC543D090D0F7CDB8FD4ADC1E1603`;
provenance `505663A051A9510817CB7F1F86EBE5664A8FE8496BA4CFCD4F1235A00C69DAA8`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
