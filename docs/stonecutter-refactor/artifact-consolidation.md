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

## Current post-bridge verification

The current published set remains exactly 17 JARs. `buildAllSupported --rerun-tasks --no-daemon --stacktrace` passed in 5m31s and `verifyAllArtifacts --no-daemon --stacktrace` passed in 5m18s. These SHA-256 values are from that output; they are build evidence, not marketplace checksums.

```text
packforge-fabric-1.3.4-beta.2-mc1.20.1.jar fdc87bda5dc41b47d42f17c704f8517a758d0da3ecdbc4770388244ffbc27787
packforge-fabric-1.3.4-beta.2-mc1.21.11.jar 626d9f0ce52a28fa2fb4be4d51601c41ef2a723dfcaa2ee459ce353ada34af19
packforge-fabric-1.3.4-beta.2-mc1.21.1.jar 569ed8b68efe5a37c2819e93859129c13ba684f031832d4ef2e0b333f2468fdb
packforge-fabric-1.3.4-beta.2-mc1.21.4.jar ee36615cd0b1e16af2c7a36db62a74db08602f9ead66a606c4f9cb1461491a9f
packforge-fabric-1.3.4-beta.2-mc1.21.8.jar 44325b96b1a5768d976492ef3eb98ebc2b9fe680fcfc76c8362c67255949fae6
packforge-fabric-1.3.4-mc26.1-26.2.jar 4c9edd810d94b5e0c0fe42e48c39a3067f8616d161cb53e33c6b40c561b2602d
packforge-forge-1.3.4-beta.2-mc1.20.1.jar f15582f122ac344a817aabcbf15a233b37c6fb47be11218812818392729f1220
packforge-forge-1.3.4-beta.2-mc1.21.11.jar ebfdeb856449b2b06dbbfd8145289aabbb8a5ff71d40c4842a917565d241d455
packforge-forge-1.3.4-beta.2-mc1.21.1.jar 70fcd22f4a9bf8d2b88d8929ea861fd16686bd79f2e35594d9f1842b3310a13d
packforge-forge-1.3.4-beta.2-mc1.21.4.jar 5086bf55015ed4f98a8cb687ada123fa83128fa3613ff9ab05fd01b5787764cb
packforge-forge-1.3.4-beta.2-mc1.21.8.jar 36dd77c2613c01a82067fdba22647c04db6e64985b8c15ac246a05c271e56ab7
packforge-forge-1.3.4-mc26.1-26.2.jar b15842c79449d2a023dd98c9dbca3ce6d5923666bc042b9d4986f78878e5face
packforge-neoforge-1.3.4-beta.2-mc1.21.11.jar 7d967436b99ccd4df59e1e3d295a8c8289e176144eccafec2f64d4b6ea78e68a
packforge-neoforge-1.3.4-beta.2-mc1.21.1.jar adae1349442e8296e8383001cdac8a9f6df15cd83335f885753d809c4bd7f847
packforge-neoforge-1.3.4-beta.2-mc1.21.4.jar a0fa89e00e63ec957b55c52a2039f47c1644ee84415ffcf5415aa5def575fce4
packforge-neoforge-1.3.4-beta.2-mc1.21.8.jar 7b042ccd2e3b69115a94a1ad6898dc7a3ac6dbc35acaf395d76f1d6102938c7f
packforge-neoforge-1.3.4-mc26.1-26.2.jar fd849db6a6c849d270fb4c1682134f786639234a4dff9077fd6f07ddab2525cc
```

The planned 1.20.2 Fabric/Forge JARs are deliberately absent from this release set; NeoForge 1.20.2 has no verified artifact.
