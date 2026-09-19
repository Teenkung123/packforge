# Full-modpack observer integration contract

The runtime runner now supplies `packforge.benchmark.scenario=menu|inworld` and
`packforge.benchmark.requireGenerationProof=true`. Full-modpack admission rejects
overlay-only readiness. Events retain `schema:1` and declare `observerProtocol:3`.
`analysis.py` validates that actual protocol instead of assuming overlay state.

Required evidence for each accepted startup/reload frame: scenario, successful
resource generation identity, rendered generation identity, and input readiness.
`resource_reload_started`, `resource_reload_complete` and failure events expose
nested/extra reloads. A hidden
RRLS overlay or completion of an earlier future is insufficient. An in-world
startup remains process creation to interactive menu; scene readiness is a
separate `world_loading`/`world_ready` sequence before priming. The neutral
observer creates `saves/packbench-scene-v1` in each fresh process; it rejects an
existing world. Original profile saves are never copied or opened.

The runner requires the full `generatedScene` contract described in
`benchmarks/observer/SCENE_CONTRACT.md`: generated owner/mode, exact world ID,
integer seed, ordered three entity UUIDs, observer/implementation/source hashes,
and manifest path/hash. It verifies all mirrored fields, the canonical source
inventory and the three compiled scene classes in the pinned observer JAR.
Scene/source/observer identity must match across modes. Runtime events report
that identity and the exact observed entity UUIDs before usable world frames.

At every usable frame, `renderedResourceGeneration` must equal the completed
`resourceGeneration`, `activeResourceReloads` must be zero, and input/render
sequence counters must advance after that generation's completion. Protocol 3
additionally requires the appropriate generation-specific actual input dispatch:
menu character handling or natural world key handling. Probe attempts and probe
generation are recorded; an activity reset rejects the run. A live raw overlay
can remain when actual input is acknowledged; `overlay_cleared` is a separate
milestone. `resources_rendered` and `input_ready` milestones must precede usable
frame acknowledgment. Every
measured reload must contain one fresh observed generation. Nested/extra
generations remain in raw JSONL and reject the attempt, matching the observer's
own failure policy. Startup lifecycle events can precede GLFW initialization;
focus/framebuffer requirements begin at the interactive-menu event.
