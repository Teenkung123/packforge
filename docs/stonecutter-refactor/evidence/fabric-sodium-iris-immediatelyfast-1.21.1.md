# Fabric Sodium/Iris/ImmediatelyFast focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR high-risk Fabric profile
run. The raw matrix output remains under the ignored
`build/production-matrix/high-risk-fabric-sodium-iris-immediatelyfast-retry`
directory.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`
- Sodium `mc1.21-0.5.11` SHA-256: `3BF1783F9081D28067457734EFF52F4579B6B8378C075663784684F6F2F1AA37`
- Iris `1.7.3+1.21` SHA-256: `BD029EC25CB3D64D40B649702A898B7CF7E59C75309C0ABA74C1F14D9AF62E54`
- ImmediatelyFast `1.2.21+1.21.1-fabric` SHA-256: `B0C65E0C4FBD044C49F6BBF0BB5D3C3D43B3ABDF7156B5AC35408C2B2F8B1338`
- Fixture SHA-256: `A1B6B087993E1A807AE95194DC8F4979B217F0C0D34E3D64DF98CE3AF92DE9AD`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `8CC3302D94117D9CDB5F7476796071EBC6F53D8A347AE56C03E67A875E836F95`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=3 reloads=10 cleanExit=true controlledTermination=false
```

The final log contained the exact path and ownership markers:

```text
PackForge compatibility path: HOOK_PRESERVING_COALESCED_PATH
PackForge compatibility profile: id=fabric-sodium-iris-immediatelyfast
immediatelyfast:true:
iris:true:
sodium:true:
```

The semantic hash was stable across startup and all ten reloads. MixinExtras
initialized at version 0.5.4, no forbidden mixin markers were present, and the
Minecraft process exited with code 0. The raw log SHA-256 is
`FF5B35878E0065F3096764D788BE2B6AFD1D4BFBF4F09D7F40DC83DA5051B3F8`.

This is focused high-risk interoperability evidence; it does not prove the
full 62-cell matrix, heavy fixtures, or performance gates.
