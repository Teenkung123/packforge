# Approved PackForge 1.4 artifact budgets

On 2026-09-19 the user approved explicit legacy release limits of 400,000 bytes
for Fabric/NeoForge and 720,000 bytes for Forge. Both the canonical target
registry and verifier test registry now record these limits. Existing mc26
limits remain 500,000 bytes (Fabric/NeoForge) and 800,000 bytes (Forge).

The September 14 reviewed artifacts were approximately 370–385 KB for 1.21.x
Fabric/NeoForge and 693–696 KB for Forge. New font preparation, ownership and
feedback code account for the growth. A read-only maximum ZIP-compression
comparison found no useful compression saving. Removed bitmap retention code
stays removed; no mandatory player library was added.

This is an explicit release-budget revision, not a disabled verifier. All class,
metadata, nested-library, registry and size checks remain required. The 128 MiB
runtime optimization-memory budget is unchanged. Future growth beyond these
limits still fails the build and must be reviewed.
