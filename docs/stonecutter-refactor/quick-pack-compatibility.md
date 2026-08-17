# Quick Pack compatibility

Date: 2026-08-15

## Focused Phase 5 runtime checkpoint (2026-08-16)

This addendum supersedes the older current-state paragraphs below. The
current catalog has 36 total profiles: 14 `SAFE_ORIGINAL_PATH`, four
`HOOK_PRESERVING_COALESCED_PATH`, two `EXTERNALLY_OWNED_PATH`, one
evidence-backed `FAILED` VulkanMod renderer crash, and 15
`UNAVAILABLE/UNAVAILABLE`; no profile remains `UNTESTED` or
`PENDING_METADATA`.

The current ten-reload Forge/NeoForge results, ownership markers, and immutable
log hashes are consolidated in
[`evidence/quick-pack-runtime-2026-08-16.md`](evidence/quick-pack-runtime-2026-08-16.md).

- Forge + Quick Pack 1.5.0 passed on the current final JAR for Forge
  1.21.1-52.0.0: ten reloads, stable resolved-resource hash
  `71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4`,
  required ownership markers, no forbidden mixin markers, and clean exit.
  Immutable summary: `evidence/quick-pack-forge-1.21.1.md`.
- NeoForge + Quick Pack 1.5.0 passed on the current final JAR for NeoForge
  21.1.1 with the same ten-reload, semantic-hash, marker, and clean-exit
  requirements. Immutable summary: `evidence/quick-pack-neoforge-1.21.1.md`.
- Fabric Sodium, Iris, ImmediatelyFast, ModernFix, FerriteCore,
  Continuity/Indium, CIT Resewn, ETF/EMF, Axiom, and the combined
  ModernFix/FerriteCore profiles passed ten reloads on the current
  `mc1_21_1` final JAR. Their expected ownership/path markers, stable semantic
  hashes, heavy-fixture evidence where applicable, and clean exits are recorded
  in `evidence/fabric-*.md`.
- The exact VulkanMod profile is recorded as a narrow external failure: the
  renderer crashes with `OutOfMemoryError: Out of stack space` during Vulkan
  initialization before PackForge readiness, without a fatal PackForge mixin
  marker. See `evidence/fabric-vulkanmod-1.21.1.md`.
- Fabric + Quick Pack 1.5.0 and the Fabric Quick Pack + Sodium/Iris and
  Quick Pack + ImmediatelyFast combinations are unavailable for the exact
  pinned artifact. Fabric Loader 0.17.3 rejects its declared
  `classTweaker` file as an `accessWidener` before PackForge initializes;
  see `quick-pack-fabric-loader-failure.md`.
- `fabric-quick-pack-default-on` remains unavailable because no default-off
  candidate has been promoted. No default was changed by this checkpoint.

This focused checkpoint is supplemented by the final-byte 62-cell base matrix;
it does not prove live renderer
families, or comparative performance gates. Repaired heavy-fixture and runtime
scenario evidence is recorded in the separate dated evidence summaries.

## Superseding 1.4 policy checkpoint (2026-08-16)

The ownership list in the older body is superseded by the current version-aware implementation at HEAD `8aa6844`:

- unknown, unparsable, or detection-failure metadata: all four known overlap capabilities;
- Quick Pack `<1.4`: resource indexing;
- Quick Pack `>=1.4,<1.5`: resource indexing plus loading-fade control;
- Quick Pack `>=1.5`: the preceding modules plus font-provider preselection and atlas-mipmap generation.

ZIP read pooling and PackForge's loading-status overlay are not externally owned. PackForge retains its diagnostics, summary toast, sprite decode, model scheduling, atlas protection, and recovery paths. The Fabric profile-only Loader `0.17.3` override is present structurally. The focused Forge and NeoForge results above are the only current Quick Pack runtime PASS records.

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source or mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Current ownership policy

`QuickPackCompatibility` reads only loader-provided public metadata for mod ID `quick-pack`. It reports `ABSENT`, `MODULE_HANDOFF`, or `DETECTION_FAILED`. Version text is diagnostic only; missing, malformed, old, current, and future strings do not select different code paths. Metadata-read failure conservatively hands off the same overlap set.

Quick Pack owns exactly four overlapping capabilities when present or when metadata is unknown:

- `RESOURCE_PACK_INDEX`
- `FONT_PROVIDER_PRESELECTION`
- `ATLAS_MIP_PARALLEL`
- `LOADING_FADE_CONTROL`

The other 25 PackForge capabilities remain governed by normal capability/config policy, including `ZIP_READ_POOL` and `LOADING_STATUS_OVERLAY`. Loader mixin selection and runtime guards preserve non-overlap diagnostics and behavior. Configuration UI shows configured value, effective value, owner, and reason without overwriting saved choices.

This policy deliberately avoids Quick Pack internal classes and configuration keys. PackForge hands off an overlap capability whenever Quick Pack is loaded, even if that Quick Pack capability is locally disabled. Correct coexistence is preferred over retaining duplicate optimization work.

## Current evidence

- Unit tests cover absent, present, old, current, future, malformed, missing-version, and detection-failure policy states.
- Packaging/structural checks cover ownership boundaries.
- `CompatibilityProfileReporter` can emit profile ID, loader/target, loader-observed mod presence, Quick Pack state, four handed-off capabilities, and 25 retained capabilities.
- Catalog declares six Quick Pack-containing recipes. The isolated Forge and NeoForge recipes are pinned and have committed runtime evidence; the isolated Fabric recipe and two Fabric high-risk combinations are explicitly unavailable because the exact artifact is rejected before PackForge initialization. The default-on profile is also deferred until a candidate is promoted.
- Schema-2 materialization can hash-verify and stage profile dependencies through all three loader wrappers.

The committed summaries and profile evidence cover every currently materializable
available recipe: 14 safe-path PASS, four hook-preserving PASS, two externally
owned PASS, and one documented VulkanMod failure. The other 15 exact-loader
recipes are explicitly `UNAVAILABLE` with dated loader/materialization reasons;
no profile remains silently `UNTESTED` or `PENDING_METADATA`.

Quick Pack 1.4 and older remain best-effort/not guaranteed. Unit policy equality across version strings proves ownership selection only; it does not prove those versions run correctly.

## Historical runtime evidence

Checkpoint `a3402866b217ac159d6a3cec70d3585028f79732` recorded this older-artifact profile:

- Fabric 1.21.1, Fabric Loader 0.19.3, Quick Pack 1.5.0.
- Quick Pack SHA-256 `7E93E08D5ADA815DB6874D5BCCA750A12A2AC97F3B80143ED47EF6D70FE32EE1`.
- PackForge SHA-256 `AAD7C8126DEFDA7A9FB115675841C4F2210607F722CA4141BC3C23BECED6E71A`.
- `MODULE_HANDOFF`, six external owners, ten reloads, natural clean exit.

This remains useful regression evidence for design behavior. Later direct-build and source changes invalidate it as proof for current PackForge bytes.

## Required current proof

Rebuild current artifacts and rerun available Quick Pack profiles after any
artifact-changing implementation commit. Retain profile/materialization
provenance, verify exact reporter ownership, exercise required fixtures and
reload count, reject fatal/mixin markers, and record exact clean exit.
