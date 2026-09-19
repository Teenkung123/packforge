# Scene preflight correction, 2026-09-10

`fo-world-candidate-preflight-02` created a world but failed before priming. The
old observer reduced its exception to `deterministic scene failed`, losing the
useful command cause from the event/log output.

Read-only inspection of that generated world's four relevant region chunks
found all 1,250 platform blocks placed, all 8,750 blocks targeted by the following
air-clear command already air, and no glass at the next placement location.
Minecraft's inspected `FillCommand` throws `commands.fill.failed` when no blocks
change. That air clear was unnecessary in the new flat world and was removed;
no command failures were suppressed. The failed world's files were not changed.
Private raw evidence is retained as
`.gradle/performance-rework/scene-failure-02-evidence.json`.

Scene/reload failure handling now prints the full exception and cause stack to
stderr before stopping; the JSON error also includes the deepest bounded cause,
including the original command string. Failed attempts cannot qualify timings.

NBT checked against the supplied 26.1 source and exact official 26.1.2 client
class constants/read paths: Entity uses `UUID`, `Motion`, `Rotation`,
`NoGravity`, and `Invulnerable`; ItemEntity uses `Item` and short `Age`; ItemStack
uses `id` and integer `count`; Mob uses `NoAI`. Armor-stand equipment is assigned
through the original item command instead of guessed equipment NBT fields.
No Quick Pack implementation or protected resource-pack contents were inspected.

The corrected observer needs rebuilding and a new scene manifest. Next runtime
attempt must use a fresh profile; the failed scene is retained as evidence.
