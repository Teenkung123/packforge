# Build and runtime library decisions

| Candidate | Baseline decision | Evidence / gate |
|---|---|---|
| Stonecutter | Adopt as the intended build-time preprocessor, not yet migrated | Must pass one-target current-artifact parity before expansion |
| Java toolchain resolver | Retain build-time resolver if it makes Java 17/21/25 CI reproducible | No runtime dependency |
| MixinExtras | Keep and embed once per loader as current build does | Current direct verifier passes pinned 0.5.4 packaging |
| Cloth Config / YACL | Do not require or shade | Native screens already exist |
| Mod Menu | Optional Fabric compile-only integration | PackForge screen must work without it |
| Architectury API | Reject as runtime dependency | Separate loader builds already exist |
| Architectury Loom | One bounded feasibility spike only | Adopt only if it reduces build duplication and preserves Forge 1.20.1/refmaps |
| JUnit/build libraries | Test/build scope only | No shipped runtime dependency |
| Publication plugin | Not selected at baseline | Must consume one registry-derived manifest if adopted |

No new required library mod is allowed.
