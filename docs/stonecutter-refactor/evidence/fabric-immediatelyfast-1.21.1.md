# Fabric ImmediatelyFast 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `fabric-immediatelyfast`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`
- ImmediatelyFast `1.2.21+1.21.1-fabric` SHA-256: `B0C65E0C4FBD044C49F6BBF0BB5D3C3D43B3ABDF7156B5AC35408C2B2F8B1338`
- Fixture SHA-256: `FD6C98DE52484E90414D20CC08597E72D92A2E60804B2D45AEB277CE78EF4B7F`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `8CC3302D94117D9CDB5F7476796071EBC6F53D8A347AE56C03E67A875E836F95`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 heavyFixtureEvidence=11 scenario=repeat additionalMods=1 reloads=10 cleanExit=true controlledTermination=false
```

The expected `HOOK_PRESERVING_COALESCED_PATH`, profile, and
`immediatelyfast:true:` markers were present. The resolved-resource hash was
stable across startup and all ten reloads; no forbidden mixin marker appeared,
heavy-fixture evidence was retained, and the client exited with code 0.

Evidence hashes: summary `099CAC352D9FB3963D57155634AF63762768148E20C2C1A97CEE6E625227D71E`;
raw log `CD00106979BB84E8B6722C2C3A5BAB3DBD029E9A178123419001C2C6CDB391EA`;
provenance `1125ED2626591F92FD8B3ED4763E2DA4DD28AF3FFE9C57F38BB3336BBDCF15C7`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
