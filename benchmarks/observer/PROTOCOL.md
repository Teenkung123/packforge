# Observer generation protocol

Runner contract: `packforge.benchmark.scenario=menu|inworld` (default `menu`).
`packforge.benchmark.requireGenerationProof=true` is accepted; proof is always enforced.
All events keep `schema: 1` and add `observerProtocol: 3`, `scenario`,
`resourceGeneration`, `renderedResourceGeneration`, `renderSequence`,
`inputTickSequence`, `resourceReloadCount`, `activeResourceReloads`.
`requestResourceGenerations` counts actual generations for the current request.
`worldRendered` distinguishes a completed world path from a menu-only render.
`menuRendered` proves title-screen extraction ran in the completed frame.
`menuInputDispatchGeneration` and `worldInputDispatchGeneration` separately record
acknowledged input after a rendered generation; they never substitute for each other.
`overlayPresent` reports raw allocation, not whether transformed input sees it.
`titleFading`, `inputProbeGeneration`, `inputProbeAttempts`, and `inputProbeChangedActivity` are diagnostic
fields. A true `inputProbeChangedActivity` rejects the process.
`discoveryOnly` is always present and false for qualifying processes.
`ready` remains first usable rendered menu, including for the in-world scenario,
so startup retains the same definition. In-world mode then emits `world_loading`
and `world_ready` before its first requested reload. In-world events also include
`sceneId: "packbench-scene-v1"` and `sceneSeed: 5782977387033873987`.
In-world source/artifact identity and fixed entity UUID fields are specified in
[SCENE_CONTRACT.md](SCENE_CONTRACT.md). Existing worlds are never benchmark inputs.

Each actual CLIENT_RESOURCES manager reload emits `resource_reload_started` and
`resource_reload_complete` with its `resourceGeneration`. Successful readiness
requires a frame extracted after that generation completed, rendered, presented,
and followed by usable client input processing. In-world readiness also requires
the world render path to return. Overlay absence alone cannot satisfy proof.
Every requested reload must start exactly one resource generation; additional or
overlapping generations are retained as events and reject the process. A reload
outside a requested measurement after readiness also rejects the process.

In-world mode creates `saves/packbench-scene-v1` only when absent, inside the
isolated game directory containing this run's new `events.jsonl`. Never supply an
original user profile as a run directory. Existing worlds are refused. Scene
construction and warmup occur before `world_ready` and before priming reload.
Scene contents, warmup, and verification limits are documented in README.

Protocol 3 milestones, once per generation:

- `resources_rendered`: new-generation menu/world content was extracted,
  rendered, and presented. This does not assert input is usable or a fade is done.
- `input_ready`: actual menu character dispatch returned after that new frame,
  or the natural in-world keybinding handler returned after a new world frame.
  Menu readiness still requires the title fade finished; no fade settings change.
- `overlay_cleared`: raw overlay became null. May precede or follow input
  readiness and must not replace it as the first-usable-frame metric.
- `frame_ready`: resource reload future succeeded, new-generation frame and input
  acknowledgment both passed. `overlayPresent: true` is permitted here.

Menu acknowledgment sends a NUL `CharacterEvent` through the original
`KeyboardHandler.charTyped` entry point; it does not call Screen directly. That
path preserves transformed window/screen/overlay gates, creates no key binding
press, and does not call vanilla's activity timer. An injection AFTER the actual
`Screen.charTyped` invocation acknowledges dispatch. A separate observer rejects
any probe causing an activity reset. No synthetic input is sent in-world.
Next reload waits for the old overlay to retire and one second of quiescence;
final shutdown also waits for retirement so its timing is retained. These waits
occur after `frame_ready` and do not inflate the reported usable-frame duration.

Untimed fullscreen-size discovery: `packforge.benchmark.discoveryOnly=true`.
This bypasses expected-size comparison only, preserving focus and generation
checks. It records actual dimensions in `ready`, emits `discovery_complete`, and
shuts down before world entry or requested reloads. It never emits `complete`;
the runner/analyzer must reject it as a qualifying process. Set exact observed
dimensions in all subsequent measured specs. Do not enable discovery in them.
