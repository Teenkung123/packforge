# Rollback and disable behavior

## Repository rollback point

The crash-only rollback commit is `4fafbef4b95c2d39bc67131256d34f170195fbc6`. Optimization commits are layered after it so a performance phase can be reverted without discarding the Forge 1.20.1 startup fix.

| Optimization area | Commit |
|---|---|
| Compact ZIP index | `19041c7` |
| Bounded ordered work | `48a96f1` |
| Reload snapshot/telemetry | `e2aedd2` |
| FilePack operation hooks | `8eaf1ab` |
| Sprite decode | `ad30efb` |
| Artifact safety gate | `fb271b8` |
| Model scheduling | `640e3dd` |
| Shared-ZIP fail-closed guard | `fafe190` |
| Atlas retry ownership | `dde13ee` |
| Async context/retention repair | `4b92976` |
| Font preparation/cache | `484f077` |
| Namespace-aware operation verifier | `111f2a9` |
| Client-only font test scoping | `a72f628` |
| Production MixinExtras signature/selector repair | `bef22bc` |

## Feature rollback paths

| Area | Disable/fallback behavior |
|---|---|
| Whole reload optimizer | `reloadOptimizerEnabled=false` restores original behavior for optimizer-scoped features |
| ZIP index | `loaderIndexEnabled=false` uses original ZIP operations; index build failure falls back for that archive generation |
| ZIP read pool | `loaderZipPoolEnabled=false` uses original Minecraft suppliers; this option remains opt-in/evidence-gated |
| Model optimization | explicit strategy can select `ORIGINAL`; compatibility decisions must preserve loader/model hooks |
| Font preselection | `fontPrepareProviderSelectionEnabled=false` uses original provider selection |
| Sprite decode batching | `atlasDecodeBatchingEnabled=false` uses original scheduling |
| Mip parallelism | disabled/unavailable capability uses the original future/path |
| Atlas cap | `atlasCapEnabled=false` preserves original dimensions |
| Atlas retry | `atlasRetryEnabled=false` creates no retry-only state; retry remains default-off |
| Diagnostics | timing flags off must remove detailed timing overhead without disabling the optimization itself |
| ResourcePack Unbounded | ownership/compatibility bridge bypass remains authoritative |

Stored configuration values for unavailable target capabilities remain preserved. Internal rework alone does not bump config version 12 or change experimental defaults.

## Forge 1.20.1 artifact rollback

- Last crash-hotfix base: `1.3.3-beta.2`, commit `4fafbef`.
- Final integrated Forge 1.20.1 artifact: SHA-256 `d8fe15c791282cf1acd893003246662a4daa97d1ba72e39510360b940d29adbd`, including runtime repair commit `bef22bc`.
- Reported broken artifact: `1.3.3-beta.1` on Forge 47.4.22.
- Replacing a mod JAR or mixin configuration requires a full Minecraft restart; F3+T does not reload mod classes.
- Users should not edit mixin JSON/refmaps inside a published JAR.
