# Artifact consolidation baseline

Current published/build artifact count is 17:

- Fabric: 6
- Forge: 6
- NeoForge: 5
- Stable 26.x: 3
- Beta pre-26.x: 14
- Total bytes after clean build: 23,691,877

Current public artifact names are target-specific, with beta suffixes on pre-26.x artifacts and a `26.1-26.2` range on stable artifacts. No expanded exact-release artifact proof exists yet, so no consolidation decision may be made from metadata alone.

## Baseline checksums

SHA-256 values below are from the clean `buildAllSupported` output on the recorded baseline branch.

```text
packforge-fabric-1.3.4-beta.2-mc1.20.1.jar 6565e2123ade91c07a1df27026099dae438617c1e3423cec3f267dc1db51842f
packforge-fabric-1.3.4-beta.2-mc1.21.11.jar 7eb35d51bca80aba670df512899968f4e076d3ed6d6d398593fdb46596e3e127
packforge-fabric-1.3.4-beta.2-mc1.21.1.jar 308adc142d9fca0862acab7416fcc0969efb18221768f9d5316d45cbe522f348
packforge-fabric-1.3.4-beta.2-mc1.21.4.jar eff3175c1b66b7d428c31e1176769664c26641b37735728913dc23eb0bed9827
packforge-fabric-1.3.4-beta.2-mc1.21.8.jar 1be217636ff8eec0199a6b1967d9d6638b1ee37603f6e52261cf27160b4f5380
packforge-fabric-1.3.4-mc26.1-26.2.jar 65ea7cb18b055964b3fbe54b060fe0afd2b2d84da309658f7f31c037c59fa629
packforge-forge-1.3.4-beta.2-mc1.20.1.jar 9d5d93ce4b73187c8c0822abd241dca9b7a0a60ec0e5ed2652997dc51b761f71
packforge-forge-1.3.4-beta.2-mc1.21.11.jar 0608b08dc310017eacacc3d3c6d829604d946a6ff59a201b69045fed2bb0b37d
packforge-forge-1.3.4-beta.2-mc1.21.1.jar db7011119f68679c0204bb3065bdf2792dab2abd9f9d5c78ca8ec0dfffc91faf
packforge-forge-1.3.4-beta.2-mc1.21.4.jar 178342d84667cb3b64cfdcb669f6247a2952ee7211ec8d3444ff8dac36b35343
packforge-forge-1.3.4-beta.2-mc1.21.8.jar 9630bfa28a00486e012fe8250bdb5c34d7624ad6fbc823cb5170d39df230e30e
packforge-forge-1.3.4-mc26.1-26.2.jar 421d61d8e1ba1edac0f7201afce5efa661aec5bc96d5f7fdc1c1ecb9c4d8bda8
packforge-neoforge-1.3.4-beta.2-mc1.21.11.jar ffae649e02d3e07e615b981ed44fbcf277128c37b50720919cf83a87900b1acc
packforge-neoforge-1.3.4-beta.2-mc1.21.1.jar 48bf2402e2e16d884fd692684bb1ac050c0666e255c7eed74e83a47dc17722de
packforge-neoforge-1.3.4-beta.2-mc1.21.4.jar e15dd0e4a752f42ca4cb441cea10ef26210cfccc14e2a10934772441c31a9528
packforge-neoforge-1.3.4-beta.2-mc1.21.8.jar 7d905f712d7a67644d7a1c41989dda4c53eeaf71ac170824b45d5155725f3a80
packforge-neoforge-1.3.4-mc26.1-26.2.jar bac9dded67b6fe83a576d915cea76b019c398401c36653b74064ce32ca5a12e2
```

Consolidation remains blocked until exact interior-release and loader evidence exists. Do not merge beta and stable ranges.
