# Quick Pack compatibility

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source/mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Implemented state

- Quick Pack runtime detection uses public loader metadata only.
- Existing loader-neutral presence seam: `PackForgePlatform.isModLoaded`.
- Existing compatibility probes cover Sodium/Embeddium, Iris/Oculus, Smooth Boot variants, ImmediatelyFast, and Fabric Model Loading API.
- The ownership policy is active on Fabric, Forge, and NeoForge.

Required profile: whenever Quick Pack is present, it owns only the six known overlapping modules. PackForge retains independently proven decode, model, cap/recovery, startup, and diagnostics behavior. Quick Pack version text is diagnostic only and never selects a different compatibility branch.

## Implemented compatibility seam

`QuickPackCompatibility` reads only the public loader mod-presence/version seam for mod ID `quick-pack`; it does not reference Quick Pack classes, copy GPL code, shade the mod, or add a runtime dependency. It reports `ABSENT`, `MODULE_HANDOFF`, or `DETECTION_FAILED`. Version strings—including missing, malformed, old, and future versions—do not alter ownership. A metadata-read failure also fails safely by handing off the same overlap set.

The ownership set is explicit: `RESOURCE_PACK_INDEX`, `ZIP_READ_POOL`, `FONT_PROVIDER_PRESELECTION`, `ATLAS_MIP_PARALLEL`, `LOADING_FADE_CONTROL`, and `LOADING_STATUS_OVERLAY`. Fabric, Forge, and NeoForge early mixin plugins suppress the overlapping main/client hooks before Mixin applies them. The central effective-state UI shows configured value, effective value, owner, and reason while preserving the configured value. Non-overlapping PackForge modules remain enabled according to normal capability/config policy.

This deliberately avoids probing Quick Pack internal classes or configuration keys. Such probes would create the frequent maintenance burden this policy is intended to remove. PackForge therefore hands off an overlapping module whenever Quick Pack is loaded, even if that Quick Pack module is locally disabled. This prefers correctness and broad coexistence over retaining every duplicate optimization.

Unit policy tests and final-JAR packaging checks pass. The required real-mod profile also passes:

- Profile: Fabric 1.21.1, Fabric Loader 0.19.3, Quick Pack `1.5.0`.
- Quick Pack SHA-1: `71b3ff38a163651c76e707087c00c9bfc41a5987`.
- PackForge final artifact SHA-256: `99D38B658B6D8A19294ADB4D5986BB714016A67C697B91C8FAB01EC7DE5B4E72`.
- Quick Pack staged-JAR SHA-256: `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`.
- Command: `scripts/Smoke-Fabric-Production.ps1` with `-AdditionalModPaths`, `-ReloadCount 10`, and `-AllowControlledTermination`.
- Result: `PASS`; `status=MODULE_HANDOFF`, `version=1.5.0` (diagnostic only), the six overlap capabilities are externally owned, ten requested reloads completed, and the client exited cleanly.

The PackForge log contains `PackForge Quick Pack compatibility: status=MODULE_HANDOFF` and the expected ownership set. Unit tests cover `1.4.0`, `1.5.0`, future major versions, malformed text, and missing version metadata with the same module-level result. The real runtime proof is Quick Pack 1.5.0; Quick Pack 1.4 and older remain best-effort and are not guaranteed. Other third-party pairwise profiles are separate scheduled work and are not claimed by this result.
