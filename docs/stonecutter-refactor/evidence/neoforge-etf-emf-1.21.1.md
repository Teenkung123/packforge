# NeoForge ETF/EMF 1.21.1 focused evidence

Checked: 2026-08-16

This immutable summary records the current final-JAR `neoforge-etf-emf`
compatibility profile after ten controlled reloads.

## Inputs

- PackForge artifact: `packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar`
- PackForge artifact SHA-256: `DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2`
- Entity Texture Features `7.1-neoforge-1.21` SHA-256: `B2E396543C678B8378A2D5557A0CD3D24790364BFF7E96EDAF17921112D6176B`
- Entity Model Features `3.2.4-neoforge-1.21` SHA-256: `84C22F2BE06EE6BFAB265A443E075225878BBA9F408843030DC6DE3152E9DEDB`
- Fixture SHA-256: `2DDD7C015DC1E325CD2B452F0150BB6E1D36C250A047703CDB2105F00DE02E7D`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

```text
PASS NeoForge production smoke: version=neoforge-21.1.1 artifact=packforge-neoforge-1.4-beta.2-mc1.20.6-1.21.1.jar sha256=DE3E95B7B62F77536BBB008E77C2A5E48B8AC3A2B6463F5C8182320376DF6DA2 resolvedResourceSha256=71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4 scenario=repeat additionalMods=2 reloads=10 cleanExit=true controlledTermination=true
```

The expected safe path, profile, ETF, and EMF markers were present. The
semantic hash was stable across startup and all ten reloads; no forbidden mixin
marker appeared, and the client exited cleanly.

Evidence hashes: summary `2075424B41B63BD0207B8EB318EE2231FEE6B220C21188AA64EFC6D041533A14`;
raw log `05E5F3436D85D77850627022DE088943AA854DE1D386DA322403569F6F177C7B`;
provenance `2386491EF7ADC6911B161B708EA9A2B638D65F95A62E89B580DD885B7E08EC05`.

This is focused interoperability evidence, not proof for the complete matrix
or performance gates.
