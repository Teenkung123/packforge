# Rollback and disable behavior

## Repository rollback point

The crash-only rollback commit is `4fafbef4b95c2d39bc67131256d34f170195fbc6`. Optimization commits are layered after it so a performance phase can be reverted without discarding the Forge 1.20.1 startup fix.

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
- Reported broken artifact: `1.3.3-beta.1` on Forge 47.4.22.
- Replacing a mod JAR or mixin configuration requires a full Minecraft restart; F3+T does not reload mod classes.
- Users should not edit mixin JSON/refmaps inside a published JAR.

