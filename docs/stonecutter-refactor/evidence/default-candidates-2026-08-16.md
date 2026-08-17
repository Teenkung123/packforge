# Default candidate focused evidence

Checked: 2026-08-16

This immutable summary records focused final-JAR runs for the three bounded
candidate overrides that are implemented but remain default-off. Each run used
the current Fabric Sodium 1.21.1 profile, the current PackForge artifact, one
startup reload plus ten controlled reloads, a clean exit, and the resolved
resource hash marker. The focused override is catalog-bound and does not alter
the catalog file or release artifact.

## Common inputs

- Profile: `fabric-sodium`, loader `fabric`, target `mc1_21_1`
- Minecraft: `1.21.1-fabric-0.15.11`
- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`
- Resolved-resource SHA-256: `71D06E45240A2F4E22858A7595BFC9C13D14E06354F24DCB5D365B8D25B179E4`
- Every run: `reloads=10`, `cleanExit=true`, no forbidden mixin marker

## Focused results

| run | in-memory override | profile fingerprint | measured reloads 3-7 (ms) | median | result |
| --- | --- | --- | --- | ---: | --- |
| baseline | none | `CB13ADDD4234A83CF96FBEF96C3B64CC0077DE33509EB4D3201056219AEE4E8F` | 812, 653, 592, 580, 632 | 632 | PASS |
| ZIP pool | `loaderZipPoolEnabled=true` | `F6182BC164CC025E18E9CB79D17739ABF60E22064ECF2BE6E8ED4A908EBDF0CF` | 775, 642, 575, 549, 590 | 590 | PASS |
| font cache | `fontBitmapProviderCacheEnabled=true` | `A12B4F571CD43810442B688C433CC985EA5AB547E5CCE8AE242BEABA8B98F884` | 907, 680, 593, 565, 586 | 593 | PASS |
| sprite batching | `atlasDecodeBatchingEnabled=true` | `DE407711EAD27129E398CF59213B5BD6D378ACD6AC277A215F04851D44F5C603` | 927, 704, 574, 577, 596 | 596 | PASS |
| combined | all three overrides | `2703BE4EF9A1D3A572DBF40FB402C87E71E3D60ABDB0F3DEC1E520C2BB467132` | 934, 710, 632, 645, 696 | 696 | PASS, investigate |

The individual medians are 6.6%, 6.2%, and 5.7% below the baseline, but the
combined median is 10.1% above the baseline. This is a measured interaction
regression, not a promotion proof. The catalog therefore keeps all three
defaults off until lifecycle, multi-pack/Quick Pack compatibility, and the
four-mode performance benchmark are complete.

## Raw evidence locations

The raw logs were retained in the local run roots below. Their hashes bind this
summary to the exact observed output; the raw temporary roots are not treated
as committed release evidence until copied into a final evidence checkpoint.

- Baseline: `C:\tmp\PackForge-Fabric-Production\1.21.1-1.21.1-fabric-0.15.11\25690816-111621-71e818bc\compatibility-runtime-evidence.log`; SHA-256 `96B24418836D1DDFBE505E13546BF518918E8E4A95854E0E4BCFD73C7B8BEA39`
- ZIP pool: `C:\tmp\PackForge-Fabric-Production\1.21.1-1.21.1-fabric-0.15.11\25690816-111147-ace5406b\compatibility-runtime-evidence.log`; SHA-256 `985AE097CFA037741FBFBFBCD8712EBE887066F43B193816B48DD23058F6EFBC`
- Font cache: `C:\tmp\PackForge-Fabric-Production\1.21.1-1.21.1-fabric-0.15.11\25690816-111257-7e694d8d\compatibility-runtime-evidence.log`; SHA-256 `8AFB5BF69F8CDC4A03422582FF4F6EFD3045CF572E860D2A0E8BD40B0E795771`
- Sprite batching: `C:\tmp\PackForge-Fabric-Production\1.21.1-1.21.1-fabric-0.15.11\25690816-111410-15304191\compatibility-runtime-evidence.log`; SHA-256 `86F02A6155D43DAB30D9B2B32091CC94D23FF6E0B5276AD442D7E6C1C3336796`
- Combined: `C:\tmp\PackForge-Fabric-Production\1.21.1-1.21.1-fabric-0.15.11\25690816-111733-0453dce8\compatibility-runtime-evidence.log`; SHA-256 `B488AA35323BFFF1F74B32C0026A7B9DD1BFCD336C49FC752B69533CB25D65E3`

## Gate disposition

- `semanticParity`: PASS for this profile; all runs retained the same resolved-resource hash.
- `stability`: PASS for this profile; all runs completed ten reloads and exited cleanly.
- `configuration`: PASS; the result records show the requested override and distinct profile fingerprint.
- `lifecycle`: NOT_RUN; no qualifying ZIP-handle, provider-retain, or native-image slope was captured.
- `performance`: NOT_RUN; the single-profile signal is not the required four-mode/multi-pack benchmark, and the combined interaction regressed.
- `compatibility`: NOT_RUN; Quick Pack and the required cross-loader candidate profiles were not executed in this focused run.

