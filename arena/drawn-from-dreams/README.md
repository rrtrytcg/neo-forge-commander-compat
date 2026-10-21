# Drawn from Dreams

`AI:RemoveDeck:All` kept Drawn from Dreams out of normal AI play even though the native `DigAi` accepts a legal second-main cast. The narrow `drawn-from-dreams` profile bypasses only that card-level exclusion. The stock AI still decides when to cast, which two cards to keep, how to pay, and how to resolve the spell.

The baseline fixture passes **4 checks**: it confirms the native Dig API, the `RemoveDeck` marker, native willingness, and exclusion from normal spell selection. The focused native suite passes **12 checks**, covering profile isolation, normal selection, native card ranking, actual `{2}{U}{U}` payment, and the native seven-card effect moving the selected cards to hand and the rest back to the library.

The full NeoForge 1.0.28 gate passes **5,895 checks across 66 suites** (12 new plus all 5,883 prior checks at exact counts). The installed-file guard checked 21,729 hashes and found zero changes.

| Seed | Profile off: casts / resolutions | Profile on: casts / resolutions |
| --- | ---: | ---: |
| 140011 | 0 / 0 | 5 / 5 |
| 140012 | 0 / 0 | 9 / 9 |

All four two-player games completed within the 120-second clocks with zero exceptions. The on-profile traces show the spell resolving and putting two cards into hand each time; exact card choice is established by the native fixture. These are card-use observations only, not winner or strength evidence. Drawn from Dreams clears **NCC - Maestros Massacre**, bringing coverage to **90 / 184 clear decks**.

Reproduce from the repository root:

```powershell
./arena/drawn-from-dreams/Run-Gate.ps1 -Baseline -OutName baseline-drawn-from-dreams-r0
./arena/drawn-from-dreams/Run-Gate.ps1 -Focused -OutName focused-drawn-from-dreams-r2
./arena/drawn-from-dreams/Run-Gate.ps1 -OutName final-drawn-from-dreams-r1
./arena/drawn-from-dreams/Run-Exposure.ps1
./arena/Audit-Deck.ps1 -All -Coverage
```

See `verification-summary.json` for provenance, hashes, and smoke summaries.

For a clone of this standalone repository, run the focused native fixture with `./scripts/Test-CommanderCompat.ps1 -Suite DrawnFromDreamsTests -NeoForgePath 'C:\Games\NeoForge'`. The root command without `-Suite` runs the complete portable suite set; the lab-specific gate and exposure scripts use the sealed development-checkout artifacts.
