# Mystic Confluence

`AI:RemoveDeck:All` kept the spell out of ordinary AI selection. The baseline fixture confirms the native `CharmAi` declines the useful one-target case because its repeated-mode chooser does not fill the three required choices.

The opt-in `mystic-confluence` profile casts only when a counter or bounce mode has a useful opportunity. It uses native counter evaluation and targeting, chooses the highest-valued legal opposing creature for bounce, then fills remaining mode slots with card draw. Native mode cloning supports repeated choices; rules, payment, targets, timing and resolution remain engine-owned. Classic and unrelated profiles retain the original block and behavior.

`MysticConfluenceTests` passes **22 native checks** covering repeated draw, target ranking, counter/bounce/draw priority and resolution, native payment, no-target holds, normal card selection, and profile isolation. The baseline suite passes **1 check** and records native refusal with one useful targeted mode.

The full NeoForge 1.0.28 gate passes **5,883 checks across 65 suites** (22 new plus all 5,861 prior checks at exact counts). The installed-file guard checked 21,729 hashes and found zero changes.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions / bounce / two-draw resolutions |
| --- | ---: | ---: |
| 139011 | 0 / 0 | 6 / 6 / 6 / 6 |
| 139012 | 0 / 0 | 12 / 12 / 12 / 11 |

In seed 139012, the remaining resolution used counter, bounce and one draw. All four games completed with zero exceptions. Logs show the chosen Craw Wurm targets and each mode line; this is card-use evidence only, not a strength or winner claim. Mystic Confluence clears **LTC - Elven Council**.

Reproduce from the repository root:

```powershell
./arena/mystic-confluence/Run-Gate.ps1 -Baseline -OutName baseline-mystic-confluence-r1
./arena/mystic-confluence/Run-Gate.ps1 -Focused -OutName focused-mystic-confluence-r2
./arena/mystic-confluence/Run-Gate.ps1 -OutName final-mystic-confluence-r2
./arena/mystic-confluence/Run-Exposure.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

See `verification-summary.json`, `target/final-mystic-confluence-r2/provenance.json`, and `target/exposure/*/summary.json` for hashes, commands and per-game log witnesses.
