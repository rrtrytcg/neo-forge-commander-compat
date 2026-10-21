# Moonlight Bargain (`moonlight-bargain`, NeoForge 1.0.27)

Native `AI:RemoveDeck:All` prevents normal selection. Bypassing that block alone is insufficient:
`ChangeZoneAi.willPayUnlessCost` refuses lands, noncreatures and creatures with printed power below
two, while permitting all five ordinary creatures for ten life. The native baseline records both
selection/payment defects in five checks.

## Approved rule

- Cast only in an opponent's end step immediately before our turn, with an empty stack, at most
  three cards in hand including Bargain, at least seven library cards, at least fourteen life, and
  native five-mana affordability. Recheck before payment in both native execution entry points.
- After the original effect legally shows all five cards, select at most three. First reserve one
  land if we control fewer than six lands and have none in hand. Land ties use card name.
- Fill remaining slots with nonlands: prefer mana value at most our land count plus one, highest
  mana value first. More expensive nonlands follow, cheapest first. Ties use card name; identical
  copies retain their shown order. Extra lands and unselected cards go to the graveyard.
- Pay the original two life per selected card, at most six life per resolution, retaining at least
  ten life. Fourteen/fifteen life permits two keeps; sixteen or more permits three. Recheck current
  life and native payment legality for each card. An unfavorable look can keep fewer or none.

The casting decision uses library count only. The keep set is selected once from native remembered
cards after the look. A weak game-keyed cache indexed by ability ID contains primitive identities
only; it retains no game/card graph or state across copied games. Recasting starts a new budget,
and later life gain does not enlarge the initial
keep set. No policy RNG, hidden opposing information or graveyard-combo planning is used.

Hooks: one line in `SpellAbilityAi.canPlayWithSubs`, one in `ChangeZoneAi.willPayUnlessCost`, and one
preflight line in each of `ComputerUtil.handlePlayingSpellAbility` and `ComputerUtil.playStack`.
The original script, payment, repeat loop, hand/graveyard moves and cleanup remain native. The
native loop offers payment once per looked-at card; a fixed set of at most three therefore caps
total payment at six. Default AI and unrelated profiles retain their existing payment decisions.

## Verification

The focused suite passes **105 checks** covering cast thresholds/timing, normal AI selection, native
colored mana payment, deterministic mixed-type keeps, duplicate identities, total life budget and
reserve, unfavorable looks, native payment prohibition, no-draw semantics with Underworld Dreams,
fresh hand/life/library/phase/mana cancellation in both execution paths, per-card rechecks, recasting,
game isolation, native game-copy/LKI ability-ID isolation, and unchanged default/unrelated-profile
behavior. The full gate passes **5,493 checks across 51 suites**: all 5,388 prior checks at their
exact counts plus 105 new. The installed-file guard checked 21,729 hashes with zero changes.
Final gate hashes and exposure results are recorded in `verification-summary.json`.

| Seed | Off: casts / resolved | On: casts / resolved | On: paid keep offers | On: life spent |
| --- | --- | --- | --- | --- |
| 122011 | 0 / 0 | 4 / 4 | 10 | 20 |
| 122012 | 0 / 0 | 1 / 1 | 2 | 4 |

All four observations finished inside their 120-second clocks, with zero exceptions. Every cast
paid five Swamp mana in the opponent's end step immediately before our turn. Seed 122011's four
resolutions paid 6, 6, 6 and 2 life (3, 3, 3 and 1 keep offers); seed 122012 paid 4 life for two.
All respected the six-life cap and ten-life reserve. The single-keep resolution demonstrates
spending below the cap on an unfavorable look.

The native log writes the `Resolve Stack` marker after the effect's life payments. The evidence
script therefore associates each payment between its cast and that resolution marker, and records
exact raw-log line numbers. Native match logs omit library-to-hand/graveyard moves; exact keep
choices, destinations, cleanup and no-draw behavior are established by the native fixtures.
Raw logs are preserved unchanged. This lab clears **C18 - Nature's Vengeance**.

Reproduce from the repository root:

```powershell
./arena/moonlight-bargain/Run-Gate.ps1 -Baseline -OutName verified-1.0.27
./arena/moonlight-bargain/Run-Gate.ps1 -Focused -OutName focused-1.0.27
./arena/moonlight-bargain/Run-Gate.ps1
./arena/moonlight-bargain/Run-Exposure.ps1
python -I arena/moonlight-bargain/Summarize-Evidence.py
./arena/Audit-Deck.ps1 -All -Coverage
```

The full gate chains on `arena/jolrael/target/final-1.0.27`. Outputs under `target/` are private and
ignored by Git; preserve sealed gates instead of overwriting them. Exposure uses purpose-made
two-player repeated-card decks, seeds 122011/122012, profile off/on, 120 seconds per observation.
The white commander is deliberately uncastable with these decks.

These checks establish bounded native correctness and autonomous card use, not strength. Ranking
uses the approved mana-value heuristic rather than a tactical card evaluator; it does not forecast
opponents' responses, later draws or triggered life loss after resolution. Life-payment prohibitions
can cause every keep to be declined. Cards entering hand through Bargain are not drawn.
