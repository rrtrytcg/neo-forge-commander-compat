# Greater Good: conservative opt-in rescue

Use `--ai-profile=greater-good`. The original card script and `AI:RemoveDeck:All` deck-building hint,
default AI, preset decks and installed application remain unchanged. Native AI still casts the
enchantment. This profile changes only its activated sacrifice/draw/discard decision and payment choice.

## Rules

- Sacrifice only a creature already threatened by visible removal or doomed in active combat, with
  current power at least four. Keep a blocker/attacker that would take an opposing creature with it;
  also keep blockers against trample where sacrificing could increase damage to the player.
- Prefer the largest qualifying power that leaves at least four library cards after drawing.
  Break equal-power ties by card ID; inspect no hidden opponent cards and add no policy RNG.
- Bind that exact creature and its incarnation/power to this ability and game/player/turn. Recheck
  the source, sacrifice legality, threat, power, drawing permission and library before any payment.
  A stale or unplanned ability fails closed, without selecting alternative fodder.
- Hold on public sacrifice/death/draw/discard triggers, active draw/move replacements, creatures with
  continuous abilities, and unsupported stacks. Theft, curse Auras and bounce do not authorize rescue.

The native threat predictor must be called with no hypothetical saviour: passing this Draw ability
suppresses its destroy prediction. The policy therefore uses its public no-rescue prediction with
native combat trade/trample guards, bounded to understood removal/damage/shrink stacks.

Before payment the native `Sacrificed$CardPower` expression evaluates to zero. The profile forecasts
the selected creature's current power without changing that SVar or populating a fake paid list.
The native cost visitor pays the bound sacrifice; the original engine then draws using actual
sacrificed LKI and discards three cards using native discard selection. Costs, taxes, restrictions,
triggers, state actions and resolution remain native. Weak plan keys and weak card/player references
avoid a global card-ID authorization cache or retaining completed games.

## Native and custom-deck evidence

The focused fixture suite has 75 checks: healthy/low-power holds, safe library boundaries, highest safe
power, original-script draw/discard, real normal-controller removal and combat responses, trade/trample
holds, public uncertainty, stale power/library/threat/source/profile/incarnation cancellation, unplanned
copies and game rollover, native activation taxes, original deck hint and default/unrelated profiles.

The full gate overlays four AI classes on the exact prior Trading Post/Gaze repair candidate and runs
all 25 suites on NeoForge 1.0.25. It requires 1935 checks: 75 new plus 1860 prior Commander checks.

Accepted results: **1935/1935 native checks**, **181 card-use checks**, three enchantment casts and
**16 resolved activations** (15 removal responses across seeds 98011/98012 and one declared-blocker
rescue at seed 98021). Both off controls produced zero card use. The removal runs and removal off
control reached their 30-second clocks; the accepted combat on/off runs ended within the clock.
Results are evaluated only for card use, with no winner or completion claim.

Coverage is now **34 clear decks** (up from 32), 328 distinct blockers across 498 occurrences, and
45 unlocked cards: **35 verified**, 10 unlocked-only. This clears the final blocker in
`BLC - Animated Army` and `MSC - Wakanda Forever`; `AFC - Aura of Courage` still has other blockers.

Custom probes are autonomous native four-player Constructed matches with deliberately nonstandard
decks. Repeated Greater Goods and creatures make use visible. Opponents supply original Murder/Doom
Blade spells or indestructible Darksteel Juggernauts with their native must-attack rule. The observer only reads events, public stack/blocker
state, native paid-sacrifice LKI and before/after zones; it never chooses actions or changes the game.
Profile-off controls use the same decks/seeds. Clock stops and shutdown interruptions are recorded;
card use is the acceptance criterion, without a completed-game, winner or strength claim.

The initial combat deck used Gigantosaurus. It cast Greater Good but produced no activation before
the observation clock: the board stalled after early attacks. That non-witness and its original deck
are preserved under `target/diagnostics/combat-gigantosaurus-on-98021`. The accepted setup changes
only the custom opponent deck, retaining seed 98021 and the tested policy/candidate/30-second clock.

`card-use-evidence.json` and `verification-summary.json` record the accepted counts, seeds, candidate,
logs, inputs and hashes. The checker asserts native draws equal sacrificed power, discard-three
consequences, a four-card library reserve, removal on the stack or a previously declared blocker,
and zero card use in off controls. Unlocked and verified registry states remain distinct.
Zone reconciliation accounts separately for creatures opponents kill with another removal spell
above a pending Greater Good activation; those additional graveyard cards are not counted as discards.

## Reproduce

```powershell
./arena/greater-good/Check-NeoForge-1.0.25.ps1
./arena/greater-good/Run-CardUseProbe.ps1 -Scenario removal -Seed 98011
./arena/greater-good/Run-CardUseProbe.ps1 -Scenario removal -Seed 98012
./arena/greater-good/Run-CardUseProbe.ps1 -Scenario combat -Seed 98021
./arena/greater-good/Run-CardUseProbe.ps1 -Scenario removal -Seed 98011 -ProfileMode off
./arena/greater-good/Run-CardUseProbe.ps1 -Scenario combat -Seed 98021 -ProfileMode off
./arena/greater-good/Check-CardUseEvidence.ps1
# Only after both evidence gates pass: record verified status in card-status.tsv.
./arena/Audit-Deck.ps1 -All -Coverage
./arena/greater-good/Refresh-Verification.ps1
```

For iteration only, `Check-NeoForge-1.0.25.ps1 -Focused` runs the new suite and writes a separate focused
manifest. It does not satisfy the full gate required by probes or verification refresh.
