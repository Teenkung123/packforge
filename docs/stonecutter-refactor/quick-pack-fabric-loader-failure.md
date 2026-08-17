# Fabric Quick Pack 1.5.0 availability evidence

Checked on 2026-08-16 against the exact catalog pin:

- Minecraft 1.21.1
- Fabric Loader 0.17.3
- Quick Pack Fabric 1.5.0
- artifact SHA-256: `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`

The focused production run used the current PackForge Fabric 1.4 artifact
(SHA-256 `09D1F1E182AF4E27733E5748EBE14A43558977DF836FF4A7301D86B32EA91309`)
and the immutable Quick Pack pin. Fabric Loader discovered the mod but aborted
before PackForge initialization:

```
Loading 6 mods:
- packforge 1.4-beta.2
- quick-pack 1.5.0
Failed to read accessWidener file from mod quick-pack
Invalid access widener file header. Expected: 'accessWidener <version> <namespace>'
```

The pinned JAR declares `quick-pack.classtweaker` through the
`accessWidener` metadata key, and its file begins with a ClassTweaker header.
Because the exact unmodified artifact cannot pass Fabric Loader validation,
the isolated Fabric Quick Pack profile and both Fabric companion profiles that
include it are recorded as `UNAVAILABLE`. No PackForge runtime, reload, hash,
or clean-exit evidence can be claimed for those profiles. This disposition does
not apply to the separately pinned Forge and NeoForge artifacts.

