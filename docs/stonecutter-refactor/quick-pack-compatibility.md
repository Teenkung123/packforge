# Quick Pack compatibility

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source/mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Implemented state

- Quick Pack runtime detection uses public loader metadata only.
- Existing loader-neutral presence seam: `PackForgePlatform.isModLoaded`.
- Existing compatibility probes cover Sodium/Embeddium, Iris/Oculus, Smooth Boot variants, ImmediatelyFast, and Fabric Model Loading API.
- The ownership policy is active on Fabric, Forge, and NeoForge.

Required profile: Quick Pack 1.5.x owns index/list/namespace lookup, font provider preselection, atlas mipmap parallelization, and overlapping fade behavior; PackForge retains only independently proven decode, model, cap/recovery, startup, and diagnostics behavior. Unknown majors fail closed into a conservative profile.

## Implemented compatibility seam

`QuickPackCompatibility` reads only the public loader mod-presence/version seam for mod ID `quick-pack`; it does not reference Quick Pack classes, copy GPL code, shade the mod, or add a runtime dependency. It reports `ABSENT`, verified `1.5`, unknown version/major, unsupported `1.x`, or detection failure. Unknown and failed states disable every overlap-bearing PackForge path.

The ownership set is explicit: `RESOURCE_PACK_INDEX`, `ZIP_READ_POOL`, `FONT_PROVIDER_PRESELECTION`, `ATLAS_MIP_PARALLEL`, `LOADING_FADE_CONTROL`, and `LOADING_STATUS_OVERLAY`. Fabric, Forge, and NeoForge early mixin plugins suppress the overlapping main/client hooks before Mixin applies them. The central effective-state UI shows configured value, effective value, owner, and reason while preserving the configured value.

Unit policy tests and final-JAR packaging checks pass. The required real-mod profile also passes:

- Profile: Fabric 1.21.1, Fabric Loader 0.19.3, Quick Pack `1.5.0`.
- Quick Pack SHA-1: `71b3ff38a163651c76e707087c00c9bfc41a5987`.
- PackForge final artifact SHA-256: `569ED8B68EFE5A37C2819E93859129C13BA684F031832D4EF2E0B333F2468FDB`.
- Quick Pack staged-JAR SHA-256: `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`.
- Command: `scripts/Smoke-Fabric-Production.ps1` with `-AdditionalModPaths`, `-ReloadCount 10`, and `-AllowControlledTermination`.
- Result: `PASS`; `status=VERIFIED_1_5`, `version=1.5.0`, the six overlap capabilities are externally owned, ten requested reloads completed, and the client exited cleanly.

The PackForge log contains `PackForge Quick Pack compatibility: status=VERIFIED_1_5` and the expected ownership set. Unknown future majors remain covered by unit policy tests and fail closed into the conservative profile. Other third-party pairwise profiles are separate scheduled work and are not claimed by this result.
