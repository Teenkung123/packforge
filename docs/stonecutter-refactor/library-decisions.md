# Build and runtime library decisions

Date: 2026-08-15

| Candidate | Current decision | Evidence / remaining gate |
|---|---|---|
| Stonecutter | Retain for registry-derived project graph, direct task ownership, and native preprocessing | Twelve source families and 19 registry build targets expand into 53 loader distributions; public leaves are direct. Fabric archive capture has native preprocessing proof across 16 targets. Full all-53 parity and general cross-loader preprocessing remain open. The legacy nested Gradle path is isolated as an explicit opt-in parity oracle. |
| Java toolchain resolver | Retain build-time resolver | Required for Java 17/21/25 reproducibility; no runtime dependency. Aggregate current proof remains open. |
| MixinExtras | Keep and embed once per loader | Packaging rules exist. Current all-53 remap/JarJar verification remains open. |
| Cloth Config / YACL | Do not require or shade | Native screens remain sufficient. |
| Mod Menu | Optional Fabric compile-only integration | PackForge screen must work without it. |
| Architectury API | Reject as runtime dependency | Separate loader distributions remain authoritative. |
| Architectury Loom | Reject for current migration | Direct registry-derived graph was selected; reopening this requires a new bounded feasibility decision. |
| JUnit/build libraries | Test/build scope only | No shipped runtime dependency. |
| Publication plugin | Not selected | Registry-derived manifest generator/verifier remains custom. Complete current 20-artifact verification is still required. |
| Compatibility-profile dependencies | Test/runtime-fixture inputs only | Materializer stages pinned JARs externally; none becomes a PackForge runtime dependency. |

No new required library mod is allowed. Build-graph adoption does not equal runtime or release verification.
