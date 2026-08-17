# Compatibility profile disposition summary

Checked: 2026-08-16

Catalog: `gradle/compatibility-profiles.json` (schema 2, 36 frozen recipes)

## Current counts

| Availability | Result | Count |
|---|---|---:|
| `AVAILABLE` | `SAFE_ORIGINAL_PATH` | 14 |
| `AVAILABLE` | `HOOK_PRESERVING_COALESCED_PATH` | 4 |
| `AVAILABLE` | `EXTERNALLY_OWNED_PATH` | 2 |
| `AVAILABLE` | `FAILED` (documented VulkanMod renderer crash) | 1 |
| `UNAVAILABLE` | `UNAVAILABLE` (dated exact-loader evidence) | 15 |

No profile remains `UNTESTED` or `PENDING_METADATA`. The catalog validator
passes 36/36 recipes and rejects its mutation suite.

## Runtime evidence boundary

Available profiles were executed in resumable, sequential batches against the
current final artifacts. Successful cells used ten controlled reloads, a stable
positive resolved-resource SHA-256, required profile/path markers, heavy-fixture
evidence when declared, forbidden-mixin scans, and clean exit. Immutable per-
profile summaries are the `fabric-*.md`, `forge-*.md`, and `neoforge-*.md` files
in this directory.

The one failed available profile is `fabric-vulkanmod`: VulkanMod crashes before
PackForge readiness with `java.lang.OutOfMemoryError: Out of stack space` in
Vulkan initialization. The run contains no PackForge fatal-mixin marker; its
narrow evidence and current artifact hash are recorded in
`fabric-vulkanmod-1.21.1.md`. It remains a user-decision/renderer-environment
waiver before Phase I can be classified `VERIFIED_COMPLETE`.

This summary does not claim the complete current-byte 62-cell matrix, live UI
route parity, comparative Quick Pack performance, or default promotion.
