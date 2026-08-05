# PackForge 1.3.3-beta.2 release notes

Fixes a startup crash affecting the 1.20.1 Forge `1.3.3-beta.1` artifact.

- Replaced unstable intermediary mixin selectors with stable structural hooks.
- Kept ZIP resource-pack indexing and atlas decode batching active.
- Added final-artifact checks that reject Fabric intermediary selectors in Forge/NeoForge mixins.
- Added checksum-verified runtime smoke for 18 cells: 17 unique target/platform combinations plus one extra reporter Forge version row. Fabric/NeoForge consume the verified packaged bytes from `build/libs`; Forge CI uses source-mode. Production Forge final-JAR validation is separate through `scripts/Smoke-Forge-Production.ps1`.
- Verified exact-JAR startup through the final atlas on Forge 1.20.1 builds `47.0.0`, `47.4.20`, and `47.4.22`; automated F3+T reload acceptance remains a separate open runtime check.

Users replacing `1.3.3-beta.1` must restart Minecraft. F3+T alone cannot reload changed mod mixins.

This file prepares release text only. No release is published by this change.
