# Doomsday Confluence (`doomsday-confluence`, NeoForge 1.0.27)

Native `AI:RemoveDeck:All` blocks normal selection. Its generic Charm AI also cannot construct
more than three selections from the three available modes, despite `CanRepeatModes=True`.
Three baseline checks reproduce the selection and repeated-mode defect with affordable X=4.

## Approved rule

- Cast in our second main phase with an empty stack, using the largest natively affordable X
  for `{X}{X}{B}`. Never cast for X=0. Native payment handles colors, artifact mana and taxes.
- Choose sacrifice at most once, and only when neither we nor any ally has a nonartifact creature
  and an opponent has a nonartifact creature the native engine permits sacrificing.
- If a slot remains, choose discard at most once when at least two opponents have nonempty hands.
  Only public hand counts enter this decision.
- Fill every remaining slot with the original 3/3 black artifact Dalek creature token with menace.
  Native chaining preserves printed resolution order: sacrifice, tokens, discard. Opponents make
  their own native sacrifice/discard choices.

The helper resets X and builds a fresh mode chain during evaluation and before payment in both
native execution entry points. It retains no state between evaluations or games. Hooks are one
line in `SpellAbilityAi.canPlayWithSubs` and one preflight line in each of
`ComputerUtil.handlePlayingSpellAbility` and `ComputerUtil.playStack`. The original card script,
costs and resolution remain unchanged; default and unrelated profiles preserve native behavior.

## Verification

The new suite passes **140 native checks** covering normal AI selection, double-X colored and
artifact mana payment, taxes, timing and X=0 holds, safe edicts including teams and sacrifice
protection, public-count discard, priority at X=1/2, repeated Daleks, printed mode order, real
sacrifice/discard consequences, stale mana/board/hand/timing cancellation and profile isolation.
The full gate passes **5,633 checks across 52 suites**: all 5,493 prior checks at their exact counts
plus 140 new. The installed-file guard checked 21,729 hashes with zero changes. Gate hashes and
per-cast witness line numbers are recorded in `verification-summary.json`.

| Seed | Off: casts / resolved | On: casts / resolved | On: X values | On: sacrifice / discard / token modes |
| --- | --- | --- | --- | --- |
| 123011 | 0 / 0 | 3 / 3 | 1, 2, 7 | 2 / 3 / 5 |
| 123012 | 0 / 0 | 3 / 3 | 1, 2, 5 | 2 / 1 / 5 |

All four final observations finished within 120 seconds (4–6 seconds each), with zero exceptions.
Each cast occurred in our second main and paid exactly `2X+1` mana from Swamps and Sol Rings.
The two enabled games logged three sacrificed Walking Corpses each and six/two discarded cards.
The resolution descriptions show ten total Dalek modes, including five repeated token modes at
X=7. Exact token characteristics and native creation are also established by the fixtures. Native
logs write sacrifice/discard consequences before the `Resolve Stack` marker; the evidence parser
associates those events with the preceding cast. This lab clears **WHO - Masters of Evil**,
raising coverage from **75 to 76 of 184 decks** (90 cards unlocked, 80 verified).

Two preliminary batches were excluded: wall-only and Craw-Wurm-only opponents each hit the
120-second clock in the first control game. Each batch was interrupted, and both pairs were then
rerun with the final low-creature-density Underworld Dreams decks at the same clock. The excluded
raw logs remain under `target/smoke` and `target/paired`; their hashes and exclusion reason are
recorded separately. Only `target/bounded` supplies the table above.

Reproduce from the repository root:

```powershell
./arena/doomsday-confluence/Run-Gate.ps1 -Baseline
./arena/doomsday-confluence/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/doomsday-confluence/Run-Gate.ps1
./arena/doomsday-confluence/Run-Exposure.ps1
python -I arena/doomsday-confluence/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/moonlight-bargain/target/final-1.0.27`. Preserve sealed outputs under
the ignored `target/` directory. Exposure uses repeated-card three-player decks, seeds
123011/123012, profile off/on, 120 seconds per observation. Three seats allow the approved
two-opponent discard condition to trigger. The white commander is deliberately uncastable.

These are bounded native correctness and autonomous card-use checks, with no strength claim.
The policy does not estimate strategic value, preserve mana for other spells, forecast responses
or choose opponents' sacrifices/discards. It never selects a second edict/discard mode and
conservatively skips edicts whenever a friendly nonartifact creature is present.
