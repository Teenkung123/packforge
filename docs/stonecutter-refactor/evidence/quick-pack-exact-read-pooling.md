# Quick Pack exact-read pooling compatibility

Date: 2026-08-17

This implementation checkpoint decouples PackForge's optional ZIP read pool
from PackForge's resource-enumeration index for exact `getResource` lookups.

When Quick Pack owns resource indexing, PackForge intentionally disables its
own `PackIndex`. Before this change, the ZIP read pool therefore fell back for
every resource read even though Quick Pack does not provide pooled ZIP handles.

The adapters now distinguish two supplier sources:

- **Exact lookup** — the entry came from `ZipFile#getEntry(String)`. Reopening
  the same path through another `ZipFile` preserves the JDK's duplicate-entry
  selection and can be pooled without a PackForge index.
- **Enumerated entry** — the entry came from `ZipFile#entries()`. Reopening by
  path may select a different duplicate than the enumerated entry, so pooling
  still requires a PackForge index proving that the path is unique.

The modern shared adapter and Minecraft 1.20.1 adapter both verify that the
pooled archive owner still refers to the exact active `ZipFile` before creating
a supplier. Closed or replaced archive state continues to fall back to the
vanilla supplier.

Focused unit coverage verifies that a pooled exact duplicate lookup returns the
same bytes selected by vanilla `ZipFile#getEntry(String)`. Existing lifecycle
coverage continues to verify bounded handles, Windows file-lock release,
one-time failure reporting, and stale-supplier fallback after close.

This checkpoint does not enable `loaderZipPoolEnabled` by default. It only lets
users who opt into the PackForge ZIP read pool retain exact-read pooling when
Quick Pack owns resource enumeration. Runtime performance and final-JAR profile
evidence remain separate gates.
