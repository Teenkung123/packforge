# Fabric Iris 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `fabric-iris`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E`
- Iris `1.7.3+1.21` SHA-256: `BD029EC25CB3D64D40B649702A898B7CF7E59C75309C0ABA74C1F14D9AF62E54`
- Sodium `mc1.21-0.5.11` SHA-256: `3BF1783F9081D28067457734EFF52F4579B6B8378C075663784684F6F2F1AA37`
- Fixture SHA-256: `A1B6B087993E1A807AE95194DC8F4979B217F0C0D34E3D64DF98CE3AF92DE9AD`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `8CC3302D94117D9CDB5F7476796071EBC6F53D8A347AE56C03E67A875E836F95`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=1E8DB5D5AECF350066B34F159CD123A040380EA093C8CC5AF8CC00F90FCA1A6E resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=2 reloads=10 cleanExit=true controlledTermination=false
```

The expected `SAFE_ORIGINAL_PATH`, `iris:true:`, and `sodium:true:` markers
were present. The resolved-resource hash was stable across startup and all
ten reloads; no forbidden mixin marker appeared, and the client exited with
code 0.

Evidence hashes: summary `C995F8B488CF028C26BAAB3A560DB74A1329EF55284B25D415691492721A932E`;
raw log `ABAE7032A59986145C122EF706B4A4FF4BFD10B6687C30D92427B71E716EFA24`;
provenance `27F7B806FD133B421FD6392CE30127E98E9F9CFC323F04ABFB20D3C43F76E665`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
