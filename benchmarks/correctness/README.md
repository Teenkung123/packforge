# Texture correctness diagnostics

This separate diagnostic mod compares complete CPU image contents from the actual
transformed Minecraft atlas path. It has no PackForge dependency or calls into
another optimizer. Use the same observer and this same capture mod in both
control and candidate. Keep all FO mods, shaders, resource ordering, game/JDK,
visual options, and pack hashes identical.

These are diagnostic runs only. Hashing reads every pixel synchronously while
Minecraft still owns the image, including on the apply thread immediately before
upload. It materially changes timing. Never use these durations as headline
performance results. No streams, packs, or native images are retained or opened
by this mod. The protected pack is never extracted or altered.

## Build and enable

The root/node build must explicitly apply `benchmarks/correctness/build-helper.gradle`
after loader dependencies are configured. It registers independent
`compileTextureCorrectness` and `textureCorrectnessJar` tasks for mc26 nodes only;
production source sets and production tasks do not include this output. Once the
root opt-in wiring is installed, build with:

```powershell
.\gradlew.bat :fabric:mc26_1_to_26_2:textureCorrectnessJar -Ppackforge_texture_correctness=true --configure-on-demand
```

Use the same loader/game override properties as the neutral observer when building
26.2 or another loader. Output:
`build/benchmarks/correctness/<loader>/<exact-game>/texture-correctness-<loader>-<exact-game>.jar`.
Metadata pins the exact game version. No mandatory runtime dependency is added
to PackForge. The mod ID is `texture_correctness`; the runner must include this
same diagnostic built-in pack in both sides' manifests, rather than silently
normalizing unknown pack differences away.

Add these properties only to a separate diagnostic specification:

```text
-Dpackforge.correctness.resourceHashingEnabled=true
-Dpackforge.correctness.output=<absolute-fresh-run-directory>/texture-pixels.jsonl
-Dpackforge.benchmark.sessionId=<same-session-as-neutral-observer>
```

The runner's `resourceHashingEnabled` diagnostic flag must also be true. Use the
new protocol-2 neutral observer with the same session ID. Without the JVM hashing
flag, the mixin plugin omits every capture mixin before transformation. Existing
output files are refused (`CREATE_NEW`). Close through Minecraft's normal shutdown
so the capture writer can flush and append its terminal record. Crashes, capture
errors, queue overflow, missing records, and incomplete shutdown fail comparison.

## What is captured

- Every client resource-manager generation gets its own monotonically increasing
  ID and start/completion records. Context is carried through the original supplied
  preparation and apply executors. Overlap, failure, or cancellation invalidates
  the run. Original futures and exceptions remain authoritative.
- Each original `SpriteLoader.loadAndStitch` call gets an independent atlas ID
  and invocation number. The complete original enumeration, custom sprite loader,
  stitch continuation, and mip future are invoked. No alternative mip algorithm
  is introduced.
- The full `SpriteContents` constructor records decoded image contents once.
  The delegating short constructor does not duplicate it. Each sprite has an
  independent ownership ID, atlas invocation, and generation. Frame dimensions
  and `isAnimated` are retained with the digest.
- `increaseMipLevel` return captures every available level, including level zero
  after solidify. `TextureAtlas.upload` additionally captures each actual region's
  final input chain and resolved region ID before calling the complete original
  upload method. Upload requires an owned decoded sprite and an earlier completed
  matching mip chain; missing instrumentation cannot yield a passing empty result.
- Complete sheets are hashed, including every animation frame, transparent RGB,
  partial alpha, and padding pixels. Canonical SHA-256 input is row-major ARGB bytes;
  no transparent-color normalization or alpha threshold is applied.
- Iris work attached synchronously to the original atlas upload inherits that
  atlas context. Custom PBR mip dispatch and BBE alpha handling remain untouched.
  An unowned sprite that performs mip work or reaches upload invalidates the run;
  unsupported asynchronous ownership must be investigated, not silently ignored.

Records use capture schema 2, separate from neutral observer schema 1/protocol 2.
Explicit IDs avoid assigning work to whichever reload happens to be newest when
it finishes. Parallel output order and allocation IDs do not affect comparison.
Duplicate sprite resource names in different atlases remain separate; counts and
all atlas invocation contents must agree. No attempt is discarded from raw output.
The writer queue has 8,192 records; hashing has 64 nonblocking permits and uses a
4 KiB chunk per active hasher. Capacity rejection invalidates diagnostics instead
of waiting or dropping evidence. Only hashes and bounded scalar records persist.

## Compare and self-tests

```powershell
python -B benchmarks/correctness/compare_textures.py control/texture-pixels.jsonl control/events.jsonl candidate/texture-pixels.jsonl candidate/events.jsonl
python -B -m unittest discover -s benchmarks/correctness -p 'test_*.py' -v
```

The comparator requires successful nonempty atlas coverage in every observed
generation, a complete neutral-observer lifecycle, rendered-generation proof,
and exact decoded/mip/upload records. Menu and in-world scenarios cannot be mixed.
Old observer protocols, hidden old frames, additional reloads, stale ownership,
missing chain levels, duplicate records/JSON keys, or differing pixels fail.

`src/test/java/dev/packbench/correctness/PixelDigestSelfTest.java` is independently
compilable with `PixelDigest.java` and Java 17+, without Minecraft or a GPU.
It checks row-major byte order, alpha/transparent RGB preservation, read count,
chunk boundaries, and invalid dimensions.

## Limits of the evidence

This proves equality of captured CPU images and atlas upload inputs, not framebuffer
or shader-output equality. It does not read GPU state. Animation frame timing,
shader sampling, connected/emissive visibility, font rendering, item/entity behavior,
and first-use responsiveness still need the generated scene/runtime checks.
It does not hash font textures, dynamic map textures, or arbitrary images outside
`SpriteContents`. A mod using a separate texture pipeline requires additional
explicit capture coverage before claiming equivalence for that pipeline.

The transformed full-FO runtime still needs compilation and an actual paired
diagnostic run. Unit/self-tests do not establish mixin application, shader/PBR
coverage, successful cancellation in Minecraft, or release readiness.
