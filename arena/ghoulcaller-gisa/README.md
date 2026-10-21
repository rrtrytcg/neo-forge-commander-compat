# Ghoulcaller Gisa (`ghoulcaller-gisa`, NeoForge 1.0.27)

`AI:RemoveDeck:All` blocks Gisa's spell and activated ability from normal selection. Native `TokenAi` also rejects Gisa's X before the sacrifice choice is available and applies its generic token-generation probability; its generic sacrifice-appetite check can reject native-payable creature sacrifices.

## Approved rule

- Cast Gisa through the native creature AI.
- Activate only in our second main phase with an empty stack, when native cost legality says `{B}`, tapping Gisa, and sacrificing another creature can be paid.
- Let native payment choose the fodder and native resolution create Zombies equal to its power. Hold outside the approved timing or whenever native costs are not payable.

The `ghoulcaller-gisa` profile applies only to this card. A narrow phase hook avoids the premature X calculation, a card-specific cost check retains native payability and payment selection, and the decision hook replaces the generic random token-generation roll with deterministic acceptance. Creature casting, target/cost legality, payment and token resolution remain native. No policy RNG or hidden information is used.

## Verification

Baseline: one check reproduces the normal-selection block. Focused gate: 19 checks. Full gate: **5,706 checks across 55 suites** (19 new plus all 5,687 prior at exact counts); the installed-file guard checked 21,729 hashes and found zero changes. Native fixtures cover deterministic activation, normal AI selection, native casting/payment and sacrifice/resolution, no-fodder and no-mana holds, timing/stack holds, tap-cost reuse and profile isolation.

| Seed | Profile off: Gisa casts / activations / Zombie resolutions | Profile on: casts / activations / Zombie resolutions |
| --- | ---: | ---: |
| 126011 | 0 / 0 / 0 | 3 / 5 / 5 |
| 126012 | 0 / 0 / 0 | 1 / 2 / 2 |

All four two-player custom-deck observations finished within 120 seconds with zero exceptions. Logs record the casts, activations and matching Zombie resolutions; native fixtures verify that Gisa paid for those activations by sacrificing another creature. These games establish autonomous card use only, with no winner or strength claim. Gate hashes and line-numbered log witnesses are recorded in `verification-summary.json`.

This clears **FDC - Wretched Ranks**, bringing coverage to **79 / 184 decks** (93 cards mechanically unlocked, 83 strategically verified).

Reproduce from the repository root:

```powershell
./arena/ghoulcaller-gisa/Run-Gate.ps1 -Baseline -OutName verified-1.0.27
./arena/ghoulcaller-gisa/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/ghoulcaller-gisa/Run-Gate.ps1
./arena/ghoulcaller-gisa/Run-Exposure.ps1
python -I arena/ghoulcaller-gisa/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/carpet-of-flowers/target/final-1.0.27`. Exposure uses purpose-built actor/opponent decks, seeds 126011/126012, profile off/on and a 120-second clock per observation.
