# PackForge 1.4 delivery status — 2026-09-20

Implementation and publication preparation are on branch `1.4`. Runtime/build changes were committed as `4048759`; registry-driven release tooling and CI were committed as `5a95172`. Both were pushed to remote `1.4`. Nothing has been published, tagged, or released. Publication requires the user's final confirmation.

## Delivered behavior

- Bounded font preparation and correct null fallback across the supported ports, with Minecraft-owned provider closure and lazy glyph baking.
- Shared 128 MiB optimization accounting, font-first admission, and removal of completed bitmap retention from stable execution.
- Versioned, backed-up configuration migration, recommended settings, separate CPU mip preparation, and requested/effective/owner explanations. Existing explicit choices remain preserved.
- Early Quick Pack ownership handoff and RRLS overlay ownership. Independent reload feedback survives replacement or hidden loading overlays.
- Meaningful progress labels, bounded live status, and completion toasts on 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11, and 26.x. Configuration access has a PF-text fallback when Fabric API resource registration is absent.
- A single Stonecutter build graph, shared common compilation/tests, strict class inventories, registry-derived artifact/runtime/publication matrices, and disabled daily schedules.

## Compatibility and artifacts

The exact 17 files are in `build/libs`. The machine-readable release manifest and dry-run payloads are in `docs/release/modrinth-1.4-preview/`. Every filename, metadata classifier, size ceiling, SHA-1, and SHA-512 passed the release check.

- Fabric and NeoForge use one `mc26.1-26.3` JAR, including stable Minecraft 26.3. The implementation compiles against the older API with narrow adapters for changed 26.3 signatures.
- Forge retains its `mc26.1-26.2` JAR. No Forge 26.3 release was available during verification; unsupported compatibility is not advertised.
- Legacy targets remain 1.20.1 and 1.21.1/4/8/11, with the existing loader matrix. Their existing `1.4-beta.1` publication labels are preserved. The 26.x files are labeled `1.4` stable.
- Approved legacy size ceilings are 400,000 bytes for Fabric/NeoForge and 720,000 bytes for Forge. The 26.x ceilings remain 500,000/800,000 bytes. Runtime memory accounting remains 128 MiB.

## Verification

- Ordinary `gradlew.bat build` passed with strict inventories and all artifact checks enabled. The final recorded full build completed in 14 seconds; this is a build result, not a client performance benchmark.
- Current common and verification reports contain 335 tests, zero failures, and zero errors. Release preparation has 13 passing tests. These counts deliberately exclude stale output from obsolete target directories.
- All six local packaged Fabric/NeoForge combinations on 26.1.2, 26.2, and 26.3 passed startup, two reloads, and clean exit. Resource-equivalence checks used 20,002 fixture entries. Harness-only reporting/provenance failures were retained and separately revalidated against the original successful client evidence.
- Direct Fabric visual checks covered all five older versions: live progress and completion feedback were observed. Combined Quick Pack/RRLS checks covered 1.21.1/4/8/11; 1.20.1 combined live feedback and standalone completion were observed. Requested-but-disabled Quick Pack ownership was inspected in the actual configuration screen.
- Production Fabric 26.3 passed OpenGL and Vulkan startup, two reloads, and clean shutdown using the final JAR, SHA-256 `731f98ec5b63b2961c7d77e20555fa33ef5ccb90c056a68ed58c5c63fda98fd1`. Vulkan was confirmed in the game log as `1.4.341 NVIDIA 610.47`. The first Vulkan request omitted the options format version and selected OpenGL; it counts only as OpenGL evidence. Adding the current options version selected Vulkan correctly.
- The unchanged protected DevelopRP 1.9.5 archive loaded on Fabric 26.3 with 4,083 providers and 17/17 optimized font stacks, no unsupported-provider or budget fallback. A diagnostic reload took 9,613 ms. Minecraft displayed a pack metadata compatibility warning; normal user-facing acceptance allowed loading. The archive was only copied, never extracted or rebuilt. This does not establish full 26.3 shader-world visual equivalence or a comparative performance gate.

Private raw logs, manifests, failed attempts, and visual evidence remain under `.gradle/performance-rework/` and isolated `C:/tmp/PackForge-Fabric-Production/` runs. Protected-pack screenshots and content are not public release/CI artifacts.

## CI and publication

- Build CI: https://github.com/Teenkung123/packforge/actions/runs/35457149690 — passed all nine jobs.
- Initial runtime CI: https://github.com/Teenkung123/packforge/actions/runs/35457151956 — all 20 older-version, 26.1.2, and 26.2 cells passed. Both hosted 26.3 cells reached Minecraft/SDL but failed to create an OpenGL window (`Couldn't find matching GLX visual`); Vulkan fallback lacked `VK_KHR_surface`. Their corresponding local packaged checks passed. This hosted environment failure is retained rather than counted as a runtime pass.
- The follow-up CI setup explicitly installs Mesa/X11/Vulkan software drivers, starts a GLX-enabled display, selects software rendering, and checks `glxinfo -B` before launching Minecraft. The smoke controller now fails promptly if both graphics backends fail instead of waiting on a native error dialog. No runtime acceptance checks were removed.
- Dry-run Modrinth validation: 17 expected, 17 present, 17 payload-ready, zero errors. The publish workflow also runs its own required build/runtime/benchmark dependencies before uploading.
- Publication stays behind the explicit `publish_confirmation=true` workflow input. No publication workflow was dispatched.

## Limits and release decision

The user accepted the manual performance and visual improvement and ended the broad benchmark campaign. Earlier 15% and Quick Pack-within-5% statistical targets are not claimed as proven. Current results establish functional delivery and publication preparation, not those uncompleted statistical comparisons.

Issues #3/#4 have bounded current-build regression evidence and user-confirmed model appearance. An earlier intermittent 26.2 Vulkan native fault remains unattributed; a fresh 26.3 Vulkan pass does not prove that historical fault fixed. Neither GitHub issue was automatically closed.

No original profile, world, account, or protected pack was modified. The 16 pre-existing documentation deletions, `.codegraph/`, and unrelated historical reports were preserved outside the implementation commits. Release approval remains with the user.
