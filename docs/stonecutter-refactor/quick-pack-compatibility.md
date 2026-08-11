# Quick Pack compatibility baseline

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source/mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Current state

- Quick Pack runtime detection: absent at baseline.
- Existing loader-neutral presence seam: `PackForgePlatform.isModLoaded`.
- Existing compatibility probes cover Sodium/Embeddium, Iris/Oculus, Smooth Boot variants, ImmediatelyFast, and Fabric Model Loading API.
- No Quick Pack ownership/effective-state policy exists yet.

Required future profile: Quick Pack 1.5.x owns index/list/namespace lookup, font provider preselection, atlas mipmap parallelization, and overlapping fade behavior; PackForge retains only independently proven decode, model, cap/recovery, startup, and diagnostics behavior. Unknown majors must fail closed into a conservative profile.

## Implemented compatibility seam

`QuickPackCompatibility` reads only the public loader mod-presence/version seam for mod ID `quick-pack`; it does not reference Quick Pack classes, copy GPL code, shade the mod, or add a runtime dependency. It reports `ABSENT`, verified `1.5`, unknown version/major, unsupported `1.x`, or detection failure. Unknown and failed states disable every overlap-bearing PackForge path.

The ownership set is explicit: `RESOURCE_PACK_INDEX`, `ZIP_READ_POOL`, `FONT_PROVIDER_PRESELECTION`, `ATLAS_MIP_PARALLEL`, `LOADING_FADE_CONTROL`, and `LOADING_STATUS_OVERLAY`. Fabric, Forge, and NeoForge early mixin plugins suppress the overlapping main/client hooks before Mixin applies them. The central effective-state UI shows configured value, effective value, owner, and reason while preserving the configured value.

Unit policy tests and final-JAR packaging checks pass. Quick Pack 1.5.x combined startup, reload, lifecycle, and popular-mod profiles remain `UNTESTED`; the compatibility policy is therefore not promoted as runtime acceptance.
