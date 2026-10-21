# Carpet of Flowers (`carpet-of-flowers`, NeoForge 1.0.27)

The native `Pump` trigger AI chooses the first legal opponent, which can miss the opponent with the most Islands. Normal permanent selection also declines this `AI:RemoveDeck:All` card. The opt-in profile admits the spell through the normal permanent AI and gives its optional trigger a deterministic, public-information target.

## Approved rule

- Cast only when an opponent currently controls at least one Island; retain native timing and mana affordability.
- On each optional trigger, choose the legal opponent with the most Islands. Resolve ties by stable seat order, and decline if no opponent controls an Island.
- Keep native once-per-turn trigger limits and the native mana-color choice.

Only the exact card and opt-in profile activate the helper. The policy reads public battlefield subtype and target-legality data, has no stored state or policy RNG, and does not alter the card script or installed engine resources.

## Verification

Baseline: one native-selection check reproduces the block. Focused gate: 18 checks. Full gate: **5,687 checks across 54 suites** (18 new plus all 5,669 prior at exact counts); the installed-file guard checked 21,729 hashes and found zero changes. Native fixtures cover admission, timing and board-state holds, greatest-Island target selection, stable ties, trigger resolution and exact mana production, and profile isolation.

| Seed | Profile off: casts / triggers / resolves | Profile on: casts / triggers / resolves |
| --- | ---: | ---: |
| 125011 | 0 / 0 / 0 | 2 / 12 / 2 |
| 125012 | 0 / 0 / 0 | 6 / 13 / 6 |

All four two-player custom-deck observations finished in 1.3–1.8 seconds with zero exceptions. Trigger log witnesses show the chosen opponent; native fixtures verify Island-count ranking and mana production. These games establish autonomous card use only, with no winner or strength claim. Gate hashes and line-numbered log evidence are recorded in `verification-summary.json`.

This clears **WHO - Paradox Power**, bringing coverage to **78 / 184 decks** (92 cards mechanically unlocked, 82 strategically verified).

Reproduce from the repository root:

```powershell
./arena/carpet-of-flowers/Run-Gate.ps1 -Baseline -OutName verified-1.0.27
./arena/carpet-of-flowers/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/carpet-of-flowers/Run-Gate.ps1
./arena/carpet-of-flowers/Run-Exposure.ps1
python -I arena/carpet-of-flowers/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/immoral-bargain/target/final-1.0.27`. Exposure uses the two purpose-built decks, seeds 125011/125012, profile off/on and a 120-second clock per observation.
