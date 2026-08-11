# Quick Pack compatibility baseline

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source/mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Current state

- Quick Pack runtime detection: absent at baseline.
- Existing loader-neutral presence seam: `PackForgePlatform.isModLoaded`.
- Existing compatibility probes cover Sodium/Embeddium, Iris/Oculus, Smooth Boot variants, ImmediatelyFast, and Fabric Model Loading API.
- No Quick Pack ownership/effective-state policy exists yet.

Required future profile: Quick Pack 1.5.x owns index/list/namespace lookup, font provider preselection, atlas mipmap parallelization, and overlapping fade behavior; PackForge retains only independently proven decode, model, cap/recovery, startup, and diagnostics behavior. Unknown majors must fail closed into a conservative profile.
