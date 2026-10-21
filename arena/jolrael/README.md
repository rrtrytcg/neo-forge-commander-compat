# Jolrael, Mwonvuli Recluse (`jolrael`, NeoForge 1.0.27)

The native `AnimateAllAi.canPlay` rejects Jolrael's hand-size activation, even with six available
mana and two ready Bears that would become 4/4. `AI:RemoveDeck:All` also blocks native selection.
The baseline fixture records both defects (two checks).

## Approved rule

- Cast with native creature evaluation; keep the original second-draw Cat trigger.
- Activate only at the beginning of our combat, with an empty stack, when at least two creatures
  that can natively attack each gain power, totaling at least four power gained.
- Hold if the native layered preview reduces any affected friendly creature's power or toughness,
  including tapped creatures and defenders. Counters and constant additive anthems are supported.
- Recheck the current hand, board, source and native affordability before payment. Hold if the
  activation is pending or this source has already activated this turn.

The helper uses no policy RNG or hidden opposing information. Hand contents are not evaluated;
the native animation uses the current hand count. Unknown continuous effects hold. Only the exact
immutable Commander Effect command-zone casting permission is exempted from this safety check;
additional continuous effects on that source are still inspected. Existing shared-preview callers
keep their previous conservative behavior.

Hooks: one line in `AnimateAllAi.canPlay`, and one preflight line in each of
`ComputerUtil.handlePlayingSpellAbility` and `ComputerUtil.playStack`. The shared
`CombatBaseStatsAi` exposes its existing safety check with an explicit opt-in Commander-permission
exception. Card scripts, default AI, other profiles and installed resources remain unchanged.
The shared gate script now matches generated patch line endings to the reconciled source, allowing
both LF and CRLF production files to build.

## Verification

The focused suite passes 71 checks covering exact thresholds, timing, attack readiness, no shrinking,
native selection, colored payment, original resolution and end-of-turn restoration, duplicate
activation suppression, stale-hand/board/source cancellation, native casting and Cat creation,
Commander permission handling, game isolation and unchanged default/unrelated profiles.
The full gate passes **5,388 checks across 50 suites**: all 5,317 prior checks at their exact counts
plus 71 new Jolrael checks. Its installed-file guard checked 21,729 hashes with zero changes.
The sealed gate and paired exposure observations are recorded in `verification-summary.json`.

| Seed | Profile off: casts / activations / Cat triggers | Profile on: casts / activations / Cat triggers |
| --- | --- | --- |
| 121011 | 0 / 0 / 0 | 1 / 4 / 1 |
| 121012 | 0 / 0 / 0 | 1 / 13 / 2 |

All four observations finished inside their 120-second clocks, with zero exceptions. Every one of
the 17 activations paid six Forest mana in our beginning of combat, resolved before attackers, and
was followed by at least two attackers and boosted damage. The summary records exact raw-log line
numbers for each payment/resolution/attack witness; damage from the animated creatures ranged
from four to eight. Cat trigger counts use the trigger's actual resolution text, which omits Jolrael's
name. Raw game logs are preserved unchanged; the evidence script recounts derived Cat metadata.

Jolrael removes the last blocked card from **MKC - Deep Clue Sea** and **SLD - From Cute to Brute**.
It also removes one of four blockers from **BLC - Peace Offering**, which remains blocked.

Reproduction from the repository root:

```powershell
./arena/jolrael/Run-Gate.ps1 -Baseline -OutName baseline-1.0.27
./arena/jolrael/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/jolrael/Run-Gate.ps1
./arena/jolrael/Run-Exposure.ps1
python -I arena/jolrael/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/easy-cards-3/target/final-1.0.27`. Output under `target/` is private
and ignored by Git; preserve sealed gates instead of overwriting them.

Exposure uses purpose-made two-player repeated-card decks, seeds 121011 and 121012 with the profile
off/on, and a 120-second clock per observation. These are native correctness and card-use witnesses;
they do not establish balance or strength. The white commander is deliberately uncastable with these
all-green decks. The policy does not forecast opponents' responses or hand changes during resolution,
and it deliberately skips defensive activations and a second activation in the same turn.
