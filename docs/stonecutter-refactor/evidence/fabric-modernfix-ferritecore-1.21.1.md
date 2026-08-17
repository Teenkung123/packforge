# Fabric ModernFix/FerriteCore 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR
`fabric-modernfix-ferritecore` compatibility profile after ten controlled
reloads and the declared cancellation/failure fixture.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`
- ModernFix `5.20.0+mc1.21.1` SHA-256: `BE2F6A6033A6CE066072EB0B5A8FFF95D75CB9E7716AC195C2058D3E976C0F78`
- FerriteCore `7.0.3-fabric` SHA-256: `98C3AB1D5AAB8F14B5D082D3F1A467727FD335F02D124555C94339761C4C18F2`
- Fixture SHA-256: `4FB19A4269C4DF6FADB57F313228A3B38B15DE32B7042F91DD709E792CC7227F`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS Fabric production smoke: minecraft=1.21.1 version=1.21.1-fabric-0.15.11 target=mc1_21_1 artifact=packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar sha256=018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42 resolvedResourceSha256=55DD06B698B6137A13B89330A263755DD06E4BD65B558E75BAD9695A3CCD4D5D scenario=repeat additionalMods=2 reloads=10 cleanExit=true controlledTermination=false
```

The expected safe path, profile, ModernFix, and FerriteCore markers were
present. Both declared fixture scenarios were materialized; the semantic hash
was stable across startup and all ten reloads, no forbidden mixin marker
appeared, and the client exited cleanly.

Evidence hashes: summary `0DE0695D64AD97066612BDEF2DEA62FA606F509D1CB00D65B1543D5745D3FC3D`;
raw log `2733839313BCEDED59E90B1254B407486185E779664A3DD8AF2EC3687A776414`;
provenance `C7DB403057C2A88CB64F038BBB4372E690DC025BF3855AEF2F56FBABEECDDCF0`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
