# Quick Pack compatibility

Date: 2026-08-15

## ZIP pool default checkpoint (2026-08-17)

`loaderZipPoolEnabled` is now configured on by default for new or missing settings, but ZIP pooling still depends on PackForge's own resource index. Quick Pack owns `RESOURCE_PACK_INDEX`, so the PackForge index and its dependent ZIP pool are both effectively inactive while Quick Pack is present. ZIP pooling remains PackForge-owned; Quick Pack is not reported as implementing it.

Existing explicit `loaderZipPoolEnabled=false` values are preserved. The current Forge/NeoForge Quick Pack evidence predates this default change and remains design-regression evidence until the focused profile is rerun against the promoted bytes. Exact Fabric Quick Pack remains unavailable because the pinned Quick Pack artifact is rejected by Fabric Loader before PackForge initializes.

## Focused Phase 5 runtime checkpoint (2026-08-16)

This addendum supersedes the older current-state paragraphs below. The
current catalog has 36 total profiles: 14 `SAFE_ORIGINAL_PATH`, four
`HOOK_PRESERVING_COALESCED_PATH`, two `EXTERNALLY_OWNED_PATH`, one
evidence-backed `FAILED` VulkanMod renderer crash, and 15
`UNAVAILABLE/UNAVAILABLE`; no profile remains `UNTESTED` or
`PENDING_METADATA`.

The ten-reload Forge/NeoForge results, ownership markers, and immutable
log hashes are consolidated in
[`evidence/quick-pack-runtime-2026-08-16.md`](evidence/quick-pack-runtime-2026-08-16.md).

- Forge + Quick Pack 1.5.0 passed on the then-current final JAR for Forge
  1.21.1-52.0.0: ten reloads, stable resolved-resource hash
  `71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4`,
  required ownership markers, no forbidden mixin markers, and clean exit.
  Immutable summary: `evidence/quick-pack-forge-1.21.1.md`.
- NeoForge + Quick Pack 1.5.0 passed on the then-current final JAR for NeoForge
  21.1.1 with the same ten-reload, semantic-hash, marker, and clean-exit
  requirements. Immutable summary: `evidence/quick-pack-neoforge-1.21.1.md`.
- Fabric Sodium, Iris, ImmediatelyFast, ModernFix, FerriteCore,
  Continuity/Indium, CIT Resewn, ETF/EMF, Axiom, and the combined
  ModernFix/FerriteCore profiles passed ten reloads on the then-current
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

This focused checkpoint is supplemented by the final-byte 62-cell base matrix;
it does not prove live renderer families or later default changes. Repaired
heavy-fixture and runtime scenario evidence is recorded in the separate dated
evidence summaries.

## Superseding 1.4 policy checkpoint (2026-08-16)

The ownership list in the older body is superseded by the current version-aware implementation:

- unknown, unparsable, future-major, or detection-failure metadata: all four known overlap capabilities;
- Quick Pack `<1.4`: resource indexing;
- Quick Pack `>=1.4,<1.5`: resource indexing plus loading-fade control;
- Quick Pack `>=1.5`: the preceding modules plus font-provider preselection and atlas-mipmap generation.

ZIP read pooling and PackForge's loading-status overlay are not externally owned. PackForge retains its diagnostics, summary toast, sprite decode, model scheduling, atlas protection, and recovery paths. The ZIP pool is additionally gated by the PackForge index because its duplicate-path safety check uses that index. The Fabric profile-only Loader `0.17.3` override is present structurally.

## Clean-room boundary

PackForge is MIT-licensed and Quick Pack is GPLv3. PackForge may detect public mod ID/version metadata, observe public behavior, and implement independent ownership rules. It must not copy Quick Pack source or mixins, import internal interfaces, shade Quick Pack, or depend on unstable internal APIs.

## Current ownership policy

`QuickPackCompatibility` reads only loader-provided public metadata for canonical mod ID `quick-pack` and Forge-family normalized ID `quick_pack`. It reports `ABSENT`, `MODULE_HANDOFF`, or `DETECTION_FAILED`. Successfully parsed versions select the known historical ownership set; missing, malformed, future-major, and failed metadata use the full known overlap set conservatively.

Quick Pack 1.5 owns four overlapping capabilities:

- `RESOURCE_PACK_INDEX`
- `FONT_PROVIDER_PRESELECTION`
- `ATLAS_MIP_PARALLEL`
- `LOADING_FADE_CONTROL`

The other 25 PackForge capabilities remain governed by normal capability/config policy, including `ZIP_READ_POOL` and `LOADING_STATUS_OVERLAY`. `ZIP_READ_POOL` is retained as PackForge-owned but reports an ineffective value when the PackForge index is externally owned. Loader mixin selection and runtime guards preserve non-overlap diagnostics and behavior. Configuration UI shows configured value, effective value, owner, and reason without overwriting saved choices.

This policy deliberately avoids Quick Pack internal classes and configuration keys. PackForge hands off an overlap capability whenever Quick Pack is loaded, even if that Quick Pack capability is locally disabled. Correct coexistence is preferred over retaining duplicate optimization work.

## Current evidence

- Unit tests cover absent, present, old, current, future, malformed, missing-version, and detection-failure policy states.
- Packaging/structural checks cover ownership boundaries.
- `CompatibilityProfileReporter` emits profile ID, loader/target, loader-observed mod presence, Quick Pack state, four handed-off capabilities, and retained PackForge capabilities with their effective states.
- Catalog declares six Quick Pack-containing recipes. The isolated Forge and NeoForge recipes have committed pre-promotion runtime evidence; the isolated Fabric recipe and two Fabric high-risk combinations are explicitly unavailable because the exact artifact is rejected before PackForge initialization.
- Schema-2 materialization can hash-verify and stage profile dependencies through all three loader wrappers.

The committed summaries and profile evidence cover every currently materializable
available recipe at their recorded artifact hashes: 14 safe-path PASS, four hook-preserving PASS, two externally
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

This remains useful regression evidence for design behavior. Later direct-build, ownership, and source changes invalidate it as proof for current PackForge bytes.

## Required current proof

Rebuild current artifacts and rerun available Quick Pack profiles after an
artifact-changing implementation commit when release evidence is required.
Retain profile/materialization provenance, verify exact reporter ownership and
effective ZIP-pool state, exercise required fixtures and reload count, reject
fatal/mixin markers, and record exact clean exit.
