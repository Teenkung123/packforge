# Fresh observer-created scene contract

No world/save is an input. Every in-world process creates its own new
`saves/packbench-scene-v1`; an existing world is refused. Menu scenarios have no
scene properties and do not create worlds.

After the exact observer JAR is built, run:

```text
python benchmarks/observer/scene_manifest.py --observer <frozen-observer.jar> --output <new-scene-manifest.json>
```

The exclusive-create helper prints the `generatedScene` object for the runner:
`generatedBy: packbench`, `mode: observer-created`, `worldId`, integer `seed`,
ordered `entityUuids`, `observerSha256`, `implementationSha256`, `sourceSha256`,
`manifestPath`, and `manifestSha256`. The source digest covers the canonical
ordered source-hash list recorded in that manifest; the observer JAR hash pins
the whole executable artifact. Keep the manifest and exact source with reports.

Runner obligations before launch:

1. Verify manifest file SHA-256; require all mirrored spec fields to match it.
2. Require its observer hash to equal the pinned observer JAR hash.
3. Independently compute `implementationSha256` from the observer JAR using the
   helper algorithm; require known scene ID, seed, and three UUIDs below.
4. Do not stage `saves/**`, add Quick Play/world-open arguments, or copy any world.
5. Pass these properties to the observer:

```text
packforge.benchmark.expectedSceneId=packbench-scene-v1
packforge.benchmark.expectedSceneSeed=5782977387033873987
packforge.benchmark.expectedSceneImplementationSha256=<implementationSha256>
packforge.benchmark.sceneSourceSha256=<sourceSha256>
```

The observer compares actual class-resource digest with the expected digest and
validates ID/seed before creating a save. The source digest is provenance supplied
by the verified manifest, not a claim that Java sources exist in the game profile.
Runtime hashes only its own three small compiled scene classes once at startup;
it reads no pack resource bytes or world files to obtain this identity.

Exact implementation-hash algorithm: SHA-256 of each following class in order,
feeding its UTF-8 path with leading `/`, one zero byte, then complete class bytes:

```text
/dev/packbench/observer/SceneIdentity.class
/dev/packbench/observer/BenchmarkScene.class
/dev/packbench/observer/BenchmarkScene$SceneCommandOutput.class
```

Fixed entity UUIDs (NBT integer arrays derived from these UUIDs): armor stand
`00000000-0000-0000-0000-000000005001`, pig `...5002`, clock item `...5003`.
The client must receive those exact three UUIDs before scene warmup; arbitrary
nearby entities cannot satisfy readiness. This stabilizes UUID-selected ETF/EMF
variants. The runner's existing fixed player UUID remains separate.

All in-world events report `sceneId`, `sceneSeed`, `sceneImplementationSha256`,
`sceneSourceSha256`, and ordered `sceneEntityUuids`. `sceneObservedEntityUuids`
is empty during startup and becomes the exact expected list after packets arrive;
require it at `world_ready` and each measured `frame_ready`. World creation and
15-second/120-frame warmup stay outside reload timings. No simulation/render work
is stopped at the first measured frame.
