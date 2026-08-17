# Fabric ModernFix 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `fabric-modernfix`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`
- ModernFix `5.20.0+mc1.21.1` SHA-256: `BE2F6A6033A6CE066072EB0B5A8FFF95D75CB9E7716AC195C2058D3E976C0F78`
- Fixture SHA-256: `5EFDEF343B1CFA81B690464BFC6147C9B5ABF6DB8CCF733382B00B672D6B34BE`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `8CC3302D94117D9CDB5F7476796071EBC6F53D8A347AE56C03E67A875E836F95`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=false
```

The expected `SAFE_ORIGINAL_PATH`, profile, and `modernfix:true:` markers were
present. The resolved-resource hash was stable across startup and all ten
reloads; no forbidden mixin marker appeared, heavy-fixture evidence was
retained, and the client exited with code 0.

Evidence hashes: summary `C44EB2A61F79A23C442D961FD37C68B72C8714738584154B4D842872212C56AA`;
raw log `6A0A775F303344C1451C970D46CCA61CBDF4128A8E2D3B51FC0AAD48742B21BD`;
provenance `DF1E03D910F59D422B0B388E1EEF86A0E033558F4230EA1BAC7E16DDBA7CE136`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
