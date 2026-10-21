# Trading Post and Gaze of Granite opt-in policies

Two separate opt-in profiles; original scripts, costs, timing, targets, removal rules, deck-building
hints and default AI remain authoritative.

## Trading Post (`--ai-profile=trading-post`)

The play list keeps at most the safest useful mode for each untapped Trading Post:

1. **Draw** only with an artifact token or at least two non-Post artifacts, drawing allowed and
   more than four cards left in the library. Native draw API safety also applies.
2. **Return artifact** only with an artifact in the graveyard and a creature token or at least two
   non-Post creatures.
3. **Goat** only above 3 life (1-life cost).
4. **Gain 4 life** only at 10 or less life with at least two cards in hand.

The only artifact/creature is never offered as sacrifice fodder. Native costs still choose the
actual sacrifice/discard card, and native timing/effects are unchanged.

The 2026-10-17 review found that the original blanket `WillPlay` shortcut bypassed native target
selection and draw safety. It has been removed, along with its process-global card-ID authorization
cache. Native ChangeZone AI chooses the graveyard target and native Draw AI checks library/hand safety.
Only the selected return/draw mode's generic sacrifice-valuation veto is replaced at its cost seam;
life, discard, counter-removal and native cost feasibility remain checked. Goat/life modes retain the
opt-in timing and deterministic mode decision after their API legality checks.

## Gaze of Granite (`--ai-profile=gaze-of-granite`)

X is chosen from 1 through the largest nonland mana value, subject to the native `{X}{B}{B}{G}`
payment check. For each X the policy evaluates opposing minus friendly nonland permanents that
would actually die (indestructible, shield and `SacMe` permanents excluded). It requires at least
one opposing permanent and a positive net value, choosing the highest positive score and keeping the
smallest X on ties. Any public battlefield-to-graveyard/exile trigger suppresses the cast. An X that
destroys a permanent with an unsuppressed continuous ability is also held: losing a toughness or
protection source can cause additional deaths that this bounded evaluator does not predict. Surviving
sources do not suppress otherwise safe lower-X casts. The
native X payment, sorcery timing and destruction effect are untouched.

## Evidence

- NeoForge 1.0.25 repair gate: **1860/1860** checks across 24 suites (60 Trading Post + 40 Gaze of Granite +
  1760 prior Commander checks).
- Fixtures use normal AI selection and `handlePlayingSpellAbility`, without manually supplying the
  artifact-return target. Empty/near-empty libraries, draw prohibition, graveyard targeting prohibition,
  native payment, profile switching and new-game boundaries have regressions.
- Gaze fixtures reproduce Eldrazi Monument protecting two Force of Savagery: destroying the Monument
  also kills both Forces through native state actions. The opt-in policy now holds that line.
- Fresh custom probes and profile-off controls use the repaired candidate; exact action counts,
  hashes and clock stops are recorded in `card-use-evidence.json`. The additional return scenario
  uses Satyr Wayfinder's original self-mill through normal native play, without injected game actions.
- Trading Post: **42 casts and 93 resolved activations** across the three on-profile probes:
  22 artifact returns, 53 draws and 18 Goats. Gaze: **23 casts/resolutions**, destroying 129 opposing
  permanents while all lands survived. Both off controls had zero card use.
- **898 card-use checks** validate the event outcomes and candidate/input hashes. Counts are derived
  from root event kinds, excluding nested before snapshots; the verification refresh preserves the
  original runner summaries when correcting that legacy text-counter overcount.
- All seven probes reached their 30-second observation clocks with native exit 0 and no unexpected
  diagnostics. Clock-stop shutdown interruptions are recorded; no completion or winner is certified.
- Coverage: 44 unlocked, **34 verified**, 10 unlocked-only, 0 unimplemented and **32** decks clear.
  Trading Post uniquely clears `LTC - Food and Fellowship`.

This is bounded correctness and card-use evidence, not a game-completion, winner or strength claim.

## Reproduce

```powershell
./arena/trading-post-gaze/Check-NeoForge-1.0.25.ps1
foreach ($card in @('trading-post','gaze-of-granite')) {
    foreach ($mode in @('on','off')) {
        $seed = if ($card -eq 'trading-post') { 97021 } else { 97031 }
        ./arena/trading-post-gaze/Run-CardUseProbe.ps1 -Card $card -ProfileMode $mode -Seed $seed
    }
}
# second on-seeds
./arena/trading-post-gaze/Run-CardUseProbe.ps1 -Card trading-post -ProfileMode on -Seed 97022
./arena/trading-post-gaze/Run-CardUseProbe.ps1 -Card gaze-of-granite -ProfileMode on -Seed 97032
./arena/trading-post-gaze/Run-CardUseProbe.ps1 -Card trading-post -Scenario return -ProfileMode on -Seed 97041
./arena/trading-post-gaze/Check-CardUseEvidence.ps1
./arena/Audit-Deck.ps1 -All -Coverage *> arena/trading-post-gaze/target/coverage-audit.log
./arena/trading-post-gaze/Refresh-Verification.ps1
```

The 1.0.25 gate requires the prior Selvala candidate and reruns every suite on the installed-engine
candidate with both new policies and the shared-hook changes.
Repair artifacts live under `target/repair-1.0.25` and `target/repair-card-use`; the earlier candidate
and logs under `target/final-1.0.25` and `target/card-use` remain intact and are superseded by this gate.
