# Quick Pack compatibility

Date: 2026-08-15

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source or mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Current ownership policy

`QuickPackCompatibility` reads only loader-provided public metadata for mod ID `quick-pack`. It reports `ABSENT`, `MODULE_HANDOFF`, or `DETECTION_FAILED`. Version text is diagnostic only; missing, malformed, old, current, and future strings do not select different code paths. Metadata-read failure conservatively hands off the same overlap set.

Quick Pack owns exactly six overlapping capabilities when present:

- `RESOURCE_PACK_INDEX`
- `ZIP_READ_POOL`
- `FONT_PROVIDER_PRESELECTION`
- `ATLAS_MIP_PARALLEL`
- `LOADING_FADE_CONTROL`
- `LOADING_STATUS_OVERLAY`

The other 23 PackForge capabilities remain governed by normal capability/config policy. Loader mixin selection and runtime guards preserve non-overlap diagnostics and behavior. Configuration UI shows configured value, effective value, owner, and reason without overwriting saved choices.

This policy deliberately avoids Quick Pack internal classes and configuration keys. PackForge hands off an overlap capability whenever Quick Pack is loaded, even if that Quick Pack capability is locally disabled. Correct coexistence is preferred over retaining duplicate optimization work.

## Current evidence

- Unit tests cover absent, present, old, current, future, malformed, missing-version, and detection-failure policy states.
- Packaging/structural checks cover ownership boundaries.
- `CompatibilityProfileReporter` can emit profile ID, loader/target, loader-observed mod presence, Quick Pack state, six handed-off capabilities, and 23 retained capabilities.
- Catalog declares six Quick Pack-containing recipes. The isolated Forge and NeoForge recipes are pinned `AVAILABLE`; the isolated Fabric recipe and three Fabric high-risk combinations remain `PENDING_METADATA`.
- Schema-2 materialization can hash-verify and stage profile dependencies through all three loader wrappers.

All six Quick Pack-containing catalog recipes remain `UNTESTED`: two are `AVAILABLE` and four are `PENDING_METADATA`. No current final-JAR Minecraft launch, reload, resource/semantic result, or clean-exit result exists. `AVAILABLE` describes pinned materializable inputs, not compatibility PASS.

Quick Pack 1.4 and older remain best-effort/not guaranteed. Unit policy equality across version strings proves ownership selection only; it does not prove those versions run correctly.

## Historical runtime evidence

Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded this older-artifact profile:

- Fabric 1.21.1, Fabric Loader 0.19.3, Quick Pack 1.5.0.
- Quick Pack SHA-256 `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`.
- PackForge SHA-256 `AAD7C8126DEFDA7A9FB115675841C4F2210607F722CA4141BC3C23BECED6E71A`.
- `MODULE_HANDOFF`, six external owners, ten reloads, natural clean exit.

This remains useful regression evidence for design behavior. Later direct-build and source changes invalidate it as proof for current PackForge bytes.

## Required current proof

Rebuild current artifacts, execute each available Quick Pack profile, retain profile/materialization provenance, verify exact reporter ownership, exercise required fixtures and reload count, reject fatal/mixin markers, and record exact clean exit. Until then, result stays `UNTESTED`.
