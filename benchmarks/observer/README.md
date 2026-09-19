# Neutral resource-load observer

This test-only mod observes Minecraft itself. It does not load or reference any
production optimizer and must be the exact same JAR in all comparison modes for
a given Minecraft/loader cell. Menu mode does not enter worlds. In-world mode
creates a new deterministic test world inside an isolated benchmark profile.
Neither mode contacts services, hashes resources, alters graphics settings, or
suppresses the title-screen fade.

Apply `gradle/benchmark-observer.gradle` after each supported node's loader
configuration, then explicitly request its `benchmarkObserverJar` task. The
helper supports only the unobfuscated 26.1–26.2 node. Runtime version overrides
produce exact-version metadata and separate outputs below
`build/benchmarks/observer/<loader>/<minecraftVersion>/`. Nothing is attached to
the normal build, release inventory, or runtime classpath.

Required JVM properties:

- `packforge.benchmark.enabled=true`
- `packforge.benchmark.sessionId=<unique session>`
- `packforge.benchmark.output=<new JSONL file path>`
- `packforge.benchmark.reloads=6` (default; accepted 1–128)
- `packforge.benchmark.expectedWidth=1280` and `.expectedHeight=720`
  (positive framebuffer pixel dimensions, not logical window dimensions)
- `packforge.benchmark.expectedPackId=<exact active pack ID>` (optional;
  nonblank when provided, for example the runner's exact `file/...zip` ID)
- `packforge.benchmark.scenario=menu|inworld` (optional; default `menu`)
- In-world mode also requires the pinned scene identity/source properties from
  [SCENE_CONTRACT.md](SCENE_CONTRACT.md); the scene is created fresh, never copied
  from a save.
- `packforge.benchmark.discoveryOnly=true` (optional; untimed dimensions probe
  only, never usable for performance qualification)

Output files use CREATE_NEW; the controller never truncates previous results.
All records carry `schema: 1`, `sessionId`, `event`, `epochMillis`, `nanoTime`,
`jvmUptimeMillis`, and `reloadIndex`. Protocol 3 also includes resource generation,
render generation, input/render sequence numbers, actual resource reload counts,
and scenario; see [PROTOCOL.md](PROTOCOL.md). Index -1 denotes startup. Index 0 is the
priming reload; indices 1–5 are measured reloads. `ready` records the first
completed render tick with an extracted title screen, an unminimized window,
and the vanilla title fade finished. Readiness additionally requires successful
completion of the actual resource-manager generation, a frame extracted after
that completion, the render path returning, and a client input tick after
completion. Actual input acknowledgment is also required. A render-tick RETURN hook deliberately
includes frame presentation and that frame's limiter overhead; it is not a GPU
fence or a claim that all world assets have been exercised.

After one second of usable scenario time, `reload_requested` precedes the vanilla
reload call. `reload_complete` records its future completion. `frame_ready`
records the first eligible frame thereafter. An allocated overlay can remain
when a mod has made it nonblocking; actual dispatch, not raw overlay presence,
determines input readiness. The raw overlay's retirement is recorded separately.
There is one second after previous overlay retirement before the next reload.
The final reload waits for overlay retirement, then emits `complete`,
closes the asynchronous event writer, and schedules normal Minecraft shutdown.
Every actual client-resource manager reload emits `resource_reload_started` and
`resource_reload_complete` (or `resource_reload_failed`). Overlapping or extra
resource generations are recorded and reject the request; an unrequested reload
after scenario readiness rejects the process. This includes reloads triggered
by another mod while an overlay is hidden. The observer does not infer duplicate
work merely from RRLS configuration names. Early startup events may have unknown
framebuffer dimensions and no acquired focus; readiness/timing admission still
requires both.

Reload errors emit `error` with only the exception class and stop the client.
Invalid options/output failures fail the run; the external runner must reject
missing `complete`, any error, nonzero exit, or incomplete event sequences.

Clock values are sampled on the observing thread before asynchronous disk I/O.
The runner measures actual process creation separately: JVM uptime does not
include OS process creation and cannot substitute for the startup metric.

## Validation before using timings

Compile each exact Minecraft/loader cell and inspect JAR entries, metadata, and
class references for independence. Launch loader plus observer first, confirm
all observer hooks applied, and validate six ordered request/complete/frame triplets,
actual input acknowledgment at readiness, and clean exit. Repeat with frozen/correctness-control/
candidate/Quick Pack modes using the same observer hash. Test disabled mode,
invalid properties, an existing destination, reload failure, and window
minimization. `GenerationTrackerSelfTest` independently checks stale extraction,
hidden-overlay/active-reload rejection, input readiness, world rendering,
mid-frame generation changes, failure, and extra/unrequested reload rejection.
These tests require only a JDK. Pixel equivalence and first-use responsiveness
still need separate correctness runs; event sequencing alone does not prove them.

## Deterministic in-world scenario

Startup `ready` still means the first interactive title menu. Then `world_loading`
precedes creation of `saves/packbench-scene-v1`, seed `5782977387033873987`, using
Minecraft's official flat preset, Creative mode, Peaceful difficulty, disabled
structures, fixed noon/clear weather, and disabled random ticks/mob spawning.
The controller refuses existing worlds and symbolic-link save directories. Its
output file must be inside that isolated game directory. It never opens an
existing save or copies a player's world.

The fixed camera faces multilingual sign text, connected glass/glass panes,
animated water/lava/campfire, an equipped armor stand, a pig, a floating clock
item, and a held sword. World setup uses the integrated server's command dispatch
and block-entity APIs. Failed commands abort setup. Readiness requires the sign,
camera position/orientation, the three exact fixed client entity UUIDs, then 120 frames
and 15 seconds of warmup before `world_ready`. Priming and measured reloads follow.
Scene generation/warmup are excluded from reload timing and reported separately.
Each measured frame rechecks scene entities, sign, and camera. Entity UUIDs are
fixed so ETF/EMF variant selection cannot drift between processes.

In-world `frame_ready` also requires no screen, grabbed mouse, live
player/level, and the world render path completing with the new resource
generation, plus natural game-keybinding dispatch after the new world frame.
Saved shader and visual settings remain untouched. The observer
does not call Iris, RRLS, or optimizer internals; the runner must pin settings
and logs and validate shader activation. A returned render path and frame swap
prove dispatch/presentation, not a GPU fence or pixel equivalence. Visual scene
checks and native/resource stability remain independent admission gates.

Every event also records `framebufferWidth` and `framebufferHeight` sampled
from `Window.getWidth()` / `getHeight()`, which return actual framebuffer
dimensions in the inspected Minecraft source. Initial usable frames must match
the expected dimensions. Once ready, a framebuffer resize away from the
expected dimensions invalidates the process even if it returns to the expected
size before the next rendered frame. Failure records retain actual sampled
dimensions when available. The observer never resizes the window itself.

`ready` and every `frame_ready` include ordered `activePackIds`, obtained from
`Minecraft.getResourceManager().listPacks().map(PackResources::packId)` on the
client thread. This reads only already-loaded pack metadata, without reopening
archives, reading resources, or hashing payloads. When `expectedPackId` is set,
its absence emits an error including the actual list and cleanly stops the run.
The runner must reject unexpected pack-list differences between modes as well
as missing primary packs, so reload fallback cannot silently qualify.

API evidence: supplied 26.1 decompiled `Minecraft.runTick(boolean)` presents and
swaps before returning; `TitleScreen.extractRenderState` owns `fading`.
Cached official 26.2 classes retain `runTick(boolean)` and `fading`; both versions
set `fading` to false after the two-second fade in render extraction. The 26.2
adapter reads `Gui.screen()` and `Gui.overlay()` because those fields moved.
Callback event indices are captured per request rather than read across threads.
Compilation
and runtime injection verification remain required for each exact loader cell.

## Foreground and throttling admission

On its first render callback, the enabled observer requests real GLFW window
focus exactly once. It checks GLFW's actual focused attribute on the client
thread, rather than overriding Minecraft's cached focus state. Initial focus
must be obtained within one second and before a usable title frame; otherwise
the run emits `error` and shuts down. This is an admission deadline, not an
added measurement delay. No retry or user-assisted focus recovery is accepted.

Once focus is acquired, any loss invalidates the run, including a loss/regain
between frames recorded by the existing Window focus callback tail hook.
Every event includes `windowActive`; qualifying ready/request/complete/frame
events require true, and any error rejects the entire process sample. Future
completion callbacks use the last observed focus state without invoking GLFW
on worker threads; the following render callback enforces admission again.

Vanilla 26.1 throttles an iconified window to 10 FPS, AFK after 60 seconds to
30 FPS, and long AFK after 600 seconds to 10 FPS. An ordinary out-of-world menu
has a 60 FPS limit. The observer rejects SHORT_AFK, LONG_AFK, or WINDOW_ICONIFIED
instead of altering options or resetting activity timestamps. The NUL character
dispatch probe is separate from physical/key-binding input and rejects a run if
it resets the activity timer. Benchmark profiles must
configure equivalent non-AFK inactivity settings. The observer preserves the
ordinary menu limiter and does not fake activity or disable fades.

Startup focus admission begins at the first render callback, not process
creation. Record process start independently; do not claim focus was observed
before Minecraft reached this hook. Validate focus rejection with a separate
pilot that switches away during a reload, and reject that pilot's timings.
