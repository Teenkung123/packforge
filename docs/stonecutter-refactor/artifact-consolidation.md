# Artifact consolidation baseline

> **Current-evidence notice (2026-08-13): HISTORICAL / INVALIDATED FOR CURRENT DIRECT-BUILD BYTES.** The artifact counts, hashes, structural checks, same-artifact runtime results, and manifest statements below preserve evidence from their named historical checkpoints. Phase E changed artifact production to the direct Stonecutter graph, and those resulting JAR bytes have not received an equivalent final 20-artifact build, hash binding, manifest verification, or 62-cell runtime pass. The registry still declares a 20-artifact publication topology, but this document is not current release-ready proof.

## Superseding 1.4 structural checkpoint (2026-08-16)

HEAD `8aa6844` adds current final-JAR invariants: one exact loader-specific `mixinextras-*-0.5.4-slim.jar` at the outer distribution level, no alternate normal top-level MixinExtras copy, recursive content scanning of every other nested JAR, recursive Forge slim-wrapper/common-runtime metadata and class checks, required mixin/refmap checks, and a 128x128 PNG icon capped at 32KiB. Fresh representative Fabric/Forge/NeoForge artifacts pass these checks; the measured icon is 10,607 bytes. The existing root directory remains mixed current/stale, so its 17 unrecreated artifacts must not be used as a current 20-artifact release set. No current all-20 manifest, same-binary range, or runtime matrix proof is asserted.

Baseline published/build artifact count was 17:

- Fabric: 6
- Forge: 6
- NeoForge: 5
- Stable 26.x: 3
- Beta pre-26.x: 14
- Total bytes after clean build: 23,691,877

At baseline, public artifact names were target-specific, with beta suffixes on pre-26.x artifacts and a `26.1-26.2` range on stable artifacts. Expanded exact target artifacts required separate same-binary range proof before publication. No beta artifact is merged with the stable 26.x range.

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

Consolidation decision: retain the 17 public artifacts, use the registry-derived exact matrix for interior-release verification, and keep pre-26.x beta metadata separate from the stable 26.x range. The exact target artifacts are not silently added to publication by changing registry status alone.

## Historical post-bridge verification

At this checkpoint the published set remained exactly 17 JARs. `buildAllSupported --rerun-tasks --no-daemon --stacktrace` passed in 5m31s and `verifyAllArtifacts --no-daemon --stacktrace` passed in 5m18s. These SHA-256 values are from that output; they are build evidence, not marketplace checksums.

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

At this historical checkpoint, the exact 1.20.2/1.20.3/1.20.4 artifacts were deliberately absent from the public release set pending same-binary range proof.

## Historical expanded exact target evidence

The following historical distribution hashes were the most recent exact target artifacts used by the legacy production harnesses at their named checkpoints; they do not identify current direct-build bytes:

```text
packforge-fabric-1.3.4-beta.3-mc1.20.2.jar ABAF4C151DF806B616617326E8D4B63A18ECDA4E2A3DE42760F62CA150B1B8CF
packforge-forge-1.3.4-beta.3-mc1.20.2.jar B7CD647909BFFD728BC9004ACDA793597751AC708C3C2DE228CBAE768977E68E
packforge-neoforge-1.3.4-beta.3-mc1.20.2.jar 6742DF5281A60D580AC6C7BC5A2275398836DCBEB6FD6A7A1E56208BA587711C
packforge-fabric-1.3.4-beta.4-mc1.20.3.jar 4BC0004A1DE3168952019A72AB2F43093162E00E8BA95DA289A4F04C0F29B9CB
packforge-forge-1.3.4-beta.4-mc1.20.3.jar 6F3024BE481424EFFF50FA350075CE5E27702094A020E561367B3F471C150A56
packforge-neoforge-1.3.4-beta.4-mc1.20.3.jar 7FB0A87F52CE61440452F5D9C1D90976A485653A844870C07EBD7F631B9AE73A
packforge-fabric-1.3.4-beta.4-mc1.20.4.jar 23BDC3DBDC2578F060DAE6C2FA68194B9426BDB48F064E2CBA954D561FED78BE
packforge-forge-1.3.4-beta.4-mc1.20.4.jar DC3EFA275C0E8E0879CC3C86E6B689B12C2E67B000804AE3DF9F43878F81EB30
packforge-neoforge-1.3.4-beta.4-mc1.20.4.jar 666A7DF5BB402C0551F7572534AD0D2923BA6724180A57E6E7C447ABA2C98203
packforge-fabric-1.3.4-mc26.1-26.2.jar 4C9EDD810D94B5E0C0FE42E48C39A3067F8616D161CB53E33C6B40C561B2602D
packforge-forge-1.3.4-mc26.1-26.2.jar 139B3ACF0F24818C0ED4B711B19467EFB2E7597DD73D0E9520EF0F0C93B13A14
packforge-neoforge-1.3.4-mc26.1-26.2.jar FDC63B9D58F5A10F48D745CDE47E617625E33921DF06CA3838E7220E15D521DA
```

The exact 1.20.5 through 1.21.10 family hashes are recorded in their phase entries in `work-log.md`; all were structurally verified before runtime smoke.

## Pre-range registry and manifest evidence

- Mojang stable release manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
- Recorded manifest SHA-256: `380769b566afa9e768c82e1337fa3af3052aea47c7a9fe09d2c5a96edcef2e6c`
- Verification: `scripts/Verify-Mojang-ReleaseSequence.ps1` PASS; all 22 required release IDs present and ordered.
- Generated matrix counts at this checkpoint: 19 build targets, 62 exact smoke cells, 26 public-anchor smoke cells, 17 publication rows.
- Historical root `build --no-daemon --console plain --stacktrace`: PASS in 4m22s (`87 actionable tasks: 24 executed, 63 up-to-date`); the checkpoint's 26.x NeoForge all-in-one output embedded MixinExtras exactly once and passed the focused 26.x artifact verifier.

The 17-artifact hashes after that build are recorded below. They are local build checksums, not marketplace checksums:

```text
packforge-fabric-1.3.4-beta.2-mc1.20.1.jar fdc87bda5dc41b47d42f17c704f8517a758d0da3ecdbc4770388244ffbc27787
packforge-fabric-1.3.4-beta.2-mc1.21.11.jar 626d9f0ce52a28fa2fb4be4d51601c41ef2a723dfcaa2ee459ce353ada34af19
packforge-fabric-1.3.4-beta.2-mc1.21.1.jar 569ed8b68efe5a37c2819e93859129c13ba684f031832d4ef2e0b333f2468fdb
packforge-fabric-1.3.4-beta.2-mc1.21.4.jar ee36615cd0b1e16af2c7a36db62a74db08602f9ead66a606c4f9cb1461491a9f
packforge-fabric-1.3.4-beta.2-mc1.21.8.jar 44325b96b1a5768d976492ef3eb98ebc2b9fe680fcfc76c8362c67255949fae6
packforge-fabric-1.3.4-mc26.1-26.2.jar 4c9edd810d94b5e0c0fe42e48c39a3067f8616d161cb53e33c6b40c561b2602d
packforge-forge-1.3.4-beta.2-mc1.20.1.jar e137920e81cd2cc9ac6d746699d9af918770a8c30d009df47adc76c859cc3caa
packforge-forge-1.3.4-beta.2-mc1.21.11.jar 20920f25a3004bf54232028d2ddd4d615c71fdcfb136ee5a03d1162b7ced0dfb
packforge-forge-1.3.4-beta.2-mc1.21.1.jar b2c51714e0ca2b5b7d4f76e3cb290be489f7d417f1adc5c108359b2ff5dc1ca5
packforge-forge-1.3.4-beta.2-mc1.21.4.jar 58080059009b399894c9f83d0bc71f0105443d4b9021d1c40f410c8084d745d6
packforge-forge-1.3.4-beta.2-mc1.21.8.jar c2a3e8ebb9a71d4f05d6d8419923ef33bb9164ded9a55a195cb9cf31604430ca
packforge-forge-1.3.4-mc26.1-26.2.jar 06d140f5984ab9e0e80d51d1a374695182d19ae3b0cc3c962fbebbe39df65876
packforge-neoforge-1.3.4-beta.2-mc1.21.11.jar 3597c043b97b895e70d3820490fefc61ad306c574489947b6c44f3f842c337a5
packforge-neoforge-1.3.4-beta.2-mc1.21.1.jar 3906c8fb436909cc6f8a5c02fece9aa10af47580fdce30d15d886f5b7f975e60
packforge-neoforge-1.3.4-beta.2-mc1.21.4.jar d2644c4a22917fafa1a964aeec416d4902a59042e9b6bd9fdef624d9a3de8c6f
packforge-neoforge-1.3.4-beta.2-mc1.21.8.jar 64704daffd62214391414ff77995554e66fe70fe5f16b66def0dcc944115a8a7
packforge-neoforge-1.3.4-mc26.1-26.2.jar 6f68f8989ccdb119439ccd479110dcca50e7d6b1eb8e1f0e1a7ad71f07e5ce8d
```

The pre-26 rows are not promoted merely because their exact target artifacts pass. The remaining artifact-consolidation work is to prove a range binary, split unsafe ranges, and keep the final count within the plan’s 17-artifact target or evidence-backed 21-artifact fallback.

## First range promotion: Minecraft 1.20.2-1.20.4

Checkpoint `fbd7b3208347230ae17e90f822bde2e100e82992` proves one range artifact per loader across all three exact releases. The tested and reproducibly rebuilt SHA-256 values are:

```text
packforge-fabric-1.3.4-beta.3-mc1.20.2-1.20.4.jar   77D690FF0D9956271196CBD96406962C052656AAC8B26FF42AD78B40954F4B1A
packforge-forge-1.3.4-beta.3-mc1.20.2-1.20.4.jar    88F4BB16DD9BEDCEA21100827B7A53794050939F936422F8180B86C451C90547
packforge-neoforge-1.3.4-beta.3-mc1.20.2-1.20.4.jar 24460EEFB3D33F1B6AD5154758A2E829394138ECD65017C8E19F39F5A1D2BC48
```

Each hash passed Minecraft 1.20.2, 1.20.3, and 1.20.4 startup, two deterministic reloads, semantic/resource evidence, and clean exit on its loader. The registry now assigns those three exact rows to `mc1_20_2`; generated publication contains 20 artifacts and 35 exact publication-smoke cells. This is an evidence-based partial promotion, not a claim that the remaining Java 21 ranges are complete.

## Second range promotion: Minecraft 1.20.5-1.21.1

Checkpoint `5ed1480540162547b3de475e0e9a51e7965d968c` makes the `mc1_21_1` anchor publish one Fabric artifact across 1.20.5-1.21.1 and one Forge/NeoForge artifact across 1.20.6-1.21.1. The loader-specific lower bound preserves official availability while retaining one artifact per loader. The runtime-tested and clean-build-reproduced SHA-256 values are:

```text
packforge-fabric-1.3.4-beta.2-mc1.20.5-1.21.1.jar   99D38B658B6D8A19294ADB4D5986BB714016A67C697B91C8FAB01EC7DE5B4E72
packforge-forge-1.3.4-beta.2-mc1.20.6-1.21.1.jar    CA9F0EEAF1B382BA13518C7B5C24149BDA6031C33AA4473A8A08CD510ED78FFA
packforge-neoforge-1.3.4-beta.2-mc1.20.6-1.21.1.jar 969BFADD9B219932FAB7040839CA6ADAB075EC8B0E941CDED829A22C3A41D5AE
```

Fabric passed 1.20.5, 1.20.6, 1.21, and 1.21.1; Forge and NeoForge each passed 1.20.6, 1.21, and 1.21.1. Every cell used the unchanged loader artifact, completed two deterministic reloads, emitted semantic/resource evidence, and exited cleanly. The Fabric artifact additionally passed ten requested reloads with the real Quick Pack 1.5.0 JAR and `status=MODULE_HANDOFF`.

The historical clean build passed in 8m49s and reproduced every tested hash. At that checkpoint, generated counts were 19 build targets, 62 exact smoke cells, 42 publication-smoke cells, and 20 publication rows, and `Generate-ReleaseManifest.py` accepted exactly 20 JARs. Seven non-anchor pre-26 releases remained to be consolidated; the intended final 20-artifact layout remained within the plan's evidence-backed ceiling.

## HISTORICAL — final 20-artifact layout invalidated for current bytes

The historical sections above show incremental promotion. At checkpoint `a3402866b217ac159d6a3cec70d3585028f79732`, all range families were proven and publication-smoke coverage was 62/62. These hashes were authoritative only for that checkpoint and are invalidated as evidence for current direct-build bytes:

```text
fabric   1.20.1             3dafe7d9e341e15a3849dc95728fe88cb9ef40751ce1c9b0679c5024b341b864
forge    1.20.1             86415ce66b254b35a8eec52daa78925dd9c1b03d1b9b54b2791243f6ed1b59b4
fabric   1.20.2-1.20.4      44a6811603abd81bb02e6b1604a24630f44c02cd1af313d9a4540846f6c8bc14
forge    1.20.2-1.20.4      2d3b96eef1eb8b10219880ac66e038ffa2e5500b3fa19da7f3583eb6b9d86076
neoforge 1.20.2-1.20.4      526bd7f7627808ad76f6497f24fbacd600d8cdd5305013e49712588bd939442c
fabric   1.20.5-1.21.1      aad7c8126defda7a9fb115675841c4f2210607f722ca4141bc3c23beced6e71a
forge    1.20.6-1.21.1      5c9abac3b8f1d8bf4fceace3d9a8d7e4509bb34d8169ca305fb18ef50928f183
neoforge 1.20.6-1.21.1      62db915a9a6fc73d7483acc3dc968557d35d5fe714a5a87eb5c3628d3b845e43
fabric   1.21.2-1.21.4      40b2d8eaba0ac2ac7e5e61c7cfbc7747634b8054d243efd202bcc7204a220870
forge    1.21.3-1.21.4      5299b4316c52a2aa3a38e7bf44db45ad60b31618b8d9f3d11bb9dabd511c2b38
neoforge 1.21.2-1.21.4      f5e8bce85b283b7615d259ce241e3a9bded9bc4af6f6f0ff5b97e40a4e8b72fc
fabric   1.21.5-1.21.8      6792e92a3f72492a04410a7cdc5e33f19820e218fab5efdc9034adb420ff3baa
forge    1.21.5-1.21.8      31fa446b98b7efaa08d406dbec4f0ced74d56bd11eaf0c247f41402e982b323a
neoforge 1.21.5-1.21.8      65f18818a39a86faa43cefacbfede479160df847c543de8f82e7a8456efad15d
fabric   1.21.9-1.21.11     e67a315958cc9044ec3137ad8f1e26808b2ef7754f30477372a2dec576b126b4
forge    1.21.9-1.21.11     bcc7ccd82fad1110ff4ab655b3bfc1ba6e6aa226a144468474b2b628a8a83670
neoforge 1.21.9-1.21.11     0240ad0f84e5e21d68bd1ae0e7ef0f73496b60d7b2e9e243cf779a644e280814
fabric   26.1-26.2           312ec96bb988c319d706110a801680b7af7a51defbff60a62072a57d9f865e95
forge    26.1-26.2           447d6c727406bde34d81a3b0df17b554f43f924e28cc805b13abfd8df08819fc
neoforge 26.1-26.2           7e946799ea1b3c81b068acb9048a93f8cfa6907316c08ef886a87b32d321aba6
```

At checkpoint `a3402866`, this was 69.70% below the naïve 66-artifact Cartesian expansion and below the evidence-backed ceiling of 21. At that checkpoint only, all 20 passed structural verification, every exact release claimed by their metadata passed the same final loader artifact, and `build/libs/release/manifest.json` plus `release-table.md` were regenerated from the set. None of those unqualified hash/runtime/manifest claims transfers to current direct-build bytes without fresh final-artifact evidence.
